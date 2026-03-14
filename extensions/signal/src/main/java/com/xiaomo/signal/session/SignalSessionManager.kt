/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.session

import android.util.Log

/**
 * Signal session management
 */
class SignalSessionManager {
    companion object {
        private const val TAG = "SignalSessionManager"
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
