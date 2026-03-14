/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/slack/(all)
 *
 * AndroidForClaw adaptation: Slack channel runtime.
 */
package com.xiaomo.slack

data class SlackConfig(
    val enabled: Boolean = false,
    val token: String = "",
    val botId: String = "",
    val domain: String = "slack",
    val connectionMode: String = "websocket",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50
)
