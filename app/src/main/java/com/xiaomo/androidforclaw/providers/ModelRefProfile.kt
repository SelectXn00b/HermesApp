package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-ref-profile.ts
 *
 * AndroidForClaw adaptation: split trailing auth profile from model reference.
 */

/**
 * Result of splitting a model reference with an optional auth profile suffix.
 * Aligned with OpenClaw splitTrailingAuthProfile return type.
 */
data class ModelRefProfileResult(
    val model: String,
    val profile: String? = null
)

/**
 * Model reference profile splitting.
 * Aligned with OpenClaw model-ref-profile.ts.
 */
object ModelRefProfile {

    private val DATE_VERSION_PATTERN = Regex("^\\d{8}(?:@|$)")

    /**
     * Split trailing auth profile from a model reference string.
     * Handles date-versioned suffixes like `@20250101` by skipping to the next `@`.
     *
     * Examples:
     * - "anthropic/claude-opus-4-6" → model="anthropic/claude-opus-4-6", profile=null
     * - "anthropic/claude-opus-4-6@myprofile" → model="anthropic/claude-opus-4-6", profile="myprofile"
     * - "anthropic/claude-opus-4-6@20250101@myprofile" → model="anthropic/claude-opus-4-6@20250101", profile="myprofile"
     *
     * Aligned with OpenClaw splitTrailingAuthProfile.
     */
    fun splitTrailingAuthProfile(raw: String): ModelRefProfileResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ModelRefProfileResult(model = "")
        }

        val lastSlash = trimmed.lastIndexOf('/')
        var profileDelimiter = trimmed.indexOf('@', lastSlash + 1)
        if (profileDelimiter <= 0) {
            return ModelRefProfileResult(model = trimmed)
        }

        // Check for date version suffix (8 digits after @)
        val versionSuffix = trimmed.substring(profileDelimiter + 1)
        if (DATE_VERSION_PATTERN.containsMatchIn(versionSuffix)) {
            val nextDelimiter = trimmed.indexOf('@', profileDelimiter + 9)
            if (nextDelimiter < 0) {
                return ModelRefProfileResult(model = trimmed)
            }
            profileDelimiter = nextDelimiter
        }

        val model = trimmed.substring(0, profileDelimiter).trim()
        val profile = trimmed.substring(profileDelimiter + 1).trim()
        if (model.isEmpty() || profile.isEmpty()) {
            return ModelRefProfileResult(model = trimmed)
        }

        return ModelRefProfileResult(model = model, profile = profile)
    }
}
