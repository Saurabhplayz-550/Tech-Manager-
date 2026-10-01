package com.example.mcp

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object McpSecurityManager {
    private const val PREFS_NAME = "mcp_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "mcp_fallback_prefs"
    private const val KEY_AUTH_TOKEN = "mcp_server_auth_token"
    private const val KEY_RELAY_HOST = "mcp_relay_host"
    private const val KEY_RELAY_SECRET = "mcp_relay_secret"

    private fun getPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Resilient fallback for Android environments or emulators where Keystore might hiccup
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Retrieves existing long random auth token or generates a new one securely.
     */
    fun getOrCreateAuthToken(context: Context): String {
        val prefs = getPreferences(context)
        var token = prefs.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = generateNewToken()
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
        return token
    }

    /**
     * Regenerates a new random auth token if requested by user.
     */
    fun regenerateAuthToken(context: Context): String {
        val prefs = getPreferences(context)
        val newToken = generateNewToken()
        prefs.edit().putString(KEY_AUTH_TOKEN, newToken).apply()
        return newToken
    }

    private fun generateNewToken(): String {
        val randomBytes = ByteArray(32) // 256-bit cryptographically secure token
        SecureRandom().nextBytes(randomBytes)
        val encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE).trim()
        return "mcp_${encoded.replace("=", "")}"
    }

    /**
     * Retrieves saved relay host (e.g. xxxx.deno.dev).
     */
    fun getRelayHost(context: Context): String {
        return getPreferences(context).getString(KEY_RELAY_HOST, "") ?: ""
    }

    /**
     * Saves relay host.
     */
    fun saveRelayHost(context: Context, host: String) {
        val cleaned = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("wss://")
            .removePrefix("ws://")
            .trimEnd('/')
        getPreferences(context).edit().putString(KEY_RELAY_HOST, cleaned).apply()
    }

    /**
     * Retrieves saved relay secret.
     */
    fun getRelaySecret(context: Context): String {
        return getPreferences(context).getString(KEY_RELAY_SECRET, "") ?: ""
    }

    /**
     * Saves relay secret.
     */
    fun saveRelaySecret(context: Context, secret: String) {
        getPreferences(context).edit().putString(KEY_RELAY_SECRET, secret.trim()).apply()
    }
}
