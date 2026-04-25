package com.xiaomo.hermes.hermes.tools

/**
 * Fuzzy Matching Module for File Operations
 * Ported from fuzzy_match.py
 *
 * Multi-strategy matching chain to robustly find and replace text,
 * accommodating variations in whitespace, indentation, and escaping.
 */

val UNICODE_MAP: Map<String, String> = mapOf(
    "\u201c" to "\"", "\u201d" to "\"",     // smart double quotes
    "\u2018" to "'", "\u2019" to "'",       // smart single quotes
    "\u2014" to "--", "\u2013" to "-",      // em/en dashes
    "\u2026" to "...", "\u00a0" to " "      // ellipsis and non-breaking space
)

private val _sequenceRatio: (String, String) -> Double = { a, b ->
    if (a.isEmpty() && b.isEmpty()) 1.0
    else if (a.isEmpty() || b.isEmpty()) 0.0
    else {
        val aChars = a.toCharArray()
        val bChars = b.toCharArray()
        val m = aChars.size
        val n = bChars.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (aChars[i - 1] == bChars[j - 1]) dp[i - 1][j - 1] + 1
                else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
        (2.0 * dp[m][n]) / (m + n)
    }
}

fun _unicodeNormalize(text: String): String {
    var result = text
    for ((char, repl) in UNICODE_MAP) {
        result = result.replace(char, repl)
    }
    return result
}

/**
 * Find and replace text using a chain of increasingly fuzzy matching strategies.
 *
 * Returns a 4-element map: content, match_count, strategy, error.
 * - If successful: (modified_content, N, strategy_used, null)
 * - If failed: (original_content, 0, null, error_description)
 */
fun fuzzyFindAndReplace(
    content: String,
    oldString: String,
    newString: String,
    replaceAll: Boolean = false): Map<String, Any?> {
    if (oldString.isEmpty()) return mapOf(
        "content" to content, "match_count" to 0, "strategy" to null,
        "error" to "old_string cannot be empty")
    if (oldString == newString) return mapOf(
        "content" to content, "match_count" to 0, "strategy" to null,
        "error" to "old_string and new_string are identical")

    val strategies = listOf(
        "exact" to ::_strategyExact,
        "line_trimmed" to ::_strategyLineTrimmed,
        "whitespace_normalized" to ::_strategyWhitespaceNormalized,
        "indentation_flexible" to ::_strategyIndentationFlexible,
        "escape_normalized" to ::_strategyEscapeNormalized,
        "trimmed_boundary" to ::_strategyTrimmedBoundary,
        "unicode_normalized" to ::_strategyUnicodeNormalized,
        "block_anchor" to ::_strategyBlockAnchor,
        "context_aware" to ::_strategyContextAware)

    for ((strategyName, strategyFn) in strategies) {
        val matches = strategyFn(content, oldString)
        if (matches.isNotEmpty()) {
            if (matches.size > 1 && !replaceAll) {
                return mapOf(
                    "content" to content, "match_count" to 0, "strategy" to null,
                    "error" to "Found ${matches.size} matches for old_string. Provide more context to make it unique, or use replace_all=True.")
            }
            if (strategyName != "exact") {
                val driftErr = _detectEscapeDrift(content, matches, oldString, newString)
                if (driftErr != null) {
                    return mapOf(
                        "content" to content, "match_count" to 0, "strategy" to null,
                        "error" to driftErr)
                }
            }
            val newContent = _applyReplacements(content, matches, newString)
            return mapOf(
                "content" to newContent, "match_count" to matches.size,
                "strategy" to strategyName, "error" to null)
        }
    }

    return mapOf(
        "content" to content, "match_count" to 0, "strategy" to null,
        "error" to "Could not find a match for old_string in the file")
}

/**
 * Detect tool-call escape-drift artifacts in new_string.
 *
 * Looks for ``\'`` or ``\"`` sequences present in both old_string and new_string
 * but not in the matched region. Writing new_string verbatim would corrupt
 * source by inserting literal backslash-escape sequences.
 */
fun _detectEscapeDrift(
    content: String,
    matches: List<Pair<Int, Int>>,
    oldString: String,
    newString: String): String? {
    if ("\\'" !in newString && "\\\"" !in newString) return null

    val matchedRegions = matches.joinToString("") { (s, e) -> content.substring(s, e) }

    for (suspect in listOf("\\'", "\\\"")) {
        if (suspect in newString && suspect in oldString && suspect !in matchedRegions) {
            val plain = suspect[1]
            return "Escape-drift detected: old_string and new_string contain the literal sequence " +
                "'$suspect'" +
                " but the matched region of the file does not. This is almost always a tool-call serialization artifact where an apostrophe or quote got prefixed with a spurious backslash. Re-read the file with read_file and pass old_string/new_string without backslash-escaping " +
                "'$plain' characters."
        }
    }
    return null
}

