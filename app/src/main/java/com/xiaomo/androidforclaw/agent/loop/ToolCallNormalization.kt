package com.xiaomo.androidforclaw.agent.loop

/**
 * Tool Call Normalization — 对齐 OpenClaw attempt.tool-call-normalization.ts
 *
 * 规范化 LLM 返回的工具调用名称：
 * - trim 空格
 * - `/` → `.` 转换
 * - 大小写不敏感匹配
 * - 从 toolCallId 推断工具名
 * - 结构化候选名构建（后缀匹配）
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/attempt.tool-call-normalization.ts
 */

private const val REPLAY_TOOL_CALL_NAME_MAX_CHARS = 64

/**
 * 规范化工具名（移除常见前缀，kebab-case → camelCase 等）。
 * OpenClaw: normalizeToolName (from tool-policy.ts)
 */
private fun normalizeToolName(name: String): String {
    var result = name.trim()
    // Remove common prefixes
    result = result.replace(Regex("""^(?:functions?|tools?)[._:/-]?""", RegexOption.IGNORE_CASE), "")
    // kebab-case → camelCase
    result = result.replace(Regex("""-([a-z])""")) { match ->
        match.groupValues[1].uppercase()
    }
    // snake_case → camelCase
    result = result.replace(Regex("""_([a-z])""")) { match ->
        match.groupValues[1].uppercase()
    }
    return result
}

/**
 * 大小写不敏感匹配允许的工具名。
 * OpenClaw: resolveCaseInsensitiveAllowedToolName
 */
private fun resolveCaseInsensitiveAllowedToolName(
    rawName: String,
    allowedToolNames: Set<String>?
): String? {
    if (allowedToolNames.isNullOrEmpty()) return null
    val folded = rawName.lowercase()
    var caseInsensitiveMatch: String? = null
    for (name in allowedToolNames) {
        if (name.lowercase() != folded) continue
        if (caseInsensitiveMatch != null && caseInsensitiveMatch != name) return null  // Ambiguous
        caseInsensitiveMatch = name
    }
    return caseInsensitiveMatch
}

/**
 * 精确匹配允许的工具名。
 * OpenClaw: resolveExactAllowedToolName
 */
private fun resolveExactAllowedToolName(
    rawName: String,
    allowedToolNames: Set<String>?
): String? {
    if (allowedToolNames.isNullOrEmpty()) return null
    if (allowedToolNames.contains(rawName)) return rawName
    val normalized = normalizeToolName(rawName)
    if (allowedToolNames.contains(normalized)) return normalized
    return resolveCaseInsensitiveAllowedToolName(rawName, allowedToolNames)
        ?: resolveCaseInsensitiveAllowedToolName(normalized, allowedToolNames)
}

/**
 * 构建结构化候选名。
 * OpenClaw: buildStructuredToolNameCandidates
 *
 * 处理：
 * - 原始名 + trim
 * - normalizeToolName 结果
 * - `/` → `.` 转换
 * - 后缀匹配 (a.b.c → b.c, c)
 */
private fun buildStructuredToolNameCandidates(rawName: String): List<String> {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) return emptyList()

    val candidates = mutableListOf<String>()
    val seen = mutableSetOf<String>()

    fun addCandidate(value: String) {
        val candidate = value.trim()
        if (candidate.isEmpty() || !seen.add(candidate)) return
        candidates.add(candidate)
    }

    addCandidate(trimmed)
    addCandidate(normalizeToolName(trimmed))

    val normalizedDelimiter = trimmed.replace('/', '.')
    addCandidate(normalizedDelimiter)
    addCandidate(normalizeToolName(normalizedDelimiter))

    val segments = normalizedDelimiter.split('.').map { it.trim() }.filter { it.isNotEmpty() }
    if (segments.size > 1) {
        for (index in 1 until segments.size) {
            val suffix = segments.subList(index, segments.size).joinToString(".")
            addCandidate(suffix)
            addCandidate(normalizeToolName(suffix))
        }
    }

    return candidates
}

/**
 * 结构化匹配允许的工具名。
 * OpenClaw: resolveStructuredAllowedToolName
 */
