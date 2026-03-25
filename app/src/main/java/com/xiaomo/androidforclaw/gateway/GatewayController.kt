/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/boot.ts, server-methods.ts
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.content.Context
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.gateway.methods.AgentMethods
import com.xiaomo.androidforclaw.gateway.methods.HealthMethods
import com.xiaomo.androidforclaw.gateway.methods.SessionMethods
import com.xiaomo.androidforclaw.gateway.methods.ModelsMethods
import com.xiaomo.androidforclaw.gateway.methods.ToolsMethods
import com.xiaomo.androidforclaw.gateway.methods.SkillsMethods
import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.gateway.methods.CronMethods
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.gateway.protocol.AgentParams
import com.xiaomo.androidforclaw.gateway.protocol.AgentWaitParams
import com.xiaomo.androidforclaw.gateway.protocol.EventFrame
import com.xiaomo.androidforclaw.gateway.security.TokenAuth
import com.xiaomo.androidforclaw.gateway.websocket.GatewayWebSocketServer
import fi.iki.elonen.NanoHTTPD
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.llm.toNewMessage
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.xiaomo.androidforclaw.logging.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Main Gateway controller that integrates all components:
 * - WebSocket RPC server (Protocol v3)
 * - Agent methods
 * - Session methods
 * - Health methods
 * - Token authentication
 *
 * Aligned with OpenClaw Gateway architecture
 */
