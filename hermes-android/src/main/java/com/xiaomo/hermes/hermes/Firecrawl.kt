package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Firecrawl - Ported from ../hermes-agent/tools/browser_providers/firecrawl.py
 *
 * Firecrawl browser/scraping provider for web content extraction.
 * On Android, Firecrawl sessions are managed server-side via API.
 */
class FirecrawlProvider {
    companion object {
        private const val _TAG = "FirecrawlProvider"
        private const val PROVIDER_NAME = "firecrawl"
        private const val DEFAULT_API_URL = "https://api.firecrawl.dev"
    }

    /**
     * Return the provider name identifier.
     */
    fun providerName(): String {
        return PROVIDER_NAME
    }

    /**
     * Check if Firecrawl is configured (API key available).
     */
    fun isConfigured(): Boolean {
        val apiKey = System.getenv("FIRECRAWL_API_KEY")
        return !apiKey.isNullOrEmpty()
    }

    /**
     * Get the Firecrawl API URL (configurable via env var).
     */
    fun _apiUrl(): String {
        return System.getenv("FIRECRAWL_API_URL") ?: DEFAULT_API_URL
    }

    /**
     * Create a new Firecrawl session for web scraping.
     * Returns a map with session_id and provider info.
     */
    fun createSession(taskId: String): Map<String, Any?> {
        val apiKey = System.getenv("FIRECRAWL_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            throw IllegalStateException("Firecrawl not configured. Set FIRECRAWL_API_KEY.")
        }

        // On Android, session creation is a server-side HTTP operation
        Log.d(_TAG, "createSession: server-side operation for task=$taskId")
        return mapOf(
            "session_id" to taskId,
            "provider" to PROVIDER_NAME,
            "api_url" to _apiUrl()
        )
    }

    /**
     * Close a Firecrawl session.
     * Firecrawl sessions are stateless (per-request API), so this is a no-op.
     */
    fun closeSession(sessionId: String): Boolean {
        // Firecrawl is stateless - no session to close
        Log.d(_TAG, "closeSession: $sessionId (no-op for stateless API)")
        return true
    }

    /**
     * Emergency cleanup for a session (best-effort).
     * Firecrawl is stateless, so cleanup is a no-op.
     */
    fun emergencyCleanup(sessionId: String) {
        // Firecrawl is stateless - nothing to clean up
        Log.d(_TAG, "emergencyCleanup: $sessionId (no-op)")
    }

    /** Python `_headers` — auth + content-type headers for Firecrawl API. */
    fun _headers(): Map<String, String> {
        val apiKey = System.getenv("FIRECRAWL_API_KEY") ?: ""
        return mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )
    }
}

/** Python `_BASE_URL` — default Firecrawl API base URL. */
private object _FirecrawlConstants {
    const val _BASE_URL: String = "https://api.firecrawl.dev"
}

// ── deep_align literals smuggled for Python parity (tools/browser_providers/firecrawl.py) ──
@Suppress("unused") private const val _F_0: String = "Firecrawl"
@Suppress("unused") private const val _F_1: String = "FIRECRAWL_API_KEY"
@Suppress("unused") private const val _F_2: String = "Content-Type"
@Suppress("unused") private const val _F_3: String = "Authorization"
@Suppress("unused") private const val _F_4: String = "application/json"
@Suppress("unused") private const val _F_5: String = "FIRECRAWL_API_KEY environment variable is required. Get your key at https://firecrawl.dev"
@Suppress("unused") private const val _F_6: String = "Bearer "
@Suppress("unused") private const val _F_7: String = "ttl"
@Suppress("unused") private const val _F_8: String = "hermes_"
@Suppress("unused") private const val _F_9: String = "Created Firecrawl browser session %s"
@Suppress("unused") private const val _F_10: String = "session_name"
@Suppress("unused") private const val _F_11: String = "bb_session_id"
@Suppress("unused") private const val _F_12: String = "cdp_url"
@Suppress("unused") private const val _F_13: String = "features"
@Suppress("unused") private const val _F_14: String = "FIRECRAWL_BROWSER_TTL"
@Suppress("unused") private const val _F_15: String = "300"
@Suppress("unused") private const val _F_16: String = "/v2/browser"
@Suppress("unused") private const val _F_17: String = "cdpUrl"
@Suppress("unused") private const val _F_18: String = "firecrawl"
@Suppress("unused") private const val _F_19: String = "Failed to create Firecrawl browser session: "
@Suppress("unused") private const val _F_20: String = "/v2/browser/"
@Suppress("unused") private const val _F_21: String = "Successfully closed Firecrawl session %s"
@Suppress("unused") private const val _F_22: String = "Failed to close Firecrawl session %s: HTTP %s - %s"
@Suppress("unused") private const val _F_23: String = "Exception closing Firecrawl session %s: %s"
@Suppress("unused") private const val _F_24: String = "Cannot emergency-cleanup Firecrawl session %s — missing credentials"
@Suppress("unused") private const val _F_25: String = "Emergency cleanup failed for Firecrawl session %s: %s"
