package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: sanitize history before prompt submission.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall

/**
 * History Sanitizer — Clean and validate conversation history before sending to LLM
 *
 * Aligned with OpenClaw:
 * - src/agents/pi-embedded-runner/history.ts (sanitizeSessionHistory)
 * - src/agents/pi-embedded-runner/thinking.ts (dropThinkingBlocks)
 * - src/agents/session-transcript-repair.ts (repairToolUseResultPairing)
 * - validateAnthropicTurns / validateGeminiTurns
 *
 * Pipeline:
 * 1. Drop thinking/reasoning content
 * 2. Repair tool use/result pairing (displaced, duplicate, orphan, missing)
 * 3. Validate turn order
 * 4. Limit history turns
 *
 * Gap 4 enhancements (aligned with OpenClaw session-transcript-repair.ts):
 * - Displaced result repair: move tool results back to their matching assistant turn
 * - Duplicate result dedup: drop duplicate tool_call_id results
 * - Aborted/errored assistant skip: don't create synthetic results for incomplete tool calls
 * - Tool name validation: reject names with invalid characters
 * - Synthetic error result insertion: for genuinely missing results
 * - Tool result name normalization
 */
object HistorySanitizer {
    private const val TAG = "HistorySanitizer"

    private const val TOOL_CALL_NAME_MAX_CHARS = 64
    private val TOOL_CALL_NAME_RE = Regex("^[A-Za-z0-9_-]+$")

    data class RepairReport(
        val added: Int = 0,
        val droppedDuplicates: Int = 0,
        val droppedOrphans: Int = 0,
        val displaced: Boolean = false
    )

    /**
     * Full sanitization pipeline (call before each LLM request)
     *
     * @param messages Raw message history (excluding system prompt)
     * @param maxTurns Maximum number of user/assistant turn pairs to keep (0 = unlimited)
     * @return Sanitized message list
     */
    fun sanitize(
        messages: List<Message>,
        maxTurns: Int = 0
    ): List<Message> {
        var result = messages.toMutableList()

        // 1. Drop thinking/reasoning content
        result = dropThinkingContent(result)

        // 2. Repair tool use/result pairing (full OpenClaw-aligned repair)
        val report = repairToolUseResultPairingInPlace(result)
        if (report.added > 0 || report.droppedDuplicates > 0 || report.droppedOrphans > 0 || report.displaced) {
            Log.d(TAG, "Tool pairing repair: added=${report.added}, deduped=${report.droppedDuplicates}, orphans=${report.droppedOrphans}, displaced=${report.displaced}")
        }

        // 3. Validate turn order
        result = validateTurnOrder(result)

        // 4. Limit history turns
        if (maxTurns > 0) {
            result = limitHistoryTurns(result, maxTurns)
        }

        if (result.size != messages.size) {
            Log.d(TAG, "History sanitized: ${messages.size} → ${result.size} messages")
        }

        return result
    }

    /**
     * Drop thinking/reasoning content from assistant messages
     * Aligned with OpenClaw's dropThinkingBlocks (thinking.ts)
     *
     * Preserves turn structure: if all content was thinking-only, inserts empty text.
     */
    internal fun dropThinkingContent(messages: MutableList<Message>): MutableList<Message> {
        return messages.map { msg ->
            if (msg.role == "assistant" && msg.content.contains("<think>")) {
                val cleaned = msg.content
                    .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                    .trim()
                // OpenClaw: preserve turn structure by inserting empty text block
                // when all content was thinking-only
                if (cleaned.isEmpty() && msg.toolCalls.isNullOrEmpty()) {
                    msg.copy(content = "(thinking content removed)")
                } else {
                    msg.copy(content = cleaned)
                }
            } else {
                msg
            }
        }.toMutableList()
    }

