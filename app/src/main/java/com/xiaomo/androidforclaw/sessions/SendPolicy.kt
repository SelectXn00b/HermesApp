package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/send-policy.ts
 *
 * AndroidForClaw adaptation: session send-policy resolution.
 *
 * Uses [com.xiaomo.androidforclaw.config.OpenClawConfig] for config access.
 * Session entries are represented as `Map<String, Any?>`.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Whether a session is allowed to send messages.
 */
enum class SessionSendPolicyDecision(val value: String) {
    ALLOW("allow"),
    DENY("deny");
}

// ---------------------------------------------------------------------------
// Helpers — chat type normalization (aligned with channels/chat-type.ts)
// ---------------------------------------------------------------------------

private fun normalizeChatType(raw: String?): String? {
    val value = raw?.trim()?.lowercase()
    if (value.isNullOrEmpty()) return null
    return when (value) {
        "direct", "dm" -> "direct"
        "group" -> "group"
        "channel" -> "channel"
        else -> null
    }
}

private fun normalizeMatchValue(raw: String?): String? {
    val value = raw?.trim()?.lowercase()
    return if (value.isNullOrEmpty()) null else value
}

private fun stripAgentSessionKeyPrefix(key: String?): String? {
    if (key.isNullOrEmpty()) return null
    val parts = key.split(":").filter { it.isNotEmpty() }
    // Canonical agent session keys: agent:<agentId>:<sessionKey...>
    if (parts.size >= 3 && parts[0] == "agent") {
        return parts.drop(2).joinToString(":")
    }
    return key
}

private fun deriveChannelFromKey(key: String?): String? {
    val normalizedKey = stripAgentSessionKeyPrefix(key) ?: return null
    val parts = normalizedKey.split(":").filter { it.isNotEmpty() }
    if (parts.size >= 3 && (parts[1] == "group" || parts[1] == "channel")) {
        return normalizeMatchValue(parts[0])
    }
    return null
}

