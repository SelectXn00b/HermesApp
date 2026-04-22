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
        private const val TAG = "BrowserbaseProvider"
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
        Log.d(TAG, "createSession: server-side operation for task=$taskId")
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
        Log.d(TAG, "closeSession: $sessionId (server-side)")
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
            Log.w(TAG, "Emergency cleanup failed for session $sessionId: ${e.message}")
        }
    }
}
