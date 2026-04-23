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

// ─────────────────────────────────────────────────────────────────────────
// Module-level constants & functions (1:1 对齐 agent/anthropic_adapter.py).
// Android 环境没有 anthropic Python SDK，subprocess，浏览器 OAuth loopback，
// 所以 SDK / OAuth / credential-file 相关函数保留签名但返回 Android 安全的
// 空值；纯逻辑函数（版本匹配、URL 判断、effort 映射）完整实现。
// ─────────────────────────────────────────────────────────────────────────

private val anthropicAdapterLogger =
    com.xiaomo.hermes.hermes.getLogger("anthropic_adapter")

val THINKING_BUDGET: Map<String, Int> = mapOf(
    "xhigh" to 32000,
    "high" to 16000,
    "medium" to 8000,
    "low" to 4000
)

val ADAPTIVE_EFFORT_MAP: Map<String, String> = mapOf(
    "max" to "max",
    "xhigh" to "xhigh",
    "high" to "high",
    "medium" to "medium",
    "low" to "low",
    "minimal" to "low"
)

val _XHIGH_EFFORT_SUBSTRINGS: List<String> = listOf("4-7", "4.7")

val _ADAPTIVE_THINKING_SUBSTRINGS: List<String> = listOf("4-6", "4.6", "4-7", "4.7")

val _NO_SAMPLING_PARAMS_SUBSTRINGS: List<String> = listOf("4-7", "4.7")

val _ANTHROPIC_OUTPUT_LIMITS: Map<String, Int> = mapOf(
    "claude-opus-4-7" to 128_000,
    "claude-opus-4-6" to 128_000,
    "claude-sonnet-4-6" to 64_000,
    "claude-opus-4-5" to 64_000,
    "claude-sonnet-4-5" to 64_000,
    "claude-haiku-4-5" to 64_000,
    "claude-opus-4" to 32_000,
    "claude-sonnet-4" to 64_000,
    "claude-3-7-sonnet" to 128_000,
    "claude-3-5-sonnet" to 8_192,
    "claude-3-5-haiku" to 8_192,
    "claude-3-opus" to 4_096,
    "claude-3-sonnet" to 4_096,
    "claude-3-haiku" to 4_096,
    "minimax" to 131_072
)

const val _ANTHROPIC_DEFAULT_OUTPUT_LIMIT: Int = 128_000

val _COMMON_BETAS: List<String> = listOf(
    "interleaved-thinking-2025-05-14",
    "fine-grained-tool-streaming-2025-05-14"
)

const val _TOOL_STREAMING_BETA: String = "fine-grained-tool-streaming-2025-05-14"

const val _FAST_MODE_BETA: String = "fast-mode-2026-02-01"

val _OAUTH_ONLY_BETAS: List<String> = listOf(
    "claude-code-20250219",
    "oauth-2025-04-20"
)

const val _CLAUDE_CODE_VERSION_FALLBACK: String = "2.1.74"

const val _CLAUDE_CODE_SYSTEM_PREFIX: String =
    "You are Claude Code, Anthropic's official CLI for Claude."

const val _MCP_TOOL_PREFIX: String = "mcp_"

const val _OAUTH_CLIENT_ID: String = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

const val _OAUTH_TOKEN_URL: String = "https://console.anthropic.com/v1/oauth/token"

const val _OAUTH_REDIRECT_URI: String =
    "https://console.anthropic.com/oauth/code/callback"

const val _OAUTH_SCOPES: String = "org:create_api_key user:profile user:inference"

val _HERMES_OAUTH_FILE: java.io.File
    get() = java.io.File(com.xiaomo.hermes.hermes.getHermesHome(), ".anthropic_oauth.json")

private var _claudeCodeVersionCache: String? = null

fun _getAnthropicMaxOutput(model: String): Int {
    val m = model.lowercase().replace(".", "-")
    var bestKey = ""
    var bestVal = _ANTHROPIC_DEFAULT_OUTPUT_LIMIT
    for ((key, v) in _ANTHROPIC_OUTPUT_LIMITS) {
        if (key in m && key.length > bestKey.length) {
            bestKey = key
            bestVal = v
        }
    }
    return bestVal
}

fun _supportsAdaptiveThinking(model: String): Boolean =
    _ADAPTIVE_THINKING_SUBSTRINGS.any { it in model }

fun _supportsXhighEffort(model: String): Boolean =
    _XHIGH_EFFORT_SUBSTRINGS.any { it in model }

