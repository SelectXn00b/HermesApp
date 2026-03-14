/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram.messaging

import android.util.Log

/**
 * Telegram @mention handling
 */
class TelegramMention {
    companion object {
        private const val TAG = "TelegramMention"

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
