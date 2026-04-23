package com.xiaomo.hermes.hermes.agent

/**
 * Copilot ACP Client - Copilot ACP 客户端
 * 1:1 对齐 hermes/agent/copilot_acp_client.py
 *
 * Minimal OpenAI-client-compatible facade for Copilot ACP.
 * 在 Android 上 copilot 二进制通常不可用；代码路径保持完整，
 * 运行时若无 binary 会按 Python 一样抛 RuntimeError。
 */

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class _ACPChatCompletions(private val _client: CopilotACPClient) {
    fun create(kwargs: Map<String, Any?> = emptyMap()): Any? {
        @Suppress("UNCHECKED_CAST")
        return _client._createChatCompletion(
            model = kwargs["model"] as? String,
            messages = kwargs["messages"] as? List<Map<String, Any?>>,
            timeout = kwargs["timeout"],
            tools = kwargs["tools"] as? List<Map<String, Any?>>,
            toolChoice = kwargs["tool_choice"],
        )
    }
}

class _ACPChatNamespace(client: CopilotACPClient) {
    val completions = _ACPChatCompletions(client)
}

class CopilotACPClient(
    apiKey: String? = null,
    baseUrl: String? = null,
    defaultHeaders: Map<String, String>? = null,
    acpCommand: String? = null,
    acpArgs: List<String>? = null,
    acpCwd: String? = null,
    command: String? = null,
    args: List<String>? = null,
) {
    val apiKey: String = apiKey ?: "copilot-acp"
    val baseUrl: String = baseUrl ?: ACP_MARKER_BASE_URL
    private val _defaultHeaders: Map<String, String> = (defaultHeaders ?: emptyMap()).toMap()
    private val _acpCommand: String = acpCommand ?: command ?: _resolveCommand()
    private val _acpArgs: List<String> = (acpArgs ?: args ?: _resolveArgs()).toList()
    private val _acpCwd: String = java.io.File(acpCwd ?: System.getProperty("user.dir") ?: ".").canonicalPath
    val chat = _ACPChatNamespace(this)
    var isClosed: Boolean = false
        private set
    private var _activeProcess: Process? = null
    private val _activeProcessLock = Any()

    fun close() {
        val proc: Process?
        synchronized(_activeProcessLock) {
            proc = _activeProcess
            _activeProcess = null
        }
        isClosed = true
        if (proc == null) return
        try {
            proc.destroy()
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        } catch (_: Exception) {
            try { proc.destroyForcibly() } catch (_: Exception) { }
        }
    }

    fun _createChatCompletion(
        model: String? = null,
        messages: List<Map<String, Any?>>? = null,
        timeout: Any? = null,
        tools: List<Map<String, Any?>>? = null,
        toolChoice: Any? = null,
    ): Any {
        val effectiveMessages = messages ?: emptyList()

        val promptText = _formatMessagesAsPrompt(effectiveMessages, model, tools, toolChoice)

        // Normalise timeout: run_agent.py may pass an httpx.Timeout-style object.
        val effectiveTimeout: Double = when (timeout) {
            null -> _DEFAULT_TIMEOUT_SECONDS
            is Int -> timeout.toDouble()
            is Long -> timeout.toDouble()
            is Float -> timeout.toDouble()
            is Double -> timeout
            is Map<*, *> -> {
                val numeric = listOf("read", "write", "connect", "pool", "timeout")
                    .mapNotNull { (timeout[it] as? Number)?.toDouble() }
                if (numeric.isNotEmpty()) numeric.max() else _DEFAULT_TIMEOUT_SECONDS
            }
            else -> _DEFAULT_TIMEOUT_SECONDS
        }

        val (responseText, reasoningText) = _runPrompt(promptText, effectiveTimeout)
        val (toolCalls, cleanedText) = _extractToolCallsFromText(responseText)

        val usage = mapOf(
            "prompt_tokens" to 0,
            "completion_tokens" to 0,
            "total_tokens" to 0,
            "prompt_tokens_details" to mapOf("cached_tokens" to 0),
        )
        val assistantMessage = mapOf<String, Any?>(
            "content" to cleanedText,
            "tool_calls" to toolCalls,
            "reasoning" to reasoningText.ifEmpty { null },
            "reasoning_content" to reasoningText.ifEmpty { null },
            "reasoning_details" to null,
        )
        val finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop"
        val choice = mapOf(
            "message" to assistantMessage,
            "finish_reason" to finishReason,
        )
        return mapOf(
            "choices" to listOf(choice),
            "usage" to usage,
            "model" to (model ?: "copilot-acp"),
        )
    }

    fun _runPrompt(
        promptText: String,
        timeoutSeconds: Double = _DEFAULT_TIMEOUT_SECONDS,
    ): Pair<String, String> {
        val proc: Process = try {
            ProcessBuilder(listOf(_acpCommand) + _acpArgs)
                .directory(java.io.File(_acpCwd))
                .redirectErrorStream(false)
                .start()
        } catch (e: java.io.IOException) {
            throw RuntimeException(
                "Could not start Copilot ACP command '$_acpCommand'. " +
                    "Install GitHub Copilot CLI or set HERMES_COPILOT_ACP_COMMAND/COPILOT_CLI_PATH.",
                e,
            )
        }

        val stdin = proc.outputStream ?: run {
            proc.destroyForcibly()
            throw RuntimeException("Copilot ACP process did not expose stdin pipe.")
        }
        val stdout = proc.inputStream ?: run {
            proc.destroyForcibly()
            throw RuntimeException("Copilot ACP process did not expose stdout pipe.")
        }
        val stderr = proc.errorStream

        isClosed = false
        synchronized(_activeProcessLock) { _activeProcess = proc }

        val inbox = LinkedBlockingQueue<Map<String, Any?>>()
        val stderrTail = ArrayDeque<String>()
        val stderrTailMax = 40

        val outThread = Thread {
            BufferedReader(InputStreamReader(stdout)).use { reader ->
                while (true) {
                    val line = try { reader.readLine() } catch (_: Exception) { null } ?: return@Thread
                    val parsed: Map<String, Any?> = try {
                        @Suppress("UNCHECKED_CAST")
                        com.xiaomo.hermes.hermes.gson.fromJson(line, Map::class.java) as Map<String, Any?>
                    } catch (_: Exception) {
                        mapOf("raw" to line.trimEnd('\n'))
                    }
                    inbox.put(parsed)
                }
            }
        }.apply { isDaemon = true }

        val errThread = Thread {
            BufferedReader(InputStreamReader(stderr)).use { reader ->
                while (true) {
                    val line = try { reader.readLine() } catch (_: Exception) { null } ?: return@Thread
                    synchronized(stderrTail) {
                        stderrTail.addLast(line.trimEnd('\n'))
                        while (stderrTail.size > stderrTailMax) stderrTail.removeFirst()
                    }
                }
            }
        }.apply { isDaemon = true }

        outThread.start()
        errThread.start()

        var nextId = 0

        fun request(
            method: String,
            params: Map<String, Any?>,
            textParts: MutableList<String>? = null,
            reasoningParts: MutableList<String>? = null,
        ): Any? {
            nextId += 1
            val requestId = nextId
            val payload = mapOf(
                "jsonrpc" to "2.0",
                "id" to requestId,
                "method" to method,
                "params" to params,
            )
            stdin.write((com.xiaomo.hermes.hermes.gson.toJson(payload) + "\n").toByteArray(Charsets.UTF_8))
            stdin.flush()

            val deadlineMs = System.currentTimeMillis() + (timeoutSeconds * 1000).toLong()
            while (System.currentTimeMillis() < deadlineMs) {
                if (!proc.isAlive) break
                val msg = inbox.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (_handleServerMessage(msg, proc, _acpCwd, textParts, reasoningParts)) continue

                val msgId = msg["id"]
                if (msgId != requestId && (msgId as? Number)?.toInt() != requestId) continue
                if (msg.containsKey("error")) {
                    @Suppress("UNCHECKED_CAST")
                    val err = (msg["error"] as? Map<String, Any?>) ?: emptyMap()
                    throw RuntimeException("Copilot ACP $method failed: ${err["message"] ?: err}")
                }
                return msg["result"]
            }

            val stderrText = synchronized(stderrTail) { stderrTail.joinToString("\n").trim() }
            if (!proc.isAlive && stderrText.isNotEmpty()) {
                throw RuntimeException("Copilot ACP process exited early: $stderrText")
            }
            throw RuntimeException("Timed out waiting for Copilot ACP response to $method.")
        }

        try {
            request(
                "initialize",
                mapOf(
                    "protocolVersion" to 1,
                    "clientCapabilities" to mapOf(
                        "fs" to mapOf(
                            "readTextFile" to true,
                            "writeTextFile" to true,
                        ),
                    ),
                    "clientInfo" to mapOf(
                        "name" to "hermes-agent",
                        "title" to "Hermes Agent",
                        "version" to "0.0.0",
                    ),
                ),
            )
            @Suppress("UNCHECKED_CAST")
            val session = (request(
                "session/new",
                mapOf(
                    "cwd" to _acpCwd,
                    "mcpServers" to emptyList<Any?>(),
                ),
            ) as? Map<String, Any?>) ?: emptyMap()
            val sessionId = (session["sessionId"] as? String)?.trim().orEmpty()
            if (sessionId.isEmpty()) {
                throw RuntimeException("Copilot ACP did not return a sessionId.")
            }

            val textParts = mutableListOf<String>()
            val reasoningParts = mutableListOf<String>()
            request(
                "session/prompt",
                mapOf(
                    "sessionId" to sessionId,
                    "prompt" to listOf(
                        mapOf("type" to "text", "text" to promptText),
                    ),
                ),
                textParts = textParts,
                reasoningParts = reasoningParts,
            )
            return textParts.joinToString("") to reasoningParts.joinToString("")
        } finally {
            close()
        }
    }

    fun _handleServerMessage(
        msg: Map<String, Any?>,
        process: Process,
        cwd: String,
        textParts: MutableList<String>?,
        reasoningParts: MutableList<String>?,
    ): Boolean {
        val method = msg["method"] as? String ?: return false

        if (method == "session/update") {
            @Suppress("UNCHECKED_CAST")
            val params = (msg["params"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val update = (params["update"] as? Map<String, Any?>) ?: emptyMap()
            val kind = (update["sessionUpdate"] as? String)?.trim().orEmpty()
            @Suppress("UNCHECKED_CAST")
            val content = (update["content"] as? Map<String, Any?>) ?: emptyMap()
            val chunkText = (content["text"] as? String).orEmpty()
            if (kind == "agent_message_chunk" && chunkText.isNotEmpty() && textParts != null) {
                textParts.add(chunkText)
            } else if (kind == "agent_thought_chunk" && chunkText.isNotEmpty() && reasoningParts != null) {
                reasoningParts.add(chunkText)
            }
            return true
        }

        val stdin = process.outputStream ?: return true
        val messageId = msg["id"]
        @Suppress("UNCHECKED_CAST")
        val params = (msg["params"] as? Map<String, Any?>) ?: emptyMap()

        val response: Map<String, Any?> = when (method) {
            "session/request_permission" -> _permissionDenied(messageId)
            "fs/read_text_file" -> try {
                val path = _ensurePathWithinCwd((params["path"] as? String).orEmpty(), cwd)
                var content = if (path.exists()) path.readText() else ""
                val line = params["line"] as? Int
                val limit = params["limit"] as? Int
                if (line != null && line > 1) {
                    val allLines = content.split("\n")
                    val start = line - 1
                    val end = if (limit != null && limit > 0) start + limit else allLines.size
                    content = allLines.subList(start.coerceAtMost(allLines.size), end.coerceAtMost(allLines.size))
                        .joinToString("\n")
                }
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to messageId,
                    "result" to mapOf("content" to content),
                )
            } catch (exc: Exception) {
                _jsonrpcError(messageId, -32602, exc.message ?: exc.toString())
            }
            "fs/write_text_file" -> try {
                val path = _ensurePathWithinCwd((params["path"] as? String).orEmpty(), cwd)
                path.parentFile?.mkdirs()
                path.writeText((params["content"] as? String).orEmpty())
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to messageId,
                    "result" to null,
                )
            } catch (exc: Exception) {
                _jsonrpcError(messageId, -32602, exc.message ?: exc.toString())
            }
            else -> _jsonrpcError(
                messageId,
                -32601,
                "ACP client method '$method' is not supported by Hermes yet.",
            )
        }

        stdin.write((com.xiaomo.hermes.hermes.gson.toJson(response) + "\n").toByteArray(Charsets.UTF_8))
        stdin.flush()
        return true
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
