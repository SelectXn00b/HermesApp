package com.xiaomo.hermes.hermes.acp

/**
 * ACP auth helpers — detect the currently configured Hermes provider.
 *
 * Ported from acp_adapter/auth.py
 */

/** Resolve the active Hermes runtime provider, or null if unavailable. */
fun detectProvider(): String? {
    return try {
        val runtime = _resolveRuntimeProvider() ?: return null
        val apiKey = runtime["api_key"] as? String
        val provider = runtime["provider"] as? String
        if (!apiKey.isNullOrBlank() && !provider.isNullOrBlank()) {
            provider.trim().lowercase()
        } else null
    } catch (_: Exception) {
        null
    }
}

/** Return True if Hermes can resolve any runtime provider credentials. */
fun hasProvider(): Boolean = detectProvider() != null

private fun _resolveRuntimeProvider(): Map<String, Any?>? {
    // TODO: port hermes_cli.runtime_provider.resolve_runtime_provider
    return null
}
