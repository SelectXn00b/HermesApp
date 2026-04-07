package com.xiaomo.androidforclaw.agent.session

import com.xiaomo.androidforclaw.routing.SessionKeyShape
import com.xiaomo.androidforclaw.routing.classifySessionKeyShape
import com.xiaomo.androidforclaw.routing.parseRawSessionConversationRef

/**
 * OpenClaw Source Reference:
 * - ../openclaw/openclaw-android/src/main/java/ai/openclaw/app/SessionKey.kt
 *   (normalizeMainKey, isCanonicalMainSessionKey)
 *
 * AndroidForClaw adaptation: session key utilities for group/DM distinction.
 *
 * @deprecated Prefer the canonical routing.SessionKey functions directly.
 *
 * Session key format conventions:
 * - Feishu extension: "$chatType:$chatId" (e.g. "group:oc_xxx", "p2p:ou_xxx")
 * - Gateway path:     "${chatId}_${chatType}" (e.g. "oc_xxx_group", "oc_xxx_p2p")
 * - Main session:     "main" / "agent:main:main" / "default"
 * - OpenClaw:         "global" / "agent:*" prefixed
 */
object SessionKeyUtils {

    /**
     * Normalize a main session key.
     * Aligned with OpenClaw normalizeMainKey.
     *
     * null / blank / "default" → "main"
     */
    @Deprecated(
        message = "Use com.xiaomo.androidforclaw.routing.normalizeMainKey() instead",
        replaceWith = ReplaceWith(
            "com.xiaomo.androidforclaw.routing.normalizeMainKey(raw)",
            "com.xiaomo.androidforclaw.routing.normalizeMainKey"
        )
    )
    fun normalizeMainKey(raw: String?): String {
        return com.xiaomo.androidforclaw.routing.normalizeMainKey(raw)
    }

    /**
     * Check if a session key is a canonical main session key.
     * Aligned with OpenClaw isCanonicalMainSessionKey.
     *
     * Returns true for: "main", "global", "agent:*"
     */
    @Deprecated(
        message = "Use com.xiaomo.androidforclaw.routing.classifySessionKeyShape() instead",
        replaceWith = ReplaceWith(
            "classifySessionKeyShape(raw) in setOf(SessionKeyShape.AGENT, SessionKeyShape.LEGACY_OR_ALIAS)",
            "com.xiaomo.androidforclaw.routing.classifySessionKeyShape",
            "com.xiaomo.androidforclaw.routing.SessionKeyShape"
        )
    )
    fun isCanonicalMainSessionKey(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        // "main" and "global" are canonical main keys
        if (trimmed == "main" || trimmed == "global") return true
        // Use routing module for agent: prefix detection
        val shape = classifySessionKeyShape(trimmed)
        return shape == SessionKeyShape.AGENT || shape == SessionKeyShape.MALFORMED_AGENT
    }

    /**
     * Determine if a session key represents a group chat context.
     *
     * Matches:
     * - "group:*" (Feishu extension format)
     * - "*_group" (Gateway format)
     * - Contains ":g-" (Telegram/Discord gateway format, e.g. "telegram:g-xxx")
     */
    @Deprecated(
        message = "Use com.xiaomo.androidforclaw.routing.parseRawSessionConversationRef() instead",
        replaceWith = ReplaceWith(
            "parseRawSessionConversationRef(sessionKey)?.kind == \"group\"",
            "com.xiaomo.androidforclaw.routing.parseRawSessionConversationRef"
        )
    )
    fun isGroupSession(sessionKey: String): Boolean {
        // Delegate to routing module when the key matches agent: format
        val ref = parseRawSessionConversationRef(sessionKey)
        if (ref != null) return ref.kind == "group"
        // Fallback for legacy key formats not understood by routing module
        val key = sessionKey.trim().lowercase()
        if (key.startsWith("group:")) return true
        if (key.endsWith("_group")) return true
        if (key.contains(":g-")) return true
        return false
    }

    /**
     * Extract chat type from a session key.
     *
     * - "group:oc_xxx" → "group"
     * - "p2p:ou_xxx" → "direct"
     * - "oc_xxx_group" → "group"
     * - "oc_xxx_p2p" → "direct"
     * - "main" → null (main session, not channel-derived)
     */
    @Deprecated(
        message = "Use com.xiaomo.androidforclaw.routing.parseRawSessionConversationRef() instead",
        replaceWith = ReplaceWith(
            "parseRawSessionConversationRef(sessionKey)?.kind",
            "com.xiaomo.androidforclaw.routing.parseRawSessionConversationRef"
        )
    )
    fun extractChatType(sessionKey: String): String? {
        // Delegate to routing module when the key matches agent: format
        val ref = parseRawSessionConversationRef(sessionKey)
        if (ref != null) return ref.kind

        // Fallback for legacy key formats
        val key = sessionKey.trim()
        if (key.startsWith("group:")) return "group"
        if (key.startsWith("p2p:")) return "direct"
        if (key.endsWith("_group")) return "group"
        if (key.endsWith("_p2p")) return "direct"
        if (key.contains(":g-")) return "group"

        return null
    }
}
