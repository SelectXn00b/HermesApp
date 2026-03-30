package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-transcript-repair.ts
 *
 * AndroidForClaw adaptation: tool call/result pairing repair in session transcripts.
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall

/**
 * Tool call input repair report.
 * Aligned with OpenClaw ToolCallInputRepairReport.
 */
data class ToolCallInputRepairReport(
    val droppedCalls: Int = 0,
    val droppedMessages: Int = 0
)

/**
 * Tool use/result pairing repair report.
 * Aligned with OpenClaw ToolUseRepairReport.
 */
data class ToolUseRepairReport(
    val insertedResults: Int = 0,
    val droppedDuplicates: Int = 0,
    val movedResults: Int = 0,
    val droppedOrphans: Int = 0
)

/**
 * Session transcript repair — ensures valid tool call/result pairing.
 * Aligned with OpenClaw session-transcript-repair.ts.
 */
object SessionTranscriptRepair {

    private const val TAG = "SessionTranscriptRepair"

    /**
     * Repair tool call inputs: drop calls with missing ID, input, or unknown names.
     * Aligned with OpenClaw repairToolCallInputs.
     */
    fun repairToolCallInputs(
        messages: List<Message>,
        knownToolNames: Set<String>? = null
    ): Pair<List<Message>, ToolCallInputRepairReport> {
        var droppedCalls = 0
        var droppedMessages = 0

        val result = messages.mapNotNull { msg ->
            if (msg.role != "assistant" || msg.toolCalls.isNullOrEmpty()) return@mapNotNull msg

            val validCalls = msg.toolCalls.filter { call ->
                val valid = call.id.isNotEmpty() &&
                    call.name.isNotEmpty() &&
                    call.arguments.isNotEmpty() &&
                    (knownToolNames == null || call.name in knownToolNames)
                if (!valid) {
                    droppedCalls++
                    Log.d(TAG, "Dropped invalid tool call: id=${call.id}, name=${call.name}")
                }
                valid
            }

            if (validCalls.isEmpty()) {
                // If no valid calls remain and message has no text content, drop the whole message
                if (msg.content.isBlank()) {
                    droppedMessages++
                    null
                } else {
                    msg.copy(toolCalls = null)
                }
            } else if (validCalls.size != msg.toolCalls.size) {
                msg.copy(toolCalls = validCalls)
            } else {
                msg
            }
        }

        return result to ToolCallInputRepairReport(droppedCalls, droppedMessages)
    }

    /**
     * Repair tool use/result pairing: ensure every tool call has a matching result.
     * Aligned with OpenClaw repairToolUseResultPairing.
     */
    fun repairToolUseResultPairing(messages: List<Message>): Pair<List<Message>, ToolUseRepairReport> {
        var insertedResults = 0
        var droppedDuplicates = 0
        var droppedOrphans = 0

        val result = mutableListOf<Message>()
        val pendingCallIds = mutableSetOf<String>()
        val seenResultIds = mutableSetOf<String>()

        for (msg in messages) {
            when (msg.role) {
                "assistant" -> {
                    result.add(msg)
                    // Track tool call IDs that need results
                    msg.toolCalls?.forEach { call ->
                        pendingCallIds.add(call.id)
                    }
                }
                "tool" -> {
                    val callId = msg.toolCallId
                    if (callId == null) {
                        droppedOrphans++
                        Log.d(TAG, "Dropped orphaned tool result (no callId)")
                        continue
                    }

                    if (callId in seenResultIds) {
                        droppedDuplicates++
                        Log.d(TAG, "Dropped duplicate tool result for callId=$callId")
                        continue
                    }

                    if (callId !in pendingCallIds) {
                        droppedOrphans++
                        Log.d(TAG, "Dropped orphaned tool result for callId=$callId (no matching call)")
                        continue
                    }

                    seenResultIds.add(callId)
                    pendingCallIds.remove(callId)
                    result.add(msg)
                }
                else -> {
                    // Before adding a non-tool message, insert missing results for pending calls
                    for (pendingId in pendingCallIds.toList()) {
                        if (pendingId !in seenResultIds) {
                            result.add(makeMissingToolResult(pendingId, null))
                            seenResultIds.add(pendingId)
                            insertedResults++
                        }
                    }
                    pendingCallIds.clear()
                    result.add(msg)
                }
            }
        }

        // Handle remaining pending calls at the end
        for (pendingId in pendingCallIds) {
            if (pendingId !in seenResultIds) {
                result.add(makeMissingToolResult(pendingId, null))
                insertedResults++
            }
        }

        return result to ToolUseRepairReport(
            insertedResults = insertedResults,
            droppedDuplicates = droppedDuplicates,
            droppedOrphans = droppedOrphans
        )
    }

    /**
     * Generate a synthetic error tool result for a missing result.
     * Aligned with OpenClaw makeMissingToolResult.
     */
    fun makeMissingToolResult(callId: String, toolName: String?): Message {
        return Message(
            role = "tool",
            content = "[Error: tool execution was interrupted or result was lost]",
            name = toolName,
            toolCallId = callId
        )
    }

    /**
     * Strip tool result detail blocks (for security during compaction).
     * Aligned with OpenClaw stripToolResultDetails.
     */
    fun stripToolResultDetails(messages: List<Message>): List<Message> {
        return messages.map { msg ->
            if (msg.role == "tool" && msg.content.length > 1000) {
                val truncated = msg.content.take(1000) + "\n[...truncated for compaction]"
                msg.copy(content = truncated)
            } else {
                msg
            }
        }
    }
}
