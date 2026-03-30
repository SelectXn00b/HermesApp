package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-call-id.ts
 *
 * AndroidForClaw adaptation: provider-specific tool call ID sanitization.
 */

import com.xiaomo.androidforclaw.providers.llm.Message
import java.security.MessageDigest

/**
 * Tool call ID sanitization mode.
 * Aligned with OpenClaw ToolCallIdMode.
 */
enum class ToolCallIdMode {
    /** Alphanumeric only, variable length */
    STRICT,
    /** Exactly 9 alphanumeric chars (Mistral requirement) */
    STRICT9
}

/**
 * Tool call ID sanitization — ensures provider-compatible IDs.
 * Aligned with OpenClaw tool-call-id.ts.
 */
object ToolCallIdSanitizer {

    private const val DEFAULT_TOOL_ID = "defaulttoolid"
    private const val SANITIZED_TOOL_ID = "sanitizedtoolid"
    private const val DEFAULT_ID_9 = "defaultid"
    private const val MAX_ID_LENGTH = 40

    /**
     * Sanitize a single tool call ID.
     * Aligned with OpenClaw sanitizeToolCallId.
     */
    fun sanitizeToolCallId(id: String?, mode: ToolCallIdMode = ToolCallIdMode.STRICT): String {
        if (id.isNullOrEmpty()) {
            return when (mode) {
                ToolCallIdMode.STRICT -> DEFAULT_TOOL_ID
                ToolCallIdMode.STRICT9 -> DEFAULT_ID_9
            }
        }

        val alphanumeric = id.replace(Regex("[^a-zA-Z0-9]"), "")

        return when (mode) {
            ToolCallIdMode.STRICT -> {
                if (alphanumeric.isEmpty()) SANITIZED_TOOL_ID else alphanumeric
            }
            ToolCallIdMode.STRICT9 -> {
                when {
                    alphanumeric.length >= 9 -> alphanumeric.substring(0, 9)
                    alphanumeric.isNotEmpty() -> shortHash(alphanumeric, 9)
                    else -> shortHash("sanitized", 9)
                }
            }
        }
    }

    /**
     * Check if a tool call ID is valid for the given mode.
     * Aligned with OpenClaw isValidCloudCodeAssistToolId.
     */
    fun isValidToolId(id: String?, mode: ToolCallIdMode = ToolCallIdMode.STRICT): Boolean {
        if (id.isNullOrEmpty()) return false
        val pattern = when (mode) {
            ToolCallIdMode.STRICT -> Regex("^[a-zA-Z0-9]+$")
            ToolCallIdMode.STRICT9 -> Regex("^[a-zA-Z0-9]{9}$")
        }
        return pattern.matches(id)
    }

    /**
     * Sanitize all tool call IDs in a message array for provider compatibility.
     * Uses occurrence-aware resolution to handle ID collisions.
     *
     * Aligned with OpenClaw sanitizeToolCallIdsForCloudCodeAssist.
     */
    fun sanitizeToolCallIdsForProvider(
        messages: List<Message>,
        mode: ToolCallIdMode = ToolCallIdMode.STRICT
    ): List<Message> {
        val resolver = OccurrenceAwareResolver(mode)
        var changed = false

        val result = messages.map { msg ->
            when (msg.role) {
                "assistant" -> {
                    val toolCalls = msg.toolCalls
                    if (toolCalls.isNullOrEmpty()) return@map msg

                    var callsChanged = false
                    val newCalls = toolCalls.map { call ->
                        val originalId = call.id
                        val sanitized = resolver.resolveAssistantToolCallId(originalId)
                        if (sanitized != originalId) {
                            callsChanged = true
                            call.copy(id = sanitized)
                        } else {
                            call
                        }
                    }

                    if (callsChanged) {
                        changed = true
                        msg.copy(toolCalls = newCalls)
                    } else {
                        msg
                    }
                }
                "tool" -> {
                    val toolCallId = msg.toolCallId
                    if (toolCallId.isNullOrEmpty()) return@map msg

                    val sanitized = resolver.resolveToolResultId(toolCallId)
                    if (sanitized != toolCallId) {
                        changed = true
                        msg.copy(toolCallId = sanitized)
                    } else {
                        msg
                    }
                }
                else -> msg
            }
        }

        return if (changed) result else messages
    }

    // ── Internal ──

    private fun shortHash(text: String, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(length)
    }

    /**
     * Occurrence-aware ID resolver that ensures unique IDs across messages.
     * Aligned with OpenClaw createOccurrenceAwareResolver.
     */
    private class OccurrenceAwareResolver(private val mode: ToolCallIdMode) {
        private val seenIds = mutableSetOf<String>()
        private val pendingQueues = mutableMapOf<String, MutableList<String>>()
        private val occurrenceCounts = mutableMapOf<String, Int>()

        fun resolveAssistantToolCallId(rawId: String?): String {
            val key = rawId ?: ""
            val count = occurrenceCounts.getOrDefault(key, 0)
            occurrenceCounts[key] = count + 1

            val input = if (count > 0) "$key:$count" else key
            val unique = makeUniqueToolId(input)

            pendingQueues.getOrPut(key) { mutableListOf() }.add(unique)
            return unique
        }

        fun resolveToolResultId(rawId: String?): String {
            val key = rawId ?: ""
            val queue = pendingQueues[key]
            if (queue != null && queue.isNotEmpty()) {
                return queue.removeAt(0)
            }
            // No pending entry — allocate a new one
            val fallbackCount = occurrenceCounts.getOrDefault("$key:tool_result", 0)
            occurrenceCounts["$key:tool_result"] = fallbackCount + 1
            return makeUniqueToolId("$key:tool_result:$fallbackCount")
        }

        private fun makeUniqueToolId(input: String): String {
            var candidate = sanitizeToolCallId(input, mode)

            // Truncate for strict mode
            if (mode == ToolCallIdMode.STRICT && candidate.length > MAX_ID_LENGTH) {
                candidate = candidate.substring(0, MAX_ID_LENGTH)
            }

            // Handle collisions
            if (candidate in seenIds) {
                val hash = shortHash(input, 8)
                candidate = when (mode) {
                    ToolCallIdMode.STRICT -> {
                        val base = candidate.take(MAX_ID_LENGTH - 8)
                        "$base$hash"
                    }
                    ToolCallIdMode.STRICT9 -> shortHash("$input:collision", 9)
                }
            }

            // Final collision fallback
            var attempt = 2
            while (candidate in seenIds) {
                candidate = when (mode) {
                    ToolCallIdMode.STRICT -> shortHash("$input:x$attempt", MAX_ID_LENGTH)
                    ToolCallIdMode.STRICT9 -> shortHash("$input:x$attempt", 9)
                }
                attempt++
            }

            seenIds.add(candidate)
            return candidate
        }
    }
}
