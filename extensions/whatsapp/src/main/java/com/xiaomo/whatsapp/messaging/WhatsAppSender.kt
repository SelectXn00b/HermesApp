/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.messaging

import android.util.Log
import com.xiaomo.whatsapp.WhatsAppClient

/**
 * WhatsApp message sender
 */
class WhatsAppSender(private val client: WhatsAppClient) {
    companion object {
        private const val TAG = "WhatsAppSender"
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
