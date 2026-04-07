package com.xiaomo.androidforclaw.routing

/**
 * OpenClaw module: routing
 * Source: OpenClaw/src/routing/session-key.ts + OpenClaw/src/sessions/session-key-utils.ts
 *
 * Session key format: "agent:<agentId>:<rest>" (colon-separated).
 */

const val DEFAULT_AGENT_ID = "main"
const val DEFAULT_MAIN_KEY = "main"

// ---------------------------------------------------------------------------
// SessionKeyShape — aligned with TS union "missing" | "agent" | ...
// ---------------------------------------------------------------------------

enum class SessionKeyShape(val value: String) {
    MISSING("missing"),
    AGENT("agent"),
    LEGACY_OR_ALIAS("legacy_or_alias"),
    MALFORMED_AGENT("malformed_agent");
}

// ---------------------------------------------------------------------------
// ParsedAgentSessionKey — from session-key-utils.ts
// ---------------------------------------------------------------------------

data class ParsedAgentSessionKey(val agentId: String, val rest: String)

// ---------------------------------------------------------------------------
// ParsedThreadSessionSuffix — from session-key-utils.ts
// ---------------------------------------------------------------------------

data class ParsedThreadSessionSuffix(
    val baseSessionKey: String?,
    val threadId: String?
)

// ---------------------------------------------------------------------------
// RawSessionConversationRef — from session-key-utils.ts
// ---------------------------------------------------------------------------

data class RawSessionConversationRef(
    val channel: String,
    val kind: String,   // "group" | "channel"
    val rawId: String,
    val prefix: String
)

// ---------------------------------------------------------------------------
// Pre-compiled regex
// ---------------------------------------------------------------------------

private val VALID_ID_RE = Regex("""^[a-z0-9][a-z0-9_-]{0,63}$""", RegexOption.IGNORE_CASE)
private val INVALID_CHARS_RE = Regex("""[^a-z0-9_-]+""")
private val LEADING_DASH_RE = Regex("""^-+""")
private val TRAILING_DASH_RE = Regex("""-+$""")

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun normalizeToken(value: String?): String =
    (value ?: "").trim().lowercase()

// ---------------------------------------------------------------------------
// parseAgentSessionKey — from session-key-utils.ts
// Colon-separated: "agent:<agentId>:<rest>"
// ---------------------------------------------------------------------------

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
// isCronSessionKey — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun isCronSessionKey(sessionKey: String?): Boolean {
    val parsed = parseAgentSessionKey(sessionKey) ?: return false
    return parsed.rest.lowercase().startsWith("cron:")
}

// ---------------------------------------------------------------------------
// isSubagentSessionKey — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun isSubagentSessionKey(sessionKey: String?): Boolean {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return false
    if (raw.lowercase().startsWith("subagent:")) return true
    val parsed = parseAgentSessionKey(raw) ?: return false
    return parsed.rest.lowercase().startsWith("subagent:")
}

// ---------------------------------------------------------------------------
// getSubagentDepth — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun getSubagentDepth(sessionKey: String?): Int {
    val raw = (sessionKey ?: "").trim().lowercase()
    if (raw.isEmpty()) return 0
    return raw.split(":subagent:").size - 1
}

// ---------------------------------------------------------------------------
// isAcpSessionKey — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun isAcpSessionKey(sessionKey: String?): Boolean {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return false
    val normalized = raw.lowercase()
    if (normalized.startsWith("acp:")) return true
    val parsed = parseAgentSessionKey(raw) ?: return false
    return parsed.rest.lowercase().startsWith("acp:")
}

