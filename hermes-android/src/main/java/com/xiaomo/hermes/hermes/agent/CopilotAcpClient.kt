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

// ── Constants ported from agent/copilot_acp_client.py ──────────────────────

const val ACP_MARKER_BASE_URL: String = "acp://copilot"
const val _DEFAULT_TIMEOUT_SECONDS: Double = 900.0

/** Matches `<tool_call>{...}</tool_call>` blocks. */
val _TOOL_CALL_BLOCK_RE: Regex = Regex(
    "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
    setOf(RegexOption.DOT_MATCHES_ALL))

/** Matches bare OpenAI-style tool-call JSON objects. */
val _TOOL_CALL_JSON_RE: Regex = Regex(
    "\\{\\s*\"id\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"type\"\\s*:\\s*\"function\"\\s*,\\s*\"function\"\\s*:\\s*\\{.*?\\}\\s*\\}",
    setOf(RegexOption.DOT_MATCHES_ALL))

// ── Module-level helpers ported from agent/copilot_acp_client.py ───────────

/** Resolve the Copilot CLI command — uses env vars, falls back to "copilot". */
fun _resolveCommand(): String {
    val override1 = System.getenv("HERMES_COPILOT_ACP_COMMAND")?.trim().orEmpty()
    if (override1.isNotEmpty()) return override1
    val override2 = System.getenv("COPILOT_CLI_PATH")?.trim().orEmpty()
    if (override2.isNotEmpty()) return override2
    return "copilot"
}

/** Resolve the arg list for the Copilot CLI subprocess. */
fun _resolveArgs(): List<String> {
    val raw = System.getenv("HERMES_COPILOT_ACP_ARGS")?.trim().orEmpty()
    if (raw.isEmpty()) return listOf("--acp", "--stdio")
    // Simple shlex-like split honouring single/double quotes.
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var quote: Char? = null
    for (c in raw) {
        when {
            quote != null -> {
                if (c == quote) quote = null else buf.append(c)
            }
            c == '"' || c == '\'' -> quote = c
            c.isWhitespace() -> {
                if (buf.isNotEmpty()) { out.add(buf.toString()); buf.setLength(0) }
            }
            else -> buf.append(c)
        }
    }
    if (buf.isNotEmpty()) out.add(buf.toString())
    return out
}

/** Build a JSON-RPC error envelope. */
fun _jsonrpcError(messageId: Any?, code: Int, message: String): Map<String, Any?> = mapOf(
    "jsonrpc" to "2.0",
    "id" to messageId,
    "error" to mapOf("code" to code, "message" to message))

/** Build a JSON-RPC "permission denied / cancelled" response. */
fun _permissionDenied(messageId: Any?): Map<String, Any?> = mapOf(
    "jsonrpc" to "2.0",
    "id" to messageId,
    "result" to mapOf("outcome" to mapOf("outcome" to "cancelled")))

/** Render a message content field (string, dict, list) into plain text. */
@Suppress("UNCHECKED_CAST")
fun _renderMessageContent(content: Any?): String {
    if (content == null) return ""
    if (content is String) return content.trim()
    if (content is Map<*, *>) {
        val map = content as Map<String, Any?>
        if (map.containsKey("text")) return (map["text"]?.toString() ?: "").trim()
        val nested = map["content"]
        if (nested is String) return nested.trim()
        return try { com.xiaomo.hermes.hermes.gson.toJson(map) } catch (_: Exception) { map.toString() }
    }
    if (content is List<*>) {
        val parts = mutableListOf<String>()
        for (item in content) {
            if (item is String) parts.add(item)
            else if (item is Map<*, *>) {
                val t = item["text"] as? String
                if (!t.isNullOrBlank()) parts.add(t.trim())
            }
        }
        return parts.joinToString("\n").trim()
    }
    return content.toString().trim()
}

/**
 * Format a list of OpenAI-style messages into a plain-text prompt for the
 * Copilot ACP agent backend.
 */
