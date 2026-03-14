/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal.policy

import android.util.Log
import com.xiaomo.signal.SignalConfig

/**
 * Signal DM/group access policy
 */
class SignalPolicy(private val config: SignalConfig) {
    companion object {
        private const val TAG = "SignalPolicy"
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
