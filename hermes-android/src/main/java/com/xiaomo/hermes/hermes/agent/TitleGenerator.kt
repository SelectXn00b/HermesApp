package com.xiaomo.hermes.hermes.agent

/**
 * Title Generator - 会话标题生成
 * 1:1 对齐 hermes/agent/title_generator.py
 *
 * 从用户第一条消息自动生成简短会话标题。
 */

class TitleGenerator {

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "can", "shall",
            "i", "you", "he", "she", "it", "we", "they",
            "my", "your", "his", "her", "its", "our", "their",
            "me", "him", "us", "them",
            "this", "that", "these", "those",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "and", "or", "but", "not", "no", "so", "if", "then",
            "请", "帮我", "怎么", "什么", "为什么", "如何",
            "的", "了", "在", "是", "我", "你", "他", "她", "它",
            "和", "与", "或", "但", "如果", "那么"
        )

        private const val MAX_TITLE_LENGTH = 60
    }

    /**
     * 从用户消息生成会话标题
     *
     * @param userMessage 用户消息
     * @return 生成的标题
     */
    fun generate(userMessage: String): String {
        val cleaned = userMessage
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) return "New Chat"
        if (cleaned.length <= MAX_TITLE_LENGTH) return cleaned

        // 提取关键词
        val words = cleaned.split(Regex("[\\s,.;:!?，。；：！？]+"))
            .filter { it.isNotEmpty() && it.lowercase() !in STOP_WORDS }

        // 取前几个关键词组成标题
        val title = StringBuilder()
        for (word in words) {
            if (title.length + word.length + 1 > MAX_TITLE_LENGTH) break
            if (title.isNotEmpty()) title.append(" ")
            title.append(word)
        }

        return if (title.isNotEmpty()) title.toString() else cleaned.take(MAX_TITLE_LENGTH)
    }

    /**
     * 从消息列表生成标题（取第一条用户消息）
     *
     * @param messages 消息列表
     * @return 生成的标题
     */
    fun generateFromMessages(messages: List<Map<String, Any>>): String {
        for (msg in messages) {
            if (msg["role"] == "user") {
                val content = msg["content"] as? String ?: continue
                return generate(content)
            }
        }
        return "New Chat"
    }


}

// ── Module-level aligned with agent/title_generator.py ───────────────────

/** Prompt used to instruct the model to write a session title. */
const val _TITLE_PROMPT: String = "Generate a short, descriptive title (3-7 words) for a conversation that starts with the following exchange. The title should capture the main topic or intent. Return ONLY the title text, nothing else. No quotes, no punctuation at the end, no prefixes."

/** Generate a title for the given conversation (stub). */
@Suppress("UNUSED_PARAMETER")
suspend fun generateTitle(messages: List<Map<String, Any?>>, provider: Any? = null): String = ""

/** Auto-title a session if it has enough content and no title yet. */
@Suppress("UNUSED_PARAMETER")
suspend fun autoTitleSession(sessionKey: String, messages: List<Map<String, Any?>>): String? = null

/** Optionally run auto_title_session based on current session state. */
@Suppress("UNUSED_PARAMETER")
suspend fun maybeAutoTitle(sessionKey: String, messages: List<Map<String, Any?>>): String? = null
