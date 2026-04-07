package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/tokens.ts
 *
 * Silent reply tokens: HEARTBEAT_OK, NO_REPLY.
 * Detection and stripping utilities for token-based reply suppression.
 */

// ============================================================================
// Token constants (aligned with OpenClaw tokens.ts)
// ============================================================================

const val HEARTBEAT_TOKEN = "HEARTBEAT_OK"
const val SILENT_REPLY_TOKEN = "NO_REPLY"

// ============================================================================
// Regex caches (aligned with OpenClaw silentExactRegexByToken / silentTrailingRegexByToken)
// ============================================================================

private val silentExactRegexCache = HashMap<String, Regex>()
private val silentTrailingRegexCache = HashMap<String, Regex>()

private fun getSilentExactRegex(token: String): Regex {
    return silentExactRegexCache.getOrPut(token) {
        val escaped = Regex.escape(token)
        Regex("^\\s*$escaped\\s*$", RegexOption.IGNORE_CASE)
    }
}

private fun getSilentTrailingRegex(token: String): Regex {
    return silentTrailingRegexCache.getOrPut(token) {
        val escaped = Regex.escape(token)
        Regex("(?:^|\\s+|\\*+)$escaped\\s*$")
    }
}

// ============================================================================
// Silent reply detection (aligned with OpenClaw tokens.ts)
// ============================================================================

/**
 * Check if text is exactly the silent reply token (with optional whitespace).
 * Aligned with OpenClaw isSilentReplyText.
 */
fun isSilentReplyText(text: String?, token: String = SILENT_REPLY_TOKEN): Boolean {
    if (text.isNullOrEmpty()) return false
    return getSilentExactRegex(token).containsMatchIn(text)
}

/**
 * Check if text is a JSON envelope containing the silent token as action.
 * Aligned with OpenClaw isSilentReplyEnvelopeText.
 */
fun isSilentReplyEnvelopeText(text: String?, token: String = SILENT_REPLY_TOKEN): Boolean {
    if (text.isNullOrEmpty()) return false
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}") || !trimmed.contains(token)) {
        return false
    }
    return try {
        // Simple JSON parse: {"action":"NO_REPLY"}
        val org = org.json.JSONObject(trimmed)
        val keys = org.keys().asSequence().toList()
        keys.size == 1 && keys[0] == "action" &&
            org.optString("action").trim() == token
    } catch (_: Exception) {
        false
    }
}

/**
 * Check if text is a silent reply (exact text or JSON envelope).
 * Aligned with OpenClaw isSilentReplyPayloadText.
 */
fun isSilentReplyPayloadText(text: String?, token: String = SILENT_REPLY_TOKEN): Boolean {
    return isSilentReplyText(text, token) || isSilentReplyEnvelopeText(text, token)
}

/**
 * Strip a trailing silent reply token from mixed-content text.
 * Aligned with OpenClaw stripSilentToken.
 */
fun stripSilentToken(text: String, token: String = SILENT_REPLY_TOKEN): String {
    return text.replace(getSilentTrailingRegex(token), "").trim()
}

/**
 * Check if text is a prefix of the silent reply token being streamed.
 * Aligned with OpenClaw isSilentReplyPrefixText.
 */
fun isSilentReplyPrefixText(text: String?, token: String = SILENT_REPLY_TOKEN): Boolean {
    if (text.isNullOrEmpty()) return false
    val trimmed = text.trimStart()
    if (trimmed.isEmpty()) return false

    // Guard against suppressing natural-language "No..." text while still
    // catching uppercase lead fragments like "NO" from streamed NO_REPLY.
    if (trimmed != trimmed.uppercase()) return false
    val normalized = trimmed.uppercase()
    if (normalized.length < 2) return false
    if (Regex("[^A-Z_]").containsMatchIn(normalized)) return false

    val tokenUpper = token.uppercase()
    if (!tokenUpper.startsWith(normalized)) return false

    if (normalized.contains("_")) return true

    // Only allow bare "NO" for NO_REPLY token streaming
    return tokenUpper == SILENT_REPLY_TOKEN && normalized == "NO"
}
