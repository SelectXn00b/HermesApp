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

// ── Module-level symbols (1:1 with tools/mcp_oauth.py) ────────────────

/** True when the Python MCP OAuth SDK was importable. Always false on Android. */
const val _OAUTH_AVAILABLE: Boolean = false

/** Locate an unused TCP port for the OAuth redirect server. */
fun _findFreePort(): Int {
    return try {
        val s = java.net.ServerSocket(0)
        val port = s.localPort
        s.close()
        port
    } catch (_: Exception) {
        0
    }
}

/** True when stdin is a TTY — Android always returns false. */
fun _isInteractive(): Boolean = false

/** True when a browser can be opened for the OAuth flow. */
fun _canOpenBrowser(): Boolean = false

/** Read a JSON file into a Map, returning null if missing/invalid. */
fun _readJson(path: File?): Map<String, Any?>? {
    if (path == null || !path.isFile) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        _gson.fromJson(path.readText(Charsets.UTF_8), Map::class.java) as? Map<String, Any?>
    } catch (_: Throwable) {
        null
    }
}

/** Write a JSON-serializable map to disk. */
fun _writeJson(path: File?, data: Map<String, Any?>?) {
    if (path == null) return
    try {
        path.parentFile?.mkdirs()
        path.writeText(_gson.toJson(data ?: emptyMap<String, Any?>()), Charsets.UTF_8)
    } catch (_: Throwable) {
        /* best-effort persistence */
    }
}

/** Build the HTTP handler that receives the OAuth callback. Android: null. */
fun _makeCallbackHandler(): Pair<Any?, MutableMap<String, Any?>> = null to mutableMapOf()

/** Coroutine-friendly redirect handler; no-op on Android. */
suspend fun _redirectHandler(authUrl: String) { /* Android stub */ }

/** Wait for the OAuth callback to arrive. Android: returns null immediately. */
suspend fun _waitForCallback(
    state: MutableMap<String, Any?>,
    timeoutSec: Int = 300
): Map<String, Any?>? = null

/** Resolve the redirect callback port from config (default: find free port). */
fun _configureCallbackPort(cfg: Map<String, Any?>?): Int {
    val configured = (cfg?.get("callback_port") as? Number)?.toInt()
    return configured ?: _findFreePort()
}

/** Build the MCP OAuth client metadata payload. */
fun _buildClientMetadata(cfg: Map<String, Any?>?): Map<String, Any?> {
    val c = cfg ?: emptyMap()
    return mapOf(
        "client_name" to (c["client_name"] ?: "Hermes"),
        "redirect_uris" to listOf(c["redirect_uri"] ?: "http://localhost/callback"),
        "grant_types" to listOf("authorization_code", "refresh_token"),
        "response_types" to listOf("code"),
        "token_endpoint_auth_method" to "none",
    )
}

/** Pre-register the client with the MCP authorization server. Android: null. */
fun _maybePreregisterClient(
    serverName: String,
    authorizationServer: String,
    metadata: Map<String, Any?>
): Map<String, Any?>? = null

/** Derive the base URL for an MCP server from its advertised URL. */
fun _parseBaseUrl(serverUrl: String): String {
    if (serverUrl.isBlank()) return ""
    return try {
        val u = java.net.URI(serverUrl)
        val scheme = u.scheme ?: "https"
        val host = u.host ?: return serverUrl
        val port = if (u.port > 0) ":${u.port}" else ""
        "$scheme://$host$port"
    } catch (_: Throwable) {
        serverUrl
    }
}

/** Build an OAuth authenticator for an MCP server. Android: returns null. */
fun buildOauthAuth(
    serverName: String,
    cfg: Map<String, Any?>?,
    interactive: Boolean = true
): Any? = null
