package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/conversation-label.ts
 *
 * Derives a human-readable conversation label from inbound message context.
 */

/**
 * Resolve a conversation label from message context fields.
 * Aligned with TS resolveConversationLabel().
 */
fun resolveConversationLabel(
    conversationLabel: String? = null,
    threadLabel: String? = null,
    chatTypeRaw: String? = null,
    senderName: String? = null,
    from: String? = null,
    groupChannel: String? = null,
    groupSubject: String? = null,
    groupSpace: String? = null,
): String? {
    val explicit = conversationLabel?.trim()
    if (!explicit.isNullOrEmpty()) return explicit

    val threadLabelTrimmed = threadLabel?.trim()
    if (!threadLabelTrimmed.isNullOrEmpty()) return threadLabelTrimmed

    val chatType = ChatType.normalize(chatTypeRaw)
    if (chatType == ChatType.DIRECT) {
        return senderName?.trim()?.ifEmpty { null }
            ?: from?.trim()?.ifEmpty { null }
    }

    val base = groupChannel?.trim()?.ifEmpty { null }
        ?: groupSubject?.trim()?.ifEmpty { null }
        ?: groupSpace?.trim()?.ifEmpty { null }
        ?: from?.trim()?.ifEmpty { null }
        ?: return null

    val id = extractConversationId(from)
    if (id == null) return base
    if (!shouldAppendId(id)) return base
    if (base == id) return base
    if (id in base) return base
    if (base.lowercase().contains(" id:")) return base
    if (base.startsWith("#") || base.startsWith("@")) return base
    return "$base id:$id"
}

private fun extractConversationId(from: String?): String? {
    val trimmed = from?.trim()
    if (trimmed.isNullOrEmpty()) return null
    val parts = trimmed.split(":").filter { it.isNotEmpty() }
    return if (parts.isNotEmpty()) parts.last() else trimmed
}

private fun shouldAppendId(id: String): Boolean {
    if (Regex("^[0-9]+$").matches(id)) return true
    if ("@g.us" in id) return true
    return false
}
