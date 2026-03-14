/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

/**
 * 推理标签过滤器 - 对齐 OpenClaw
 *
 * 从 AI 回复中移除内部推理标签，如 <think>, <thinking>, <final> 等
 *
 * 参考: OpenClaw src/shared/text/reasoning-tags.ts
 */
object ReasoningTagFilter {

    /**
     * 从文本中移除推理标签
     *
     * - 移除 <final></final> 标签但保留内容
     * - 移除 <think>, <thinking>, <thought> 标签及其内容
     * - 保护代码块内的标签不被移除
     *
     * @param text 原始文本
     * @return 过滤后的文本
     */
    fun stripReasoningTags(text: String): String {
        if (text.isEmpty()) return text

        // Quick check: 如果没有推理标签，直接返回
        val quickCheckPattern = """<\s*/?\s*(?:think(?:ing)?|thought|antthinking|final)\b""".toRegex(RegexOption.IGNORE_CASE)
        if (!quickCheckPattern.containsMatchIn(text)) {
            return text
        }

        // 1. 找出所有代码区域（需要保护）
        val codeRegions = findCodeRegions(text)

        // 2. 移除 <final> 标签（保留内容）
        var cleaned = text
        val finalTagPattern = """<\s*/?\s*final\b[^<>]*>""".toRegex(RegexOption.IGNORE_CASE)
        val finalMatches = finalTagPattern.findAll(cleaned).toList().reversed()
        for (match in finalMatches) {
            val start = match.range.first
            if (!isInsideCodeRegion(start, codeRegions)) {
                cleaned = cleaned.removeRange(match.range)
            }
        }

        // 3. 移除推理标签及其内容
        val thinkingTagPattern = """<\s*(/?)\s*(?:think(?:ing)?|thought|antthinking)\b[^<>]*>""".toRegex(RegexOption.IGNORE_CASE)
        val updatedCodeRegions = findCodeRegions(cleaned)

        val result = StringBuilder()
        var lastIndex = 0
        var inThinking = false
        var thinkingStart = 0

        thinkingTagPattern.findAll(cleaned).forEach { match ->
            val start = match.range.first
            if (isInsideCodeRegion(start, updatedCodeRegions)) {
                return@forEach
            }

            val isClosing = match.groupValues[1] == "/"

            if (!inThinking && !isClosing) {
                // 开始推理块
                result.append(cleaned.substring(lastIndex, start))
                inThinking = true
                thinkingStart = match.range.last + 1
            } else if (inThinking && isClosing) {
                // 结束推理块
                inThinking = false
                lastIndex = match.range.last + 1
            }
        }

        if (!inThinking) {
            result.append(cleaned.substring(lastIndex))
        }

        return result.toString().trim()
    }

    /**
     * 查找代码区域（fenced code blocks 和 inline code）
     */
    private fun findCodeRegions(text: String): List<IntRange> {
        val regions = mutableListOf<IntRange>()

        // Fenced code blocks (``` 或 ~~~)
        val fencedPattern = """(```|~~~)[^\n]*\n[\s\S]*?\1""".toRegex()
        fencedPattern.findAll(text).forEach {
            regions.add(it.range)
        }

        // Inline code (backticks)
        val inlinePattern = """`[^`\n]+`""".toRegex()
        inlinePattern.findAll(text).forEach {
            // 只添加不在 fenced block 内的 inline code
            if (!regions.any { range -> it.range.first in range }) {
                regions.add(it.range)
            }
        }

        return regions
    }

    /**
     * 检查位置是否在代码区域内
     */
    private fun isInsideCodeRegion(position: Int, codeRegions: List<IntRange>): Boolean {
        return codeRegions.any { position in it }
    }
}
