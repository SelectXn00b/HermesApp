package com.xiaomo.hermes.hermes.agent

/**
 * Anthropic Adapter - Anthropic SDK 封装 + OAuth
 * 1:1 对齐 hermes/agent/anthropic_adapter.py
 *
 * Android 简化版：保留接口定义和消息转换逻辑，
 * OAuth 流程由 app 模块实现。
 */

// ── Data Classes ─────────────────────────────────────────────────────────

data class AnthropicConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.anthropic.com",
    val apiVersion: String = "2023-06-01",
    val maxTokens: Int = 4096,
    val model: String = "claude-sonnet-4-20250514",
    val oauthToken: String = "",
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val oauthRefreshToken: String = ""
)

data class AnthropicMessage(
    val role: String,
    val content: Any  // String or List<Map<String, Any>>
)

data class AnthropicTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

data class AnthropicToolUse(
    val id: String,
    val name: String,
    val input: Map<String, Any>
)

data class AnthropicToolResult(
    val toolUseId: String,
    val content: String,
    val isError: Boolean = false
)

data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<Map<String, Any>>,
    val model: String,
    val stopReason: String?,
    val stopSequence: String?,
    val usage: AnthropicUsage?
)

data class AnthropicUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadInputTokens: Int? = null,
    val cacheCreationInputTokens: Int? = null
)

data class AnthropicStreamEvent(
    val type: String,
    val index: Int = 0,
    val delta: Map<String, Any>? = null,
    val contentBlock: Map<String, Any>? = null,
    val message: Map<String, Any>? = null,
    val usage: AnthropicUsage? = null
)

data class AnthropicOAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val tokenType: String = "bearer"
)

// ── Message Builder ──────────────────────────────────────────────────────

/**
 * Anthropic 消息构建器
 */
class AnthropicMessageBuilder {

    /**
     * 构建 system prompt（支持缓存）
     *
     * @param text system prompt 文本
     * @param cache 是否启用缓存
     * @return system content 列表
     */
    fun buildSystemPrompt(text: String, cache: Boolean = true): List<Map<String, Any>> {
        return buildCachedSystemPrompt(text, cache)
    }

    /**
     * 构建用户消息
     *
     * @param text 用户文本
     * @return 消息 map
     */
    fun buildUserMessage(text: String): Map<String, Any> {
        return mapOf("role" to "user", "content" to text)
    }

    /**
     * 构建包含图片的用户消息
     *
     * @param text 文本
     * @param imageBase64 base64 编码的图片
     * @param mediaType 图片 MIME 类型
     * @return 消息 map
     */
    fun buildUserMessageWithImage(
        text: String,
        imageBase64: String,
        mediaType: String = "image/png"
    ): Map<String, Any> {
        val content = mutableListOf<Map<String, Any>>()
        if (text.isNotEmpty()) {
            content.add(mapOf("type" to "text", "text" to text))
        }
        content.add(
            mapOf(
                "type" to "image",
                "source" to mapOf(
                    "type" to "base64",
                    "media_type" to mediaType,
                    "data" to imageBase64
                )
            )
        )
        return mapOf("role" to "user", "content" to content)
    }

    /**
     * 构建助手消息（包含 tool_use）
     *
     * @param text 文本（可选）
     * @param toolUses 工具调用列表
     * @return 消息 map
     */
    fun buildAssistantMessage(
        text: String = "",
        toolUses: List<AnthropicToolUse> = emptyList()
    ): Map<String, Any> {
        val content = mutableListOf<Map<String, Any>>()
        if (text.isNotEmpty()) {
            content.add(mapOf("type" to "text", "text" to text))
        }
        for (toolUse in toolUses) {
            content.add(
                mapOf(
                    "type" to "tool_use",
                    "id" to toolUse.id,
                    "name" to toolUse.name,
                    "input" to toolUse.input
                )
            )
        }
        return mapOf("role" to "assistant", "content" to content)
    }

    /**
     * 构建 tool_result 消息
     *
     * @param toolResults 工具结果列表
     * @return 消息 map
     */
    fun buildToolResultMessage(toolResults: List<AnthropicToolResult>): Map<String, Any> {
        val content = toolResults.map { result ->
            mapOf(
                "type" to "tool_result",
                "tool_use_id" to result.toolUseId,
                "content" to result.content,
                "is_error" to result.isError
            )
        }
        return mapOf("role" to "user", "content" to content)
    }