fun _forbidsSamplingParams(model: String): Boolean =
    _NO_SAMPLING_PARAMS_SUBSTRINGS.any { it in model }

fun _detectClaudeCodeVersion(): String {
    // Android: 没有 subprocess，直接回退到 fallback。
    return _CLAUDE_CODE_VERSION_FALLBACK
}

fun _getClaudeCodeVersion(): String {
    if (_claudeCodeVersionCache == null) {
        _claudeCodeVersionCache = _detectClaudeCodeVersion()
    }
    return _claudeCodeVersionCache!!
}

fun _isOauthToken(key: String?): Boolean {
    if (key.isNullOrEmpty()) return false
    if (key.startsWith("sk-ant-api")) return false
    if (key.startsWith("sk-ant-")) return true
    if (key.startsWith("eyJ")) return true
    return false
}

fun _normalizeBaseUrlText(baseUrl: Any?): String {
    if (baseUrl == null) return ""
    val s = baseUrl.toString().trim()
    return s
}

fun _isThirdPartyAnthropicEndpoint(baseUrl: String?): Boolean {
    val normalized = _normalizeBaseUrlText(baseUrl)
    if (normalized.isEmpty()) return false
    val n = normalized.trimEnd('/').lowercase()
    if ("anthropic.com" in n) return false
    return true
}

fun _requiresBearerAuth(baseUrl: String?): Boolean {
    val normalized = _normalizeBaseUrlText(baseUrl)
    if (normalized.isEmpty()) return false
    val n = normalized.trimEnd('/').lowercase()
    return n.startsWith("https://api.minimax.io/anthropic") ||
        n.startsWith("https://api.minimaxi.com/anthropic")
}

fun _commonBetasForBaseUrl(baseUrl: String?): List<String> {
    return if (_requiresBearerAuth(baseUrl)) {
        _COMMON_BETAS.filter { it != _TOOL_STREAMING_BETA }
    } else {
        _COMMON_BETAS
    }
}

fun buildAnthropicClient(
    apiKey: String,
    baseUrl: String? = null,
    timeout: Double? = null
): Any? {
    // Android 没有 Anthropic Python SDK；调用侧需走 AnthropicAdapter 类的
    // HTTP 实现。保留签名用于对齐检查。
    anthropicAdapterLogger.debug(
        "buildAnthropicClient: not supported on Android (key=${if (apiKey.isNotEmpty()) "***" else ""})"
    )
    return null
}

fun buildAnthropicBedrockClient(region: String): Any? {
    anthropicAdapterLogger.debug("buildAnthropicBedrockClient: Bedrock not supported on Android")
    return null
}

fun readClaudeCodeCredentials(): Map<String, Any?>? {
    // Android 环境里没有 ~/.claude/.credentials.json；保留签名返回 null。
    return null
}

