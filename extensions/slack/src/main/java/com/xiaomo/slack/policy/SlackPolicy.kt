/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack.policy

import android.util.Log
import com.xiaomo.slack.SlackConfig

/**
 * Slack DM/group access policy
 */
class SlackPolicy(private val config: SlackConfig) {
    companion object {
        private const val TAG = "SlackPolicy"
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
