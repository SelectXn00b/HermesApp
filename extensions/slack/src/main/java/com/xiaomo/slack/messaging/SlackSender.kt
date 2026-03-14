/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.messaging

import android.util.Log
import com.xiaomo.slack.SlackClient

/**
 * Slack message sender
 */
class SlackSender(private val client: SlackClient) {
    companion object {
        private const val TAG = "SlackSender"
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
