/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram.policy

import android.util.Log
import com.xiaomo.telegram.TelegramConfig

/**
 * Telegram DM/group access policy
 */
class TelegramPolicy(private val config: TelegramConfig) {
    companion object {
        private const val TAG = "TelegramPolicy"
    }

    fun isDmAllowed(senderId: String): Boolean {
        return config.dmPolicy == "open"
    }

    fun isGroupAllowed(groupId: String): Boolean {
        return config.groupPolicy == "open"
    }

    fun requiresMention(): Boolean {
        return config.requireMention
    }
}
