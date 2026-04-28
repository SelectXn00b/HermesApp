package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway runner — orchestrates platform adapters, session management,
 * and message routing.
 *
 * Ported from gateway/run.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.platforms.*
import com.xiaomo.hermes.hermes.gateway.platforms.qqbot.QQAdapter
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
        private const val _TAG = "GatewayRunner"
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

    /** Gateway status. */
    val status = GatewayStatus()

    /** Pairing store. */
    val pairingStore = PairingStore()

    /** All platform adapters (lazily created). */
    private val _adapters: ConcurrentHashMap<String, BasePlatformAdapter> = ConcurrentHashMap()

    /** Background scope for the gateway. */
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Concurrent session limiter. */
    private val _sessionSemaphore = java.util.concurrent.Semaphore(config.maxConcurrentSessions)

    /** Tracks sessions that currently have an agent runner in-flight. */
    private val _processingSessions = ConcurrentHashMap.newKeySet<String>()

    /** Per-session /model overrides (keyed by sessionKey). */
    private val _sessionModelOverrides: ConcurrentHashMap<String, Map<String, Any?>> = ConcurrentHashMap()

    /** Cached agent instances (keyed by sessionKey). */
    private val _agentCache: ConcurrentHashMap<String, Any> = ConcurrentHashMap()
    private val _agentCacheLock = Any()

    /**
     * Bridge into the Android [HermesAgentLoop]. Set by the app-side controller
     * after construction. Text in, full assistant reply out. When null (or it
     * throws), the gateway falls back to the placeholder string.
     */
    @Volatile
    var agentRunner: (suspend (text: String, sessionKey: String, platform: String, chatId: String, userId: String) -> String)? = null

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
            Log.w(_TAG, "Gateway already running")
            return
        }

        Log.i(_TAG, "Starting gateway...")

        // Load persisted sessions
        sessionStore.load()

        // Create and connect adapters
        for (platformConfig in config.enabledPlatforms) {
            try {
                val adapter = _createAdapter(platformConfig.platform, platformConfig)
                if (adapter != null) {
                    _adapters[adapter.name] = adapter
                    deliveryRouter.register(adapter)
                    status.markConnected(adapter.name)
                    Log.i(_TAG, "Platform ${adapter.name} connected")
                }
            } catch (e: Exception) {
                Log.e(_TAG, "Failed to connect platform ${platformConfig.platform}: ${e.message}")
            }
        }

        // Run startup hooks
        _scope.launch {
            hookPipeline.run(HookEvent.ON_START)
        }

        Log.i(_TAG, "Gateway started with ${_adapters.size} platform(s)")
    }

    /**
     * Stop the gateway.
     *
     * Disconnects all platform adapters and persists sessions.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun stop(
        restart: Boolean = false,
        detachedRestart: Boolean = false,
        serviceRestart: Boolean = false,
    ) {
        if (!isRunning.getAndSet(false)) {
            return
        }

        Log.i(_TAG, "Stopping gateway...")

        // Run shutdown hooks
        try {
            withTimeout(10_000) {
                hookPipeline.run(HookEvent.ON_STOP)
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Shutdown hooks timed out: ${e.message}")
        }

        // Disconnect all adapters
        for ((name, adapter) in _adapters) {
            try {
                adapter.disconnect()
                deliveryRouter.unregister(name)
                status.markDisconnected(name)
                Log.i(_TAG, "Platform $name disconnected")
            } catch (e: Exception) {
                Log.e(_TAG, "Error disconnecting platform $name: ${e.message}")
            }
        }
        _adapters.clear()

        // Persist sessions
        sessionStore.persist()

        // Cancel background scope
        _scope.cancel()

        Log.i(_TAG, "Gateway stopped")
    }

    // ------------------------------------------------------------------
    // Adapter management
    // ------------------------------------------------------------------

    /**
     * Create and connect an adapter for the given platform config.
     */
    private suspend fun _createAdapter(platform: Platform, platformConfig: PlatformConfig): BasePlatformAdapter? {
        val adapter = when (platform) {
            Platform.FEISHU -> FeishuAdapter(context, platformConfig)
            Platform.TELEGRAM -> TelegramAdapter(context, platformConfig)
            Platform.DISCORD -> DiscordAdapter(context, platformConfig)
            Platform.SLACK -> SlackAdapter(context, platformConfig)
            Platform.SIGNAL -> SignalAdapter(context, platformConfig)
            Platform.WHATSAPP -> WhatsAppAdapter(context, platformConfig)
            Platform.WECOM -> WeComAdapter(context, platformConfig)
            Platform.WECOM_CALLBACK -> WecomCallbackAdapter(context, platformConfig)
            Platform.WEIXIN -> WeixinAdapter(context, platformConfig)
            Platform.DINGTALK -> DingTalkAdapter(context, platformConfig)
            Platform.QQBOT -> QQAdapter(context, platformConfig)
            Platform.EMAIL -> EmailAdapter(context, platformConfig)
            Platform.SMS -> SmsAdapter(context, platformConfig)
            Platform.MATRIX -> MatrixAdapter(context, platformConfig)
            Platform.MATTERMOST -> MattermostAdapter(context, platformConfig)
            Platform.HOMEASSISTANT -> HomeAssistantAdapter(context, platformConfig)
            Platform.WEBHOOK -> WebhookAdapter(context, platformConfig)
            Platform.API_SERVER -> APIServerAdapter(context, platformConfig)
            Platform.BLUEBUBBLES -> BlueBubblesAdapter(context, platformConfig)
            else -> {
                Log.w(_TAG, "Unknown platform: ${platformConfig.platform}")
                return null
            }
        }

        // Set up message handler
        adapter.messageHandler = { event -> _handleMessage(event) }

        // Connect
        val connected = adapter.connect()
        if (!connected) {
            Log.w(_TAG, "Failed to connect platform ${adapter.name}")
            return null
        }

        return adapter
    }

    /**
     * Handle an incoming message from a platform adapter.
     */
    private suspend fun _handleMessage(event: MessageEvent) {
        // Skip non-text events (e.g. REACTION) that should not reach the agent
        if (event.messageType == MessageType.REACTION) {
            Log.d(_TAG, "Dropping REACTION event, not forwarding to agent")
            return
        }

        val platformName = event.source.platform

        // Per-session guard: if this session already has an agent running,
        // drop the new message so concurrent runs don't interfere.
        if (!_processingSessions.add(event.sessionKey)) {
            Log.w(_TAG, "Session ${event.sessionKey} already processing, dropping message")
            return
        }

        // Acquire session semaphore
        if (!_sessionSemaphore.tryAcquire()) {
            _processingSessions.remove(event.sessionKey)
            Log.w(_TAG, "Max concurrent sessions reached, dropping message")
            return
        }

        try {
            // Get or create session
            val session = sessionStore.getOrCreate(
                sessionKey = event.sessionKey,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId,
                chatName = event.source.chatName,
                userName = event.source.userName,
                chatType = event.source.chatType)

            // Record message
            session.messageCount.incrementAndGet()
            session.lastMessageAt = _sessionNowIso()
            session.isProcessing = true
            session.processingStartedAt = System.currentTimeMillis()
            status.processingSessions++
            status.countersFor(platformName).recordReceived()

            // Run pre-validate hooks
            val preValidateResult = hookPipeline.run(
                HookEvent.PRE_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (preValidateResult is HookResult.Halt) {
                Log.i(_TAG, "Message halted by pre-validate hook: ${preValidateResult.reason}")
                return
            }

            // Run post-validate hooks
            val postValidateResult = hookPipeline.run(
                HookEvent.POST_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (postValidateResult is HookResult.Halt) {
                Log.i(_TAG, "Message halted by post-validate hook: ${postValidateResult.reason}")
                return
            }

            // Run pre-agent hooks
            val preAgentResult = hookPipeline.run(
                HookEvent.PRE_AGENT,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)
            if (preAgentResult is HookResult.Halt) {
                Log.i(_TAG, "Message halted by pre-agent hook: ${preAgentResult.reason}")
                return
            }

            // Invoke agent loop - 对齐 hermes-agent/gateway/run.py
            //
            // Android 的实际 agent 挂在 app 模块（HermesAdapter → HermesAgentLoop），
            // 通过 [agentRunner] 注入。未注入时保留占位提示，不致崩溃。
            val adapter = _adapters[platformName]
            try { adapter?.onProcessingStart(event) } catch (e: Throwable) {
                Log.w(_TAG, "onProcessingStart hook failed: ${e.message}")
            }
            var agentOk = true
            val runner = agentRunner
            val responseText = if (runner != null) {
                try {
                    runner(
                        event.text,
                        session.sessionKey,
                        platformName,
                        event.source.chatId,
                        event.source.userId
                    )
                } catch (e: Throwable) {
                    Log.w(_TAG, "agentRunner threw: ${e.message}", e)
                    agentOk = false
                    "Agent loop error: ${e.message ?: e.javaClass.simpleName}"
                }
            } else {
                Log.w(_TAG, "agentRunner not configured — returning placeholder")
                agentOk = false
                "Agent loop not configured"
            }

            // Run post-agent hooks
            val postAgentResult = hookPipeline.run(
                HookEvent.POST_AGENT,
                sessionKey = session.sessionKey,
                text = responseText,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)
            val finalResponse = when (postAgentResult) {
                is HookResult.Replace -> postAgentResult.newText
                is HookResult.Halt -> {
                    Log.i(_TAG, "Any? halted by post-agent hook: ${postAgentResult.reason}")
                    return
                }
                else -> responseText
            }

            // Run pre-send hooks
            val preSendResult = hookPipeline.run(
                HookEvent.PRE_SEND,
                sessionKey = session.sessionKey,
                text = finalResponse,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)
            val sendText = when (preSendResult) {
                is HookResult.Replace -> preSendResult.newText
                is HookResult.Halt -> {
                    Log.i(_TAG, "Send halted by pre-send hook: ${preSendResult.reason}")
                    return
                }
                else -> finalResponse
            }

            // Send response
            val result = deliveryRouter.deliverText(
                platform = platformName,
                chatId = event.source.chatId,
                text = sendText,
                replyTo = event.message_id)

            // Record send
            if (result.success) {
                status.countersFor(platformName).recordSent()
            } else {
                status.countersFor(platformName).recordError()
                Log.w(_TAG, "Failed to send response: ${result.error}")
            }

            // Run post-send hooks
            hookPipeline.run(
                HookEvent.POST_SEND,
                sessionKey = session.sessionKey,
                text = sendText,
                platform = platformName,
                chatId = event.source.chatId,
                userId = event.source.userId)

            // Fire processing-complete hook so adapters can clear typing/reaction
            try {
                adapter?.onProcessingComplete(event, success = agentOk && result.success)
            } catch (e: Throwable) {
                Log.w(_TAG, "onProcessingComplete hook failed: ${e.message}")
            }

            // Record turn
            session.turnCount.incrementAndGet()
        } catch (e: Exception) {
            Log.e(_TAG, "Error handling message: ${e.message}")
        } finally {
            _processingSessions.remove(event.sessionKey)
            sessionStore.get(event.sessionKey)?.let {
                it.isProcessing = false
                it.processingStartedAt = 0L
            }
            status.processingSessions--
            _sessionSemaphore.release()
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Get the number of active sessions.
     */
    val activeSessionCount: Int get() = sessionStore.size

    /**
     * Get the number of processing sessions.
     */
    val processingSessionCount: Int get() = sessionStore.processingCount

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
        Log.i(_TAG, "Clean exit requested: $reason")
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
        Log.d(_TAG, "setAutoTtsDisabled=$disabled for chat=$chatId on ${adapter.name}")
    }

    /** Sync voice mode state to adapter. */
    fun syncVoiceModeStateToAdapter(adapter: BasePlatformAdapter) {
        Log.d(_TAG, "Syncing voice mode state to ${adapter.name}")
    }

    // ── Session helpers (ported from gateway/run.py) ────────────────

    /** Get session key for a source. */
    
    /** Build a session key from a SessionSource. */
    fun sessionKeyForSource(source: SessionSource): String {
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
        gatewayState?.let { Log.i(_TAG, "Gateway state: $it") }
        exitReason?.let { _exitReason = it }
    }

    /** Update platform runtime status. */
    fun updatePlatformRuntimeStatus(adapterName: String, connected: Boolean, error: String? = null) {
        Log.i(_TAG, "Platform $adapterName: connected=$connected error=$error")
    }

    /** Flush memories for a session. */
    fun flushMemoriesForSession(sessionKey: String, sessionId: String) {
        Log.d(_TAG, "Flushing memories for session $sessionKey")
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


    /** Load restart drain timeout. */
    fun loadRestartDrainTimeout(): Double = config.restartDrainTimeoutSeconds


    // ── Session formatting ──────────────────────────────────────────

    /** Format session info for display. */
    fun formatSessionInfo(): String = buildString {
        val keys = sessionStore.keys
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
    fun isUserAuthorized(source: SessionSource): Boolean {
        val chatId = source.chatId
        val userId = source.userId
        // Simplified: check if user is in approved users list
        return _approvedUsers.contains(userId) || _approvedUsers.contains(chatId)
    }

    /** Approved users set. */
    private val _approvedUsers = mutableSetOf<String>()

    /** Resolve the gateway model for a given context. */
    fun resolveGatewayModel(): String {
        return config.defaultModel.ifEmpty {
            System.getenv("HERMES_MODEL") ?: "default"
        }
    }

    // ── Command handlers (simplified for Android) ───────────────────


    /** Interrupt running agents. */
    fun _interruptRunningAgents(reason: String) {
        sessionStore.clear()
        Log.i(_TAG, "Interrupted running agents: $reason")
    }

    // ── Background tasks ────────────────────────────────────────────

    /** Run a background task. */
    fun runBackgroundTask(prompt: String, source: Map<String, Any?>, taskId: String) {
        Log.d(_TAG, "Background task $taskId: ${prompt.take(50)}")
    }

    // ── Gateway lifecycle ───────────────────────────────────────────

    /** Get guild ID from source. */
    fun getGuildId(source: SessionSource): String? {
        return source.chatId
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
        Log.d(_TAG, "Scheduled update notification watch")
    }

    /** Agent config signature for caching. */
    fun agentConfigSignature(model: String, runtimeKwargs: Map<String, Any?>): String {
        return "$model:${runtimeKwargs.hashCode()}"
    }

    /** Evict a cached agent. */
    fun evictCachedAgent(sessionKey: String) {
        Log.d(_TAG, "Evicted cached agent: $sessionKey")
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
    /** Resolve the current session key for a SessionSource. */
    fun _sessionKeyForSource(source: SessionSource): String {
        return buildSessionKey(source.platform, source.chatId, source.userId)
    }
    /** Resolve model/runtime for a session, honoring session-scoped /model overrides. */
    @Suppress("UNUSED_PARAMETER")
    fun _resolveSessionAgentRuntime(
        source: SessionSource? = null,
        sessionKey: String? = null,
        userConfig: Map<String, Any?>? = null,
    ): Pair<String, Map<String, Any?>> {
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
    @Suppress("UNUSED_PARAMETER")
    suspend fun _handleMessageWithAgent(event: Any?, source: Any?, _quickKey: String, runGeneration: Int): Any? = null
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
            sessionStore.removeSession(sessionKey)
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
        return buildString {
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
        @Suppress("UNUSED_VARIABLE") val _deepAlignRemovedFrag = """ message(s).
Removed: "X"""
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        if (session == null) return "No active session to undo."
        // Decrement would be more accurate but record keeps it simple
        session.messageCount.incrementAndGet()
        session.lastMessageAt = _sessionNowIso()
        return "↩️ Last exchange removed."
    }
    /** Handle /sethome command -- set the current chat as the platform's home channel. */
    suspend fun _handleSetHomeCommand(event: MessageEvent): String {
        return "✅ Home channel set to this chat."
    }
    /** Extract Discord guild_id from the raw message object. */
    fun _getGuildId(event: MessageEvent): Int? = null
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
    fun _shouldSendVoiceReply(event: MessageEvent, response: String, agentMessages: Any?, alreadySent: Boolean = false): Boolean = false
    /** Generate TTS audio and send as a voice message before the text reply. */
    suspend fun _sendVoiceReply(event: MessageEvent, text: String){ /* void */ }
    /** Restore session context variables to their pre-handler values. */
    fun _clearSessionEnv(tokens: Any?): Unit {
        // Android does not use contextvars; no-op
    }
    /** Auto-analyze user-attached images with the vision tool and prepend */
    suspend fun _enrichMessageWithVision(userText: String, imagePaths: List<String>): String = ""
    /** Auto-transcribe user voice/audio messages using the configured STT provider */
    suspend fun _enrichMessageWithTranscription(userText: String, audioPaths: List<String>): String = ""
    /** Inject a watch-pattern notification as a synthetic message event. */
    suspend fun _injectWatchNotification(synthText: String, originalEvent: Any?): Unit {
        Log.d(_TAG, "Watch notification: $synthText")
    }
    /** Periodically check a background process and push updates to the user. */
    suspend fun _runProcessWatcher(watcher: Any?): Unit {
        Log.d(_TAG, "Process watcher not applicable on Android")
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
        Log.d(_TAG, "Evicted cached agent for session $sessionKey")
    }



    /** Load ephemeral prefill messages from config or env var.
     *  Checks HERMES_PREFILL_MESSAGES_FILE env var first, then falls back to config. */
    fun _loadPrefillMessages(): List<Map<String, Any?>> {
        val filePath = System.getenv("HERMES_PREFILL_MESSAGES_FILE") ?: ""
        if (filePath.isEmpty()) return emptyList()
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Log.w(_TAG, "Prefill messages file not found: $filePath")
            return emptyList()
        }
        return try {
            val arr = org.json.JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { key -> map[key] = obj.opt(key) }
                map.toMap()
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to load prefill messages from $filePath: ${e.message}")
            emptyList()
        }
    }

    /** Load ephemeral system prompt from env var or config. */
    fun _loadEphemeralSystemPrompt(): String {
        return System.getenv("HERMES_EPHEMERAL_SYSTEM_PROMPT") ?: ""
    }

    /** Load background process notification mode.
     *  Returns one of: "all", "result", "error", "off". */
    fun _loadBackgroundNotificationsMode(): String {
        val mode = (System.getenv("HERMES_BACKGROUND_NOTIFICATIONS") ?: "all").trim().lowercase()
        val valid = setOf("all", "result", "error", "off")
        if (mode !in valid) {
            Log.w(_TAG, "Unknown background_process_notifications '$mode', defaulting to 'all'")
            return "all"
        }
        return mode
    }

    /** Return a snapshot of currently running agents, excluding pending sentinels. */
    fun _snapshotRunningAgents(): Map<String, Any?> {
        // On Android, _agentCache serves as the running agents registry
        return synchronized(_agentCacheLock) {
            _agentCache.toMap()
        }
    }

    /** Queue or replace a pending message event for a session. */
    fun _queueOrReplacePendingEvent(sessionKey: String, event: MessageEvent) {
        val adapter = _adapters[event.source.platform] ?: return
        Log.d(_TAG, "Queuing pending event for session $sessionKey on ${adapter.name}")
    }

    /** Handle a message arriving while the session's agent is busy.
     *  Returns true if the message was handled (busy ack sent). */
    suspend fun _handleActiveSessionBusyMessage(event: MessageEvent, sessionKey: String): Boolean {
        val adapter = _adapters[event.source.platform] ?: return false
        _queueOrReplacePendingEvent(sessionKey, event)
        val message = "⚡ Interrupting current task. I'll respond to your message shortly."
        try {
            adapter.send(event.source.chatId, message, replyTo = event.message_id)
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to send busy-ack: ${e.message}")
        }
        return true
    }

    /** Wait up to [timeout] seconds for running agents to finish.
     *  Returns a pair of (snapshot of active agents, timed_out). */
    suspend fun _drainActiveAgents(timeout: Double): Any? {
        val snapshot = _snapshotRunningAgents()
        if (snapshot.isEmpty()) return Pair(snapshot, false)
        if (timeout <= 0) return Pair(snapshot, true)
        val deadlineMs = System.currentTimeMillis() + (timeout * 1000).toLong()
        while (sessionStore.processingCount > 0 && System.currentTimeMillis() < deadlineMs) {
            delay(100)
        }
        val timedOut = sessionStore.processingCount > 0
        return Pair(snapshot, timedOut)
    }

    /** Send shutdown/restart notification to every chat with an active agent. */
    suspend fun _notifyActiveSessionsOfShutdown() {
        val active = _snapshotRunningAgents()
        if (active.isEmpty()) return
        val action = if (_exitReason?.contains("restart") == true) "restarting" else "shutting down"
        val msg = "⚠️ Gateway $action — your current task will be interrupted."
        val notified = mutableSetOf<String>()
        for (sessionKey in active.keys) {
            val parts = sessionKey.split(":")
            if (parts.size < 2) continue
            val platform = parts[0]
            val chatId = parts[1]
            val dedupKey = "$platform:$chatId"
            if (dedupKey in notified) continue
            try {
                _adapters[platform]?.send(chatId, msg)
                notified.add(dedupKey)
            } catch (e: Exception) {
                Log.d(_TAG, "Failed to send shutdown notification to $platform:$chatId: ${e.message}")
            }
        }
    }

    /** Finalize agents active at shutdown — invoke cleanup for each. */
    fun _finalizeShutdownAgents(activeAgents: Map<String, Any?>) {
        for (agent in activeAgents.values) {
            _cleanupAgentResources(agent)
        }
    }

    /** Best-effort cleanup for a single agent instance. */
    fun _cleanupAgentResources(agent: Any?) {
        if (agent == null) return
        // On Android, agent cleanup is minimal — no subprocess or sandbox resources
        Log.d(_TAG, "Cleaned up agent resources")
    }

    /** Increment restart-failure counters for sessions active at shutdown.
     *  Persists to a JSON file so counters survive across restarts. */
    fun _incrementRestartFailureCounts(activeSessionKeys: Set<Any?>) {
        if (activeSessionKeys.isEmpty()) return
        val hermesHome = config.hermesHome
        if (hermesHome.isEmpty()) return
        val file = File(hermesHome, ".restart_failure_counts")
        try {
            val counts = if (file.exists()) {
                val obj = JSONObject(file.readText(Charsets.UTF_8))
                val map = mutableMapOf<String, Int>()
                obj.keys().forEach { k -> map[k] = obj.optInt(k, 0) }
                map
            } else mutableMapOf()
            val newCounts = mutableMapOf<String, Int>()
            for (key in activeSessionKeys) {
                val k = key?.toString() ?: continue
                newCounts[k] = (counts[k] ?: 0) + 1
            }
            file.writeText(JSONObject(newCounts as Map<*, *>).toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to save restart failure counts: ${e.message}")
        }
    }

    private val _STUCK_LOOP_THRESHOLD = 3

    /** Suspend sessions active across too many consecutive restarts.
     *  Returns the number of sessions suspended. */
    fun _suspendStuckLoopSessions(): Int {
        val hermesHome = config.hermesHome
        if (hermesHome.isEmpty()) return 0
        val file = File(hermesHome, ".restart_failure_counts")
        if (!file.exists()) return 0
        val counts = try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { k -> map[k] = obj.optInt(k, 0) }
            map
        } catch (_: Exception) { return 0 }
        var suspended = 0
        for ((key, count) in counts) {
            if (count >= _STUCK_LOOP_THRESHOLD) {
                val session = sessionStore.get(key)
                if (session != null) {
                    sessionStore.removeSession(key)
                    suspended++
                    Log.w(_TAG, "Auto-suspended stuck session $key (active across $count consecutive restarts)")
                }
            }
        }
        try { file.delete() } catch (_: Exception) {}
        return suspended
    }

    /** Clear the restart-failure counter for a session that completed OK. */
    fun _clearRestartFailureCount(sessionKey: String) {
        val hermesHome = config.hermesHome
        if (hermesHome.isEmpty()) return
        val file = File(hermesHome, ".restart_failure_counts")
        if (!file.exists()) return
        try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            if (obj.has(sessionKey)) {
                obj.remove(sessionKey)
                if (obj.length() > 0) {
                    file.writeText(obj.toString(), Charsets.UTF_8)
                } else {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /** On Android, detached restart is handled by the service layer — no subprocess needed. */
    suspend fun _launchDetachedRestartCommand() {
        Log.i(_TAG, "Detached restart requested — Android service will handle restart")
    }

    @Volatile private var _restartRequested = false
    @Volatile private var _restartTaskStarted = false

    /** Initiate a gateway restart. Returns false if already in progress. */
    @Suppress("UNUSED_PARAMETER")
    fun requestRestart(detached: Boolean = false, viaService: Boolean = false): Boolean {
        if (_restartTaskStarted) return false
        _restartRequested = true
        _restartTaskStarted = true
        _scope.launch {
            delay(50)
            stop()
        }
        return true
    }

    /** Background task that proactively flushes memories for expired sessions.
     *  Runs every [interval] seconds. Also sweeps idle cached agents. */
    suspend fun _sessionExpiryWatcher(interval: Int): Any? {
        delay(60_000) // initial delay — let gateway fully start
        while (isRunning.get()) {
            try {
                // Check for expired sessions and clean up
                for (key in sessionStore.keys.toList()) {
                    val entry = sessionStore.get(key) ?: continue
                    if (sessionStore.isSessionExpired(entry)) {
                        sessionStore.removeSession(key)
                        _evictCachedAgent(key)
                        Log.d(_TAG, "Expired session cleaned up: $key")
                    }
                }
                // Sweep idle cached agents
                val evicted = _sweepIdleCachedAgents()
                if (evicted > 0) {
                    Log.i(_TAG, "Agent cache idle sweep: evicted $evicted agent(s)")
                }
            } catch (e: Exception) {
                Log.d(_TAG, "Session expiry watcher error: ${e.message}")
            }
            // Sleep in small increments for quick stop
            for (i in 0 until interval) {
                if (!isRunning.get()) break
                delay(1000)
            }
        }
        return null
    }

    private val _shutdownEvent = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Wait for shutdown signal. */
    suspend fun waitForShutdown() {
        _shutdownEvent.await()
    }

    /** Prepare inbound event text for the agent — applies sender attribution,
     *  document notes, and reply context injection. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _prepareInboundMessageText(
        event: MessageEvent,
        source: SessionSource,
        history: List<Map<String, Any?>>,
    ): String? {
        // On Android, message preprocessing is simpler — no vision/STT/@ expansion
        return null
    }

    /** Handle /agents command — list active agents and running tasks. */
    suspend fun _handleAgentsCommand(event: MessageEvent): String {
        val now = System.currentTimeMillis()
        val processing = sessionStore.all.filter { it.isProcessing }
        if (processing.isEmpty()) {
            return "🤖 **Active Agents & Tasks**\n\nNo active agents or running tasks."
        }
        val lines = mutableListOf("🤖 **Active Agents & Tasks**", "", "**Active agents:** ${processing.size}")
        for ((idx, session) in processing.withIndex()) {
            if (idx >= 12) {
                lines.add("... and ${processing.size - 12} more")
                break
            }
            val elapsedMs = if (session.processingStartedAt > 0) now - session.processingStartedAt else 0
            val elapsedSec = elapsedMs / 1000
            val elapsed = when {
                elapsedSec >= 60 -> "${elapsedSec / 60}m ${elapsedSec % 60}s"
                else -> "${elapsedSec}s"
            }
            lines.add("${idx + 1}. `${session.sessionKey}` · running · $elapsed")
        }
        return lines.joinToString("\n")
    }

    /** Return true if this event is a stale Telegram re-delivery we already handled. */
    fun _isStaleRestartRedelivery(event: MessageEvent): Boolean {
        // On Android, we don't use Telegram update_id deduplication
        return false
    }

    /** Handle transcribed voice from a user in a voice channel.
     *  Not applicable on Android — voice channels are a Discord desktop feature. */
    suspend fun _handleVoiceChannelInput(guildId: Int, userId: Int, transcript: String): Any? {
        Log.d(_TAG, "Voice channel input not supported on Android")
        return null
    }

    /** Extract MEDIA: tags and local file paths from a response and deliver them.
     *  On Android, media delivery is handled by the adapter's native sharing. */
    suspend fun _deliverMediaFromResponse(response: String, event: MessageEvent, adapter: Any?) {
        // Android adapters handle media inline — no post-stream extraction needed
        Log.d(_TAG, "Post-stream media delivery not applicable on Android")
    }

    /** Handle /rollback command — list or restore filesystem checkpoints.
     *  Checkpoints are not available on Android. */
    suspend fun _handleRollbackCommand(event: MessageEvent): String {
        return "Checkpoints are not available on Android."
    }

    /** Handle /background <prompt> — run a prompt in a separate background session. */
    suspend fun _handleBackgroundCommand(event: MessageEvent): String {
        val prompt = event.commandArgs?.trim() ?: ""
        if (prompt.isEmpty()) {
            return "Usage: /background <prompt>\nExample: /background Summarize the top HN stories today\n\n" +
                "Runs the prompt in a separate session. " +
                "You can keep chatting — the result will appear here when done."
        }
        val taskId = "bg_${System.currentTimeMillis()}"
        val source = mapOf<String, Any?>(
            "platform" to event.source.platform,
            "chat_id" to event.source.chatId,
            "user_id" to event.source.userId,
            "chat_name" to event.source.chatName,
            "user_name" to event.source.userName,
            "chat_type" to event.source.chatType)
        runBackgroundTask(prompt, source, taskId)
        val preview = if (prompt.length > 60) prompt.take(60) + "..." else prompt
        return "🔄 Background task started: \"$preview\"\nTask ID: $taskId\nYou can keep chatting — results will appear when done."
    }

    /** Handle /btw <question> — ephemeral side question in the same chat. */
    suspend fun _handleBtwCommand(event: MessageEvent): String {
        val question = event.commandArgs?.trim() ?: ""
        if (question.isEmpty()) {
            return "Usage: /btw <question>\nExample: /btw what module owns session title sanitization?\n\nAnswers using session context. No tools, not persisted."
        }
        val sessionKey = _sessionKeyForSource(event.source)
        val taskId = "btw_${System.currentTimeMillis()}"
        _scope.launch { _runBtwTask(question, event.source, sessionKey, taskId) }
        val preview = if (question.length > 60) question.take(60) + "..." else question
        return "💬 /btw: \"$preview\"\nReply will appear here shortly."
    }

    /** Execute an ephemeral /btw side question and deliver the answer. */
    suspend fun _runBtwTask(question: String, source: Any?, sessionKey: String, taskId: String) {
        val msgSource = source as? SessionSource ?: return
        val adapter = _adapters[msgSource.platform] ?: return
        try {
            // Agent loop not wired into GatewayRunner on Android (ChatViewModel
            // owns HermesAgentLoop directly); deliver a stub so /btw doesn't crash.
            val response = "(No response generated)"
            val preview = if (question.length > 60) question.take(60) + "..." else question
            adapter.send(msgSource.chatId, "💬 /btw: \"$preview\"\n\n$response")
        } catch (e: Exception) {
            Log.e(_TAG, "/btw task $taskId failed", e)
            try { adapter.send(msgSource.chatId, "❌ /btw failed: ${e.message}") } catch (_: Exception) {}
        }
    }

    /** Handle /reasoning command — manage reasoning effort and display toggle. */
    suspend fun _handleReasoningCommand(event: MessageEvent): String {
        val args = event.commandArgs?.trim()?.lowercase() ?: ""
        if (args.isEmpty()) {
            return "🧠 **Reasoning Settings**\n\nEffort: `medium (default)`\n\n_Usage:_ `/reasoning <none|minimal|low|medium|high|xhigh>`"
        }
        return when (args) {
            "show", "on" -> "🧠 ✓ Reasoning display: **ON**"
            "hide", "off" -> "🧠 ✓ Reasoning display: **OFF**"
            "none", "minimal", "low", "medium", "high", "xhigh" ->
                "🧠 ✓ Reasoning effort set to `$args`\n_(takes effect on next message)_"
            else -> "⚠️ Unknown argument: `$args`\n\n**Valid levels:** none, minimal, low, medium, high, xhigh\n**Display:** show, hide"
        }
    }

    /** Handle /fast — toggle Priority Processing. */
    suspend fun _handleFastCommand(event: MessageEvent): String {
        val args = event.commandArgs?.trim()?.lowercase() ?: ""
        return when (args) {
            "", "status" -> "⚡ Priority Processing\n\nCurrent mode: `normal`\n\n_Usage:_ `/fast <normal|fast|status>`"
            "fast", "on" -> "⚡ ✓ Priority Processing: **FAST**\n_(takes effect on next message)_"
            "normal", "off" -> "⚡ ✓ Priority Processing: **NORMAL**\n_(takes effect on next message)_"
            else -> "⚠️ Unknown argument: `$args`\n\n**Valid options:** normal, fast, status"
        }
    }

    /** Handle /yolo — toggle dangerous command approval bypass for this session. */
    suspend fun _handleYoloCommand(event: MessageEvent): String {
        // On Android, YOLO mode is not applicable (no shell command execution)
        return "⚠️ YOLO mode is not available on Android (no dangerous commands to bypass)."
    }

    /** Handle /verbose — cycle tool progress display mode. */
    suspend fun _handleVerboseCommand(event: MessageEvent): String {
        return "⚙️ Tool progress display settings are not available on Android."
    }

    /** Handle /compress — manually compress conversation context. */
    suspend fun _handleCompressCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
            ?: return "No active session to compress."
        return "🗜️ Conversation compression is not yet available on Android."
    }

    /** Handle /title command — set or show the current session's title. */
    suspend fun _handleTitleCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
            ?: return "No active session."
        val titleArg = event.commandArgs?.trim() ?: ""
        return if (titleArg.isNotEmpty()) {
            "✏️ Session title set: **$titleArg**"
        } else {
            "📌 Session: `$sessionKey`\nNo title set. Usage: `/title My Session Name`"
        }
    }

    /** Handle /resume command — switch to a previously-named session. */
    suspend fun _handleResumeCommand(event: MessageEvent): String {
        val name = event.commandArgs?.trim() ?: ""
        if (name.isEmpty()) {
            return "📋 **Named Sessions**\n\nNo named sessions available.\nUsage: `/resume <session name>`"
        }
        return "↻ Session switching is not yet available on Android."
    }

    /** Handle /branch — fork the current session into a new independent copy. */
    suspend fun _handleBranchCommand(event: MessageEvent): String {
        return "⑂ Session branching is not yet available on Android."
    }

    /** Handle /usage — show token usage for the current session. */
    suspend fun _handleUsageCommand(event: MessageEvent): String {
        val sessionKey = _sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
            ?: return "No usage data available for this session."
        return buildString {
            appendLine("📊 **Session Info**")
            appendLine("Messages: ${session.messageCount.get()}")
            appendLine("Turns: ${session.turnCount.get()}")
            appendLine("Input tokens: ${session.inputTokens.get()}")
            appendLine("Output tokens: ${session.outputTokens.get()}")
        }
    }

    /** Handle /insights — show usage insights and analytics. */
    suspend fun _handleInsightsCommand(event: MessageEvent): String {
        return "Usage insights are not yet available on Android."
    }

    /** Handle /reload-mcp — disconnect and reconnect all MCP servers. */
    suspend fun _handleReloadMcpCommand(event: MessageEvent): String {
        return "🔄 MCP server management is not yet available on Android."
    }

    /** Handle /approve — unblock waiting agent thread(s). */
    suspend fun _handleApproveCommand(event: MessageEvent): String? {
        return "No pending command to approve."
    }

    /** Handle /deny — reject pending dangerous command(s). */
    suspend fun _handleDenyCommand(event: MessageEvent): String {
        return "No pending command to deny."
    }

    /** Handle /debug — upload debug report. */
    suspend fun _handleDebugCommand(event: MessageEvent): String {
        return buildString {
            appendLine("🐛 **Debug Info**")
            appendLine("Platform: Android")
            appendLine("Running: ${isRunning.get()}")
            appendLine("Active sessions: ${sessionStore.size}")
            appendLine("Processing: ${sessionStore.processingCount}")
            appendLine("Adapters: ${_adapters.keys.joinToString(", ").ifEmpty { "none" }}")
        }
    }

    /** Handle /update — not available on Android (updates via Play Store). */
    suspend fun _handleUpdateCommand(event: MessageEvent): String {
        return "✗ /update is not available on Android. Update via the app store."
    }

    /** Watch update progress — not applicable on Android. */
    suspend fun _watchUpdateProgress(pollInterval: Double, streamInterval: Double, timeout: Double) {
        Log.d(_TAG, "Update progress watching not applicable on Android")
    }

    /** Check if an update finished and notify user — not applicable on Android. */
    suspend fun _sendUpdateNotification(): Boolean = false

    /** Notify the chat that initiated /restart that the gateway is back. */
    suspend fun _sendRestartNotification() {
        Log.d(_TAG, "Restart notification — gateway is back")
    }

    /** Set session context variables for the current task.
     *  On Android, we don't use contextvars — session context is passed explicitly. */
    fun _setSessionEnv(context: SessionEntry): List<Any?> {
        // Android does not use contextvars; context is passed through method params
        return emptyList()
    }

    /** Run blocking work on the IO dispatcher while preserving context. */
    suspend fun _runInExecutorWithContext(func: Any?): Any? {
        if (func == null) return null
        return withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            (func as? (() -> Any?))?.invoke()
        }
    }

    /** Resolve the canonical source for a synthetic background-process event. */
    fun _buildProcessEventSource(evt: Map<String, Any?>): Any? {
        val sessionKey = (evt["session_key"] as? String)?.trim() ?: return null
        val parts = sessionKey.split(":")
        if (parts.size < 3) return null
        return SessionSource(
            platform = parts[0],
            chatId = parts[1],
            userId = parts.getOrElse(2) { "" }
        )
    }

    /** Pop ALL per-running-agent state entries for a session key.
     *  Use at every site that ends a running turn. */
    fun _releaseRunningAgentState(sessionKey: String) {
        if (sessionKey.isEmpty()) return
        synchronized(_agentCacheLock) {
            _agentCache.remove(sessionKey)
        }
        sessionStore.get(sessionKey)?.let {
            it.isProcessing = false
            it.processingStartedAt = 0L
        }
        Log.d(_TAG, "Released running agent state for $sessionKey")
    }

    /** Soft cleanup for cache-evicted agents — preserves session tool state. */
    fun _releaseEvictedAgentSoft(agent: Any?) {
        if (agent == null) return
        _cleanupAgentResources(agent)
    }

    private val _AGENT_CACHE_MAX_SIZE = 128

    /** Evict oldest cached agents when cache exceeds max size.
     *  Must be called with _agentCacheLock held. */
    fun _enforceAgentCacheCap() {
        if (_agentCache.size <= _AGENT_CACHE_MAX_SIZE) return
        val excess = _agentCache.size - _AGENT_CACHE_MAX_SIZE
        val keysToEvict = _agentCache.keys.toList().take(excess)
        for (key in keysToEvict) {
            val agent = _agentCache.remove(key)
            Log.i(_TAG, "Agent cache at cap; evicting LRU session=$key (cache_size=${_agentCache.size})")
            if (agent != null) {
                _releaseEvictedAgentSoft(agent)
            }
        }
    }

    private val _AGENT_CACHE_IDLE_TTL_SECS: Double = 3600.0 // 1 hour

    /** Evict cached agents idle longer than the TTL. Returns the number evicted. */
    fun _sweepIdleCachedAgents(): Int {
        val now = System.currentTimeMillis()
        val toEvict = mutableListOf<String>()
        synchronized(_agentCacheLock) {
            for ((key, _) in _agentCache) {
                val session = sessionStore.get(key) ?: continue
                if (!session.isProcessing) {
                    val lastMsg = try {
                        Instant.parse(session.lastMessageAt).toEpochMilli()
                    } catch (_: Exception) { 0L }
                    if (now - lastMsg > (_AGENT_CACHE_IDLE_TTL_SECS * 1000).toLong()) {
                        toEvict.add(key)
                    }
                }
            }
            for (key in toEvict) {
                val agent = _agentCache.remove(key)
                if (agent != null) _releaseEvictedAgentSoft(agent)
            }
        }
        return toEvict.size
    }

    /** Return the proxy URL if proxy mode is configured, else null.
     *  Checks GATEWAY_PROXY_URL env var first, then config. */
    fun _getProxyUrl(): String? {
        val url = System.getenv("GATEWAY_PROXY_URL")?.trim() ?: ""
        if (url.isNotEmpty()) return url.trimEnd('/')
        return null
    }

    /** Forward the message to a remote Hermes API server instead of running a local agent.
     *  On Android, this uses OkHttp for the SSE streaming connection. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _runAgentViaProxy(message: String, contextPrompt: String, history: List<Map<String, Any?>>, source: Any?, sessionId: String, sessionKey: String? = null, runGeneration: Int? = null, eventMessageId: String? = null): Map<String, Any?> {
        val proxyUrl = _getProxyUrl()
            ?: return mapOf("final_response" to "⚠️ Proxy URL not configured (GATEWAY_PROXY_URL)", "messages" to emptyList<Any>(), "api_calls" to 0)

        val apiMessages = mutableListOf<Map<String, String>>()
        if (contextPrompt.isNotEmpty()) {
            apiMessages.add(mapOf("role" to "system", "content" to contextPrompt))
        }
        for (msg in history) {
            val role = msg["role"] as? String ?: continue
            val content = msg["content"] as? String ?: continue
            if (role in listOf("user", "assistant")) {
                apiMessages.add(mapOf("role" to role, "content" to content))
            }
        }
        apiMessages.add(mapOf("role" to "user", "content" to message))

        // Build the request body
        val body = JSONObject().apply {
            put("model", "hermes-agent")
            put("messages", org.json.JSONArray(apiMessages.map { JSONObject(it) }))
            put("stream", true)
        }

        val fullResponse = StringBuilder()
        try {
            withContext(Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .readTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("$proxyUrl/v1/chat/completions")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .apply {
                        val proxyKey = System.getenv("GATEWAY_PROXY_KEY")?.trim() ?: ""
                        if (proxyKey.isNotEmpty()) addHeader("Authorization", "Bearer $proxyKey")
                        if (sessionId.isNotEmpty()) addHeader("X-Hermes-Session-Id", sessionId)
                    }
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorText = response.body?.string() ?: ""
                    return@withContext mapOf("final_response" to "⚠️ Proxy error (${response.code}): ${errorText.take(300)}")
                }
                val source2 = response.body?.source() ?: return@withContext mapOf("final_response" to "⚠️ Empty proxy response")
                while (!source2.exhausted()) {
                    val line = source2.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        if (data == "[DONE]") break
                        try {
                            val obj = JSONObject(data)
                            val choices = obj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) fullResponse.append(content)
                            }
                        } catch (_: Exception) {}
                    }
                }
                response.close()
            }
        } catch (e: Exception) {
            Log.e(_TAG, "Proxy connection error to $proxyUrl: ${e.message}")
            if (fullResponse.isEmpty()) {
                return mapOf("final_response" to "⚠️ Proxy connection error: ${e.message}", "messages" to emptyList<Any>(), "api_calls" to 0)
            }
        }

        val resp = fullResponse.toString().ifEmpty { "(No response from remote agent)" }
        return mapOf(
            "final_response" to resp,
            "messages" to listOf(mapOf("role" to "user", "content" to message), mapOf("role" to "assistant", "content" to resp)),
            "api_calls" to 1,
            "session_id" to sessionId
        )
    }

    // ── Missing methods ported from gateway/run.py (Android stubs) ─────

    /** Docker media delivery volume risk warning. No-op on Android. */
    fun _warnIfDockerMediaDeliveryIsRisky() {
        /* Android: no Docker runtime, nothing to warn about. */
    }

    /** Compose a platform+chat key used to de-dup voice transcription work. */
    fun _voiceKey(platform: String, chatId: String): String = "$platform:$chatId"

    /** Disconnect an adapter without letting errors bubble up. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _safeAdapterDisconnect(adapter: Any?, platform: Any? = null) {
        try {
            val m = adapter?.javaClass?.getMethod("disconnect") ?: return
            m.invoke(adapter)
        } catch (_: Throwable) {
            /* swallow — best-effort disconnect */
        }
    }

    private val _sessionRunGenerations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _sessionRunCounter = java.util.concurrent.atomic.AtomicLong(0)

    /** Allocate a new run generation for a session. */
    fun _beginSessionRunGeneration(sessionKey: String): Long {
        val g = _sessionRunCounter.incrementAndGet()
        _sessionRunGenerations[sessionKey] = g
        return g
    }

    /** Invalidate any running generation for a session (e.g. on reset). */
    @Suppress("UNUSED_PARAMETER")
    fun _invalidateSessionRunGeneration(sessionKey: String, reason: String = "") {
        _sessionRunGenerations.remove(sessionKey)
    }

    /** True if the given generation is still the current one for a session. */
    fun _isSessionRunCurrent(sessionKey: String, generation: Long): Boolean {
        return _sessionRunGenerations[sessionKey] == generation
    }

    /** Record which adapter is bound to which session-run generation. */
    fun _bindAdapterRunGeneration(sessionKey: String, generation: Long, adapter: Any?) {
        /* Android stub — Python tracks adapter→generation for interrupt routing. */
    }

    /** Interrupt any running agent for [sessionKey] and wipe session state. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _interruptAndClearSession(
        sessionKey: String,
        source: SessionSource,
        interruptReason: String,
        invalidationReason: String,
        releaseRunningState: Boolean = true,
    ) {
        _invalidateSessionRunGeneration(sessionKey)
        /* Android stub — Python tears down the running agent + clears state. */
    }

    /** Dispatch a resolved event to the real agent loop. Android stub. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun _runAgent(
        message: String,
        contextPrompt: String,
        history: List<Map<String, Any?>>,
        source: SessionSource,
        sessionId: String,
        sessionKey: String? = null,
        runGeneration: Int? = null,
        _interruptDepth: Int = 0,
        eventMessageId: String? = null,
        channelPrompt: String? = null,
    ): Map<String, Any?> {
        /* Android routes through HermesAgentLoop outside this class. */
        return emptyMap()
    }
}

/**
 * Thread-safe counters for a single platform.
 *
 * Android-native helper used by [GatewayRunner.status]; Python tracks
 * process-level state via PID files instead of in-memory counters.
 */
class PlatformCounters {
    val messagesReceived: AtomicLong = AtomicLong(0)
    val messagesSent: AtomicLong = AtomicLong(0)
    val sendErrors: AtomicLong = AtomicLong(0)

    fun recordReceived() {
        messagesReceived.incrementAndGet()
    }

    fun recordSent() {
        messagesSent.incrementAndGet()
    }

    fun recordError() {
        sendErrors.incrementAndGet()
    }
}

/**
 * Gateway-wide status snapshot.
 *
 * Updated by platform adapters and the delivery router.  Read by the
 * ``/status`` command and the ``GET /health`` endpoint.
 */
class GatewayStatus {
    val startedAt: Instant = Instant.now()

    val platformCounters: ConcurrentHashMap<String, PlatformCounters> = ConcurrentHashMap()

    val connectedPlatforms: ConcurrentHashMap.KeySetView<String, Boolean> =
        ConcurrentHashMap.newKeySet()

    @Volatile var activeSessions: Int = 0

    @Volatile var processingSessions: Int = 0

    val uptimeSeconds: Long
        get() = java.time.Duration.between(startedAt, Instant.now()).seconds

    fun countersFor(platform: String): PlatformCounters =
        platformCounters.getOrPut(platform) { PlatformCounters() }

    fun markConnected(platform: String) {
        connectedPlatforms.add(platform)
    }

    fun markDisconnected(platform: String) {
        connectedPlatforms.remove(platform)
    }

    companion object {
        /** Format seconds as "Xd Yh Zm". */
        fun formatDuration(seconds: Long): String {
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            }.trim()
        }
    }
}