// ---------------------------------------------------------------------------
// parseThreadSessionSuffix — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun parseThreadSessionSuffix(sessionKey: String?): ParsedThreadSessionSuffix {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return ParsedThreadSessionSuffix(baseSessionKey = null, threadId = null)

    val lowerRaw = raw.lowercase()
    val threadMarker = ":thread:"
    val markerIndex = lowerRaw.lastIndexOf(threadMarker)

    val baseSessionKey = if (markerIndex == -1) raw else raw.substring(0, markerIndex)
    val threadIdRaw = if (markerIndex == -1) null else raw.substring(markerIndex + threadMarker.length)
    val threadId = threadIdRaw?.trim()?.ifEmpty { null }

    return ParsedThreadSessionSuffix(baseSessionKey, threadId)
}

// ---------------------------------------------------------------------------
// parseRawSessionConversationRef — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun parseRawSessionConversationRef(sessionKey: String?): RawSessionConversationRef? {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return null

    val rawParts = raw.split(":").filter { it.isNotEmpty() }
    val bodyStartIndex =
        if (rawParts.size >= 3 && rawParts[0].trim().lowercase() == "agent") 2 else 0
    val parts = rawParts.drop(bodyStartIndex)
    if (parts.size < 3) return null

    val channel = parts[0].trim().lowercase().ifEmpty { null } ?: return null
    val kind = parts[1].trim().lowercase()
    if (kind != "group" && kind != "channel") return null

    val rawId = parts.drop(2).joinToString(":").trim()
    val prefix = rawParts.take(bodyStartIndex + 2).joinToString(":").trim()
    if (rawId.isEmpty() || prefix.isEmpty()) return null

    return RawSessionConversationRef(channel, kind, rawId, prefix)
}

// ---------------------------------------------------------------------------
// resolveThreadParentSessionKey — from session-key-utils.ts
// ---------------------------------------------------------------------------

fun resolveThreadParentSessionKey(sessionKey: String?): String? {
    val (baseSessionKey, threadId) = parseThreadSessionSuffix(sessionKey)
    if (threadId == null) return null
    val parent = baseSessionKey?.trim()
    return if (parent.isNullOrEmpty()) null else parent
}

// ---------------------------------------------------------------------------
// scopedHeartbeatWakeOptions — from session-key.ts
// ---------------------------------------------------------------------------

fun scopedHeartbeatWakeOptions(
    sessionKey: String,
    wakeOptions: MutableMap<String, Any?>
): Map<String, Any?> {
    return if (parseAgentSessionKey(sessionKey) != null) {
        wakeOptions.also { it["sessionKey"] = sessionKey }
    } else {
        wakeOptions
    }
}

// ---------------------------------------------------------------------------
// normalizeMainKey — from session-key.ts
// ---------------------------------------------------------------------------

fun normalizeMainKey(value: String?): String {
    val trimmed = (value ?: "").trim()
    return if (trimmed.isNotEmpty()) trimmed.lowercase() else DEFAULT_MAIN_KEY
}

// ---------------------------------------------------------------------------
// toAgentRequestSessionKey — from session-key.ts
// ---------------------------------------------------------------------------

fun toAgentRequestSessionKey(storeKey: String?): String? {
    val raw = (storeKey ?: "").trim()
    if (raw.isEmpty()) return null
    return parseAgentSessionKey(raw)?.rest ?: raw
}

// ---------------------------------------------------------------------------
// toAgentStoreSessionKey — from session-key.ts
// ---------------------------------------------------------------------------

fun toAgentStoreSessionKey(
    agentId: String,
    requestKey: String?,
    mainKey: String? = null
): String {
    val raw = (requestKey ?: "").trim()
    if (raw.isEmpty() || raw.lowercase() == DEFAULT_MAIN_KEY) {
        return buildAgentMainSessionKey(agentId, mainKey)
    }
    val parsed = parseAgentSessionKey(raw)
    if (parsed != null) {
        return "agent:${parsed.agentId}:${parsed.rest}"
    }
    val lowered = raw.lowercase()
    if (lowered.startsWith("agent:")) {
        return lowered
    }
    return "agent:${normalizeAgentId(agentId)}:$lowered"
}

