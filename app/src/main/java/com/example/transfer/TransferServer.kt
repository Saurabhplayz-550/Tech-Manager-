package com.example.transfer

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class TransferServer(
    private val context: Context,
    private val pinCode: String,
    private val deviceName: String,
    private val onProgress: (TransferProgress) -> Unit,
    private val onFileReceived: (ReceivedFile) -> Unit,
    private val onError: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private var broadcastJob: Job? = null
    val port: Int = 8888

    fun start(scope: CoroutineScope, localIp: String) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                startUdpBroadcast(scope, localIp)

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
                    withContext(Dispatchers.Main) {
                        onError("Failed to start receiver: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun startUdpBroadcast(scope: CoroutineScope, localIp: String) {
        broadcastJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val payload = "TECHSHARE::$localIp::$port::$pinCode::$deviceName".toByteArray(Charsets.UTF_8)
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(payload, payload.size, broadcastAddr, 8889)

                while (isActive && isRunning) {
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        // ignore packet send errors on networks where broadcast is restricted
                    }
                    delay(1500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = BufferedInputStream(socket.getInputStream())
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            // Read headers
            val reader = BufferedReader(InputStreamReader(inputStream))
            val requestLine = reader.readLine() ?: run {
                socket.close()
                return@withContext
            }

            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
                line = reader.readLine()
            }

            if (requestLine.startsWith("GET /ping")) {
                val queryCode = extractQueryParam(requestLine, "code")
                if (queryCode == pinCode) {
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 29\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"code\":\"match\"}"
                    outputStream.write(resp.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } else {
                    val resp = "HTTP/1.1 403 Forbidden\r\nContent-Length: 21\r\nConnection: close\r\n\r\n{\"error\":\"invalid_pin\"}"
                    outputStream.write(resp.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                socket.close()
                return@withContext
            }

            if (requestLine.startsWith("POST /upload")) {
                val queryCode = extractQueryParam(requestLine, "code") ?: headers["x-code"]
                if (queryCode != pinCode) {
                    val resp = "HTTP/1.1 403 Forbidden\r\nContent-Length: 21\r\nConnection: close\r\n\r\n{\"error\":\"invalid_pin\"}"
                    outputStream.write(resp.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    return@withContext
                }

                val encodedFileName = headers["x-file-name"] ?: "received_file_${System.currentTimeMillis()}"
                val fileName = try {
                    URLDecoder.decode(encodedFileName, "UTF-8")
                } catch (e: Exception) {
                    encodedFileName
                }
                val fileSize = headers["content-length"]?.toLongOrNull()
                    ?: headers["x-file-size"]?.toLongOrNull()
                    ?: 0L
                val fileIndex = headers["x-file-index"]?.toIntOrNull() ?: 1
                val totalFiles = headers["x-total-files"]?.toIntOrNull() ?: 1

                // Target download directory
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "TechManager"
                ).apply { mkdirs() }

                // Avoid collision
                var destFile = File(targetDir, fileName)
                var counter = 1
                val baseName = fileName.substringBeforeLast('.', fileName)
                val extension = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
                while (destFile.exists()) {
                    destFile = File(targetDir, "$baseName ($counter)$extension")
                    counter++
                }

                val fileOut = FileOutputStream(destFile)
                val buffer = ByteArray(64 * 1024)
                var bytesReadTotal = 0L
                var lastProgressTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0L

                try {
                    while (bytesReadTotal < fileSize) {
                        val needed = (fileSize - bytesReadTotal).coerceAtMost(buffer.size.toLong()).toInt()
                        val count = inputStream.read(buffer, 0, needed)
                        if (count == -1) break
                        fileOut.write(buffer, 0, count)
                        bytesReadTotal += count
                        bytesSinceLastCalc += count

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastProgressTime
                        if (elapsed >= 300) {
                            currentSpeed = (bytesSinceLastCalc * 1000L) / elapsed
                            bytesSinceLastCalc = 0L
                            lastProgressTime = now

                            withContext(Dispatchers.Main) {
                                onProgress(
                                    TransferProgress(
                                        currentFileName = fileName,
                                        bytesTransferred = bytesReadTotal,
                                        totalBytes = fileSize,
                                        speedBps = currentSpeed,
                                        currentFileIndex = fileIndex,
                                        totalFiles = totalFiles
                                    )
                                )
                            }
                        }
                    }
                    fileOut.flush()
                } finally {
                    fileOut.close()
                }

                // Send success response
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 17\r\nConnection: close\r\n\r\n{\"status\":\"saved\"}"
                outputStream.write(resp.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                socket.close()

                withContext(Dispatchers.Main) {
                    onProgress(
                        TransferProgress(
                            currentFileName = fileName,
                            bytesTransferred = fileSize,
                            totalBytes = fileSize,
                            speedBps = 0L,
                            currentFileIndex = fileIndex,
                            totalFiles = totalFiles
                        )
                    )
                    onFileReceived(
                        ReceivedFile(
                            name = destFile.name,
                            path = destFile.absolutePath,
                            size = destFile.length()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun extractQueryParam(requestLine: String, param: String): String? {
        val qIdx = requestLine.indexOf('?')
        val sIdx = requestLine.indexOf(" HTTP/")
        if (qIdx == -1 || sIdx == -1 || qIdx >= sIdx) return null
        val query = requestLine.substring(qIdx + 1, sIdx)
        val pairs = query.split('&')
        for (pair in pairs) {
            val parts = pair.split('=')
            if (parts.size == 2 && parts[0].trim() == param) {
                return parts[1].trim()
            }
        }
        return null
    }

    fun stop() {
        isRunning = false
        broadcastJob?.cancel()
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }
}
