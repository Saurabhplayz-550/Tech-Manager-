package com.example.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Outbound WebSocket Relay Client for MCP.
 * Connects to: wss://<relayHost>/device-ws?secret=<relaySecret>
 *
 * Incoming messages:
 *   { "id": ..., "method": ..., "path": ..., "headers": { ... }, "body": "..." }
 * Calls shared handleMcpRequest(headers, body) on McpServer.
 *
 * Outgoing response:
 *   { "id": ..., "status": ..., "body": "..." }
 *
 * Implements auto-reconnect with exponential backoff (2s up to 30s) while enabled.
 */
class RelayWebSocketClient(
    private val relayHost: String,
    private val relaySecret: String,
    private val mcpServer: McpServer,
    private val onLog: (String) -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .build()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var webSocket: WebSocket? = null
    private var isEnabled = false
    private var reconnectJob: Job? = null
    private var currentScope: CoroutineScope? = null
    private var currentBackoffMs = INITIAL_BACKOFF_MS

    companion object {
        private const val INITIAL_BACKOFF_MS = 2000L
        private const val MAX_BACKOFF_MS = 30000L
    }

    fun start(scope: CoroutineScope) {
        if (isEnabled) return
        isEnabled = true
        currentScope = scope
        currentBackoffMs = INITIAL_BACKOFF_MS
        connect()
    }

    fun stop() {
        isEnabled = false
        _isConnected.value = false
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            webSocket?.close(1000, "Client stopped")
        } catch (_: Exception) {}
        webSocket = null
        onLog("Relay WebSocket disconnected and stopped")
    }

    private fun connect() {
        if (!isEnabled) return

        val cleanHost = relayHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("wss://")
            .removePrefix("ws://")
            .trimEnd('/')

        if (cleanHost.isBlank()) {
            onLog("Relay host is empty, cannot connect")
            return
        }

        val encodedSecret = try {
            URLEncoder.encode(relaySecret.trim(), "UTF-8")
        } catch (_: Exception) {
            relaySecret.trim()
        }

        val wsUrl = "wss://$cleanHost/device-ws?secret=$encodedSecret"
        onLog("Connecting to relay: wss://$cleanHost/device-ws...")

        try {
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _isConnected.value = true
                    currentBackoffMs = INITIAL_BACKOFF_MS // Reset backoff on successful connection
                    onLog("Connected to relay: $cleanHost")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleIncomingMessage(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    _isConnected.value = false
                    onLog("Relay closing ($code): $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _isConnected.value = false
                    onLog("Relay connection closed: $reason")
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _isConnected.value = false
                    val errorMsg = t.localizedMessage ?: t.message ?: "Connection error"
                    onLog("Relay connection failed: $errorMsg")
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            _isConnected.value = false
            onLog("Error initializing WebSocket connection: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun handleIncomingMessage(text: String) {
        val scope = currentScope ?: CoroutineScope(Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(text)
                val id = json.opt("id")
                val method = json.optString("method", "POST")
                val path = json.optString("path", "/mcp")
                val body = json.optString("body", "")

                // Parse headers map
                val headers = mutableMapOf<String, String>()
                val headersJson = json.optJSONObject("headers")
                if (headersJson != null) {
                    val keys = headersJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        headers[key] = headersJson.optString(key, "")
                    }
                }

                // Call shared request handling on McpServer (passing path to support ?token= param)
                val (statusCode, responseBody) = mcpServer.handleMcpRequest(headers, body, path)

                // Return response as JSON: { id, status, body }
                val responseJson = JSONObject().apply {
                    put("id", id ?: JSONObject.NULL)
                    put("status", statusCode)
                    put("body", responseBody)
                }.toString()

                webSocket?.send(responseJson)
            } catch (e: Exception) {
                onLog("Error processing relay message: ${e.message}")
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isEnabled) return
        val scope = currentScope ?: return
        reconnectJob?.cancel()
        val delayTime = currentBackoffMs

        // Calculate next backoff exponentially up to 30s
        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)

        onLog("Reconnecting to relay in ${delayTime / 1000}s...")
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delayTime)
            if (isActive && isEnabled && !_isConnected.value) {
                connect()
            }
        }
    }
}
