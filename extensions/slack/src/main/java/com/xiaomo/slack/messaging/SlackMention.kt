/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.messaging

import android.util.Log

/**
 * Slack @mention handling
 */
class SlackMention {
    companion object {
        private const val TAG = "SlackMention"

        fun isMentioned(text: String, botId: String): Boolean {
            Log.d(TAG, "Checking mention for bot: $botId")
            // TODO: Implement mention detection
            return false
        }

        fun stripMention(text: String, botId: String): String {
            // TODO: Strip bot mention from text
            return text
        }
    }
}
