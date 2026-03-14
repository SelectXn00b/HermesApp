/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.messaging

import android.util.Log

/**
 * WhatsApp @mention handling
 */
class WhatsAppMention {
    companion object {
        private const val TAG = "WhatsAppMention"

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
