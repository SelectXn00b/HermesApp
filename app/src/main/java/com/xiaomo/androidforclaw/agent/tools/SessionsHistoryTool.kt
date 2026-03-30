/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/sessions-history-tool.ts
 * - ../openclaw/src/logging/redact.ts (redactSensitiveText) → SensitiveTextRedactor.kt
 *
 * AndroidForClaw adaptation: LLM-facing tool to read subagent conversation history.
 * Reads from AgentLoop.conversationMessages for active runs, or frozenResultText for completed runs.
 * Includes per-field truncation (4000 chars), sensitive text redaction, and total cap (80KB).
 * Aligned with OpenClaw sessions-history-tool + redact pipeline.
 */
package com.xiaomo.androidforclaw.agent.tools

import com.xiaomo.androidforclaw.agent.subagent.SessionAccessResult
import com.xiaomo.androidforclaw.agent.subagent.SessionVisibilityGuard
import com.xiaomo.androidforclaw.agent.subagent.SubagentRegistry
import com.xiaomo.androidforclaw.logging.SensitiveTextRedactor
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * sessions_history — Read conversation history of a child subagent session.
 * Aligned with OpenClaw createSessionsHistoryTool.
 */
class SessionsHistoryTool(
    private val registry: SubagentRegistry,
    private val parentSessionKey: String,
) : Tool {

    companion object {
        /** Maximum total output bytes (aligned with OpenClaw SESSIONS_HISTORY_MAX_BYTES) */
        private const val MAX_BYTES = SensitiveTextRedactor.MAX_BYTES

        /**
         * Redact sensitive text patterns.
         * Delegates to SensitiveTextRedactor (extracted from this companion).
         */
        fun redactSensitiveText(text: String): Pair<String, Boolean> =
            SensitiveTextRedactor.redactSensitiveText(text)

        /**
         * Truncate and redact history text.
         * Delegates to SensitiveTextRedactor.
         */
        fun truncateHistoryText(text: String): Triple<String, Boolean, Boolean> =
            SensitiveTextRedactor.truncateHistoryText(text)
    }

    override val name = "sessions_history"
    override val description = "Read the conversation history of a child subagent session."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "target" to PropertySchema(
                            type = "string",
                            description = "Target subagent: 'last', numeric index (1-based), label, label prefix, run ID, or session key."
                        ),
                        "limit" to PropertySchema(
                            type = "number",
                            description = "Maximum number of messages to return. Default: 50."
                        ),
                        "include_tools" to PropertySchema(
                            type = "boolean",
                            description = "Include tool call/result messages. Default: false."
                        ),
                    ),
                    required = listOf("target")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val target = args["target"] as? String
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: target")
        }
        val limit = (args["limit"] as? Number)?.toInt() ?: 50
        val includeTools = (args["include_tools"] as? Boolean) ?: false

        // Resolve target
        val record = registry.resolveTarget(target, parentSessionKey)
            ?: return ToolResult(success = false, content = "No matching subagent found for target: $target")

        // Visibility guard (aligned with OpenClaw controlScope)
        val visibility = SessionVisibilityGuard.resolveVisibility(parentSessionKey, registry)
        val access = SessionVisibilityGuard.checkAccess(
            "read history of", parentSessionKey, record.childSessionKey, visibility, registry
        )
        if (access is SessionAccessResult.Denied) {
            return ToolResult(success = false, content = access.reason)
        }

        // Get messages from AgentLoop (active) or frozenResultText (completed)
        val loop = registry.getAgentLoop(record.runId)
        val messages = loop?.conversationMessages

        if (messages.isNullOrEmpty()) {
            // Fallback to frozen result for completed runs
            val frozen = record.frozenResultText
            if (!frozen.isNullOrBlank()) {
                val (sanitized, truncated, redacted) = truncateHistoryText(frozen)
                return ToolResult(
                    success = true,
                    content = buildString {
                        appendLine("Session: ${record.childSessionKey} (completed)")
                        appendLine("Status: ${record.outcome?.status?.wireValue ?: "unknown"}")
                        appendLine()
                        appendLine("[assistant] $sanitized")
                    },
                    metadata = mapOf(
                        "sessionKey" to record.childSessionKey,
                        "messages" to 1,
                        "truncated" to truncated,
                        "contentRedacted" to redacted,
                    )
                )
            }
            return ToolResult(success = true, content = "No message history available for '${record.label}'.")
        }

        // Filter and format messages with sanitization
        val filtered = messages.takeLast(limit).filter { msg ->
            includeTools || (msg.role != "tool" && msg.toolCalls.isNullOrEmpty())
        }

        var totalBytes = 0
        var anyTruncated = false
        var anyRedacted = false
        var droppedMessages = false

        // Build lines newest→oldest to cap correctly (drop oldest first, aligned with OpenClaw)
        val lines = mutableListOf<String>()
        for (msg in filtered.reversed()) {
            val (sanitizedContent, truncated, redacted) = truncateHistoryText(msg.content)
            if (truncated) anyTruncated = true
            if (redacted) anyRedacted = true

            val line = "[${msg.role}] $sanitizedContent\n\n"
            val lineBytes = line.toByteArray(Charsets.UTF_8).size

            if (totalBytes + lineBytes > MAX_BYTES) {
                droppedMessages = true
                break
            }
            lines.add(line)
            totalBytes += lineBytes
        }
        lines.reverse() // Back to chronological order

        val formatted = buildString {
            appendLine("Session: ${record.childSessionKey} (${if (record.isActive) "active" else "completed"})")
            appendLine("Messages: ${lines.size}/${messages.size}")
            if (droppedMessages) appendLine("(oldest messages dropped, exceeded ${MAX_BYTES / 1024}KB limit)")
            appendLine()

            for (line in lines) {
                append(line)
            }
        }.trimEnd()

        // Hard cap safety net: if even after truncation we exceed MAX_BYTES
        val finalFormatted = if (formatted.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            droppedMessages = true
            "[sessions_history omitted: output too large]"
        } else {
            formatted
        }

        return ToolResult(
            success = true,
            content = finalFormatted,
            metadata = mapOf(
                "sessionKey" to record.childSessionKey,
                "messages" to filtered.size,
                "bytes" to totalBytes,
                "truncated" to (anyTruncated || droppedMessages),
                "droppedMessages" to droppedMessages,
                "contentRedacted" to anyRedacted,
            )
        )
    }
}
