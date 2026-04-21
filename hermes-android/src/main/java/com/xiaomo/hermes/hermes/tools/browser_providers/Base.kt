package com.xiaomo.hermes.hermes.tools.browser_providers

/**
 * Interface for cloud browser backends (Browserbase, Steel, etc.).
 * Ported from tools/browser_providers/base.py
 *
 * Implementations live in sibling modules. The user selects a provider via
 * config; the choice is persisted as config["browser"]["cloud_provider"].
 *
 * On Android, cloud browser providers are not directly supported, so this
 * serves as a structural placeholder with no-op defaults.
 */
abstract class CloudBrowserProvider {
    /**
     * Short, human-readable name shown in logs and diagnostics.
     */
    open fun providerName(): String {
        return "unknown"
    }

    /**
     * Return true when all required env vars / credentials are present.
     * Called at tool-registration time to gate availability. Must be cheap.
     */
    open fun isConfigured(): Boolean {
        return false
    }

    /**
     * Create a cloud browser session and return session metadata.
     * Must return a map with at least: session_name, bb_session_id, cdp_url, features.
     * On Android, cloud browser sessions are not supported.
     */
    open fun createSession(taskId: String): Map<String, Any?> {
        return mapOf(
            "error" to "Cloud browser sessions are not supported on Android"
        )
    }

    /**
     * Release / terminate a cloud session by its provider session ID.
     * Returns true on success, false on failure. Should not raise.
     */
    open fun closeSession(sessionId: String): Boolean {
        return false
    }

    /**
     * Best-effort session teardown during process exit.
     * Called from exit handlers. Must tolerate missing credentials, network errors.
     */
    open fun emergencyCleanup(sessionId: String) {
        // No-op: cloud browser not supported on Android
    }
}