    /**
     * 构建工具定义
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param inputSchema 输入 schema
     * @return 工具 map
     */
    fun buildToolDefinition(
        name: String,
        description: String,
        inputSchema: Map<String, Any>
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "input_schema" to inputSchema
        )
    }

    /**
     * 将 OpenAI 格式的消息转换为 Anthropic 格式
     *
     * @param openaiMessages OpenAI 格式的消息列表
     * @return Anthropic 格式的消息列表
     */
    fun convertFromOpenAI(openaiMessages: List<Map<String, Any>>): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        for (msg in openaiMessages) {
            val role = msg["role"] as? String ?: continue
            val content = msg["content"]

            when (role) {
                "system" -> {
                    // system 消息在 Anthropic 中作为顶层参数
                    result.add(mapOf("role" to "user", "content" to (content?.toString() ?: "")))
                }
                "user" -> {
                    result.add(mapOf("role" to "user", "content" to (content?.toString() ?: "")))
                }
                "assistant" -> {
                    result.add(mapOf("role" to "assistant", "content" to (content?.toString() ?: "")))
                }
                "tool" -> {
                    val toolCallId = msg["tool_call_id"] as? String ?: ""
                    result.add(
                        mapOf(
                            "role" to "user",
                            "content" to listOf(
                                mapOf(
                                    "type" to "tool_result",
                                    "tool_use_id" to toolCallId,
                                    "content" to (content as? String ?: "")
                                )
                            )
                        )
                    )
                }
            }
        }
        return result
    }

    /**
     * 将 Anthropic 格式的响应转换为 OpenAI 格式
     *
     * @param response Anthropic 响应
     * @return OpenAI 格式的响应 map
     */
    fun convertToOpenAI(response: AnthropicResponse): Map<String, Any> {
        val choices = mutableListOf<Map<String, Any>>()
        val messageContent = mutableListOf<Any>()
        val toolCalls = mutableListOf<Map<String, Any>>()

        for (block in response.content) {
            when (block["type"]) {
                "text" -> messageContent.add(block["text"] ?: "")
                "tool_use" -> {
                    toolCalls.add(
                        mapOf(
                            "id" to (block["id"] ?: ""),
                            "type" to "function",
                            "function" to mapOf(
                                "name" to (block["name"] ?: ""),
                                "arguments" to com.google.gson.Gson().toJson(block["input"])
                            )
                        )
                    )
                }
            }
        }

        val message = mutableMapOf<String, Any>(
            "role" to "assistant",
            "content" to messageContent.joinToString("")
        )
        if (toolCalls.isNotEmpty()) {
            message["tool_calls"] = toolCalls
        }

        choices.add(
            mapOf(
                "index" to 0,
                "message" to message,
                "finish_reason" to when (response.stopReason) {
                    "end_turn" -> "stop"
                    "max_tokens" -> "length"
                    "tool_use" -> "tool_calls"
                    else -> response.stopReason ?: "stop"
                }
            )
        )

        return mapOf(
            "id" to response.id,
            "object" to "chat.completion",
            "model" to response.model,
            "choices" to choices,
            "usage" to mapOf(
                "prompt_tokens" to (response.usage?.inputTokens ?: 0),
                "completion_tokens" to (response.usage?.outputTokens ?: 0),
                "total_tokens" to ((response.usage?.inputTokens ?: 0) + (response.usage?.outputTokens ?: 0))
            )
        )
    }
}

// ── OAuth Manager ────────────────────────────────────────────────────────

/**
 * Anthropic OAuth 管理器（Android 简化版）
 *
 * OAuth 流程需要 WebView 或外部浏览器，由 app 模块实现。
 * 此类提供 token 存储和刷新逻辑。
 */
