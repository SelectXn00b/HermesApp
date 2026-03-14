/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp.policy

import android.util.Log
import com.xiaomo.whatsapp.WhatsAppConfig

/**
 * WhatsApp DM/group access policy
 */
class WhatsAppPolicy(private val config: WhatsAppConfig) {
    companion object {
        private const val TAG = "WhatsAppPolicy"
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