@Suppress("UNCHECKED_CAST")
fun _formatMessagesAsPrompt(
    messages: List<Map<String, Any?>>,
    model: String? = null,
    tools: List<Map<String, Any?>>? = null,
    toolChoice: Any? = null): String {
    val sections = mutableListOf(
        "You are being used as the active ACP agent backend for Hermes.",
        "Use ACP capabilities to complete tasks.",
        "IMPORTANT: If you take an action with a tool, you MUST output tool calls using <tool_call>{...}</tool_call> blocks with JSON exactly in OpenAI function-call shape.",
        "If no tool is needed, answer normally.")
    if (!model.isNullOrEmpty()) sections.add("Hermes requested model hint: $model")

    if (tools != null && tools.isNotEmpty()) {
        val specs = mutableListOf<Map<String, Any?>>()
        for (t in tools) {
            val fn = t["function"] as? Map<String, Any?> ?: continue
            val name = (fn["name"] as? String)?.trim()
            if (name.isNullOrEmpty()) continue
            specs.add(mapOf(
                "name" to name,
                "description" to (fn["description"] ?: ""),
                "parameters" to (fn["parameters"] ?: emptyMap<String, Any?>())))
        }
        if (specs.isNotEmpty()) {
            sections.add(
                "Available tools (OpenAI function schema). When using a tool, emit ONLY <tool_call>{...}</tool_call> with one JSON object containing id/type/function{name,arguments}. arguments must be a JSON string.\n" +
                    com.xiaomo.hermes.hermes.gson.toJson(specs))
        }
    }

    if (toolChoice != null) {
        sections.add("Tool choice hint: " + com.xiaomo.hermes.hermes.gson.toJson(toolChoice))
    }

    val transcript = mutableListOf<String>()
    for (msg in messages) {
        var role = (msg["role"] as? String ?: "unknown").trim().lowercase()
        if (role != "system" && role != "user" && role != "assistant" && role != "tool") role = "context"
        val rendered = _renderMessageContent(msg["content"])
        if (rendered.isEmpty()) continue
        val label = when (role) {
            "system" -> "System"; "user" -> "User"; "assistant" -> "Assistant"
            "tool" -> "Tool"; "context" -> "Context"
            else -> role.replaceFirstChar { it.uppercase() }
        }
        transcript.add("$label:\n$rendered")
    }
    if (transcript.isNotEmpty()) {
        sections.add("Conversation transcript:\n\n" + transcript.joinToString("\n\n"))
    }
    sections.add("Continue the conversation from the latest user request.")
    return sections.filter { it.isNotBlank() }.joinToString("\n\n") { it.trim() }
}

/**
 * Pull `<tool_call>{...}</tool_call>` blocks (or bare JSON objects) out of
 * model output text. Returns (list of parsed tool-calls, residual text).
 */
@Suppress("UNCHECKED_CAST")
fun _extractToolCallsFromText(text: String?): Pair<List<Map<String, Any?>>, String> {
    if (text.isNullOrBlank()) return emptyList<Map<String, Any?>>() to ""
    val extracted = mutableListOf<Map<String, Any?>>()
    val consumed = mutableListOf<IntRange>()

    fun tryAdd(rawJson: String) {
        val obj = try {
            com.xiaomo.hermes.hermes.gson.fromJson(rawJson, Map::class.java) as? Map<String, Any?>
        } catch (_: Exception) { null } ?: return
        val fn = obj["function"] as? Map<String, Any?> ?: return
        val fnName = (fn["name"] as? String)?.trim().orEmpty()
        if (fnName.isEmpty()) return
        val rawArgs = fn["arguments"] ?: "{}"
        val fnArgs = if (rawArgs is String) rawArgs else com.xiaomo.hermes.hermes.gson.toJson(rawArgs)
        val callId = (obj["id"] as? String)?.trim()?.ifEmpty { null }
            ?: "acp_call_${extracted.size + 1}"
        extracted.add(mapOf(
            "id" to callId,
            "call_id" to callId,
            "response_item_id" to null,
            "type" to "function",
            "function" to mapOf("name" to fnName, "arguments" to fnArgs)))
    }

    for (m in _TOOL_CALL_BLOCK_RE.findAll(text)) {
        tryAdd(m.groupValues[1])
        consumed.add(m.range)
    }
    if (extracted.isEmpty()) {
        for (m in _TOOL_CALL_JSON_RE.findAll(text)) {
            tryAdd(m.value)
            consumed.add(m.range)
        }
    }
    if (consumed.isEmpty()) return extracted to text.trim()

    val sorted = consumed.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in sorted) {
        if (merged.isEmpty() || r.first > merged.last().last + 1) merged.add(r)
        else {
            val last = merged.last()
            merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
        }
    }
    val parts = mutableListOf<String>()
    var cursor = 0
    for (r in merged) {
        if (cursor < r.first) parts.add(text.substring(cursor, r.first))
        cursor = maxOf(cursor, r.last + 1)
    }
    if (cursor < text.length) parts.add(text.substring(cursor))
    val cleaned = parts.filter { it.isNotBlank() }.joinToString("\n") { it.trim() }.trim()
    return extracted to cleaned
}

/**
 * Validate that *pathText* is an absolute path within *cwd*.
 *
 * Throws SecurityException on escape attempts.
 */
fun _ensurePathWithinCwd(pathText: String, cwd: String): java.io.File {
    val candidate = java.io.File(pathText)
    if (!candidate.isAbsolute) {
        throw SecurityException("ACP file-system paths must be absolute.")
    }
    val resolved = candidate.canonicalFile
    val root = java.io.File(cwd).canonicalFile
    val resolvedPath = resolved.absolutePath
    val rootPath = root.absolutePath
    if (!(resolvedPath == rootPath || resolvedPath.startsWith(rootPath + java.io.File.separator))) {
        throw SecurityException("Path '$resolvedPath' is outside the session cwd '$rootPath'.")
    }
    return resolved
}
