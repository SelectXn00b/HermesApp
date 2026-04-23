package com.xiaomo.hermes.hermes.agent

/**
 * Prompt Caching - Anthropic 缓存策略
 * 1:1 对齐 hermes/agent/prompt_caching.py
 *
 * 管理 Anthropic prompt caching 的 cache_control 标记。
 */

data class CacheControl(
    val type: String = "ephemeral"
)

data class CachedContent(
    val content: String,
    val cacheControl: CacheControl? = null
)

/**
 * 为 system prompt 添加缓存控制标记
 *
 * @param systemPrompt system prompt 内容
 * @param enabled 是否启用缓存
 * @return 带缓存标记的 content 列表
 */
fun buildCachedSystemPrompt(systemPrompt: String, enabled: Boolean = true): List<Map<String, Any>> {
    if (!enabled) {
        return listOf(mapOf("type" to "text", "text" to systemPrompt))
    }
    return listOf(
        mapOf(
            "type" to "text",
            "text" to systemPrompt,
            "cache_control" to mapOf("type" to "ephemeral")
        )
    )
}

/**
 * 为消息列表中的最后一条添加缓存标记
 *
 * @param messages 消息列表
 * @return 带缓存标记的消息列表
 */
fun addCacheControlToMessages(messages: List<Map<String, Any>>): List<Map<String, Any>> {
    if (messages.isEmpty()) return messages
    return messages.mapIndexed { index, msg ->
        if (index == messages.size - 1) {
            val content = msg["content"]
            if (content is String) {
                msg + mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to content,
                            "cache_control" to mapOf("type" to "ephemeral")
                        )
                    )
                )
            } else {
                msg
            }
        } else {
            msg
        }
    }


}

/** Python `_apply_cache_marker` — add cache_control to a single message. */
@Suppress("UNCHECKED_CAST")
private fun _applyCacheMarker(
    msg: MutableMap<String, Any?>,
    cacheMarker: Map<String, Any?>,
    nativeAnthropic: Boolean = false,
) {
    val role = msg["role"] as? String ?: ""
    val content = msg["content"]

    if (role == "tool") {
        if (nativeAnthropic) {
            msg["cache_control"] = cacheMarker
        }
        return
    }

    if (content == null || content == "") {
        msg["cache_control"] = cacheMarker
        return
    }

    if (content is String) {
        msg["content"] = listOf(
            mapOf("type" to "text", "text" to content, "cache_control" to cacheMarker)
        )
        return
    }

    if (content is List<*> && content.isNotEmpty()) {
        val last = content.last()
        if (last is MutableMap<*, *>) {
            (last as MutableMap<String, Any?>)["cache_control"] = cacheMarker
        }
    }
}

/** Python `apply_anthropic_cache_control` — place up to 4 cache breakpoints. */
fun applyAnthropicCacheControl(
    apiMessages: List<Map<String, Any?>>,
    cacheTtl: String = "5m",
    nativeAnthropic: Boolean = false,
): List<Map<String, Any?>> {
    val messages = apiMessages.map { it.toMutableMap() }.toMutableList()
    if (messages.isEmpty()) return messages

    val marker = mutableMapOf<String, Any?>("type" to "ephemeral")
    if (cacheTtl == "1h") {
        marker["ttl"] = "1h"
    }

    var breakpointsUsed = 0

    if (messages[0]["role"] == "system") {
        _applyCacheMarker(messages[0], marker, nativeAnthropic = nativeAnthropic)
        breakpointsUsed += 1
    }

    val remaining = 4 - breakpointsUsed
    val nonSys = messages.indices.filter { messages[it]["role"] != "system" }
    for (idx in nonSys.takeLast(remaining)) {
        _applyCacheMarker(messages[idx], marker, nativeAnthropic = nativeAnthropic)
    }

    return messages
}
