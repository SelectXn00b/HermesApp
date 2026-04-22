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