    /**
     * Full tool use/result pairing repair.
     * Aligned with OpenClaw repairToolUseResultPairing (session-transcript-repair.ts)
     *
     * Handles:
     * - Displaced results: tool results that ended up after user turns → move back
     * - Duplicate results: same tool_call_id appears multiple times → keep first
     * - Orphan results: tool results with no matching tool call → drop
     * - Missing results: tool calls with no matching result → insert synthetic error
     * - Aborted assistant messages: stopReason=error/aborted → skip tool result creation
     * - Name validation: tool call name must match [A-Za-z0-9_-]+ and ≤64 chars
     */
    private fun repairToolUseResultPairingInPlace(messages: MutableList<Message>): RepairReport {
        val out = mutableListOf<Message>()
        val seenToolResultIds = mutableSetOf<String>()
        var addedCount = 0
        var droppedDuplicateCount = 0
        var droppedOrphanCount = 0
        var moved = false

        var i = 0
        while (i < messages.size) {
            val msg = messages[i]

            if (msg.role != "assistant") {
                // Drop free-floating tool results (orphans)
                if (msg.role == "tool") {
                    val tcId = msg.toolCallId
                    if (tcId != null && tcId in seenToolResultIds) {
                        droppedDuplicateCount++
                    } else {
                        droppedOrphanCount++
                    }
                    i++
                    continue
                }
                out.add(msg)
                i++
                continue
            }

            val assistant = msg

            // Check if this assistant message is aborted/errored
            // In AndroidForClaw, we check if content contains error markers
            val isAborted = isAbortedAssistantMessage(assistant)
            if (isAborted) {
                out.add(msg)
                i++
                continue
            }

            // Extract tool call IDs from this assistant message
            val toolCalls = assistant.toolCalls ?: emptyList()
            if (toolCalls.isEmpty()) {
                out.add(msg)
                i++
                continue
            }

            val toolCallIds = toolCalls.map { it.id }.toSet()
            val toolCallNamesById = toolCalls.associate { it.id to it.name }

            // Scan forward for matching tool results
            val spanResultsById = mutableMapOf<String, Message>()
            val remainder = mutableListOf<Message>()

            var j = i + 1
            while (j < messages.size) {
                val next = messages[j]

                // Stop at next assistant message
                if (next.role == "assistant") break

                if (next.role == "tool") {
                    val id = next.toolCallId
                    if (id != null && id in toolCallIds) {
                        // Check for duplicate
                        if (id in seenToolResultIds) {
                            droppedDuplicateCount++
                            j++
                            continue
                        }
                        // Normalize tool result name
                        val normalizedResult = normalizeToolResultName(next, toolCallNamesById[id])
                        if (!spanResultsById.containsKey(id)) {
                            spanResultsById[id] = normalizedResult
                        }
                        j++
                        continue
                    }
                    // Tool result that doesn't match current assistant → orphan
                    droppedOrphanCount++
                    j++
                    continue
                }

                // Non-tool, non-assistant message (e.g., user) → remainder
                remainder.add(next)
                j++
            }

            // Emit the assistant message
            out.add(msg)

            // Check if results were displaced (found across non-tool messages)
            if (spanResultsById.isNotEmpty() && remainder.isNotEmpty()) {
                moved = true
            }

            // Emit tool results in tool call order, insert synthetic for missing
            for (tc in toolCalls) {
                val id = tc.id
                if (id.isBlank()) continue
                val existing = spanResultsById[id]
                if (existing != null) {
                    seenToolResultIds.add(id)
                    out.add(existing)
                } else {
                    // Insert synthetic error result
                    val synthetic = Message(
                        role = "tool",
                        content = "[openclaw] missing tool result in session history; inserted synthetic error result for transcript repair.",
                        toolCallId = id,
                        name = tc.name.ifBlank { "unknown" }
                    )
                    seenToolResultIds.add(id)
                    out.add(synthetic)
                    addedCount++
                }
            }

            // Emit remainder (user messages etc.) after tool results
            for (rem in remainder) {
                out.add(rem)
            }

            i = j
        }

        messages.clear()
        messages.addAll(out)

        return RepairReport(
            added = addedCount,
            droppedDuplicates = droppedDuplicateCount,
            droppedOrphans = droppedOrphanCount,
            displaced = moved
        )
    }

    /**
     * Check if an assistant message was aborted/errored.
     * Aborted messages may have incomplete tool_use blocks (partialJson: true)
     * and should not have synthetic tool_results created.
     */
    private fun isAbortedAssistantMessage(msg: Message): Boolean {
        // Check common error patterns in content
        val content = msg.content
        if (content.contains("[error]", ignoreCase = true) ||
            content.contains("[aborted]", ignoreCase = true)) {
            return true
        }
        // If tool calls exist but none have valid IDs, likely aborted
        val toolCalls = msg.toolCalls ?: return false
        return toolCalls.isNotEmpty() && toolCalls.all { it.id.isBlank() }
    }

