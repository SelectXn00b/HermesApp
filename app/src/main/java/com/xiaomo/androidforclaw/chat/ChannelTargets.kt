package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/targets.ts
 *
 * Messaging target resolution: parse and normalize channel/user targets
 * from raw strings with support for mention patterns, prefixes, and @-syntax.
 */

// ---------------------------------------------------------------------------
// Types  (aligned with TS MessagingTargetKind, MessagingTarget, etc.)
// ---------------------------------------------------------------------------

enum class MessagingTargetKind(val value: String) {
    USER("user"),
    CHANNEL("channel"),
}

data class MessagingTarget(
    val kind: MessagingTargetKind,
    val id: String,
    val raw: String,
    val normalized: String,
)

data class MessagingTargetParseOptions(
    val defaultKind: MessagingTargetKind? = null,
    val ambiguousMessage: String? = null,
)

// ---------------------------------------------------------------------------
// Normalization  (aligned with TS normalizeTargetId, buildMessagingTarget)
// ---------------------------------------------------------------------------

fun normalizeTargetId(kind: MessagingTargetKind, id: String): String =
    "${kind.value}:$id".lowercase()

fun buildMessagingTarget(kind: MessagingTargetKind, id: String, raw: String): MessagingTarget =
    MessagingTarget(
        kind = kind,
        id = id,
        raw = raw,
        normalized = normalizeTargetId(kind, id),
    )

// ---------------------------------------------------------------------------
// Validation  (aligned with TS ensureTargetId)
// ---------------------------------------------------------------------------

fun ensureTargetId(candidate: String, pattern: Regex, errorMessage: String): String {
    if (!pattern.matches(candidate)) throw IllegalArgumentException(errorMessage)
    return candidate
}

// ---------------------------------------------------------------------------
// Parsing  (aligned with TS parseTargetMention, parseTargetPrefix, etc.)
// ---------------------------------------------------------------------------

fun parseTargetMention(
    raw: String,
    mentionPattern: Regex,
    kind: MessagingTargetKind,
): MessagingTarget? {
    val match = mentionPattern.find(raw) ?: return null
    val id = match.groupValues.getOrNull(1) ?: return null
    return buildMessagingTarget(kind, id, raw)
}

fun parseTargetPrefix(
    raw: String,
    prefix: String,
    kind: MessagingTargetKind,
): MessagingTarget? {
    if (!raw.startsWith(prefix)) return null
    val id = raw.substring(prefix.length).trim()
    return if (id.isNotEmpty()) buildMessagingTarget(kind, id, raw) else null
}

data class PrefixKindEntry(
    val prefix: String,
    val kind: MessagingTargetKind,
)

fun parseTargetPrefixes(
    raw: String,
    prefixes: List<PrefixKindEntry>,
): MessagingTarget? {
    for (entry in prefixes) {
        val parsed = parseTargetPrefix(raw, entry.prefix, entry.kind)
        if (parsed != null) return parsed
    }
    return null
}

fun parseAtUserTarget(
    raw: String,
    pattern: Regex,
    errorMessage: String,
): MessagingTarget? {
    if (!raw.startsWith("@")) return null
    val candidate = raw.substring(1).trim()
    val id = ensureTargetId(candidate, pattern, errorMessage)
    return buildMessagingTarget(MessagingTargetKind.USER, id, raw)
}

fun parseMentionPrefixOrAtUserTarget(
    raw: String,
    mentionPattern: Regex,
    prefixes: List<PrefixKindEntry>,
    atUserPattern: Regex,
    atUserErrorMessage: String,
): MessagingTarget? {
    val mentionTarget = parseTargetMention(raw, mentionPattern, MessagingTargetKind.USER)
    if (mentionTarget != null) return mentionTarget
    val prefixedTarget = parseTargetPrefixes(raw, prefixes)
    if (prefixedTarget != null) return prefixedTarget
    return parseAtUserTarget(raw, atUserPattern, atUserErrorMessage)
}

fun requireTargetKind(
    platform: String,
    target: MessagingTarget?,
    kind: MessagingTargetKind,
): String {
    val kindLabel = kind.value
    if (target == null) {
        throw IllegalArgumentException("$platform $kindLabel id is required.")
    }
    if (target.kind != kind) {
        throw IllegalArgumentException("$platform $kindLabel id is required (use $kindLabel:<id>).")
    }
    return target.id
}
