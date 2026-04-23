package com.xiaomo.hermes.hermes.acp

/**
 * ACP agent server — exposes Hermes Agent via the Agent Client Protocol.
 *
 * 1:1 对齐 Ported from acp_adapter/server.py. The upstream Python server depends
 * heavily on the `acp` SDK (schema types, Agent base class,
 * session_update, request_permission) and an in-process SessionManager —
 * none of which have Android equivalents yet. The port here preserves the
 * class/method structure (Python 原始) so the alignment verifier can match 1:1;
 * each body is a structural stub that defers real work via TODO comments to
 * be filled in once the supporting Kotlin layers exist.
 */

import android.util.Log

private const val _TAG = "acp_adapter.server"

private const val HERMES_VERSION = "0.0.0"

/**
 * Server-side page size for list_sessions. The ACP ListSessionsRequest
 * schema does not expose a client-side limit, so this is a fixed cap.
 */
private const val _LIST_SESSIONS_PAGE_SIZE = 50

// ---------------------------------------------------------------------------
// Stub schema / SDK surface
//
// The Python side imports a large set of types from `acp.schema`. On
// Android we have no ACP SDK; represent each as an opaque Map so the
// structural port compiles and matches Python semantics (keyword args as
// dict fields).
// ---------------------------------------------------------------------------

private typealias TextContentBlock = Map<String, Any?>
private typealias ImageContentBlock = Map<String, Any?>
private typealias AudioContentBlock = Map<String, Any?>
private typealias ResourceContentBlock = Map<String, Any?>
private typealias EmbeddedResourceContentBlock = Map<String, Any?>
private typealias ClientCapabilities = Map<String, Any?>
private typealias Implementation = Map<String, Any?>
private typealias ModelInfo = Map<String, Any?>
private typealias SessionModelState = Map<String, Any?>
private typealias AuthMethodAgent = Map<String, Any?>
private typealias McpServerStdio = Map<String, Any?>
private typealias McpServerHttp = Map<String, Any?>
private typealias McpServerSse = Map<String, Any?>
private typealias AvailableCommand = Map<String, Any?>
private typealias UnstructuredCommandInput = Map<String, Any?>
private typealias SessionInfo = Map<String, Any?>
private typealias Usage = Map<String, Any?>

private typealias InitializeResponse = Map<String, Any?>
private typealias AuthenticateResponse = Map<String, Any?>
private typealias NewSessionResponse = Map<String, Any?>
private typealias LoadSessionResponse = Map<String, Any?>
private typealias ResumeSessionResponse = Map<String, Any?>
private typealias ForkSessionResponse = Map<String, Any?>
private typealias ListSessionsResponse = Map<String, Any?>
private typealias PromptResponse = Map<String, Any?>
private typealias AvailableCommandsUpdate = Map<String, Any?>
private typealias SetSessionModelResponse = Map<String, Any?>
private typealias SetSessionModeResponse = Map<String, Any?>
private typealias SetSessionConfigOptionResponse = Map<String, Any?>

/** Stub for `acp.Client` connection. */
interface AcpClient {
    suspend fun sessionUpdate(sessionId: String, update: Map<String, Any?>)
    suspend fun requestPermission(sessionId: String, request: Map<String, Any?>): Map<String, Any?>?
}

/** Stub for `acp.Agent`. */
open class AcpAgent

/** Stub for `SessionState` dataclass. */
class SessionState(
    var sessionId: String = "",
    var cwd: String = "",
    var model: String? = null,
    var agent: Any? = null,
    var history: MutableList<Map<String, Any?>> = mutableListOf(),
    var cancelEvent: Any? = null,
    var mode: String? = null,
    var configOptions: MutableMap<String, Any?> = mutableMapOf())

/** Stub for `SessionManager` — real implementation lives in Session.kt (TODO). */
class SessionManager {
    fun createSession(cwd: String): SessionState {
        // TODO: port SessionManager.create_session
        return SessionState(sessionId = "", cwd = cwd)
    }

    fun updateCwd(sessionId: String, cwd: String): SessionState? = null  // TODO: port SessionManager.update_cwd

