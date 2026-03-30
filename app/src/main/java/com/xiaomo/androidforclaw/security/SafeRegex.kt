package com.xiaomo.androidforclaw.security

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/security/safe-regex.ts
 *   (compileSafeRegex, compileSafeRegexDetailed, hasNestedRepetition, testRegexWithBoundedInput)
 *
 * AndroidForClaw adaptation: safe regex compilation with ReDoS protection.
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * Rejection reasons for unsafe regex patterns.
 * Aligned with OpenClaw SafeRegexRejectReason.
 */
enum class SafeRegexRejectReason {
    EMPTY,
    UNSAFE_NESTED_REPETITION,
    INVALID_REGEX
}

/**
 * Result of safe regex compilation.
 * Aligned with OpenClaw SafeRegexCompileResult.
 */
data class SafeRegexCompileResult(
    val regex: Regex?,
    val source: String,
    val flags: String,
    val reason: SafeRegexRejectReason?
)

/**
 * SafeRegex — Compile user-supplied regex patterns safely.
 * Aligned with OpenClaw safe-regex.ts.
 */
object SafeRegex {

    private const val TAG = "SafeRegex"

    const val CACHE_MAX = 256
    const val TEST_WINDOW = 2048

    /** LRU cache. Key format: "${flags}::${source}" (aligned with OpenClaw). */
    private val cache = LinkedHashMap<String, SafeRegexCompileResult>(
        CACHE_MAX, 0.75f, true
    )

    /**
     * Check if a regex source has nested repetition (ReDoS-prone).
     * Aligned with OpenClaw hasNestedRepetition.
     *
     * Uses a stack-based parser that tracks:
     * - Whether a group contains a quantifier
     * - Whether alternation branches have different lengths (ambiguous alternation)
     * - Nested quantifier detection: group with quantifier followed by outer quantifier
     */
    fun hasNestedRepetition(source: String): Boolean {
        data class Frame(
            var containsRepetition: Boolean = false,
            var hasAlternation: Boolean = false,
            var branchMinLength: Int = 0,
            var branchMaxLength: Int = 0,
            var altMinLength: Int? = null,
            var altMaxLength: Int? = null
        )

        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame())  // root frame

        var i = 0
        while (i < source.length) {
            val c = source[i]
            val frame = stack.last()

            when {
                c == '\\' -> {
                    i += 2  // skip escaped char
                    frame.branchMinLength++
                    frame.branchMaxLength++
                    continue
                }
                c == '[' -> {
                    // Skip entire character class
                    i++
                    while (i < source.length && source[i] != ']') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    frame.branchMinLength++
                    frame.branchMaxLength++
                }
                c == '(' -> {
                    stack.addLast(Frame())
                }
                c == ')' -> {
                    if (stack.size <= 1) { i++; continue }

                    val closed = stack.removeAt(stack.lastIndex)
                    val parent = stack.last()

                    // Finalize alternation for the closed group
                    val finalMinLen = if (closed.altMinLength != null) {
                        minOf(closed.altMinLength!!, closed.branchMinLength)
                    } else closed.branchMinLength
                    val finalMaxLen = if (closed.altMaxLength != null) {
                        maxOf(closed.altMaxLength!!, closed.branchMaxLength)
                    } else closed.branchMaxLength

                    val hasAmbiguousAlternation = closed.hasAlternation && finalMinLen != finalMaxLen

                    // Check if next char is a quantifier
                    val nextIdx = i + 1
                    val hasOuterQuantifier = nextIdx < source.length &&
                        (source[nextIdx] == '+' || source[nextIdx] == '*' ||
                            source[nextIdx] == '{' || source[nextIdx] == '?')

                    if (hasOuterQuantifier) {
                        // Nested repetition: group with inner quantifier, then outer quantifier
                        if (closed.containsRepetition) return true
                        // Ambiguous alternation under quantifier
                        if (hasAmbiguousAlternation) return true

                        parent.containsRepetition = true
                    }

                    if (closed.containsRepetition) parent.containsRepetition = true
                    parent.branchMinLength += finalMinLen
                    parent.branchMaxLength += finalMaxLen
                }
                c == '|' -> {
                    frame.hasAlternation = true
                    // Store branch lengths
                    frame.altMinLength = if (frame.altMinLength != null) {
                        minOf(frame.altMinLength!!, frame.branchMinLength)
                    } else frame.branchMinLength
                    frame.altMaxLength = if (frame.altMaxLength != null) {
                        maxOf(frame.altMaxLength!!, frame.branchMaxLength)
                    } else frame.branchMaxLength
                    frame.branchMinLength = 0
                    frame.branchMaxLength = 0
                }
                c == '+' || c == '*' -> {
                    frame.containsRepetition = true
                    if (c == '*') frame.branchMinLength = 0
                }
                c == '{' -> {
                    val closeBrace = source.indexOf('}', i)
                    if (closeBrace > i) {
                        val quantifier = source.substring(i, closeBrace + 1)
                        if (quantifier.matches(Regex("\\{\\d+,\\d*}"))) {
                            frame.containsRepetition = true
                            i = closeBrace
                        }
                    }
                }
                c == '?' -> {
                    // Lazy modifier after quantifier, or optional (0-1)
                    // Don't mark as repetition for simple ?
                }
                else -> {
                    frame.branchMinLength++
                    frame.branchMaxLength++
                }
            }
            i++
        }

        return false
    }

    /**
     * Compile a regex safely with detailed result.
     * Aligned with OpenClaw compileSafeRegexDetailed.
     * Cache key format: "${flags}::${trimmedSource}" (aligned with OpenClaw).
     */
    fun compileDetailed(source: String, flags: String = ""): SafeRegexCompileResult {
        val trimmed = source.trim()
        val cacheKey = "${flags}::${trimmed}"

        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        val result = when {
            trimmed.isBlank() -> SafeRegexCompileResult(null, trimmed, flags, SafeRegexRejectReason.EMPTY)
            hasNestedRepetition(trimmed) -> {
                Log.w(TAG, "Rejected unsafe regex (nested repetition): $trimmed")
                SafeRegexCompileResult(null, trimmed, flags, SafeRegexRejectReason.UNSAFE_NESTED_REPETITION)
            }
            else -> {
                try {
                    val options = mutableSetOf<RegexOption>()
                    if ('i' in flags) options.add(RegexOption.IGNORE_CASE)
                    if ('m' in flags) options.add(RegexOption.MULTILINE)
                    if ('s' in flags) options.add(RegexOption.DOT_MATCHES_ALL)
                    val regex = Regex(trimmed, options)
                    SafeRegexCompileResult(regex, trimmed, flags, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid regex: $trimmed — ${e.message}")
                    SafeRegexCompileResult(null, trimmed, flags, SafeRegexRejectReason.INVALID_REGEX)
                }
            }
        }

        synchronized(cache) {
            if (cache.size >= CACHE_MAX) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
            cache[cacheKey] = result
        }

        return result
    }

    fun compile(source: String, flags: String = ""): Regex? {
        return compileDetailed(source, flags).regex
    }

    /**
     * Test regex against bounded input.
     * Aligned with OpenClaw testRegexWithBoundedInput.
     */
    fun testWithBoundedInput(
        regex: Regex,
        input: String,
        maxWindow: Int = TEST_WINDOW
    ): Boolean {
        if (maxWindow <= 0) return false
        if (input.length <= maxWindow) {
            return regex.containsMatchIn(input)
        }
        val head = input.substring(0, maxWindow)
        if (regex.containsMatchIn(head)) return true
        val tail = input.substring(input.length - maxWindow)
        return regex.containsMatchIn(tail)
    }
}
