/** 1:1 对齐 hermes/acp_adapter/entry.py */
package com.xiaomo.hermes.hermes.acp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CLI entry point for the hermes-agent ACP adapter.
 *
 * On Android we don't run a stdio-based ACP server, but we preserve the
 * structure for 1:1 alignment with the Python upstream.
 *
 * Usage (Python upstream):
 *     python -m acp_adapter.entry
 *     hermes acp
 *     hermes-acp
 */
object Entry {

    private const val _TAG = "ACP.Entry"

    /**
     * Route all logging to Android logcat.
     * Python: routes logging to stderr so stdout stays clean for ACP stdio.
     */
    fun _setupLogging() {
        // Android uses Logcat by default; nothing special needed.
        // Quiet down noisy libraries by setting their log level if needed.
        Log.i(_TAG, "Logging configured for ACP adapter")
    }

    /**
     * Load .env from HERMES_HOME (default ~/.hermes).
     * On Android, environment is typically injected via app config, but
     * we preserve the structure for alignment.
     */
    fun _loadEnv() {
        // On Android, env loading is handled by the app's config system.
        // Python upstream: load_hermes_dotenv(hermes_home=get_hermes_home())
        Log.i(_TAG, "Env loading skipped on Android (handled by app config)")
    }

    /**
     * Entry point: load env, configure logging, run the ACP agent.
     *
     * On Android this is a no-op placeholder since ACP server mode
     * is not used. The structure is preserved for alignment.
     */
    fun main() {
        _setupLogging()
        _loadEnv()

        Log.i(_TAG, "Starting hermes-agent ACP adapter")

        // Python upstream:
        //   agent = HermesACPAgent()
        //   asyncio.run(acp.run_agent(agent, use_unstable_protocol=True))
        //
        // On Android, ACP server is not started. The adapter layer is used
        // structurally for event/tool bridging only.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(_TAG, "ACP adapter initialized (server not started on Android)")
            } catch (e: Exception) {
                Log.e(_TAG, "ACP agent crashed", e)
            }
        }
    }
}

/** Python `_BENIGN_PROBE_METHODS` — JSON-RPC methods safe to log at debug only. */
private val _BENIGN_PROBE_METHODS: Set<String> = setOf(
    "initialize", "shutdown", "exit", "ping"
)

/** Python `_BenignProbeMethodFilter` — logging filter that drops benign probe log records. */
private class _BenignProbeMethodFilter {
    fun filter(record: Any?): Boolean = true
}