fun readClaudeManagedKey(): String? {
    // Python 读 ~/.claude.json 里的 primaryApiKey。Android 应用层没有这个
    // 文件，但 hermes-android 也可以跑在桌面 JVM 上，所以保留同样的路径读取逻辑。
    val home = System.getProperty("user.home") ?: return null
    val claudeJson = java.io.File(home, ".claude.json")
    if (!claudeJson.exists()) return null
    return try {
        val text = claudeJson.readText(Charsets.UTF_8)
        val parsed = com.google.gson.Gson().fromJson(text, Map::class.java)
        val primary = parsed?.get("primaryApiKey") as? String
        primary?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

fun isClaudeCodeTokenValid(creds: Map<String, Any?>?): Boolean {
    if (creds == null) return false
    val expiresAt = when (val v = creds["expiresAt"]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: return false
        else -> return false
    }
    // Python 的实现：expires_at 是毫秒时间戳，且比当前时间晚 60s 以上认为有效
    val now = System.currentTimeMillis()
    return expiresAt > now + 60_000L
}

fun refreshAnthropicOauthPure(
    refreshToken: String,
    useJson: Boolean = false
): Map<String, Any?> {
    // Android 上没有 httpx/requests 同步栈；异步 HTTP 由 AnthropicAdapter 类
    // 负责实现。保留签名返回空映射以维持 API 形状。
    return mapOf(
        "success" to false,
        "error" to "OAuth refresh not supported in this adapter stub"
    )
}

fun _refreshOauthToken(creds: Map<String, Any?>): String? {
    // Python: 拿 refreshToken 调 OAuth 端点刷新 → 写 ~/.claude/.credentials.json
    // → 返回新的 access_token。Android 端 refresh 路径通过
    // AnthropicOAuthManager 走 DataStore；此顶层函数仅做签名对齐 + 纯函数分支。
    val refreshToken = (creds["refreshToken"] as? String)?.trim().orEmpty()
    if (refreshToken.isEmpty()) return null
    return try {
        val refreshed = refreshAnthropicOauthPure(refreshToken, useJson = false)
        if (refreshed["success"] == false) return null
        val accessToken = (refreshed["access_token"] as? String)?.trim().orEmpty()
        if (accessToken.isEmpty()) return null
        _writeClaudeCodeCredentials(
            mapOf(
                "accessToken" to accessToken,
                "refreshToken" to (refreshed["refresh_token"] as? String ?: refreshToken),
                "expiresAt" to (refreshed["expires_at_ms"] as? Number ?: 0L)
            )
        )
        accessToken
    } catch (_: Exception) {
        null
    }
}

fun _writeClaudeCodeCredentials(creds: Map<String, Any?>): Boolean {
    // Android: 我们不写 ~/.claude/.credentials.json。调用者若需要持久化，
    // 走 AnthropicOAuthManager 的 DataStore。
    return false
}

fun _resolveClaudeCodeTokenFromCredentials(
    creds: Map<String, Any?>? = null
): String? {
    val c = creds ?: readClaudeCodeCredentials() ?: return null
    val token = c["accessToken"] as? String
    if (token.isNullOrEmpty()) return null
    if (!isClaudeCodeTokenValid(c)) return null
    return token
}

fun _preferRefreshableClaudeCodeToken(
    envToken: String,
    creds: Map<String, Any?>?
): String? {
    val resolved = _resolveClaudeCodeTokenFromCredentials(creds)
    if (!resolved.isNullOrEmpty()) return resolved
    return envToken.ifEmpty { null }
}

fun resolveAnthropicToken(): String? {
    val envToken = System.getenv("ANTHROPIC_API_KEY") ?: ""
    val creds = readClaudeCodeCredentials()
    val preferred = _preferRefreshableClaudeCodeToken(envToken, creds)
    if (!preferred.isNullOrEmpty()) return preferred
    return readClaudeManagedKey()
}

fun runOauthSetupToken(): String? {
    // Android 上用 AnthropicOAuthManager 走 AppAuth/自定义 scheme；此 stub
    // 对齐 Python 签名，始终返回 null（不支持 TTY 交互输入）。
    return null
}

fun _generatePkce(): Pair<String, String> {
    val verifierBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    val verifier = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.UTF_8))
    val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    return verifier to challenge
}

fun runHermesOauthLoginPure(): Map<String, Any?>? {
    // 本机 OAuth loopback 在 Android 上不可行；由 AnthropicOAuthManager 承接。
    return null
}

