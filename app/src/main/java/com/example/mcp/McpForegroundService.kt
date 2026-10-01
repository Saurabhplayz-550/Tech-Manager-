package com.example.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground Service hosting the MCP Server and Outbound Relay WebSocket Client.
 *
 * Runs with foregroundServiceType="dataSync" to keep the MCP JSON-RPC server
 * and outbound WebSocket connection alive when the user navigates away or puts
 * the app in the background.
 */
class McpForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "mcp_connector_channel"
        const val NOTIFICATION_ID = 8899

        const val ACTION_START = "com.example.mcp.action.START"
        const val ACTION_STOP = "com.example.mcp.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            McpManager.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Show foreground notification immediately (Android requirement)
        val initialNotification = buildNotification("AI Connector active — tap to manage", "Starting server...")
        startForeground(NOTIFICATION_ID, initialNotification)

        // Start MCP server and outbound relay in dedicated service CoroutineScope
        McpManager.start(applicationContext, serviceScope)

        // Observe UI state to update notification dynamically
        McpManager.uiState.onEach { state ->
            if (state.isRunning) {
                val text = when {
                    state.isRelayConnected -> "Online & relay connected (${state.publicMcpUrl ?: "public"})"
                    state.relayStatus == "Connecting" -> "Connecting to relay..."
                    else -> "Local server active on port ${state.serverPort}"
                }
                updateNotification(text)
            }
        }.launchIn(serviceScope)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Connector (MCP)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the MCP server and remote relay connection active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        // Tap notification to open app directly to MCP Connector Screen
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "mcp")
        }
        val pendingContentIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick stop action from notification
        val stopIntent = Intent(this, McpForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingContentIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStopIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification("AI Connector active — tap to manage", content))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        McpManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
