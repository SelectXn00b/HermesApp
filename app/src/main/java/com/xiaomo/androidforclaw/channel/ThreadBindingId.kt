package com.xiaomo.androidforclaw.channel

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/thread-binding-id.ts
 *   (resolveThreadBindingConversationIdFromBindingId)
 *
 * AndroidForClaw adaptation: thread/topic binding ID resolution.
 * Maps thread/topic IDs to conversation IDs for session routing.
 */

/**
 * ThreadBindingId — Resolve thread binding conversation IDs.
 * Aligned with OpenClaw thread-binding-id.ts.
 */
object ThreadBindingId {

    /**
     * Extract conversation ID from a binding ID by stripping the account prefix.
     * Aligned with OpenClaw resolveThreadBindingConversationIdFromBindingId.
     *
     * @param accountId The account ID prefix (e.g. "feishu:app_xxx")
     * @param bindingId The full binding ID (e.g. "feishu:app_xxx:oc_thread_123")
     * @return The conversation ID without the account prefix, or null
     */
    fun resolveConversationIdFromBindingId(
        accountId: String,
        bindingId: String?
    ): String? {
        if (bindingId.isNullOrBlank()) return null
        val prefix = "$accountId:"
        if (!bindingId.startsWith(prefix)) return null
        val conversationId = bindingId.removePrefix(prefix)
        return conversationId.ifBlank { null }
    }

    /**
     * Build a binding ID from account ID and conversation ID.
     */
    fun buildBindingId(accountId: String, conversationId: String): String {
        return "$accountId:$conversationId"
    }

    /**
     * Build a thread session key for Feishu topic sessions.
     * Format: "group:$chatId:topic:$threadId"
     */
    fun buildFeishuTopicSessionKey(chatId: String, threadId: String): String {
        return "group:$chatId:topic:$threadId"
    }

    /**
     * Check if a session key represents a thread/topic session.
     */
    fun isThreadSession(sessionKey: String): Boolean {
        return sessionKey.contains(":topic:") || sessionKey.contains(":thread:")
    }

    /**
     * Strip thread/topic suffix from a session key.
     * "group:oc_xxx:topic:123" → "group:oc_xxx"
     */
    fun stripThreadSuffix(sessionKey: String): String {
        val regex = Regex("(:(?:thread|topic):\\S+)$", RegexOption.IGNORE_CASE)
        return sessionKey.replace(regex, "")
    }
}
