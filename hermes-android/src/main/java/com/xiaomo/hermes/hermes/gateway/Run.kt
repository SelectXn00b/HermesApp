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
            Log.w(_TAG, "Gateway already running")
            return
        }

        Log.i(_TAG, "Starting gateway...")

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
    suspend fun stop() {
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
    private suspend fun _createAdapter(platformConfig: PlatformConfig): BasePlatformAdapter? {
        val adapter = when (platformConfig.platform) {
            Platform.FEISHU -> Feishu(context, platformConfig)
            Platform.TELEGRAM -> Telegram(context, platformConfig)
            Platform.DISCORD -> Discord(context, platformConfig)
            Platform.SLACK -> Slack(context, platformConfig)
            Platform.SIGNAL -> Signal(context, platformConfig)
            Platform.WHATSAPP -> WhatsApp(context, platformConfig)
            Platform.WECOM -> WeCom(context, platformConfig)
            Platform.WECOM_CALLBACK -> WeComCallback(context, platformConfig)
            Platform.WEIXIN -> Weixin(context, platformConfig)
            Platform.DINGTALK -> Dingtalk(context, platformConfig)
            Platform.QQBOT -> QQAdapter(context, platformConfig)
            Platform.EMAIL -> Email(context, platformConfig)
            Platform.SMS -> Sms(context, platformConfig)
            Platform.MATRIX -> Matrix(context, platformConfig)
            Platform.MATTERMOST -> Mattermost(context, platformConfig)
            Platform.HOMEASSISTANT -> Homeassistant(context, platformConfig)
            Platform.WEBHOOK -> Webhook(context, platformConfig)
            Platform.API_SERVER -> ApiServer(context, platformConfig)
            Platform.BLUEBUBBLES -> Bluebubbles(context, platformConfig)
            else -> {
                Log.w(_TAG, "Unknown platform: ${platformConfig.platform}")
                return null
            }
        }

        // Set up message handler
        adapter.messageHandler = { event -> _handleMessage(event, adapter) }

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
    private suspend fun _handleMessage(event: MessageEvent, adapter: BasePlatformAdapter) {
        // Acquire session semaphore
        if (!_sessionSemaphore.tryAcquire()) {
            Log.w(_TAG, "Max concurrent sessions reached, dropping message")
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
                Log.i(_TAG, "Message halted by pre-validate hook: ${preValidateResult.reason}")
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
                Log.i(_TAG, "Message halted by post-validate hook: ${postValidateResult.reason}")
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
                Log.i(_TAG, "Message halted by pre-agent hook: ${preAgentResult.reason}")
                return
            }

            // Invoke agent loop - 对齐 hermes-agent/gateway/run.py
            //
            // Android 不运行 gateway runner 这条路径（AppChat adapter 直接
            // 挂在 ChatViewModel 上），这里保留占位以便未来把 HermesAgentLoop
            // 接上。目前永远返回未配置提示。
            val responseText = "Agent loop not configured"

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
                platform = adapter.name,
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
                platform = adapter.name,
                chatId = event.source.chatId,
                text = sendText,
                replyTo = event.message_id)

            // Record send
            if (result.success) {
                status.countersFor(adapter.name).recordSent()
            } else {
                status.countersFor(adapter.name).recordError()
                Log.w(_TAG, "Failed to send response: ${result.error}")
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
        } catch (e: Exception) {
            Log.e(_TAG, "Error handling message: ${e.message}")
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
    fun isUserAuthorized(source: MessageSource): Boolean {
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
    fun getGuildId(source: MessageSource): String? {
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
    /** Run the agent with the given message and context. */
    suspend fun _runAgent(message: String, contextPrompt: String, history: List<Map<String, Any>>, source: SessionSource, sessionId: String, sessionKey: String? = null, _interruptDepth: Int = 0, eventMessageId: String? = null): Map<String, Any> {
        return emptyMap()
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
                    sessionStore.remove(key)
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
    fun requestRestart(): Boolean {
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
                        sessionStore.remove(key)
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
    suspend fun _prepareInboundMessageText(): String? {
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
        val msgSource = source as? MessageSource ?: return
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
    suspend fun _sendUpdateNotification(): Boolean {
        return false
    }

    /** Notify the chat that initiated /restart that the gateway is back. */
    suspend fun _sendRestartNotification() {
        Log.d(_TAG, "Restart notification — gateway is back")
    }

    /** Set session context variables for the current task.
     *  On Android, we don't use contextvars — session context is passed explicitly. */
    fun _setSessionEnv(context: SessionRecord): List<Any?> {
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
        return MessageSource(
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
        sessionStore.get(sessionKey)?.markIdle()
        Log.d(_TAG, "Released running agent state for $sessionKey")
    }

    /** Soft cleanup for cache-evicted agents — preserves session tool state. */
    fun _releaseEvictedAgentSoft(agent: Any?) {
        if (agent == null) return
        _cleanupAgentResources(agent)
    }

    private val _AGENT_CACHE_MAX_SIZE = 20

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

    private val _AGENT_CACHE_IDLE_TTL_SECS = 1800L // 30 minutes

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
                    if (now - lastMsg > _AGENT_CACHE_IDLE_TTL_SECS * 1000) {
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
    suspend fun _runAgentViaProxy(message: String, contextPrompt: String, history: List<Map<String, Any?>>, source: Any?, sessionId: String, sessionKey: String? = null, eventMessageId: String? = null): Map<String, Any?> {
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
}
