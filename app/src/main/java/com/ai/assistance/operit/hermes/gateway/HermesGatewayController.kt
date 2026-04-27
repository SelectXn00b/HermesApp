package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ModelConfigDefaults
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives a single [GatewayRunner] instance on behalf of
 * [com.ai.assistance.operit.services.gateway.GatewayForegroundService].
 *
 * Owns a supervisor scope on [Dispatchers.IO] so platform adapter
 * connect/disconnect work does not block the service's main thread.
 * Settings UI observes [status] to render start/stop state live.
 */
class HermesGatewayController private constructor(private val appContext: Context) {

    enum class Status { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var runner: GatewayRunner? = null
    private val _scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _mutex = Mutex()

    suspend fun start(): Boolean = _mutex.withLock {
        if (_status.value == Status.RUNNING || _status.value == Status.STARTING) return@withLock true
        _status.value = Status.STARTING
        _error.value = null
        try {
            val config = HermesGatewayConfigBuilder.build(appContext)
            if (config.enabledPlatforms.isEmpty()) {
                _status.value = Status.FAILED
                _error.value = "no enabled platforms with credentials"
                Log.w(TAG, "start(): no enabled platforms — refusing to start")
                return@withLock false
            }
            val instance = GatewayRunner(appContext, config)
            instance.agentRunner = { text, sessionKey, platform, chatId, userId ->
                runHermesAgent(text = text, sessionKey = sessionKey, chatId = chatId)
            }
            runner = instance
            instance.start()
            _status.value = Status.RUNNING
            Log.i(TAG, "gateway started with ${config.enabledPlatforms.size} platform(s)")
            true
        } catch (e: Throwable) {
            _status.value = Status.FAILED
            _error.value = e.message
            Log.e(TAG, "start() failed", e)
            runner = null
            false
        }
    }

    suspend fun stop() = _mutex.withLock {
        val instance = runner ?: run {
            _status.value = Status.STOPPED
            return@withLock
        }
        _status.value = Status.STOPPING
        try {
            instance.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "stop() threw: ${e.message}")
        } finally {
            runner = null
            _status.value = Status.STOPPED
        }
    }

    /** Fire-and-forget start used by service onStartCommand. */
    fun startAsync(): Job = _scope.launch { start() }

    /** Fire-and-forget stop used by service onDestroy. */
    fun stopAsync(): Job = _scope.launch { stop() }

    /**
     * Feed [text] into the app's [EnhancedAIService] — the same path the
     * in-app chat dialog uses — so the gateway gets identical tool capabilities,
     * system prompts, memory, and prompt hooks.  Collects all streamed chunks
     * and strips internal markup so the gateway only echoes user-visible plain
     * text back to the platform.
     */
    private suspend fun runHermesAgent(
        text: String,
        sessionKey: String,
        chatId: String,
    ): String {
        val historyChatId = "gw:$sessionKey:$chatId"
        val service = EnhancedAIService.getInstance(appContext)
        val history = ChatHistoryManager.getInstance(appContext)

        // Persist the inbound user message
        try {
            history.ensureChatWithId(historyChatId, title = gatewayChatTitle(sessionKey, chatId))
            history.addMessage(historyChatId, ChatMessage(sender = "user", content = text))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist inbound gateway message: ${e.message}")
        }

        // Load prior chat history so the AI retains context across messages
        val chatHistory = try {
            val msgs = history.loadChatMessages(historyChatId)
            // Drop the last message (the one we just added) — sendMessage
            // appends the current user message itself.
            val msgsWithoutCurrent = if (msgs.isNotEmpty()) msgs.dropLast(1) else msgs
            AIMessageManager.getMemoryFromMessages(messages = msgsWithoutCurrent)
        } catch (e: Throwable) {
            Log.w(TAG, "failed to load chat history, proceeding without: ${e.message}")
            emptyList()
        }

        val prefs = HermesGatewayPreferences.getInstance(appContext)
        val maxTurns = prefs.agentMaxTurnsFlow.first()
        val timeoutMs = maxTurns.toLong() * 120_000L

        // Use model config defaults for token budget
        val maxTokens = (ModelConfigDefaults.DEFAULT_CONTEXT_LENGTH * 1024).toInt()
        val tokenUsageThreshold = ModelConfigDefaults.DEFAULT_SUMMARY_TOKEN_THRESHOLD.toDouble()

        Log.i(TAG, "runHermesAgent: text=${text.take(80)} chatId=$historyChatId " +
            "historyTurns=${chatHistory.size} " +
            "timeoutMs=$timeoutMs maxTurns=$maxTurns maxTokens=$maxTokens")

        val startMs = System.currentTimeMillis()
        val stream = service.sendMessage(
            message = text,
            chatId = historyChatId,
            chatHistory = chatHistory,
            maxTokens = maxTokens,
            tokenUsageThreshold = tokenUsageThreshold,
            isSubTask = true
        )
        val raw = StringBuilder()
        val completed = withTimeoutOrNull(timeoutMs) {
            stream.collect { chunk ->
                raw.append(chunk)
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "runHermesAgent: chunk len=${chunk.length} totalLen=${raw.length}")
                }
            }
            true
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        if (completed == null) {
            Log.w(TAG, "runHermesAgent: TIMED OUT after ${elapsedMs}ms " +
                "collectedLen=${raw.length}")
        } else {
            Log.i(TAG, "runHermesAgent: completed in ${elapsedMs}ms " +
                "rawLen=${raw.length}")
        }

        val rawText = raw.toString()
        // Save the full raw AI response (with tool markup) to history so
        // future calls can reconstruct the complete conversation context.
        val strippedReply = stripInternalMarkup(rawText).trim().ifEmpty {
            if (completed == null) "(agent timed out)" else "(empty response)"
        }

        try {
            history.addMessage(historyChatId, ChatMessage(sender = "ai", content = rawText))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist outbound gateway reply: ${e.message}")
        }

        // Return stripped text to the platform (no XML markup)
        return strippedReply
    }

    private fun gatewayChatTitle(sessionKey: String, chatId: String): String {
        val platform = sessionKey.substringBefore(':').ifEmpty { sessionKey }
        val shortChat = chatId.substringBefore('@').take(24).ifEmpty { chatId.take(24) }
        return "$platform: $shortChat"
    }

    private fun stripInternalMarkup(xml: String): String {
        if (xml.isEmpty()) return xml
        return xml
            .replace(ChatMarkupRegex.thinkTag, "")
            .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
    }

    companion object {
        private const val TAG = "HermesGatewayCtl"

        @Volatile private var INSTANCE: HermesGatewayController? = null

        fun getInstance(context: Context): HermesGatewayController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HermesGatewayController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
