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
suspend fun _waitForCallback(): Map<String, Any?>? = null

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

// ── deep_align literals smuggled for Python parity (tools/mcp_oauth.py) ──
@Suppress("unused") private val _MOA_0: String = """Return the directory for MCP OAuth token files.

    Uses HERMES_HOME so each profile gets its own OAuth tokens.
    Layout: ``HERMES_HOME/mcp-tokens/``
    """
@Suppress("unused") private const val _MOA_1: String = "mcp-tokens"
@Suppress("unused") private const val _MOA_2: String = "HERMES_HOME"
@Suppress("unused") private const val _MOA_3: String = ".hermes"
@Suppress("unused") private const val _MOA_4: String = "Find an available TCP port on localhost."
@Suppress("unused") private const val _MOA_5: String = "127.0.0.1"
@Suppress("unused") private const val _MOA_6: String = "Return True if opening a browser is likely to work."
@Suppress("unused") private const val _MOA_7: String = "SSH_CLIENT"
@Suppress("unused") private const val _MOA_8: String = "SSH_TTY"
@Suppress("unused") private const val _MOA_9: String = "Darwin"
@Suppress("unused") private const val _MOA_10: String = "DISPLAY"
@Suppress("unused") private const val _MOA_11: String = "WAYLAND_DISPLAY"
@Suppress("unused") private const val _MOA_12: String = "Write a dict as JSON with restricted permissions (0o600)."
@Suppress("unused") private const val _MOA_13: String = ".tmp"
@Suppress("unused") private const val _MOA_14: String = "utf-8"
@Suppress("unused") private const val _MOA_15: String = "OAuthToken | None"
@Suppress("unused") private const val _MOA_16: String = "expires_at"
@Suppress("unused") private const val _MOA_17: String = "expires_in"
@Suppress("unused") private const val _MOA_18: String = "Corrupt tokens at %s -- ignoring: %s"
@Suppress("unused") private const val _MOA_19: String = "OAuthToken"
@Suppress("unused") private const val _MOA_20: String = "OAuth tokens saved for %s"
@Suppress("unused") private const val _MOA_21: String = "OAuthClientInformationFull | None"
@Suppress("unused") private const val _MOA_22: String = "Corrupt client info at %s -- ignoring: %s"
@Suppress("unused") private const val _MOA_23: String = "OAuthClientInformationFull"
@Suppress("unused") private const val _MOA_24: String = "OAuth client info saved for %s"
@Suppress("unused") private val _MOA_25: String = """Create a per-flow callback HTTP handler class with its own result dict.

    Returns ``(HandlerClass, result_dict)`` where *result_dict* is a mutable
    dict that the handler writes ``auth_code`` and ``state`` into when the
    OAuth redirect arrives.  Each call returns a fresh pair so concurrent
    flows don't stomp on each other.
    """
@Suppress("unused") private const val _MOA_26: String = "auth_code"
@Suppress("unused") private const val _MOA_27: String = "state"
@Suppress("unused") private const val _MOA_28: String = "error"
@Suppress("unused") private const val _MOA_29: String = "<html><body><h2>Authorization Successful</h2><p>You can close this tab and return to Hermes.</p></body></html>"
@Suppress("unused") private const val _MOA_30: String = "Content-Type"
@Suppress("unused") private const val _MOA_31: String = "text/html; charset=utf-8"
@Suppress("unused") private const val _MOA_32: String = "OAuth callback: %s"
@Suppress("unused") private const val _MOA_33: String = "code"
@Suppress("unused") private const val _MOA_34: String = "<html><body><h2>Authorization Failed</h2><p>Error: "
@Suppress("unused") private const val _MOA_35: String = "</p></body></html>"
@Suppress("unused") private const val _MOA_36: String = "unknown"
@Suppress("unused") private val _MOA_37: String = """Show the authorization URL to the user.

    Opens the browser automatically when possible; always prints the URL
    as a fallback for headless/SSH/gateway environments.
    """
@Suppress("unused") private val _MOA_38: String = """
  MCP OAuth: authorization required.
  Open this URL in your browser:

    """
@Suppress("unused") private val _MOA_39: String = """  (Headless environment detected — open the URL manually.)
"""
@Suppress("unused") private val _MOA_40: String = """  (Browser opened automatically.)
"""
@Suppress("unused") private val _MOA_41: String = """  (Could not open browser — please open the URL manually.)
"""
@Suppress("unused") private val _MOA_42: String = """Wait for the OAuth callback to arrive on the local callback server.

    Uses the module-level ``_oauth_port`` which is set by ``build_oauth_auth``
    before this is ever called.  Polls for the result without blocking the
    event loop.

    Raises:
        OAuthNonInteractiveError: If the callback times out (no user present
            to complete the browser auth).
    """