class AnthropicOAuthManager(
    private val config: AnthropicConfig
) {

    private var tokens: AnthropicOAuthTokens? = null

    /**
     * 获取当前访问令牌
     */
    fun getAccessToken(): String? {
        val currentTokens = tokens ?: return null
        if (System.currentTimeMillis() >= currentTokens.expiresAt) {
            return null // 过期，需要刷新
        }
        return currentTokens.accessToken
    }

    /**
     * 设置令牌
     */
    fun setTokens(newTokens: AnthropicOAuthTokens) {
        tokens = newTokens
    }

    /**
     * 刷新访问令牌（Android 简化版：返回 null，由 app 模块处理）
     */
    suspend fun refreshAccessToken(): String? {
        val refreshToken = tokens?.refreshToken ?: config.oauthRefreshToken
        if (refreshToken.isEmpty()) return null
        // Android 简化版：实际刷新由 app 模块实现
        return null
    }

    /**
     * 检查是否有有效的令牌
     */
    fun hasValidToken(): Boolean {
        return getAccessToken() != null
    }

    /**
     * 清除令牌
     */
    fun clearTokens() {
        tokens = null
    }

    /**
     * 是否有 OAuth 配置
     */
    fun hasOAuthConfig(): Boolean {
        return config.oauthClientId.isNotEmpty() && config.oauthRefreshToken.isNotEmpty()
    }
}

// ── Adapter Main ─────────────────────────────────────────────────────────

/**
 * Anthropic 适配器主类
 */
class AnthropicAdapter(
    val config: AnthropicConfig = AnthropicConfig()
) {

    val messageBuilder = AnthropicMessageBuilder()
    val oauthManager = AnthropicOAuthManager(config)

    /**
     * 获取 API 请求头
     *
     * @return 请求头 map
     */
    fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["anthropic-version"] = config.apiVersion
        headers["content-type"] = "application/json"

        // 优先使用 OAuth token
        val oauthToken = oauthManager.getAccessToken()
        if (oauthToken != null) {
            headers["authorization"] = "Bearer $oauthToken"
        } else if (config.apiKey.isNotEmpty()) {
            headers["x-api-key"] = config.apiKey
        }

        return headers
    }

    /**
     * 构建完整的 API 请求体
     *
     * @param messages 消息列表
     * @param systemPrompt system prompt
     * @param tools 工具定义列表
     * @param model 模型 ID
     * @param maxTokens 最大 token 数
     * @param temperature 温度
     * @return 请求体 map
     */
    fun buildRequestBody(
        messages: List<Map<String, Any>>,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList(),
        model: String = config.model,
        maxTokens: Int = config.maxTokens,
        temperature: Double? = null,
        stream: Boolean = false
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "max_tokens" to maxTokens,
            "messages" to messages,
            "stream" to stream
        )

        if (systemPrompt.isNotEmpty()) {
            body["system"] = messageBuilder.buildSystemPrompt(systemPrompt, cache = true)
        }

        if (tools.isNotEmpty()) {
            body["tools"] = tools
        }

        if (temperature != null) {
            body["temperature"] = temperature
        }

        return body
    }

    /**
     * 解析 Anthropic API 响应
     *
     * @param json 响应 JSON
     * @return 解析后的响应
     */
    fun parseResponse(json: Map<String, Any>): AnthropicResponse {
        val gson = com.google.gson.Gson()
        val jsonString = gson.toJson(json)
        return gson.fromJson(jsonString, AnthropicResponse::class.java)
    }

    /**
     * 解析流式事件
     *
     * @param json 事件 JSON
     * @return 解析后的事件
     */
    fun parseStreamEvent(json: Map<String, Any>): AnthropicStreamEvent {
        val gson = com.google.gson.Gson()
        val jsonString = gson.toJson(json)
        return gson.fromJson(jsonString, AnthropicStreamEvent::class.java)
    }

    /**
     * 从响应中提取文本内容
     */
    fun extractText(response: AnthropicResponse): String {
        return response.content
            .filter { it["type"] == "text" }
            .joinToString("") { (it["text"] as? String) ?: "" }
    }

    /**
     * 从响应中提取工具调用
     */
    fun extractToolUses(response: AnthropicResponse): List<AnthropicToolUse> {
        return response.content
            .filter { it["type"] == "tool_use" }
            .map { block ->
                @Suppress("UNCHECKED_CAST")
                AnthropicToolUse(
                    id = block["id"] as? String ?: "",
                    name = block["name"] as? String ?: "",
                    input = (block["input"] as? Map<String, Any>) ?: emptyMap()
                )
            }
    }
}
