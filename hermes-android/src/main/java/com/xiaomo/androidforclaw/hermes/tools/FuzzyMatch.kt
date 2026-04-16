package com.xiaomo.androidforclaw.hermes.tools

import java.util.regex.Pattern

/**
 * Fuzzy Matching Module for File Operations.
 * Multi-strategy matching chain to find and replace text.
 * Ported from fuzzy_match.py
 */
object FuzzyMatch {

    private val UNICODE_MAP = mapOf(
        "\u201c" to "\"", "\u201d" to "\"",   // smart double quotes
        "\u2018" to "'", "\u2019" to "'",     // smart single quotes
        "\u2014" to "--", "\u2013" to "-",    // em/en dashes
        "\u2026" to "...", "\u00a0" to " ",   // ellipsis and non-breaking space
    )

    private fun unicodeNormalize(text: String): String {
        var result = text
        for ((char, repl) in UNICODE_MAP) {
            result = result.replace(char, repl)
        }
        return result
    }

    data class FuzzyReplaceResult(
        val content: String,
        val matchCount: Int,
        val strategy: String?,
        val error: String?)

    /**
     * Find and replace text using a chain of increasingly fuzzy matching strategies.
     */
    fun fuzzyFindAndReplace(
        content: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false): FuzzyReplaceResult {
        if (oldString.isEmpty()) return FuzzyReplaceResult(content, 0, null, "old_string cannot be empty")
        if (oldString == newString) return FuzzyReplaceResult(content, 0, null, "old_string and new_string are identical")

        val strategies = listOf(
            "exact" to ::strategyExact,
            "line_trimmed" to ::strategyLineTrimmed,
            "whitespace_normalized" to ::strategyWhitespaceNormalized,
            "indentation_flexible" to ::strategyIndentationFlexible,
            "escape_normalized" to ::strategyEscapeNormalized,
            "trimmed_boundary" to ::strategyTrimmedBoundary,
            "unicode_normalized" to ::strategyUnicodeNormalized,
            "block_anchor" to ::strategyBlockAnchor,
            "context_aware" to ::strategyContextAware)

        for ((name, strategyFn) in strategies) {
            val matches = strategyFn(content, oldString)
            if (matches.isNotEmpty()) {
                if (matches.size > 1 && !replaceAll) {
                    return FuzzyReplaceResult(content, 0, null,
                        "Found ${matches.size} matches for old_string. Provide more context or use replace_all=true.")
                }
                val newContent = applyReplacements(content, matches, newString)
                return FuzzyReplaceResult(newContent, matches.size, name, null)
            }
        }

        return FuzzyReplaceResult(content, 0, null, "Could not find a match for old_string in the file")
    }

    private fun applyReplacements(content: String, matches: List<Pair<Int, Int>>, newString: String): String {
        val sorted = matches.sortedByDescending { it.first }
        var result = content
        for ((start, end) in sorted) {
            result = result.substring(0, start) + newString + result.substring(end)
        }
        return result
    }

    // Strategy 1: Exact string match
    private fun strategyExact(content: String, pattern: String): List<Pair<Int, Int>> {
        val matches = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (true) {
            val pos = content.indexOf(pattern, start)
            if (pos == -1) break
            matches.add(pos to pos + pattern.length)
            start = pos + 1
        }
        return matches
    }

    // Strategy 2: Match with line-by-line whitespace trimming
    private fun strategyLineTrimmed(content: String, pattern: String): List<Pair<Int, Int>> {
        val patternNormalized = pattern.split('\n').joinToString("\n") { it.trim() }
        val contentLines = content.split('\n')
        val contentNormalized = contentLines.map { it.trim() }
        return findNormalizedMatches(content, contentLines, contentNormalized, pattern, patternNormalized)
    }

