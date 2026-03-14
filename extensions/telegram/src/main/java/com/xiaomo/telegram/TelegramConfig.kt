/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/telegram/(all)
 *
 * AndroidForClaw adaptation: Telegram channel runtime.
 */
package com.xiaomo.telegram

data class TelegramConfig(
    val enabled: Boolean = false,
    val token: String = "",
    val botId: String = "",
    val domain: String = "telegram",
    val connectionMode: String = "websocket",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50
)
