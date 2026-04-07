package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/chat-type.ts
 *
 * Chat conversation type enumeration and normalization.
 */

enum class ChatType(val value: String) {
    DIRECT("direct"),
    GROUP("group"),
    CHANNEL("channel");

    companion object {
        /**
         * Normalize a raw string to a ChatType.
         * Aligned with TS normalizeChatType().
         */
        fun normalize(raw: String?): ChatType? {
            val v = raw?.trim()?.lowercase()
            if (v.isNullOrEmpty()) return null
            return when (v) {
                "direct", "dm" -> DIRECT
                "group" -> GROUP
                "channel" -> CHANNEL
                else -> null
            }
        }
    }
}