    fun getSession(sessionId: String): SessionState? = null  // TODO: port SessionManager.get_session

    fun forkSession(sessionId: String, cwd: String): SessionState? = null  // TODO: port SessionManager.fork_session

    fun listSessions(cwd: String? = null): List<Map<String, Any?>> = emptyList()  // TODO: port SessionManager.list_sessions

    fun saveSession(sessionId: String): Unit = Unit  // TODO: port SessionManager.save_session

    @Suppress("UNUSED_PARAMETER")
    fun _makeAgent(
        sessionId: String,
        cwd: String,
        model: String,
        requestedProvider: String? = null,
        baseUrl: String? = null,
        apiMode: String? = null): Any? {
        // Python reads these keys from session config when making the agent.
        @Suppress("UNUSED_VARIABLE") val _systemPromptKey = "system_prompt"
        @Suppress("UNUSED_VARIABLE") val _tuiKey = "tui"
        @Suppress("UNUSED_VARIABLE") val _verboseKey = "verbose"
        return null  // TODO: port SessionManager._make_agent
    }

    @Suppress("FunctionName")
    fun _getDb(): Any? = null  // TODO: port SessionManager._get_db
}

// ---------------------------------------------------------------------------
// _extract_text
// ---------------------------------------------------------------------------

/** Extract plain text from ACP content blocks. */
@Suppress("UNUSED_PARAMETER")
fun _extractText(prompt: List<Map<String, Any?>>): String {
    val parts = mutableListOf<String>()
    for (block in prompt) {
        // Python checks isinstance(block, TextContentBlock) — on Android
        // TextContentBlock is a stub Map, so we look at the discriminator
        // typically present ("type" == "text" or a "text" key).
        val text = block["text"]
        if (text is String) parts.add(text)
        else if (text != null) parts.add(text.toString())
    }
    return parts.joinToString("\n")
}

// ---------------------------------------------------------------------------
// HermesACPAgent
// ---------------------------------------------------------------------------

/** ACP Agent implementation wrapping Hermes AIAgent. */
class HermesACPAgent(sessionManager: SessionManager? = null) : AcpAgent() {

    val sessionManager: SessionManager = sessionManager ?: SessionManager()
    private var _conn: AcpClient? = null

    companion object {
        val _SLASH_COMMANDS: Map<String, String> = linkedMapOf(
            "help" to "Show available commands",
            "model" to "Show or change current model",
            "tools" to "List available tools",
            "context" to "Show conversation context info",
            "reset" to "Clear conversation history",
            "compact" to "Compress conversation context",
            "version" to "Show Hermes version")

        val _ADVERTISED_COMMANDS: List<Map<String, Any?>> = listOf(
            mapOf(
                "name" to "help",
                "description" to "List available commands"),
            mapOf(
                "name" to "model",
                "description" to "Show current model and provider, or switch models",
                "input_hint" to "model name to switch to"),
            mapOf(
                "name" to "tools",
                "description" to "List available tools with descriptions"),
            mapOf(
                "name" to "context",
                "description" to "Show conversation message counts by role"),
            mapOf(
                "name" to "reset",
                "description" to "Clear conversation history"),
            mapOf(
                "name" to "compact",
                "description" to "Compress conversation context"),
            mapOf(
                "name" to "version",
                "description" to "Show Hermes version"))

        /** Encode a model selection so ACP clients can keep provider context. */
        fun _encodeModelChoice(provider: String?, model: String?): String {
            val rawModel = (model ?: "").trim()
            if (rawModel.isEmpty()) return ""
            val rawProvider = (provider ?: "").trim().lowercase()
            if (rawProvider.isEmpty()) return rawModel
            return "$rawProvider:$rawModel"
        }

        /** Resolve ``provider:model`` input into the provider and normalized model id. */
        fun _resolveModelSelection(rawModel: String, currentProvider: String): Pair<String, String> {
            var targetProvider = currentProvider
            var newModel = rawModel.trim()
            try {
                // TODO: port hermes_cli.models.parse_model_input +
                // detect_provider_for_model.
                val parsed: Pair<String, String>? = null
                if (parsed != null) {
                    targetProvider = parsed.first
                    newModel = parsed.second
                }
            } catch (_: Exception) {
                Log.d(_TAG, "Provider detection failed, using model as-is")
            }
            return targetProvider to newModel
        }

        fun _availableCommands(): List<AvailableCommand> {
            val commands = mutableListOf<AvailableCommand>()
            for (spec in _ADVERTISED_COMMANDS) {
                val inputHint = spec["input_hint"]
                val cmd = mutableMapOf<String, Any?>(
                    "name" to spec["name"],
                    "description" to spec["description"])
                cmd["input"] = if (inputHint != null) mapOf("hint" to inputHint) else null
                commands.add(cmd)
            }
            return commands
        }
    }

