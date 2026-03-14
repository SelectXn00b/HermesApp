/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/signal/(all)
 *
 * AndroidForClaw adaptation: Signal channel runtime.
 */
package com.xiaomo.signal

data class SignalConfig(
    val enabled: Boolean = false,
    val token: String = "",
    val botId: String = "",
    val domain: String = "signal",
    val connectionMode: String = "websocket",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50
)