private fun deriveChatTypeFromKey(key: String?): String? {
    val normalizedKey = stripAgentSessionKeyPrefix(key)?.trim()?.lowercase()
    if (normalizedKey.isNullOrEmpty()) return null
    val tokens = normalizedKey.split(":").filter { it.isNotEmpty() }.toSet()
    if ("group" in tokens) return "group"
    if ("channel" in tokens) return "channel"
    if ("direct" in tokens || "dm" in tokens) return "direct"
    if (Regex("^group:[^:]+$").matches(normalizedKey)) return "group"
    if (Regex("^[0-9]+(?:-[0-9]+)*@g\\.us$").matches(normalizedKey)) return "group"
    if (Regex("^whatsapp:(?!.*:group:).+@g\\.us$").matches(normalizedKey)) return "group"
    if (Regex("^discord:(?:[^:]+:)?guild-[^:]+:channel-[^:]+$").matches(normalizedKey)) return "channel"
    return null
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Normalize a raw send-policy string to a [SessionSendPolicyDecision], or `null` if invalid.
 */
fun normalizeSendPolicy(raw: String?): SessionSendPolicyDecision? {
    val value = raw?.trim()?.lowercase()
    return when (value) {
        "allow" -> SessionSendPolicyDecision.ALLOW
        "deny" -> SessionSendPolicyDecision.DENY
        else -> null
    }
}

/**
 * Resolve the effective send policy for a session.
 *
 * Resolution order:
 * 1. Per-session override (`entry.sendPolicy`)
 * 2. Config rules matching channel / chatType / keyPrefix / rawKeyPrefix
 * 3. Config default
 * 4. Fall back to ALLOW
 *
 * @param cfg        The OpenClaw config instance.
 * @param entry      Optional session entry map (may contain `sendPolicy`, `channel`, `lastChannel`, `chatType`).
 * @param sessionKey Optional session key for pattern-based matching.
 * @param channel    Explicit channel override.
 * @param chatType   Explicit chat-type override.
 */
fun resolveSendPolicy(
    cfg: OpenClawConfig,
    entry: Map<String, Any?>? = null,
    sessionKey: String? = null,
    channel: String? = null,
    chatType: String? = null
): SessionSendPolicyDecision {
    // 1. Per-session override
    val override = normalizeSendPolicy(entry?.get("sendPolicy") as? String)
    if (override != null) return override

    // 2. Config-level send policy
    // OpenClaw: cfg.session?.sendPolicy — AndroidForClaw SessionConfig doesn't have sendPolicy yet,
    // so we read it from a generic map if available. For forward-compat, accept a sendPolicy
    // property on the config's session section via reflection or map access.
    val policy = getSendPolicyConfig(cfg) ?: return SessionSendPolicyDecision.ALLOW

    val resolvedChannel =
        normalizeMatchValue(channel)
            ?: normalizeMatchValue(entry?.get("channel") as? String)
            ?: normalizeMatchValue(entry?.get("lastChannel") as? String)
            ?: deriveChannelFromKey(sessionKey)

    val resolvedChatType =
        normalizeChatType(chatType ?: entry?.get("chatType") as? String)
            ?: normalizeChatType(deriveChatTypeFromKey(sessionKey))

    val rawSessionKey = sessionKey.orEmpty()
    val strippedSessionKey = stripAgentSessionKeyPrefix(rawSessionKey).orEmpty()
    val rawSessionKeyNorm = rawSessionKey.lowercase()
    val strippedSessionKeyNorm = strippedSessionKey.lowercase()

    var allowedMatch = false
    val rules = policy["rules"] as? List<*> ?: emptyList<Any>()
    for (ruleRaw in rules) {
        if (ruleRaw == null) continue
        @Suppress("UNCHECKED_CAST")
        val rule = ruleRaw as? Map<String, Any?> ?: continue
        val action = normalizeSendPolicy(rule["action"] as? String) ?: SessionSendPolicyDecision.ALLOW

        @Suppress("UNCHECKED_CAST")
        val match = rule["match"] as? Map<String, Any?> ?: emptyMap()
        val matchChannel = normalizeMatchValue(match["channel"] as? String)
        val matchChatType = normalizeChatType(match["chatType"] as? String)
        val matchPrefix = normalizeMatchValue(match["keyPrefix"] as? String)
        val matchRawPrefix = normalizeMatchValue(match["rawKeyPrefix"] as? String)

        if (matchChannel != null && matchChannel != resolvedChannel) continue
        if (matchChatType != null && matchChatType != resolvedChatType) continue
        if (matchRawPrefix != null && !rawSessionKeyNorm.startsWith(matchRawPrefix)) continue
        if (matchPrefix != null &&
            !rawSessionKeyNorm.startsWith(matchPrefix) &&
            !strippedSessionKeyNorm.startsWith(matchPrefix)
        ) continue

        if (action == SessionSendPolicyDecision.DENY) return SessionSendPolicyDecision.DENY
        allowedMatch = true
    }

    if (allowedMatch) return SessionSendPolicyDecision.ALLOW

    val fallback = normalizeSendPolicy(policy["default"] as? String)
    return fallback ?: SessionSendPolicyDecision.ALLOW
}

// ---------------------------------------------------------------------------
// Config bridge — forward-compat helper
// ---------------------------------------------------------------------------

/**
 * Extract the send-policy config block from OpenClawConfig.
 *
 * Since [com.xiaomo.androidforclaw.config.SessionConfig] may not yet declare
 * `sendPolicy`, this uses a duck-typed map lookup as a forward-compat shim.
 * When SessionConfig gains the field, this can be replaced with a direct accessor.
 */
@Suppress("UNUSED_PARAMETER")
private fun getSendPolicyConfig(cfg: OpenClawConfig): Map<String, Any?>? {
    // Attempt reflective access; returns null if the field does not exist yet.
    return try {
        val field = cfg.session::class.java.getDeclaredField("sendPolicy")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(cfg.session) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    }
}
