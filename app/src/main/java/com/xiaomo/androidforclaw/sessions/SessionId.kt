package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-id.ts
 *
 * AndroidForClaw adaptation: UUID-based session ID validation.
 */

/**
 * Regex pattern matching a standard UUID v4 string (case-insensitive).
 */
val SESSION_ID_RE = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE
)

/**
 * Returns `true` when [value] looks like a valid UUID session ID.
 */
fun looksLikeSessionId(value: String): Boolean {
    return SESSION_ID_RE.matches(value.trim())
}
