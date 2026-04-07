package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/model.ts
 *
 * Extract /model directive from inbound message text.
 * Supports /model provider/model@profile and alias directives.
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Result of extracting a model directive from message text.
 */
data class ModelDirectiveResult(
    /** Message text with the directive removed. */
    val cleaned: String,
    /** Raw model reference (provider/model). */
    val rawModel: String? = null,
    /** Raw auth profile (after @ in provider/model@profile). */
    val rawProfile: String? = null,
    /** Whether a directive was found. */
    val hasDirective: Boolean
)

// ============================================================================
// Directive extraction (aligned with OpenClaw extractModelDirective)
// ============================================================================

/**
 * Extract a /model directive from message text.
 * Aligned with OpenClaw extractModelDirective.
 *
 * Supports:
 * - /model provider/model
 * - /model provider/model@profile
 * - /alias (when aliases are provided)
 */
fun extractModelDirective(
    body: String?,
    aliases: List<String>? = null
): ModelDirectiveResult {
    if (body.isNullOrEmpty()) {
        return ModelDirectiveResult(cleaned = "", hasDirective = false)
    }

    // Try /model directive first
    val modelRegex = Regex("(?:^|\\s)/model(?=$|\\s|:)\\s*:?\\s*([A-Za-z0-9_.:@-]+(?:/[A-Za-z0-9_.:@-]+)*)?", RegexOption.IGNORE_CASE)
    val modelMatch = modelRegex.find(body)

    // Try alias directives
    val cleanAliases = aliases?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val aliasMatch = if (modelMatch == null && cleanAliases.isNotEmpty()) {
        val aliasPattern = cleanAliases.joinToString("|") { Regex.escape(it) }
        val aliasRegex = Regex("(?:^|\\s)/($aliasPattern)(?=$|\\s|:)(?:\\s*:\\s*)?", RegexOption.IGNORE_CASE)
        aliasRegex.find(body)
    } else null

    val match = modelMatch ?: aliasMatch
    val raw = if (modelMatch != null) {
        modelMatch.groupValues.getOrNull(1)?.trim()?.ifEmpty { null }
    } else {
        aliasMatch?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null }
    }

    // Split trailing @profile from model reference
    var rawModel = raw
    var rawProfile: String? = null
    if (raw != null) {
        val split = splitTrailingAuthProfile(raw)
        rawModel = split.first
        rawProfile = split.second
    }

    val cleaned = if (match != null) {
        body.replaceFirst(match.value, " ").replace(Regex("\\s+"), " ").trim()
    } else {
        body.trim()
    }

    return ModelDirectiveResult(
        cleaned = cleaned,
        rawModel = rawModel,
        rawProfile = rawProfile,
        hasDirective = match != null
    )
}

/**
 * Split a trailing @profile from a model reference string.
 * Aligned with OpenClaw splitTrailingAuthProfile.
 *
 * Examples:
 * - "openai/gpt-4" -> ("openai/gpt-4", null)
 * - "openai/gpt-4@work" -> ("openai/gpt-4", "work")
 */
private fun splitTrailingAuthProfile(ref: String): Pair<String, String?> {
    // Find the last @ that is not part of the model name
    // Model names can contain dots, dashes, underscores
    // Profile is the part after the last @ outside a path segment
    val lastAt = ref.lastIndexOf('@')
    if (lastAt <= 0 || lastAt == ref.length - 1) {
        return ref to null
    }

    val model = ref.substring(0, lastAt)
    val profile = ref.substring(lastAt + 1)

    // Validate profile looks like an identifier
    if (profile.isEmpty() || !profile.matches(Regex("[A-Za-z0-9_.-]+"))) {
        return ref to null
    }

    return model to profile
}
