package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/level-overrides.ts
 *
 * AndroidForClaw adaptation: verbose-level override parsing and application.
 *
 * The TS version references `normalizeVerboseLevel` from auto-reply/thinking.ts
 * which normalises fuzzy strings ("on"/"off"/"full"/etc.) into the `VerboseLevel`
 * union. That logic is inlined here to avoid a cross-package dependency on an
 * auto-reply module that may not yet be ported.
 *
 * Session entries are represented as `MutableMap<String, Any?>`.
 */

// ---------------------------------------------------------------------------
// Verbose level type (aligned with OpenClaw VerboseLevel = "off" | "on" | "full")
// ---------------------------------------------------------------------------

/**
 * Verbose-level values.
 */
enum class VerboseLevel(val value: String) {
    OFF("off"),
    ON("on"),
    FULL("full");
}

// ---------------------------------------------------------------------------
// Inline normalizer (aligned with auto-reply/thinking.shared.ts)
// ---------------------------------------------------------------------------

/**
 * Normalize a raw verbose-level string.
 *
 * Accepts fuzzy inputs: "off"/"false"/"no"/"0", "on"/"minimal"/"true"/"yes"/"1",
 * "full"/"all"/"everything".
 */
private fun normalizeVerboseLevel(raw: String?): VerboseLevel? {
    if (raw.isNullOrBlank()) return null
    return when (raw.trim().lowercase()) {
        "off", "false", "no", "0" -> VerboseLevel.OFF
        "full", "all", "everything" -> VerboseLevel.FULL
        "on", "minimal", "true", "yes", "1" -> VerboseLevel.ON
        else -> null
    }
}

// ---------------------------------------------------------------------------
// Parse result
// ---------------------------------------------------------------------------

/**
 * Result of parsing a verbose-level override value.
 */
sealed class VerboseLevelParseResult {
    /**
     * Parsed successfully.
     *
     * [value] semantics:
     * - A [VerboseLevel] instance: set the level.
     * - `null` literal (`value == null`): clear / reset the override.
     *
     * The original TS type uses `null | undefined` to distinguish "clear" from "no-op".
     * In Kotlin we use a nullable wrapper: `Ok(null)` = clear, the caller can also
     * check for [VerboseLevel] directly.
     */
    data class Ok(val value: VerboseLevel?) : VerboseLevelParseResult()

    data class Error(val error: String) : VerboseLevelParseResult()
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Parse a raw verbose-override value.
 *
 * - `null` input -> Ok(null)         (means "clear the override")
 * - valid string -> Ok(VerboseLevel) (set)
 * - invalid      -> Error(message)
 *
 * Note: The TS version distinguishes between JS `null` and `undefined` to represent
 * "clear" vs "no-op". In Kotlin both map to `null`; callers should treat `Ok(null)`
 * as "clear the field" and the absence of a call as "no-op".
 */
fun parseVerboseOverride(raw: Any?): VerboseLevelParseResult {
    if (raw == null) {
        return VerboseLevelParseResult.Ok(null)
    }
    if (raw !is String) {
        return VerboseLevelParseResult.Error("invalid verboseLevel (use \"on\"|\"off\")")
    }
    val normalized = normalizeVerboseLevel(raw)
        ?: return VerboseLevelParseResult.Error("invalid verboseLevel (use \"on\"|\"off\")")
    return VerboseLevelParseResult.Ok(normalized)
}

/**
 * Apply a verbose-level override to a session entry.
 *
 * - `null` level: remove the `verboseLevel` field from the entry.
 * - non-null level: set `verboseLevel` to the level's string value.
 *
 * Pass `Unit`-like "do nothing" by simply not calling this function.
 */
fun applyVerboseOverride(entry: MutableMap<String, Any?>, level: VerboseLevel?) {
    if (level == null) {
        entry.remove("verboseLevel")
        return
    }
    entry["verboseLevel"] = level.value
}
