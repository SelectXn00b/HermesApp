/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/whatsapp/(all)
 *
 * AndroidForClaw adaptation: WhatsApp channel runtime.
 */
package com.xiaomo.whatsapp

data class WhatsAppConfig(
    val enabled: Boolean = false,
    val token: String = "",
    val botId: String = "",
    val domain: String = "whatsapp",
    val connectionMode: String = "websocket",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50
)
