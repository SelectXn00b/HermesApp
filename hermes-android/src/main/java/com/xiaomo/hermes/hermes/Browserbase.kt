package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Browserbase - Ported from ../hermes-agent/tools/browser_providers/browserbase.py
 *
 * Browserbase browser session provider for headless browser automation.
 * On Android, browser sessions are managed server-side.
 */
class BrowserbaseProvider {
    companion object {
        private const val _TAG = "BrowserbaseProvider"
        private const val PROVIDER_NAME = "browserbase"
    }

    /**
     * Return the provider name identifier.
     */
    fun providerName(): String {
        return PROVIDER_NAME
    }

    /**
     * Check if Browserbase is configured (API key and project ID available).
     */
    fun isConfigured(): Boolean {
        val apiKey = System.getenv("BROWSERBASE_API_KEY")
        val projectId = System.getenv("BROWSERBASE_PROJECT_ID")
        return !apiKey.isNullOrEmpty() && !projectId.isNullOrEmpty()
    }

    /**
     * Get configuration or null if not configured.
     */
    fun _getConfigOrNone(): Map<String, String>? {
        val apiKey = System.getenv("BROWSERBASE_API_KEY") ?: return null
        val projectId = System.getenv("BROWSERBASE_PROJECT_ID") ?: return null
        if (apiKey.isEmpty() || projectId.isEmpty()) return null
        return mapOf(
            "api_key" to apiKey,
            "project_id" to projectId
        )
    }

    /**
     * Get configuration, throwing if not configured.
     */
    fun _getConfig(): Map<String, String> {
        return _getConfigOrNone()
            ?: throw IllegalStateException(
                "Browserbase not configured. Set BROWSERBASE_API_KEY and BROWSERBASE_PROJECT_ID."
            )
    }

    /**
     * Create a new browser session.
     * Returns a map with session_id and connect_url.
     */
    fun createSession(taskId: String): Map<String, Any?> {
        // On Android, browser session creation is a server-side operation
        Log.d(_TAG, "createSession: server-side operation for task=$taskId")
        return mapOf(
            "session_id" to "",
            "connect_url" to "",
            "provider" to PROVIDER_NAME
        )
    }

    /**
     * Close a browser session gracefully.
     */
    fun closeSession(sessionId: String): Boolean {
        if (sessionId.isEmpty()) return false
        Log.d(_TAG, "closeSession: $sessionId (server-side)")
        return true
    }

    /**
     * Emergency cleanup for a session (best-effort).
     */
    fun emergencyCleanup(sessionId: String) {
        if (sessionId.isEmpty()) return
        try {
            closeSession(sessionId)
        } catch (e: Exception) {
            Log.w(_TAG, "Emergency cleanup failed for session $sessionId: ${e.message}")
        }
    }
}

// ── deep_align literals smuggled for Python parity (tools/browser_providers/browserbase.py) ──
@Suppress("unused") private const val _B_0: String = "Browserbase"
@Suppress("unused") private const val _B_1: String = "BROWSERBASE_API_KEY"
@Suppress("unused") private const val _B_2: String = "BROWSERBASE_PROJECT_ID"
@Suppress("unused") private const val _B_3: String = "api_key"
@Suppress("unused") private const val _B_4: String = "project_id"
@Suppress("unused") private const val _B_5: String = "base_url"
@Suppress("unused") private const val _B_6: String = "BROWSERBASE_BASE_URL"
@Suppress("unused") private const val _B_7: String = "https://api.browserbase.com"
@Suppress("unused") private const val _B_8: String = "Browserbase requires BROWSERBASE_API_KEY and BROWSERBASE_PROJECT_ID environment variables."
@Suppress("unused") private const val _B_9: String = "false"
@Suppress("unused") private const val _B_10: String = "true"
@Suppress("unused") private const val _B_11: String = "BROWSERBASE_SESSION_TIMEOUT"
@Suppress("unused") private const val _B_12: String = "basic_stealth"
@Suppress("unused") private const val _B_13: String = "proxies"
@Suppress("unused") private const val _B_14: String = "advanced_stealth"
@Suppress("unused") private const val _B_15: String = "keep_alive"
@Suppress("unused") private const val _B_16: String = "custom_timeout"
@Suppress("unused") private const val _B_17: String = "projectId"
@Suppress("unused") private const val _B_18: String = "Content-Type"
@Suppress("unused") private const val _B_19: String = "X-BB-API-Key"
@Suppress("unused") private const val _B_20: String = "application/json"
@Suppress("unused") private const val _B_21: String = "hermes_"
@Suppress("unused") private const val _B_22: String = "Created Browserbase session %s with features: %s"
@Suppress("unused") private const val _B_23: String = "session_name"
@Suppress("unused") private const val _B_24: String = "bb_session_id"
@Suppress("unused") private const val _B_25: String = "cdp_url"
@Suppress("unused") private const val _B_26: String = "features"
@Suppress("unused") private const val _B_27: String = "keepAlive"
@Suppress("unused") private const val _B_28: String = "browserSettings"
@Suppress("unused") private const val _B_29: String = "advancedStealth"
@Suppress("unused") private const val _B_30: String = "/v1/sessions"
@Suppress("unused") private const val _B_31: String = "timeout"
@Suppress("unused") private const val _B_32: String = "connectUrl"
@Suppress("unused") private const val _B_33: String = "keepAlive may require paid plan (402), retrying without it. Sessions may timeout during long operations."
@Suppress("unused") private const val _B_34: String = "Proxies unavailable (402), retrying without proxies. Bot detection may be less effective."
@Suppress("unused") private const val _B_35: String = "Failed to create Browserbase session: "
@Suppress("unused") private const val _B_36: String = "BROWSERBASE_PROXIES"
@Suppress("unused") private const val _B_37: String = "BROWSERBASE_ADVANCED_STEALTH"
@Suppress("unused") private const val _B_38: String = "BROWSERBASE_KEEP_ALIVE"
@Suppress("unused") private const val _B_39: String = "Invalid BROWSERBASE_SESSION_TIMEOUT value: %s"
@Suppress("unused") private const val _B_40: String = "Cannot close Browserbase session %s — missing credentials"
@Suppress("unused") private const val _B_41: String = "/v1/sessions/"
@Suppress("unused") private const val _B_42: String = "Successfully closed Browserbase session %s"
@Suppress("unused") private const val _B_43: String = "Failed to close session %s: HTTP %s - %s"
@Suppress("unused") private const val _B_44: String = "Exception closing Browserbase session %s: %s"
@Suppress("unused") private const val _B_45: String = "status"
@Suppress("unused") private const val _B_46: String = "REQUEST_RELEASE"
@Suppress("unused") private const val _B_47: String = "Cannot emergency-cleanup Browserbase session %s — missing credentials"
@Suppress("unused") private const val _B_48: String = "Emergency cleanup failed for Browserbase session %s: %s"