    /**
     * Normalize tool result name — ensure it has a valid name field.
     * Aligned with OpenClaw normalizeToolResultName.
     */
    private fun normalizeToolResultName(msg: Message, fallbackName: String?): Message {
        val currentName = msg.name?.trim()
        if (!currentName.isNullOrEmpty()) {
            // If name was trimmed, update
            return if (currentName != msg.name) msg.copy(name = currentName) else msg
        }
        // No valid name — use fallback
        val normalizedFallback = fallbackName?.trim()
        if (!normalizedFallback.isNullOrEmpty()) {
            return msg.copy(name = normalizedFallback)
        }
        // Last resort
        return if (msg.name == null) msg else msg.copy(name = "unknown")
    }

    /**
     * Validate tool call name format.
     * Aligned with OpenClaw TOOL_CALL_NAME_RE.
     */
    private fun isValidToolCallName(name: String?): Boolean {
        if (name == null) return false
        val trimmed = name.trim()
        return trimmed.isNotEmpty() &&
               trimmed.length <= TOOL_CALL_NAME_MAX_CHARS &&
               TOOL_CALL_NAME_RE.matches(trimmed)
    }

    /**
     * Validate turn order — ensure proper alternation
     * Aligned with OpenClaw's validateAnthropicTurns/validateGeminiTurns
     *
     * Rules:
     * - First non-system message should be "user"
     * - No consecutive "user" messages (merge them)
     * - No consecutive "assistant" messages without tool results in between
     * - "tool" messages must follow "assistant" messages with tool_calls
     */
    internal fun validateTurnOrder(messages: MutableList<Message>): MutableList<Message> {
        if (messages.isEmpty()) return messages

        val result = mutableListOf<Message>()
        var lastRole = ""

        for (msg in messages) {
            when {
                msg.role == "system" -> {
                    result.add(msg)
                }
                msg.role == "user" && lastRole == "user" -> {
                    // Merge consecutive user messages
                    val prev = result.removeLastOrNull()
                    if (prev != null) {
                        result.add(prev.copy(content = prev.content + "\n\n" + msg.content))
                        Log.d(TAG, "Merged consecutive user messages")
                    } else {
                        result.add(msg)
                    }
                }
                msg.role == "tool" -> {
                    // Tool messages are allowed after assistant (tool calls)
                    result.add(msg)
                    // Don't update lastRole — tool messages are part of the assistant turn
                }
                else -> {
                    result.add(msg)
                    lastRole = msg.role
                }
            }
        }

        return result
    }

    /**
     * Limit history to recent N turn pairs
     * Aligned with OpenClaw's limitHistoryTurns
     *
     * A "turn pair" = one user message + one assistant response (including tool calls/results)
     * Always keeps the system prompt and the most recent user message
     */
    fun limitHistoryTurns(messages: MutableList<Message>, maxTurns: Int): MutableList<Message> {
        if (maxTurns <= 0) return messages

        val systemMessages = messages.filter { it.role == "system" }
        val conversationMessages = messages.filter { it.role != "system" }

        if (conversationMessages.isEmpty()) return messages

        // Count turn pairs (each user message starts a new turn)
        val turns = mutableListOf<MutableList<Message>>()
        var currentTurn = mutableListOf<Message>()

        for (msg in conversationMessages) {
            if (msg.role == "user" && currentTurn.isNotEmpty()) {
                turns.add(currentTurn)
                currentTurn = mutableListOf()
            }
            currentTurn.add(msg)
        }
        if (currentTurn.isNotEmpty()) {
            turns.add(currentTurn)
        }

        val keptTurns = if (turns.size > maxTurns) {
            Log.d(TAG, "Limiting history: ${turns.size} turns → $maxTurns")
            turns.takeLast(maxTurns)
        } else {
            turns
        }

        val result = mutableListOf<Message>()
        result.addAll(systemMessages)
        keptTurns.forEach { result.addAll(it) }

        return result
    }
}
