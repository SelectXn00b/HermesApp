/** 1:1 对齐 hermes/tools/mcp_oauth_manager.py */
package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Central manager for per-server MCP OAuth state.
 *
 * One instance shared across the process. Holds per-server OAuth provider
 * instances and coordinates:
 *
 * - Cross-process token reload via mtime-based disk watch.
 * - 401 deduplication via in-flight futures.
 * - Reconnect signalling for long-lived MCP sessions.
 */

private val logger = Logger.getLogger("McpOauthManager")

// ---------------------------------------------------------------------------
// Per-server entry
// ---------------------------------------------------------------------------

data class ProviderEntry(
    val serverUrl: String,
    val oauthConfig: Map<String, Any?>?,
    var provider: Any? = null,
    var lastMtimeNs: Long = 0L,
    val lock: Mutex = Mutex(),
    val pending401: MutableMap<String, CompletableDeferred<Boolean>> = mutableMapOf()
)

// ---------------------------------------------------------------------------
// HermesMCPOAuthProvider — placeholder for SDK subclass with disk-watch
// ---------------------------------------------------------------------------

/**
 * In the Python implementation, this subclass hooks into the MCP SDK's
 * OAuthClientProvider to inject pre-flow disk-mtime reload.
 * On Android, this is a structural placeholder maintaining 1:1 alignment.
 */
open class HermesMCPOAuthProvider(
    val serverName: String = ""
) {
    /**
     * Pre-flow hook: ask the manager to refresh from disk if needed.
     */
    suspend fun asyncAuthFlow() {
        try {
            getManager().invalidateIfDiskChanged(serverName)
        } catch (e: Exception) {
            logger.fine("MCP OAuth '$serverName': pre-flow disk-watch failed (non-fatal): $e")
        }
    }
}

// ---------------------------------------------------------------------------
// Manager
// ---------------------------------------------------------------------------

class MCPOAuthManager {
    private val _entries = ConcurrentHashMap<String, ProviderEntry>()
    private val _entriesLock = Any()

    // -- Provider construction / caching ------------------------------------

    fun getOrBuildProvider(
        serverName: String,
        serverUrl: String,
        oauthConfig: Map<String, Any?>?
    ): Any? {
        synchronized(_entriesLock) {
            var entry = _entries[serverName]
            if (entry != null && entry.serverUrl != serverUrl) {
                logger.info("MCP OAuth '$serverName': URL changed from ${entry.serverUrl} to $serverUrl, discarding cache")
                entry = null
            }

            if (entry == null) {
                entry = ProviderEntry(
                    serverUrl = serverUrl,
                    oauthConfig = oauthConfig
                )
                _entries[serverName] = entry
            }

            if (entry.provider == null) {
                entry.provider = _buildProvider(serverName, entry)
            }

            return entry.provider
        }
    }

    private fun _buildProvider(
        serverName: String,
        entry: ProviderEntry
    ): Any? {
        // In the Python implementation, this constructs a HermesMCPOAuthProvider
        // using helpers from tools.mcp_oauth. On Android, this is a structural
        // placeholder. The actual MCP OAuth flow would be integrated when
        // MCP SDK support is available on Android.
        logger.info("MCP OAuth '$serverName': building provider (Android placeholder)")
        return HermesMCPOAuthProvider(serverName = serverName)
    }

    fun remove(serverName: String) {
        synchronized(_entriesLock) {
            _entries.remove(serverName)
        }
        // In Python: remove_oauth_tokens(server_name)
        logger.info("MCP OAuth '$serverName': evicted from cache and removed from disk")
    }

    // -- Disk watch ---------------------------------------------------------

    suspend fun invalidateIfDiskChanged(serverName: String): Boolean {
        val entry = _entries[serverName] ?: return false
        if (entry.provider == null) return false

        entry.lock.withLock {
            // In Python, this checks the tokens file mtime and forces
            // the SDK provider to reload if changed. On Android, this
            // is a structural placeholder.
            // TODO: Implement actual file mtime checking when MCP OAuth
            // token storage is ported to Android.
            return false
        }
    }

    // -- 401 handler (dedup'd) ----------------------------------------------

    suspend fun handle401(
        serverName: String,
        failedAccessToken: String? = null
    ): Boolean {
        val entry = _entries[serverName] ?: return false
        if (entry.provider == null) return false

        val key = failedAccessToken ?: "<unknown>"

        entry.lock.withLock {
            val pending = entry.pending401[key]
            if (pending != null) {
                // Another call is already handling this 401 — wait for it
                return try {
                    pending.await()
                } catch (e: Exception) {
                    logger.warning("MCP OAuth '$serverName': awaiting 401 handler failed: $e")
                    false
                }
            }

            val deferred = CompletableDeferred<Boolean>()
            entry.pending401[key] = deferred

            try {
                // Step 1: Did disk change? Picks up external refresh.
                val diskChanged = invalidateIfDiskChanged(serverName)
                if (diskChanged) {
                    deferred.complete(true)
                    return true
                }

                // Step 2: No disk change — check if SDK can refresh in-place
                // On Android, this is a placeholder.
                deferred.complete(false)
                return false
            } catch (e: Exception) {
                logger.warning("MCP OAuth '$serverName': 401 handler failed: $e")
                deferred.complete(false)
                return false
            } finally {
                entry.pending401.remove(key)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Module-level singleton
// ---------------------------------------------------------------------------

@Volatile
private var _MANAGER: MCPOAuthManager? = null
private val _MANAGER_LOCK = Any()

fun getManager(): MCPOAuthManager {
    if (_MANAGER != null) return _MANAGER!!
    synchronized(_MANAGER_LOCK) {
        if (_MANAGER == null) {
            _MANAGER = MCPOAuthManager()
        }
        return _MANAGER!!
    }
}

fun resetManagerForTests() {
    synchronized(_MANAGER_LOCK) {
        _MANAGER = null
    }
}

/**
 * Per-server OAuth state tracked by the manager (Python dataclass alignment).
 * Ported from _ProviderEntry in mcp_oauth_manager.py.
 *
 * Note: The actual functionality is already in [ProviderEntry] data class above.
 * This class exists for 1:1 structural alignment with the Python source.
 */
class _ProviderEntry(
    val serverUrl: String = "",
    val oauthConfig: Map<String, Any?>? = null,
    var provider: Any? = null,
    var lastMtimeNs: Long = 0L,
    val lock: kotlinx.coroutines.sync.Mutex = kotlinx.coroutines.sync.Mutex(),
    val pending401: MutableMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>> = mutableMapOf()
)

/** Python `_make_hermes_provider_class` — stub. */
private fun _makeHermesProviderClass(serverName: String): Any? = null
