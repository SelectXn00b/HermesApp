package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/channel-send-result.ts
 *
 * Channel send result types and helper functions.
 * Android adaptation: no ChannelOutboundAdapter dependency; standalone result types.
 */

// ---------- Types ----------

/**
 * Raw channel send result.
 * Aligned with TS ChannelSendRawResult.
 */
data class ChannelSendRawResult(
    val ok: Boolean,
    val messageId: String? = null,
    val error: String? = null,
)

/**
 * Outbound delivery result with channel attached.
 * Aligned with TS OutboundDeliveryResult shape.
 */
data class OutboundDeliveryResult(
    val channel: String,
    val messageId: String = "",
    val ok: Boolean = true,
    val error: Exception? = null,
)

// ---------- Helpers ----------

/**
 * Attach a channel identifier to a result object.
 * Aligned with TS attachChannelToResult.
 */
fun <T : Map<String, Any?>> attachChannelToResult(channel: String, result: T): Map<String, Any?> {
    return mapOf("channel" to channel) + result
}

/**
 * Create an empty channel result with default messageId.
 * Aligned with TS createEmptyChannelResult.
 */
fun createEmptyChannelResult(
    channel: String,
    messageId: String = "",
    ok: Boolean = true,
): OutboundDeliveryResult = OutboundDeliveryResult(
    channel = channel,
    messageId = messageId,
    ok = ok,
)

/**
 * Normalize raw channel send results into the shape shared outbound callers expect.
 * Aligned with TS buildChannelSendResult.
 */
fun buildChannelSendResult(channel: String, result: ChannelSendRawResult): OutboundDeliveryResult =
    OutboundDeliveryResult(
        channel = channel,
        ok = result.ok,
        messageId = result.messageId ?: "",
        error = if (!result.error.isNullOrEmpty()) Exception(result.error) else null,
    )