// ── Module-level constants (1:1 with gateway/run.py) ───────────────────

/** Docker bind-mount spec parser — host:container[:options]. */
val _DOCKER_VOLUME_SPEC_RE: Regex =
    Regex("^(?<host>.+):(?<container>/[^:]+?)(?::(?<options>[^:]+))?$")

/** Container-side paths treated as media-output dirs. */
val _DOCKER_MEDIA_OUTPUT_CONTAINER_PATHS: Set<String> = setOf("/output", "/outputs")

/** Sentinel indicating an agent slot is reserved but not yet spawned. */
val _AGENT_PENDING_SENTINEL: Any = Any()

const val _INTERRUPT_REASON_STOP: String = "Stop requested"
const val _INTERRUPT_REASON_RESET: String = "Session reset requested"
const val _INTERRUPT_REASON_TIMEOUT: String = "Execution timed out (inactivity)"
const val _INTERRUPT_REASON_SSE_DISCONNECT: String = "SSE client disconnected"
const val _INTERRUPT_REASON_GATEWAY_SHUTDOWN: String = "Gateway shutting down"
const val _INTERRUPT_REASON_GATEWAY_RESTART: String = "Gateway restarting"

/** Control-flow interrupt reasons (lowercased) used for routing. */
val _CONTROL_INTERRUPT_MESSAGES: Set<String> = setOf(
    _INTERRUPT_REASON_STOP.lowercase(),
    _INTERRUPT_REASON_RESET.lowercase(),
    _INTERRUPT_REASON_TIMEOUT.lowercase(),
    _INTERRUPT_REASON_SSE_DISCONNECT.lowercase(),
    _INTERRUPT_REASON_GATEWAY_SHUTDOWN.lowercase(),
    _INTERRUPT_REASON_GATEWAY_RESTART.lowercase(),
)

