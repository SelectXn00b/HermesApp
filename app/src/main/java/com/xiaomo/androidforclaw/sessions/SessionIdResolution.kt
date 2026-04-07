package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-id-resolution.ts
 *
 * AndroidForClaw adaptation: session-ID-to-key resolution with alias collapsing
 * and freshest-match selection.
 *
 * Session entries are represented as `Map<String, Any?>`.
 *
 * The TS version references `toAgentRequestSessionKey` from routing/session-key.ts.
 * That function is equivalent to `parseAgentSessionKey(key)?.rest ?: key`,
 * which is inlined here to avoid a cross-package dependency.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Result of resolving a session ID to one or more session keys.
 */
sealed class SessionIdMatchSelection {
    /** No matching session keys found. */
    data object None : SessionIdMatchSelection()

    /** Multiple ambiguous matches; caller must disambiguate. */
    data class Ambiguous(val sessionKeys: List<String>) : SessionIdMatchSelection()

    /** A single session key was selected. */
    data class Selected(val sessionKey: String) : SessionIdMatchSelection()
}

// ---------------------------------------------------------------------------
// Internal types & helpers
// ---------------------------------------------------------------------------

private data class NormalizedSessionIdMatch(
    val sessionKey: String,
    val entry: Map<String, Any?>,
    val normalizedSessionKey: String,
    val normalizedRequestKey: String,
    val isCanonicalSessionKey: Boolean,
    val isStructural: Boolean
)

private fun normalizeLookupKey(value: String): String = value.trim().lowercase()

/**
 * Equivalent to OpenClaw `toAgentRequestSessionKey` — strips agent prefix.
 */
private fun toAgentRequestSessionKey(storeKey: String): String {
    val raw = storeKey.trim()
    if (raw.isEmpty()) return raw
    return parseAgentSessionKey(raw)?.rest ?: raw
}

private fun compareNormalizedUpdatedAtDescending(
    a: NormalizedSessionIdMatch,
    b: NormalizedSessionIdMatch
): Int {
    val aTime = (a.entry["updatedAt"] as? Number)?.toLong() ?: 0L
    val bTime = (b.entry["updatedAt"] as? Number)?.toLong() ?: 0L
    return bTime.compareTo(aTime)
}

private fun normalizeSessionIdMatches(
    matches: List<Pair<String, Map<String, Any?>>>,
    normalizedSessionId: String
): List<NormalizedSessionIdMatch> {
    return matches.map { (sessionKey, entry) ->
        val normalizedSessionKey = normalizeLookupKey(sessionKey)
        val normalizedRequestKey = normalizeLookupKey(toAgentRequestSessionKey(sessionKey))
        NormalizedSessionIdMatch(
            sessionKey = sessionKey,
            entry = entry,
            normalizedSessionKey = normalizedSessionKey,
            normalizedRequestKey = normalizedRequestKey,
            isCanonicalSessionKey = sessionKey == normalizedSessionKey,
            isStructural =
                normalizedSessionKey.endsWith(":$normalizedSessionId") ||
                        normalizedRequestKey == normalizedSessionId ||
                        normalizedRequestKey.endsWith(":$normalizedSessionId")
        )
    }
}

private fun collapseAliasMatches(
    matches: List<NormalizedSessionIdMatch>
): List<NormalizedSessionIdMatch> {
    val grouped = linkedMapOf<String, MutableList<NormalizedSessionIdMatch>>()
    for (match in matches) {
        grouped.getOrPut(match.normalizedRequestKey) { mutableListOf() }.add(match)
    }
    return grouped.values.map { group ->
        if (group.size == 1) {
            group[0]
        } else {
            group.sortedWith(Comparator { a, b ->
                val timeDiff = compareNormalizedUpdatedAtDescending(a, b)
                if (timeDiff != 0) return@Comparator timeDiff
                if (a.isCanonicalSessionKey != b.isCanonicalSessionKey) {
                    return@Comparator if (a.isCanonicalSessionKey) -1 else 1
                }
                a.normalizedSessionKey.compareTo(b.normalizedSessionKey)
            }).first()
        }
    }
}

private fun selectFreshestUniqueMatch(
    matches: List<NormalizedSessionIdMatch>
): NormalizedSessionIdMatch? {
    if (matches.size == 1) return matches[0]
    val sorted = matches.sortedWith(Comparator(::compareNormalizedUpdatedAtDescending))
    val freshest = sorted.getOrNull(0)
    val secondFreshest = sorted.getOrNull(1)
    val freshestTime = (freshest?.entry?.get("updatedAt") as? Number)?.toLong() ?: 0L
    val secondTime = (secondFreshest?.entry?.get("updatedAt") as? Number)?.toLong() ?: 0L
    return if (freshestTime > secondTime) freshest else null
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Resolve a set of session-ID matches to a single selection (or ambiguity).
 *
 * @param matches   List of (sessionKey, sessionEntry) pairs whose session-ID matches.
 * @param sessionId The session ID that was looked up.
 */
fun resolveSessionIdMatchSelection(
    matches: List<Pair<String, Map<String, Any?>>>,
    sessionId: String
): SessionIdMatchSelection {
    if (matches.isEmpty()) return SessionIdMatchSelection.None

    val canonicalMatches = collapseAliasMatches(
        normalizeSessionIdMatches(matches, normalizeLookupKey(sessionId))
    )
    if (canonicalMatches.size == 1) {
        return SessionIdMatchSelection.Selected(canonicalMatches[0].sessionKey)
    }

    val structuralMatches = canonicalMatches.filter { it.isStructural }
    val selectedStructural = selectFreshestUniqueMatch(structuralMatches)
    if (selectedStructural != null) {
        return SessionIdMatchSelection.Selected(selectedStructural.sessionKey)
    }
    if (structuralMatches.size > 1) {
        return SessionIdMatchSelection.Ambiguous(structuralMatches.map { it.sessionKey })
    }

    val selectedCanonical = selectFreshestUniqueMatch(canonicalMatches)
    if (selectedCanonical != null) {
        return SessionIdMatchSelection.Selected(selectedCanonical.sessionKey)
    }

    return SessionIdMatchSelection.Ambiguous(canonicalMatches.map { it.sessionKey })
}

/**
 * Convenience: resolve matches and return the selected session key, or `null`
 * if none or ambiguous.
 */
fun resolvePreferredSessionKeyForSessionIdMatches(
    matches: List<Pair<String, Map<String, Any?>>>,
    sessionId: String
): String? {
    val selection = resolveSessionIdMatchSelection(matches, sessionId)
    return if (selection is SessionIdMatchSelection.Selected) selection.sessionKey else null
}
