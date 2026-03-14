/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.messaging

import android.util.Log

/**
 * Signal @mention handling
 */
class SignalMention {
    companion object {
        private const val TAG = "SignalMention"

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
