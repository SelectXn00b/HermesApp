package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/channel-config.ts
 *
 * Channel config matching: resolves per-channel config entries with
 * support for direct match, parent fallback, wildcard, and normalized key matching.
 */

// ---------------------------------------------------------------------------
// Types  (aligned with TS ChannelMatchSource, ChannelEntryMatch, etc.)
// ---------------------------------------------------------------------------

enum class ChannelMatchSource(val value: String) {
    DIRECT("direct"),
    PARENT("parent"),
    WILDCARD("wildcard"),
}

data class ChannelEntryMatch<T>(
    val entry: T? = null,
    val key: String? = null,
    val wildcardEntry: T? = null,
    val wildcardKey: String? = null,
    val parentEntry: T? = null,
    val parentKey: String? = null,
    val matchKey: String? = null,
    val matchSource: ChannelMatchSource? = null,
)

// ---------------------------------------------------------------------------
// Slug normalization  (aligned with TS normalizeChannelSlug)
// ---------------------------------------------------------------------------

fun normalizeChannelSlug(value: String): String {
    return value.trim()
        .lowercase()
        .removePrefix("#")
        .replace(Regex("[^a-z0-9]+"), "-")
        .replace(Regex("^-+|-+$"), "")
}

// ---------------------------------------------------------------------------
// Key candidates  (aligned with TS buildChannelKeyCandidates)
// ---------------------------------------------------------------------------

fun buildChannelKeyCandidates(vararg keys: String?): List<String> {
    val seen = mutableSetOf<String>()
    val candidates = mutableListOf<String>()
    for (key in keys) {
        if (key == null) continue
        val trimmed = key.trim()
        if (trimmed.isEmpty() || !seen.add(trimmed)) continue
        candidates.add(trimmed)
    }
    return candidates
}

// ---------------------------------------------------------------------------
// Entry matching  (aligned with TS resolveChannelEntryMatch)
// ---------------------------------------------------------------------------

fun <T> resolveChannelEntryMatch(
    entries: Map<String, T>?,
    keys: List<String>,
    wildcardKey: String? = null,
): ChannelEntryMatch<T> {
    val effectiveEntries = entries ?: emptyMap()
    var match = ChannelEntryMatch<T>()

    for (key in keys) {
        if (effectiveEntries.containsKey(key)) {
            match = match.copy(entry = effectiveEntries[key], key = key)
            break
        }
    }

    if (wildcardKey != null && effectiveEntries.containsKey(wildcardKey)) {
        match = match.copy(
            wildcardEntry = effectiveEntries[wildcardKey],
            wildcardKey = wildcardKey,
        )
    }

    return match
}

// ---------------------------------------------------------------------------
// Entry matching with fallback  (aligned with TS resolveChannelEntryMatchWithFallback)
// ---------------------------------------------------------------------------

fun <T> resolveChannelEntryMatchWithFallback(
    entries: Map<String, T>?,
    keys: List<String>,
    parentKeys: List<String> = emptyList(),
    wildcardKey: String? = null,
    normalizeKey: ((String) -> String)? = null,
): ChannelEntryMatch<T> {
    val direct = resolveChannelEntryMatch(entries, keys, wildcardKey)

    if (direct.entry != null && direct.key != null) {
        return direct.copy(matchKey = direct.key, matchSource = ChannelMatchSource.DIRECT)
    }

    // Try normalized keys
    if (normalizeKey != null) {
        val normalizedKeys = keys.map { normalizeKey(it) }.filter { it.isNotEmpty() }
        if (normalizedKeys.isNotEmpty()) {
            for ((entryKey, entry) in entries.orEmpty()) {
                val normalizedEntry = normalizeKey(entryKey)
                if (normalizedEntry.isNotEmpty() && normalizedEntry in normalizedKeys) {
                    return direct.copy(
                        entry = entry,
                        key = entryKey,
                        matchKey = entryKey,
                        matchSource = ChannelMatchSource.DIRECT,
                    )
                }
            }
        }
    }

    // Try parent keys
    if (parentKeys.isNotEmpty()) {
        val parent = resolveChannelEntryMatch(entries, parentKeys)
        if (parent.entry != null && parent.key != null) {
            return direct.copy(
                entry = parent.entry,
                key = parent.key,
                parentEntry = parent.entry,
                parentKey = parent.key,
                matchKey = parent.key,
                matchSource = ChannelMatchSource.PARENT,
            )
        }
        // Try normalized parent keys
        if (normalizeKey != null) {
            val normalizedParentKeys = parentKeys.map { normalizeKey(it) }.filter { it.isNotEmpty() }
            if (normalizedParentKeys.isNotEmpty()) {
                for ((entryKey, entry) in entries.orEmpty()) {
                    val normalizedEntry = normalizeKey(entryKey)
                    if (normalizedEntry.isNotEmpty() && normalizedEntry in normalizedParentKeys) {
                        return direct.copy(
                            entry = entry,
                            key = entryKey,
                            parentEntry = entry,
                            parentKey = entryKey,
                            matchKey = entryKey,
                            matchSource = ChannelMatchSource.PARENT,
                        )
                    }
                }
            }
        }
    }

    // Wildcard fallback
    if (direct.wildcardEntry != null && direct.wildcardKey != null) {
        return direct.copy(
            entry = direct.wildcardEntry,
            key = direct.wildcardKey,
            matchKey = direct.wildcardKey,
            matchSource = ChannelMatchSource.WILDCARD,
        )
    }

    return direct
}

// ---------------------------------------------------------------------------
// Nested allowlist decision  (aligned with TS resolveNestedAllowlistDecision)
// ---------------------------------------------------------------------------

fun resolveNestedAllowlistDecision(
    outerConfigured: Boolean,
    outerMatched: Boolean,
    innerConfigured: Boolean,
    innerMatched: Boolean,
): Boolean {
    if (!outerConfigured) return true
    if (!outerMatched) return false
    if (!innerConfigured) return true
    return innerMatched
}
