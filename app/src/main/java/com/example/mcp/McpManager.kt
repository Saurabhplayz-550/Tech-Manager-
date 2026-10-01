package com.example.mcp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class McpUiState(
    val isRunning: Boolean = false,
    val isStarting: Boolean = false,
    val serverPort: Int = 8899,
    val localMcpUrl: String = "http://localhost:8899/mcp",
    val publicMcpUrl: String? = null,
    val isRelayConnected: Boolean = false,
    val relayStatus: String = "Disconnected", // Disconnected, Connecting, Connected
    val relayError: String? = null,
    val authToken: String = "",
    val relayHost: String = "",
    val relaySecret: String = "",
    val totalRequests: Int = 0,
    val recentLogs: List<String> = emptyList(),
    val statusMessage: String = "Server is idle"
)

object McpManager {
    private const val PORT = 8899
    private var mcpServer: McpServer? = null
    private var relayClient: RelayWebSocketClient? = null
    private var relayStatusJob: Job? = null
    private var activeScope: CoroutineScope? = null

    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun initialize(context: Context) {
        val authToken = McpSecurityManager.getOrCreateAuthToken(context)
        val relayHost = McpSecurityManager.getRelayHost(context)
        val relaySecret = McpSecurityManager.getRelaySecret(context)
        val publicUrl = computePublicUrl(relayHost)

        _uiState.update { current ->
            current.copy(
                authToken = authToken,
                relayHost = relayHost,
                relaySecret = relaySecret,
                publicMcpUrl = publicUrl,
                localMcpUrl = "http://localhost:$PORT/mcp"
            )
        }
    }

    private fun computePublicUrl(host: String): String? {
        val cleanHost = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("wss://")
            .removePrefix("ws://")
            .trimEnd('/')
        return if (cleanHost.isNotBlank()) "https://$cleanHost/mcp" else null
    }

    fun start(context: Context, scope: CoroutineScope) {
        if (_uiState.value.isRunning || _uiState.value.isStarting) return
        activeScope = scope

        _uiState.update { it.copy(isStarting = true, relayError = null, statusMessage = "Starting MCP server...") }
        addLog("Starting MCP Server in Foreground Service...")

        val authToken = McpSecurityManager.getOrCreateAuthToken(context)
        val relayHost = McpSecurityManager.getRelayHost(context)
        val relaySecret = McpSecurityManager.getRelaySecret(context)

        val server = McpServer(
            context = context.applicationContext,
            port = PORT,
            authTokenProvider = { McpSecurityManager.getOrCreateAuthToken(context) },
            onLog = { msg ->
                addLog(msg)
                if (msg.startsWith("Executing tool:") || msg.startsWith("Received MCP method:")) {
                    _uiState.update { it.copy(totalRequests = it.totalRequests + 1) }
                }
            },
            onError = { err ->
                addLog("Error: $err")
                _uiState.update { it.copy(statusMessage = "Server error: $err") }
            }
        )
        mcpServer = server
        server.start(scope)

        val publicUrl = computePublicUrl(relayHost)
        val hasRelay = relayHost.isNotBlank()

        _uiState.update {
            it.copy(
                isRunning = true,
                isStarting = false,
                authToken = authToken,
                relayHost = relayHost,
                relaySecret = relaySecret,
                publicMcpUrl = publicUrl,
                statusMessage = if (hasRelay) "Server running locally. Connecting to relay..." else "Running locally on port $PORT (Relay host not set)"
            )
        }

        // Start WebSocket relay if host is configured
        if (hasRelay) {
            startRelay(scope, relayHost, relaySecret, server)
        }
    }

    private fun startRelay(
        scope: CoroutineScope,
        host: String,
        secret: String,
        server: McpServer
    ) {
        relayClient?.stop()
        relayStatusJob?.cancel()

        val client = RelayWebSocketClient(
            relayHost = host,
            relaySecret = secret,
            mcpServer = server,
            onLog = { msg -> addLog("[Relay] $msg") }
        )
        relayClient = client

        _uiState.update { it.copy(relayStatus = "Connecting") }

        relayStatusJob = scope.launch {
            client.isConnected.collect { connected ->
                _uiState.update { current ->
                    current.copy(
                        isRelayConnected = connected,
                        relayStatus = if (connected) "Connected" else "Connecting",
                        statusMessage = if (connected) {
                            "Online and accessible at ${current.publicMcpUrl}"
                        } else {
                            "Local server running; connecting to relay..."
                        }
                    )
                }
            }
        }

        client.start(scope)
    }

    fun stop() {
        relayStatusJob?.cancel()
        relayStatusJob = null
        relayClient?.stop()
        relayClient = null

        mcpServer?.stop()
        mcpServer = null
        activeScope = null

        _uiState.update {
            it.copy(
                isStarting = false,
                isRunning = false,
                isRelayConnected = false,
                relayStatus = "Disconnected",
                statusMessage = "Server stopped"
            )
        }
        addLog("MCP Server and relay client stopped.")
    }

    fun saveRelaySettings(
        context: Context,
        host: String,
        secret: String
    ) {
        McpSecurityManager.saveRelayHost(context, host)
        McpSecurityManager.saveRelaySecret(context, secret)

        val cleanHost = McpSecurityManager.getRelayHost(context)
        val cleanSecret = McpSecurityManager.getRelaySecret(context)
        val publicUrl = computePublicUrl(cleanHost)

        _uiState.update {
            it.copy(
                relayHost = cleanHost,
                relaySecret = cleanSecret,
                publicMcpUrl = publicUrl
            )
        }
        addLog("Saved relay settings: $cleanHost")

        // If server is currently running in Foreground Service, reconnect relay with new settings
        val scope = activeScope
        if (_uiState.value.isRunning && mcpServer != null && scope != null) {
            if (cleanHost.isNotBlank()) {
                startRelay(scope, cleanHost, cleanSecret, mcpServer!!)
            } else {
                relayClient?.stop()
                relayClient = null
                _uiState.update {
                    it.copy(
                        isRelayConnected = false,
                        relayStatus = "Disconnected",
                        statusMessage = "Running locally (Relay host cleared)"
                    )
                }
            }
        }
    }

    fun regenerateAuthToken(context: Context) {
        val newToken = McpSecurityManager.regenerateAuthToken(context)
        _uiState.update { it.copy(authToken = newToken) }
        addLog("Regenerated secure X-Auth-Token.")
    }

    fun clearLogs() {
        _uiState.update { it.copy(recentLogs = emptyList()) }
    }

    private fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $message"
        _uiState.update { current ->
            val updatedList = (listOf(entry) + current.recentLogs).take(40)
            current.copy(recentLogs = updatedList)
        }
    }
}
