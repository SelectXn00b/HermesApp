/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/boot.ts, server-methods.ts
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.content.Context
import com.xiaomo.androidforclaw.infra.FixedWindowRateLimiter
import com.xiaomo.androidforclaw.process.resetAllLanes
import com.xiaomo.androidforclaw.routing.buildAgentMainSessionKey
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.gateway.methods.AgentMethods
import com.xiaomo.androidforclaw.gateway.methods.HealthMethods
import com.xiaomo.androidforclaw.gateway.methods.SessionMethods
import com.xiaomo.androidforclaw.gateway.methods.ModelsMethods
import com.xiaomo.androidforclaw.gateway.methods.ToolsMethods
import com.xiaomo.androidforclaw.gateway.methods.SkillsMethods
import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.gateway.methods.TalkMethods
import com.xiaomo.androidforclaw.gateway.methods.CronMethods
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.hooks.loadInternalHooks
import com.xiaomo.androidforclaw.plugins.PluginLoader
import com.xiaomo.androidforclaw.plugins.PluginRegistry
import com.xiaomo.androidforclaw.plugins.PluginRecordStatus
import com.xiaomo.androidforclaw.workspace.StoragePaths
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
import com.xiaomo.androidforclaw.util.SPHelper
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

    // ContextBuilder for full system prompt (SOUL.md, AGENTS.md, skills, etc.)
    private val contextBuilder: ContextBuilder by lazy {
        ContextBuilder(
            context = context,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry
        )
    }
    private companion object {
        private const val PREF_THINKING_LEVEL = "chat_thinking_level"
    }
    private var server: GatewayWebSocketServer? = null
    private var tokenAuth: TokenAuth? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Per-session rate limiter: max 10 requests per 60 seconds (infra.FixedWindowRateLimiter)
    private val sessionRateLimiters = ConcurrentHashMap<String, FixedWindowRateLimiter>()

    // Active agent runs: runId -> coroutine Job (for abort support)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    // Per-session AgentLoop instances (so sessions don't share shouldStop flag)
    private val sessionAgentLoops = ConcurrentHashMap<String, AgentLoop>()
    // Map runId -> sessionKey for abort routing
    private val runToSession = ConcurrentHashMap<String, String>()

    private lateinit var agentMethods: AgentMethods
    private lateinit var sessionMethods: SessionMethods
    private lateinit var healthMethods: HealthMethods
    private lateinit var modelsMethods: ModelsMethods
    private lateinit var toolsMethods: ToolsMethods
    private lateinit var skillsMethods: SkillsMethods
    private lateinit var configMethods: ConfigMethods
    private lateinit var talkMethods: TalkMethods

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
            // Reset process command lanes on gateway (re)start
            resetAllLanes()

            // Initialize plugins (PluginLoader) and hooks (HookLoader)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val pluginSnapshot = PluginLoader.loadPlugins()
                    Log.i(TAG, "Loaded ${pluginSnapshot.plugins.size} plugins at gateway startup")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load plugins at gateway startup", e)
                }
                try {
                    val cfg = ConfigLoader(context).loadOpenClawConfig()
                    val hookCount = loadInternalHooks(
                        cfg = cfg,
                        workspaceDir = StoragePaths.workspace.absolutePath
                    )
                    Log.i(TAG, "Loaded $hookCount internal hooks at gateway startup")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load hooks at gateway startup", e)
                }
            }

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
                talkMethods = TalkMethods.getInstance(context)
                talkMethods.init()

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
                    val sessionKey = p["sessionKey"] as? String
                        ?: buildAgentMainSessionKey("main")
                    val userMsg = p["message"] as? String ?: ""

                    // Per-session rate limit check (infra.FixedWindowRateLimiter)
                    val rateLimiter = sessionRateLimiters.getOrPut(sessionKey) {
                        FixedWindowRateLimiter(maxRequests = 10, windowMs = 60_000L)
                    }
                    val rateLimitResult = rateLimiter.consume()
                    if (!rateLimitResult.allowed) {
                        return@registerMethod mapOf(
                            "error" to "rate_limited",
                            "retryAfterMs" to rateLimitResult.retryAfterMs
                        )
                    }
                    val thinking = p["thinking"] as? String ?: "off"
                    SPHelper.getInstance(context).saveData(PREF_THINKING_LEVEL, thinking)
                    val reasoningEnabled = thinking != "off"
                    @Suppress("UNCHECKED_CAST")
                    val attachments = p["attachments"] as? List<Map<String, Any?>> ?: emptyList()
                    val runId = "run_${UUID.randomUUID()}"

                    // Extract and sanitize images from attachments
                    // Aligned with OpenClaw image-sanitization.ts: max 1200px, 5MB
                    val imageBlocks = mutableListOf<com.xiaomo.androidforclaw.providers.llm.ImageBlock>()
                    for (att in attachments) {
                        val type = att["type"] as? String ?: continue
                        if (type == "image" || type == "image_url") {
                            // Anthropic format: { type: "image", source: { data, media_type } }
                            val source = att["source"] as? Map<*, *>
                            val rawBase64 = source?.get("data") as? String
                            val mimeType = source?.get("media_type") as? String ?: "image/jpeg"
                            if (!rawBase64.isNullOrBlank()) {
                                val sanitized = com.xiaomo.androidforclaw.media.ImageSanitizer.sanitize(
                                    base64Data = rawBase64,
                                    sourceMimeType = mimeType
                                )
                                if (sanitized != null) {
                                    imageBlocks.add(com.xiaomo.androidforclaw.providers.llm.ImageBlock(
                                        base64 = sanitized.base64,
                                        mimeType = sanitized.mimeType
                                    ))
                                    Log.i(TAG, "📷 Image sanitized: ${sanitized.originalBytes}→${sanitized.sanitizedBytes} bytes, resized=${sanitized.resized}")
                                }
                            }
                            // OpenAI format: { type: "image_url", image_url: { url: "data:...;base64,..." } }
                            val imageUrl = att["image_url"] as? Map<*, *>
                            val url = imageUrl?.get("url") as? String
                            if (url != null && url.startsWith("data:")) {
                                val parts = url.removePrefix("data:").split(";base64,", limit = 2)
                                if (parts.size == 2) {
                                    val sanitized = com.xiaomo.androidforclaw.media.ImageSanitizer.sanitize(
                                        base64Data = parts[1],
                                        sourceMimeType = parts[0]
                                    )
                                    if (sanitized != null) {
                                        imageBlocks.add(com.xiaomo.androidforclaw.providers.llm.ImageBlock(
                                            base64 = sanitized.base64,
                                            mimeType = sanitized.mimeType
                                        ))
                                        Log.i(TAG, "📷 Image sanitized (URL): ${sanitized.originalBytes}→${sanitized.sanitizedBytes} bytes")
                                    }
                                }
                            }
                        }
                    }

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

                    // Cancel previous run for the SAME session only
                    // Find runIds belonging to this session and cancel them
                    runToSession.entries.filter { it.value == sessionKey }.forEach { (oldRunId, _) ->
                        Log.w(TAG, "🛑 [chat.send] Cancelling previous run $oldRunId for session $sessionKey")
                        sessionAgentLoops[sessionKey]?.stop()
                        activeJobs[oldRunId]?.cancel()
                        activeJobs.remove(oldRunId)
                        runToSession.remove(oldRunId)
                    }

                    // Create a per-session AgentLoop so sessions don't share shouldStop flag
                    val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(context)
                    val perSessionLoop = AgentLoop(
                        llmProvider = llmProvider,
                        toolRegistry = toolRegistry,
                        androidToolRegistry = androidToolRegistry
                    )
                    // Copy extra tools from the shared agentLoop if any
                    perSessionLoop.extraTools = agentLoop.extraTools
                    sessionAgentLoops[sessionKey] = perSessionLoop
                    runToSession[runId] = sessionKey

                    val job = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        // Track tool call IDs for correlating start/result pairs
                        val pendingToolCallIds = ConcurrentHashMap<String, String>()

                        // Collect streaming progress events in parallel
                        val streamJob = launch {
                            perSessionLoop.progressFlow.collect { update ->
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
                            val systemPrompt = contextBuilder.buildSystemPrompt(
                                userGoal = userMsg,
                                packageName = "",
                                testMode = "exploration"
                            )
                            Log.d(TAG, "✅ Gateway system prompt built (${systemPrompt.length} chars)")

                            val result = perSessionLoop.run(
                                systemPrompt = systemPrompt,
                                userMessage = userMsg,
                                contextHistory = contextHistory,
                                reasoningEnabled = reasoningEnabled,
                                images = imageBlocks.ifEmpty { null }
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
                            runToSession.remove(runId)
                            sessionAgentLoops.remove(sessionKey)
                        }
                    }
                    activeJobs[runId] = job

                    mapOf("runId" to runId)
                }

                // chat.history: return session message history in OpenClaw format.
                registerMethod("chat.history") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val sessionKey = p["sessionKey"] as? String
                        ?: buildAgentMainSessionKey("main")
                    val session = sessionManager.get(sessionKey)
                    val messageList = session?.messages?.mapIndexed { idx, msg ->
                        val ts = session.messageTimestamps.getOrElse(idx) { System.currentTimeMillis() }
                        mapOf(
                            "role" to msg.role,
                            "content" to legacyContentToOpenClaw(msg.content),
                            "timestamp" to ts
                        )
                    } ?: emptyList()
                    val savedThinking = SPHelper.getInstance(context)
                        .getData(PREF_THINKING_LEVEL, "off")
                        ?.takeIf { it.isNotBlank() } ?: "off"
                    mapOf(
                        "sessionKey" to sessionKey,
                        "sessionId" to session?.sessionId,
                        "thinkingLevel" to savedThinking,
                        "messages" to messageList
                    )
                }

                // chat.setThinkingLevel: persist the thinking level immediately on change.
                registerMethod("chat.setThinkingLevel") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val level = p["level"] as? String ?: "off"
                    SPHelper.getInstance(context).saveData(PREF_THINKING_LEVEL, level)
                    mapOf("ok" to true)
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
                        val sk = runToSession[runId]
                        if (sk != null) sessionAgentLoops[sk]?.stop()
                        activeJobs[runId]?.cancel()
                        activeJobs.remove(runId)
                        runToSession.remove(runId)
                        Log.i(TAG, "Aborted run: $runId")
                    } else {
                        // Abort all active runs
                        sessionAgentLoops.values.forEach { it.stop() }
                        sessionAgentLoops.clear()
                        activeJobs.values.forEach { it.cancel() }
                        activeJobs.clear()
                        runToSession.clear()
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

                // ── Plugin RPC methods ────────────────────────────────
                registerMethod("plugins.list") { _ ->
                    val snapshot = PluginRegistry.requireActive()
                    mapOf("plugins" to snapshot.plugins.map { plugin ->
                        mapOf(
                            "id" to plugin.id,
                            "name" to plugin.name,
                            "status" to plugin.status.value,
                            "origin" to plugin.origin.value,
                            "channels" to plugin.channels,
                            "providers" to plugin.providers,
                        )
                    })
                }

                registerMethod("plugins.enable") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val pluginId = p["pluginId"] as? String
                        ?: throw IllegalArgumentException("pluginId required")
                    // Re-load plugins to pick up activation changes
                    val snapshot = PluginLoader.loadPlugins()
                    val found = snapshot.plugins.any { it.id == pluginId }
                    mapOf("ok" to found, "pluginId" to pluginId)
                }

                registerMethod("plugins.disable") { params ->
                    @Suppress("UNCHECKED_CAST")
                    val p = params as? Map<String, Any?> ?: emptyMap()
                    val pluginId = p["pluginId"] as? String
                        ?: throw IllegalArgumentException("pluginId required")
                    val info = PluginRegistry.get(pluginId)
                    if (info != null) {
                        PluginRegistry.unregister(pluginId)
                    }
                    mapOf("ok" to (info != null), "pluginId" to pluginId)
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

                // ── Talk (TTS) methods ───────────────────────────────────
                registerMethod("talk.config") { params ->
                    talkMethods.talkConfig(params)
                }
                registerMethod("talk.speak") { params ->
                    talkMethods.talkSpeak(params)
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
            if (::talkMethods.isInitialized) talkMethods.shutdown()
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
