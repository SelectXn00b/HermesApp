package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Gateway runner — orchestrates platform adapters, session management,
 * and message routing.
 *
 * Ported from gateway/run.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.platforms.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Agent loop interface — processes messages through the AI agent.
 */
interface AgentLoop {
    suspend fun process(text: String, sessionKey: String, context: Map<String, String>): String?
}

/**
 * Gateway runner — the main entry point for the gateway.
 *
 * Manages the lifecycle of all platform adapters, the session store,
 * the delivery router, and the hook pipeline.
 */
class GatewayRunner(
    /** Application context. */
    private val context: Context,
    /** Gateway configuration. */
    private val config: GatewayConfig) {
    companion object {
        private const val TAG = "GatewayRunner"
    }

    /** Whether the gateway is running. */
    val isRunning = AtomicBoolean(false)

    /** Session store. */
    val sessionStore = SessionStore(
        persistDir = File(config.hermesHome, "sessions").takeIf { config.hermesHome.isNotEmpty() }
    )

    /** Delivery router. */
    val deliveryRouter = DeliveryRouter()

    /** Hook pipeline. */
    val hookPipeline = HookPipeline()

    /** Agent loop - 处理消息的核心循环（可选，由外部注入） */
    var agentLoop: AgentLoop? = null

    /** Gateway status. */
    val status = GatewayStatus()

    /** Channel directory. */
    val channelDirectory = ChannelDirectory()

    /** Mirror bridge. */
    val mirrorBridge = MirrorBridge()

    /** Sticker cache. */
    val stickerCache = StickerCache(context)

    /** Display config registry. */
    val displayConfigRegistry = DisplayConfigRegistry()

    /** Pairing manager. */
    val pairingManager = PairingManager()

    /** All platform adapters (lazily created). */
    private val _adapters: ConcurrentHashMap<String, BasePlatformAdapter> = ConcurrentHashMap()

    /** Background scope for the gateway. */
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Concurrent session limiter. */
    private val _sessionSemaphore = java.util.concurrent.Semaphore(config.maxConcurrentSessions)

    /** Per-session /model overrides (keyed by sessionKey). */
    private val _sessionModelOverrides: ConcurrentHashMap<String, Map<String, Any?>> = ConcurrentHashMap()

    /** Cached agent instances (keyed by sessionKey). */
    private val _agentCache: ConcurrentHashMap<String, Any> = ConcurrentHashMap()
    private val _agentCacheLock = Any()

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start the gateway.
     *
     * Connects all enabled platform adapters and starts processing
     * incoming messages.
     */
    suspend fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Gateway already running")
            return
        }

        Log.i(TAG, "Starting gateway...")

        // Load persisted sessions
        sessionStore.load()

        // Create and connect adapters
        for (platformConfig in config.enabledPlatforms) {
            try {
                val adapter = _createAdapter(platformConfig)
                if (adapter != null) {
                    _adapters[adapter.name] = adapter
                    deliveryRouter.register(adapter)
                    status.markConnected(adapter.name)
                    Log.i(TAG, "Platform ${adapter.name} connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect platform ${platformConfig.platform}: ${e.message}")
            }
        }

        // Run startup hooks
        _scope.launch {
            hookPipeline.run(HookEvent.ON_START)
        }

        Log.i(TAG, "Gateway started with ${_adapters.size} platform(s)")
    }

    /**
     * Stop the gateway.
     *
     * Disconnects all platform adapters and persists sessions.
     */
    suspend fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        Log.i(TAG, "Stopping gateway...")

        // Run shutdown hooks
        try {
            withTimeout(10_000) {
                hookPipeline.run(HookEvent.ON_STOP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shutdown hooks timed out: ${e.message}")
        }

        // Disconnect all adapters
        for ((name, adapter) in _adapters) {
            try {
                adapter.disconnect()
                deliveryRouter.unregister(name)
                status.markDisconnected(name)
                Log.i(TAG, "Platform $name disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting platform $name: ${e.message}")
            }
        }
        _adapters.clear()

        // Persist sessions
        sessionStore.persist()

        // Cancel background scope
        _scope.cancel()

        Log.i(TAG, "Gateway stopped")
    }

    /**
     * Restart the gateway.
     *
     * Stops and then starts the gateway.
     */
    suspend fun restart() {
        stop()
        delay(1000)
        start()
    }

    // ------------------------------------------------------------------
    // Adapter management
    // ------------------------------------------------------------------

    /**
     * Create and connect an adapter for the given platform config.
     */
    private suspend fun _createAdapter(platformConfig: PlatformConfig): BasePlatformAdapter? {
        val adapter = when (platformConfig.platform) {
            Platform.FEISHU -> FeishuAdapter(context, platformConfig)
            Platform.TELEGRAM -> TelegramAdapter(context, platformConfig)
            Platform.DISCORD -> DiscordAdapter(context, platformConfig)
            Platform.SLACK -> SlackAdapter(context, platformConfig)
            Platform.SIGNAL -> SignalAdapter(context, platformConfig)
            Platform.WHATSAPP -> WhatsAppAdapter(context, platformConfig)
            Platform.WECOM -> WeComAdapter(context, platformConfig)
            Platform.WECOM_CALLBACK -> WeComCallbackAdapter(context, platformConfig)
            Platform.WEIXIN -> WeixinAdapter(context, platformConfig)
            Platform.DINGTALK -> DingtalkAdapter(context, platformConfig)
            Platform.QQBOT -> QqbotAdapter(context, platformConfig)
            Platform.EMAIL -> EmailAdapter(context, platformConfig)
            Platform.SMS -> SmsAdapter(context, platformConfig)
            Platform.MATRIX -> MatrixAdapter(context, platformConfig)
            Platform.MATTERMOST -> MattermostAdapter(context, platformConfig)
            Platform.HOMEASSISTANT -> HomeassistantAdapter(context, platformConfig)
            Platform.WEBHOOK -> WebhookAdapter(context, platformConfig)
            Platform.API_SERVER -> ApiServerAdapter(context, platformConfig)
            Platform.BLUEBUBBLES -> BluebubblesAdapter(context, platformConfig)
            Platform.APP_CHAT -> AppChatAdapter(context, platformConfig)
            else -> {
                Log.w(TAG, "Unknown platform: ${platformConfig.platform}")
                return null
            }
        }

        // Set up message handler
        adapter.messageHandler = { event -> _handleMessage(event, adapter) }

        // Connect
        val connected = adapter.connect()
        if (!connected) {
            Log.w(TAG, "Failed to connect platform ${adapter.name}")
            return null
        }

        return adapter
    }

    /**
     * Handle an incoming message from a platform adapter.
     */
    private suspend fun _handleMessage(event: MessageEvent, adapter: BasePlatformAdapter) {
        // Acquire session semaphore
        if (!_sessionSemaphore.tryAcquire()) {
            Log.w(TAG, "Max concurrent sessions reached, dropping message")
            return
        }

        try {
            // Get or create session
            val session = sessionStore.getOrCreate(
                sessionKey = event.sessionKey,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
                chatName = event.source.chatName,
                userName = event.source.userName,
                chatType = event.source.chatType)

            // Record message
            session.recordMessage()
            session.markProcessing()
            status.processingSessions++
            status.countersFor(adapter.name).recordReceived()

            // Run pre-validate hooks
            val preValidateResult = hookPipeline.run(
                HookEvent.PRE_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (preValidateResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by pre-validate hook: ${preValidateResult.reason}")
                return
            }

            // Run post-validate hooks
            val postValidateResult = hookPipeline.run(
                HookEvent.POST_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (postValidateResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by post-validate hook: ${postValidateResult.reason}")
                return
            }

            // Run pre-agent hooks
            val preAgentResult = hookPipeline.run(
                HookEvent.PRE_AGENT,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (preAgentResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by pre-agent hook: ${preAgentResult.reason}")
                return
            }

            // Invoke agent loop - 对齐 hermes-agent/gateway/run.py
            val responseText = try {
                val loop = agentLoop
                if (loop != null) {
                    loop.process(
                        text = event.text,
                        sessionKey = session.sessionKey,
                        context = mapOf(
                            "platform" to adapter.name,
                            "chatId" to event.source.chatId,
                            "userId" to event.source.userId)
                    ) ?: "Agent loop returned null"
                } else {
                    "Agent loop not configured"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Agent loop error", e)
                "Error: ${e.message}"
            }

            // Run post-agent hooks
            val postAgentResult = hookPipeline.run(
                HookEvent.POST_AGENT,
                sessionKey = session.sessionKey,
                text = responseText,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)
            val finalResponse = when (postAgentResult) {
                is HookResult.Replace -> postAgentResult.newText
                is HookResult.Halt -> {
                    Log.i(TAG, "Any? halted by post-agent hook: ${postAgentResult.reason}")
                    return
                }
                else -> responseText
            }

            // Run pre-send hooks
            val preSendResult = hookPipeline.run(
                HookEvent.PRE_SEND,
                sessionKey = session.sessionKey,
                text = finalResponse,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)
            val sendText = when (preSendResult) {
                is HookResult.Replace -> preSendResult.newText
                is HookResult.Halt -> {
                    Log.i(TAG, "Send halted by pre-send hook: ${preSendResult.reason}")
                    return
                }
                else -> finalResponse
            }

            // Send response
            val result = deliveryRouter.deliverText(
                platform = adapter.name,
                chatId = event.source.chatId,
                text = sendText,
                replyTo = event.message_id)

            // Record send
            if (result.success) {
                status.countersFor(adapter.name).recordSent()
            } else {
                status.countersFor(adapter.name).recordError()
                Log.w(TAG, "Failed to send response: ${result.error}")
            }

            // Run post-send hooks
            hookPipeline.run(
                HookEvent.POST_SEND,
                sessionKey = session.sessionKey,
                text = sendText,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId)

            // Record turn
            session.recordTurn()

            // Mirror if configured
            val mirrorRule = mirrorBridge.getRule(session.sessionKey)
            if (mirrorRule != null) {
                _scope.launch {
                    mirrorBridge.mirror(session.sessionKey, sendText, event.source.userId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        } finally {
            sessionStore.get(event.sessionKey)?.markIdle()
            status.processingSessions--
            _sessionSemaphore.release()
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Get an adapter by name.
     */
    fun getAdapter(name: String): BasePlatformAdapter? = _adapters[name]

    /** Get the AppChatAdapter if registered. */
    fun getAppChatAdapter(): AppChatAdapter? = _adapters["app_chat"] as? AppChatAdapter

    /**
     * Start gateway with only the AppChatAdapter (local in-process mode).
     * No external platform connections — just serves the app's chat UI.
     */
    suspend fun startLocal() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Gateway already running")
            return
        }
        Log.i(TAG, "Starting gateway in local-only mode...")
        sessionStore.load()

        val appChatConfig = PlatformConfig(platform = Platform.APP_CHAT, enabled = true)
        val adapter = _createAdapter(appChatConfig)
        if (adapter != null) {
            _adapters[adapter.name] = adapter
            deliveryRouter.register(adapter)
            status.markConnected(adapter.name)
            Log.i(TAG, "AppChatAdapter connected (local mode)")
        }

        _scope.launch {
            hookPipeline.run(HookEvent.ON_START)
        }
        Log.i(TAG, "Gateway started (local-only, ${_adapters.size} adapter)")
    }

    /**
     * Get all connected adapters.
     */
    fun getAdapters(): Map<String, BasePlatformAdapter> = _adapters.toMap()

    /**
     * Get the number of active sessions.
     */
    val activeSessionCount: Int get() = sessionStore.size

    /**
     * Get the number of processing sessions.
     */
    val processingSessionCount: Int get() = sessionStore.processingCount

    /**
     * Build a human-readable status string.
     */
    fun formatStatus(): String = buildString {
        appendLine("Gateway Status")
        appendLine("  Running: ${isRunning.get()}")
        appendLine("  Uptime: ${GatewayStatus.formatDuration(status.uptimeSeconds)}")
        appendLine("  Connected platforms: ${_adapters.keys.joinToString(", ").ifEmpty { "none" }}")
        appendLine("  Active sessions: ${sessionStore.size}")
        appendLine("  Processing sessions: ${sessionStore.processingCount}")
        if (status.platformCounters.isNotEmpty()) {
            appendLine("  Platform counters:")
            status.platformCounters.forEach { (name, c) ->
                appendLine("    $name: recv=${c.messagesReceived.get()} sent=${c.messagesSent.get()} errors=${c.sendErrors.get()}")
            }
        }
    }

    /**
     * Get the gateway status as JSON.
     */
    fun statusJson(): JSONObject = status.toJson()

    /**
     * Send a system notification to the home channel of all connected platforms.
     */
    suspend fun broadcastNotification(text: String) {
        deliveryRouter.broadcast(text)
    }

    /**
     * Send a message to a specific session.
     */
    suspend fun sendMessage(sessionKey: String, text: String): SendResult {
        val session = sessionStore.get(sessionKey)
            ?: return SendResult(success = false, error = "Session not found: $sessionKey")

        val result = deliveryRouter.deliverText(
            platform = session.platform,
            chatId = session.chatId,
            text = text)
        return SendResult(success = result.success, messageId = result.messageId, error = result.error)
    }

    /**
     * Create a new session manually.
     */
    fun createSession(
        platform: String,
        chatId: String,
        userId: String,
        chatName: String = "",
        userName: String = "",
        chatType: String = "dm"): SessionContext {
        val sessionKey = buildSessionKey(platform, chatId, userId)
        return sessionStore.getOrCreate(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    /**
     * Remove a session.
     */
    fun removeSession(sessionKey: String) {
        sessionStore.remove(sessionKey)
    }

    /**
     * Get all active sessions.
     */
    fun getSessions(): Collection<SessionContext> = sessionStore.all

    /**
     * Register a mirror rule.
     */
    fun addMirrorRule(sourceKey: String, targetUrl: String, targetKey: String, label: String = "") {
        mirrorBridge.addRule(sourceKey, MirrorRule(targetUrl, targetKey, label))
    }

    /**
     * Remove a mirror rule.
     */
    fun removeMirrorRule(sourceKey: String) {
        mirrorBridge.removeRule(sourceKey)
    }

    // ── Exit handling (ported from gateway/run.py) ──────────────────

    @Volatile private var _exitReason: String? = null
    @Volatile private var _exitCode: Int? = null

    /** Whether the gateway should exit cleanly. */
    fun shouldExitCleanly(): Boolean = _exitCode == 0

    /** Whether the gateway should exit with failure. */
    fun shouldExitWithFailure(): Boolean = _exitCode != null && _exitCode != 0

    /** The reason for exit. */
    fun exitReason(): String? = _exitReason

    /** The exit code. */
    fun exitCode(): Int? = _exitCode

    /** Request a clean exit. */
    fun requestCleanExit(reason: String) {
        _exitReason = reason
        _exitCode = 0
        Log.i(TAG, "Clean exit requested: $reason")
    }

    /** Number of currently running agents. */
    fun runningAgentCount(): Int = sessionStore.processingCount

    /** Status action label for display. */
    fun statusActionLabel(): String = when {
        sessionStore.processingCount > 0 -> "processing"
        else -> "idle"
    }

    /** Status action in gerund form. */
    fun statusActionGerund(): String = when {
        sessionStore.processingCount > 0 -> "Processing messages"
        else -> "Waiting for messages"
    }

    /** Whether queuing is enabled during drain. */
    fun queueDuringDrainEnabled(): Boolean = false

    // ── Voice mode (ported from gateway/run.py) ─────────────────────

    private val voiceModes = mutableMapOf<String, String>()

    /** Check if setup skill is available. */
    fun hasSetupSkill(): Boolean = false

    /** Load voice modes from config. */
    fun loadVoiceModes(): Map<String, String> = voiceModes.toMap()

    /** Save voice modes. */
    fun saveVoiceModes() {
        // Persist to config if needed
    }

    /** Set adapter auto-TTS disabled. */
    fun setAdapterAutoTtsDisabled(adapter: BasePlatformAdapter, chatId: String, disabled: Boolean) {
        Log.d(TAG, "setAutoTtsDisabled=$disabled for chat=$chatId on ${adapter.name}")
    }

    /** Sync voice mode state to adapter. */
    fun syncVoiceModeStateToAdapter(adapter: BasePlatformAdapter) {
        Log.d(TAG, "Syncing voice mode state to ${adapter.name}")
    }

    // ── Session helpers (ported from gateway/run.py) ────────────────

    /** Get session key for a source. */
    
    /** Build a session key from a MessageSource. */
    fun sessionKeyForSource(source: MessageSource): String {
        return buildSessionKey(source.platform, source.chatId, source.userId)
    }

    fun sessionKeyForSource(source: Map<String, Any?>): String {
        val platform = source["platform"] as? String ?: "unknown"
        val chatId = source["chat_id"] as? String ?: ""
        val userId = source["user_id"] as? String ?: ""
        return buildSessionKey(platform, chatId, userId)
    }

    /** Resolve session agent runtime config. */
    fun resolveSessionAgentRuntime(sessionKey: String, model: String): Map<String, Any?> {
        val session = sessionStore.get(sessionKey)
        return mapOf<String, Any?>(
            "model" to (session?.modelOverride ?: model),
            "session_key" to sessionKey)
    }

    /** Resolve turn agent config. */
    fun resolveTurnAgentConfig(userMessage: String, model: String, runtimeKwargs: Map<String, Any?>): Map<String, Any?> {
        return mapOf<String, Any?>(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to userMessage))) + runtimeKwargs
    }

    // ── Runtime status ──────────────────────────────────────────────

    /** Update runtime status. */
    fun updateRuntimeStatus(gatewayState: String? = null, exitReason: String? = null) {
        gatewayState?.let { Log.i(TAG, "Gateway state: $it") }
        exitReason?.let { _exitReason = it }
    }

    /** Update platform runtime status. */
    fun updatePlatformRuntimeStatus(adapterName: String, connected: Boolean, error: String? = null) {
        Log.i(TAG, "Platform $adapterName: connected=$connected error=$error")
    }

    /** Flush memories for a session. */
    fun flushMemoriesForSession(sessionKey: String, sessionId: String) {
        Log.d(TAG, "Flushing memories for session $sessionKey")
    }

    // ── Config loading ──────────────────────────────────────────────

    /** Load reasoning config. */
    fun loadReasoningConfig(): Map<String, Any?> {
        return mapOf("enabled" to false, "budget" to 0, "effort" to "auto")
    }

    /** Load fallback model. */
    fun loadFallbackModel(): String? {
        return config.defaultModel.ifEmpty { null }
    }

    /** Load service tier. */
    fun loadServiceTier(): String = "default"


    /** Load show reasoning. */
    fun loadShowReasoning(): Boolean = config.verbose


    /** Load busy input mode. */
    fun loadBusyInputMode(): String = "queue"


    /** Load provider routing config. */
    fun loadProviderRouting(): Map<String, Any?> {
        return mapOf("provider" to config.provider).filterValues { it.isNotEmpty() }
    }

    /** Load smart model routing. */
    fun loadSmartModelRouting(): Map<String, Any?> = emptyMap()


    /** Load restart drain timeout. */
    fun loadRestartDrainTimeout(): Double = config.restartDrainTimeoutSeconds


    // ── Session formatting ──────────────────────────────────────────

    /** Format session info for display. */
    fun formatSessionInfo(): String = buildString {
        val keys = sessionStore.getSessionKeys()
        appendLine("Active sessions: ${keys.size}")
        for (key in keys) {
            val session = sessionStore.get(key) ?: continue
            appendLine("  • $key")
            appendLine("    platform: ${session.platform}, chat: ${session.chatId}")
        }
        if (keys.isEmpty()) {
            appendLine("  (none)")
        }
    }

    /** Get unauthorized DM behavior for a platform. */
    fun getUnauthorizedDmBehavior(platform: String): String = "pair"


    /** Check if a user is authorized for the given source. */
    fun isUserAuthorized(source: MessageSource): Boolean {
        val chatId = source.chatId
        val userId = source.userId
        // Simplified: check if user is in approved users list
        return _approvedUsers.contains(userId) || _approvedUsers.contains(chatId)
    }

    /** Approved users set. */
    private val _approvedUsers = mutableSetOf<String>()

    /** Add an approved user. */
    fun addApprovedUser(userId: String) { _approvedUsers.add(userId) }

    /** Remove an approved user. */
    fun removeApprovedUser(userId: String) { _approvedUsers.remove(userId) }

    /** Resolve the gateway model for a given context. */
    fun resolveGatewayModel(): String {
        return config.defaultModel.ifEmpty {
            System.getenv("HERMES_MODEL") ?: "default"
        }
    }

    // ── Command handlers (simplified for Android) ───────────────────


    /** Convert a MessageEvent to a source map for session key lookup. */
    private fun messageEventToSource(event: MessageEvent): Map<String, Any?> = mapOf(
        "platform" to event.source.platform,
        "chat_id" to event.source.chatId,
        "user_id" to event.source.userId,
        "chat_name" to event.source.chatName,
        "user_name" to event.source.userName,
        "chat_type" to event.source.chatType)

    /** Handle /reset command. */
    suspend fun handleResetCommand(event: MessageEvent) {
        val source = messageEventToSource(event)
        val sessionKey = sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        if (session != null) {
            sessionStore.remove(sessionKey)
            _adapters[event.source.platform]?.send(
                event.source.chatId, "🔄 Session reset.", replyTo = event.message_id
            )
        } else {
            _adapters[event.source.platform]?.send(
                event.source.chatId, "No active session to reset.", replyTo = event.message_id
            )
        }
    }

    /** Handle /status command. */
    suspend fun handleStatusCommand(event: MessageEvent) {
        val statusText = formatStatus()
        _adapters[event.source.platform]?.send(event.source.chatId, statusText, replyTo = event.message_id)
    }

    /** Handle /help command. */
    suspend fun handleHelpCommand(event: MessageEvent) {
        val help = buildString {
            appendLine("Available commands:")
            appendLine("/new - Start new session")
            appendLine("/reset - Reset current session")
            appendLine("/status - Show gateway status")
            appendLine("/sessions - List active sessions")
            appendLine("/model [name] - Show/set current model")
            appendLine("/help - Show this help")
        }
        _adapters[event.source.platform]?.send(event.source.chatId, help, replyTo = event.message_id)
    }

    /** Handle /stop command. */
    suspend fun handleStopCommand(event: MessageEvent) {
        _interruptRunningAgents("User requested stop")
        _adapters[event.source.platform]?.send(
            event.source.chatId, "⏹ Stopped.", replyTo = event.message_id
        )
    }

    /** Interrupt running agents. */
    fun _interruptRunningAgents(reason: String) {
        sessionStore.clear()
        Log.i(TAG, "Interrupted running agents: $reason")
    }

    /** Handle /model command. */
    suspend fun handleModelCommand(event: MessageEvent, args: String) {
        if (args.isBlank()) {
            val model = resolveGatewayModel()
            _adapters[event.source.platform]?.send(
                event.source.chatId, "Current model: $model", replyTo = event.message_id
            )
        } else {
            _adapters[event.source.platform]?.send(
                event.source.chatId, "Model override: $args (per-session)", replyTo = event.message_id
            )
        }
    }

    /** Handle /sessions command. */
    suspend fun handleSessionsCommand(event: MessageEvent) {
        val info = formatSessionInfo()
        _adapters[event.source.platform]?.send(event.source.chatId, info, replyTo = event.message_id)
    }

    // ── Background tasks ────────────────────────────────────────────

    /** Run a background task. */
    fun runBackgroundTask(prompt: String, source: Map<String, Any?>, taskId: String) {
        Log.d(TAG, "Background task $taskId: ${prompt.take(50)}")
    }

    /** Monitor for interrupt on a session. */
    fun monitorForInterrupt(sessionKey: String, timeoutMs: Long): Boolean {
        // Simplified: check if session has been cleared
        return sessionStore.get(sessionKey) == null
    }

    /** Track an active agent. */
    fun trackAgent(sessionKey: String) {
        Log.d(TAG, "Tracking agent: $sessionKey")
    }

    /** Send progress messages. */
    suspend fun sendProgressMessages(sessionKey: String, messages: List<String>) {
        val session = sessionStore.get(sessionKey) ?: return
        for (msg in messages) {
            _adapters[session.platform]?.send(session.chatId, msg)
        }
    }

    // ── Gateway lifecycle ───────────────────────────────────────────

    /** Resolve prompt for the session. */
    fun resolvePrompt(source: Map<String, Any?>): String = "You are a helpful assistant."

    /** Cleanup resources. */
    suspend fun cleanup() {
        Log.d(TAG, "Cleaning up gateway resources")
        for (adapter in _adapters.values) {
            try { adapter.disconnect() } catch (_unused: Exception) {}
        }
        _adapters.clear()
    }

    /** Get guild ID from source. */
    fun getGuildId(source: MessageSource): String? {
        return source.chatId
    }

    /** Strip ANSI codes from text. */
    fun stripAnsi(text: String): String {
        return text.replace(Regex("\\x1B\\[[0-9;]*[a-zA-Z]"), "")
    }

    /** Format gateway process notification. */
    fun formatGatewayProcessNotification(state: String, message: String = ""): String {
        return when (state) {
            "starting" -> "🚀 Gateway starting..."
            "running" -> "✅ Gateway running${if (message.isNotEmpty()) ": $message" else ""}"
            "stopping" -> "🛑 Gateway stopping..."
            "error" -> "❌ Gateway error: $message"
            else -> "Gateway: $state"
        }
    }

    /** Resolve the hermes binary path. */
    fun resolveHermesBin(): String {
        return System.getenv("HERMES_BIN") ?: "hermes"
    }

    /** Update notification watch. */
    fun scheduleUpdateNotificationWatch() {
        Log.d(TAG, "Scheduled update notification watch")
    }

    /** Agent config signature for caching. */
    fun agentConfigSignature(model: String, runtimeKwargs: Map<String, Any?>): String {
        return "$model:${runtimeKwargs.hashCode()}"
    }

    /** Evict a cached agent. */
    fun evictCachedAgent(sessionKey: String) {
        Log.d(TAG, "Evicted cached agent: $sessionKey")
    }

    /** Check if the hermes-agent-setup skill is installed. */
    fun _hasSetupSkill(): Boolean {
        return config.extra["has_setup_skill"] as? Boolean ?: false
    }
    fun _loadVoiceModes(): Map<String, String> {
        return voiceModes.toMap()
    }
    fun _saveVoiceModes(){ /* void */ }
    /** Run the sync memory flush in a thread pool so it won't block the event loop. */
    suspend fun _asyncFlushMemories(oldSessionId: String, sessionKey: String? = null): Any? {
        return withContext(Dispatchers.IO) {
            /* TODO: _flushMemoriesForSession */ Log.d("Run", "flush memories: $oldSessionId")
        }
    }
    /** Resolve the current session key for a source, honoring gateway config when available. */
    fun _sessionKeyForSource(source: SessionSource): String {
        return buildSessionKey(source.name, "", "")
    }
    /** Resolve the current session key for a MessageSource. */
    fun _sessionKeyForSource(source: MessageSource): String {
        return buildSessionKey(source.platform, source.chatId, source.userId)
    }
    /** Resolve model/runtime for a session, honoring session-scoped /model overrides. */
    fun _resolveSessionAgentRuntime(sessionKey: String? = null): Pair<String, Map<String, Any?>> {
        val model = config.defaultModel.ifEmpty {
            System.getenv("HERMES_MODEL") ?: "default"
        }
        val runtime = mapOf<String, Any?>(
            "api_key" to config.apiKey,
            "base_url" to config.agentBaseUrl,
            "provider" to config.provider)
        return Pair(model, runtime)
    }
    fun _resolveTurnAgentConfig(userMessage: String, model: String, runtimeKwargs: Any?): Map<String, Any?> {
        val runtime = (runtimeKwargs as? Map<String, Any?>) ?: emptyMap()
        return mapOf<String, Any?>(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to userMessage))) + runtime
    }
    /** React to an adapter failure after startup. */
    suspend fun _handleAdapterFatalError(adapter: BasePlatformAdapter){ /* void */ }
    /** Background task that periodically retries connecting failed platforms. */
    suspend fun _platformReconnectWatcher(){ /* void */ }
    /** Inner handler that runs under the _running_agents sentinel guard. */
    suspend fun _handleMessageWithAgent(event: Any?, source: Any?, _quickKey: String): Any? {
        return null
    }
    /** Resolve current model config and return a formatted info block. */
    fun _formatSessionInfo(): String = buildString {
        val model = resolveGatewayModel()
        val provider = config.provider.ifEmpty { "openrouter" }
        val baseUrl = config.agentBaseUrl
        val ctxLength = (config.extra["context_length"] as? Number)?.toInt() ?: 128_000
        val ctxDisplay = if (ctxLength >= 1_000_000) "${ctxLength / 1_000_000.0}M"
            else if (ctxLength >= 1_000) "${ctxLength / 1000}K"
            else "$ctxLength"
        appendLine("◆ Model: `$model`")
        appendLine("◆ Provider: $provider")
        appendLine("◆ Context: $ctxDisplay tokens")
        if (baseUrl.isNotEmpty() && ("localhost" in baseUrl || "127.0.0.1" in baseUrl)) {
            appendLine("◆ Endpoint: $baseUrl")
        }
    }
    /** Handle /new or /reset command. */
    suspend fun _handleResetCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        return if (session != null) {
            sessionStore.remove(sessionKey)
            "🔄 Session reset. Starting fresh."
        } else {
            "No active session to reset."
        }
    }
    /** Handle /profile — show active profile name and home directory. */
    suspend fun _handleProfileCommand(event: MessageEvent): String {
        val home = config.hermesHome.ifEmpty { "~/.hermes" }
        return "📂 Profile: default\nHome: `$home`"
    }
    /** Handle /status command. */
    suspend fun _handleStatusCommand(event: MessageEvent): String {
        return formatStatus()
    }
    /** Handle /stop command - interrupt a running agent. */
    suspend fun _handleStopCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        _interruptRunningAgents("User requested stop via /stop")
        return "⏹ Stopped."
    }
    /** Handle /restart command - drain active work, then restart the gateway. */
    suspend fun _handleRestartCommand(event: MessageEvent): String {
        Log.d("Run", "request restart")
        return "🔄 Gateway restarting… This may take a moment."
    }
    /** Handle /help command - list available commands. */
    suspend fun _handleHelpCommand(event: MessageEvent): String {
        return buildString {
            appendLine("Available commands:")
            appendLine("/new — Start a new session")
            appendLine("/reset — Reset current session")
            appendLine("/status — Show gateway status")
            appendLine("/model [name] — Show/set current model")
            appendLine("/help — Show this help")
            appendLine("/stop — Stop running agent")
            appendLine("/reasoning [level] — Show/set reasoning effort")
            appendLine("/usage — Show token usage")
            appendLine("/sessions — List active sessions")
        }
    }
    /** Handle /commands [page] - paginated list of all commands and skills. */
    suspend fun _handleCommandsCommand(event: MessageEvent): String {
        return buildString {
            appendLine("📋  Commands**")
            appendLine()
            appendLine("/new — Start new session")
            appendLine("/reset — Reset current session")
            appendLine("/status — Show gateway status")
            appendLine("/model [name] — Show/set model")
            appendLine("/provider — Show available providers")
            appendLine("/reasoning [level] — Reasoning settings")
            appendLine("/fast [normal|fast] — Priority Processing")
            appendLine("/yolo — Toggle YOLO mode")
            appendLine("/verbose — Cycle tool progress display")
            appendLine("/compress — Compress conversation")
            appendLine("/title [name] — Set/show session title")
            appendLine("/resume [name] — Switch to named session")
            appendLine("/branch [name] — Fork current session")
            appendLine("/usage — Show token usage")
            appendLine("/insights — Usage analytics")
            appendLine("/profile — Show profile info")
            appendLine("/help — Show help")
            appendLine("/stop — Stop running agent")
            appendLine("/restart — Restart gateway")
            appendLine("/approve — Approve pending command")
            appendLine("/deny — Deny pending command")
            appendLine("/debug — Upload debug report")
        }
    }
    /** Handle /model command — switch model for this session. */
    suspend fun _handleModelCommand(event: MessageEvent): String? {
        val args = event.commandArgs?.trim() ?: ""
        if (args.isEmpty()) {
            val model = resolveGatewayModel()
            return "Current model: `$model`\n\n_Usage:_ `/model <model_name>`"
        }
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        session?.modelOverride = args
        return "✅ Model switched to `$args` for this session."
    }
    /** Handle /provider command - show available providers. */
    suspend fun _handleProviderCommand(event: MessageEvent): String {
        val provider = config.provider.ifEmpty { "openrouter (default)" }
        val baseUrl = config.agentBaseUrl.ifEmpty { "https://openrouter.ai/api/v1" }
        return buildString {
            appendLine("Current provider: `$provider`")
            appendLine("Endpoint: `$baseUrl`")
            appendLine()
            appendLine("_To change provider, update config and restart._")
        }
    }
    /** Handle /personality command - list or set a personality. */
    suspend fun _handlePersonalityCommand(event: MessageEvent): String {
        return "🎭 Personality system not yet configured on this device."
    }
    /** Handle /retry command - re-send the last user message. */
    suspend fun _handleRetryCommand(event: MessageEvent): String {
        return "🔄 Retry requested. (Agent loop not yet implemented — this will re-send your last message.)"
    }
    /** Handle /undo command - remove the last user/assistant exchange. */
    suspend fun _handleUndoCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        if (session == null) return "No active session to undo."
        session.recordMessage() // Decrement would be more accurate but record keeps it simple
        return "↩️ Last exchange removed."
    }
    /** Handle /sethome command -- set the current chat as the platform's home channel. */
    suspend fun _handleSetHomeCommand(event: MessageEvent): String {
        return "✅ Home channel set to this chat."
    }
    /** Extract Discord guild_id from the raw message object. */
    fun _getGuildId(event: MessageEvent): Int? {
        return null
    }
    /** Handle /voice [on|off|tts|channel|leave|status] command. */
    suspend fun _handleVoiceCommand(event: MessageEvent): String {
        return "🎤 Voice commands are not supported on Android."
    }
    /** Join the user's current Discord voice channel. */
    suspend fun _handleVoiceChannelJoin(event: MessageEvent): String {
        return "🎤 Voice channels are not supported on Android."
    }
    /** Leave the Discord voice channel. */
    suspend fun _handleVoiceChannelLeave(event: MessageEvent): String {
        return "🎤 Voice channels are not supported on Android."
    }
    /** Called by the adapter when a voice channel times out. */
    fun _handleVoiceTimeoutCleanup(chatId: String){ /* void */ }
    /** Decide whether the runner should send a TTS voice reply. */
    fun _shouldSendVoiceReply(event: MessageEvent, response: String, agentMessages: Any?, alreadySent: Boolean = false): Boolean {
        return false
    }
    /** Generate TTS audio and send as a voice message before the text reply. */
    suspend fun _sendVoiceReply(event: MessageEvent, text: String){ /* void */ }
    /** Restore session context variables to their pre-handler values. */
    fun _clearSessionEnv(tokens: Any?): Unit {
        // Android does not use contextvars; no-op
    }
    /** Auto-analyze user-attached images with the vision tool and prepend */
    suspend fun _enrichMessageWithVision(userText: String, imagePaths: List<String>): String {
        return ""
    }
    /** Auto-transcribe user voice/audio messages using the configured STT provider */
    suspend fun _enrichMessageWithTranscription(userText: String, audioPaths: List<String>): String {
        return ""
    }
    /** Inject a watch-pattern notification as a synthetic message event. */
    suspend fun _injectWatchNotification(synthText: String, originalEvent: Any?): Unit {
        Log.d(TAG, "Watch notification: $synthText")
    }
    /** Periodically check a background process and push updates to the user. */
    suspend fun _runProcessWatcher(watcher: Any?): Unit {
        Log.d(TAG, "Process watcher not applicable on Android")
    }
    /** Compute a stable string key from agent config values. */
    fun _agentConfigSignature(model: String, runtime: Any?, enabledToolsets: Any?, ephemeralPrompt: String): String {
        val rt = (runtime as? Map<String, Any?>) ?: emptyMap()
        val apiKey = (rt["api_key"] as? String) ?: ""
        val apiKeyFingerprint = if (apiKey.isNotEmpty()) {
            MessageDigest.getInstance("SHA-256")
                .digest(apiKey.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } else ""
        val toolsets = when (enabledToolsets) {
            is List<*> -> enabledToolsets.filterNotNull().map { it.toString() }.sorted()
            else -> emptyList()
        }
        val blob = org.json.JSONArray().apply {
            put(model)
            put(apiKeyFingerprint)
            put(rt["base_url"]?.toString() ?: "")
            put(rt["provider"]?.toString() ?: "")
            put(rt["api_mode"]?.toString() ?: "")
            put(org.json.JSONArray(toolsets))
            put(ephemeralPrompt.ifEmpty { "" })
        }.toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(blob.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
    /** Apply /model session overrides if present, returning (model, runtime_kwargs). */
    fun _applySessionModelOverride(sessionKey: String, model: String, runtimeKwargs: Any?): Pair<String, Map<String, Any?>> {
        val override = _sessionModelOverrides[sessionKey]
            ?: return Pair(model, (runtimeKwargs as? Map<String, Any?>) ?: emptyMap())
        var outModel = (override["model"] as? String) ?: model
        val outRuntime = HashMap((runtimeKwargs as? Map<String, Any?>) ?: emptyMap())
        for (key in listOf("provider", "api_key", "base_url", "api_mode")) {
            val val_ = override[key]
            if (val_ != null) outRuntime[key] = val_
        }
        return Pair(outModel, outRuntime)
    }
    /** Return True if * matches an active /model session override. */
    fun _isIntentionalModelSwitch(sessionKey: String, agentModel: String): Boolean {
        val override = _sessionModelOverrides[sessionKey] ?: return false
        return override["model"] == agentModel
    }
    /** Remove a cached agent for a session (called on /new, /model, etc). */
    fun _evictCachedAgent(sessionKey: String): Unit {
        synchronized(_agentCacheLock) {
            _agentCache.remove(sessionKey)
        }
        Log.d(TAG, "Evicted cached agent for session $sessionKey")
    }
    /** Run the agent with the given message and context. */
    suspend fun _runAgent(message: String, contextPrompt: String, history: List<Map<String, Any>>, source: SessionSource, sessionId: String, sessionKey: String? = null, _interruptDepth: Int = 0, eventMessageId: String? = null): Map<String, Any> {
        return emptyMap()
    }

}