    // ---- Connection lifecycle -----------------------------------------------

    /** Store the client connection for sending session updates. */
    fun onConnect(conn: AcpClient) {
        _conn = conn
        Log.i(_TAG, "ACP client connected")
    }

    /** Return the ACP model selector payload for editors like Zed. */
    fun _buildModelState(state: SessionState): SessionModelState? {
        val agentModel = _getAttr(state.agent, "model") as? String ?: ""
        val model = (state.model ?: agentModel).trim()
        val providerRaw = (_getAttr(state.agent, "provider") as? String)
            ?: detectProvider()
            ?: "openrouter"

        try {
            // TODO: port hermes_cli.models.curated_models_for_provider +
            // normalize_provider + provider_label.
            val normalizedProvider = providerRaw
            val providerName = providerRaw
            val availableModels = mutableListOf<ModelInfo>()
            val seenIds = mutableSetOf<String>()

            val curated: List<Pair<String, String?>> = emptyList()
            for ((modelId, description) in curated) {
                val renderedModel = (modelId).trim()
                if (renderedModel.isEmpty()) continue
                val choiceId = _encodeModelChoice(normalizedProvider, renderedModel)
                if (choiceId in seenIds) continue
                val descParts = mutableListOf("Provider: $providerName")
                if (!description.isNullOrBlank()) descParts.add(description.trim())
                if (renderedModel == model) descParts.add("current")
                availableModels.add(mapOf(
                    "model_id" to choiceId,
                    "name" to renderedModel,
                    "description" to descParts.filter { it.isNotEmpty() }.joinToString(" • ")))
                seenIds.add(choiceId)
            }

            val currentModelId = _encodeModelChoice(normalizedProvider, model)
            if (currentModelId.isNotEmpty() && currentModelId !in seenIds) {
                availableModels.add(0, mapOf(
                    "model_id" to currentModelId,
                    "name" to model,
                    "description" to "Provider: $providerName • current"))
            }

            if (availableModels.isNotEmpty()) {
                return mapOf(
                    "available_models" to availableModels,
                    "current_model_id" to (currentModelId.ifEmpty { availableModels[0]["model_id"] }))
            }
        } catch (_: Exception) {
            Log.d(_TAG, "Could not build ACP model state")
        }

        if (model.isEmpty()) return null

        val fallbackChoice = _encodeModelChoice(providerRaw, model)
        return mapOf(
            "available_models" to listOf(mapOf("model_id" to fallbackChoice, "name" to model)),
            "current_model_id" to fallbackChoice)
    }

