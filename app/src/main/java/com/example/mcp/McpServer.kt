package com.example.mcp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class McpServer(
    private val context: Context,
    val port: Int = 8899,
    private val authTokenProvider: () -> String,
    private val onLog: (message: String) -> Unit = {},
    private val onError: (error: String) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                onLog("MCP Server started on port $port")

                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    val msg = "MCP Server startup error: ${e.localizedMessage}"
                    onLog(msg)
                    onError(msg)
                }
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        onLog("MCP Server stopped")
    }

    fun isServerRunning(): Boolean = isRunning

    /**
     * Shared request handling logic extracted for both local HTTP server and WebSocket relay client.
     * Validates authentication via EITHER:
     * 1. Header: X-Auth-Token (or Authorization: Bearer <token>)
     * 2. Query parameter: "token" on path (e.g. /mcp?token=XXXX)
     *
     * Then processes JSON-RPC 2.0 requests.
     * Returns Pair(statusCode, responseJsonBody).
     */
    suspend fun handleMcpRequest(
        headers: Map<String, String>,
        body: String,
        path: String = "/mcp"
    ): Pair<Int, String> {
        val expectedToken = authTokenProvider()

        // 1. Check Header Token
        val normalizedHeaders = headers.mapKeys { it.key.lowercase() }
        val headerToken = normalizedHeaders["x-auth-token"]
            ?: normalizedHeaders["authorization"]?.removePrefix("Bearer ")?.trim()

        // 2. Check Query Parameter Token (from path+query, e.g. /mcp?token=XXXX)
        val queryToken = extractQueryParam(path, "token")

        val isAuthenticated = (!headerToken.isNullOrEmpty() && headerToken == expectedToken) ||
                (!queryToken.isNullOrEmpty() && queryToken == expectedToken)

        if (!isAuthenticated) {
            onLog("Authentication rejected: invalid or missing X-Auth-Token header and token query parameter")
            val errorJson = JSONObject().apply {
                put("error", "Unauthorized: Invalid or missing X-Auth-Token header or ?token= query parameter")
                put("statusCode", 401)
            }.toString()
            return Pair(401, errorJson)
        }

        val responseJson = processJsonRpcRequest(body)
        return Pair(200, responseJson)
    }

    /**
     * Helper to extract a query parameter value from a URI path+query string.
     */
    private fun extractQueryParam(fullPath: String, paramName: String): String? {
        val queryIndex = fullPath.indexOf('?')
        if (queryIndex == -1 || queryIndex == fullPath.length - 1) return null

        val queryString = fullPath.substring(queryIndex + 1)
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            val key = if (idx > 0) pair.substring(0, idx) else pair
            if (key.equals(paramName, ignoreCase = true)) {
                val value = if (idx > 0 && idx < pair.length - 1) pair.substring(idx + 1) else ""
                return try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    value
                }
            }
        }
        return null
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 30000 // 30s timeout
            val inputStream = BufferedInputStream(socket.getInputStream())
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val requestLine = reader.readLine() ?: run {
                socket.close()
                return@withContext
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendErrorResponse(outputStream, 400, "Bad Request")
                socket.close()
                return@withContext
            }

            val method = parts[0]
            val rawPathWithQuery = parts[1]
            val basePath = rawPathWithQuery.substringBefore("?")

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            var contentLength = 0

            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val headerName = line.substring(0, colonIdx).trim().lowercase()
                    val headerValue = line.substring(colonIdx + 1).trim()
                    headers[headerName] = headerValue
                    if (headerName == "content-length") {
                        contentLength = headerValue.toIntOrNull() ?: 0
                    }
                }
                line = reader.readLine()
            }

            // CORS Preflight
            if (method.equals("OPTIONS", ignoreCase = true)) {
                sendCorsPreflight(outputStream)
                socket.close()
                return@withContext
            }

            // Path must be /mcp (stripping query string before comparing)
            if (basePath != "/mcp" && basePath != "/mcp/") {
                sendErrorResponse(outputStream, 404, "Not Found. MCP endpoint is at /mcp")
                socket.close()
                return@withContext
            }

            // HTTP Method must be POST
            if (!method.equals("POST", ignoreCase = true)) {
                sendErrorResponse(outputStream, 405, "Method Not Allowed. Use POST for /mcp")
                socket.close()
                return@withContext
            }

            // Read request body
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val buffer = CharArray(2048)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val toRead = (contentLength - totalRead).coerceAtMost(buffer.size)
                    val read = reader.read(buffer, 0, toRead)
                    if (read == -1) break
                    bodyBuilder.append(buffer, 0, read)
                    totalRead += read
                }
            }

            val requestBody = bodyBuilder.toString()
            val (statusCode, responseBody) = handleMcpRequest(headers, requestBody, rawPathWithQuery)

            if (statusCode == 200) {
                sendJsonResponse(outputStream, 200, responseBody)
            } else {
                sendErrorResponse(outputStream, statusCode, responseBody)
            }
        } catch (e: Exception) {
            onLog("Client handling error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun processJsonRpcRequest(requestBody: String): String {
        return try {
            val json = JSONObject(requestBody)
            val jsonRpcVersion = json.optString("jsonrpc", "2.0")
            val id = json.opt("id") ?: 1
            val method = json.optString("method", "")

            onLog("Received MCP method: '$method'")

            when (method) {
                "initialize" -> {
                    val result = JSONObject().apply {
                        put("protocolVersion", "2024-11-05")
                        put("capabilities", JSONObject().apply {
                            put("tools", JSONObject().apply {
                                put("listChanged", false)
                            })
                        })
                        put("serverInfo", JSONObject().apply {
                            put("name", "Android-File-Manager-MCP")
                            put("version", "1.0.0")
                        })
                    }
                    buildSuccessResponse(id, result)
                }

                "notifications/initialized" -> {
                    buildSuccessResponse(id, JSONObject())
                }

                "ping" -> {
                    buildSuccessResponse(id, JSONObject())
                }

                "tools/list" -> {
                    val tools = McpTools.getToolsListJson()
                    val result = JSONObject().apply {
                        put("tools", tools)
                    }
                    buildSuccessResponse(id, result)
                }

                "tools/call" -> {
                    val params = json.optJSONObject("params") ?: JSONObject()
                    val toolName = params.optString("name", "")
                    val arguments = params.optJSONObject("arguments") ?: JSONObject()

                    onLog("Executing tool: '$toolName'")
                    val (isError, contentText) = McpTools.executeTool(context, toolName, arguments)

                    val contentItem = JSONObject().apply {
                        put("type", "text")
                        put("text", contentText)
                    }

                    val result = JSONObject().apply {
                        put("content", JSONArray().apply { put(contentItem) })
                        put("isError", isError)
                    }

                    buildSuccessResponse(id, result)
                }

                else -> {
                    onLog("Method not found: '$method'")
                    buildErrorResponse(id, -32601, "Method not found: $method")
                }
            }
        } catch (e: Exception) {
            onLog("JSON-RPC parse error: ${e.message}")
            buildErrorResponse(null, -32700, "Parse error: ${e.message}")
        }
    }

    private fun buildSuccessResponse(id: Any?, result: JSONObject): String {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("result", result)
        }.toString()
    }

    private fun buildErrorResponse(id: Any?, code: Int, message: String): String {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }.toString()
    }

    private fun sendJsonResponse(outputStream: BufferedOutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Auth-Token, Authorization\r\n" +
                "Access-Control-Allow-Methods: POST, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"

        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendErrorResponse(outputStream: BufferedOutputStream, statusCode: Int, message: String) {
        val statusText = when (statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val bytes = message.toByteArray(Charsets.UTF_8)

        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Auth-Token, Authorization\r\n" +
                "Access-Control-Allow-Methods: POST, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"

        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendCorsPreflight(outputStream: BufferedOutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Auth-Token, Authorization\r\n" +
                "Access-Control-Allow-Methods: POST, OPTIONS\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }
}