    // Strategy 3: Collapse multiple whitespace to single space
    private fun strategyWhitespaceNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
        val wsRegex = Regex("[ \t]+")
        val normPattern = wsRegex.replace(pattern, " ")
        val normContent = wsRegex.replace(content, " ")
        return strategyExact(normContent, normPattern)
    }

    // Strategy 4: Ignore indentation differences
    private fun strategyIndentationFlexible(content: String, pattern: String): List<Pair<Int, Int>> {
        val contentLines = content.split('\n')
        val contentStripped = contentLines.map { it.trimStart() }
        val patternLines = pattern.split('\n').map { it.trimStart() }
        return findNormalizedMatches(content, contentLines, contentStripped, pattern, patternLines.joinToString("\n"))
    }

    // Strategy 5: Convert escape sequences
    private fun strategyEscapeNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
        val unescaped = pattern.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
        if (unescaped == pattern) return emptyList()
        return strategyExact(content, unescaped)
    }

    // Strategy 6: Trim first and last lines only
    private fun strategyTrimmedBoundary(content: String, pattern: String): List<Pair<Int, Int>> {
        val patternLines = pattern.split('\n').toMutableList()
        if (patternLines.isEmpty()) return emptyList()
        patternLines[0] = patternLines[0].trim()
        if (patternLines.size > 1) patternLines[patternLines.lastIndex] = patternLines.last().trim()

        val contentLines = content.split('\n')
        val matches = mutableListOf<Pair<Int, Int>>()
        val patternLineCount = patternLines.size

        for (i in 0..contentLines.size - patternLineCount) {
            val block = contentLines.subList(i, i + patternLineCount).toMutableList()
            block[0] = block[0].trim()
            if (block.size > 1) block[block.lastIndex] = block.last().trim()

            if (block.joinToString("\n") == patternLines.joinToString("\n")) {
                val (startPos, endPos) = calculateLinePositions(contentLines, i, i + patternLineCount, content.length)
                matches.add(startPos to endPos)
            }
        }
        return matches
    }

    // Strategy 7: Unicode normalisation
    private fun strategyUnicodeNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
        val normPattern = unicodeNormalize(pattern)
        val normContent = unicodeNormalize(content)
        if (normContent == content && normPattern == pattern) return emptyList()
        return strategyExact(normContent, normPattern)
    }

    // Strategy 8: Match by anchoring on first and last lines
    private fun strategyBlockAnchor(content: String, pattern: String): List<Pair<Int, Int>> {
        val patternLines = pattern.split('\n')
        if (patternLines.size < 2) return emptyList()
        val firstLine = patternLines.first().trim()
        val lastLine = patternLines.last().trim()
        val contentLines = content.split('\n')
        val matches = mutableListOf<Pair<Int, Int>>()

        for (i in 0..contentLines.size - patternLines.size) {
            if (contentLines[i].trim() == firstLine && contentLines[i + patternLines.size - 1].trim() == lastLine) {
                if (patternLines.size <= 2) {
                    val (startPos, endPos) = calculateLinePositions(contentLines, i, i + patternLines.size, content.length)
                    matches.add(startPos to endPos)
                } else {
                    val contentMiddle = contentLines.subList(i + 1, i + patternLines.size - 1).joinToString("\n")
                    val patternMiddle = patternLines.subList(1, patternLines.size - 1).joinToString("\n")
                    val similarity = sequenceMatchSimilarity(contentMiddle, patternMiddle)
                    if (similarity >= 0.50) {
                        val (startPos, endPos) = calculateLinePositions(contentLines, i, i + patternLines.size, content.length)
                        matches.add(startPos to endPos)
                    }
                }
            }
        }
        return matches
    }

    // Strategy 9: Line-by-line similarity with 50% threshold
    private fun strategyContextAware(content: String, pattern: String): List<Pair<Int, Int>> {
        val patternLines = pattern.split('\n')
        val contentLines = content.split('\n')
        if (patternLines.isEmpty()) return emptyList()
        val matches = mutableListOf<Pair<Int, Int>>()

        for (i in 0..contentLines.size - patternLines.size) {
            val block = contentLines.subList(i, i + patternLines.size)
            var highSimCount = 0
            for ((pLine, cLine) in patternLines.zip(block)) {
                if (sequenceMatchSimilarity(pLine.trim(), cLine.trim()) >= 0.80) highSimCount++
            }
            if (highSimCount >= patternLines.size * 0.5) {
                val (startPos, endPos) = calculateLinePositions(contentLines, i, i + patternLines.size, content.length)
                matches.add(startPos to endPos)
            }
        }
        return matches
    }

    private fun calculateLinePositions(contentLines: List<String>, startLine: Int, endLine: Int, contentLength: Int): Pair<Int, Int> {
        val startPos = contentLines.take(startLine).sumOf { it.length + 1 }
        var endPos = contentLines.take(endLine).sumOf { it.length + 1 } - 1
        if (endPos >= contentLength) endPos = contentLength
        return startPos to endPos
    }

    private fun findNormalizedMatches(
        content: String,
        contentLines: List<String>,
        contentNormalizedLines: List<String>,
        pattern: String,
        patternNormalized: String): List<Pair<Int, Int>> {
        val patternNormLines = patternNormalized.split('\n')
        val patternLineCount = patternNormLines.size
        val matches = mutableListOf<Pair<Int, Int>>()

        for (i in 0..contentNormalizedLines.size - patternLineCount) {
            val block = contentNormalizedLines.subList(i, i + patternLineCount).joinToString("\n")
            if (block == patternNormalized) {
                val (startPos, endPos) = calculateLinePositions(contentLines, i, i + patternLineCount, content.length)
                matches.add(startPos to endPos)
            }
        }
        return matches
    }

    private fun sequenceMatchSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        // Simple character-level similarity
        val aChars = a.toCharArray()
        val bChars = b.toCharArray()
        val maxLen = maxOf(aChars.size, bChars.size)
        val commonLen = lcsLength(aChars, bChars)
        return (2.0 * commonLen) / (aChars.size + bChars.size)
    }

    private fun lcsLength(a: CharArray, b: CharArray): Int {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1
                else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
        return dp[m][n]
    }


}