/**
 * Apply replacements at the given positions, from end to start.
 */
fun _applyReplacements(content: String, matches: List<Pair<Int, Int>>, newString: String): String {
    val sorted = matches.sortedByDescending { it.first }
    var result = content
    for ((start, end) in sorted) {
        result = result.substring(0, start) + newString + result.substring(end)
    }
    return result
}

// =============================================================================
// Matching Strategies
// =============================================================================

/** Strategy 1: Exact string match. */
fun _strategyExact(content: String, pattern: String): List<Pair<Int, Int>> {
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

/** Strategy 2: Match with line-by-line whitespace trimming. */
fun _strategyLineTrimmed(content: String, pattern: String): List<Pair<Int, Int>> {
    val patternLines = pattern.split('\n').map { it.trim() }
    val patternNormalized = patternLines.joinToString("\n")
    val contentLines = content.split('\n')
    val contentNormalizedLines = contentLines.map { it.trim() }
    return _findNormalizedMatches(content, contentLines, contentNormalizedLines, pattern, patternNormalized)
}

/** Strategy 3: Collapse multiple whitespace to single space. */
fun _strategyWhitespaceNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
    val wsRegex = Regex("[ \t]+")
    val patternNormalized = wsRegex.replace(pattern, " ")
    val contentNormalized = wsRegex.replace(content, " ")
    val matchesInNormalized = _strategyExact(contentNormalized, patternNormalized)
    if (matchesInNormalized.isEmpty()) return emptyList()
    return _mapNormalizedPositions(content, contentNormalized, matchesInNormalized)
}

/** Strategy 4: Ignore indentation differences entirely. */
fun _strategyIndentationFlexible(content: String, pattern: String): List<Pair<Int, Int>> {
    val contentLines = content.split('\n')
    val contentStrippedLines = contentLines.map { it.trimStart() }
    val patternLines = pattern.split('\n').map { it.trimStart() }
    return _findNormalizedMatches(content, contentLines, contentStrippedLines, pattern, patternLines.joinToString("\n"))
}

/** Strategy 5: Convert escape sequences to actual characters. */
fun _strategyEscapeNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
    val unescaped = pattern.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
    if (unescaped == pattern) return emptyList()
    return _strategyExact(content, unescaped)
}

/** Strategy 6: Trim whitespace from first and last lines only. */
fun _strategyTrimmedBoundary(content: String, pattern: String): List<Pair<Int, Int>> {
    val patternLines = pattern.split('\n').toMutableList()
    if (patternLines.isEmpty()) return emptyList()
    patternLines[0] = patternLines[0].trim()
    if (patternLines.size > 1) patternLines[patternLines.lastIndex] = patternLines.last().trim()
    val modifiedPattern = patternLines.joinToString("\n")

    val contentLines = content.split('\n')
    val matches = mutableListOf<Pair<Int, Int>>()
    val patternLineCount = patternLines.size

    for (i in 0..contentLines.size - patternLineCount) {
        val blockLines = contentLines.subList(i, i + patternLineCount).toMutableList()
        blockLines[0] = blockLines[0].trim()
        if (blockLines.size > 1) blockLines[blockLines.lastIndex] = blockLines.last().trim()
        if (blockLines.joinToString("\n") == modifiedPattern) {
            val (startPos, endPos) = _calculateLinePositions(contentLines, i, i + patternLineCount, content.length)
            matches.add(startPos to endPos)
        }
    }
    return matches
}

/**
 * Build a list mapping each original character index to its normalized index.
 * Because UNICODE_MAP replacements may expand characters, the normalized
 * string can be longer than the original.
 */
fun _buildOrigToNormMap(original: String): List<Int> {
    val result = mutableListOf<Int>()
    var normPos = 0
    for (char in original) {
        result.add(normPos)
        val repl = UNICODE_MAP[char.toString()]
        normPos += repl?.length ?: 1
    }
    result.add(normPos)
    return result
}

/** Convert (start, end) positions in the normalized string to original positions. */
fun _mapPositionsNormToOrig(
    origToNorm: List<Int>,
    normMatches: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    val normToOrigStart = mutableMapOf<Int, Int>()
    for (origPos in 0 until origToNorm.size - 1) {
        val normPos = origToNorm[origPos]
        if (normPos !in normToOrigStart) normToOrigStart[normPos] = origPos
    }

    val results = mutableListOf<Pair<Int, Int>>()
    val origLen = origToNorm.size - 1

    for ((normStart, normEnd) in normMatches) {
        if (normStart !in normToOrigStart) continue
        val origStart = normToOrigStart[normStart]!!
        var origEnd = origStart
        while (origEnd < origLen && origToNorm[origEnd] < normEnd) origEnd++
        results.add(origStart to origEnd)
    }
    return results
}

