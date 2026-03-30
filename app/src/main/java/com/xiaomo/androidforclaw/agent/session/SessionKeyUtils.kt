package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/openclaw-android/src/main/java/ai/openclaw/app/SessionKey.kt
 *   (normalizeMainKey, isCanonicalMainSessionKey)
 *
 * AndroidForClaw adaptation: session key utilities for group/DM distinction.
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
    fun normalizeMainKey(raw: String?): String {
        val trimmed = raw?.trim()
        return if (!trimmed.isNullOrEmpty() && trimmed != "default") trimmed else "main"
    }

    /**
     * Check if a session key is a canonical main session key.
     * Aligned with OpenClaw isCanonicalMainSessionKey.
     *
     * Returns true for: "main", "global", "agent:*"
     */
    fun isCanonicalMainSessionKey(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (trimmed == "main" || trimmed == "global") return true
        return trimmed.startsWith("agent:")
    }

    /**
     * Determine if a session key represents a group chat context.
     *
     * Matches:
     * - "group:*" (Feishu extension format)
     * - "*_group" (Gateway format)
     * - Contains ":g-" (Telegram/Discord gateway format, e.g. "telegram:g-xxx")
     */
    fun isGroupSession(sessionKey: String): Boolean {
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
    fun extractChatType(sessionKey: String): String? {
        val key = sessionKey.trim()

        // Feishu extension format: "$chatType:$chatId"
        if (key.startsWith("group:")) return "group"
        if (key.startsWith("p2p:")) return "direct"

        // Gateway format: "${chatId}_${chatType}"
        if (key.endsWith("_group")) return "group"
        if (key.endsWith("_p2p")) return "direct"

        // Telegram/Discord gateway format with ":g-" prefix
        if (key.contains(":g-")) return "group"

        return null
    }
}
