package com.xiaomo.androidforclaw.util

/**
 * Parses Rive expression tags from AI reply text.
 *
 * Supported formats:
 *   [rive:happy]                — named emotion
 *   [rive:2.5]                  — direct Expressions number
 *   [rive:happy,seasonal=2]     — named emotion + key=value overrides
 *   [rive:expressions=3,seasonal=1] — all key=value
 */
object RiveEmotionTagParser {
    // Matches [rive:...] with arbitrary content inside
    private val TAG_RE = Regex("""\[rive:([^\]]+)]""", RegexOption.IGNORE_CASE)

    data class Result(
        val cleanText: String,
        val emotion: String?,           // named emotion tag (e.g. "happy") or null
        val expressionValue: Float?,     // direct numeric Expressions value or null
        val extras: Map<String, String>, // key=value pairs (e.g. "seasonal" -> "2")
    )

    fun parse(text: String): Result {
        val match = TAG_RE.find(text) ?: return Result(text, null, null, emptyMap())
        val cleaned = TAG_RE.replace(text, "").trimEnd()

        val inner = match.groupValues[1].trim()
        val parts = inner.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        var emotion: String? = null
        var expressionValue: Float? = null
        val extras = mutableMapOf<String, String>()

        for (part in parts) {
            if (part.contains("=")) {
                // key=value pair
                val (key, value) = part.split("=", limit = 2).map { it.trim() }
                extras[key.lowercase()] = value
            } else {
                // Either a named emotion or direct number
                val asFloat = part.toFloatOrNull()
                if (asFloat != null) {
                    expressionValue = asFloat
                } else {
                    emotion = part.lowercase()
                }
            }
        }

        return Result(cleaned, emotion, expressionValue, extras)
    }
}