/** Strategy 7: Unicode normalisation. */
fun _strategyUnicodeNormalized(content: String, pattern: String): List<Pair<Int, Int>> {
    val normPattern = _unicodeNormalize(pattern)
    val normContent = _unicodeNormalize(content)
    if (normContent == content && normPattern == pattern) return emptyList()

    var normMatches = _strategyExact(normContent, normPattern)
    if (normMatches.isEmpty()) normMatches = _strategyLineTrimmed(normContent, normPattern)
    if (normMatches.isEmpty()) return emptyList()

    val origToNorm = _buildOrigToNormMap(content)
    return _mapPositionsNormToOrig(origToNorm, normMatches)
}

/** Strategy 8: Match by anchoring on first and last lines. */
fun _strategyBlockAnchor(content: String, pattern: String): List<Pair<Int, Int>> {
    val normPattern = _unicodeNormalize(pattern)
    val normContent = _unicodeNormalize(content)

    val patternLines = normPattern.split('\n')
    if (patternLines.size < 2) return emptyList()

    val firstLine = patternLines.first().trim()
    val lastLine = patternLines.last().trim()

    val normContentLines = normContent.split('\n')
    val origContentLines = content.split('\n')
    val patternLineCount = patternLines.size

    val potentialMatches = mutableListOf<Int>()
    for (i in 0..normContentLines.size - patternLineCount) {
        if (normContentLines[i].trim() == firstLine &&
            normContentLines[i + patternLineCount - 1].trim() == lastLine) {
            potentialMatches.add(i)
        }
    }

    val matches = mutableListOf<Pair<Int, Int>>()
    val candidateCount = potentialMatches.size
    val threshold = if (candidateCount == 1) 0.50 else 0.70

    for (i in potentialMatches) {
        val similarity = if (patternLineCount <= 2) 1.0
        else {
            val contentMiddle = normContentLines.subList(i + 1, i + patternLineCount - 1).joinToString("\n")
            val patternMiddle = patternLines.subList(1, patternLines.size - 1).joinToString("\n")
            _sequenceRatio(contentMiddle, patternMiddle)
        }
        if (similarity >= threshold) {
            val (startPos, endPos) = _calculateLinePositions(origContentLines, i, i + patternLineCount, content.length)
            matches.add(startPos to endPos)
        }
    }
    return matches
}

/** Strategy 9: Line-by-line similarity with 50% threshold. */
fun _strategyContextAware(content: String, pattern: String): List<Pair<Int, Int>> {
    val patternLines = pattern.split('\n')
    val contentLines = content.split('\n')
    if (patternLines.isEmpty()) return emptyList()

    val matches = mutableListOf<Pair<Int, Int>>()
    val patternLineCount = patternLines.size

    for (i in 0..contentLines.size - patternLineCount) {
        val blockLines = contentLines.subList(i, i + patternLineCount)
        var highSimilarityCount = 0
        for ((pLine, cLine) in patternLines.zip(blockLines)) {
            val sim = _sequenceRatio(pLine.trim(), cLine.trim())
            if (sim >= 0.80) highSimilarityCount++
        }
        if (highSimilarityCount >= patternLines.size * 0.5) {
            val (startPos, endPos) = _calculateLinePositions(contentLines, i, i + patternLineCount, content.length)
            matches.add(startPos to endPos)
        }
    }
    return matches
}

// =============================================================================
// Helper Functions
// =============================================================================

/** Calculate start and end character positions from line indices. */
fun _calculateLinePositions(
    contentLines: List<String>,
    startLine: Int,
    endLine: Int,
    contentLength: Int): Pair<Int, Int> {
    val startPos = contentLines.take(startLine).sumOf { it.length + 1 }
    var endPos = contentLines.take(endLine).sumOf { it.length + 1 } - 1
    if (endPos >= contentLength) endPos = contentLength
    return startPos to endPos
}

/** Find matches in normalized content and map back to original positions. */
fun _findNormalizedMatches(
    content: String,
    contentLines: List<String>,
    contentNormalizedLines: List<String>,
    pattern: String,
    patternNormalized: String): List<Pair<Int, Int>> {
    val patternNormLines = patternNormalized.split('\n')
    val numPatternLines = patternNormLines.size
    val matches = mutableListOf<Pair<Int, Int>>()

    for (i in 0..contentNormalizedLines.size - numPatternLines) {
        val block = contentNormalizedLines.subList(i, i + numPatternLines).joinToString("\n")
        if (block == patternNormalized) {
            val (startPos, endPos) = _calculateLinePositions(contentLines, i, i + numPatternLines, content.length)
            matches.add(startPos to endPos)
        }
    }
    return matches
}

