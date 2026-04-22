package com.xiaomo.hermes.hermes.agent

/**
 * Redact - 敏感信息脱敏
 * 1:1 对齐 hermes/agent/redact.py
 *
 * 脱敏 API key、token、密码等敏感信息，防止泄露到日志。
 */

class Redact {

    companion object {
        // API key 模式
        private val API_KEY_PATTERNS = listOf(
            Regex("(sk-[a-zA-Z0-9]{20,})"),                           // OpenAI
            Regex("(sk-ant-[a-zA-Z0-9]{20,})"),                       // Anthropic
            Regex("(sk-or-[a-zA-Z0-9]{20,})"),                        // OpenRouter
            Regex("(AIza[a-zA-Z0-9_-]{35})"),                         // Google
            Regex("(xai-[a-zA-Z0-9]{20,})"),                          // xAI
            Regex("(gsk_[a-zA-Z0-9]{20,})"),                          // Groq
            Regex("(Bearer\\s+[a-zA-Z0-9._-]{20,})"),                 // Bearer token
            Regex("(api[_-]?key[\"':=]\\s*[\"']?[a-zA-Z0-9._-]{20,})"), // api_key=xxx
            Regex("(token[\"':=]\\s*[\"']?[a-zA-Z0-9._-]{20,})"),    // token=xxx
            Regex("(password[\"':=]\\s*[\"']?[^\\s\"']{8,})"),        // password=xxx
        )

        // 替换模板
        private const val REDACTED = "[REDACTED]"

        // 保留前后字符的脱敏
        private const val VISIBLE_PREFIX = 6
        private const val VISIBLE_SUFFIX = 4
    }

    /**
     * 脱敏文本中的敏感信息
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    fun redact(text: String): String {
        var result = text
        for (pattern in API_KEY_PATTERNS) {
            result = pattern.replace(result) { match ->
                val value = match.groupValues[1]
                if (value.length > VISIBLE_PREFIX + VISIBLE_SUFFIX + 3) {
                    "${value.take(VISIBLE_PREFIX)}...${value.takeLast(VISIBLE_SUFFIX)}"
                } else {
                    REDACTED
                }
            }
        }
        return result
    }

    /**
     * 检查文本是否包含敏感信息
     *
     * @param text 待检查文本
     * @return 是否包含敏感信息
     */
    fun containsSensitiveData(text: String): Boolean {
        return API_KEY_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * 完全脱敏（不保留前后字符）
     *
     * @param text 原始文本
     * @return 完全脱敏后的文本
     */
    fun redactFull(text: String): String {
        var result = text
        for (pattern in API_KEY_PATTERNS) {
            result = pattern.replace(result, REDACTED)
        }
        return result
    }

    /** Check if a text contains secrets (without masking). */
    fun containsSecrets(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return API_KEY_PATTERNS.any { it.containsMatchIn(text) }
    }

    /** Count the number of secrets found in text. */
    fun countSecrets(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return API_KEY_PATTERNS.sumOf { it.findAll(text).count() }
    }

    /** Redact secrets from a list of strings. */
    fun redactSensitiveList(lines: List<String>): List<String> {
        return lines.map { redact(it) }
    }

    /** Get all secret matches found in text (for debugging). */
    fun findSecrets(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return API_KEY_PATTERNS.flatMap { it.findAll(text).map { m -> m.value }.toList() }
    }

    /** Redact secrets from a JSON string. */
    fun redactSensitiveJson(json: String): String {
        return redact(json)
    }

    fun format(record: Any?): String {
        return ""
    }

}

class RedactingFormatter {
    private val redact = Redact()

    fun format(record: Any?): String {
        val original = record?.toString() ?: ""
        return redact.redact(original)
    }
}
