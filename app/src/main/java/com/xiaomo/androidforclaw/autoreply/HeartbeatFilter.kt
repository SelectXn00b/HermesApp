package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/heartbeat-filter.ts
 *
 * Filter heartbeat user-assistant pairs from message history
 * to keep context windows clean.
 */

// ============================================================================
// Constants (aligned with OpenClaw heartbeat-filter.ts)
// ============================================================================

private const val HEARTBEAT_TASK_PROMPT_PREFIX =
    "Run the following periodic tasks (only those due based on their intervals):"
private const val HEARTBEAT_TASK_PROMPT_ACK = "After completing all due tasks, reply HEARTBEAT_OK."

// ============================================================================
// Message text resolution (aligned with OpenClaw resolveMessageText)
// ============================================================================

data class ResolvedMessageText(
    val text: String,
    val hasNonTextContent: Boolean
)

/**
 * Resolve message content to text.
 * Aligned with OpenClaw resolveMessageText.
 *
 * On Android, content is typically already a String, but this handles
 * list-of-blocks format (content array with type/text blocks) as well.
 */
fun resolveMessageText(content: Any?): ResolvedMessageText {
    if (content is String) {
        return ResolvedMessageText(text = content, hasNonTextContent = false)
    }
    if (content == null) {
        return ResolvedMessageText(text = "", hasNonTextContent = false)
    }
    if (content is List<*>) {
        var hasNonTextContent = false
        val text = content.mapNotNull { block ->
            if (block is Map<*, *>) {
                val type = block["type"] as? String
                val blockText = block["text"] as? String
                if (type == "text" && blockText != null) {
                    blockText
                } else {
                    hasNonTextContent = true
                    null
                }
            } else {
                hasNonTextContent = true
                null
            }
        }.joinToString("")
        return ResolvedMessageText(text = text, hasNonTextContent = hasNonTextContent)
    }
    return ResolvedMessageText(text = "", hasNonTextContent = true)
}

// ============================================================================
// Heartbeat message detection (aligned with OpenClaw heartbeat-filter.ts)
// ============================================================================

/**
 * Check if a message is a heartbeat user prompt.
 * Aligned with OpenClaw isHeartbeatUserMessage.
 */
fun isHeartbeatUserMessage(
    role: String,
    content: Any?,
    heartbeatPrompt: String? = null
): Boolean {
    if (role != "user") return false
    val (text) = resolveMessageText(content)
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    val normalizedHeartbeatPrompt = heartbeatPrompt?.trim()
    if (!normalizedHeartbeatPrompt.isNullOrEmpty() && trimmed.startsWith(normalizedHeartbeatPrompt)) {
        return true
    }

    return trimmed.startsWith(HEARTBEAT_TASK_PROMPT_PREFIX) &&
        trimmed.contains(HEARTBEAT_TASK_PROMPT_ACK)
}

/**
 * Check if a message is a heartbeat OK response (assistant side).
 * Aligned with OpenClaw isHeartbeatOkResponse.
 */
fun isHeartbeatOkResponse(
    role: String,
    content: Any?,
    ackMaxChars: Int? = null
): Boolean {
    if (role != "assistant") return false
    val (text, hasNonTextContent) = resolveMessageText(content)
    if (hasNonTextContent) return false

    return stripHeartbeatToken(text, mode = StripHeartbeatMode.HEARTBEAT, maxAckChars = ackMaxChars).shouldSkip
}

// ============================================================================
// Heartbeat pair filtering (aligned with OpenClaw filterHeartbeatPairs)
// ============================================================================

/**
 * Represents a chat message with role and content.
 */
interface ChatMessage {
    val role: String
    val content: Any?
}

/**
 * Simple data class implementation of ChatMessage.
 */
data class SimpleChatMessage(
    override val role: String,
    override val content: Any?
) : ChatMessage

/**
 * Filter consecutive heartbeat user/assistant pairs from message history.
 * Aligned with OpenClaw filterHeartbeatPairs.
 */
fun <T> filterHeartbeatPairs(
    messages: List<T>,
    ackMaxChars: Int? = null,
    heartbeatPrompt: String? = null,
    getRole: (T) -> String,
    getContent: (T) -> Any?
): List<T> {
    if (messages.size < 2) return messages

    val result = mutableListOf<T>()
    var i = 0
    while (i < messages.size) {
        if (i + 1 < messages.size &&
            isHeartbeatUserMessage(getRole(messages[i]), getContent(messages[i]), heartbeatPrompt) &&
            isHeartbeatOkResponse(getRole(messages[i + 1]), getContent(messages[i + 1]), ackMaxChars)
        ) {
            i += 2
            continue
        }
        result.add(messages[i])
        i++
    }

    return result
}

// ============================================================================
// Legacy helpers (kept for backward compatibility)
// ============================================================================

/**
 * Check if a heartbeat should be sent based on elapsed time.
 */
fun shouldSendHeartbeat(lastHeartbeatMs: Long, intervalMs: Long = 60_000): Boolean {
    return System.currentTimeMillis() - lastHeartbeatMs >= intervalMs
}

/**
 * Filter a reply payload by suppressing silent heartbeat replies.
 */
fun filterHeartbeatPayload(payload: ReplyPayload): ReplyPayload? {
    if (isSilentReplyText(payload.text, HEARTBEAT_TOKEN)) return null
    if (isSilentReplyText(payload.text, SILENT_REPLY_TOKEN)) return null
    return payload
}