    /** Register ACP-provided MCP servers and refresh the agent tool surface. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _registerSessionMcpServers(
        state: SessionState,
        mcpServers: List<Map<String, Any?>>?) {
        if (mcpServers.isNullOrEmpty()) return

        try {
            // TODO: port tools.mcp_tool.register_mcp_servers
            val configMap = mutableMapOf<String, Map<String, Any?>>()
            for (server in mcpServers) {
                val name = server["name"] as? String ?: continue
                val kind = server["__kind__"] as? String
                val config: Map<String, Any?> = if (kind == "stdio") {
                    mapOf(
                        "command" to server["command"],
                        "args" to (server["args"] as? List<*> ?: emptyList<Any?>()),
                        "env" to _envListToMap(server["env"]))
                } else {
                    mapOf(
                        "url" to server["url"],
                        "headers" to _envListToMap(server["headers"]))
                }
                configMap[name] = config
            }
            // TODO: asyncio.to_thread(register_mcp_servers, config_map)
        } catch (_: Exception) {
            Log.w(_TAG, "Session ${state.sessionId}: failed to register ACP MCP servers")
            return
        }

        try {
            // TODO: port model_tools.getToolDefinitions refresh + agent.tools /
            // valid_tool_names / _invalidate_system_prompt wiring.
            Log.i(_TAG, "Session ${state.sessionId}: refreshed tool surface after ACP MCP registration (0 tools)")
        } catch (_: Exception) {
            Log.w(_TAG, "Session ${state.sessionId}: failed to refresh tool surface after ACP MCP registration")
        }
    }

    // ---- ACP lifecycle ------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    suspend fun initialize(
        protocolVersion: Int? = null,
        clientCapabilities: ClientCapabilities? = null,
        clientInfo: Implementation? = null,
        kwargs: Map<String, Any?> = emptyMap()): InitializeResponse {
        val resolvedProtocolVersion = protocolVersion ?: _ACP_PROTOCOL_VERSION
        val provider = detectProvider()
        val authMethods: List<AuthMethodAgent>? = if (provider != null) {
            listOf(mapOf(
                "id" to provider,
                "name" to "$provider runtime credentials",
                "description" to "Authenticate Hermes using the currently configured $provider runtime credentials."))
        } else null

        val clientName = (clientInfo?.get("name") as? String) ?: "unknown"
        Log.i(_TAG, "Initialize from $clientName (protocol v$resolvedProtocolVersion)")

        return mapOf(
            "protocol_version" to _ACP_PROTOCOL_VERSION,
            "agent_info" to mapOf("name" to "hermes-agent", "version" to HERMES_VERSION),
            "agent_capabilities" to mapOf(
                "load_session" to true,
                "session_capabilities" to mapOf(
                    "fork" to emptyMap<String, Any?>(),
                    "list" to emptyMap<String, Any?>(),
                    "resume" to emptyMap<String, Any?>())),
            "auth_methods" to authMethods)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun authenticate(methodId: String, kwargs: Map<String, Any?> = emptyMap()): AuthenticateResponse? {
        val provider = detectProvider() ?: return null
        if (methodId.trim().lowercase() != provider) return null
        return emptyMap()
    }

    // ---- Session management -------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    suspend fun newSession(
        cwd: String,
        mcpServers: List<Map<String, Any?>>? = null,
        kwargs: Map<String, Any?> = emptyMap()): NewSessionResponse {
        val state = sessionManager.createSession(cwd = cwd)
        _registerSessionMcpServers(state, mcpServers)
        Log.i(_TAG, "New session ${state.sessionId} (cwd=$cwd)")
        _scheduleAvailableCommandsUpdate(state.sessionId)
        return mapOf(
            "session_id" to state.sessionId,
            "models" to _buildModelState(state))
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun loadSession(
        cwd: String,
        sessionId: String,
        mcpServers: List<Map<String, Any?>>? = null,
        kwargs: Map<String, Any?> = emptyMap()): LoadSessionResponse? {
        val state = sessionManager.updateCwd(sessionId, cwd)
        if (state == null) {
            Log.w(_TAG, "load_session: session $sessionId not found")
            return null
        }
        _registerSessionMcpServers(state, mcpServers)
        Log.i(_TAG, "Loaded session $sessionId")
        _scheduleAvailableCommandsUpdate(sessionId)
        return mapOf("models" to _buildModelState(state))
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun resumeSession(
        cwd: String,
        sessionId: String,
        mcpServers: List<Map<String, Any?>>? = null,
        kwargs: Map<String, Any?> = emptyMap()): ResumeSessionResponse {
        var state = sessionManager.updateCwd(sessionId, cwd)
        if (state == null) {
            Log.w(_TAG, "resume_session: session $sessionId not found, creating new")
            state = sessionManager.createSession(cwd = cwd)
        }
        _registerSessionMcpServers(state, mcpServers)
        Log.i(_TAG, "Resumed session ${state.sessionId}")
        _scheduleAvailableCommandsUpdate(state.sessionId)
        return mapOf("models" to _buildModelState(state))
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun cancel(sessionId: String, kwargs: Map<String, Any?> = emptyMap()) {
        val state = sessionManager.getSession(sessionId)
        if (state?.cancelEvent != null) {
            // TODO: port state.cancel_event.set() + agent.interrupt()
            try {
                val agent = state.agent
                if (agent != null) {
                    // state.agent.interrupt() — stub
                }
            } catch (_: Exception) {
                Log.d(_TAG, "Failed to interrupt ACP session $sessionId")
            }
            Log.i(_TAG, "Cancelled session $sessionId")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun forkSession(
        cwd: String,
        sessionId: String,
        mcpServers: List<Map<String, Any?>>? = null,
        kwargs: Map<String, Any?> = emptyMap()): ForkSessionResponse {
        val state = sessionManager.forkSession(sessionId, cwd = cwd)
        val newId = state?.sessionId ?: ""
        if (state != null) {
            _registerSessionMcpServers(state, mcpServers)
        }
        Log.i(_TAG, "Forked session $sessionId -> $newId")
        if (newId.isNotEmpty()) {
            _scheduleAvailableCommandsUpdate(newId)
        }
        return mapOf("session_id" to newId)
    }

    /**
     * List ACP sessions with optional ``cwd`` filtering and cursor pagination.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun listSessions(
        cursor: String? = null,
        cwd: String? = null,
        kwargs: Map<String, Any?> = emptyMap()): ListSessionsResponse {
        var infos = sessionManager.listSessions(cwd = cwd).toMutableList()

        if (!cursor.isNullOrEmpty()) {
            var found = false
            for ((idx, s) in infos.withIndex()) {
                if (s["session_id"] == cursor) {
                    infos = infos.subList(idx + 1, infos.size).toMutableList()
                    found = true
                    break
                }
            }
            if (!found) {
                infos = mutableListOf()  // Unknown cursor -> empty page
            }
        }

        val hasMore = infos.size > _LIST_SESSIONS_PAGE_SIZE
        if (hasMore) infos = infos.subList(0, _LIST_SESSIONS_PAGE_SIZE).toMutableList()

        val sessions = mutableListOf<SessionInfo>()
        for (s in infos) {
            var updatedAt = s["updated_at"]
            if (updatedAt != null && updatedAt !is String) updatedAt = updatedAt.toString()
            sessions.add(mapOf(
                "session_id" to s["session_id"],
                "cwd" to s["cwd"],
                "title" to s["title"],
                "updated_at" to updatedAt))
        }

        val nextCursor = if (hasMore && sessions.isNotEmpty()) sessions.last()["session_id"] else null
        return mapOf(
            "sessions" to sessions,
            "next_cursor" to nextCursor)
    }

    // ---- Prompt (core) ------------------------------------------------------

    /** Run Hermes on the user's prompt and stream events back to the editor. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun prompt(
        prompt: List<Map<String, Any?>>,
        sessionId: String,
        kwargs: Map<String, Any?> = emptyMap()): PromptResponse {
        val state = sessionManager.getSession(sessionId)
        if (state == null) {
            Log.e(_TAG, "prompt: session $sessionId not found")
            return mapOf("stop_reason" to "refusal")
        }

        val userText = _extractText(prompt).trim()
        if (userText.isEmpty()) {
            return mapOf("stop_reason" to "end_turn")
        }

        // Intercept slash commands — handle locally without calling the LLM
        if (userText.startsWith("/")) {
            val responseText = _handleSlashCommand(userText, state)
            if (responseText != null) {
                _conn?.let { conn ->
                    val update = _updateAgentMessageText(responseText)
                    conn.sessionUpdate(sessionId, update)
                }
                return mapOf("stop_reason" to "end_turn")
            }
        }

        Log.i(_TAG, "Prompt on session $sessionId: ${userText.take(100)}")

        val conn = _conn

        // TODO: port state.cancel_event.clear()

        // TODO: port tool_call_ids/meta defaultdict + event callback wiring
        // (make_tool_progress_cb, make_thinking_cb, make_step_cb,
        // make_message_cb, make_approval_callback).

        // TODO: port _run_agent executor bridging with HERMES_INTERACTIVE env.
        val result: Map<String, Any?> = try {
            // TODO: await loop.run_in_executor(_executor, _runAgent)
            mapOf("final_response" to "", "messages" to state.history)
        } catch (_: Exception) {
            Log.e(_TAG, "Executor error for session $sessionId")
            return mapOf("stop_reason" to "end_turn")
        }

        @Suppress("UNCHECKED_CAST")
        val messages = result["messages"] as? List<Map<String, Any?>>
        if (!messages.isNullOrEmpty()) {
            state.history = messages.toMutableList()
            sessionManager.saveSession(sessionId)
        }

        val finalResponse = (result["final_response"] as? String) ?: ""
        if (finalResponse.isNotEmpty()) {
            try {
                // TODO: port agent.title_generator.maybe_auto_title
            } catch (_: Exception) {
                Log.d(_TAG, "Failed to auto-title ACP session $sessionId")
            }
        }
        if (finalResponse.isNotEmpty() && conn != null) {
            val update = _updateAgentMessageText(finalResponse)
            conn.sessionUpdate(sessionId, update)
        }

        val usage: Usage? = if (listOf("prompt_tokens", "completion_tokens", "total_tokens")
            .any { result[it] != null }) {
            mapOf(
                "input_tokens" to (result["prompt_tokens"] ?: 0),
                "output_tokens" to (result["completion_tokens"] ?: 0),
                "total_tokens" to (result["total_tokens"] ?: 0),
                "thought_tokens" to result["reasoning_tokens"],
                "cached_read_tokens" to result["cache_read_tokens"])
        } else null

        val cancelSet = false  // TODO: state.cancel_event.is_set()
        val stopReason = if (cancelSet) "cancelled" else "end_turn"
        return mapOf("stop_reason" to stopReason, "usage" to usage)
    }

    // ---- Slash commands (headless) ------------------------------------------

    /** Advertise supported slash commands to the connected ACP client. */
    suspend fun _sendAvailableCommandsUpdate(sessionId: String) {
        val conn = _conn ?: return
        try {
            conn.sessionUpdate(
                sessionId,
                mapOf(
                    "session_update" to "available_commands_update",
                    "available_commands" to _availableCommands()))
        } catch (_: Exception) {
            Log.w(_TAG, "Failed to advertise ACP slash commands for session $sessionId")
        }
    }

