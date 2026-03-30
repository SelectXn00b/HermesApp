package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/command/delivery.ts
 *
 * AndroidForClaw adaptation: unified outbound delivery of agent run output.
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * Delivery target for agent output.
 */
data class DeliveryTarget(
    val channel: String,
    val target: String? = null,
    val threadId: String? = null,
    val accountId: String? = null
)

/**
 * Delivery result.
 */
data class DeliveryResult(
    val delivered: Boolean,
    val error: String? = null
)

/**
 * Agent output payload for delivery.
 */
data class AgentOutputPayload(
    val text: String? = null,
    val thinking: String? = null,
    val isNested: Boolean = false,
    val model: String? = null
)

/**
 * Command delivery — orchestrates delivering agent output to channels.
 * Aligned with OpenClaw command/delivery.ts.
 */
object CommandDelivery {

    private const val TAG = "CommandDelivery"

    /**
     * Deliver agent command result to the appropriate channel.
     * Aligned with OpenClaw deliverAgentCommandResult.
     *
     * @param payload The agent output to deliver
     * @param target The delivery target (channel, conversation, thread)
     * @param bestEffort If true, log errors instead of throwing
     * @param sendFn The actual send function (provided by the channel plugin)
     */
    suspend fun deliverAgentCommandResult(
        payload: AgentOutputPayload,
        target: DeliveryTarget,
        bestEffort: Boolean = false,
        sendFn: suspend (channel: String, target: String?, text: String, threadId: String?) -> Unit
    ): DeliveryResult {
        val text = payload.text
        if (text.isNullOrBlank()) {
            Log.d(TAG, "No text to deliver, skipping")
            return DeliveryResult(delivered = false, error = "No content")
        }

        // Format text for nested agents
        val formattedText = if (payload.isNested) {
            formatNestedAgentOutput(text, payload.model)
        } else {
            text
        }

        return try {
            sendFn(target.channel, target.target, formattedText, target.threadId)
            Log.d(TAG, "Delivered to ${target.channel}/${target.target}")
            DeliveryResult(delivered = true)
        } catch (e: Exception) {
            val errorMsg = "Delivery failed: ${e.message}"
            if (bestEffort) {
                Log.w(TAG, "[best-effort] $errorMsg")
                DeliveryResult(delivered = false, error = errorMsg)
            } else {
                Log.e(TAG, errorMsg)
                throw e
            }
        }
    }

    /**
     * Format output from a nested agent run.
     * Aligned with OpenClaw nested output formatting in delivery.ts.
     */
    private fun formatNestedAgentOutput(text: String, model: String?): String {
        val prefix = if (model != null) "[agent:nested model=$model]" else "[agent:nested]"
        return "$prefix\n$text"
    }
}