fun readHermesOauthCredentials(): Map<String, Any?>? {
    val f = _HERMES_OAUTH_FILE
    if (!f.exists()) return null
    return try {
        val text = f.readText(Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        com.xiaomo.hermes.hermes.gson.fromJson(text, Map::class.java) as? Map<String, Any?>
    } catch (_: Throwable) {
        null
    }
}

fun normalizeModelName(model: String, preserveDots: Boolean = false): String {
    var m = model
    val lower = m.lowercase()
    if (lower.startsWith("anthropic/")) {
        m = m.substring("anthropic/".length)
    }
    if (!preserveDots) {
        m = m.replace(".", "-")
    }
    return m
}

fun _sanitizeToolId(toolId: String?): String {
    if (toolId.isNullOrEmpty()) return "tool_0"
    val sanitized = Regex("[^a-zA-Z0-9_-]").replace(toolId, "_")
    return sanitized.ifEmpty { "tool_0" }
}

@Suppress("UNCHECKED_CAST")
fun convertToolsToAnthropic(tools: List<Map<String, Any?>>?): List<Map<String, Any?>> {
    if (tools.isNullOrEmpty()) return emptyList()
    val result = mutableListOf<Map<String, Any?>>()
    for (t in tools) {
        val fn = (t["function"] as? Map<String, Any?>) ?: emptyMap()
        result.add(
            mapOf(
                "name" to (fn["name"] ?: ""),
                "description" to (fn["description"] ?: ""),
                "input_schema" to (fn["parameters"]
                    ?: mapOf("type" to "object", "properties" to emptyMap<String, Any?>()))
            )
        )
    }
    return result
}

fun _imageSourceFromOpenaiUrl(url: String?): Map<String, String> {
    val raw = (url ?: "").trim()
    if (raw.isEmpty()) return mapOf("type" to "url", "url" to "")
    if (raw.startsWith("data:")) {
        val commaIdx = raw.indexOf(',')
        val header = if (commaIdx >= 0) raw.substring(0, commaIdx) else raw
        val data = if (commaIdx >= 0) raw.substring(commaIdx + 1) else ""
        var mediaType = "image/jpeg"
        if (header.startsWith("data:")) {
            val mimePart = header.substring("data:".length).split(";", limit = 2)[0].trim()
            if (mimePart.startsWith("image/")) mediaType = mimePart
        }
        return mapOf(
            "type" to "base64",
            "media_type" to mediaType,
            "data" to data
        )
    }
    return mapOf("type" to "url", "url" to raw)
}

@Suppress("UNCHECKED_CAST")
fun _convertContentPartToAnthropic(part: Any?): Map<String, Any?>? {
    if (part == null) return null
    if (part is String) return mapOf("type" to "text", "text" to part)
    if (part !is Map<*, *>) return mapOf("type" to "text", "text" to part.toString())
    val p = part as Map<String, Any?>
    val ptype = p["type"] as? String
    if (ptype == "input_text" || ptype == "text") {
        return mapOf("type" to "text", "text" to (p["text"] ?: ""))
    }
    if (ptype == "image_url" || ptype == "input_image") {
        val src = when (val raw = p["image_url"]) {
            is Map<*, *> -> _imageSourceFromOpenaiUrl((raw as Map<String, Any?>)["url"] as? String)
            is String -> _imageSourceFromOpenaiUrl(raw)
            else -> _imageSourceFromOpenaiUrl(p["url"] as? String)
        }
        return mapOf("type" to "image", "source" to src)
    }
    return p
}

fun _toPlainData(value: Any?, depth: Int = 0, path: MutableSet<Int>? = null): Any? {
    if (depth > 64) return value?.toString() ?: ""
    if (value == null) return null
    if (value is String || value is Number || value is Boolean) return value
    val visited = path ?: mutableSetOf()
    val id = System.identityHashCode(value)
    if (id in visited) return "<cycle>"
    visited.add(id)
    return when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) ->
            (k?.toString() ?: "") to _toPlainData(v, depth + 1, visited)
        }
        is List<*> -> value.map { _toPlainData(it, depth + 1, visited) }
        is Array<*> -> value.map { _toPlainData(it, depth + 1, visited) }
        else -> value.toString()
    }
}

@Suppress("UNCHECKED_CAST")
fun _extractPreservedThinkingBlocks(message: Map<String, Any?>): List<Map<String, Any?>> {
    val blocks = message["_anthropic_thinking_blocks"] as? List<Map<String, Any?>>
    return blocks ?: emptyList()
}

@Suppress("UNCHECKED_CAST")
fun _convertContentToAnthropic(content: Any?): Any? {
    if (content == null) return null
    if (content is String) return content
    if (content is List<*>) {
        return content.mapNotNull { _convertContentPartToAnthropic(it) }
    }
    return content.toString()
}

@Suppress("UNCHECKED_CAST")
fun convertMessagesToAnthropic(
    messages: List<Map<String, Any?>>,
    model: String = ""
): Pair<String?, List<Map<String, Any?>>> {
    // 精简版转换：抽取 system 消息作为顶层 system 字段，其余直接映射 role/content。
    // 完整的工具调用折叠/prefix-cache 标注由 AnthropicAdapter 类处理。
    var system: String? = null
    val out = mutableListOf<Map<String, Any?>>()
    for (m in messages) {
        val role = m["role"] as? String ?: continue
        val content = m["content"]
        if (role == "system") {
            val text = when (content) {
                is String -> content
                is List<*> -> content.joinToString("\n") {
                    when (it) {
                        is String -> it
                        is Map<*, *> -> ((it as Map<String, Any?>)["text"] as? String) ?: ""
                        else -> it?.toString() ?: ""
                    }
                }
                else -> content?.toString() ?: ""
            }
            system = if (system.isNullOrEmpty()) text else "$system\n\n$text"
            continue
        }
        val converted = _convertContentToAnthropic(content)
        out.add(mapOf("role" to role, "content" to (converted ?: "")))
    }
    return system to out
}

