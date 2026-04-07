package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-chat-type.ts
 *
 * AndroidForClaw adaptation: best-effort chat-type extraction from session keys.
 *
 * The TS version also queries channel-plugin bootstrap registry for legacy patterns;
 * in Android, only built-in legacy patterns are evaluated (no plugin iterator).
 */

/**
 * Chat type derived from a session key.
 */
enum class SessionKeyChatType(val value: String) {
    DIRECT("direct"),
    GROUP("group"),
    CHANNEL("channel"),
    UNKNOWN("unknown");
}

// ---------------------------------------------------------------------------
// Built-in legacy patterns
// ---------------------------------------------------------------------------

private fun deriveBuiltInLegacySessionChatType(scopedSessionKey: String): SessionKeyChatType? {
    // group:<id>
    if (Regex("^group:[^:]+$").matches(scopedSessionKey)) return SessionKeyChatType.GROUP
    // WhatsApp-style numeric group: 123-456@g.us
    if (Regex("^[0-9]+(?:-[0-9]+)*@g\\.us$").matches(scopedSessionKey)) return SessionKeyChatType.GROUP
    // whatsapp:..@g.us (but not whatsapp:...:group:...@g.us)
    if (Regex("^whatsapp:(?!.*:group:).+@g\\.us$").matches(scopedSessionKey)) return SessionKeyChatType.GROUP
    // discord:guild-<guildId>:channel-<channelId>
    if (Regex("^discord:(?:[^:]+:)?guild-[^:]+:channel-[^:]+$").matches(scopedSessionKey)) return SessionKeyChatType.CHANNEL
    return null
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Best-effort chat-type extraction from session keys across canonical and legacy formats.
 *
 * @param sessionKey The raw session key (may be agent-scoped).
 * @return The derived [SessionKeyChatType]; defaults to [SessionKeyChatType.UNKNOWN].
 */
fun deriveSessionChatType(sessionKey: String?): SessionKeyChatType {
    val raw = (sessionKey ?: "").trim().lowercase()
    if (raw.isEmpty()) return SessionKeyChatType.UNKNOWN

    val scoped = parseAgentSessionKey(raw)?.rest ?: raw
    val tokens = scoped.split(":").filter { it.isNotEmpty() }.toSet()

    if ("group" in tokens) return SessionKeyChatType.GROUP
    if ("channel" in tokens) return SessionKeyChatType.CHANNEL
    if ("direct" in tokens || "dm" in tokens) return SessionKeyChatType.DIRECT

    val builtInLegacy = deriveBuiltInLegacySessionChatType(scoped)
    if (builtInLegacy != null) return builtInLegacy

    // NOTE: OpenClaw iterates channel-plugin bootstrap registry here for
    // plugin.messaging?.deriveLegacySessionChatType. Android skips this
    // until channel plugins are ported.

    return SessionKeyChatType.UNKNOWN
}
