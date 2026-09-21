package com.example.transfer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TransferMode {
    NONE,
    SEND,
    RECEIVE
}

enum class SenderConnectTab {
    SCAN_QR,
    ENTER_CODE
}

class TransferManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var activeMode by mutableStateOf(TransferMode.NONE)
        private set

    var senderTab by mutableStateOf(SenderConnectTab.SCAN_QR)

    // Receiver state
    var receiverPin by mutableStateOf("")
        private set
    var receiverIp by mutableStateOf<String?>(null)
        private set
    var qrBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // Transfer status
    var status by mutableStateOf(TransferStatus.IDLE)
        private set
    var progress by mutableStateOf(TransferProgress())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var statusMessage by mutableStateOf("")
        private set

    // Files
    val selectedFilesToSend = mutableStateListOf<File>()
    val transferredFiles = mutableStateListOf<File>()
    val receivedFiles = mutableStateListOf<ReceivedFile>()

    private var server: TransferServer? = null
    private var client: TransferClient? = null
    private var transferJob: Job? = null

    fun startReceiveMode() {
        stopAll()
        activeMode = TransferMode.RECEIVE
        status = TransferStatus.WAITING_FOR_CONNECTION
        errorMessage = null
        statusMessage = "Setting up receiver..."

        val ip = NetworkUtil.getLocalIpAddress()
        receiverIp = ip
        val pin = NetworkUtil.generateRandomPin()
        receiverPin = pin

        if (ip.isNullOrEmpty()) {
            status = TransferStatus.ERROR
            errorMessage = "No Wi-Fi or Hotspot network detected. Please turn on Hotspot or connect to Wi-Fi."
            return
        }

        val deviceName = NetworkUtil.getDeviceName()
        val info = ShareConnectionInfo(ip = ip, port = 8888, code = pin, deviceName = deviceName)
        qrBitmap = QrCodeUtil.generateQrCodeBitmap(info.toPayloadString(), 512)

        server = TransferServer(
            context = context,
            pinCode = pin,
            deviceName = deviceName,
            onProgress = { p ->
                status = TransferStatus.TRANSFERRING
                progress = p
                statusMessage = "Receiving ${p.currentFileName} (${p.currentFileIndex}/${p.totalFiles})"
            },
            onFileReceived = { received ->
                receivedFiles.add(0, received)
                status = TransferStatus.COMPLETED
                statusMessage = "File received: ${received.name}"
            },
            onError = { err ->
                status = TransferStatus.ERROR
                errorMessage = err
            }
        ).also {
            it.start(scope, ip)
        }

        statusMessage = "Waiting for sender to scan QR or enter code..."
    }

    fun startSendMode(initialFiles: List<File> = emptyList()) {
        stopAll()
        activeMode = TransferMode.SEND
        status = TransferStatus.IDLE
        errorMessage = null
        statusMessage = "Select how to connect to receiver"
        selectedFilesToSend.clear()
        selectedFilesToSend.addAll(initialFiles)
    }

    fun addFilesToSend(files: List<File>) {
        for (f in files) {
            if (!selectedFilesToSend.contains(f)) {
                selectedFilesToSend.add(f)
            }
        }
    }

    fun removeFileToSend(file: File) {
        selectedFilesToSend.remove(file)
    }

    fun connectAndSendViaPayload(payload: String) {
        val info = ShareConnectionInfo.fromPayloadString(payload)
        if (info == null) {
            errorMessage = "Invalid QR code. Please scan a valid file share QR code."
            return
        }
        sendFilesToTarget(info)
    }

    fun connectAndSendViaPin(pin: String, manualIp: String? = null) {
        val cleanPin = pin.replace(" ", "").trim()
        if (cleanPin.length < 4) {
            errorMessage = "Please enter a valid code"
            return
        }

        if (selectedFilesToSend.isEmpty()) {
            errorMessage = "Please select at least one file to send"
            return
        }

        status = TransferStatus.CONNECTING
        errorMessage = null
        statusMessage = "Connecting to receiver with code $cleanPin..."

        transferJob?.cancel()
        transferJob = scope.launch(Dispatchers.IO) {
            val targetInfo = if (!manualIp.isNullOrBlank()) {
                val port = 8888
                ShareConnectionInfo(manualIp.trim(), port, cleanPin, "Target Device")
            } else {
                val tempClient = TransferClient({}, {}, {}, {})
                tempClient.resolveReceiverByPin(cleanPin)
            }

            if (targetInfo == null) {
                withContext(Dispatchers.Main) {
                    status = TransferStatus.ERROR
                    errorMessage = "Could not find receiver with code $cleanPin.\nMake sure both phones are on the same Wi-Fi or one phone is connected to other's Hotspot."
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                sendFilesToTarget(targetInfo)
            }
        }
    }

    private fun sendFilesToTarget(targetInfo: ShareConnectionInfo) {
        if (selectedFilesToSend.isEmpty()) {
            errorMessage = "No files selected to send"
            return
        }

        status = TransferStatus.TRANSFERRING
        errorMessage = null
        statusMessage = "Connecting to ${targetInfo.deviceName}..."
        transferredFiles.clear()

        client = TransferClient(
            onProgress = { p ->
                status = TransferStatus.TRANSFERRING
                progress = p
                statusMessage = "Sending ${p.currentFileName} (${p.currentFileIndex}/${p.totalFiles})"
            },
            onFileSent = { f ->
                transferredFiles.add(f)
            },
            onComplete = {
                status = TransferStatus.COMPLETED
                statusMessage = "All ${selectedFilesToSend.size} file(s) sent successfully!"
            },
            onError = { err ->
                status = TransferStatus.ERROR
                errorMessage = err
            }
        )

        transferJob?.cancel()
        transferJob = scope.launch(Dispatchers.IO) {
            client?.sendFiles(targetInfo, selectedFilesToSend.toList())
        }
    }

    fun cancelTransfer() {
        client?.cancel()
        transferJob?.cancel()
        status = TransferStatus.IDLE
        statusMessage = "Transfer cancelled"
    }

    fun stopAll() {
        transferJob?.cancel()
        client?.cancel()
        server?.stop()
        server = null
        client = null
        activeMode = TransferMode.NONE
        status = TransferStatus.IDLE
        errorMessage = null
        statusMessage = ""
        progress = TransferProgress()
    }
}
