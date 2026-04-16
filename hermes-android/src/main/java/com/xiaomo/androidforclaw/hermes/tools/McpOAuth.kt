package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * MCP OAuth credential resolution.
 * Ported from mcp_oauth.py
 */
object McpOAuth {

    private const val TAG = "McpOAuth"
    private val gson = Gson()

    data class TokenSet(
        val accessToken: String = "",
        val tokenType: String = "Bearer",
        val expiresAt: String? = null)

    /**
     * Load OAuth token for a given MCP server name.
     * Returns the token string or null if not available.
     */
    fun loadToken(serverName: String, authFile: File? = null): String? {
        // 1. Environment variable override
        val envKey = "MCP_${serverName.uppercase().replace('-', '_')}_TOKEN"
        val envToken = System.getenv(envKey)
        if (!envToken.isNullOrBlank()) return envToken.trim()

        // 2. Auth file
        val file = authFile ?: getDefaultAuthFile()
        if (file?.exists() == true) {
            try {
                val json = gson.fromJson(file.readText(Charsets.UTF_8), Map::class.java) as Map<String, Any>
                val providers = json["providers"] as? Map<String, Any> ?: return null
                val entry = providers[serverName] as? Map<String, Any> ?: return null
                val accessToken = entry["access_token"] as? String
                if (!accessToken.isNullOrBlank()) return accessToken.trim()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load OAuth token for $serverName: ${e.message}")
            }
        }

        return null
    }

    private fun getDefaultAuthFile(): File? {
        val home = System.getProperty("user.home") ?: return null
        return File(home, ".hermes/auth.json").takeIf { it.exists() }
    }



    /** Sanitize server name for use as a filename. */
    private fun safeFileName(name: String): String {
        return name.replace(Regex("[^\\w\\-]"), "_").trim('_').take(128).ifEmpty { "default" }
    }

    /** Get the base token storage directory. */
    private fun getTokenDir(): File {
        val home = System.getProperty("user.home", "/tmp")
        return File(home, ".hermes/mcp-tokens")
    }

    fun _tokensPath(serverName: String): String {
        return File(getTokenDir(), "${safeFileName(serverName)}.json").absolutePath
    }
    fun _clientInfoPath(serverName: String): String {
        return File(getTokenDir(), "${safeFileName(serverName)}.client.json").absolutePath
    }
    suspend fun getTokens(): Any? {
        return null
    }
    suspend fun setTokens(serverName: String, tokens: Any?){ /* void */ }
    suspend fun setClientInfo(serverName: String, clientInfo: Any?): Unit {
        val path = File(_clientInfoPath(serverName))
        path.parentFile?.mkdirs()
        val jsonStr = when (clientInfo) {
            is String -> clientInfo
            is Map<*, *> -> gson.toJson(clientInfo)
            else -> gson.toJson(clientInfo)
        }
        val tmp = File(path.parent, "${path.name}.tmp")
        try {
            tmp.writeText(jsonStr, Charsets.UTF_8)
            tmp.renameTo(path)
            Log.d(TAG, "OAuth client info saved for $serverName")
        } catch (e: Exception) {
            tmp.delete()
            Log.e(TAG, "Failed to save client info: ${e.message}")
        }
    }
    /** Delete all stored OAuth state for this server. */
    fun remove(serverName: String): Unit {
        val tokensPath = File(_tokensPath(serverName))
        val clientInfoPath = File(_clientInfoPath(serverName))
        tokensPath.delete()
        clientInfoPath.delete()
        Log.d(TAG, "OAuth tokens removed for '$serverName'")
    }
    /** Return True if we have tokens on disk (may be expired). */
    fun hasCachedTokens(serverName: String): Boolean {
        return File(_tokensPath(serverName)).exists()
    }

}
