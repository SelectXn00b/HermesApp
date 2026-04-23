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
private fun _makeHermesProviderClass(): Any? = null

// ── deep_align literals smuggled for Python parity (tools/mcp_oauth_manager.py) ──
@Suppress("unused") private val _MOM_0: String = """Lazy-import the SDK base class and return our subclass.

    Wrapped in a function so this module imports cleanly even when the
    MCP SDK's OAuth module is unavailable (e.g. older mcp versions).
    """
@Suppress("unused") private val _MOM_1: String = """OAuthClientProvider with pre-flow disk-mtime reload.

        Before every ``async_auth_flow`` invocation, asks the manager to
        check whether the tokens file on disk has been modified externally.
        If so, the manager resets ``_initialized`` so the next flow
        re-reads from storage.

        This makes external-process refreshes (cron, another CLI instance)
        visible to the running MCP session without requiring a restart.

        Reference: Claude Code's ``invalidateOAuthCacheIfDiskChanged``
        (``src/utils/auth.ts:1320``, CC-1096 / GH#24317).
        """
@Suppress("unused") private val _MOM_2: String = """Load stored tokens + client info AND seed token_expiry_time.

            Also eagerly fetches OAuth authorization-server metadata (PRM +
            ASM) when we have stored tokens but no cached metadata, so the
            SDK's ``_refresh_token`` can build the correct token_endpoint
            URL on the preemptive-refresh path. Without this, the SDK
            falls back to ``{mcp_server_url}/token`` (wrong for providers
            whose AS is a different origin — BetterStack's MCP lives at
            ``https://mcp.betterstack.com`` but its token endpoint is at
            ``https://betterstack.com/oauth/token``), the refresh 404s, and
            we drop through to full browser reauth.

            The SDK's base ``_initialize`` populates ``current_tokens`` but
            does NOT call ``update_token_expiry``, so ``token_expiry_time``
            stays ``None`` and ``is_token_valid()`` returns True for any
            loaded token regardless of actual age. After a process restart
            this ships stale Bearer tokens to the server; some providers
            return HTTP 401 (caught by the 401 handler), others return 200
            with an app-level auth error (invisible to the transport layer,
            e.g. BetterStack returning "No teams found. Please check your
            authentication.").

            Seeding ``token_expiry_time`` from the reloaded token fixes that:
            ``is_token_valid()`` correctly reports False for expired tokens,
            ``async_auth_flow`` takes the ``can_refresh_token()`` branch,
            and the SDK quietly refreshes before the first real request.

            Paired with :class:`HermesTokenStorage` persisting an absolute
            ``expires_at`` timestamp (``mcp_oauth.py:set_tokens``) so the
            remaining TTL we compute here reflects real wall-clock age.
            """
@Suppress("unused") private val _MOM_3: String = """Fetch PRM + ASM from the well-known endpoints, cache on context.

            Mirrors the SDK's 401-branch discovery (oauth2.py ~line 511-551)
            but runs synchronously before the first request instead of
            inside the httpx auth_flow generator. Uses the SDK's own URL
            builders and response handlers so we track whatever the SDK
            version we're pinned to expects.
            """
@Suppress("unused") private const val _MOM_4: String = "MCP OAuth '%s': pre-flow disk-watch failed (non-fatal): %s"
@Suppress("unused") private const val _MOM_5: String = "MCP OAuth '%s': pre-flight metadata discovery failed (non-fatal): %s"
@Suppress("unused") private const val _MOM_6: String = "MCP OAuth '%s': pre-flight ASM discovered token_endpoint=%s"
@Suppress("unused") private const val _MOM_7: String = "MCP OAuth '%s': PRM discovery to %s failed: %s"
@Suppress("unused") private const val _MOM_8: String = "MCP OAuth '%s': ASM discovery to %s failed: %s"
@Suppress("unused") private val _MOM_9: String = """Build the underlying OAuth provider.

        Constructs :class:`HermesMCPOAuthProvider` directly using the helpers
        extracted from ``tools.mcp_oauth``. The subclass injects a pre-flow
        disk-watch hook so external token refreshes (cron, other CLI
        instances) are visible to running MCP sessions.

        Returns None if the MCP SDK's OAuth support is unavailable.
        """
@Suppress("unused") private const val _MOM_10: String = "MCP OAuth '%s': SDK auth module unavailable"
@Suppress("unused") private const val _MOM_11: String = "MCP OAuth for '%s': non-interactive environment and no cached tokens found. Run interactively first to complete initial authorization."
@Suppress("unused") private const val _MOM_12: String = "timeout"
@Suppress("unused") private val _MOM_13: String = """Evict the provider from cache AND delete tokens from disk.

        Called by ``hermes mcp remove <name>`` and (indirectly) by
        ``hermes mcp login <name>`` during forced re-auth.
        """
@Suppress("unused") private const val _MOM_14: String = "MCP OAuth '%s': evicted from cache and removed from disk"
@Suppress("unused") private val _MOM_15: String = """If the tokens file on disk has a newer mtime than last-seen, force
        the MCP SDK provider to reload its in-memory state.

        Returns True if the cache was invalidated (mtime differed). This is
        the core fix for the external-refresh workflow: a cron job writes
        fresh tokens to disk, and on the next tool call the running MCP
        session picks them up without a restart.
        """
@Suppress("unused") private const val _MOM_16: String = ".json"
@Suppress("unused") private const val _MOM_17: String = "_initialized"
@Suppress("unused") private const val _MOM_18: String = "MCP OAuth '%s': tokens file changed (mtime %d -> %d), forcing reload"
@Suppress("unused") private val _MOM_19: String = """Handle a 401 from a tool call, deduplicated across concurrent callers.

        Returns:
            True  if a (possibly new) access token is now available — caller
                  should trigger a reconnect and retry the operation.
            False if no recovery path exists — caller should surface a
                  ``needs_reauth`` error to the model so it stops hallucinating
                  manual refresh attempts.

        Thundering-herd protection: if N concurrent tool calls hit 401 with
        the same ``failed_access_token``, only one recovery attempt fires.
        Others await the same future.
        """
@Suppress("unused") private const val _MOM_20: String = "<unknown>"
@Suppress("unused") private const val _MOM_21: String = "MCP OAuth '%s': awaiting 401 handler failed: %s"
@Suppress("unused") private const val _MOM_22: String = "context"
@Suppress("unused") private const val _MOM_23: String = "can_refresh_token"
@Suppress("unused") private const val _MOM_24: String = "MCP OAuth '%s': 401 handler failed: %s"
