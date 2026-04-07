package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/pairing-labels.ts
 *
 * Resolve the human-readable ID label for a pairing channel.
 * Aligned 1:1 with TS resolvePairingIdLabel.
 * Android adaptation: channel plugin registry not available; use simple mapping.
 */
object PairingLabels {

    private val CHANNEL_ID_LABELS = mapOf(
        "discord" to "userId",
        "slack" to "userId",
        "telegram" to "userId",
        "whatsapp" to "phoneNumber",
        "signal" to "phoneNumber",
        "feishu" to "userId",
        "weixin" to "userId",
    )

    /**
     * Resolve the ID label for a pairing channel.
     * Aligned with TS resolvePairingIdLabel.
     */
    fun resolvePairingIdLabel(channel: PairingChannel): String {
        return CHANNEL_ID_LABELS[channel.trim().lowercase()] ?: "userId"
    }
}
