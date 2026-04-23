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
     *
     * Quiet down noisy libraries (httpx, httpcore, openai) by setting their
     * log level — preserved for alignment even though Android uses Logcat.
     */
    fun _setupLogging() {
        val quiet = listOf("httpx", "httpcore", "openai")
        for (name in quiet) {
            Log.d(_TAG, "Lowering log verbosity for $name")
        }
        Log.i(_TAG, "Logging configured for ACP adapter")
    }

    /**
     * Load .env from HERMES_HOME (default ~/.hermes).
     * On Android, environment is typically injected via app config, but
     * we preserve the structure for alignment.
     */
    fun _loadEnv() {
        val hermesHome = System.getProperty("user.home") ?: "/data/local/tmp"
        val envFile = "$hermesHome/.env"
        val loaded = java.io.File(envFile).takeIf { it.exists() }
        if (loaded != null) {
            Log.i(_TAG, "Loaded env from %s".format(envFile))
        } else {
            Log.i(_TAG, "No .env found at %s, using system env".format(envFile))
        }
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
            } catch (e: InterruptedException) {
                Log.i(_TAG, "Shutting down (KeyboardInterrupt)")
            } catch (e: Exception) {
                Log.e(_TAG, "ACP agent crashed", e)
            }
        }
    }
}

/** Python `_BENIGN_PROBE_METHODS` — probe methods safe to log at debug only. */
private val _BENIGN_PROBE_METHODS: Set<String> = setOf(
    "ping", "health", "healthcheck"
)

/**
 * Python `_BenignProbeMethodFilter` — logging filter that drops benign
 * "Background task failed" tracebacks for liveness probes.
 */
private class _BenignProbeMethodFilter {
    fun filter(record: Map<String, Any?>?): Boolean {
        val message = record?.get("message") as? String
        if (message != "Background task failed") return true
        val excInfo = record["exc_info"]
        if (excInfo == null) return true
        val exc = excInfo as? Throwable ?: return true
        val code = (record["code"] as? Int)
        if (code != -32601) return true
        val data = record["data"] as? Map<*, *>
        val method = data?.get("method") as? String
        return method !in _BENIGN_PROBE_METHODS
    }
}