/** Map positions from normalized string back to original. Best-effort for whitespace normalization. */
fun _mapNormalizedPositions(
    original: String,
    normalized: String,
    normalizedMatches: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    if (normalizedMatches.isEmpty()) return emptyList()

    val origToNorm = mutableListOf<Int>()
    var origIdx = 0
    var normIdx = 0

    while (origIdx < original.length && normIdx < normalized.length) {
        if (original[origIdx] == normalized[normIdx]) {
            origToNorm.add(normIdx)
            origIdx++
            normIdx++
        } else if (original[origIdx] in " \t" && normalized[normIdx] == ' ') {
            origToNorm.add(normIdx)
            origIdx++
            if (origIdx < original.length && original[origIdx] !in " \t") normIdx++
        } else if (original[origIdx] in " \t") {
            origToNorm.add(normIdx)
            origIdx++
        } else {
            origToNorm.add(normIdx)
            origIdx++
        }
    }

    while (origIdx < original.length) {
        origToNorm.add(normalized.length)
        origIdx++
    }

    val normToOrigStart = mutableMapOf<Int, Int>()
    val normToOrigEnd = mutableMapOf<Int, Int>()
    for ((origPos, normPos) in origToNorm.withIndex()) {
        if (normPos !in normToOrigStart) normToOrigStart[normPos] = origPos
        normToOrigEnd[normPos] = origPos
    }

    val originalMatches = mutableListOf<Pair<Int, Int>>()
    for ((normStart, normEnd) in normalizedMatches) {
        val origStart = normToOrigStart[normStart]
            ?: origToNorm.withIndex().firstOrNull { (_, n) -> n >= normStart }?.index
            ?: continue
        var origEnd = if ((normEnd - 1) in normToOrigEnd) normToOrigEnd[normEnd - 1]!! + 1
        else origStart + (normEnd - normStart)
        while (origEnd < original.length && original[origEnd] in " \t") origEnd++
        originalMatches.add(origStart to minOf(origEnd, original.length))
    }
    return originalMatches
}

/**
 * Find lines in content most similar to old_string for "did you mean?" feedback.
 */
fun findClosestLines(
    oldString: String,
    content: String,
    contextLines: Int = 2,
    maxResults: Int = 3): String {
    if (oldString.isEmpty() || content.isEmpty()) return ""

    val oldLines = oldString.split('\n')
    val contentLines = content.split('\n')

    if (oldLines.isEmpty() || contentLines.isEmpty()) return ""

    var anchor = oldLines[0].trim()
    if (anchor.isEmpty()) {
        val candidates = oldLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return ""
        anchor = candidates[0]
    }

    val scored = mutableListOf<Pair<Double, Int>>()
    for ((i, line) in contentLines.withIndex()) {
        val stripped = line.trim()
        if (stripped.isEmpty()) continue
        val ratio = _sequenceRatio(anchor, stripped)
        if (ratio > 0.3) scored.add(ratio to i)
    }

    if (scored.isEmpty()) return ""

    scored.sortByDescending { it.first }
    val top = scored.take(maxResults)

    val parts = mutableListOf<String>()
    val seenRanges = mutableSetOf<Pair<Int, Int>>()
    for ((_, lineIdx) in top) {
        val start = maxOf(0, lineIdx - contextLines)
        val end = minOf(contentLines.size, lineIdx + oldLines.size + contextLines)
        val key = start to end
        if (key in seenRanges) continue
        seenRanges.add(key)
        val snippet = (0 until end - start).joinToString("\n") { j ->
            "%4d| %s".format(start + j + 1, contentLines[start + j])
        }
        parts.add(snippet)
    }

    if (parts.isEmpty()) return ""
    return parts.joinToString("\n---\n")
}

/**
 * Return a "\n\nDid you mean..." snippet for plain no-match errors.
 */
fun formatNoMatchHint(
    error: String?,
    matchCount: Int,
    oldString: String,
    content: String): String {
    if (matchCount != 0) return ""
    if (error == null || !error.startsWith("Could not find")) return ""
    val hint = findClosestLines(oldString, content)
    if (hint.isEmpty()) return ""
    return "\n\nDid you mean one of these sections?\n" + hint
}

@Suppress("unused")
private val _NO_MATCH_HINT_HEADER: String = """

Did you mean one of these sections?
"""
