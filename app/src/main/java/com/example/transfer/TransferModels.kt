package com.example.transfer

data class ShareConnectionInfo(
    val ip: String,
    val port: Int,
    val code: String,
    val deviceName: String
) {
    fun toPayloadString(): String {
        return "TSHARE::$ip::$port::$code::$deviceName"
    }

    companion object {
        fun fromPayloadString(payload: String): ShareConnectionInfo? {
            return try {
                if (payload.startsWith("TSHARE::")) {
                    val parts = payload.split("::")
                    if (parts.size >= 5) {
                        ShareConnectionInfo(
                            ip = parts[1],
                            port = parts[2].toIntOrNull() ?: 8888,
                            code = parts[3],
                            deviceName = parts[4]
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class TransferStatus {
    IDLE,
    WAITING_FOR_CONNECTION,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    ERROR
}

data class TransferProgress(
    val currentFileName: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class ReceivedFile(
    val name: String,
    val path: String,
    val size: Long,
    val timeReceived: Long = System.currentTimeMillis()
)
