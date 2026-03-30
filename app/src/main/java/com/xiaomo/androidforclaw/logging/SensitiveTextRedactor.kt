package com.xiaomo.androidforclaw.logging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/logging/redact.ts (DEFAULT_REDACT_PATTERNS, maskToken, redactSensitiveText)
 *
 * AndroidForClaw adaptation: standalone sensitive text redaction utility.
 * Extracted from SessionsHistoryTool.Companion for reuse across:
 * - Session history reading (SessionsHistoryTool)
 * - Outbound message redaction (group chat contexts)
 * - General logging sanitization
 */

/**
 * SensitiveTextRedactor — Redact secrets from text.
 * Aligned with OpenClaw logging/redact.ts.
 */
object SensitiveTextRedactor {

    /** Maximum chars per field (aligned with OpenClaw SESSIONS_HISTORY_TEXT_MAX_CHARS) */
    const val TEXT_MAX_CHARS = 4000

    /** Maximum total output bytes (aligned with OpenClaw SESSIONS_HISTORY_MAX_BYTES) */
    const val MAX_BYTES = 80 * 1024  // 80KB

    /** Chunk size for bounded regex replacement to avoid catastrophic backtracking */
    const val REGEX_CHUNK_SIZE = 16_384

    /**
     * Sensitive text redaction patterns.
     * Aligned with OpenClaw DEFAULT_REDACT_PATTERNS from logging/redact.ts.
     */
    val REDACT_PATTERNS: List<Regex> by lazy {
        listOf(
            // ENV-style: KEY=value, TOKEN=value, SECRET=value, PASSWORD=value, PASSWD=value
            Regex("""(?i)((?:API[_-]?KEY|TOKEN|SECRET|PASSWORD|PASSWD)\s*[=:]\s*)(\S+)"""),
            // JSON fields: "apiKey": "...", "token": "...", etc.
            Regex("""(?i)("(?:api[_-]?key|token|secret|password|passwd|access[_-]?token|refresh[_-]?token)"\s*:\s*")([^"]+)"""),
            // CLI flags: --api-key value, --token value, etc.
            Regex("""(?i)(--(?:api-key|token|secret|password|passwd)\s+)(\S+)"""),
            // Authorization: Bearer ...
            Regex("""(Authorization:\s*Bearer\s+)(\S{18,})"""),
            // Bare Bearer token
            Regex("""(Bearer\s+)(\S{18,})"""),
            // PEM private keys
            Regex("""-----BEGIN\s+[A-Z\s]*PRIVATE KEY-----[\s\S]*?-----END\s+[A-Z\s]*PRIVATE KEY-----"""),
            // OpenAI-style: sk-...
            Regex("""(sk-[A-Za-z0-9]{8,})"""),
            // GitHub PATs: ghp_... and github_pat_...
            Regex("""(ghp_[A-Za-z0-9]{20,})"""),
            Regex("""(github_pat_[A-Za-z0-9]{20,})"""),
            // Slack tokens: xox[baprs]-... and xapp-...
            Regex("""(xox[baprs]-[A-Za-z0-9\-]{10,})"""),
            Regex("""(xapp-[A-Za-z0-9\-]{10,})"""),
            // Groq keys: gsk_...
            Regex("""(gsk_[A-Za-z0-9]{10,})"""),
            // Google AI keys: AIza...
            Regex("""(AIza[A-Za-z0-9_\-]{20,})"""),
            // Perplexity keys: pplx-...
            Regex("""(pplx-[A-Za-z0-9]{10,})"""),
            // npm tokens: npm_...
            Regex("""(npm_[A-Za-z0-9]{10,})"""),
            // Telegram bot tokens: 123456789:ABC-DEF...
            Regex("""(\d{8,10}:[A-Za-z0-9_\-]{30,})"""),
        )
    }

    /**
     * Mask a token value. Aligned with OpenClaw maskToken.
     * Short tokens (<18 chars) → "***"
     * Long tokens → first 6 + "..." + last 4
     */
    fun maskToken(token: String): String {
        return if (token.length < 18) {
            "***"
        } else {
            "${token.take(6)}...${token.takeLast(4)}"
        }
    }

    /**
     * Redact sensitive text patterns.
     * Aligned with OpenClaw redactSensitiveText.
     * Uses bounded replacement for large texts to avoid regex performance issues.
     */
    fun redactSensitiveText(text: String): Pair<String, Boolean> {
        if (text.isEmpty()) return Pair(text, false)

        var result = text
        var redacted = false

        for (pattern in REDACT_PATTERNS) {
            val newResult = if (result.length > REGEX_CHUNK_SIZE * 2) {
                replacePatternBounded(result, pattern)
            } else {
                pattern.replace(result) { match ->
                    redacted = true
                    when {
                        // PEM key block
                        match.value.startsWith("-----BEGIN") ->
                            "-----BEGIN PRIVATE KEY-----\n...redacted...\n-----END PRIVATE KEY-----"
                        // Patterns with prefix group + token group
                        match.groupValues.size >= 3 && match.groupValues[1].isNotEmpty() ->
                            "${match.groupValues[1]}${maskToken(match.groupValues[2])}"
                        // Standalone token patterns (sk-, ghp_, etc.)
                        else -> maskToken(match.value)
                    }
                }
            }
            if (newResult != result) {
                redacted = true
                result = newResult
            }
        }

        return Pair(result, redacted)
    }

    /**
     * Bounded regex replacement for large texts.
     * Aligned with OpenClaw replacePatternBounded.
     * Processes in chunks to avoid catastrophic backtracking.
     */
    fun replacePatternBounded(text: String, pattern: Regex): String {
        val sb = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            val end = minOf(offset + REGEX_CHUNK_SIZE, text.length)
            val chunk = text.substring(offset, end)
            sb.append(pattern.replace(chunk) { match ->
                when {
                    match.value.startsWith("-----BEGIN") ->
                        "-----BEGIN PRIVATE KEY-----\n...redacted...\n-----END PRIVATE KEY-----"
                    match.groupValues.size >= 3 && match.groupValues[1].isNotEmpty() ->
                        "${match.groupValues[1]}${maskToken(match.groupValues[2])}"
                    else -> maskToken(match.value)
                }
            })
            offset = end
        }
        return sb.toString()
    }

    /**
     * Truncate and redact history text.
     * Aligned with OpenClaw truncateHistoryText.
     * Returns (sanitized text, truncated, redacted).
     */
    fun truncateHistoryText(text: String): Triple<String, Boolean, Boolean> {
        val (redactedText, wasRedacted) = redactSensitiveText(text)
        return if (redactedText.length > TEXT_MAX_CHARS) {
            Triple(redactedText.take(TEXT_MAX_CHARS) + "\n...(truncated)...", true, wasRedacted)
        } else {
            Triple(redactedText, false, wasRedacted)
        }
    }
}
