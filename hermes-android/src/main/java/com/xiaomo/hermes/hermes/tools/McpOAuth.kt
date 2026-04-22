package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * MCP OAuth 2.1 Client Support — credential persistence.
 * Ported from mcp_oauth.py (partial stub — browser-based OAuth flow pending).
 */

private val _gson = Gson()
private const val _TAG = "McpOAuth"

/** Return the base directory for persisted MCP tokens. */
fun _getTokenDir(): File {
    val home = System.getProperty("user.home", "/tmp")
    return File(home, ".hermes/mcp-tokens")
}

/** Sanitize server name for use as a filename. */
fun _safeFilename(name: String): String {
    return name.replace(Regex("[^\\w\\-]"), "_").trim('_').take(128).ifEmpty { "default" }
}

/**
 * Raised when OAuth requires browser interaction in a non-interactive env.
 * Ported from OAuthNonInteractiveError in mcp_oauth.py.
 */
class OAuthNonInteractiveError(message: String = "OAuth requires browser interaction in a non-interactive environment") : RuntimeException(message)

/**
 * Persist OAuth tokens and client registration to JSON files.
 * Ported from HermesTokenStorage in mcp_oauth.py.
 *
 * File layout:
 *   HERMES_HOME/mcp-tokens/<server_name>.json         -- tokens
 *   HERMES_HOME/mcp-tokens/<server_name>.client.json  -- client info
 */
class HermesTokenStorage(serverName: String) {
    private val _serverName: String = _safeFilename(serverName)

    fun _tokensPath(): File = File(_getTokenDir(), "${_serverName}.json")

    fun _clientInfoPath(): File = File(_getTokenDir(), "${_serverName}.client.json")

    // -- tokens --------------------------------------------------------------

    suspend fun getTokens(): Any? {
        val path = _tokensPath()
        if (!path.exists()) return null
        return try {
            _gson.fromJson(path.readText(Charsets.UTF_8), Map::class.java)
        } catch (e: Exception) {
            Log.w(_TAG, "Corrupt tokens at ${path} -- ignoring: ${e.message}")
            null
        }
    }

    suspend fun setTokens(tokens: Any?) {
        val path = _tokensPath()
        path.parentFile?.mkdirs()
        val jsonStr = when (tokens) {
            is String -> tokens
            else -> _gson.toJson(tokens)
        }
        val tmp = File(path.parent, "${path.name}.tmp")
        try {
            tmp.writeText(jsonStr, Charsets.UTF_8)
            tmp.renameTo(path)
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    // -- client info ---------------------------------------------------------

    suspend fun getClientInfo(): Any? {
        val path = _clientInfoPath()
        if (!path.exists()) return null
        return try {
            _gson.fromJson(path.readText(Charsets.UTF_8), Map::class.java)
        } catch (e: Exception) {
            Log.w(_TAG, "Corrupt client info at ${path} -- ignoring: ${e.message}")
            null
        }
    }

    suspend fun setClientInfo(clientInfo: Any?) {
        val path = _clientInfoPath()
        path.parentFile?.mkdirs()
        val jsonStr = when (clientInfo) {
            is String -> clientInfo
            else -> _gson.toJson(clientInfo)
        }
        val tmp = File(path.parent, "${path.name}.tmp")
        try {
            tmp.writeText(jsonStr, Charsets.UTF_8)
            tmp.renameTo(path)
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    // -- cleanup -------------------------------------------------------------

    fun remove() {
        _tokensPath().delete()
        _clientInfoPath().delete()
    }

    fun hasCachedTokens(): Boolean = _tokensPath().exists()
}

/** Delete all stored OAuth state for a server. */
fun removeOauthTokens(serverName: String) {
    HermesTokenStorage(serverName).remove()
}
