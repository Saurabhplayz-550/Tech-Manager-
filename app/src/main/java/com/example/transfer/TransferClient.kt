package com.example.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

class TransferClient(
    private val onProgress: (TransferProgress) -> Unit,
    private val onFileSent: (File) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    /**
     * Resolves the receiver's IP address on the local Wi-Fi or Hotspot network
     * using the 6-digit PIN code.
     */
    suspend fun resolveReceiverByPin(pin: String): ShareConnectionInfo? = withContext(Dispatchers.IO) {
        val cleanPin = pin.replace(" ", "").trim()

        // 1. Try to catch UDP broadcast beacon from receiver
        val udpResult = withTimeoutOrNull(2000L) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 800
                    bind(InetSocketAddress(8889))
                }
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)
                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (text.startsWith("TECHSHARE::")) {
                            val parts = text.split("::")
                            if (parts.size >= 5 && parts[3] == cleanPin) {
                                return@withTimeoutOrNull ShareConnectionInfo(
                                    ip = parts[1],
                                    port = parts[2].toIntOrNull() ?: 8888,
                                    code = parts[3],
                                    deviceName = parts[4]
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                socket?.close()
            }
            null
        }

        if (udpResult != null) return@withContext udpResult

        // 2. Check Android standard hotspot gateway (192.168.43.1)
        val hotspotGateway = "192.168.43.1"
        if (testPing(hotspotGateway, 8888, cleanPin)) {
            return@withContext ShareConnectionInfo(hotspotGateway, 8888, cleanPin, "Hotspot Host")
        }

        // 3. Check common Wi-Fi Direct / Hotspot alternative gateway (192.168.49.1)
        val wifiDirectGateway = "192.168.49.1"
        if (testPing(wifiDirectGateway, 8888, cleanPin)) {
            return@withContext ShareConnectionInfo(wifiDirectGateway, 8888, cleanPin, "Host Device")
        }

        // 4. Quick probe on local subnet based on own IP
        val localIp = NetworkUtil.getLocalIpAddress()
        if (localIp != null && localIp.contains('.')) {
            val subnetPrefix = localIp.substringBeforeLast('.')
            // Test router/gateway and top common addresses
            val candidates = listOf("$subnetPrefix.1", "$subnetPrefix.2", "$subnetPrefix.100")
            for (cand in candidates) {
                if (testPing(cand, 8888, cleanPin)) {
                    return@withContext ShareConnectionInfo(cand, 8888, cleanPin, "Receiver")
                }
            }
        }

        null
    }

    suspend fun testPing(ip: String, port: Int, pin: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("http://$ip:$port/ping?code=$pin")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 800
            conn.readTimeout = 800
            conn.requestMethod = "GET"
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendFiles(
        connectionInfo: ShareConnectionInfo,
        files: List<File>
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        val totalFiles = files.size
        var successCount = 0

        for ((index, file) in files.withIndex()) {
            if (isCancelled) break
            val fileIndex = index + 1
            val success = sendSingleFile(connectionInfo, file, fileIndex, totalFiles)
            if (success) {
                successCount++
                withContext(Dispatchers.Main) {
                    onFileSent(file)
                }
            } else {
                if (isCancelled) break
                withContext(Dispatchers.Main) {
                    onError("Failed to send ${file.name}")
                }
                return@withContext
            }
        }

        withContext(Dispatchers.Main) {
            if (!isCancelled && successCount == totalFiles) {
                onComplete()
            }
        }
    }

    private suspend fun sendSingleFile(
        info: ShareConnectionInfo,
        file: File,
        fileIndex: Int,
        totalFiles: Int
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(info.ip, info.port), 5000)
            socket.soTimeout = 30000

            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val inputStream = BufferedInputStream(socket.getInputStream())

            val encodedName = URLEncoder.encode(file.name, "UTF-8")
            val fileSize = file.length()

            val requestHeader = buildString {
                append("POST /upload?code=${info.code} HTTP/1.1\r\n")
                append("Host: ${info.ip}:${info.port}\r\n")
                append("Content-Length: $fileSize\r\n")
                append("X-File-Name: $encodedName\r\n")
                append("X-File-Size: $fileSize\r\n")
                append("X-File-Index: $fileIndex\r\n")
                append("X-Total-Files: $totalFiles\r\n")
                append("X-Code: ${info.code}\r\n")
                append("Connection: close\r\n\r\n")
            }

            outputStream.write(requestHeader.toByteArray(Charsets.UTF_8))
            outputStream.flush()

            val fileIn = FileInputStream(file)
            val buffer = ByteArray(64 * 1024)
            var bytesSent = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeed = 0L

            fileIn.use { fis ->
                var read = fis.read(buffer)
                while (read != -1 && !isCancelled) {
                    outputStream.write(buffer, 0, read)
                    bytesSent += read
                    bytesSinceLastCalc += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 300) {
                        currentSpeed = (bytesSinceLastCalc * 1000L) / elapsed
                        bytesSinceLastCalc = 0L
                        lastTime = now

                        withContext(Dispatchers.Main) {
                            onProgress(
                                TransferProgress(
                                    currentFileName = file.name,
                                    bytesTransferred = bytesSent,
                                    totalBytes = fileSize,
                                    speedBps = currentSpeed,
                                    currentFileIndex = fileIndex,
                                    totalFiles = totalFiles
                                )
                            )
                        }
                    }

                    read = fis.read(buffer)
                }
                outputStream.flush()
            }

            if (isCancelled) {
                socket.close()
                return@withContext false
            }

            // Read response
            val reader = BufferedReader(InputStreamReader(inputStream))
            val statusLine = reader.readLine()
            socket.close()

            statusLine != null && statusLine.contains("200")
        } catch (e: Exception) {
            e.printStackTrace()
            try { socket?.close() } catch (_: Exception) {}
            false
        }
    }
}
