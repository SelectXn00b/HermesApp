package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Gateway restart helpers.
 *
 * Ported from gateway/restart.py
 */

/** Exit code that systemd / launchd / our own supervisor recognises as "restart requested". */
const val GATEWAY_SERVICE_RESTART_EXIT_CODE: Int = 42

/** Default drain timeout (seconds) — how long the gateway waits for in-flight sessions before restarting. */
const val DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT: Double = 30.0

/** Accepted suffices (case-insensitive) for human-readable duration strings. */
private val _SUFFIXES: Map<String, Double> = mapOf(
    "s" to 1.0,
    "sec" to 1.0,
    "second" to 1.0,
    "seconds" to 1.0,
    "m" to 60.0,
    "min" to 60.0,
    "minute" to 60.0,
    "minutes" to 60.0)

/**
 * Parse a drain timeout from a config/env value.
 *
 * Accepts plain integers (seconds) or suffixed strings like "30s", "2m".
 * Returns [DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT] on parse failure.
 */
fun parseRestartDrainTimeout(raw: Any?): Double {
    if (raw == null) return DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT

    val text = raw.toString().trim().lowercase()
    if (text.isEmpty()) return DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT

    // Plain number — treat as seconds
    text.toDoubleOrNull()?.let { n ->
        return if (n > 0.0) n else DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT
    }

    // Suffixed string
    for ((suffix, multiplier) in _SUFFIXES) {
        if (text.endsWith(suffix)) {
            val digits = text.removeSuffix(suffix).trim()
            digits.toDoubleOrNull()?.let { n ->
                if (n > 0.0) return n * multiplier
            }
        }
    }

    return DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT
}