// ---------------------------------------------------------------------------
// resolveAgentIdFromSessionKey — from session-key.ts
// ---------------------------------------------------------------------------

fun resolveAgentIdFromSessionKey(sessionKey: String?): String {
    val parsed = parseAgentSessionKey(sessionKey)
    return normalizeAgentId(parsed?.agentId ?: DEFAULT_AGENT_ID)
}

// ---------------------------------------------------------------------------
// classifySessionKeyShape — from session-key.ts
// ---------------------------------------------------------------------------

fun classifySessionKeyShape(sessionKey: String?): SessionKeyShape {
    val raw = (sessionKey ?: "").trim()
    if (raw.isEmpty()) return SessionKeyShape.MISSING
    if (parseAgentSessionKey(raw) != null) return SessionKeyShape.AGENT
    return if (raw.lowercase().startsWith("agent:")) SessionKeyShape.MALFORMED_AGENT
    else SessionKeyShape.LEGACY_OR_ALIAS
}

// ---------------------------------------------------------------------------
// normalizeAgentId — from session-key.ts
// ---------------------------------------------------------------------------

fun normalizeAgentId(value: String?): String {
    val trimmed = (value ?: "").trim()
    if (trimmed.isEmpty()) return DEFAULT_AGENT_ID

    // Valid ID — just lowercase
    if (VALID_ID_RE.matches(trimmed)) return trimmed.lowercase()

    // Best-effort fallback: collapse invalid characters to "-"
    val fallback = trimmed
        .lowercase()
        .replace(INVALID_CHARS_RE, "-")
        .replace(LEADING_DASH_RE, "")
        .replace(TRAILING_DASH_RE, "")
        .take(64)
    return fallback.ifEmpty { DEFAULT_AGENT_ID }
}

// ---------------------------------------------------------------------------
// isValidAgentId — from session-key.ts
// ---------------------------------------------------------------------------

fun isValidAgentId(value: String?): Boolean {
    val trimmed = (value ?: "").trim()
    return trimmed.isNotEmpty() && VALID_ID_RE.matches(trimmed)
}

// ---------------------------------------------------------------------------
// sanitizeAgentId — from session-key.ts (alias for normalizeAgentId)
// ---------------------------------------------------------------------------

fun sanitizeAgentId(value: String?): String = normalizeAgentId(value)

// ---------------------------------------------------------------------------
// buildAgentMainSessionKey — from session-key.ts
// Format: "agent:<agentId>:<mainKey>"
// ---------------------------------------------------------------------------

fun buildAgentMainSessionKey(agentId: String, mainKey: String? = null): String {
    val normalizedAgentId = normalizeAgentId(agentId)
    val normalizedMainKey = normalizeMainKey(mainKey)
    return "agent:$normalizedAgentId:$normalizedMainKey"
}

// ---------------------------------------------------------------------------
// buildAgentPeerSessionKey — from session-key.ts
// Full params including dmScope and identityLinks.
// ---------------------------------------------------------------------------

