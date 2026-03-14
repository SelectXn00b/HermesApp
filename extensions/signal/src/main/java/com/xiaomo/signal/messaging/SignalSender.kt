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
 * Signal message sender
 */
class SignalSender(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalSender"
    }

    suspend fun send(target: String, text: String): Boolean {
        Log.d(TAG, "Sending to $target: ${text.take(50)}")
        return client.sendMessage(target, text)
    }

    suspend fun sendMedia(target: String, mediaUrl: String, caption: String? = null): Boolean {
        Log.d(TAG, "Sending media to $target: $mediaUrl")
        // TODO: Implement media sending
        return false
    }
}
