/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.session

import android.util.Log

/**
 * Slack session management
 */
class SlackSessionManager {
    companion object {
        private const val TAG = "SlackSessionManager"
    }

    private val sessions = mutableMapOf<String, SessionState>()

    fun getOrCreate(sessionKey: String): SessionState {
        return sessions.getOrPut(sessionKey) {
            Log.d(TAG, "Creating new session: $sessionKey")
            SessionState(sessionKey)
        }
    }

    data class SessionState(
        val key: String,
        var messageCount: Int = 0,
        var lastActivityMs: Long = System.currentTimeMillis()
    )
}
