package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.hermes.HermesAdapter
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     * Feed [text] into the app's HermesAdapter, collect all streamed chunks,
     * and strip internal `<think>` / `<tool>` / `<tool_result>` / `<status>`
     * markup so the gateway only echoes the user-visible plain text back to
     * the platform. Also persists both the inbound user message and the
     * outbound assistant reply into [ChatHistoryManager] so the session
     * surfaces in the main 会话记录 UI alongside in-app chats.
     */
    private suspend fun runHermesAgent(
        text: String,
        sessionKey: String,
        chatId: String,
    ): String {
        val historyChatId = "gw:$sessionKey:$chatId"
        val adapter = HermesAdapter.getInstance(appContext)
        val history = ChatHistoryManager.getInstance(appContext)

        try {
            history.ensureChatWithId(historyChatId, title = gatewayChatTitle(sessionKey, chatId))
            history.addMessage(historyChatId, ChatMessage(sender = "user", content = text))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist inbound gateway message: ${e.message}")
        }

        val stream = adapter.sendMessage(message = text, chatId = historyChatId)
        val raw = StringBuilder()
        stream.collect { raw.append(it) }
        val reply = stripInternalMarkup(raw.toString()).trim().ifEmpty { "(empty response)" }

        try {
            history.addMessage(historyChatId, ChatMessage(sender = "ai", content = reply))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist outbound gateway reply: ${e.message}")
        }

        return reply
    }

    private fun gatewayChatTitle(sessionKey: String, chatId: String): String {
        val platform = sessionKey.substringBefore(':').ifEmpty { sessionKey }
        val shortChat = chatId.substringBefore('@').take(24).ifEmpty { chatId.take(24) }
        return "$platform: $shortChat"
    }

    private fun stripInternalMarkup(xml: String): String {
        if (xml.isEmpty()) return xml
        return xml
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<tool_result[^>]*>[\\s\\S]*?</tool_result>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<tool\\s[^>]*>[\\s\\S]*?</tool>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<status[^>]*>[\\s\\S]*?</status>", RegexOption.IGNORE_CASE), "")
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
