/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.messaging

import android.util.Log
import com.xiaomo.signal.SignalClient

/**
 * Signal typing indicators
 */
class SignalTyping(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalTyping"
    }

    suspend fun sendTyping(chatId: String) {
        Log.d(TAG, "Sending typing indicator to $chatId")
        // TODO: Implement typing indicator
    }
}