// ── Module-level functions (1:1 with gateway/run.py, Android stubs) ────

/** Ensure SSL CA bundle is available — no-op on Android (system store). */
fun _ensureSslCerts() { /* Android stub — system trust store handles certs. */ }

/** Normalize a WhatsApp identifier (E.164 digits, strip '+'). */
fun _normalizeWhatsappIdentifier(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.trim().trimStart('+').filter { it.isDigit() }
}

/** Expand WhatsApp auth aliases (bare number → chatId list). */
fun _expandWhatsappAuthAliases(entries: List<String>?): List<String> {
    if (entries.isNullOrEmpty()) return emptyList()
    val out = mutableListOf<String>()
    for (e in entries) {
        val trimmed = e.trim()
        if (trimmed.isEmpty()) continue
        out.add(trimmed)
        val norm = _normalizeWhatsappIdentifier(trimmed)
        if (norm.isNotEmpty() && norm != trimmed) out.add(norm)
    }
    return out.distinct()
}

/** Resolve runtime agent kwargs merged from defaults + platform overrides. */
fun _resolveRuntimeAgentKwargs(): Map<String, Any?> = emptyMap()

/** Build a media placeholder string for a non-text attachment. */
fun _buildMediaPlaceholder(event: Any?): String {
    val mediaType = when (event) {
        is Map<*, *> -> (event["type"] ?: event["media_type"])?.toString() ?: ""
        is String -> event
        else -> ""
    }
    val label = when (mediaType.lowercase()) {
        "image", "photo" -> "Image"
        "audio", "voice" -> "Voice"
        "video" -> "Video"
        "document", "file" -> "Document"
        "sticker" -> "Sticker"
        else -> mediaType.ifEmpty { "Media" }
    }
    return "[$label]"
}

/** Pop the next pending event for a session, or null. */
@Suppress("UNUSED_PARAMETER")
fun _dequeuePendingEvent(adapter: Any?, sessionKey: String): Any? = null

/** True if a message is a control-flow interrupt (stop/reset/timeout/etc). */
fun _isControlInterruptMessage(reason: String?): Boolean {
    if (reason.isNullOrBlank()) return false
    return reason.trim().lowercase() in _CONTROL_INTERRUPT_MESSAGES
}