private fun resolveStructuredAllowedToolName(
    rawName: String,
    allowedToolNames: Set<String>?
): String? {
    if (allowedToolNames.isNullOrEmpty()) return null

    val candidates = buildStructuredToolNameCandidates(rawName)
    for (candidate in candidates) {
        if (allowedToolNames.contains(candidate)) return candidate
    }
    for (candidate in candidates) {
        val match = resolveCaseInsensitiveAllowedToolName(candidate, allowedToolNames)
        if (match != null) return match
    }
    return null
}

/**
 * 从 toolCallId 推断工具名。
 * OpenClaw: inferToolNameFromToolCallId
 */
private fun inferToolNameFromToolCallId(
    rawId: String?,
    allowedToolNames: Set<String>?
): String? {
    if (rawId.isNullOrEmpty() || allowedToolNames.isNullOrEmpty()) return null
    val id = rawId.trim()
    if (id.isEmpty()) return null

    val candidateTokens = mutableSetOf<String>()

    fun addToken(value: String) {
        val t = value.trim()
        if (t.isEmpty()) return
        candidateTokens.add(t)
        candidateTokens.add(t.replace(Regex("""[:._/-]\d+$"""), ""))
        candidateTokens.add(t.replace(Regex("""\d+$"""), ""))

        val normalizedDelimiter = t.replace('/', '.')
        candidateTokens.add(normalizedDelimiter)
        candidateTokens.add(normalizedDelimiter.replace(Regex("""[:._-]\d+$"""), ""))
        candidateTokens.add(normalizedDelimiter.replace(Regex("""\d+$"""), ""))

        for (prefixPattern in listOf(Regex("""^functions?[._-]?""", RegexOption.IGNORE_CASE), Regex("""^tools?[._-]?""", RegexOption.IGNORE_CASE))) {
            val stripped = normalizedDelimiter.replace(prefixPattern, "")
            if (stripped != normalizedDelimiter) {
                candidateTokens.add(stripped)
                candidateTokens.add(stripped.replace(Regex("""[:._-]\d+$"""), ""))
                candidateTokens.add(stripped.replace(Regex("""\d+$"""), ""))
            }
        }
    }

    val preColon = id.split(':').firstOrNull() ?: id
    for (seed in listOf(id, preColon)) {
        addToken(seed)
    }

    var singleMatch: String? = null
    for (candidate in candidateTokens) {
        val matched = resolveStructuredAllowedToolName(candidate, allowedToolNames) ?: continue
        if (singleMatch != null && singleMatch != matched) return null  // Ambiguous
        singleMatch = matched
    }
    return singleMatch
}

/**
 * 检测畸形工具名计数器（如 "functions_read_1"）。
 * OpenClaw: looksLikeMalformedToolNameCounter
 */
private fun looksLikeMalformedToolNameCounter(rawName: String): Boolean {
    val normalizedDelimiter = rawName.trim().replace('/', '.')
    return Regex("""^(?:functions?|tools?)[._-]?""", RegexOption.IGNORE_CASE).containsMatchIn(normalizedDelimiter) &&
        Regex("""(?:[:._-]\d+|\d+)$""").containsMatchIn(normalizedDelimiter)
}

/**
 * 规范化工具调用名称以用于分发。
 * OpenClaw: normalizeToolCallNameForDispatch
 *
 * @param rawName 原始工具名
 * @param allowedToolNames 允许的工具名集合（可选，用于精确匹配）
 * @param rawToolCallId 工具调用 ID（可选，用于推断工具名）
 * @return 规范化后的工具名
 */
fun normalizeToolCallNameForDispatch(
    rawName: String,
    allowedToolNames: Set<String>? = null,
    rawToolCallId: String? = null
): String {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) {
        return inferToolNameFromToolCallId(rawToolCallId, allowedToolNames) ?: rawName
    }
    if (allowedToolNames.isNullOrEmpty()) return trimmed

    val exact = resolveExactAllowedToolName(trimmed, allowedToolNames)
    if (exact != null) return exact

    val inferredFromName = inferToolNameFromToolCallId(trimmed, allowedToolNames)
    if (inferredFromName != null) return inferredFromName

    if (looksLikeMalformedToolNameCounter(trimmed)) return trimmed

    return resolveStructuredAllowedToolName(trimmed, allowedToolNames) ?: trimmed
}