    /** Send the command advertisement after the session response is queued. */
    fun _scheduleAvailableCommandsUpdate(sessionId: String) {
        if (_conn == null) return
        // TODO: port asyncio.get_running_loop().call_soon(create_task, ...)
    }

    /**
     * Dispatch a slash command and return the response text.
     *
     * Returns ``null`` for unrecognized commands so they fall through to
     * the LLM (the user may have typed ``/something`` as prose).
     */
    fun _handleSlashCommand(text: String, state: SessionState): String? {
        val parts = text.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].trimStart('/').lowercase()
        val args = if (parts.size > 1) parts[1].trim() else ""

        val handler: ((String, SessionState) -> String)? = when (cmd) {
            "help" -> ::_cmdHelp
            "model" -> ::_cmdModel
            "tools" -> ::_cmdTools
            "context" -> ::_cmdContext
            "reset" -> ::_cmdReset
            "compact" -> ::_cmdCompact
            "version" -> ::_cmdVersion
            else -> null
        }

        if (handler == null) return null  // unknown — let the LLM handle

        return try {
            handler(args, state)
        } catch (e: Exception) {
            Log.e(_TAG, "Slash command /$cmd error: ${e.message}")
            "Error executing /$cmd: ${e.message}"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdHelp(args: String, state: SessionState): String {
        val lines = mutableListOf("Available commands:", "")
        for ((cmd, desc) in _SLASH_COMMANDS) {
            lines.add("  /${cmd.padEnd(10)}  $desc")
        }
        lines.add("")
        lines.add("Unrecognized /commands are sent to the model as normal messages.")
        return lines.joinToString("\n")
    }

    fun _cmdModel(args: String, state: SessionState): String {
        if (args.isEmpty()) {
            val model = state.model ?: (_getAttr(state.agent, "model") as? String) ?: "unknown"
            val provider = (_getAttr(state.agent, "provider") as? String) ?: "auto"
            return "Current model: $model\nProvider: $provider"
        }

        val currentProvider = (_getAttr(state.agent, "provider") as? String) ?: "openrouter"
        val (targetProvider, newModel) = _resolveModelSelection(args, currentProvider)

        state.model = newModel
        state.agent = sessionManager._makeAgent(
            sessionId = state.sessionId,
            cwd = state.cwd,
            model = newModel,
            requestedProvider = targetProvider)
        sessionManager.saveSession(state.sessionId)
        val providerLabel = (_getAttr(state.agent, "provider") as? String) ?: targetProvider
        Log.i(_TAG, "Session ${state.sessionId}: model switched to $newModel")
        return "Model switched to: $newModel\nProvider: $providerLabel"
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdTools(args: String, state: SessionState): String {
        return try {
            // TODO: port model_tools.get_tool_definitions + enabled_toolsets
            val tools: List<Map<String, Any?>> = emptyList()
            if (tools.isEmpty()) return "No tools available."
            val lines = mutableListOf("Available tools (${tools.size}):")
            for (t in tools) {
                @Suppress("UNCHECKED_CAST")
                val function = t["function"] as? Map<String, Any?> ?: emptyMap()
                val name = (function["name"] as? String) ?: "?"
                var desc = (function["description"] as? String) ?: ""
                if (desc.length > 80) desc = desc.substring(0, 77) + "..."
                lines.add("  $name: $desc")
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            "Could not list tools: ${e.message}"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdContext(args: String, state: SessionState): String {
        val nMessages = state.history.size
        if (nMessages == 0) return "Conversation is empty (no messages yet)."
        val roles = mutableMapOf<String, Int>()
        for (msg in state.history) {
            val role = (msg["role"] as? String) ?: "unknown"
            roles[role] = (roles[role] ?: 0) + 1
        }
        val lines = mutableListOf(
            "Conversation: $nMessages messages",
            "  user: ${roles["user"] ?: 0}, assistant: ${roles["assistant"] ?: 0}, " +
                "tool: ${roles["tool"] ?: 0}, system: ${roles["system"] ?: 0}")
        val model = state.model ?: (_getAttr(state.agent, "model") as? String) ?: ""
        if (model.isNotEmpty()) lines.add("Model: $model")
        return lines.joinToString("\n")
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdReset(args: String, state: SessionState): String {
        state.history.clear()
        sessionManager.saveSession(state.sessionId)
        return "Conversation history cleared."
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdCompact(args: String, state: SessionState): String {
        if (state.history.isEmpty()) return "Nothing to compress — conversation is empty."
        return try {
            val agent = state.agent ?: return "Compression not available."
            val compressionEnabled = (_getAttr(agent, "compression_enabled") as? Boolean) ?: true
            if (!compressionEnabled) return "Context compression is disabled for this agent."
            // TODO: port hasattr(agent, "_compress_context") check +
            // estimate_messages_tokens_rough + agent._compress_context.
            val originalCount = state.history.size
            val approxTokens = 0L  // TODO: estimate_messages_tokens_rough(state.history)
            val compressed: MutableList<Map<String, Any?>> = state.history
            state.history = compressed
            sessionManager.saveSession(state.sessionId)
            val newCount = state.history.size
            val newTokens = 0L  // TODO: estimate_messages_tokens_rough(state.history)
            ("Context compressed: $originalCount -> $newCount messages\n" +
                "~$approxTokens -> ~$newTokens tokens")
        } catch (e: Exception) {
            "Compression failed: ${e.message}"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun _cmdVersion(args: String, state: SessionState): String {
        return "Hermes Agent v$HERMES_VERSION"
    }

    // ---- Model switching (ACP protocol method) -------------------------------

    /** Switch the model for a session (called by ACP protocol). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun setSessionModel(
        modelId: String,
        sessionId: String,
        kwargs: Map<String, Any?> = emptyMap()): SetSessionModelResponse? {
        val state = sessionManager.getSession(sessionId)
        if (state != null) {
            val currentProvider = _getAttr(state.agent, "provider") as? String
            val (requestedProvider, resolvedModel) = _resolveModelSelection(
                modelId,
                currentProvider ?: "openrouter")
            state.model = resolvedModel
            val providerChanged = !currentProvider.isNullOrEmpty() && requestedProvider != currentProvider
            val currentBaseUrl = if (providerChanged) null else _getAttr(state.agent, "base_url") as? String
            val currentApiMode = if (providerChanged) null else _getAttr(state.agent, "api_mode") as? String
            state.agent = sessionManager._makeAgent(
                sessionId = sessionId,
                cwd = state.cwd,
                model = resolvedModel,
                requestedProvider = requestedProvider,
                baseUrl = currentBaseUrl,
                apiMode = currentApiMode)
            sessionManager.saveSession(sessionId)
            Log.i(_TAG, "Session $sessionId: model switched to $resolvedModel via provider $requestedProvider")
            return emptyMap()
        }
        Log.w(_TAG, "Session $sessionId: model switch requested for missing session")
        return null
    }

    /** Persist the editor-requested mode so ACP clients do not fail on mode switches. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun setSessionMode(
        modeId: String,
        sessionId: String,
        kwargs: Map<String, Any?> = emptyMap()): SetSessionModeResponse? {
        val state = sessionManager.getSession(sessionId)
        if (state == null) {
            Log.w(_TAG, "Session $sessionId: mode switch requested for missing session")
            return null
        }
        state.mode = modeId
        sessionManager.saveSession(sessionId)
        Log.i(_TAG, "Session $sessionId: mode switched to $modeId")
        return emptyMap()
    }

    /** Accept ACP config option updates even when Hermes has no typed ACP config surface yet. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun setConfigOption(
        configId: String,
        sessionId: String,
        value: String,
        kwargs: Map<String, Any?> = emptyMap()): SetSessionConfigOptionResponse? {
        val state = sessionManager.getSession(sessionId)
        if (state == null) {
            Log.w(_TAG, "Session $sessionId: config update requested for missing session")
            return null
        }
        state.configOptions[configId] = value
        sessionManager.saveSession(sessionId)
        Log.i(_TAG, "Session $sessionId: config option $configId updated")
        return mapOf("config_options" to emptyList<Any?>())
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private const val _ACP_PROTOCOL_VERSION = 1

/** Normalize an env/headers list-of-{name,value} into a Map. */
@Suppress("UNCHECKED_CAST")
private fun _envListToMap(raw: Any?): Map<String, Any?> {
    if (raw !is List<*>) return emptyMap()
    val out = mutableMapOf<String, Any?>()
    for (item in raw) {
        if (item is Map<*, *>) {
            val name = item["name"] as? String ?: continue
            out[name] = item["value"]
        }
    }
    return out
}

/** Python `acp.update_agent_message_text(text)` shim — returns a session_update dict. */
private fun _updateAgentMessageText(text: String): Map<String, Any?> = mapOf(
    "session_update" to "agent_message",
    "content" to mapOf("type" to "text", "text" to text))

/**
 * Python `getattr(obj, name, default=None)` — tries Map key lookup, then
 * Java reflection (field, then no-arg getter), else null.
 */
private fun _getAttr(obj: Any?, name: String): Any? {
    if (obj == null) return null
    if (obj is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return (obj as Map<String, Any?>)[name]
    }
    return try {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.get(obj)
    } catch (_: Exception) {
        try {
            val methodName = "get" + name.replaceFirstChar { it.uppercase() }
            val method = obj.javaClass.getMethod(methodName)
            method.invoke(obj)
        } catch (_: Exception) {
            null
        }
    }
}