class GatewayController(
    private val context: Context,
    private val agentLoop: AgentLoop,
    private val sessionManager: SessionManager,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    private val skillsLoader: SkillsLoader,
    private val port: Int = 8765,
    private val authToken: String? = null
) {
    private val TAG = "GatewayController"
    private var server: GatewayWebSocketServer? = null
    private var tokenAuth: TokenAuth? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Active agent runs: runId -> coroutine Job (for abort support)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private lateinit var agentMethods: AgentMethods
    private lateinit var sessionMethods: SessionMethods
    private lateinit var healthMethods: HealthMethods
    private lateinit var modelsMethods: ModelsMethods
    private lateinit var toolsMethods: ToolsMethods
    private lateinit var skillsMethods: SkillsMethods
    private lateinit var configMethods: ConfigMethods

    var isRunning = false
        private set

    /** 本地进程内事件接收器，由 LocalGatewayChannel 注册，绕过 WebSocket 直接收取事件。 */
    @Volatile var localEventSink: ((event: String, payloadJson: String) -> Unit)? = null

    /** 广播事件：同时发给 WebSocket 客户端和本地 channel。 */
    private fun broadcastEvent(frame: EventFrame) {
        server?.broadcast(frame)
        localEventSink?.let { sink ->
            try {
                val payloadJson = com.google.gson.Gson().toJson(frame.payload)
                sink(frame.event, payloadJson)
            } catch (_: Throwable) { /* 序列化失败忽略 */ }
        }
    }

    /**
     * Start the Gateway WebSocket server
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG,"Gateway already running")
            return
        }

        try {
            // Initialize token auth if configured
            if (authToken != null) {
                tokenAuth = TokenAuth(authToken)
                Log.i(TAG,"Token authentication enabled")
            } else {
                Log.w(TAG,"Token authentication disabled - running in insecure mode")
            }

            // Create WebSocket server
            server = GatewayWebSocketServer(
                context = context,
                port = port,
                tokenAuth = tokenAuth
            ).apply {
                // Initialize method handlers
                agentMethods = AgentMethods(context, agentLoop, sessionManager, this, activeJobs)
                sessionMethods = SessionMethods(sessionManager)
                healthMethods = HealthMethods()
                modelsMethods = ModelsMethods(context)
                toolsMethods = ToolsMethods(toolRegistry, androidToolRegistry)
                skillsMethods = SkillsMethods(context)
                configMethods = ConfigMethods(context)

                // ── OpenClaw loopback handshake ───────────────────────────
                // Client (OpenClaw Android) sends "connect" after receiving
                // the "connect.challenge" event.  We respond with server info.
                registerMethod("connect") { _ ->
                    mapOf(
                        "server" to mapOf("host" to "AndroidForClaw"),
                        "auth" to mapOf("deviceToken" to null),
                        "canvasHostUrl" to null,
                        "snapshot" to mapOf(
                            "sessionDefaults" to mapOf("mainSessionKey" to "main")
                        )
                    )
                }

                // ── OpenClaw chat protocol ─────────────────────────────────
                // chat.send: run agent asynchronously, stream "agent" events,
                // finish with a "chat" final event.
                registerMethod("chat.send") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val sessionKey = p["sessionKey"] as? String ?: "default"
                    val userMsg = p["message"] as? String ?: ""
                    val thinking = p["thinking"] as? String ?: "off"
                    val reasoningEnabled = thinking != "off"
                    @Suppress("UNCHECKED_CAST")
                    val attachments = p["attachments"] as? List<Map<String, Any?>> ?: emptyList()
                    val runId = "run_${UUID.randomUUID()}"

                    // Build content as raw maps — lossless roundtrip through SessionManager
                    val textPart: Map<String, Any?> = mapOf("type" to "text", "text" to userMsg)
                    val userContent: Any = if (attachments.isEmpty()) {
                        userMsg
                    } else {
                        mutableListOf(textPart).apply { addAll(attachments) }
                    }

                    // Store user message via SessionManager
                    val session = sessionManager.getOrCreate(sessionKey)
                    session.addMessage(LegacyMessage(role = "user", content = userContent))
                    sessionManager.save(session)

                    // Build context history from session messages
                    val contextHistory = session.messages.dropLast(1).map { it.toNewMessage() }

                    // Cancel any previously active run to avoid single agentLoop contention
                    if (activeJobs.isNotEmpty()) {
                        Log.w(TAG, "🛑 [chat.send] Cancelling ${activeJobs.size} previous run(s)")
                        agentLoop.stop()
                        activeJobs.values.forEach { it.cancel() }
                        activeJobs.clear()
                    }

                    val job = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        // Track tool call IDs for correlating start/result pairs
                        val pendingToolCallIds = ConcurrentHashMap<String, String>()

                        // Collect streaming progress events in parallel
                        val streamJob = launch {
                            agentLoop.progressFlow.collect { update ->
                                when (update) {
                                    is ProgressUpdate.BlockReply -> {
                                        broadcastEvent(EventFrame(event = "agent", payload = mapOf(
                                            "sessionKey" to sessionKey,
                                            "stream" to "assistant",
                                            "data" to mapOf("text" to update.text)
                                        )))
                                    }
                                    is ProgressUpdate.ToolCall -> {
                                        val toolCallId = "tc_${UUID.randomUUID()}"
                                        pendingToolCallIds[update.name] = toolCallId
                                        broadcastEvent(EventFrame(event = "agent", payload = mapOf(
                                            "sessionKey" to sessionKey,
                                            "stream" to "tool",
                                            "data" to mapOf(
                                                "phase" to "start",
                                                "name" to update.name,
                                                "toolCallId" to toolCallId,
                                                "arguments" to update.arguments
                                            )
                                        )))
                                    }
                                    is ProgressUpdate.ToolResult -> {
                                        val toolCallId = pendingToolCallIds.remove(update.name) ?: "tc_${UUID.randomUUID()}"
                                        broadcastEvent(EventFrame(event = "agent", payload = mapOf(
                                            "sessionKey" to sessionKey,
                                            "stream" to "tool",
                                            "data" to mapOf(
                                                "phase" to "result",
                                                "name" to update.name,
                                                "toolCallId" to toolCallId,
                                                "result" to update.result
                                            )
                                        )))
                                    }
                                    else -> { /* ignore other progress types */ }
                                }
                            }
                        }

                        try {
                            val result = agentLoop.run(
                                systemPrompt = "You are a helpful AI assistant.",
                                userMessage = userMsg,
                                contextHistory = contextHistory,
                                reasoningEnabled = reasoningEnabled
                            )
                            streamJob.cancel()

                            val text = result.finalContent
                            val msgId = "msg_${UUID.randomUUID()}"
                            val nowMs = System.currentTimeMillis()

                            // Store assistant message via SessionManager
                            session.addMessage(LegacyMessage(role = "assistant", content = text))
                            sessionManager.save(session)

                            // Send final assistant text (full accumulated)
                            broadcastEvent(EventFrame(event = "agent", payload = mapOf(
                                "sessionKey" to sessionKey,
                                "stream" to "assistant",
                                "data" to mapOf("text" to text)
                            )))
                            // OpenClaw client expects "state" in chat events
                            broadcastEvent(EventFrame(event = "chat", payload = mapOf(
                                "state" to "final",
                                "sessionKey" to sessionKey,
                                "runId" to runId,
                                "message" to mapOf(
                                    "id" to msgId,
                                    "role" to "assistant",
                                    "content" to listOf(mapOf("type" to "text", "text" to text)),
                                    "timestamp" to nowMs
                                )
                            )))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            streamJob.cancel()
                            Log.i(TAG, "chat.send cancelled (abort): $runId")
                            broadcastEvent(EventFrame(event = "chat", payload = mapOf(
                                "state" to "aborted",
                                "sessionKey" to sessionKey,
                                "runId" to runId
                            )))
                        } catch (e: Exception) {
                            streamJob.cancel()
                            Log.e(TAG, "chat.send agent failed: ${e.message}", e)
                            val errorMsg = e.message ?: "error"
                            broadcastEvent(EventFrame(event = "agent", payload = mapOf(
                                "sessionKey" to sessionKey,
                                "stream" to "error",
                                "data" to mapOf("error" to errorMsg)
                            )))
                            broadcastEvent(EventFrame(event = "chat", payload = mapOf(
                                "state" to "error",
                                "sessionKey" to sessionKey,
                                "runId" to runId,
                                "errorMessage" to errorMsg
                            )))
                        } finally {
                            activeJobs.remove(runId)
                        }
                    }
                    activeJobs[runId] = job

                    mapOf("runId" to runId)
                }

                // chat.history: return session message history in OpenClaw format.
                registerMethod("chat.history") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val sessionKey = p["sessionKey"] as? String ?: "default"
                    val session = sessionManager.get(sessionKey)
                    val messageList = session?.messages?.mapIndexed { idx, msg ->
                        val ts = session.messageTimestamps.getOrElse(idx) { System.currentTimeMillis() }
                        mapOf(
                            "role" to msg.role,
                            "content" to legacyContentToOpenClaw(msg.content),
                            "timestamp" to ts
                        )
                    } ?: emptyList()
                    mapOf(
                        "sessionKey" to sessionKey,
                        "sessionId" to session?.sessionId,
                        "thinkingLevel" to null,
                        "messages" to messageList
                    )
                }

                // chat.health: returns current session health for the chat tab.
                registerMethod("chat.health") { _ ->
                    mapOf("ok" to true, "agentBusy" to false)
                }

                // chat.abort: cancel the running agent for the given runId.
                registerMethod("chat.abort") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val runId = p["runId"] as? String
                    if (runId != null) {
                        agentLoop.stop()
                        activeJobs[runId]?.cancel()
                        activeJobs.remove(runId)
                        Log.i(TAG, "Aborted run: $runId")
                    } else {
                        // Abort all active runs
                        agentLoop.stop()
                        activeJobs.values.forEach { it.cancel() }
                        activeJobs.clear()
                        Log.i(TAG, "Aborted all active runs")
                    }
                    mapOf("aborted" to true)
                }

                // agents.list: list available agents (AndroidForClaw only has one).
                registerMethod("agents.list") { _ ->
                    mapOf("agents" to listOf(
                        mapOf(
                            "id" to "androidforclaw",
                            "name" to "AndroidForClaw",
                            "description" to "AI Agent for Android"
                        )
                    ))
                }

                // Register Agent methods
                registerMethod("agent") { params ->
                    val agentParams = parseAgentParams(params)
                    agentMethods.agent(agentParams)
                }

                registerMethod("agent.wait") { params ->
                    val waitParams = parseAgentWaitParams(params)
                    agentMethods.agentWait(waitParams)
                }

                // OpenClaw uses "agent.identity.get" not "agent.identity"
                registerMethod("agent.identity.get") { _ ->
                    agentMethods.agentIdentity()
                }

                // Register Session methods
                registerMethod("sessions.list") { params ->
                    sessionMethods.sessionsList(params)
                }

                registerMethod("sessions.preview") { params ->
                    sessionMethods.sessionsPreview(params)
                }

                registerMethod("sessions.reset") { params ->
                    sessionMethods.sessionsReset(params)
                }

                registerMethod("sessions.delete") { params ->
                    sessionMethods.sessionsDelete(params)
                }

                registerMethod("sessions.patch") { params ->
                    sessionMethods.sessionsPatch(params)
                }

                // Register Health methods
                registerMethod("health") { _ ->
                    healthMethods.health()
                }

                registerMethod("status") { _ ->
                    healthMethods.status()
                }

                // Register Models methods
                registerMethod("models.list") { _ ->
                    modelsMethods.modelsList()
                }

                // Register Tools methods
                registerMethod("tools.catalog") { _ ->
                    toolsMethods.toolsCatalog()
                }

                registerMethod("tools.list") { _ ->
                    toolsMethods.toolsList()
                }

                // Register Skills methods
                registerMethod("skills.status") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.status(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.bins") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.bins(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.reload") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.reload(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.install") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.install(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.update") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.update(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.search") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.search(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.uninstall") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.uninstall(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                // Register Config methods
                registerMethod("config.get") { params ->
                    configMethods.configGet(params)
                }

                registerMethod("config.set") { params ->
                    configMethods.configSet(params)
                }

                registerMethod("config.reload") { _ ->
                    configMethods.configReload()
                }

                // Register Cron methods (OpenClaw alignment)
                registerMethod("cron.list") { params ->
                    CronMethods.list(params as JSONObject)
                }

                registerMethod("cron.status") { params ->
                    CronMethods.status(params as JSONObject)
                }

                registerMethod("cron.add") { params ->
                    CronMethods.add(params as JSONObject)
                }

                registerMethod("cron.update") { params ->
                    CronMethods.update(params as JSONObject)
                }

                registerMethod("cron.remove") { params ->
                    CronMethods.remove(params as JSONObject)
                }

                registerMethod("cron.run") { params ->
                    CronMethods.run(params as JSONObject)
                }

                registerMethod("cron.runs") { params ->
                    CronMethods.runs(params as JSONObject)
                }

                Log.i(TAG,"Registered ${getMethodCount()} RPC methods")
            }

            // Start server in background
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Use 60 second timeout for slow operations (like ClawHub API calls)
                    // NanoHTTPD.SOCKET_READ_TIMEOUT is 5000ms by default, too short
                    server?.start(60000, false)  // 60 seconds
                    isRunning = true
                    Log.i(TAG,"Gateway WebSocket server started on port $port with 60s timeout")
                    Log.i(TAG,"Access UI at http://localhost:$port/")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start Gateway server", e)
                    isRunning = false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gateway", e)
            throw e
        }
    }

    /**
     * Stop the Gateway WebSocket server
     */
    fun stop() {
        if (!isRunning) {
            Log.w(TAG,"Gateway not running")
            return
        }

        try {
            server?.stop()
            server = null
            isRunning = false
            Log.i(TAG, "Gateway WebSocket server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Gateway", e)
        }
    }

    /**
     * Generate a new authentication token
     */
    fun generateToken(label: String = "generated", ttlMs: Long? = null): String? {
        return tokenAuth?.generateToken(label, ttlMs)
    }

    /**
     * Revoke an authentication token
     */
    fun revokeToken(token: String): Boolean {
        return tokenAuth?.revokeToken(token) ?: false
    }

    /**
     * Get server info
     */
    fun getInfo(): Map<String, Any> {
        return mapOf(
            "running" to isRunning,
            "port" to port,
            "authenticated" to (tokenAuth != null),
            "connections" to (server?.getActiveConnections() ?: 0),
            "url" to "ws://localhost:$port"
        )
    }

    // Helper methods to parse params
    // OpenClaw Protocol v3: params is Any? (can be Map, List, primitive, etc.)

    /**
     * Convert LegacyMessage.content (String or List<ContentBlock>) to
     * the OpenClaw format: List<Map<type, text?>>
     * Client parseHistory expects a JsonArray of content parts.
     */
    @Suppress("UNCHECKED_CAST")
    private fun legacyContentToOpenClaw(content: Any?): List<Map<String, Any?>> {
        return when (content) {
            is String -> listOf(mapOf("type" to "text", "text" to content))
            is List<*> -> content.mapNotNull { block ->
                when (block) {
                    is com.xiaomo.androidforclaw.providers.ContentBlock -> when (block.type) {
                        "text" -> mapOf("type" to "text", "text" to (block.text ?: ""))
                        "image_url" -> {
                            val dataUrl = block.imageUrl?.url ?: ""
                            // "data:image/png;base64,..." → extract mimeType
                            val mimeType = dataUrl.removePrefix("data:").substringBefore(";")
                            mapOf(
                                "type" to "image_url",
                                "mimeType" to mimeType.ifEmpty { "image/jpeg" },
                                "content" to dataUrl.substringAfter("base64,")
                            )
                        }
                        else -> null
                    }
                    is Map<*, *> -> (block as? Map<String, Any?>)  // raw map (attachment / post-JSONL-load)
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAgentParams(params: Any?): AgentParams {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object for agent method")

        return AgentParams(
            sessionKey = paramsMap["sessionKey"] as? String
                ?: throw IllegalArgumentException("sessionKey required"),
            message = paramsMap["message"] as? String
                ?: throw IllegalArgumentException("message required"),
            thinking = paramsMap["thinking"] as? String,
            model = paramsMap["model"] as? String
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAgentWaitParams(params: Any?): AgentWaitParams {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object for agent.wait method")

        return AgentWaitParams(
            runId = paramsMap["runId"] as? String
                ?: throw IllegalArgumentException("runId required"),
            timeout = (paramsMap["timeout"] as? Number)?.toLong()
        )
    }

    /**
     * 本地进程内直接调用 RPC 方法（绕过 WebSocket），返回 JSON 字符串。
     * 供 LocalGatewayChannel 调用。
     */
    suspend fun handleLocalRequest(method: String, paramsJson: String?): String {
        val srv = server ?: throw IllegalStateException("Gateway not started")
        val result = srv.handleLocalRequest(method, paramsJson)
        return com.google.gson.Gson().toJson(result)
    }
}
