package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
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
     *
     * Uses [EnhancedAIService.getChatInstance] with a gateway-specific chat ID
     * so the gateway's execution contexts are fully isolated from the main app.
     * Without this, [EnhancedAIService.cancelConversation] called from the UI
     * (notification cancel, in-app cancel, or app exit) would kill ALL execution
     * contexts — including the gateway's running agent loop — via
     * [invalidateAllExecutionContexts].
     */
    private suspend fun runHermesAgent(
        text: String,
        sessionKey: String,
        chatId: String,
    ): String {
        val historyChatId = "gw:$sessionKey:$chatId"
        // Use a per-chatId instance so the gateway is isolated from the main
        // app's cancel operations (which call invalidateAllExecutionContexts
        // on the singleton).
        val service = EnhancedAIService.getChatInstance(appContext, historyChatId)
        val history = ChatHistoryManager.getInstance(appContext)

        // Persist the inbound user message
        try {
            history.ensureChatWithId(historyChatId, title = gatewayChatTitle(sessionKey, chatId))
            history.addMessage(historyChatId, ChatMessage(sender = "user", content = text))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist inbound gateway message: ${e.message}")
        }

        // Load prior chat history so the AI retains context across messages.
        // Cap to the most recent MAX_HISTORY_MESSAGES to prevent unbounded
        // context growth (which was causing 50k+ input tokens and polluting
        // the model with old "User cancelled" failures).
        val chatHistory = try {
            val allMsgs = history.loadChatMessages(historyChatId)
            // Drop the last message (the one we just added) — sendMessage
            // appends the current user message itself.
            val msgsWithoutCurrent = if (allMsgs.isNotEmpty()) allMsgs.dropLast(1) else allMsgs
            // Keep only the most recent messages to prevent context bloat
            val recentMsgs = if (msgsWithoutCurrent.size > MAX_HISTORY_MESSAGES) {
                Log.i(TAG, "trimming chat history from ${msgsWithoutCurrent.size} to $MAX_HISTORY_MESSAGES messages")
                msgsWithoutCurrent.takeLast(MAX_HISTORY_MESSAGES)
            } else {
                msgsWithoutCurrent
            }
            AIMessageManager.getMemoryFromMessages(messages = recentMsgs)
        } catch (e: Throwable) {
            Log.w(TAG, "failed to load chat history, proceeding without: ${e.message}")
            emptyList()
        }

        val prefs = HermesGatewayPreferences.getInstance(appContext)
        val maxTurns = prefs.agentMaxTurnsFlow.first()
        val timeoutMs = maxTurns.toLong() * 120_000L

        // Read user's thinking mode preferences so gateway matches main app behavior.
        // Safety: thinking guidance and thinking mode are mutually exclusive.
        // When guidance is enabled, we avoid enabling provider-level thinking
        // simultaneously (same guard as MessageCoordinationDelegate).
        val apiPrefs = ApiPreferences.getInstance(appContext)
        val thinkingGuidance = try {
            apiPrefs.enableThinkingGuidanceFlow.first()
        } catch (_: Throwable) { false }
        val enableThinking = try {
            apiPrefs.enableThinkingModeFlow.first() && !thinkingGuidance
        } catch (_: Throwable) { false }

        // Read user's memory query setting (main app reads apiConfigDelegate.enableMemoryQuery)
        val enableMemoryQuery = try {
            apiPrefs.enableMemoryQueryFlow.first()
        } catch (_: Throwable) { true }

        // Read actual model config for CHAT function (same as main app)
        // so gateway uses the user's configured context length and summary
        // threshold instead of hardcoded defaults.
        val modelConfig = try {
            val funcMgr = FunctionalConfigManager(appContext)
            val configId = funcMgr.getConfigIdForFunction(FunctionType.CHAT)
            val cfgMgr = ModelConfigManager(appContext)
            cfgMgr.getModelConfigFlow(configId).first()
        } catch (e: Throwable) {
            Log.w(TAG, "failed to read model config, using defaults: ${e.message}")
            null
        }
        val contextLength = if (modelConfig != null) {
            if (modelConfig.enableMaxContextMode) modelConfig.maxContextLength
            else modelConfig.contextLength
        } else {
            48.0f // fallback default
        }
        val maxTokens = (contextLength * 1024).toInt()
        val tokenUsageThreshold = modelConfig?.summaryTokenThreshold?.toDouble() ?: 0.70

        // Read the user's active role card so gateway uses the same character
        // personality and system prompt sections as the main app.
        val roleCardId = try {
            ActivePromptManager.getInstance(appContext).resolveActiveCardIdForSend()
        } catch (e: Throwable) {
            Log.w(TAG, "failed to read active role card, using default: ${e.message}")
            null
        }

        // Resolve character name and per-role-card model binding (same as
        // MessageCoordinationDelegate.resolveRoleCardChatModelOverrides).
        val characterCardManager = CharacterCardManager.getInstance(appContext)
        val (characterName, chatModelConfigIdOverride, chatModelIndexOverride) = try {
            if (roleCardId != null) {
                val roleCard = characterCardManager.getCharacterCardFlow(roleCardId).first()
                val bindingMode = CharacterCardChatModelBindingMode.normalize(roleCard.chatModelBindingMode)
                val (cfgId, cfgIdx) = if (
                    bindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                    !roleCard.chatModelConfigId.isNullOrBlank()
                ) {
                    Pair(roleCard.chatModelConfigId, roleCard.chatModelIndex.coerceAtLeast(0))
                } else {
                    Pair(null, null)
                }
                Triple(roleCard.name, cfgId, cfgIdx)
            } else {
                Triple(null, null, null)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "failed to read role card details: ${e.message}")
            Triple(null, null, null)
        }

        // Pre-activate Automatic_ui_base so the model knows about UI
        // automation tools without having to discover and call use_package first.
        // This ensures the gateway agent has the same tool awareness as the
        // main app where users typically have the package already activated.
        val packageManager = PackageManager.getInstance(
            appContext, AIToolHandler.getInstance(appContext)
        )
        val uiPackagePrompt = try {
            packageManager.usePackage("Automatic_ui_base")
        } catch (e: Throwable) {
            Log.w(TAG, "failed to pre-activate Automatic_ui_base: ${e.message}")
            null
        }

        // Prepend a synthetic use_package turn to the chat history so the
        // model sees the package tools already available in context.
        val augmentedHistory = buildList {
            if (uiPackagePrompt != null && chatHistory.none {
                    it.kind == PromptTurnKind.TOOL_RESULT &&
                        it.content.contains("Automatic_ui_base")
                }) {
                add(PromptTurn(
                    kind = PromptTurnKind.ASSISTANT,
                    content = "<tool name=\"use_package\"><param name=\"package_name\">Automatic_ui_base</param></tool>"
                ))
                add(PromptTurn(
                    kind = PromptTurnKind.TOOL_RESULT,
                    content = "<tool_result name=\"use_package\">\n$uiPackagePrompt\n</tool_result>"
                ))
            }
            // Inject a synthetic "warmup" exchange so the AI has
            // operational self-awareness from the very first message.
            // Testing showed that when the user first asks "what tools
            // do you have?", subsequent tasks succeed because the AI's
            // own capability summary persists in chat history and guides
            // subsequent tool usage decisions.
            if (chatHistory.isEmpty()) {
                add(PromptTurn(
                    kind = PromptTurnKind.USER,
                    content = "你能操作手机app吗？你有什么工具？"
                ))
                add(PromptTurn(
                    kind = PromptTurnKind.ASSISTANT,
                    content = WARMUP_CAPABILITY_SUMMARY
                ))
            }
            addAll(chatHistory)
            // Gateway-specific: inject a system instruction forbidding
            // wait_for_user_need.  In the main app the user can reply
            // after seeing that status, but the gateway has no such
            // interactive turn — once the agent loop ends the entire
            // call finishes.  This forces the AI to always either call
            // a tool (continue working) or emit <status type="complete">.
            add(PromptTurn(
                kind = PromptTurnKind.SYSTEM,
                content = GATEWAY_AGENT_RULES
            ))
        }

        Log.i(TAG, "runHermesAgent: text=${text.take(80)} chatId=$historyChatId " +
            "historyTurns=${chatHistory.size} " +
            "timeoutMs=$timeoutMs maxTurns=$maxTurns maxTokens=$maxTokens " +
            "roleCardId=$roleCardId contextLength=$contextLength")

        val startMs = System.currentTimeMillis()
        val stream = service.sendMessage(
            message = text,
            chatId = historyChatId,
            chatHistory = augmentedHistory,
            maxTokens = maxTokens,
            tokenUsageThreshold = tokenUsageThreshold,
            enableThinking = enableThinking,
            thinkingGuidance = thinkingGuidance,
            enableMemoryQuery = enableMemoryQuery,
            // Use null (same as main app) so the system prompt auto-selects
            // based on locale and includes all sections (character identity,
            // workspace guidelines, etc.).
            customSystemPromptTemplate = null,
            characterName = characterName,
            roleCardId = roleCardId,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride,
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

        /**
         * Maximum number of raw [ChatMessage]s loaded from history per gateway
         * call.  Keeps context within a reasonable token budget and prevents
         * old failure traces (e.g. "User cancelled") from poisoning the model.
         * 30 messages ≈ last 3-5 user interactions with their tool results.
         */
        private const val MAX_HISTORY_MESSAGES = 30

        /**
         * Extra system-level rules injected into every gateway agent call.
         *
         * The gateway has no interactive user turn — once the agent loop
         * finishes, the entire call returns.  The standard system prompt
         * allows `<status type="wait_for_user_need">` which causes the AI
         * to stop working and ask the user for help.  In the main app the
         * user can reply, but in the gateway this terminates the task.
         *
         * These rules override that behavior: the AI must keep calling
         * tools until the task is done, then use `<status type="complete">`.
         */
        private const val GATEWAY_AGENT_RULES = """[Gateway Agent Rules — HIGHEST PRIORITY]
You are running as an autonomous gateway agent. There is NO interactive user available.

CRITICAL RULES:
1. FORBIDDEN: <status type="wait_for_user_need">. Never ask the user for help or clarification.
2. Complete the task autonomously. If a step fails, retry with a different approach.
3. After each tool call, proceed to the NEXT step immediately.
4. Use get_page_info to observe the current screen state.
5. If waiting for content to appear (e.g., AI response, page loading), use sleep(duration_ms=3000) then get_page_info to check. Repeat until content appears.
6. Only use <status type="complete"> when the entire task is finished.
7. Be concise — focus on DOING, not explaining."""

        /**
         * Synthetic "warmup" assistant reply injected into the first
         * message of a new gateway conversation. Replicates the effect
         * of a user first asking "what tools do you have?" — which
         * testing showed dramatically improves task completion rates
         * because the AI's self-generated capability summary persists
         * in chat history and guides subsequent tool usage decisions.
         */
        private const val WARMUP_CAPABILITY_SUMMARY = """我可以操作手机上的 App。以下是我的工具和能力：

**可用工具**
- `app_launch`：启动/切换应用
- `get_page_info`：读取当前界面 UI 结构
- `get_page_screenshot_image`：截取屏幕截图
- `click_element`：点击界面元素
- `set_input_text`：在输入框中输入文字
- `tap` / `double_tap` / `long_press`：按坐标操作
- `swipe`：滑动屏幕
- `press_key`：按键（返回、主页等）
- `sleep`：等待指定时间
- `skill_recorder`：录制操作步骤生成可复用技能

**操作策略**
- 每步操作后用 `get_page_info` 确认结果
- 页面加载中时用 `sleep(duration_ms=3000)` 等待，然后 `get_page_info` 检查，循环直到内容出现
- 任务完成后用 `<status type="complete">` 结束

我现在可以帮你操作 App。"""

        @Volatile private var INSTANCE: HermesGatewayController? = null

        fun getInstance(context: Context): HermesGatewayController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HermesGatewayController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
