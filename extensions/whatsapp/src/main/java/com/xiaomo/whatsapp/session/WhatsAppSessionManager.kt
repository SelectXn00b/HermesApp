/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.session

import android.util.Log

/**
 * WhatsApp session management
 */
class WhatsAppSessionManager {
    companion object {
        private const val TAG = "WhatsAppSessionManager"
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