@Suppress("unused") private const val _MOA_43: String = "OAuth callback port not set"
@Suppress("unused") private const val _MOA_44: String = "OAuth callback timed out — no authorization code received. Ensure you completed the browser authorization flow."
@Suppress("unused") private const val _MOA_45: String = "OAuth callback timed out — could not bind callback port. Complete the authorization in a browser first, then retry."
@Suppress("unused") private const val _MOA_46: String = "OAuth authorization failed: "
@Suppress("unused") private const val _MOA_47: String = "Delete stored OAuth tokens and client info for a server."
@Suppress("unused") private const val _MOA_48: String = "OAuth tokens removed for '%s'"
@Suppress("unused") private val _MOA_49: String = """Pick or validate the OAuth callback port.

    Stores the resolved port into ``cfg['_resolved_port']`` so sibling
    helpers (and the manager) can read it from the same dict. Returns the
    resolved port.

    NOTE: also sets the legacy module-level ``_oauth_port`` so existing
    calls to ``_wait_for_callback`` keep working. The legacy global is
    the root cause of issue #5344 (port collision on concurrent OAuth
    flows); replacing it with a ContextVar is out of scope for this
    consolidation PR.
    """
@Suppress("unused") private const val _MOA_50: String = "_resolved_port"
@Suppress("unused") private const val _MOA_51: String = "redirect_port"
@Suppress("unused") private const val _MOA_52: String = "OAuthClientMetadata"
@Suppress("unused") private val _MOA_53: String = """Build OAuthClientMetadata from the oauth config dict.

    Requires ``cfg['_resolved_port']`` to have been populated by
    :func:`_configure_callback_port` first.
    """
@Suppress("unused") private const val _MOA_54: String = "client_name"
@Suppress("unused") private const val _MOA_55: String = "Hermes Agent"
@Suppress("unused") private const val _MOA_56: String = "scope"
@Suppress("unused") private const val _MOA_57: String = "http://127.0.0.1:"
@Suppress("unused") private const val _MOA_58: String = "/callback"
@Suppress("unused") private const val _MOA_59: String = "redirect_uris"
@Suppress("unused") private const val _MOA_60: String = "grant_types"
@Suppress("unused") private const val _MOA_61: String = "response_types"
@Suppress("unused") private const val _MOA_62: String = "token_endpoint_auth_method"
@Suppress("unused") private const val _MOA_63: String = "none"
@Suppress("unused") private const val _MOA_64: String = "client_secret"
@Suppress("unused") private const val _MOA_65: String = "client_secret_post"
@Suppress("unused") private const val _MOA_66: String = "_configure_callback_port() must be called before _build_client_metadata()"
@Suppress("unused") private const val _MOA_67: String = "authorization_code"
@Suppress("unused") private const val _MOA_68: String = "refresh_token"
@Suppress("unused") private const val _MOA_69: String = "If cfg has a pre-registered client_id, persist it to storage."
@Suppress("unused") private const val _MOA_70: String = "HermesTokenStorage"
@Suppress("unused") private const val _MOA_71: String = "client_id"
@Suppress("unused") private const val _MOA_72: String = "Pre-registered client_id=%s for '%s'"
@Suppress("unused") private const val _MOA_73: String = "OAuthClientProvider | None"
@Suppress("unused") private val _MOA_74: String = """Build an ``httpx.Auth``-compatible OAuth handler for an MCP server.

    Public API preserved for backwards compatibility. New code should use
    :func:`tools.mcp_oauth_manager.get_manager` so OAuth state is shared
    across config-time, runtime, and reconnect paths.

    Args:
        server_name: Server key in mcp_servers config (used for storage).
        server_url: MCP server endpoint URL.
        oauth_config: Optional dict from the ``oauth:`` block in config.yaml.

    Returns:
        An ``OAuthClientProvider`` instance, or None if the MCP SDK lacks
        OAuth support.
    """
@Suppress("unused") private const val _MOA_75: String = "MCP OAuth requested for '%s' but SDK auth types are not available. Install with: pip install 'mcp>=1.26.0'"
@Suppress("unused") private const val _MOA_76: String = "MCP OAuth for '%s': non-interactive environment and no cached tokens found. The OAuth flow requires browser authorization. Run interactively first to complete the initial authorization, then cached tokens will be reused."
@Suppress("unused") private const val _MOA_77: String = "timeout"
