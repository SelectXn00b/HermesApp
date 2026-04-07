package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/pairing-messages.ts
 *
 * Builds pairing reply messages for DM pairing challenges.
 * Aligned 1:1 with TS buildPairingReply.
 */
object PairingMessages {

    /**
     * Build the pairing reply message text.
     * Aligned with TS buildPairingReply.
     */
    fun buildPairingReply(
        channel: PairingChannel,
        idLine: String,
        code: String,
    ): String {
        val approveCommand = "openclaw pairing approve $channel $code"
        return listOf(
            "OpenClaw: access not configured.",
            "",
            idLine,
            "Pairing code:",
            "```",
            code,
            "```",
            "",
            "Ask the bot owner to approve with:",
            approveCommand,
            "```",
            approveCommand,
            "```",
        ).joinToString("\n")
    }
}
