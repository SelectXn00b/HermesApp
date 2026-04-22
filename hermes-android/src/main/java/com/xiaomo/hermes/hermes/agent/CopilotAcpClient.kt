package com.xiaomo.hermes.hermes.agent

/**
 * Copilot ACP Client - Copilot ACP 客户端（简化版）
 * 1:1 对齐 hermes/agent/copilot_acp_client.py（大幅简化）
 *
 * Android 上不需要完整的 Copilot ACP 协议实现。
 * 保留类名、方法名、结构与 Python 一致，具体实现为 stub。
 */

data class CopilotAcpConfig(
    val apiEndpoint: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scopes: List<String> = emptyList()
)

data class CopilotAcpResponse(
    val content: String,
    val model: String = "",
    val finishReason: String = "",
    val usage: Map<String, Int>? = null
)

/**
 * Copilot ACP 客户端（Android 简化版）
 */
class CopilotAcpClient(
    private val config: CopilotAcpConfig = CopilotAcpConfig()
) {

    private var accessToken: String? = null
    private var tokenExpiryMs: Long = 0L
    private var _activeProcess: Process? = null
    var isClosed: Boolean = false

    /**
     * 获取访问令牌
     *
     * @return 访问令牌
     */
    suspend fun getAccessToken(): String? {
        // Android 简化版：token 由外部提供
        return accessToken
    }

    /**
     * 设置访问令牌
     *
     * @param token 访问令牌
     * @param expiresInMs 过期时间（毫秒）
     */
    fun setAccessToken(token: String, expiresInMs: Long = 3600_000L) {
        accessToken = token
        tokenExpiryMs = System.currentTimeMillis() + expiresInMs
    }

    /**
     * 检查令牌是否有效
     *
     * @return 令牌是否有效
     */
    fun isTokenValid(): Boolean {
        return accessToken != null && System.currentTimeMillis() < tokenExpiryMs
    }

    /**
     * 发送聊天请求
     *
     * @param messages 消息列表
     * @param model 模型 ID
     * @return 响应结果
     */
    suspend fun chat(
        messages: List<Map<String, Any>>,
        model: String = ""
    ): CopilotAcpResponse {
        // Android 简化版：实际实现由 app 模块提供
        return CopilotAcpResponse(
            content = "",
            model = model,
            finishReason = "stub"
        )
    }

    /**
     * 发送流式聊天请求
     *
     * @param messages 消息列表
     * @param model 模型 ID
     * @param onChunk 接收每个 chunk 的回调
     */
    suspend fun chatStream(
        messages: List<Map<String, Any>>,
        model: String = "",
        onChunk: (String) -> Unit
    ) {
        // Android 简化版：实际实现由 app 模块提供
    }

    /**
     * 列出可用模型
     *
     * @return 模型 ID 列表
     */
    suspend fun listModels(): List<String> {
        // Android 简化版：返回空列表
        return emptyList()
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息 map
     */
    suspend fun getUserInfo(): Map<String, Any>? {
        // Android 简化版：返回 null
        return null
    }

    /**
     * 撤销访问令牌
     */
    suspend fun revokeToken() {
        accessToken = null
        tokenExpiryMs = 0L
    }



    fun create(kwargs: Any): Any {
        throw NotImplementedError("create")
    }
    /** Release resources. Mark client as closed and terminate any active process. */
    fun close(): Unit {
        val proc = _activeProcess
        _activeProcess = null
        isClosed = true
        if (proc == null) return
        try {
            proc.destroy()
            if (!proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        } catch (_: Exception) { }
    }
    fun _createChatCompletion(_unused: Any): Any {
        throw NotImplementedError("_createChatCompletion")
    }
    fun _runPrompt(promptText: String): Pair<String, String> {
        throw NotImplementedError("_runPrompt")
    }
    fun _handleServerMessage(msg: Map<String, Any>): Boolean {
        return false
    }

}

class _ACPChatCompletions(private val _client: CopilotACPClient) {
    fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        return _client._createChatCompletion(kwargs)
    }
}

class _ACPChatNamespace(client: CopilotACPClient) {
    val completions = _ACPChatCompletions(client)
}

class CopilotACPClient(
    val apiKey: String = "copilot-acp",
    val baseUrl: String = "",
    private val _defaultHeaders: Map<String, String> = emptyMap(),
    private val _acpCommand: String = "",
    private val _acpArgs: List<String> = emptyList(),
    private val _acpCwd: String = ""
) {
    val chat = _ACPChatNamespace(this)
    var isClosed: Boolean = false
        private set
    private var _activeProcess: Process? = null

    fun close() {
        val proc = _activeProcess
        _activeProcess = null
        isClosed = true
        if (proc == null) return
        try {
            proc.destroy()
            if (!proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        } catch (_: Exception) { }
    }

    fun _createChatCompletion(kwargs: Any): Any? {
        // Android: ACP subprocess not available; stub only
        return null
    }

    fun _runPrompt(promptText: String): Pair<String, String> {
        throw NotImplementedError("_runPrompt: ACP not available on Android")
    }

    fun _handleServerMessage(msg: Map<String, Any>): Boolean {
        return false
    }
}