fun buildAgentPeerSessionKey(
    agentId: String,
    mainKey: String? = null,
    channel: String,
    accountId: String? = null,
    peerKind: String? = null,
    peerId: String? = null,
    identityLinks: Map<String, List<String>>? = null,
    dmScope: String? = null
): String {
    val effectivePeerKind = peerKind ?: "direct"

    if (effectivePeerKind == "direct") {
        val effectiveDmScope = dmScope ?: "main"
        var resolvedPeerId = (peerId ?: "").trim()
        val linkedPeerId = if (effectiveDmScope == "main") null
        else resolveLinkedPeerId(
            identityLinks = identityLinks,
            channel = channel,
            peerId = resolvedPeerId
        )
        if (linkedPeerId != null) {
            resolvedPeerId = linkedPeerId
        }
        resolvedPeerId = resolvedPeerId.lowercase()

        if (effectiveDmScope == "per-account-channel-peer" && resolvedPeerId.isNotEmpty()) {
            val ch = channel.trim().lowercase().ifEmpty { "unknown" }
            val acct = normalizeAccountId(accountId)
            return "agent:${normalizeAgentId(agentId)}:$ch:$acct:direct:$resolvedPeerId"
        }
        if (effectiveDmScope == "per-channel-peer" && resolvedPeerId.isNotEmpty()) {
            val ch = channel.trim().lowercase().ifEmpty { "unknown" }
            return "agent:${normalizeAgentId(agentId)}:$ch:direct:$resolvedPeerId"
        }
        if (effectiveDmScope == "per-peer" && resolvedPeerId.isNotEmpty()) {
            return "agent:${normalizeAgentId(agentId)}:direct:$resolvedPeerId"
        }
        return buildAgentMainSessionKey(agentId, mainKey)
    }

    // Non-direct: group / channel
    val ch = channel.trim().lowercase().ifEmpty { "unknown" }
    val resolvedPeerId = (peerId ?: "").trim().ifEmpty { "unknown" }.lowercase()
    return "agent:${normalizeAgentId(agentId)}:$ch:$effectivePeerKind:$resolvedPeerId"
}

// ---------------------------------------------------------------------------
// resolveLinkedPeerId — private helper for identity link resolution
// ---------------------------------------------------------------------------

private fun resolveLinkedPeerId(
    identityLinks: Map<String, List<String>>?,
    channel: String,
    peerId: String
): String? {
    if (identityLinks == null) return null
    val trimmedPeerId = peerId.trim()
    if (trimmedPeerId.isEmpty()) return null

    val candidates = mutableSetOf<String>()
    val rawCandidate = normalizeToken(trimmedPeerId)
    if (rawCandidate.isNotEmpty()) candidates.add(rawCandidate)

    val normalizedChannel = normalizeToken(channel)
    if (normalizedChannel.isNotEmpty()) {
        val scopedCandidate = normalizeToken("$normalizedChannel:$trimmedPeerId")
        if (scopedCandidate.isNotEmpty()) candidates.add(scopedCandidate)
    }

    if (candidates.isEmpty()) return null

    for ((canonical, ids) in identityLinks) {
        val canonicalName = canonical.trim()
        if (canonicalName.isEmpty()) continue
        for (id in ids) {
            val normalized = normalizeToken(id)
            if (normalized.isNotEmpty() && normalized in candidates) {
                return canonicalName
            }
        }
    }
    return null
}

// ---------------------------------------------------------------------------
// buildGroupHistoryKey — from session-key.ts
// ---------------------------------------------------------------------------

fun buildGroupHistoryKey(
    channel: String,
    accountId: String? = null,
    peerKind: String,
    peerId: String
): String {
    val ch = normalizeToken(channel).ifEmpty { "unknown" }
    val acct = normalizeAccountId(accountId)
    val pid = peerId.trim().lowercase().ifEmpty { "unknown" }
    return "$ch:$acct:$peerKind:$pid"
}

// ---------------------------------------------------------------------------
// resolveThreadSessionKeys — from session-key.ts
// ---------------------------------------------------------------------------

data class ThreadSessionKeys(
    val sessionKey: String,
    val parentSessionKey: String?
)

fun resolveThreadSessionKeys(
    baseSessionKey: String,
    threadId: String? = null,
    parentSessionKey: String? = null,
    useSuffix: Boolean = true,
    normalizeThreadId: ((String) -> String)? = null
): ThreadSessionKeys {
    val tid = (threadId ?: "").trim()
    if (tid.isEmpty()) {
        return ThreadSessionKeys(baseSessionKey, parentSessionKey = null)
    }
    val normalizedThreadId = (normalizeThreadId ?: { it.lowercase() })(tid)
    val sessionKey = if (useSuffix) "$baseSessionKey:thread:$normalizedThreadId" else baseSessionKey
    return ThreadSessionKeys(sessionKey, parentSessionKey)
}
