/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram.messaging

import android.util.Log
import com.xiaomo.telegram.TelegramClient

/**
 * Telegram message sender
 */
class TelegramSender(private val client: TelegramClient) {
    companion object {
        private const val TAG = "TelegramSender"
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
