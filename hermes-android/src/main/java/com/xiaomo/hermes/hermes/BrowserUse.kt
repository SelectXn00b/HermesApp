package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Browser Use cloud browser provider.
 * Ported from browser_use.py (hermes-agent).
 *
 * NOTE: Android cannot run a headless browser locally. This module is a
 * stub that communicates with a remote Browser Use API when configured,
 * but all browser automation must happen server-side.
 */
class BrowserUseProvider {

    companion object {
        private const val _TAG = "BrowserUseProvider"
        private const val BASE_URL = "https://api.browser-use.com/api/v3"
        private const val DEFAULT_MANAGED_TIMEOUT_MINUTES = 5
        private const val DEFAULT_MANAGED_PROXY_COUNTRY_CODE = "us"
    }

    /** Return the provider name. */
    fun providerName(): String {
        return "Browser Use"
    }

    /** Check if the provider is configured (API key present). */
    fun isConfigured(): Boolean {
        return getConfigOrNone() != null
    }

    /**
     * Resolve config from environment or return null.
     * Returns a map with api_key, base_url, managed_mode.
     */
    fun getConfigOrNone(): Map<String, Any>? {
        val apiKey = System.getenv("BROWSER_USE_API_KEY")
        if (!apiKey.isNullOrEmpty()) {
            return mapOf(
                "api_key" to apiKey,
                "base_url" to BASE_URL,
                "managed_mode" to false)
        }
        // On Android, no managed gateway by default
        return null
    }

    /**
     * Resolve config or throw if not configured.
     */
    fun getConfig(): Map<String, Any> {
        return getConfigOrNone()
            ?: throw IllegalStateException(
                "Browser Use requires a BROWSER_USE_API_KEY environment variable."
            )
    }

    /**
     * Build HTTP headers for Browser Use API requests.
     */
    fun headers(config: Map<String, Any>): Map<String, String> {
        return mapOf(
            "Content-Type" to "application/json",
            "X-Browser-Use-API-Key" to (config["api_key"] as? String ?: ""))
    }

    /**
     * Create a browser session.
     * Returns a map with session_name, bb_session_id, cdp_url, features.
     */
    fun createSession(taskId: String): Map<String, Any> {
        val config = getConfig()
        val managedMode = config["managed_mode"] as? Boolean ?: false
        val sessionName = "hermes_${taskId}_${System.currentTimeMillis().toString(16).takeLast(8)}"

        // On Android we cannot actually create a browser session — log and return stub
        Log.w(_TAG, "createSession called but Android cannot run headless browsers. Session: $sessionName")

        return mapOf(
            "session_name" to sessionName,
            "bb_session_id" to "",
            "cdp_url" to "",
            "features" to mapOf("browser_use" to true),
            "error" to "Headless browser not supported on Android. Use remote API instead.")
    }

    /**
     * Close a browser session.
     */
    fun closeSession(sessionId: String): Boolean {
        Log.d(_TAG, "closeSession called for $sessionId — no-op on Android")
        return false
    }

    /**
     * Emergency cleanup for a browser session.
     */
    fun emergencyCleanup(sessionId: String) {
        Log.w(_TAG, "emergencyCleanup called for $sessionId — no-op on Android")
    }

    /** Alias: get config or null. */
    fun _getConfigOrNone(): Map<String, Any>? = getConfigOrNone()

    /** Alias: get config. */
    fun _getConfig(): Map<String, Any> = getConfig()

    /** Alias: build headers. */
    fun _headers(config: Map<String, Any>): Map<String, String> = headers(config)
}
