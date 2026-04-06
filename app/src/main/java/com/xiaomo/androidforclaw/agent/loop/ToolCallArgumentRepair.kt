package com.xiaomo.androidforclaw.agent.loop

/**
 * Tool Call Argument Repair — 对齐 OpenClaw attempt.tool-call-argument-repair.ts
 *
 * 修复 LLM 返回的畸形工具调用参数：
 * - 截断 JSON 修复
 * - 前缀/后缀清理
 * - HTML 实体解码
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/attempt.tool-call-argument-repair.ts
 */

private const val MAX_TOOLCALL_REPAIR_BUFFER_CHARS = 64_000
private const val MAX_TOOLCALL_REPAIR_LEADING_CHARS = 96
private const val MAX_TOOLCALL_REPAIR_TRAILING_CHARS = 3
private val TOOLCALL_REPAIR_ALLOWED_LEADING_RE = Regex("""^[a-z0-9\s"'`.:/_\\-]+$""", RegexOption.IGNORE_CASE)
private val TOOLCALL_REPAIR_ALLOWED_TRAILING_RE = Regex("""^[^\s{}\[\]",:\\]{1,3}$""")
private val HTML_ENTITY_RE = Regex("""&(?:amp|lt|gt|quot|apos|#39|#x[0-9a-f]+|#\d+);""", RegexOption.IGNORE_CASE)

/** OpenClaw: normalizeProviderId(provider) === "kimi" */
fun shouldRepairMalformedAnthropicToolCallArguments(provider: String?): Boolean {
    val normalized = provider?.lowercase()?.trim() ?: return false
    return normalized == "kimi"
}

/**
 * 提取平衡的 JSON 前缀。
 * OpenClaw: extractBalancedJsonPrefix
 */
data class BalancedJsonPrefix(
    val json: String,
    val startIndex: Int
)

fun extractBalancedJsonPrefix(raw: String): BalancedJsonPrefix? {
    var start = 0
    while (start < raw.length) {
        val char = raw[start]
        if (char == '{' || char == '[') break
        start++
    }
    if (start >= raw.length) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until raw.length) {
        val char = raw[i]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }
        if (char == '"') {
            inString = true
            continue
        }
        if (char == '{' || char == '[') {
            depth++
            continue
        }
        if (char == '}' || char == ']') {
            depth--
            if (depth == 0) {
                return BalancedJsonPrefix(json = raw.substring(start, i + 1), startIndex = start)
            }
        }
    }
    return null
}

/**
 * 判断是否应该尝试修复畸形工具调用参数。
 * OpenClaw: shouldAttemptMalformedToolCallRepair
 */
fun shouldAttemptMalformedToolCallRepair(partialJson: String, delta: String): Boolean {
    if (delta.contains('}') || delta.contains(']')) return true
    val trimmedDelta = delta.trim()
    return (
        trimmedDelta.isNotEmpty() &&
        trimmedDelta.length <= MAX_TOOLCALL_REPAIR_TRAILING_CHARS &&
        (partialJson.contains('}') || partialJson.contains(']'))
    )
}

/**
 * 判断前缀是否允许用于工具调用修复。
 * OpenClaw: isAllowedToolCallRepairLeadingPrefix
 */
private fun isAllowedToolCallRepairLeadingPrefix(prefix: String): Boolean {
    if (prefix.isEmpty()) return true
    if (prefix.length > MAX_TOOLCALL_REPAIR_LEADING_CHARS) return false
    if (!TOOLCALL_REPAIR_ALLOWED_LEADING_RE.matches(prefix)) return false
    return prefix.first().let { it == '.' || it == ':' || it == '\'' || it == '"' || it == '`' || it == '-' } ||
        Regex("""^(?:functions?|tools?)[._:/-]?""", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
}

/**
 * 工具调用参数修复结果。
 * OpenClaw: ToolCallArgumentRepair
 */
data class ToolCallArgumentRepairResult(
    val args: Map<String, Any?>,
    val kind: String,  // "preserved" or "repaired"
    val leadingPrefix: String,
    val trailingSuffix: String
)

/**
 * 尝试从原始字符串中提取可用的工具调用参数。
 * OpenClaw: tryExtractUsableToolCallArguments
 */
fun tryExtractUsableToolCallArguments(raw: String): ToolCallArgumentRepairResult? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    // 尝试直接解析
    try {
        val parsed = com.google.gson.JsonParser.parseString(trimmed)
        if (parsed.isJsonObject) {
            val map = com.google.gson.Gson().fromJson(parsed, Map::class.java) as Map<String, Any?>
            return ToolCallArgumentRepairResult(args = map, kind = "preserved", leadingPrefix = "", trailingSuffix = "")
        }
        return null
    } catch (_: Exception) { /* continue to repair */ }

    // 尝试提取平衡 JSON
    val extracted = extractBalancedJsonPrefix(trimmed) ?: return null
    val leadingPrefix = trimmed.substring(0, extracted.startIndex).trim()
    if (!isAllowedToolCallRepairLeadingPrefix(leadingPrefix)) return null

    val suffix = trimmed.substring(extracted.startIndex + extracted.json.length).trim()
    if (leadingPrefix.isEmpty() && suffix.isEmpty()) return null
    if (suffix.length > MAX_TOOLCALL_REPAIR_TRAILING_CHARS ||
        (suffix.isNotEmpty() && !TOOLCALL_REPAIR_ALLOWED_TRAILING_RE.matches(suffix))) {
        return null
    }

    try {
        val parsed = com.google.gson.JsonParser.parseString(extracted.json)
        if (parsed.isJsonObject) {
            val map = com.google.gson.Gson().fromJson(parsed, Map::class.java) as Map<String, Any?>
            return ToolCallArgumentRepairResult(args = map, kind = "repaired", leadingPrefix = leadingPrefix, trailingSuffix = suffix)
        }
    } catch (_: Exception) { /* give up */ }
    return null
}

/**
 * HTML 实体解码。
 * OpenClaw: decodeHtmlEntities — 支持 &amp; &lt; &gt; &quot; &apos; &#39; &#xHH; &#DDD;
 */
fun decodeHtmlEntities(value: String): String {
    if (!HTML_ENTITY_RE.containsMatchIn(value)) return value
    return value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""&#x([0-9a-f]+);""", RegexOption.IGNORE_CASE)) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value
        }
        .replace(Regex("""&#(\d+);""")) { match ->
            match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
        }
}

/**
 * 递归解码对象中的 HTML 实体。
 * OpenClaw: decodeHtmlEntitiesInObject
 */
fun decodeHtmlEntitiesInObject(obj: Any?): Any? {
    return when (obj) {
        is String -> if (HTML_ENTITY_RE.containsMatchIn(obj)) decodeHtmlEntities(obj) else obj
        is List<*> -> obj.map { decodeHtmlEntitiesInObject(it) }
        is Map<*, *> -> obj.mapValues { (_, v) -> decodeHtmlEntitiesInObject(v) }
        else -> obj
    }
}
