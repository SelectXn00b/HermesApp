package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-key-utils.ts
 *
 * AndroidForClaw adaptation: session key parsing, cron/subagent/acp detection,
 * thread suffix extraction, and conversation reference parsing.
 *
 * NOTE: This file lives in the `sessions/` package (mirroring TS `sessions/`)
 * and is distinct from `agent.session.SessionKeyUtils` which holds the older
 * normalize/isGroup helpers.
 */

// ---------------------------------------------------------------------------
// Data classes
// ---------------------------------------------------------------------------

/**
 * Result of parsing an agent-scoped session key (`agent:<agentId>:<rest>`).
 * Values are normalized to lowercase.
 */
data class ParsedAgentSessionKey(
    val agentId: String,
    val rest: String
)

/**
 * Result of parsing a thread session suffix.
 */
data class ParsedThreadSessionSuffix(
    val baseSessionKey: String?,
    val threadId: String?
)

/**
 * A raw conversation reference extracted from a session key.
 */
data class RawSessionConversationRef(
    val channel: String,
    val kind: String,   // "group" | "channel"
    val rawId: String,
    val prefix: String
)

// ---------------------------------------------------------------------------
// Agent session key parsing
// ---------------------------------------------------------------------------

/**
 * Parse agent-scoped session keys in a canonical, case-insensitive way.
 * Returned values are normalized to lowercase for stable comparisons/routing.
 *
 * Expected format: `agent:<agentId>:<rest...>`
 */
fun parseAgentSessionKey(sessionKey: String?): ParsedAgentSessionKey? {
    val raw = (sessionKey ?: "").trim().lowercase()
    if (raw.isEmpty()) return null
    val parts = raw.split(":").filter { it.isNotEmpty() }
    if (parts.size < 3) return null
    if (parts[0] != "agent") return null
    val agentId = parts[1].trim()
    val rest = parts.drop(2).joinToString(":")
    if (agentId.isEmpty() || rest.isEmpty()) return null
    return ParsedAgentSessionKey(agentId, rest)
}

// ---------------------------------------------------------------------------
// Cron / subagent / ACP detection
// ---------------------------------------------------------------------------

/**
 * Returns `true` when the session key matches a cron-run session
 * (`agent:<id>:cron:<name>:run:<runId>`).
 */
fun isCronRunSessionKey(sessionKey: String?): Boolean {
    val parsed = parseAgentSessionKey(sessionKey) ?: return false
    return Regex("^cron:[^:]+:run:[^:]+$").matches(parsed.rest)
}

/**
 * Returns `true` when the session key belongs to any cron session
 * (`agent:<id>:cron:...`).
 */
fun isCronSessionKey(sessionKey: String?): Boolean {
    val parsed = parseAgentSessionKey(sessionKey) ?: return false
    return parsed.rest.lowercase().startsWith("cron:")
}

/**
 * Returns `true` when the session key represents a subagent session.
 */
fun isSubagentSessionKey(sessionKey: String?): Boolean {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return false
    if (raw.lowercase().startsWith("subagent:")) return true
    val parsed = parseAgentSessionKey(raw) ?: return false
    return parsed.rest.lowercase().startsWith("subagent:")
}

/**
 * Returns the subagent nesting depth by counting `:subagent:` segments.
 */
fun getSubagentDepth(sessionKey: String?): Int {
    val raw = (sessionKey ?: "").trim().lowercase()
    if (raw.isEmpty()) return 0
    return raw.split(":subagent:").size - 1
}

/**
 * Returns `true` when the session key represents an ACP session.
 */
fun isAcpSessionKey(sessionKey: String?): Boolean {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return false
    val normalized = raw.lowercase()
    if (normalized.startsWith("acp:")) return true
    val parsed = parseAgentSessionKey(raw) ?: return false
    return parsed.rest.lowercase().startsWith("acp:")
}

// ---------------------------------------------------------------------------
// Thread suffix parsing
// ---------------------------------------------------------------------------

/**
 * Parse a thread session suffix by locating the `:thread:` marker.
 */
fun parseThreadSessionSuffix(sessionKey: String?): ParsedThreadSessionSuffix {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return ParsedThreadSessionSuffix(baseSessionKey = null, threadId = null)

    val lowerRaw = raw.lowercase()
    val threadMarker = ":thread:"
    val markerIndex = lowerRaw.lastIndexOf(threadMarker)

    val baseSessionKey = if (markerIndex == -1) raw else raw.substring(0, markerIndex)
    val threadIdRaw = if (markerIndex == -1) null else raw.substring(markerIndex + threadMarker.length)
    val threadId = threadIdRaw?.trim()?.takeIf { it.isNotEmpty() }

    return ParsedThreadSessionSuffix(baseSessionKey, threadId)
}

// ---------------------------------------------------------------------------
// Conversation reference parsing
// ---------------------------------------------------------------------------

private fun normalizeSessionConversationChannel(value: String?): String? {
    val trimmed = (value ?: "").trim().lowercase()
    return trimmed.ifEmpty { null }
}

/**
 * Extract a raw session conversation reference from a session key.
 *
 * Expected pattern (after optional agent prefix):
 *   `<channel>:<group|channel>:<rawId...>`
 */
fun parseRawSessionConversationRef(sessionKey: String?): RawSessionConversationRef? {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return null

    val rawParts = raw.split(":").filter { it.isNotEmpty() }
    val bodyStartIndex =
        if (rawParts.size >= 3 && rawParts[0].trim().lowercase() == "agent") 2 else 0
    val parts = rawParts.drop(bodyStartIndex)
    if (parts.size < 3) return null

    val channel = normalizeSessionConversationChannel(parts[0]) ?: return null
    val kind = parts[1].trim().lowercase()
    if (kind != "group" && kind != "channel") return null

    val rawId = parts.drop(2).joinToString(":").trim()
    val prefix = rawParts.take(bodyStartIndex + 2).joinToString(":").trim()
    if (rawId.isEmpty() || prefix.isEmpty()) return null

    return RawSessionConversationRef(channel, kind, rawId, prefix)
}

/**
 * Resolve the parent session key for a threaded session.
 *
 * @return The parent key if a `:thread:` suffix is present and valid, else `null`.
 */
fun resolveThreadParentSessionKey(sessionKey: String?): String? {
    val (baseSessionKey, threadId) = parseThreadSessionSuffix(sessionKey)
    if (threadId == null) return null
    val parent = baseSessionKey?.trim()
    return if (parent.isNullOrEmpty()) null else parent
}