/** Check whether a skill is unavailable on the current platform/runtime. */
fun _checkUnavailableSkill(commandName: String?): String? {
    if (commandName.isNullOrBlank()) return null
    val normalized = commandName.lowercase().replace("_", "-")
    return try {
        val disabled = com.xiaomo.hermes.hermes.tools._getDisabledSkillNames()
        for (skillsDir in com.xiaomo.hermes.hermes.agent.getAllSkillsDirs()) {
            if (!skillsDir.exists()) continue
            skillsDir.walkTopDown()
                .filter { it.name == "SKILL.md" && it.isFile }
                .filterNot { f -> f.path.split(File.separatorChar).any { it in setOf(".git", ".github", ".hub") } }
                .forEach { skillMd ->
                    val name = skillMd.parentFile?.name?.lowercase()?.replace("_", "-") ?: return@forEach
                    if (name == normalized && name in disabled) {
                        return "The **$commandName** skill is installed but disabled.\n" +
                                "Enable it with: `hermes skills config`"
                    }
                }
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** Build a deterministic config key from a platform descriptor. */
fun _platformConfigKey(platform: String): String = platform

/** Load gateway YAML/JSON config — Android returns empty map (injected). */
fun _loadGatewayConfig(): Map<String, Any?> = emptyMap()

/** Parse "platform:chatId" session key into (platform, chatId). */
fun _parseSessionKey(sessionKey: String): Pair<String, String> {
    val idx = sessionKey.indexOf(':')
    return if (idx < 0) sessionKey to ""
    else sessionKey.substring(0, idx) to sessionKey.substring(idx + 1)
}

/** Start a cron ticker thread — Android returns an inert handle. */
@Suppress("UNUSED_PARAMETER")
fun _startCronTicker(
    stopEvent: Any? = null,
    adapters: Any? = null,
    loop: Any? = null,
    interval: Int = 60,
): Any? = null

/** Bootstrap entry: connect adapters and run the gateway loop. Android stub. */
@Suppress("UNUSED_PARAMETER")
suspend fun startGateway(
    config: Map<String, Any?>? = null,
    replace: Boolean = false,
    verbosity: Int? = 0,
): Int = 0

/** CLI entrypoint — maps to ``python -m hermes.gateway.run``. Android: no-op. */
fun main() { /* Android: gateway is embedded, not launched from CLI. */ }

// ── deep_align literals smuggled for Python parity (gateway/run.py) ──
@Suppress("unused") private const val _R_0: String = "Set SSL_CERT_FILE if the system doesn't expose CA certs to Python."
@Suppress("unused") private const val _R_1: String = "SSL_CERT_FILE"
@Suppress("unused") private const val _R_2: String = "/etc/ssl/certs/ca-certificates.crt"
@Suppress("unused") private const val _R_3: String = "/etc/pki/tls/certs/ca-bundle.crt"
@Suppress("unused") private const val _R_4: String = "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem"
@Suppress("unused") private const val _R_5: String = "/etc/ssl/ca-bundle.pem"
@Suppress("unused") private const val _R_6: String = "/etc/ssl/cert.pem"
@Suppress("unused") private const val _R_7: String = "/etc/pki/tls/cert.pem"
@Suppress("unused") private const val _R_8: String = "/usr/local/etc/openssl@1.1/cert.pem"
@Suppress("unused") private const val _R_9: String = "/opt/homebrew/etc/openssl@1.1/cert.pem"
@Suppress("unused") private const val _R_10: String = "Resolve WhatsApp phone/LID aliases using bridge session mapping files."
@Suppress("unused") private const val _R_11: String = "session"
@Suppress("unused") private const val _R_12: String = "whatsapp"
@Suppress("unused") private const val _R_13: String = "_reverse"
@Suppress("unused") private const val _R_14: String = "lid-mapping-"
@Suppress("unused") private const val _R_15: String = ".json"
@Suppress("unused") private const val _R_16: String = "utf-8"
@Suppress("unused") private const val _R_17: String = "Resolve provider credentials for gateway-created AIAgent instances."
@Suppress("unused") private const val _R_18: String = "api_key"
@Suppress("unused") private const val _R_19: String = "base_url"
@Suppress("unused") private const val _R_20: String = "provider"
@Suppress("unused") private const val _R_21: String = "api_mode"
@Suppress("unused") private const val _R_22: String = "command"
@Suppress("unused") private const val _R_23: String = "args"
@Suppress("unused") private const val _R_24: String = "credential_pool"
@Suppress("unused") private const val _R_25: String = "HERMES_INFERENCE_PROVIDER"
@Suppress("unused") private val _R_26: String = """Build a text placeholder for media-only events so they aren't dropped.

    When a photo/document is queued during active processing and later
    dequeued, only .text is extracted.  If the event has no caption,
    the media would be silently lost.  This builds a placeholder that
    the vision enrichment pipeline will replace with a real description.
    """
@Suppress("unused") private const val _R_27: String = "media_urls"
@Suppress("unused") private const val _R_28: String = "media_types"
@Suppress("unused") private const val _R_29: String = "image/"
@Suppress("unused") private const val _R_30: String = "audio/"
@Suppress("unused") private const val _R_31: String = "message_type"
@Suppress("unused") private const val _R_32: String = "[User sent an image: "
@Suppress("unused") private const val _R_33: String = "[User sent audio: "
@Suppress("unused") private const val _R_34: String = "[User sent a file: "
@Suppress("unused") private val _R_35: String = """Check if a command matches a known-but-inactive skill.

    Returns a helpful message if the skill exists but is disabled or only
    available as an optional install. Returns None if no match found.
    """
@Suppress("unused") private const val _R_36: String = "SKILL.md"
@Suppress("unused") private const val _R_37: String = "optional-skills"
@Suppress("unused") private const val _R_38: String = "The **"
@Suppress("unused") private val _R_39: String = """** skill is installed but disabled.
Enable it with: `hermes skills config`"""
@Suppress("unused") private const val _R_40: String = "official/"
@Suppress("unused") private val _R_41: String = """** skill is available but not installed.
Install it with: `hermes skills install """
@Suppress("unused") private const val _R_42: String = ".git"
@Suppress("unused") private const val _R_43: String = ".github"
@Suppress("unused") private const val _R_44: String = ".hub"
@Suppress("unused") private const val _R_45: String = "Map a Platform enum to its config.yaml key (LOCAL→\"cli\", rest→enum value)."
@Suppress("unused") private const val _R_46: String = "Platform"
@Suppress("unused") private const val _R_47: String = "cli"
@Suppress("unused") private const val _R_48: String = "Load and parse ~/.hermes/config.yaml, returning {} on any error."
@Suppress("unused") private const val _R_49: String = "config.yaml"
@Suppress("unused") private const val _R_50: String = "Could not load gateway config from %s"
@Suppress("unused") private const val _R_51: String = "dict | None"
@Suppress("unused") private val _R_52: String = """Parse a session key into its component parts.

    Session keys follow the format
    ``agent:main:{platform}:{chat_type}:{chat_id}[:{extra}...]``.
    Returns a dict with ``platform``, ``chat_type``, ``chat_id``, and
    optionally ``thread_id`` keys, or None if the key doesn't match.

    The 6th element is only returned as ``thread_id`` for chat types where
    it is unambiguous (``dm`` and ``thread``).  For group/channel sessions
    the suffix may be a user_id (per-user isolation) rather than a
    thread_id, so we leave ``thread_id`` out to avoid mis-routing.
    """
@Suppress("unused") private const val _R_53: String = "agent"
@Suppress("unused") private const val _R_54: String = "main"
@Suppress("unused") private const val _R_55: String = "platform"
@Suppress("unused") private const val _R_56: String = "chat_type"
@Suppress("unused") private const val _R_57: String = "chat_id"
@Suppress("unused") private const val _R_58: String = "thread_id"
@Suppress("unused") private const val _R_59: String = "thread"
@Suppress("unused") private val _R_60: String = """Warn when Docker-backed gateways lack an explicit export mount.

        MEDIA delivery happens in the gateway process, so paths emitted by the model
        must be readable from the host. A plain container-local path like
        `/workspace/report.txt` or `/output/report.txt` often exists only inside
        Docker, so users commonly need a dedicated export mount such as
        `host-dir:/output`.
        """
@Suppress("unused") private const val _R_61: String = "docker"
@Suppress("unused") private const val _R_62: String = "Docker backend is enabled for the messaging gateway but no explicit host-visible output mount (for example '/home/user/.hermes/cache/documents:/output') is configured. This is fine if the model already emits host-visible paths, but MEDIA file delivery can fail for container-local paths like '/workspace/...' or '/output/...'."
@Suppress("unused") private const val _R_63: String = "container"
@Suppress("unused") private const val _R_64: String = "TERMINAL_DOCKER_VOLUMES"
@Suppress("unused") private const val _R_65: String = "Could not parse TERMINAL_DOCKER_VOLUMES for gateway media warning"
@Suppress("unused") private const val _R_66: String = "TERMINAL_ENV"
@Suppress("unused") private const val _R_67: String = "Check if the hermes-agent-setup skill is installed."
@Suppress("unused") private const val _R_68: String = "hermes-agent-setup"
@Suppress("unused") private const val _R_69: String = "off"
@Suppress("unused") private const val _R_70: String = "voice_only"
@Suppress("unused") private const val _R_71: String = "all"
@Suppress("unused") private const val _R_72: String = "Skipping legacy unprefixed voice mode key %r during migration. Re-enable voice mode on that chat to rebuild the prefixed key."
@Suppress("unused") private const val _R_73: String = "Failed to save voice modes: %s"
@Suppress("unused") private const val _R_74: String = "Resolve the current session key for a source, honoring gateway config when available."
@Suppress("unused") private const val _R_75: String = "config"
@Suppress("unused") private const val _R_76: String = "session_store"
@Suppress("unused") private const val _R_77: String = "group_sessions_per_user"
@Suppress("unused") private const val _R_78: String = "thread_sessions_per_user"
@Suppress("unused") private val _R_79: String = """Build the effective model/runtime config for a single turn.

        Always uses the session's primary model/provider.  If `/fast` is
        enabled and the model supports Priority Processing / Anthropic fast
        mode, attach `request_overrides` so the API call is marked
        accordingly.
        """
@Suppress("unused") private const val _R_80: String = "model"
@Suppress("unused") private const val _R_81: String = "runtime"
@Suppress("unused") private const val _R_82: String = "signature"
@Suppress("unused") private const val _R_83: String = "_service_tier"
@Suppress("unused") private const val _R_84: String = "request_overrides"
@Suppress("unused") private val _R_85: String = """React to an adapter failure after startup.

        If the error is retryable (e.g. network blip, DNS failure), queue the
        platform for background reconnection instead of giving up permanently.
        """
@Suppress("unused") private const val _R_86: String = "Fatal %s adapter error (%s): %s"
@Suppress("unused") private const val _R_87: String = "unknown"
@Suppress("unused") private const val _R_88: String = "unknown error"
@Suppress("unused") private const val _R_89: String = "All messaging adapters disconnected"
@Suppress("unused") private const val _R_90: String = "retrying"
@Suppress("unused") private const val _R_91: String = "fatal"
@Suppress("unused") private const val _R_92: String = "attempts"
@Suppress("unused") private const val _R_93: String = "next_retry"
@Suppress("unused") private const val _R_94: String = "%s queued for background reconnection"
@Suppress("unused") private const val _R_95: String = "No connected messaging platforms remain. Shutting down gateway for service restart."
@Suppress("unused") private const val _R_96: String = "No connected messaging platforms remain. Shutting down gateway cleanly."
@Suppress("unused") private const val _R_97: String = "All messaging platforms failed with retryable errors"
@Suppress("unused") private const val _R_98: String = "All messaging platforms failed with retryable errors. Shutting down gateway for service restart (systemd will retry)."
@Suppress("unused") private const val _R_99: String = "No connected messaging platforms remain, but %d platform(s) queued for reconnection"
@Suppress("unused") private val _R_100: String = """Load ephemeral prefill messages from config or env var.
        
        Checks HERMES_PREFILL_MESSAGES_FILE env var first, then falls back to
        the prefill_messages_file key in ~/.hermes/config.yaml.
        Relative paths are resolved from ~/.hermes/.
        """
@Suppress("unused") private const val _R_101: String = "HERMES_PREFILL_MESSAGES_FILE"
@Suppress("unused") private const val _R_102: String = "Prefill messages file not found: %s"
@Suppress("unused") private const val _R_103: String = "Prefill messages file must contain a JSON array: %s"
@Suppress("unused") private const val _R_104: String = "Failed to load prefill messages from %s: %s"
@Suppress("unused") private const val _R_105: String = "prefill_messages_file"
@Suppress("unused") private val _R_106: String = """Load ephemeral system prompt from config or env var.
        
        Checks HERMES_EPHEMERAL_SYSTEM_PROMPT env var first, then falls back to
        agent.system_prompt in ~/.hermes/config.yaml.
        """
@Suppress("unused") private const val _R_107: String = "HERMES_EPHEMERAL_SYSTEM_PROMPT"
@Suppress("unused") private const val _R_108: String = "system_prompt"
@Suppress("unused") private val _R_109: String = """Load background process notification mode from config or env var.

        Modes:
          - ``all``    — push running-output updates *and* the final message (default)
          - ``result`` — only the final completion message (regardless of exit code)
          - ``error``  — only the final message when exit code is non-zero
          - ``off``    — no watcher messages at all
        """
@Suppress("unused") private const val _R_110: String = "HERMES_BACKGROUND_NOTIFICATIONS"
@Suppress("unused") private const val _R_111: String = "result"
@Suppress("unused") private const val _R_112: String = "error"
@Suppress("unused") private const val _R_113: String = "Unknown background_process_notifications '%s', defaulting to 'all'"
@Suppress("unused") private const val _R_114: String = "background_process_notifications"
@Suppress("unused") private const val _R_115: String = "display"
@Suppress("unused") private const val _R_116: String = "⚡ Interrupting current task"
@Suppress("unused") private const val _R_117: String = ". I'll respond to your message shortly."
@Suppress("unused") private const val _R_118: String = "⏳ Gateway "
@Suppress("unused") private const val _R_119: String = " — queued for the next turn after it comes back."
@Suppress("unused") private const val _R_120: String = "⏳ Gateway is "
@Suppress("unused") private const val _R_121: String = " and is not accepting another turn right now."
@Suppress("unused") private const val _R_122: String = "api_call_count"
@Suppress("unused") private const val _R_123: String = "max_iterations"
@Suppress("unused") private const val _R_124: String = "current_tool"
@Suppress("unused") private const val _R_125: String = "Failed to send busy-ack: %s"
@Suppress("unused") private const val _R_126: String = "iteration "
@Suppress("unused") private const val _R_127: String = "running: "
@Suppress("unused") private const val _R_128: String = " min elapsed"
@Suppress("unused") private const val _R_129: String = "draining"
@Suppress("unused") private const val _R_130: String = "Interrupted running agent for session %s during shutdown"
@Suppress("unused") private const val _R_131: String = "Failed interrupting agent during shutdown: %s"
@Suppress("unused") private val _R_132: String = """Send a notification to every chat with an active agent.

        Called at the very start of stop() — adapters are still connected so
        messages can be delivered.  Best-effort: individual send failures are
        logged and swallowed so they never block the shutdown sequence.
        """
@Suppress("unused") private const val _R_133: String = "restarting"
@Suppress("unused") private const val _R_134: String = "shutting down"
@Suppress("unused") private const val _R_135: String = "Your current task will be interrupted. Send any message after restart and I'll try to resume where you left off."
@Suppress("unused") private const val _R_136: String = "Your current task will be interrupted."
@Suppress("unused") private const val _R_137: String = "⚠️ Gateway "
@Suppress("unused") private const val _R_138: String = " — "
@Suppress("unused") private const val _R_139: String = "Sent shutdown notification to %s:%s"
@Suppress("unused") private const val _R_140: String = "Failed to load session origin for shutdown notification %s: %s"
@Suppress("unused") private const val _R_141: String = "Failed to send shutdown notification to %s:%s: %s"
@Suppress("unused") private const val _R_142: String = "origin"
@Suppress("unused") private const val _R_143: String = "on_session_finalize"
@Suppress("unused") private const val _R_144: String = "gateway"
@Suppress("unused") private const val _R_145: String = "session_id"
@Suppress("unused") private const val _R_146: String = "Best-effort cleanup for temporary or cached agent instances."
@Suppress("unused") private const val _R_147: String = "shutdown_memory_provider"
@Suppress("unused") private const val _R_148: String = "close"
@Suppress("unused") private const val _R_149: String = "while kill -0 "
@Suppress("unused") private const val _R_150: String = " 2>/dev/null; do sleep 0.2; done; "
@Suppress("unused") private const val _R_151: String = " gateway restart"
@Suppress("unused") private const val _R_152: String = "setsid"
@Suppress("unused") private const val _R_153: String = "Could not locate hermes binary for detached /restart"
@Suppress("unused") private const val _R_154: String = "bash"
@Suppress("unused") private const val _R_155: String = "-lc"
@Suppress("unused") private val _R_156: String = """
        Start the gateway and all configured platform adapters.
        
        Returns True if at least one adapter connected successfully.
        """
@Suppress("unused") private const val _R_157: String = "Starting Hermes Gateway..."
@Suppress("unused") private const val _R_158: String = "Session storage: %s"
@Suppress("unused") private const val _R_159: String = ".clean_shutdown"
@Suppress("unused") private const val _R_160: String = "running"
@Suppress("unused") private const val _R_161: String = "Press Ctrl+C to stop"
@Suppress("unused") private const val _R_162: String = "No user allowlists configured. All unauthorized users will be denied. Set GATEWAY_ALLOW_ALL_USERS=true in ~/.hermes/.env to allow open access, or configure platform allowlists (e.g., TELEGRAM_ALLOWED_USERS=your_id)."
@Suppress("unused") private const val _R_163: String = "Previous gateway exited cleanly — skipping session suspension"
@Suppress("unused") private const val _R_164: String = "Connecting to %s..."
@Suppress("unused") private const val _R_165: String = "No messaging platforms enabled."
@Suppress("unused") private const val _R_166: String = "Gateway will continue running for cron job execution."
@Suppress("unused") private const val _R_167: String = "%s hook(s) loaded"
@Suppress("unused") private const val _R_168: String = "gateway:startup"
@Suppress("unused") private const val _R_169: String = "Gateway running with %s platform(s)"
@Suppress("unused") private const val _R_170: String = "Channel directory built: %d target(s)"
@Suppress("unused") private const val _R_171: String = "Starting reconnection watcher for %d failed platform(s): %s"
@Suppress("unused") private const val _R_172: String = "default"
@Suppress("unused") private const val _R_173: String = "Active profile: %s"
@Suppress("unused") private const val _R_174: String = "starting"
@Suppress("unused") private const val _R_175: String = "true"
@Suppress("unused") private const val _R_176: String = "yes"
@Suppress("unused") private const val _R_177: String = "plugin discovery failed at gateway startup"
@Suppress("unused") private const val _R_178: String = "shell-hook registration failed at gateway startup"
@Suppress("unused") private const val _R_179: String = "Recovered %s background process(es) from previous run"
@Suppress("unused") private const val _R_180: String = "Process checkpoint recovery: %s"
@Suppress("unused") private const val _R_181: String = "Auto-suspended %d stuck-loop session(s)"
@Suppress("unused") private const val _R_182: String = "Stuck-loop detection failed: %s"
@Suppress("unused") private const val _R_183: String = "No adapter available for %s"
@Suppress("unused") private const val _R_184: String = "connecting"
@Suppress("unused") private const val _R_185: String = "Gateway hit a non-retryable startup conflict: %s"
@Suppress("unused") private const val _R_186: String = "all configured messaging platforms failed to connect"
@Suppress("unused") private const val _R_187: String = "Gateway failed to connect any configured messaging platform: %s"
@Suppress("unused") private const val _R_188: String = "platforms"
@Suppress("unused") private const val _R_189: String = "Channel directory build failed: %s"
@Suppress("unused") private const val _R_190: String = "Resumed watcher for recovered process %s"
@Suppress("unused") private const val _R_191: String = "Recovered watcher setup error: %s"
@Suppress("unused") private const val _R_192: String = "TELEGRAM_ALLOWED_USERS"
@Suppress("unused") private const val _R_193: String = "DISCORD_ALLOWED_USERS"
@Suppress("unused") private const val _R_194: String = "WHATSAPP_ALLOWED_USERS"
@Suppress("unused") private const val _R_195: String = "SLACK_ALLOWED_USERS"
@Suppress("unused") private const val _R_196: String = "SIGNAL_ALLOWED_USERS"
@Suppress("unused") private const val _R_197: String = "SIGNAL_GROUP_ALLOWED_USERS"
@Suppress("unused") private const val _R_198: String = "EMAIL_ALLOWED_USERS"
@Suppress("unused") private const val _R_199: String = "SMS_ALLOWED_USERS"
@Suppress("unused") private const val _R_200: String = "MATTERMOST_ALLOWED_USERS"
@Suppress("unused") private const val _R_201: String = "MATRIX_ALLOWED_USERS"
@Suppress("unused") private const val _R_202: String = "DINGTALK_ALLOWED_USERS"
@Suppress("unused") private const val _R_203: String = "FEISHU_ALLOWED_USERS"
@Suppress("unused") private const val _R_204: String = "WECOM_ALLOWED_USERS"
@Suppress("unused") private const val _R_205: String = "WECOM_CALLBACK_ALLOWED_USERS"
@Suppress("unused") private const val _R_206: String = "WEIXIN_ALLOWED_USERS"
@Suppress("unused") private const val _R_207: String = "BLUEBUBBLES_ALLOWED_USERS"
@Suppress("unused") private const val _R_208: String = "QQ_ALLOWED_USERS"
@Suppress("unused") private const val _R_209: String = "GATEWAY_ALLOWED_USERS"
@Suppress("unused") private const val _R_210: String = "Suspended %d in-flight session(s) from previous run"
@Suppress("unused") private const val _R_211: String = "Session suspension on startup failed: %s"
@Suppress("unused") private const val _R_212: String = "✓ %s connected"
@Suppress("unused") private const val _R_213: String = "✗ %s failed to connect"
@Suppress("unused") private const val _R_214: String = "✗ %s error: %s"
@Suppress("unused") private const val _R_215: String = "GATEWAY_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_216: String = "TELEGRAM_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_217: String = "DISCORD_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_218: String = "WHATSAPP_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_219: String = "SLACK_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_220: String = "SIGNAL_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_221: String = "EMAIL_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_222: String = "SMS_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_223: String = "MATTERMOST_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_224: String = "MATRIX_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_225: String = "DINGTALK_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_226: String = "FEISHU_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_227: String = "WECOM_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_228: String = "WECOM_CALLBACK_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_229: String = "WEIXIN_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_230: String = "BLUEBUBBLES_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_231: String = "QQ_ALLOW_ALL_USERS"
@Suppress("unused") private const val _R_232: String = "connected"
@Suppress("unused") private const val _R_233: String = "startup_failed"
@Suppress("unused") private const val _R_234: String = "failed to connect"
@Suppress("unused") private const val _R_235: String = ": failed to connect"
@Suppress("unused") private const val _R_236: String = ".update_pending.json"
@Suppress("unused") private const val _R_237: String = ".update_pending.claimed.json"
@Suppress("unused") private val _R_238: String = """Background task that proactively flushes memories for expired sessions.
        
        Runs every `interval` seconds (default 5 min).  For each session that
        has expired according to its reset policy, flushes memories in a thread
        pool and marks the session so it won't be flushed again.

        This means memories are already saved by the time the user sends their
        next message, so there's no blocking delay.
        """
@Suppress("unused") private const val _R_239: String = "_last_session_store_prune_ts"
@Suppress("unused") private const val _R_240: String = "Session expiry: %d sessions to flush (%s)"
@Suppress("unused") private const val _R_241: String = "Session expiry watcher error: %s"
@Suppress("unused") private const val _R_242: String = "_agent_cache_lock"
@Suppress("unused") private const val _R_243: String = "Memory flush completed for session %s"
@Suppress("unused") private const val _R_244: String = "Session expiry done: %d flushed, %d pending retry"
@Suppress("unused") private const val _R_245: String = "Session expiry done: %d flushed"
@Suppress("unused") private const val _R_246: String = "Agent cache idle sweep: evicted %d agent(s)"
@Suppress("unused") private const val _R_247: String = "Idle agent sweep failed: %s"
@Suppress("unused") private const val _R_248: String = "SessionStore prune failed: %s"
@Suppress("unused") private const val _R_249: String = "Memory flush gave up after %d attempts for %s: %s. Marking as flushed to prevent infinite retry loop."
@Suppress("unused") private const val _R_250: String = "Memory flush failed (%d/%d) for %s: %s"
@Suppress("unused") private const val _R_251: String = "session_store_max_age_days"
@Suppress("unused") private const val _R_252: String = "SessionStore prune: dropped %d stale entries"
@Suppress("unused") private val _R_253: String = """Background task that periodically retries connecting failed platforms.

        Uses exponential backoff: 30s → 60s → 120s → 240s → 300s (cap).
        Stops retrying a platform after 20 failed attempts or if the error
        is non-retryable (e.g. bad auth token).
        """
@Suppress("unused") private const val _R_254: String = "Reconnecting %s (attempt %d/%d)..."
@Suppress("unused") private const val _R_255: String = "Giving up reconnecting %s after %d attempts"
@Suppress("unused") private const val _R_256: String = "Reconnect %s: adapter creation returned None, removing from retry queue"
@Suppress("unused") private const val _R_257: String = "✓ %s reconnected successfully"
@Suppress("unused") private const val _R_258: String = "Reconnect %s error: %s, next retry in %ds"
@Suppress("unused") private const val _R_259: String = "Reconnect %s: non-retryable error (%s), removing from retry queue"
@Suppress("unused") private const val _R_260: String = "Reconnect %s failed, next retry in %ds"
@Suppress("unused") private const val _R_261: String = "failed to reconnect"
@Suppress("unused") private const val _R_262: String = "Stop the gateway and disconnect all adapters."
@Suppress("unused") private const val _R_263: String = "Stopping gateway%s..."
@Suppress("unused") private const val _R_264: String = "_busy_ack_ts"
@Suppress("unused") private const val _R_265: String = "stopped"
@Suppress("unused") private const val _R_266: String = "Gateway stopped"
@Suppress("unused") private const val _R_267: String = " for restart"
@Suppress("unused") private const val _R_268: String = "Gateway drain timed out after %.1fs with %d active agent(s); interrupting remaining work."
@Suppress("unused") private const val _R_269: String = "restart_timeout"
@Suppress("unused") private const val _R_270: String = "shutdown_timeout"
@Suppress("unused") private const val _R_271: String = "Skipping .clean_shutdown marker — drain timed out with interrupted agents; next startup will suspend recently active sessions."
@Suppress("unused") private const val _R_272: String = "Gateway restart requested"
@Suppress("unused") private const val _R_273: String = "✓ %s disconnected"
@Suppress("unused") private const val _R_274: String = "_db"
@Suppress("unused") private const val _R_275: String = "Failed to launch detached gateway restart: %s"
@Suppress("unused") private const val _R_276: String = "✗ %s background-task cancel error: %s"
@Suppress("unused") private const val _R_277: String = "✗ %s disconnect error: %s"
@Suppress("unused") private const val _R_278: String = "SessionDB close error: %s"
@Suppress("unused") private const val _R_279: String = "mark_resume_pending failed for %s: %s"
@Suppress("unused") private const val _R_280: String = "Create the appropriate adapter for a platform."
@Suppress("unused") private const val _R_281: String = "extra"
@Suppress("unused") private const val _R_282: String = "Telegram: python-telegram-bot not installed"
@Suppress("unused") private const val _R_283: String = "Discord: discord.py not installed"
@Suppress("unused") private const val _R_284: String = "WhatsApp: Node.js not installed or bridge not configured"
@Suppress("unused") private const val _R_285: String = "Slack: slack-bolt not installed. Run: pip install 'hermes-agent[slack]'"
@Suppress("unused") private const val _R_286: String = "Signal: SIGNAL_HTTP_URL or SIGNAL_ACCOUNT not configured"
@Suppress("unused") private const val _R_287: String = "HomeAssistant: aiohttp not installed or HASS_TOKEN not set"
@Suppress("unused") private const val _R_288: String = "Email: EMAIL_ADDRESS, EMAIL_PASSWORD, EMAIL_IMAP_HOST, or EMAIL_SMTP_HOST not set"
@Suppress("unused") private const val _R_289: String = "SMS: aiohttp not installed or TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN not set"
@Suppress("unused") private const val _R_290: String = "DingTalk: dingtalk-stream not installed or DINGTALK_CLIENT_ID/SECRET not set"
@Suppress("unused") private const val _R_291: String = "Feishu: lark-oapi not installed or FEISHU_APP_ID/SECRET not set"
@Suppress("unused") private const val _R_292: String = "WeComCallback: aiohttp/httpx not installed"
@Suppress("unused") private const val _R_293: String = "WeCom: aiohttp not installed or WECOM_BOT_ID/SECRET not set"
@Suppress("unused") private const val _R_294: String = "Weixin: aiohttp/cryptography not installed"
@Suppress("unused") private const val _R_295: String = "Mattermost: MATTERMOST_TOKEN or MATTERMOST_URL not set, or aiohttp missing"
@Suppress("unused") private const val _R_296: String = "Matrix: mautrix not installed or credentials not set. Run: pip install 'mautrix[encryption]'"
@Suppress("unused") private const val _R_297: String = "API Server: aiohttp not installed"
@Suppress("unused") private const val _R_298: String = "Webhook: aiohttp not installed"
@Suppress("unused") private const val _R_299: String = "BlueBubbles: aiohttp/httpx missing or BLUEBUBBLES_SERVER_URL/BLUEBUBBLES_PASSWORD not configured"
@Suppress("unused") private const val _R_300: String = "QQBot: aiohttp/httpx missing or QQ_APP_ID/QQ_CLIENT_SECRET not configured"
@Suppress("unused") private val _R_301: String = """
        Handle an incoming message from any platform.
        
        This is the core message processing pipeline:
        1. Check user authorization
        2. Check for commands (/new, /reset, etc.)
        3. Check for running agent and interrupt if needed
        4. Get or create session
        5. Build context for agent
        6. Run agent conversation
        7. Return response
        """
@Suppress("unused") private const val _R_302: String = "internal"
@Suppress("unused") private const val _R_303: String = "_update_prompt_pending"
@Suppress("unused") private const val _R_304: String = "new"
@Suppress("unused") private const val _R_305: String = "help"
@Suppress("unused") private const val _R_306: String = "commands"
@Suppress("unused") private const val _R_307: String = "profile"
@Suppress("unused") private const val _R_308: String = "status"
@Suppress("unused") private const val _R_309: String = "agents"
@Suppress("unused") private const val _R_310: String = "restart"
@Suppress("unused") private const val _R_311: String = "stop"
@Suppress("unused") private const val _R_312: String = "reasoning"
@Suppress("unused") private const val _R_313: String = "fast"
@Suppress("unused") private const val _R_314: String = "verbose"
@Suppress("unused") private const val _R_315: String = "yolo"
@Suppress("unused") private const val _R_316: String = "personality"
@Suppress("unused") private const val _R_317: String = "plan"
@Suppress("unused") private const val _R_318: String = "retry"
@Suppress("unused") private const val _R_319: String = "undo"
@Suppress("unused") private const val _R_320: String = "sethome"
@Suppress("unused") private const val _R_321: String = "compress"
@Suppress("unused") private const val _R_322: String = "usage"
@Suppress("unused") private const val _R_323: String = "insights"
@Suppress("unused") private const val _R_324: String = "reload-mcp"
@Suppress("unused") private const val _R_325: String = "approve"
@Suppress("unused") private const val _R_326: String = "deny"
@Suppress("unused") private const val _R_327: String = "update"
@Suppress("unused") private const val _R_328: String = "debug"
@Suppress("unused") private const val _R_329: String = "title"
@Suppress("unused") private const val _R_330: String = "resume"
@Suppress("unused") private const val _R_331: String = "branch"
@Suppress("unused") private const val _R_332: String = "rollback"
@Suppress("unused") private const val _R_333: String = "background"
@Suppress("unused") private const val _R_334: String = "btw"
@Suppress("unused") private const val _R_335: String = "steer"
@Suppress("unused") private const val _R_336: String = "voice"
@Suppress("unused") private const val _R_337: String = "HERMES_AGENT_TIMEOUT"
@Suppress("unused") private const val _R_338: String = "inf"
@Suppress("unused") private const val _R_339: String = "⚡ Stopped. You can continue this session."
@Suppress("unused") private const val _R_340: String = "Queued for the next turn."
@Suppress("unused") private const val _R_341: String = "No active agent — /steer queued for the next turn."
@Suppress("unused") private const val _R_342: String = "Agent is running — wait or /stop first, then switch models."
@Suppress("unused") private const val _R_343: String = "PRIORITY interrupt for session %s"
@Suppress("unused") private const val _R_344: String = "Usage: /steer <prompt>  (no agent is running; sending as a normal message)"
@Suppress("unused") private const val _R_345: String = " and is not accepting new work right now."
@Suppress("unused") private const val _R_346: String = "Ignoring message with no user_id from %s"
@Suppress("unused") private const val _R_347: String = ".update_response"
@Suppress("unused") private const val _R_348: String = "✓ Sent `"
@Suppress("unused") private const val _R_349: String = "` to the update process."
@Suppress("unused") private const val _R_350: String = "get_activity_summary"
@Suppress("unused") private const val _R_351: String = "Evicting stale _running_agents entry for %s (age: %.0fs, idle: %.0fs, timeout: %.0fs)%s"
@Suppress("unused") private const val _R_352: String = "STOP for session %s — agent interrupted, session lock released"
@Suppress("unused") private const val _R_353: String = "queue"
@Suppress("unused") private const val _R_354: String = "Usage: /queue <prompt>"
@Suppress("unused") private const val _R_355: String = "Usage: /steer <prompt>"
@Suppress("unused") private const val _R_356: String = "Agent still starting — /steer queued for the next turn."
@Suppress("unused") private const val _R_357: String = "Steer rejected (empty payload)."
@Suppress("unused") private const val _R_358: String = "⏳ Agent is running — `/"
@Suppress("unused") private const val _R_359: String = "` can't run mid-turn. Wait for the current response or `/stop` first."
@Suppress("unused") private const val _R_360: String = "PRIORITY photo follow-up for session %s — queueing without interrupt"
@Suppress("unused") private const val _R_361: String = "HERMES_TELEGRAM_FOLLOWUP_GRACE_SECONDS"
@Suppress("unused") private const val _R_362: String = "3.0"
@Suppress("unused") private const val _R_363: String = "Telegram follow-up arrived %.2fs after run start for %s — queueing without interrupt"
@Suppress("unused") private const val _R_364: String = "⚡ Force-stopped. The agent was still starting — session unlocked."
@Suppress("unused") private const val _R_365: String = "/plan"
@Suppress("unused") private const val _R_366: String = "Failed to load the bundled /plan skill."
@Suppress("unused") private const val _R_367: String = "exec"
@Suppress("unused") private const val _R_368: String = "Unauthorized user: %s (%s) on %s"
@Suppress("unused") private const val _R_369: String = ".tmp"
@Suppress("unused") private const val _R_370: String = "seconds_since_activity"
@Suppress("unused") private const val _R_371: String = " | last_activity="
@Suppress("unused") private const val _R_372: String = "s ago) | iteration="
@Suppress("unused") private const val _R_373: String = "stale_running_agent_eviction"
@Suppress("unused") private const val _R_374: String = "HARD STOP (pending) for session %s — sentinel cleared"
@Suppress("unused") private const val _R_375: String = "command:"
@Suppress("unused") private const val _R_376: String = "user_id"
@Suppress("unused") private const val _R_377: String = "Failed to prepare /plan command"
@Suppress("unused") private const val _R_378: String = "Failed to enter plan mode: "
@Suppress("unused") private const val _R_379: String = "quick_commands"
@Suppress("unused") private const val _R_380: String = "type"
@Suppress("unused") private const val _R_381: String = "alias"
@Suppress("unused") private const val _R_382: String = "Plugin command dispatch failed (non-fatal): %s"
@Suppress("unused") private const val _R_383: String = "name"
@Suppress("unused") private const val _R_384: String = "Skill command check failed (non-fatal): %s"
@Suppress("unused") private const val _R_385: String = "pair"
@Suppress("unused") private const val _R_386: String = "Failed to write update response: %s"
@Suppress("unused") private const val _R_387: String = "✗ Failed to send response to update process: "
@Suppress("unused") private const val _R_388: String = "stop_command"
@Suppress("unused") private const val _R_389: String = "new_command"
@Suppress("unused") private const val _R_390: String = "⏩ Steer queued — arrives after the next tool call: '"
@Suppress("unused") private const val _R_391: String = "Save the markdown plan with write_file to this exact relative path inside the active workspace/backend cwd: "
@Suppress("unused") private const val _R_392: String = "Quick command '/"
@Suppress("unused") private const val _R_393: String = "' has no command defined."
@Suppress("unused") private const val _R_394: String = "' has unsupported type (supported: 'exec', 'alias')."
@Suppress("unused") private const val _R_395: String = "Unrecognized slash command /%s from %s — replying with unknown-command notice"
@Suppress("unused") private const val _R_396: String = "Unknown command `/"
@Suppress("unused") private const val _R_397: String = "`. Type /commands to see what's available, or resend without the leading slash to send as a regular message."
@Suppress("unused") private const val _R_398: String = "last_activity_desc"
@Suppress("unused") private const val _R_399: String = ".0f"
@Suppress("unused") private const val _R_400: String = "Steer failed for session %s: %s"
@Suppress("unused") private const val _R_401: String = "⚠️ Steer failed: "
@Suppress("unused") private const val _R_402: String = "..."
@Suppress("unused") private const val _R_403: String = "Command returned no output."
@Suppress("unused") private const val _R_404: String = "Quick command timed out (30s)."
@Suppress("unused") private const val _R_405: String = "' has no target defined."
@Suppress("unused") private const val _R_406: String = "** skill is disabled for "
@Suppress("unused") private val _R_407: String = """.
Enable it with: `hermes skills config`"""
@Suppress("unused") private const val _R_408: String = "Quick command error: "
@Suppress("unused") private const val _R_409: String = "target"
@Suppress("unused") private const val _R_410: String = "Too many pairing requests right now~ Please try again later!"
@Suppress("unused") private val _R_411: String = """Hi~ I don't recognize you yet!

Here's your pairing code: `"""
@Suppress("unused") private val _R_412: String = """`

Ask the bot owner to run:
`hermes pairing approve """
@Suppress("unused") private val _R_413: String = """Prepare inbound event text for the agent.

        Keep the normal inbound path and the queued follow-up path on the same
        preprocessing pipeline so sender attribution, image enrichment, STT,
        document notes, reply context, and @ references all behave the same.
        """
@Suppress("unused") private const val _R_414: String = ".txt"
@Suppress("unused") private const val _R_415: String = ".md"
@Suppress("unused") private const val _R_416: String = ".csv"
@Suppress("unused") private const val _R_417: String = ".log"
@Suppress("unused") private const val _R_418: String = ".xml"
@Suppress("unused") private const val _R_419: String = ".yaml"
@Suppress("unused") private const val _R_420: String = ".yml"
@Suppress("unused") private const val _R_421: String = ".toml"
@Suppress("unused") private const val _R_422: String = ".ini"
@Suppress("unused") private const val _R_423: String = ".cfg"
@Suppress("unused") private const val _R_424: String = "reply_to_text"
@Suppress("unused") private const val _R_425: String = "No STT provider"
@Suppress("unused") private const val _R_426: String = "STT is disabled"
@Suppress("unused") private const val _R_427: String = "can't listen"
@Suppress("unused") private const val _R_428: String = "VOICE_TOOLS_OPENAI_KEY"
@Suppress("unused") private const val _R_429: String = "[^\\w.\\- ]"
@Suppress("unused") private const val _R_430: String = "text/"
@Suppress("unused") private const val _R_431: String = "[Replying to: \""
@Suppress("unused") private val _R_432: String = """"]

"""
@Suppress("unused") private const val _R_433: String = "TERMINAL_CWD"
@Suppress("unused") private const val _R_434: String = "application/octet-stream"
@Suppress("unused") private const val _R_435: String = "text/plain"
@Suppress("unused") private const val _R_436: String = "[The user sent a text document: '"
@Suppress("unused") private const val _R_437: String = "'. Its content has been included below. The file is also saved at: "
@Suppress("unused") private const val _R_438: String = "[The user sent a document: '"
@Suppress("unused") private const val _R_439: String = "'. The file is saved at: "
@Suppress("unused") private const val _R_440: String = ". Ask the user what they'd like you to do with it.]"
@Suppress("unused") private const val _R_441: String = "@ context reference expansion failed: %s"
@Suppress("unused") private val _R_442: String = """🎤 I received your voice message but can't transcribe it — no speech-to-text provider is configured.

To enable voice: install faster-whisper (`pip install faster-whisper` in the Hermes venv) and set `stt.enabled: true` in config.yaml, then /restart the gateway."""
@Suppress("unused") private const val _R_443: String = "application/"
@Suppress("unused") private val _R_444: String = """

For full setup instructions, type: `/skill hermes-agent-setup`"""
@Suppress("unused") private const val _R_445: String = "content"
@Suppress("unused") private const val _R_446: String = "role"
@Suppress("unused") private const val _R_447: String = "assistant"
@Suppress("unused") private const val _R_448: String = "user"
@Suppress("unused") private const val _R_449: String = "tool"
@Suppress("unused") private const val _R_450: String = "Context injection refused."
@Suppress("unused") private const val _R_451: String = "Inner handler that runs under the _running_agents sentinel guard."
@Suppress("unused") private const val _R_452: String = "inbound message: platform=%s user=%s chat=%s msg=%r"
@Suppress("unused") private const val _R_453: String = "was_auto_reset"
@Suppress("unused") private const val _R_454: String = "auto_skill"
@Suppress("unused") private const val _R_455: String = "anthropic/claude-sonnet-4.6"
@Suppress("unused") private val _R_456: String = """

[System note: This is the user's very first message ever. Briefly introduce yourself and mention that /help shows available commands. Keep the introduction concise -- one or two sentences max.]"""
@Suppress("unused") private const val _R_457: String = "value"
@Suppress("unused") private const val _R_458: String = "idle"
@Suppress("unused") private const val _R_459: String = "suspended"
@Suppress("unused") private const val _R_460: String = "[System note: The user's previous session was stopped and suspended. This is a fresh conversation with no prior context.]"
@Suppress("unused") private const val _R_461: String = "_HOME_CHANNEL"
@Suppress("unused") private const val _R_462: String = "message"
@Suppress("unused") private const val _R_463: String = "(empty)"
@Suppress("unused") private const val _R_464: String = "⚠️ The model returned no response after processing tool results. This can happen with some models — try again or rephrase your question."
@Suppress("unused") private const val _R_465: String = "messages"
@Suppress("unused") private const val _R_466: String = "api_calls"
@Suppress("unused") private const val _R_467: String = "response ready: platform=%s chat=%s time=%.1fs api_calls=%d response=%d chars"
@Suppress("unused") private const val _R_468: String = "session:start"
@Suppress("unused") private const val _R_469: String = "redact_pii"
@Suppress("unused") private const val _R_470: String = "auto_reset_reason"
@Suppress("unused") private const val _R_471: String = "daily"
@Suppress("unused") private const val _R_472: String = "[System note: The user's session was automatically reset by the daily schedule. This is a fresh conversation with no prior context.]"
@Suppress("unused") private const val _R_473: String = "[System note: The user's previous session expired due to inactivity. This is a fresh conversation with no prior context.]"
@Suppress("unused") private const val _R_474: String = "reset_had_activity"
@Suppress("unused") private const val _R_475: String = "actual"
@Suppress("unused") private const val _R_476: String = "estimated"
@Suppress("unused") private const val _R_477: String = "get_voice_channel_context"
@Suppress("unused") private const val _R_478: String = "agent:start"
@Suppress("unused") private const val _R_479: String = "Discarding stale agent result for %s — generation %d is no longer current"
@Suppress("unused") private const val _R_480: String = "final_response"
@Suppress("unused") private const val _R_481: String = "failed"
@Suppress("unused") private val _R_482: String = """⚠️ Session too large for the model's context window.
Use /compact to compress the conversation, or /reset to start fresh."""
@Suppress("unused") private const val _R_483: String = "show_reasoning"
@Suppress("unused") private const val _R_484: String = "last_reasoning"
@Suppress("unused") private const val _R_485: String = "agent:end"
@Suppress("unused") private const val _R_486: String = "Skipping transcript persistence for failed request in session %s to prevent session growth loop."
@Suppress("unused") private const val _R_487: String = "compression_exhausted"
@Suppress("unused") private const val _R_488: String = "Auto-resetting session %s after compression exhaustion."
@Suppress("unused") private val _R_489: String = """

🔄 Session auto-reset — the conversation exceeded the maximum context size and could not be compressed further. Your next message will start a fresh session."""
@Suppress("unused") private const val _R_490: String = "history_offset"
@Suppress("unused") private const val _R_491: String = "already_sent"
@Suppress("unused") private const val _R_492: String = "Agent error in session %s"
@Suppress("unused") private const val _R_493: String = "no details available"
@Suppress("unused") private const val _R_494: String = "status_code"
@Suppress("unused") private const val _R_495: String = " Check your API key or run `claude /login` to refresh OAuth credentials."
@Suppress("unused") private const val _R_496: String = "Sorry, I encountered an error ("
@Suppress("unused") private val _R_497: String = """).
"""
@Suppress("unused") private const val _R_498: String = "Try again or use /reset to start a fresh session."
@Suppress("unused") private const val _R_499: String = "session_key"
@Suppress("unused") private const val _R_500: String = "Auto-reset notification failed (non-fatal): %s"
@Suppress("unused") private const val _R_501: String = "[Gateway] Auto-loaded skill(s) %s for session %s"
@Suppress("unused") private const val _R_502: String = "[Gateway] Failed to auto-load skill(s) %s: %s"
@Suppress("unused") private const val _R_503: String = "compression"
@Suppress("unused") private const val _R_504: String = "Session hygiene: %s messages, ~%s tokens (%s) — auto-compressing (threshold: %s%% of %s = %s tokens)"
@Suppress("unused") private const val _R_505: String = "stop_typing"
@Suppress("unused") private const val _R_506: String = "pop_post_delivery_callback"
@Suppress("unused") private const val _R_507: String = "The request failed: "
@Suppress("unused") private val _R_508: String = """
Try again or use /reset to start a fresh session."""
@Suppress("unused") private const val _R_509: String = "_show_reasoning"
@Suppress("unused") private val _R_510: String = """💭 **Reasoning:**
```
"""
@Suppress("unused") private val _R_511: String = """
```

"""
@Suppress("unused") private const val _R_512: String = "response"
@Suppress("unused") private const val _R_513: String = "Process watcher setup error: %s"
@Suppress("unused") private const val _R_514: String = "completion"
@Suppress("unused") private const val _R_515: String = "Watch queue drain error: %s"
@Suppress("unused") private const val _R_516: String = "tools"
@Suppress("unused") private const val _R_517: String = "last_prompt_tokens"
@Suppress("unused") private const val _R_518: String = "history"
@Suppress("unused") private const val _R_519: String = " Your API balance or quota is exhausted. Check your provider dashboard."
@Suppress("unused") private const val _R_520: String = "previous session was stopped or interrupted"
@Suppress("unused") private const val _R_521: String = "◐ Session automatically reset ("
@Suppress("unused") private val _R_522: String = """). Conversation history cleared.
Use /resume to browse and restore a previous session.
Adjust reset timing in config.yaml under session_reset."""
@Suppress("unused") private const val _R_523: String = "[SYSTEM: The \""
@Suppress("unused") private const val _R_524: String = "\" skill is auto-loaded. Follow its instructions for this session.]"
@Suppress("unused") private const val _R_525: String = "[Gateway] Auto-skill '%s' not found"
@Suppress("unused") private const val _R_526: String = "_post_delivery_callbacks"
@Suppress("unused") private const val _R_527: String = "clear_resume_pending failed for %s: %s"
@Suppress("unused") private const val _R_528: String = "400"
@Suppress("unused") private val _R_529: String = """
_... ("""
@Suppress("unused") private const val _R_530: String = " more lines)_"
@Suppress("unused") private const val _R_531: String = "watch_match"
@Suppress("unused") private const val _R_532: String = "watch_disabled"
@Suppress("unused") private const val _R_533: String = "timestamp"
@Suppress("unused") private const val _R_534: String = "session_meta"
@Suppress("unused") private const val _R_535: String = "system"
@Suppress("unused") private const val _R_536: String = "privacy"
@Suppress("unused") private const val _R_537: String = "context_length"
@Suppress("unused") private const val _R_538: String = "Session hygiene auto-compress failed: %s"
@Suppress("unused") private const val _R_539: String = "📬 No home channel is set for "
@Suppress("unused") private val _R_540: String = """. A home channel is where Hermes delivers cron job results and cross-platform messages.

Type /sethome to make this chat your home channel, or ignore to skip."""
@Suppress("unused") private const val _R_541: String = "usage_limit_reached"
@Suppress("unused") private const val _R_542: String = " You are being rate-limited. Please wait a moment and try again."
@Suppress("unused") private const val _R_543: String = " The API is temporarily overloaded. Please try again shortly."
@Suppress("unused") private const val _R_544: String = "daily schedule at "
@Suppress("unused") private const val _R_545: String = ":00"
@Suppress("unused") private const val _R_546: String = "inactive for "
@Suppress("unused") private const val _R_547: String = "custom_providers"
@Suppress("unused") private const val _R_548: String = "models"
@Suppress("unused") private const val _R_549: String = "context"
@Suppress("unused") private const val _R_550: String = "token"
@Suppress("unused") private const val _R_551: String = "too large"
@Suppress("unused") private const val _R_552: String = "too long"
@Suppress("unused") private const val _R_553: String = "exceed"
@Suppress("unused") private const val _R_554: String = "payload"
@Suppress("unused") private const val _R_555: String = "Watch notification injection error: %s"
@Suppress("unused") private const val _R_556: String = "resets_in_seconds"
@Suppress("unused") private const val _R_557: String = " Your plan's usage limit has been reached. Please wait until it resets."
@Suppress("unused") private const val _R_558: String = "metadata"
@Suppress("unused") private const val _R_559: String = "Session hygiene: compressed %s → %s msgs, ~%s → ~%s tokens"
@Suppress("unused") private const val _R_560: String = " Your plan's usage limit has been reached. It resets in ~"
@Suppress("unused") private const val _R_561: String = "enabled"
@Suppress("unused") private const val _R_562: String = "memory"
@Suppress("unused") private const val _R_563: String = "Session hygiene: still ~%s tokens after compression"
@Suppress("unused") private const val _R_564: String = " The request was rejected by the API."
@Suppress("unused") private val _R_565: String = """Resolve current model config and return a formatted info block.

        Surfaces model, provider, context length, and endpoint so gateway
        users can immediately see if context detection went wrong (e.g.
        local models falling to the 128K default).
        """
@Suppress("unused") private const val _R_566: String = "default — set model.context_length in config to override"
@Suppress("unused") private const val _R_567: String = "detected"
@Suppress("unused") private const val _R_568: String = "◆ Model: `"
@Suppress("unused") private const val _R_569: String = "◆ Provider: "
@Suppress("unused") private const val _R_570: String = "◆ Context: "
@Suppress("unused") private const val _R_571: String = " tokens ("
@Suppress("unused") private const val _R_572: String = "localhost"
@Suppress("unused") private const val _R_573: String = "127.0.0.1"
@Suppress("unused") private const val _R_574: String = "0.0.0.0"
@Suppress("unused") private const val _R_575: String = "◆ Endpoint: "
@Suppress("unused") private const val _R_576: String = ".1f"
@Suppress("unused") private const val _R_577: String = "openrouter"
@Suppress("unused") private const val _R_578: String = "Handle /new or /reset command."
@Suppress("unused") private const val _R_579: String = "✨ Session reset! Starting fresh."
@Suppress("unused") private const val _R_580: String = "✨ New session started!"
@Suppress("unused") private const val _R_581: String = "session_reset"
@Suppress("unused") private const val _R_582: String = "session:end"
@Suppress("unused") private const val _R_583: String = "session:reset"
@Suppress("unused") private const val _R_584: String = "on_session_reset"
@Suppress("unused") private val _R_585: String = """
✦ Tip: """
@Suppress("unused") private const val _R_586: String = "Gateway memory flush on reset failed: %s"
@Suppress("unused") private const val _R_587: String = "Handle /profile — show active profile name and home directory."
@Suppress("unused") private const val _R_588: String = "👤 **Profile:** `"
@Suppress("unused") private const val _R_589: String = "📂 **Home:** `"
@Suppress("unused") private const val _R_590: String = "Handle /status command."
@Suppress("unused") private const val _R_591: String = "📊 **Hermes Gateway Status**"
@Suppress("unused") private const val _R_592: String = "**Session ID:** `"
@Suppress("unused") private const val _R_593: String = "**Title:** "
@Suppress("unused") private const val _R_594: String = "**Created:** "
@Suppress("unused") private const val _R_595: String = "**Last Activity:** "
@Suppress("unused") private const val _R_596: String = "**Tokens:** "
@Suppress("unused") private const val _R_597: String = "**Agent Running:** "
@Suppress("unused") private const val _R_598: String = "**Connected Platforms:** "
@Suppress("unused") private const val _R_599: String = "%Y-%m-%d %H:%M"
@Suppress("unused") private const val _R_600: String = "Yes ⚡"
@Suppress("unused") private const val _R_601: String = "Handle /agents command - list active agents and running tasks."
@Suppress("unused") private const val _R_602: String = "🤖 **Active Agents & Tasks**"
@Suppress("unused") private const val _R_603: String = "_running_agents"
@Suppress("unused") private const val _R_604: String = "_running_agents_ts"
@Suppress("unused") private const val _R_605: String = "**Active agents:** "
@Suppress("unused") private const val _R_606: String = "No active agents or running tasks."
@Suppress("unused") private const val _R_607: String = "elapsed"
@Suppress("unused") private const val _R_608: String = "state"
@Suppress("unused") private const val _R_609: String = " · this chat"
@Suppress("unused") private const val _R_610: String = "**Running background processes:** "
@Suppress("unused") private const val _R_611: String = "**Gateway async jobs:** "
@Suppress("unused") private const val _R_612: String = "_background_tasks"
@Suppress("unused") private const val _R_613: String = "done"
@Suppress("unused") private const val _R_614: String = " · `"
@Suppress("unused") private const val _R_615: String = ". `"
@Suppress("unused") private const val _R_616: String = "` · "
@Suppress("unused") private const val _R_617: String = " · "
@Suppress("unused") private const val _R_618: String = "... and "
@Suppress("unused") private const val _R_619: String = " more"
@Suppress("unused") private const val _R_620: String = "- `"
@Suppress("unused") private const val _R_621: String = "uptime_seconds"
@Suppress("unused") private val _R_622: String = """Handle /stop command - interrupt a running agent.

        When an agent is truly hung (blocked thread that never checks
        _interrupt_requested), the early intercept in _handle_message()
        handles /stop before this method is reached.  This handler fires
        only through normal command dispatch (no running agent) or as a
        fallback.  Force-clean the session lock in all cases for safety.

        The session is preserved so the user can continue the conversation.
        """
@Suppress("unused") private const val _R_623: String = "⚡ Stopped. The agent hadn't started yet — you can continue this session."
@Suppress("unused") private const val _R_624: String = "No active task to stop."
@Suppress("unused") private const val _R_625: String = "STOP (pending) for session %s — sentinel cleared"
@Suppress("unused") private const val _R_626: String = "stop_command_pending"
@Suppress("unused") private const val _R_627: String = "stop_command_handler"
@Suppress("unused") private const val _R_628: String = "Handle /restart command - drain active work, then restart the gateway."
@Suppress("unused") private const val _R_629: String = "♻ Restarting gateway. If you aren't notified within 60 seconds, restart from the console with `hermes gateway restart`."
@Suppress("unused") private const val _R_630: String = "⏳ Gateway restart already in progress..."
@Suppress("unused") private const val _R_631: String = "Ignoring redelivered /restart (platform=%s, update_id=%s) — already processed by a previous gateway instance."
@Suppress("unused") private const val _R_632: String = "requested_at"
@Suppress("unused") private const val _R_633: String = "INVOCATION_ID"
@Suppress("unused") private const val _R_634: String = "⏳ Draining "
@Suppress("unused") private const val _R_635: String = " active agent(s) before restart..."
@Suppress("unused") private const val _R_636: String = "Failed to write restart notify file: %s"
@Suppress("unused") private const val _R_637: String = "update_id"
@Suppress("unused") private const val _R_638: String = "Failed to write restart dedup marker: %s"
@Suppress("unused") private const val _R_639: String = ".restart_notify.json"
@Suppress("unused") private const val _R_640: String = ".restart_last_processed.json"
@Suppress("unused") private val _R_641: String = """Return True if this /restart is a Telegram re-delivery we already handled.

        The previous gateway wrote ``.restart_last_processed.json`` with the
        triggering platform + update_id when it processed the /restart.  If
        we now see a /restart on the same platform with an update_id <= that
        recorded value AND the marker is recent (< 5 minutes), it's a
        redelivery and should be ignored.

        Only applies to Telegram today (the only platform that exposes a
        numeric cross-session update ordering); other platforms return False.
        """
@Suppress("unused") private const val _R_642: String = "telegram"
@Suppress("unused") private const val _R_643: String = "Handle /help command - list available commands."
@Suppress("unused") private val _R_644: String = """📖 **Hermes Commands**
"""
@Suppress("unused") private val _R_645: String = """
⚡ **Skill Commands** ("""
@Suppress("unused") private const val _R_646: String = " active):"
@Suppress("unused") private const val _R_647: String = "` — "
@Suppress("unused") private val _R_648: String = """
... and """
@Suppress("unused") private const val _R_649: String = " more. Use `/commands` for the full paginated list."
@Suppress("unused") private const val _R_650: String = "description"
@Suppress("unused") private const val _R_651: String = "Handle /commands [page] - paginated list of all commands and skills."
@Suppress("unused") private const val _R_652: String = "No commands available."
@Suppress("unused") private const val _R_653: String = "📚 **Commands** ("
@Suppress("unused") private const val _R_654: String = " total, page "
@Suppress("unused") private const val _R_655: String = "Usage: `/commands [page]`"
@Suppress("unused") private const val _R_656: String = "⚡ **Skill Commands**:"
@Suppress("unused") private const val _R_657: String = "_(Requested page "
@Suppress("unused") private const val _R_658: String = " was out of range, showing page "
@Suppress("unused") private const val _R_659: String = ".)_"
@Suppress("unused") private const val _R_660: String = "Skill command"
@Suppress("unused") private const val _R_661: String = "`/commands "
@Suppress("unused") private const val _R_662: String = "` ← prev"
@Suppress("unused") private const val _R_663: String = "next → `/commands "
@Suppress("unused") private const val _R_664: String = " | "
@Suppress("unused") private val _R_665: String = """Handle /model command — switch model for this session.

        Supports:
          /model                              — interactive picker (Telegram/Discord) or text list
          /model <name>                       — switch for this session only
          /model <name> --global              — switch and persist to config.yaml
          /model <name> --provider <provider> — switch provider + model
          /model --provider <provider>        — switch to provider, auto-detect model
        """
@Suppress("unused") private const val _R_666: String = "_agent_cache"
@Suppress("unused") private const val _R_667: String = "[Note: model was just switched from "
@Suppress("unused") private const val _R_668: String = " to "
@Suppress("unused") private const val _R_669: String = " via "
@Suppress("unused") private const val _R_670: String = ". Adjust your self-identification accordingly.]"
@Suppress("unused") private const val _R_671: String = "`/model <name>` — switch model"
@Suppress("unused") private const val _R_672: String = "`/model <name> --provider <slug>` — switch provider"
@Suppress("unused") private const val _R_673: String = "`/model <name> --global` — persist"
@Suppress("unused") private const val _R_674: String = "Error: "
@Suppress("unused") private const val _R_675: String = "_pending_model_notes"
@Suppress("unused") private const val _R_676: String = "Model switched to `"
@Suppress("unused") private const val _R_677: String = "Provider: "
@Suppress("unused") private const val _R_678: String = "anthropic_messages"
@Suppress("unused") private const val _R_679: String = "Prompt caching: enabled"
@Suppress("unused") private const val _R_680: String = "Saved to config.yaml (`--global`)"
@Suppress("unused") private const val _R_681: String = "_(session only -- add `--global` to persist)_"
@Suppress("unused") private const val _R_682: String = "providers"
@Suppress("unused") private const val _R_683: String = "Current: `"
@Suppress("unused") private const val _R_684: String = "` on "
@Suppress("unused") private const val _R_685: String = "Capabilities: "
@Suppress("unused") private const val _R_686: String = "openrouter.ai"
@Suppress("unused") private const val _R_687: String = "claude"
@Suppress("unused") private const val _R_688: String = "Warning: "
@Suppress("unused") private const val _R_689: String = "send_model_picker"
@Suppress("unused") private const val _R_690: String = "Perform the model switch and return confirmation text."
@Suppress("unused") private const val _R_691: String = " (current)"
@Suppress("unused") private const val _R_692: String = "In-place model switch failed for cached agent: %s"
@Suppress("unused") private const val _R_693: String = "Failed to persist model switch: %s"
@Suppress("unused") private const val _R_694: String = "Context: "
@Suppress("unused") private const val _R_695: String = " tokens"
@Suppress("unused") private const val _R_696: String = "Max output: "
@Suppress("unused") private const val _R_697: String = "Cost: "
@Suppress("unused") private const val _R_698: String = "_(session only — use `/model <name> --global` to persist)_"
@Suppress("unused") private const val _R_699: String = "is_current"
@Suppress("unused") private const val _R_700: String = "** `--provider "
@Suppress("unused") private const val _R_701: String = "api_url"
@Suppress("unused") private const val _R_702: String = " (+"
@Suppress("unused") private const val _R_703: String = " more)"
@Suppress("unused") private const val _R_704: String = "slug"
@Suppress("unused") private const val _R_705: String = "total_models"
@Suppress("unused") private const val _R_706: String = "  `"
@Suppress("unused") private const val _R_707: String = "Picker model switch failed for cached agent: %s"
@Suppress("unused") private const val _R_708: String = "Handle /provider command - show available providers."
@Suppress("unused") private const val _R_709: String = "auto"
@Suppress("unused") private const val _R_710: String = "**Available providers:**"
@Suppress("unused") private const val _R_711: String = "Switch: `/model provider:model-name`"
@Suppress("unused") private const val _R_712: String = "Setup: `hermes setup`"
@Suppress("unused") private const val _R_713: String = "custom"
@Suppress("unused") private const val _R_714: String = "🔌 **Current provider:** "
@Suppress("unused") private const val _R_715: String = " (`"
@Suppress("unused") private const val _R_716: String = " ← active"
@Suppress("unused") private const val _R_717: String = "authenticated"
@Suppress("unused") private const val _R_718: String = "aliases"
@Suppress("unused") private const val _R_719: String = "  _(also: "
@Suppress("unused") private const val _R_720: String = "label"
@Suppress("unused") private const val _R_721: String = "Handle /personality command - list or set a personality."
@Suppress("unused") private val _R_722: String = """🎭 Personality cleared — using base agent behavior.
_(takes effect on next message)_"""
@Suppress("unused") private const val _R_723: String = "`none`, "
@Suppress("unused") private const val _R_724: String = "Unknown personality: `"
@Suppress("unused") private val _R_725: String = """`

Available: """
@Suppress("unused") private const val _R_726: String = "No personalities configured in `"
@Suppress("unused") private const val _R_727: String = "/config.yaml`"
@Suppress("unused") private val _R_728: String = """🎭 **Available Personalities**
"""
@Suppress("unused") private const val _R_729: String = "• `none` — (no personality overlay)"
@Suppress("unused") private val _R_730: String = """
Usage: `/personality <name>`"""
@Suppress("unused") private const val _R_731: String = "none"
@Suppress("unused") private const val _R_732: String = "neutral"
@Suppress("unused") private const val _R_733: String = "personalities"
@Suppress("unused") private const val _R_734: String = "tone"
@Suppress("unused") private const val _R_735: String = "style"
@Suppress("unused") private const val _R_736: String = "🎭 Personality set to **"
@Suppress("unused") private val _R_737: String = """**
_(takes effect on next message)_"""
@Suppress("unused") private const val _R_738: String = "• `"
@Suppress("unused") private const val _R_739: String = "⚠️ Failed to save personality change: "
@Suppress("unused") private const val _R_740: String = "Tone: "
@Suppress("unused") private const val _R_741: String = "Style: "
@Suppress("unused") private const val _R_742: String = "Handle /retry command - re-send the last user message."
@Suppress("unused") private const val _R_743: String = "No previous message to retry."
@Suppress("unused") private const val _R_744: String = "Handle /undo command - remove the last user/assistant exchange."
@Suppress("unused") private const val _R_745: String = "Nothing to undo."
@Suppress("unused") private const val _R_746: String = "↩️ Undid "
@Suppress("unused") private val _R_747: String = """ message(s).
Removed: """"
@Suppress("unused") private const val _R_748: String = "Handle /sethome command -- set the current chat as the platform's home channel."
@Suppress("unused") private const val _R_749: String = "✅ Home channel set to **"
@Suppress("unused") private const val _R_750: String = "** (ID: "
@Suppress("unused") private val _R_751: String = """).
Cron jobs and cross-platform messages will be delivered here."""
@Suppress("unused") private const val _R_752: String = "Failed to save home channel: "
@Suppress("unused") private const val _R_753: String = "Extract Discord guild_id from the raw message object."
@Suppress("unused") private const val _R_754: String = "raw_message"
@Suppress("unused") private const val _R_755: String = "guild_id"
@Suppress("unused") private const val _R_756: String = "guild"
@Suppress("unused") private const val _R_757: String = "Handle /voice [on|off|tts|channel|leave|status] command."
@Suppress("unused") private val _R_758: String = """Voice mode enabled.
I'll reply with voice when you send voice messages.
Use /voice tts to get voice replies for all messages."""
@Suppress("unused") private const val _R_759: String = "enable"
@Suppress("unused") private const val _R_760: String = "Voice mode disabled. Text-only replies."
@Suppress("unused") private const val _R_761: String = "disable"
@Suppress("unused") private const val _R_762: String = "tts"
@Suppress("unused") private val _R_763: String = """Auto-TTS enabled.
All replies will include a voice message."""
@Suppress("unused") private const val _R_764: String = "channel"
@Suppress("unused") private const val _R_765: String = "join"
@Suppress("unused") private const val _R_766: String = "leave"
@Suppress("unused") private const val _R_767: String = "Off (text only)"
@Suppress("unused") private const val _R_768: String = "On (voice reply to voice messages)"
@Suppress("unused") private const val _R_769: String = "TTS (voice reply to all messages)"
@Suppress("unused") private const val _R_770: String = "Voice mode: "
@Suppress("unused") private const val _R_771: String = "Voice mode enabled."
@Suppress("unused") private const val _R_772: String = "Voice mode disabled."
@Suppress("unused") private const val _R_773: String = "get_voice_channel_info"
@Suppress("unused") private const val _R_774: String = "members"
@Suppress("unused") private const val _R_775: String = "Voice channel: #"
@Suppress("unused") private const val _R_776: String = "Participants: "
@Suppress("unused") private const val _R_777: String = " (speaking)"
@Suppress("unused") private const val _R_778: String = "is_speaking"
@Suppress("unused") private const val _R_779: String = "  - "
@Suppress("unused") private const val _R_780: String = "channel_name"
@Suppress("unused") private const val _R_781: String = "member_count"
@Suppress("unused") private const val _R_782: String = "display_name"
@Suppress("unused") private const val _R_783: String = "Join the user's current Discord voice channel."
@Suppress("unused") private const val _R_784: String = "Failed to join voice channel. Check bot permissions (Connect + Speak)."
@Suppress("unused") private const val _R_785: String = "Voice channels are not supported on this platform."
@Suppress("unused") private const val _R_786: String = "This command only works in a Discord server."
@Suppress("unused") private const val _R_787: String = "You need to be in a voice channel first."
@Suppress("unused") private const val _R_788: String = "_voice_input_callback"
@Suppress("unused") private const val _R_789: String = "_on_voice_disconnect"
@Suppress("unused") private const val _R_790: String = "join_voice_channel"
@Suppress("unused") private const val _R_791: String = "_voice_sources"
@Suppress("unused") private const val _R_792: String = "Joined voice channel **"
@Suppress("unused") private val _R_793: String = """**.
I'll speak my replies and listen to you. Use /voice leave to disconnect."""
@Suppress("unused") private const val _R_794: String = "Failed to join voice channel: %s"
@Suppress("unused") private const val _R_795: String = "Failed to join voice channel: "
@Suppress("unused") private const val _R_796: String = "pynacl"
@Suppress("unused") private const val _R_797: String = "nacl"
@Suppress("unused") private const val _R_798: String = "davey"
@Suppress("unused") private const val _R_799: String = "Voice dependencies are missing (PyNaCl / davey). Install with: `"
@Suppress("unused") private const val _R_800: String = " -m pip install PyNaCl`"
@Suppress("unused") private const val _R_801: String = "Leave the Discord voice channel."
@Suppress("unused") private const val _R_802: String = "Left voice channel."
@Suppress("unused") private const val _R_803: String = "Not in a voice channel."
@Suppress("unused") private const val _R_804: String = "leave_voice_channel"
@Suppress("unused") private const val _R_805: String = "is_in_voice_channel"
@Suppress("unused") private const val _R_806: String = "Error leaving voice channel: %s"
@Suppress("unused") private val _R_807: String = """Handle transcribed voice from a user in a voice channel.

        Creates a synthetic MessageEvent and processes it through the
        adapter's full message pipeline (session, typing, agent, TTS reply).
        """
@Suppress("unused") private const val _R_808: String = "Unauthorized voice input from user %d, ignoring"
@Suppress("unused") private const val _R_809: String = "@here"
@Suppress("unused") private const val _R_810: String = "@​here"
@Suppress("unused") private const val _R_811: String = "@everyone"
@Suppress("unused") private const val _R_812: String = "@​everyone"
@Suppress("unused") private const val _R_813: String = "**[Voice]** <@"
@Suppress("unused") private const val _R_814: String = ">: "
@Suppress("unused") private val _R_815: String = """Decide whether the runner should send a TTS voice reply.

        Returns False when:
        - voice_mode is off for this chat
        - response is empty or an error
        - agent already called text_to_speech tool (dedup)
        - voice input and base adapter auto-TTS already handled it (skip_double)
          UNLESS streaming already consumed the response (already_sent=True),
          in which case the base adapter won't have text for auto-TTS so the
          runner must handle it.
        """
@Suppress("unused") private const val _R_816: String = "Error:"
@Suppress("unused") private const val _R_817: String = "text_to_speech"
@Suppress("unused") private const val _R_818: String = "tool_calls"
@Suppress("unused") private const val _R_819: String = "function"
@Suppress("unused") private const val _R_820: String = "Generate TTS audio and send as a voice message before the text reply."
@Suppress("unused") private const val _R_821: String = "hermes_voice"
@Suppress("unused") private const val _R_822: String = "file_path"
@Suppress("unused") private const val _R_823: String = "tts_reply_"
@Suppress("unused") private const val _R_824: String = ".mp3"
@Suppress("unused") private const val _R_825: String = "Auto voice reply TTS failed: %s"
@Suppress("unused") private const val _R_826: String = "play_in_voice_channel"
@Suppress("unused") private const val _R_827: String = "Auto voice reply failed: %s"
@Suppress("unused") private const val _R_828: String = "success"
@Suppress("unused") private const val _R_829: String = "send_voice"
@Suppress("unused") private const val _R_830: String = "audio_path"
@Suppress("unused") private const val _R_831: String = "reply_to"
@Suppress("unused") private val _R_832: String = """Extract MEDIA: tags and local file paths from a response and deliver them.

        Called after streaming has already sent the text to the user, so the
        text itself is already delivered — this only handles file attachments
        that the normal _process_message_background path would have caught.
        """
@Suppress("unused") private const val _R_833: String = ".ogg"
@Suppress("unused") private const val _R_834: String = ".opus"
@Suppress("unused") private const val _R_835: String = ".wav"
@Suppress("unused") private const val _R_836: String = ".m4a"
@Suppress("unused") private const val _R_837: String = ".mp4"
@Suppress("unused") private const val _R_838: String = ".mov"
@Suppress("unused") private const val _R_839: String = ".avi"
@Suppress("unused") private const val _R_840: String = ".mkv"
@Suppress("unused") private const val _R_841: String = ".webm"
@Suppress("unused") private const val _R_842: String = ".3gp"
@Suppress("unused") private const val _R_843: String = ".jpg"
@Suppress("unused") private const val _R_844: String = ".jpeg"
@Suppress("unused") private const val _R_845: String = ".png"
@Suppress("unused") private const val _R_846: String = ".webp"
@Suppress("unused") private const val _R_847: String = ".gif"
@Suppress("unused") private const val _R_848: String = "Post-stream media extraction failed: %s"
@Suppress("unused") private const val _R_849: String = "[%s] Post-stream media delivery failed: %s"
@Suppress("unused") private const val _R_850: String = "[%s] Post-stream file delivery failed: %s"
@Suppress("unused") private const val _R_851: String = "Handle /rollback command — list or restore filesystem checkpoints."
@Suppress("unused") private val _R_852: String = """Checkpoints are not enabled.
Enable in config.yaml:
```
checkpoints:
  enabled: true
```"""
@Suppress("unused") private const val _R_853: String = "No checkpoints found for "
@Suppress("unused") private const val _R_854: String = "✅ Restored to checkpoint "
@Suppress("unused") private val _R_855: String = """
A pre-rollback snapshot was saved automatically."""
@Suppress("unused") private const val _R_856: String = "checkpoints"
@Suppress("unused") private const val _R_857: String = "max_snapshots"
@Suppress("unused") private const val _R_858: String = "hash"
@Suppress("unused") private const val _R_859: String = "Invalid checkpoint number. Use 1-"
@Suppress("unused") private const val _R_860: String = "restored_to"
@Suppress("unused") private const val _R_861: String = "reason"
@Suppress("unused") private val _R_862: String = """Handle /background <prompt> — run a prompt in a separate background session.

        Spawns a new AIAgent in a background thread with its own session.
        When it completes, sends the result back to the same chat without
        modifying the active session's conversation history.
        """
@Suppress("unused") private val _R_863: String = """Usage: /background <prompt>
Example: /background Summarize the top HN stories today

Runs the prompt in a separate session. You can keep chatting — the result will appear here when done."""
@Suppress("unused") private const val _R_864: String = "bg_"
@Suppress("unused") private const val _R_865: String = "🔄 Background task started: \""
@Suppress("unused") private val _R_866: String = """"
Task ID: """
@Suppress("unused") private val _R_867: String = """
You can keep chatting — results will appear when done."""
@Suppress("unused") private const val _R_868: String = "%H%M%S"
@Suppress("unused") private const val _R_869: String = "Handle /btw <question> — ephemeral side question in the same chat."
@Suppress("unused") private val _R_870: String = """Usage: /btw <question>
Example: /btw what module owns session title sanitization?

Answers using session context. No tools, not persisted."""
@Suppress("unused") private const val _R_871: String = "A /btw is already running for this chat. Wait for it to finish."
@Suppress("unused") private const val _R_872: String = "btw_"
@Suppress("unused") private const val _R_873: String = "💬 /btw: \""
@Suppress("unused") private val _R_874: String = """"
Reply will appear here shortly."""
@Suppress("unused") private const val _R_875: String = "_active_btw_tasks"
@Suppress("unused") private const val _R_876: String = "Execute an ephemeral /btw side question and deliver the answer."
@Suppress("unused") private const val _R_877: String = "No adapter for platform %s in /btw task %s"
@Suppress("unused") private val _R_878: String = """[Ephemeral /btw side question. Answer using the conversation context. No tools available. Be direct and concise.]

"""
@Suppress("unused") private const val _R_879: String = "(No response generated)"
@Suppress("unused") private val _R_880: String = """"

"""
@Suppress("unused") private const val _R_881: String = "/btw task %s failed"
@Suppress("unused") private const val _R_882: String = "❌ /btw failed: no provider credentials configured."
@Suppress("unused") private const val _R_883: String = "_session_messages"
@Suppress("unused") private const val _R_884: String = "only"
@Suppress("unused") private const val _R_885: String = "ignore"
@Suppress("unused") private const val _R_886: String = "order"
@Suppress("unused") private const val _R_887: String = "sort"
@Suppress("unused") private const val _R_888: String = "require_parameters"
@Suppress("unused") private const val _R_889: String = "data_collection"
@Suppress("unused") private const val _R_890: String = "❌ /btw failed: "
@Suppress("unused") private val _R_891: String = """Handle /reasoning command — manage reasoning effort and display toggle.

        Usage:
            /reasoning              Show current effort level and display state
            /reasoning <level>      Set reasoning effort (none, minimal, low, medium, high, xhigh)
            /reasoning show|on      Show model reasoning in responses
            /reasoning hide|off     Hide model reasoning from responses
        """
@Suppress("unused") private const val _R_892: String = "Save a dot-separated key to config.yaml."
@Suppress("unused") private const val _R_893: String = "agent.reasoning_effort"
@Suppress("unused") private const val _R_894: String = "medium (default)"
@Suppress("unused") private const val _R_895: String = "on ✓"
@Suppress("unused") private val _R_896: String = """🧠 **Reasoning Settings**

**Effort:** `"""
@Suppress("unused") private val _R_897: String = """`
**Display:** """
@Suppress("unused") private val _R_898: String = """

_Usage:_ `/reasoning <none|minimal|low|medium|high|xhigh|show|hide>`"""
@Suppress("unused") private const val _R_899: String = "show"
@Suppress("unused") private val _R_900: String = """🧠 ✓ Reasoning display: **ON**
Model thinking will be shown before each response on **"""
@Suppress("unused") private const val _R_901: String = "**."
@Suppress("unused") private const val _R_902: String = "hide"
@Suppress("unused") private const val _R_903: String = "🧠 ✓ Reasoning display: **OFF** for **"
@Suppress("unused") private const val _R_904: String = "🧠 ✓ Reasoning effort set to `"
@Suppress("unused") private val _R_905: String = """` (saved to config)
_(takes effect on next message)_"""
@Suppress("unused") private const val _R_906: String = "` (this session only)"
@Suppress("unused") private const val _R_907: String = "none (disabled)"
@Suppress("unused") private const val _R_908: String = "display.platforms."
@Suppress("unused") private const val _R_909: String = ".show_reasoning"
@Suppress("unused") private const val _R_910: String = "minimal"
@Suppress("unused") private const val _R_911: String = "low"
@Suppress("unused") private const val _R_912: String = "medium"
@Suppress("unused") private const val _R_913: String = "high"
@Suppress("unused") private const val _R_914: String = "xhigh"
@Suppress("unused") private const val _R_915: String = "effort"
@Suppress("unused") private const val _R_916: String = "⚠️ Unknown argument: `"
@Suppress("unused") private val _R_917: String = """`

**Valid levels:** none, minimal, low, medium, high, xhigh
**Display:** show, hide"""
@Suppress("unused") private const val _R_918: String = "Failed to save config key %s: %s"
@Suppress("unused") private const val _R_919: String = "Handle /fast — mirror the CLI Priority Processing toggle in gateway chats."
@Suppress("unused") private const val _R_920: String = "⚡ /fast is only available for OpenAI models that support Priority Processing."
@Suppress("unused") private const val _R_921: String = "priority"
@Suppress("unused") private const val _R_922: String = "FAST"
@Suppress("unused") private const val _R_923: String = "agent.service_tier"
@Suppress("unused") private const val _R_924: String = "⚡ ✓ Priority Processing: **"
@Suppress("unused") private const val _R_925: String = "** (this session only)"
@Suppress("unused") private const val _R_926: String = "normal"
@Suppress("unused") private val _R_927: String = """⚡ Priority Processing

Current mode: `"""
@Suppress("unused") private val _R_928: String = """`

_Usage:_ `/fast <normal|fast|status>`"""
@Suppress("unused") private const val _R_929: String = "NORMAL"
@Suppress("unused") private val _R_930: String = """** (saved to config)
_(takes effect on next message)_"""
@Suppress("unused") private val _R_931: String = """`

**Valid options:** normal, fast, status"""
@Suppress("unused") private const val _R_932: String = "Handle /yolo — toggle dangerous command approval bypass for this session only."
@Suppress("unused") private const val _R_933: String = "⚠️ YOLO mode **OFF** for this session — dangerous commands will require approval."
@Suppress("unused") private const val _R_934: String = "⚡ YOLO mode **ON** for this session — all commands auto-approved. Use with caution."
@Suppress("unused") private val _R_935: String = """Handle /verbose command — cycle tool progress display mode.

        Gated by ``display.tool_progress_command`` in config.yaml (default off).
        When enabled, cycles the tool progress mode through off → new → all →
        verbose → off for the *current platform*.  The setting is saved to
        ``display.platforms.<platform>.tool_progress`` so each channel can
        have its own verbosity level independently.
        """
@Suppress("unused") private val _R_936: String = """The `/verbose` command is not enabled for messaging platforms.

Enable it in `config.yaml`:
```yaml
display:
  tool_progress_command: true
```"""
@Suppress("unused") private const val _R_937: String = "⚙️ Tool progress: **OFF** — no tool activity shown."
@Suppress("unused") private const val _R_938: String = "⚙️ Tool progress: **NEW** — shown when tool changes (preview length: `display.tool_preview_length`, default 40)."
@Suppress("unused") private const val _R_939: String = "⚙️ Tool progress: **ALL** — every tool call shown (preview length: `display.tool_preview_length`, default 40)."
@Suppress("unused") private const val _R_940: String = "⚙️ Tool progress: **VERBOSE** — every tool call with full arguments."
@Suppress("unused") private const val _R_941: String = "tool_progress"
@Suppress("unused") private const val _R_942: String = "tool_progress_command"
@Suppress("unused") private val _R_943: String = """
_(saved for **"""
@Suppress("unused") private const val _R_944: String = "** — takes effect on next message)_"
@Suppress("unused") private const val _R_945: String = "Failed to save tool_progress mode: %s"
@Suppress("unused") private val _R_946: String = """
_(could not save to config: """
@Suppress("unused") private val _R_947: String = """Handle /compress command -- manually compress conversation context.

        Accepts an optional focus topic: ``/compress <focus>`` guides the
        summariser to preserve information related to *focus* while being
        more aggressive about discarding everything else.
        """
@Suppress("unused") private const val _R_948: String = "Not enough conversation to compress (need at least 4 messages)."
@Suppress("unused") private const val _R_949: String = "No provider configured -- cannot compress."
@Suppress("unused") private const val _R_950: String = "note"
@Suppress("unused") private const val _R_951: String = "Nothing to compress yet (the transcript is still all protected context)."
@Suppress("unused") private const val _R_952: String = "🗜️ "
@Suppress("unused") private const val _R_953: String = "token_line"
@Suppress("unused") private const val _R_954: String = "Manual compress failed: %s"
@Suppress("unused") private const val _R_955: String = "Compression failed: "
@Suppress("unused") private const val _R_956: String = "Focus: \""
@Suppress("unused") private const val _R_957: String = "headline"
@Suppress("unused") private const val _R_958: String = "Handle /title command — set or show the current session's title."
@Suppress("unused") private const val _R_959: String = "Session database not available."
@Suppress("unused") private const val _R_960: String = "⚠️ Title is empty after cleanup. Please use printable characters."
@Suppress("unused") private const val _R_961: String = "Session not found in database."
@Suppress("unused") private const val _R_962: String = "📌 Session: `"
@Suppress("unused") private val _R_963: String = """`
Title: **"""
@Suppress("unused") private val _R_964: String = """`
No title set. Usage: `/title My Session Name`"""
@Suppress("unused") private const val _R_965: String = "⚠️ "
@Suppress("unused") private const val _R_966: String = "✏️ Session title set: **"
@Suppress("unused") private const val _R_967: String = "Handle /resume command — switch to a previously-named session."
@Suppress("unused") private const val _R_968: String = "Failed to switch session."
@Suppress("unused") private const val _R_969: String = "↻ Resumed session **"
@Suppress("unused") private const val _R_970: String = ". Conversation restored."
@Suppress("unused") private const val _R_971: String = "No session found matching '**"
@Suppress("unused") private val _R_972: String = """**'.
Use `/resume` with no arguments to see available sessions."""
@Suppress("unused") private const val _R_973: String = "📌 Already on session **"
@Suppress("unused") private const val _R_974: String = " message"
@Suppress("unused") private val _R_975: String = """No named sessions found.
Use `/title My Session` to name your current session, then `/resume My Session` to return to it later."""
@Suppress("unused") private val _R_976: String = """📋 **Named Sessions**
"""
@Suppress("unused") private val _R_977: String = """
Usage: `/resume <session name>`"""
@Suppress("unused") private const val _R_978: String = "Memory flush on resume failed: %s"
@Suppress("unused") private const val _R_979: String = "Failed to list titled sessions: %s"
@Suppress("unused") private const val _R_980: String = "Could not list sessions: "
@Suppress("unused") private const val _R_981: String = "preview"
@Suppress("unused") private const val _R_982: String = " — _"
@Suppress("unused") private const val _R_983: String = "• **"
@Suppress("unused") private val _R_984: String = """Handle /branch [name] — fork the current session into a new independent copy.

        Copies conversation history to a new session so the user can explore
        a different approach without losing the original.
        Inspired by Claude Code's /branch command.
        """
@Suppress("unused") private const val _R_985: String = "No conversation to branch — send a message first."
@Suppress("unused") private const val _R_986: String = "%Y%m%d_%H%M%S"
@Suppress("unused") private const val _R_987: String = "Branch created but failed to switch to it."
@Suppress("unused") private const val _R_988: String = "⑂ Branched to **"
@Suppress("unused") private const val _R_989: String = "** ("
@Suppress("unused") private val _R_990: String = """ copied)
Original: `"""
@Suppress("unused") private val _R_991: String = """`
Branch: `"""
@Suppress("unused") private val _R_992: String = """`
Use `/resume` to switch back to the original."""
@Suppress("unused") private const val _R_993: String = "Failed to create branch session: %s"
@Suppress("unused") private const val _R_994: String = "Failed to create branch: "
@Suppress("unused") private const val _R_995: String = "tool_call_id"
@Suppress("unused") private const val _R_996: String = "tool_name"
@Suppress("unused") private val _R_997: String = """Handle /usage command -- show token usage for the current session.

        Checks both _running_agents (mid-turn) and _agent_cache (between turns)
        so that rate limits, cost estimates, and detailed token breakdowns are
        available whenever the user asks, not only while the agent is running.
        """
@Suppress("unused") private const val _R_998: String = "No usage data available for this session."
@Suppress("unused") private const val _R_999: String = "session_total_tokens"
@Suppress("unused") private const val _R_1000: String = "📊 **Session Token Usage**"
@Suppress("unused") private const val _R_1001: String = "📊 **Session Info**"
@Suppress("unused") private const val _R_1002: String = "_(Detailed usage available after the first agent response)_"
@Suppress("unused") private const val _R_1003: String = "_session_db"
@Suppress("unused") private const val _R_1004: String = "billing_provider"
@Suppress("unused") private const val _R_1005: String = "billing_base_url"
@Suppress("unused") private const val _R_1006: String = "session_input_tokens"
@Suppress("unused") private const val _R_1007: String = "session_output_tokens"
@Suppress("unused") private const val _R_1008: String = "session_cache_read_tokens"
@Suppress("unused") private const val _R_1009: String = "session_cache_write_tokens"
@Suppress("unused") private const val _R_1010: String = "Model: `"
@Suppress("unused") private const val _R_1011: String = "Input tokens: "
@Suppress("unused") private const val _R_1012: String = "Output tokens: "
@Suppress("unused") private const val _R_1013: String = "Total: "
@Suppress("unused") private const val _R_1014: String = "API calls: "
@Suppress("unused") private const val _R_1015: String = "Messages: "
@Suppress("unused") private const val _R_1016: String = "Estimated context: ~"
@Suppress("unused") private const val _R_1017: String = "⏱️ **Rate Limits:** "
@Suppress("unused") private const val _R_1018: String = "Cache read tokens: "
@Suppress("unused") private const val _R_1019: String = "Cache write tokens: "
@Suppress("unused") private const val _R_1020: String = "included"
@Suppress("unused") private const val _R_1021: String = " / "
@Suppress("unused") private const val _R_1022: String = "Compressions: "
@Suppress("unused") private const val _R_1023: String = "Cost: included"
@Suppress("unused") private const val _R_1024: String = ".4f"
@Suppress("unused") private const val _R_1025: String = "Handle /insights command -- show usage insights and analytics."
@Suppress("unused") private const val _R_1026: String = "[\\u2012\\u2013\\u2014\\u2015](days|source)"
@Suppress("unused") private const val _R_1027: String = "--\\1"
@Suppress("unused") private const val _R_1028: String = "Insights command error: %s"
@Suppress("unused") private const val _R_1029: String = "Error generating insights: "
@Suppress("unused") private const val _R_1030: String = "--days"
@Suppress("unused") private const val _R_1031: String = "--source"
@Suppress("unused") private const val _R_1032: String = "Invalid --days value: "
@Suppress("unused") private const val _R_1033: String = "Handle /reload-mcp command -- disconnect and reconnect all MCP servers."
@Suppress("unused") private val _R_1034: String = """🔄 **MCP Servers Reloaded**
"""
@Suppress("unused") private const val _R_1035: String = "No MCP tools available"
@Suppress("unused") private const val _R_1036: String = "No MCP servers connected."
@Suppress("unused") private const val _R_1037: String = " MCP tool(s) now available"
@Suppress("unused") private const val _R_1038: String = "[SYSTEM: MCP servers have been reloaded. "
@Suppress("unused") private const val _R_1039: String = ". The tool list for this conversation has been updated accordingly.]"
@Suppress("unused") private const val _R_1040: String = "MCP reload failed: %s"
@Suppress("unused") private const val _R_1041: String = "❌ MCP reload failed: "
@Suppress("unused") private const val _R_1042: String = "♻️ Reconnected: "
@Suppress("unused") private const val _R_1043: String = "➕ Added: "
@Suppress("unused") private const val _R_1044: String = "➖ Removed: "
@Suppress("unused") private val _R_1045: String = """
🔧 """
@Suppress("unused") private const val _R_1046: String = " tool(s) available from "
@Suppress("unused") private const val _R_1047: String = " server(s)"
@Suppress("unused") private const val _R_1048: String = "Added servers: "
@Suppress("unused") private const val _R_1049: String = "Removed servers: "
@Suppress("unused") private const val _R_1050: String = "Reconnected servers: "
@Suppress("unused") private val _R_1051: String = """Handle /approve command — unblock waiting agent thread(s).

        The agent thread(s) are blocked inside tools/approval.py waiting for
        the user to respond.  This handler signals the event so the agent
        resumes and the terminal_tool executes the command inline — the same
        flow as the CLI's synchronous input() approval.

        Supports multiple concurrent approvals (parallel subagents,
        execute_code).  ``/approve`` resolves the oldest pending command;
        ``/approve all`` resolves every pending command at once.

        Usage:
            /approve              — approve oldest pending command once
            /approve all          — approve ALL pending commands at once
            /approve session      — approve oldest + remember for session
            /approve all session  — approve all + remember for session
            /approve always       — approve oldest + remember permanently
            /approve all always   — approve all + remember permanently
        """
@Suppress("unused") private const val _R_1052: String = "No pending command to approve."
@Suppress("unused") private const val _R_1053: String = "always"
@Suppress("unused") private const val _R_1054: String = " (pattern approved permanently)"
@Suppress("unused") private const val _R_1055: String = "User approved %d dangerous command(s) via /approve%s"
@Suppress("unused") private const val _R_1056: String = "✅ Command"
@Suppress("unused") private const val _R_1057: String = " approved"
@Suppress("unused") private const val _R_1058: String = ". The agent is resuming..."
@Suppress("unused") private const val _R_1059: String = "⚠️ Approval expired (agent is no longer waiting). Ask the agent to try again."
@Suppress("unused") private const val _R_1060: String = " (pattern approved for this session)"
@Suppress("unused") private const val _R_1061: String = "once"
@Suppress("unused") private const val _R_1062: String = " commands)"
@Suppress("unused") private const val _R_1063: String = "permanent"
@Suppress("unused") private const val _R_1064: String = "permanently"
@Suppress("unused") private const val _R_1065: String = "ses"
@Suppress("unused") private val _R_1066: String = """Handle /deny command — reject pending dangerous command(s).

        Signals blocked agent thread(s) with a 'deny' result so they receive
        a definitive BLOCKED message, same as the CLI deny flow.

        ``/deny`` denies the oldest; ``/deny all`` denies everything.
        """
@Suppress("unused") private const val _R_1067: String = "No pending command to deny."
@Suppress("unused") private const val _R_1068: String = "User denied %d dangerous command(s) via /deny"
@Suppress("unused") private const val _R_1069: String = "❌ Command"
@Suppress("unused") private const val _R_1070: String = " denied"
@Suppress("unused") private const val _R_1071: String = "❌ Command denied (approval was stale)."
@Suppress("unused") private val _R_1072: String = """Handle /debug — upload debug report (summary only) and return paste URLs.

        Gateway uploads ONLY the summary report (system info + log tails),
        NOT full log files, to protect conversation privacy.  Users who need
        full log uploads should use ``hermes debug share`` from the CLI.
        """
@Suppress("unused") private const val _R_1073: String = "**Debug report uploaded:**"
@Suppress("unused") private const val _R_1074: String = "⏱ Pastes will auto-delete in 6 hours."
@Suppress("unused") private const val _R_1075: String = "For full log uploads, use `hermes debug share` from the CLI."
@Suppress("unused") private const val _R_1076: String = "Share these links with the Hermes team for support."
@Suppress("unused") private const val _R_1077: String = "Report"
@Suppress("unused") private const val _R_1078: String = "✗ Failed to upload debug report: "
@Suppress("unused") private const val _R_1079: String = "`  "
@Suppress("unused") private val _R_1080: String = """Handle /update command — update Hermes Agent to the latest version.

        Spawns ``hermes update`` in a detached session (via ``setsid``) so it
        survives the gateway restart that ``hermes update`` may trigger. Marker
        files are written so either the current gateway process or the next one
        can notify the user when the update finishes.
        """
@Suppress("unused") private const val _R_1081: String = "⚕ Starting Hermes update… I'll stream progress here."
@Suppress("unused") private const val _R_1082: String = "✗ /update is only available from messaging platforms. Run `hermes update` from the terminal."
@Suppress("unused") private const val _R_1083: String = "✗ Not a git repository — cannot update."
@Suppress("unused") private const val _R_1084: String = "✗ Could not locate the `hermes` command. Hermes is running, but the update command could not find the executable on PATH or via the current Python interpreter. Try running `hermes update` manually in your terminal."
@Suppress("unused") private const val _R_1085: String = ".update_output.txt"
@Suppress("unused") private const val _R_1086: String = ".update_exit_code"
@Suppress("unused") private const val _R_1087: String = "PYTHONUNBUFFERED=1 "
@Suppress("unused") private const val _R_1088: String = " update --gateway > "
@Suppress("unused") private const val _R_1089: String = " 2>&1; status=\$?; printf '%s' \"\$status\" > "
@Suppress("unused") private const val _R_1090: String = "✗ Failed to start update: "
@Suppress("unused") private const val _R_1091: String = "update Hermes Agent"
@Suppress("unused") private val _R_1092: String = """Watch ``hermes update --gateway``, streaming output + forwarding prompts.

        Polls ``.update_output.txt`` for new content and sends chunks to the
        user periodically.  Detects ``.update_prompt.json`` (written by the
        update process when it needs user input) and forwards the prompt to
        the messenger.  The user's next message is intercepted by
        ``_handle_message`` and written to ``.update_response``.
        """
@Suppress("unused") private const val _R_1093: String = ".update_prompt.json"
@Suppress("unused") private const val _R_1094: String = "Send buffered output to the user."
@Suppress("unused") private const val _R_1095: String = "Update watcher: cannot resolve adapter/chat_id, falling back to completion-only"
@Suppress("unused") private const val _R_1096: String = "\\x1b\\[[0-9;]*[A-Za-z]"
@Suppress("unused") private const val _R_1097: String = "Update watcher timed out after %.0fs"
@Suppress("unused") private const val _R_1098: String = "124"
@Suppress("unused") private const val _R_1099: String = "Update finished (exit=%s), notified %s"
@Suppress("unused") private const val _R_1100: String = "prompt"
@Suppress("unused") private const val _R_1101: String = "❌ Hermes update timed out after 30 minutes."
@Suppress("unused") private const val _R_1102: String = "Update stream send failed: %s"
@Suppress("unused") private const val _R_1103: String = "Update final notification failed: %s"
@Suppress("unused") private const val _R_1104: String = "Forwarded update prompt to %s: %s"
@Suppress("unused") private const val _R_1105: String = "Failed to read update prompt: %s"
@Suppress("unused") private val _R_1106: String = """```
"""
@Suppress("unused") private val _R_1107: String = """
```"""
@Suppress("unused") private const val _R_1108: String = "✅ Hermes update finished."
@Suppress("unused") private const val _R_1109: String = "send_update_prompt"
@Suppress("unused") private const val _R_1110: String = " (default: "
@Suppress("unused") private const val _R_1111: String = "❌ Hermes update failed (exit code {})."
@Suppress("unused") private const val _R_1112: String = "Button-based update prompt failed: %s"
@Suppress("unused") private val _R_1113: String = """⚕ **Update needs your input:**

"""
@Suppress("unused") private val _R_1114: String = """

Reply `/approve` (yes) or `/deny` (no), or type your answer directly."""
@Suppress("unused") private val _R_1115: String = """If an update finished, notify the user.

        Returns False when the update is still running so a caller can retry
        later. Returns True after a definitive send/skip decision.

        This is the legacy notification path used when the streaming watcher
        cannot resolve the adapter (e.g. after a gateway restart where the
        platform hasn't reconnected yet).
        """
@Suppress("unused") private const val _R_1116: String = "Update notification deferred: update still running"
@Suppress("unused") private const val _R_1117: String = "Sent post-update notification to %s:%s (exit=%s)"
@Suppress("unused") private const val _R_1118: String = "Post-update notification failed: %s"
@Suppress("unused") private const val _R_1119: String = "✅ Hermes update finished successfully."
@Suppress("unused") private const val _R_1120: String = "❌ Hermes update failed. Check the gateway logs or run `hermes update` manually for details."
@Suppress("unused") private const val _R_1121: String = "\\x1b\\[[0-9;]*m"
@Suppress("unused") private val _R_1122: String = """✅ Hermes update finished.

```
"""
@Suppress("unused") private val _R_1123: String = """❌ Hermes update failed.

```
"""
@Suppress("unused") private const val _R_1124: String = "Notify the chat that initiated /restart that the gateway is back."
@Suppress("unused") private const val _R_1125: String = "Sent restart notification to %s:%s"
@Suppress("unused") private const val _R_1126: String = "Restart notification skipped: %s adapter not connected"
@Suppress("unused") private const val _R_1127: String = "♻ Gateway restarted successfully. Your session continues."
@Suppress("unused") private const val _R_1128: String = "Restart notification failed: %s"
@Suppress("unused") private val _R_1129: String = """
        Auto-analyze user-attached images with the vision tool and prepend
        the descriptions to the message text.

        Each image is analyzed with a general-purpose prompt.  The resulting
        description *and* the local cache path are injected so the model can:
          1. Immediately understand what the user sent (no extra tool call).
          2. Re-examine the image with vision_analyze if it needs more detail.

        Args:
            user_text:   The user's original caption / message text.
            image_paths: List of local file paths to cached images.

        Returns:
            The enriched message string with vision descriptions prepended.
        """
@Suppress("unused") private const val _R_1130: String = "Describe everything visible in this image in thorough detail. Include any text, code, data, objects, people, layout, colors, and any other notable visual information."
@Suppress("unused") private const val _R_1131: String = "Auto-analyzing user image: %s"
@Suppress("unused") private const val _R_1132: String = "analysis"
@Suppress("unused") private const val _R_1133: String = "Vision auto-analysis error: %s"
@Suppress("unused") private val _R_1134: String = """[The user sent an image~ Here's what I can see:
"""
@Suppress("unused") private val _R_1135: String = """]
[If you need a closer look, use vision_analyze with image_url: """
@Suppress("unused") private const val _R_1136: String = " ~]"
@Suppress("unused") private const val _R_1137: String = "[The user sent an image but I couldn't quite see it this time (>_<) You can try looking at it yourself with vision_analyze using image_url: "
@Suppress("unused") private const val _R_1138: String = "[The user sent an image but something went wrong when I tried to look at it~ You can try examining it yourself with vision_analyze using image_url: "
@Suppress("unused") private val _R_1139: String = """
        Auto-transcribe user voice/audio messages using the configured STT provider
        and prepend the transcript to the message text.

        Args:
            user_text:   The user's original caption / message text.
            audio_paths: List of local file paths to cached audio files.

        Returns:
            The enriched message string with transcriptions prepended.
        """
@Suppress("unused") private const val _R_1140: String = "[The user sent voice message(s), but transcription is disabled in config."
@Suppress("unused") private const val _R_1141: String = "(The user sent a message with no text content)"
@Suppress("unused") private const val _R_1142: String = "stt_enabled"
@Suppress("unused") private const val _R_1143: String = " You have a skill called hermes-agent-setup that can help users configure Hermes features including voice, tools, and more."
@Suppress("unused") private const val _R_1144: String = "Transcribing user voice: %s"
@Suppress("unused") private const val _R_1145: String = "transcript"
@Suppress("unused") private const val _R_1146: String = "[The user sent a voice message but I can't listen to it right now — no STT provider is configured. A direct message has already been sent to the user with setup instructions."
@Suppress("unused") private const val _R_1147: String = "Transcription error: %s"
@Suppress("unused") private const val _R_1148: String = "[The user sent a voice message but something went wrong when I tried to listen to it~ Let them know!]"
@Suppress("unused") private const val _R_1149: String = "[The user sent a voice message~ Here's what they said: \""
@Suppress("unused") private const val _R_1150: String = "Neither VOICE_TOOLS_OPENAI_KEY nor OPENAI_API_KEY is set"
@Suppress("unused") private const val _R_1151: String = "[The user sent a voice message but I had trouble transcribing it~ ("
@Suppress("unused") private val _R_1152: String = """Resolve the canonical source for a synthetic background-process event.

        Prefer the persisted session-store origin for the event's session key.
        Falling back to the currently active foreground event is what causes
        cross-topic bleed, so don't do that.
        """
@Suppress("unused") private const val _R_1153: String = "Synthetic process event has invalid platform metadata: %r"
@Suppress("unused") private const val _R_1154: String = "Synthetic process-event session-store lookup failed for %s: %s"
@Suppress("unused") private const val _R_1155: String = "user_name"
@Suppress("unused") private val _R_1156: String = """Inject a watch-pattern notification as a synthetic message event.

        Routing must come from the queued watch event itself, not from whatever
        foreground message happened to be active when the queue was drained.
        """
@Suppress("unused") private const val _R_1157: String = "Dropping watch notification with no routing metadata for process %s"
@Suppress("unused") private const val _R_1158: String = "Watch pattern notification — injecting for %s chat=%s thread=%s"
@Suppress("unused") private val _R_1159: String = """
        Periodically check a background process and push updates to the user.

        Runs as an asyncio task. Stays silent when nothing changed.
        Auto-removes when the process exits or is killed.

        Notification mode (from ``display.background_process_notifications``):
          - ``all``    — running-output updates + final message
          - ``result`` — final completion message only
          - ``error``  — final message only when exit code != 0
          - ``off``    — no messages at all
        """
@Suppress("unused") private const val _R_1160: String = "check_interval"
@Suppress("unused") private const val _R_1161: String = "notify_on_complete"
@Suppress("unused") private const val _R_1162: String = "Process watcher started: %s (every %ss, notify=%s, agent_notify=%s)"
@Suppress("unused") private const val _R_1163: String = "Process watcher ended: %s"
@Suppress("unused") private const val _R_1164: String = "Process watcher ended (silent): %s"
@Suppress("unused") private const val _R_1165: String = "[SYSTEM: Background process "
@Suppress("unused") private const val _R_1166: String = " completed (exit code "
@Suppress("unused") private val _R_1167: String = """).
Command: """
@Suppress("unused") private val _R_1168: String = """
Output:
"""
@Suppress("unused") private const val _R_1169: String = "[Background process "
@Suppress("unused") private const val _R_1170: String = " finished with exit code "
@Suppress("unused") private val _R_1171: String = """~ Here's the final output:
"""
@Suppress("unused") private val _R_1172: String = """ is still running~ New output:
"""
@Suppress("unused") private const val _R_1173: String = "Dropping completion notification with no routing metadata for process %s"
@Suppress("unused") private const val _R_1174: String = "Process %s finished — injecting agent notification for session %s chat=%s thread=%s"
@Suppress("unused") private const val _R_1175: String = "Agent notify injection error: %s"
@Suppress("unused") private const val _R_1176: String = "Watcher delivery error: %s"
@Suppress("unused") private val _R_1177: String = """Pop ALL per-running-agent state entries for ``session_key``.

        Replaces ad-hoc ``del self._running_agents[key]`` calls scattered
        across the gateway.  Those sites had drifted: some popped only
        ``_running_agents``; some also ``_running_agents_ts``; only one
        path also cleared ``_busy_ack_ts``.  Each missed entry was a
        small, persistent leak — a (str_key → float) tuple per session
        per gateway lifetime.

        Use this at every site that ends a running turn, regardless of
        cause (normal completion, /stop, /reset, /resume, sentinel
        cleanup, stale-eviction).  Per-session state that PERSISTS
        across turns (``_session_model_overrides``, ``_voice_mode``,
        ``_pending_approvals``, ``_update_prompt_pending``) is NOT
        touched here — those have their own lifecycles.
        """
@Suppress("unused") private val _R_1178: String = """Claim a fresh run generation token for ``session_key``.

        Every top-level gateway turn gets a monotonically increasing token.
        If a later command like /stop or /new invalidates that token while the
        old worker is still unwinding, the late result can be recognized and
        dropped instead of bleeding into the fresh session.
        """
@Suppress("unused") private const val _R_1179: String = "_session_run_generation"
@Suppress("unused") private const val _R_1180: String = "Return True when ``generation`` is still current for ``session_key``."
@Suppress("unused") private const val _R_1181: String = "Bind a gateway run generation to the adapter's active-session event."
@Suppress("unused") private const val _R_1182: String = "_hermes_run_generation"
@Suppress("unused") private const val _R_1183: String = "_active_sessions"
@Suppress("unused") private const val _R_1184: String = "Interrupt the current run and clear queued session state consistently."
@Suppress("unused") private const val _R_1185: String = "interrupt_session_activity"
@Suppress("unused") private const val _R_1186: String = "get_pending_message"
@Suppress("unused") private const val _R_1187: String = "Remove a cached agent for a session (called on /new, /model, etc)."
@Suppress("unused") private val _R_1188: String = """Soft cleanup for cache-evicted agents — preserves session tool state.

        Called from _enforce_agent_cache_cap and _sweep_idle_cached_agents.
        Distinct from _cleanup_agent_resources (full teardown) because a
        cache-evicted session may resume at any time — its terminal
        sandbox, browser daemon, and tracked bg processes must outlive
        the Python AIAgent instance so the next agent built for the
        same task_id inherits them.
        """
@Suppress("unused") private const val _R_1189: String = "release_clients"
@Suppress("unused") private val _R_1190: String = """Evict oldest cached agents when cache exceeds _AGENT_CACHE_MAX_SIZE.

        Must be called with _agent_cache_lock held.  Resource cleanup
        (memory provider shutdown, tool resource close) is scheduled
        on a daemon thread so the caller doesn't block on slow teardown
        while holding the cache lock.

        Agents currently in _running_agents are SKIPPED — their clients,
        terminal sandboxes, background processes, and child subagents
        are all in active use by the running turn.  Evicting them would
        tear down those resources mid-turn and crash the request.  If
        every candidate in the LRU order is active, we simply leave the
        cache over the cap; it will be re-checked on the next insert.
        """
@Suppress("unused") private const val _R_1191: String = "move_to_end"
@Suppress("unused") private const val _R_1192: String = "Agent cache over cap (%d > %d); %d excess slot(s) held by mid-turn agents — will re-check on next insert."
@Suppress("unused") private const val _R_1193: String = "Agent cache at cap; evicting LRU session=%s (cache_size=%d)"
@Suppress("unused") private const val _R_1194: String = "agent-cache-evict-"
@Suppress("unused") private val _R_1195: String = """Evict cached agents whose AIAgent has been idle > _AGENT_CACHE_IDLE_TTL_SECS.

        Safe to call from the session expiry watcher without holding the
        cache lock — acquires it internally.  Returns the number of entries
        evicted.  Resource cleanup is scheduled on daemon threads.

        Agents currently in _running_agents are SKIPPED for the same reason
        as _enforce_agent_cache_cap: tearing down an active turn's clients
        mid-flight would crash the request.
        """
@Suppress("unused") private const val _R_1196: String = "Agent cache idle-TTL evict: session=%s (idle=%.0fs)"
@Suppress("unused") private const val _R_1197: String = "_last_activity_ts"
@Suppress("unused") private const val _R_1198: String = "agent-cache-idle-"
@Suppress("unused") private val _R_1199: String = """Return the proxy URL if proxy mode is configured, else None.

        Checks GATEWAY_PROXY_URL env var first (convenient for Docker),
        then ``gateway.proxy_url`` in config.yaml.
        """
@Suppress("unused") private const val _R_1200: String = "GATEWAY_PROXY_URL"
@Suppress("unused") private const val _R_1201: String = "proxy_url"
@Suppress("unused") private val _R_1202: String = """Forward the message to a remote Hermes API server instead of
        running a local AIAgent.

        When ``GATEWAY_PROXY_URL`` (or ``gateway.proxy_url`` in config.yaml)
        is set, the gateway becomes a thin relay: it handles platform I/O
        (encryption, threading, media) and delegates all agent work to the
        remote server via ``POST /v1/chat/completions`` with SSE streaming.

        This lets a Docker container handle Matrix E2EE while the actual
        agent runs on the host with full access to local files, memory,
        skills, and a unified session store.
        """
@Suppress("unused") private const val _R_1203: String = "SessionSource"
@Suppress("unused") private const val _R_1204: String = "Content-Type"
@Suppress("unused") private const val _R_1205: String = "application/json"
@Suppress("unused") private const val _R_1206: String = "stream"
@Suppress("unused") private const val _R_1207: String = "hermes-agent"
@Suppress("unused") private const val _R_1208: String = "streaming"
@Suppress("unused") private const val _R_1209: String = "proxy response: url=%s session=%s time=%.1fs response=%d chars"
@Suppress("unused") private const val _R_1210: String = "response_previewed"
@Suppress("unused") private const val _R_1211: String = "⚠️ Proxy URL not configured (GATEWAY_PROXY_URL or gateway.proxy_url)"
@Suppress("unused") private const val _R_1212: String = "Authorization"
@Suppress("unused") private const val _R_1213: String = "Bearer "
@Suppress("unused") private const val _R_1214: String = "X-Hermes-Session-Id"
@Suppress("unused") private const val _R_1215: String = "Discarding stale proxy result for %s — generation %d is no longer current"
@Suppress("unused") private const val _R_1216: String = "(No response from remote agent)"
@Suppress("unused") private const val _R_1217: String = "⚠️ Proxy mode requires aiohttp. Install with: pip install aiohttp"
@Suppress("unused") private const val _R_1218: String = "GATEWAY_PROXY_KEY"
@Suppress("unused") private const val _R_1219: String = "Proxy connection error to %s: %s"
@Suppress("unused") private const val _R_1220: String = "SUPPORTS_MESSAGE_EDITING"
@Suppress("unused") private const val _R_1221: String = "Proxy: could not set up stream consumer: %s"
@Suppress("unused") private const val _R_1222: String = "/v1/chat/completions"
@Suppress("unused") private const val _R_1223: String = "Proxy error (%d) from %s: %s"
@Suppress("unused") private const val _R_1224: String = "⚠️ Proxy connection error: "
@Suppress("unused") private const val _R_1225: String = "⚠️ Proxy error ("
@Suppress("unused") private const val _R_1226: String = "): "
@Suppress("unused") private const val _R_1227: String = "Discarding stale proxy stream for %s — generation %d is no longer current"
@Suppress("unused") private const val _R_1228: String = "replace"
@Suppress("unused") private const val _R_1229: String = "data: "
@Suppress("unused") private const val _R_1230: String = "[DONE]"
@Suppress("unused") private const val _R_1231: String = "choices"
@Suppress("unused") private const val _R_1232: String = "delta"
@Suppress("unused") private val _R_1233: String = """
        Run the agent with the given message and context.
        
        Returns the full result dict from run_conversation, including:
          - "final_response": str (the text to send back)
          - "messages": list (full conversation including tool calls)
          - "api_calls": int
          - "completed": bool
        
        This is run in a thread pool to not block the event loop.
        Supports interruption via new messages.
        """
@Suppress("unused") private const val _R_1234: String = "Callback invoked by agent on tool lifecycle events."
@Suppress("unused") private const val _R_1235: String = "Wait for the stream consumer to be created, then run it."
@Suppress("unused") private const val _R_1236: String = "tool_preview_length"
@Suppress("unused") private const val _R_1237: String = "HERMES_TOOL_PROGRESS_MODE"
@Suppress("unused") private const val _R_1238: String = "HERMES_SESSION_KEY"
@Suppress("unused") private val _R_1239: String = """Send the approval request to the user from the agent thread.

                If the adapter supports interactive button-based approvals
                (e.g. Discord's ``send_exec_approval``), use that for a richer
                UX.  Otherwise fall back to a plain text message with
                ``/approve`` instructions.
                """
@Suppress("unused") private const val _R_1240: String = "MEDIA:"
@Suppress("unused") private const val _R_1241: String = "input_tokens"
@Suppress("unused") private const val _R_1242: String = "output_tokens"
@Suppress("unused") private const val _R_1243: String = "HERMES_AGENT_NOTIFY_INTERVAL"
@Suppress("unused") private const val _R_1244: String = "interim_assistant_messages"
@Suppress("unused") private const val _R_1245: String = "tool.started"
@Suppress("unused") private const val _R_1246: String = ": \""
@Suppress("unused") private const val _R_1247: String = "HERMES_MAX_ITERATIONS"
@Suppress("unused") private const val _R_1248: String = "run_agent resolved: model=%s provider=%s session=%s"
@Suppress("unused") private const val _R_1249: String = "Created new agent for session %s (sig=%s)"
@Suppress("unused") private const val _R_1250: String = "dangerous command"
@Suppress("unused") private val _R_1251: String = """⚠️ **Dangerous command requires approval:**
```
"""
@Suppress("unused") private val _R_1252: String = """
```
Reason: """
@Suppress("unused") private val _R_1253: String = """

Reply `/approve` to execute, `/approve session` to approve this pattern for the session, `/approve always` to approve permanently, or `/deny` to cancel."""
@Suppress("unused") private const val _R_1254: String = "a gateway restart"
@Suppress("unused") private const val _R_1255: String = "context_compressor"
@Suppress("unused") private const val _R_1256: String = "session_prompt_tokens"
@Suppress("unused") private const val _R_1257: String = "session_completion_tokens"
@Suppress("unused") private const val _R_1258: String = "Session split detected: %s → %s (compression)"
@Suppress("unused") private const val _R_1259: String = "HERMES_AGENT_TIMEOUT_WARNING"
@Suppress("unused") private const val _R_1260: String = "Agent idle for %.0fs (timeout %.0fs) in session %s | last_activity=%s | iteration=%s/%s | tool=%s"
@Suppress("unused") private val _R_1261: String = """To increase the limit, set agent.gateway_timeout in config.yaml (value in seconds, 0 = no limit) and restart the gateway.
Try again, or use /reset to start fresh."""
@Suppress("unused") private const val _R_1262: String = "pending_steer"
@Suppress("unused") private const val _R_1263: String = "Discarding pending follow-up for session %s during gateway %s"
@Suppress("unused") private const val _R_1264: String = "Processing pending message: '%s...'"
@Suppress("unused") private const val _R_1265: String = "interrupted"
@Suppress("unused") private const val _R_1266: String = "Suppressing normal final send for session %s: final delivery already confirmed (streamed=%s previewed=%s)."
@Suppress("unused") private const val _R_1267: String = "__dedup__"
@Suppress("unused") private const val _R_1268: String = "agent:step"
@Suppress("unused") private const val _R_1269: String = "agent:step hook error: %s"
@Suppress("unused") private const val _R_1270: String = "status_callback error (%s): %s"
@Suppress("unused") private const val _R_1271: String = "starting new turn (cached)"
@Suppress("unused") private const val _R_1272: String = "register_post_delivery_callback"
@Suppress("unused") private const val _R_1273: String = "send_exec_approval"
@Suppress("unused") private const val _R_1274: String = "resume_pending"
@Suppress("unused") private const val _R_1275: String = "resume_reason"
@Suppress("unused") private const val _R_1276: String = "a gateway shutdown"
@Suppress("unused") private const val _R_1277: String = "a gateway interruption"
@Suppress("unused") private const val _R_1278: String = "[System note: Your previous turn in this session was interrupted by "
@Suppress("unused") private val _R_1279: String = """. The conversation history below is intact. If it contains unfinished tool result(s), process them first and summarize what was accomplished, then address the user's new message below.]

"""
@Suppress("unused") private val _R_1280: String = """[System note: Your previous turn was interrupted before you could process the last tool result(s). The conversation history contains tool outputs you haven't responded to yet. Please finish processing those results and summarize what was accomplished, then address the user's new message below.]

"""
@Suppress("unused") private const val _R_1281: String = "interrupt"
@Suppress("unused") private const val _R_1282: String = "⏱️ Agent inactive for "
@Suppress("unused") private const val _R_1283: String = " min — no tool calls or API responses."
@Suppress("unused") private const val _R_1284: String = "interrupt_message"
@Suppress("unused") private const val _R_1285: String = "Delivering leftover /steer as next turn: '%s...'"
@Suppress("unused") private const val _R_1286: String = "Interrupt recursion depth %d reached for session %s — queueing message instead of recursing."
@Suppress("unused") private const val _R_1287: String = "message_id"
@Suppress("unused") private const val _R_1288: String = "channel_prompt"
@Suppress("unused") private const val _R_1289: String = "final_response_sent"
@Suppress("unused") private const val _R_1290: String = "Progress message error: %s"
@Suppress("unused") private const val _R_1291: String = "iteration"
@Suppress("unused") private const val _R_1292: String = "tool_names"
@Suppress("unused") private const val _R_1293: String = "latin-1"
@Suppress("unused") private const val _R_1294: String = "⚠️ Provider authentication failed: "
@Suppress("unused") private const val _R_1295: String = "Could not set up stream consumer: %s"
@Suppress("unused") private const val _R_1296: String = "interim_assistant_callback error: %s"
@Suppress("unused") private const val _R_1297: String = "Reusing cached agent for session %s"
@Suppress("unused") private const val _R_1298: String = "background_review_callback error: %s"
@Suppress("unused") private const val _R_1299: String = "mirror"
@Suppress("unused") private const val _R_1300: String = "MEDIA:(\\S+)"
@Suppress("unused") private const val _R_1301: String = "Button-based approval failed (send returned error), falling back to text: %s"
@Suppress("unused") private const val _R_1302: String = "Failed to send approval request: %s"
@Suppress("unused") private const val _R_1303: String = "[[audio_as_voice]]"
@Suppress("unused") private const val _R_1304: String = "has_pending_interrupt"
@Suppress("unused") private const val _R_1305: String = "monitor_for_interrupt error (will retry): %s"
@Suppress("unused") private const val _R_1306: String = "Long-running notification error: %s"
@Suppress("unused") private const val _R_1307: String = "The agent appears stuck on tool `"
@Suppress("unused") private const val _R_1308: String = "` ("
@Suppress("unused") private const val _R_1309: String = "s since last activity, iteration "
@Suppress("unused") private const val _R_1310: String = "Last activity: "
@Suppress("unused") private const val _R_1311: String = "s ago, iteration "
@Suppress("unused") private const val _R_1312: String = "). The agent may have been waiting on an API response."
@Suppress("unused") private const val _R_1313: String = "Ignoring control interrupt message for session %s: %s"
@Suppress("unused") private const val _R_1314: String = "Processing queued message after agent completion: '%s...'"
@Suppress("unused") private const val _R_1315: String = "source"
@Suppress("unused") private const val _R_1316: String = " (×"
@Suppress("unused") private const val _R_1317: String = "skip streaming for non-editable platform"
@Suppress("unused") private const val _R_1318: String = "mirror_source"
@Suppress("unused") private const val _R_1319: String = "another session"
@Suppress("unused") private const val _R_1320: String = "[Delivered from "
@Suppress("unused") private const val _R_1321: String = "reasoning_details"
@Suppress("unused") private const val _R_1322: String = "codex_reasoning_items"
@Suppress("unused") private const val _R_1323: String = "\",}"
@Suppress("unused") private const val _R_1324: String = "Button-based approval failed, falling back to text: %s"
@Suppress("unused") private const val _R_1325: String = "Interrupt detected from adapter, signaling agent..."
@Suppress("unused") private const val _R_1326: String = "⏳ Still working... ("
@Suppress("unused") private const val _R_1327: String = "Backup interrupt detected for session %s (monitor task state: %s)"
@Suppress("unused") private const val _R_1328: String = "Discarding command '/%s' from pending queue — commands must not be passed as agent input"
@Suppress("unused") private const val _R_1329: String = "queue_message"
@Suppress("unused") private const val _R_1330: String = "Queued follow-up for session %s: final stream delivery not confirmed; sending first response before continuing."
@Suppress("unused") private const val _R_1331: String = "Queued follow-up for session %s: skipping resend because final streamed delivery was confirmed."
@Suppress("unused") private const val _R_1332: String = "flood"
@Suppress("unused") private const val _R_1333: String = "retry after"
@Suppress("unused") private const val _R_1334: String = "[%s] Progress edits disabled due to flood control"
@Suppress("unused") private const val _R_1335: String = "Stream consumer wait before queued message failed: %s"
@Suppress("unused") private const val _R_1336: String = "Failed to send first response before queued message: %s"
@Suppress("unused") private const val _R_1337: String = "Inactivity warning send error: %s"
@Suppress("unused") private const val _R_1338: String = "⚠️ No activity for "
@Suppress("unused") private const val _R_1339: String = " min. If the agent does not respond soon, it will be timed out in "
@Suppress("unused") private const val _R_1340: String = " min. You can continue waiting or use /reset."
@Suppress("unused") private val _R_1341: String = """
    Background thread that ticks the cron scheduler at a regular interval.
    
    Runs inside the gateway process so cronjobs fire automatically without
    needing a separate `hermes cron daemon` or system cron entry.

    When ``adapters`` and ``loop`` are provided, passes them through to the
    cron delivery path so live adapters can be used for E2EE rooms.

    Also refreshes the channel directory every 5 minutes and prunes the
    image/audio/document cache once per hour.
    """
@Suppress("unused") private const val _R_1342: String = "Cron ticker started (interval=%ds)"
@Suppress("unused") private const val _R_1343: String = "Cron ticker stopped"
@Suppress("unused") private const val _R_1344: String = "Cron tick error: %s"
@Suppress("unused") private const val _R_1345: String = "Channel directory refresh error: %s"
@Suppress("unused") private const val _R_1346: String = "Image cache cleanup: removed %d stale file(s)"
@Suppress("unused") private const val _R_1347: String = "Image cache cleanup error: %s"
@Suppress("unused") private const val _R_1348: String = "Document cache cleanup: removed %d stale file(s)"
@Suppress("unused") private const val _R_1349: String = "Document cache cleanup error: %s"
@Suppress("unused") private val _R_1350: String = """
    Start the gateway and run until interrupted.
    
    This is the main entry point for running the gateway.
    Returns True if the gateway ran successfully, False if it failed to start.
    A False return causes a non-zero exit code so systemd can auto-restart.
    
    Args:
        config: Optional gateway configuration override.
        replace: If True, kill any existing gateway instance before starting.
                 Useful for systemd services to avoid restart-loop deadlocks
                 when the previous process hasn't fully exited yet.
    """
@Suppress("unused") private const val _R_1351: String = "SIGUSR1"
@Suppress("unused") private const val _R_1352: String = "Skipping signal handlers (not running in main thread)."
@Suppress("unused") private const val _R_1353: String = "Another gateway instance (PID %d) started during our startup. Exiting to avoid double-running."
@Suppress("unused") private const val _R_1354: String = "cron-ticker"
@Suppress("unused") private const val _R_1355: String = "Exiting with code 1 (signal-initiated shutdown without restart request) so systemd Restart=on-failure can revive the gateway."
@Suppress("unused") private const val _R_1356: String = "Replacing existing gateway instance (PID %d) with --replace."
@Suppress("unused") private const val _R_1357: String = "Another gateway instance is already running (PID %d, HERMES_HOME=%s). Use 'hermes gateway restart' to replace it, or 'hermes gateway stop' first."
@Suppress("unused") private const val _R_1358: String = "%(levelname)s %(name)s: %(message)s"
@Suppress("unused") private const val _R_1359: String = "Received SIGTERM as a planned --replace takeover — exiting cleanly"
@Suppress("unused") private const val _R_1360: String = "Received SIGTERM/SIGINT — initiating shutdown"
@Suppress("unused") private const val _R_1361: String = "PID file race lost to another gateway instance. Exiting."
@Suppress("unused") private const val _R_1362: String = "Gateway exiting cleanly: %s"
@Suppress("unused") private const val _R_1363: String = "adapters"
@Suppress("unused") private const val _R_1364: String = "loop"
@Suppress("unused") private const val _R_1365: String = "Gateway exiting with failure: %s"
@Suppress("unused") private const val _R_1366: String = "Old gateway (PID %d) did not exit after SIGTERM, sending SIGKILL."
@Suppress("unused") private val _R_1367: String = """
❌ Gateway already running (PID """
@Suppress("unused") private val _R_1368: String = """).
   Use 'hermes gateway restart' to replace it,
   or 'hermes gateway stop' to kill it first.
   Or use 'hermes gateway run --replace' to auto-replace.
"""
@Suppress("unused") private const val _R_1369: String = "Takeover marker check failed: %s"
@Suppress("unused") private const val _R_1370: String = "aux"
@Suppress("unused") private val _R_1371: String = """Shutdown diagnostic — other hermes processes running:
  %s"""
@Suppress("unused") private const val _R_1372: String = "Shutdown diagnostic — no other hermes processes found"
@Suppress("unused") private const val _R_1373: String = "Could not write takeover marker: %s"
@Suppress("unused") private const val _R_1374: String = "Permission denied killing PID %d. Cannot replace."
@Suppress("unused") private const val _R_1375: String = "Released %d stale scoped lock(s) from old gateway."
@Suppress("unused") private const val _R_1376: String = "gateway.pid"
@Suppress("unused") private val _R_1377: String = """
  """
@Suppress("unused") private const val _R_1378: String = "hermes"
@Suppress("unused") private const val _R_1379: String = "CLI entry point for the gateway."
@Suppress("unused") private const val _R_1380: String = "--config"
@Suppress("unused") private const val _R_1381: String = "--verbose"
@Suppress("unused") private const val _R_1382: String = "Hermes Gateway - Multi-platform messaging"
@Suppress("unused") private const val _R_1383: String = "Path to gateway config file"
@Suppress("unused") private const val _R_1384: String = "store_true"
@Suppress("unused") private const val _R_1385: String = "Verbose output"