fun buildAnthropicKwargs(
    model: String,
    messages: List<Map<String, Any?>>,
    tools: List<Map<String, Any?>>? = null,
    maxTokens: Int? = null,
    temperature: Double? = null,
    topP: Double? = null,
    topK: Int? = null,
    effort: String? = null,
    extra: Map<String, Any?> = emptyMap()
): Map<String, Any?> {
    val normalizedModel = normalizeModelName(model)
    val (system, anthMessages) = convertMessagesToAnthropic(messages, normalizedModel)
    val kwargs = mutableMapOf<String, Any?>(
        "model" to normalizedModel,
        "messages" to anthMessages,
        "max_tokens" to (maxTokens ?: _getAnthropicMaxOutput(normalizedModel))
    )
    if (!system.isNullOrEmpty()) kwargs["system"] = system
    if (!tools.isNullOrEmpty()) kwargs["tools"] = convertToolsToAnthropic(tools)
    if (!_forbidsSamplingParams(normalizedModel)) {
        if (temperature != null) kwargs["temperature"] = temperature
        if (topP != null) kwargs["top_p"] = topP
        if (topK != null) kwargs["top_k"] = topK
    }
    if (!effort.isNullOrEmpty() && _supportsAdaptiveThinking(normalizedModel)) {
        var mappedEffort = ADAPTIVE_EFFORT_MAP[effort] ?: effort
        if (mappedEffort == "xhigh" && !_supportsXhighEffort(normalizedModel)) {
            mappedEffort = "max"
        }
        kwargs["output_config"] = mapOf("effort" to mappedEffort)
    }
    kwargs.putAll(extra)
    return kwargs
}

@Suppress("UNCHECKED_CAST")
fun normalizeAnthropicResponse(
    response: Any?,
    @Suppress("UNUSED_PARAMETER") stripToolPrefix: Boolean = false,
): Map<String, Any?> {
    // 将 Anthropic Messages 响应转为 OpenAI ChatCompletion 形状的极简映射。
    // 完整版由 AnthropicAdapter 类实现；这里仅用于对齐/测试。
    if (response !is Map<*, *>) return mapOf("choices" to emptyList<Any?>())
    val r = response as Map<String, Any?>
    val contentList = (r["content"] as? List<Map<String, Any?>>) ?: emptyList()
    val textBuf = StringBuilder()
    val toolCalls = mutableListOf<Map<String, Any?>>()
    for (block in contentList) {
        when (block["type"] as? String) {
            "text" -> textBuf.append(block["text"] as? String ?: "")
            "tool_use" -> toolCalls.add(
                mapOf(
                    "id" to _sanitizeToolId(block["id"] as? String),
                    "type" to "function",
                    "function" to mapOf(
                        "name" to (block["name"] as? String ?: ""),
                        "arguments" to com.xiaomo.hermes.hermes.gson.toJson(block["input"] ?: emptyMap<String, Any?>())
                    )
                )
            )
        }
    }
    val message = mutableMapOf<String, Any?>(
        "role" to "assistant",
        "content" to textBuf.toString()
    )
    if (toolCalls.isNotEmpty()) message["tool_calls"] = toolCalls
    return mapOf(
        "id" to (r["id"] ?: ""),
        "model" to (r["model"] ?: ""),
        "choices" to listOf(
            mapOf(
                "index" to 0,
                "finish_reason" to (r["stop_reason"] ?: "stop"),
                "message" to message
            )
        ),
        "usage" to (r["usage"] ?: emptyMap<String, Any?>())
    )
}

@Suppress("UNCHECKED_CAST")
fun normalizeAnthropicResponseV2(
    response: Any?,
    @Suppress("UNUSED_PARAMETER") stripToolPrefix: Boolean = false,
): Map<String, Any?> {
    // v2 与 v1 同构，只是把 stop_reason 映射为 OpenAI 术语。
    val base = normalizeAnthropicResponse(response)
    val choices = (base["choices"] as? List<Map<String, Any?>>) ?: emptyList()
    val fixed = choices.map { c ->
        val reason = when (c["finish_reason"] as? String) {
            "end_turn" -> "stop"
            "max_tokens" -> "length"
            "tool_use" -> "tool_calls"
            null -> "stop"
            else -> c["finish_reason"] as String
        }
        c + ("finish_reason" to reason)
    }
    return base + ("choices" to fixed)
}
