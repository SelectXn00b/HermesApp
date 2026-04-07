package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-label.ts
 *
 * AndroidForClaw adaptation: session label parsing and validation.
 */

/** Maximum allowed length for a session label. */
const val SESSION_LABEL_MAX_LENGTH = 512

/**
 * Result of parsing a raw session label value.
 */
sealed class ParsedSessionLabel {
    data class Ok(val label: String) : ParsedSessionLabel()
    data class Error(val error: String) : ParsedSessionLabel()
}

/**
 * Parse and validate a raw session label.
 *
 * @param raw The raw value to parse (expected to be a [String]).
 * @return [ParsedSessionLabel.Ok] with the trimmed label, or [ParsedSessionLabel.Error].
 */
fun parseSessionLabel(raw: Any?): ParsedSessionLabel {
    if (raw !is String) {
        return ParsedSessionLabel.Error("invalid label: must be a string")
    }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return ParsedSessionLabel.Error("invalid label: empty")
    }
    if (trimmed.length > SESSION_LABEL_MAX_LENGTH) {
        return ParsedSessionLabel.Error("invalid label: too long (max $SESSION_LABEL_MAX_LENGTH)")
    }
    return ParsedSessionLabel.Ok(trimmed)
}
