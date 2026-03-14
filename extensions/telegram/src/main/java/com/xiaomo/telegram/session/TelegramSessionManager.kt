/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram.session

import android.util.Log

/**
 * Telegram session management
 */
class TelegramSessionManager {
    companion object {
        private const val TAG = "TelegramSessionManager"
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
