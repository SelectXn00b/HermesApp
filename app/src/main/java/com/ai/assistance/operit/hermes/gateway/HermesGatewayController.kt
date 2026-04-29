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
        GatewayFileLogger.logSessionStart()
        try {
            val config = HermesGatewayConfigBuilder.build(appContext)
            if (config.enabledPlatforms.isEmpty()) {
                _status.value = Status.FAILED
                _error.value = "no enabled platforms with credentials"
                Log.w(TAG, "start(): no enabled platforms — refusing to start")
                GatewayFileLogger.w(TAG, "start(): no enabled platforms — refusing to start")
                return@withLock false
            }
            val instance = GatewayRunner(appContext, config)
            instance.agentRunner = { text, sessionKey, platform, chatId, userId ->
                runHermesAgent(text = text, sessionKey = sessionKey, chatId = chatId)
            }
            runner = instance
            instance.start()
            _status.value = Status.RUNNING
            val msg = "gateway started with ${config.enabledPlatforms.size} platform(s)"
            Log.i(TAG, msg)
            GatewayFileLogger.i(TAG, msg)
            GatewayFileLogger.i(TAG, "log file: ${GatewayFileLogger.getLogFilePath()}")
            true
        } catch (e: Throwable) {
            _status.value = Status.FAILED
            _error.value = e.message
            Log.e(TAG, "start() failed", e)
            GatewayFileLogger.e(TAG, "start() failed: ${e.message}")
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
        GatewayFileLogger.i(TAG, "═══ runHermesAgent START ═══")
        GatewayFileLogger.i(TAG, "  user text (${text.length} chars): ${text.take(1000)}${if (text.length > 1000) "…[truncated]" else ""}")
        GatewayFileLogger.i(TAG, "  chatId: $historyChatId")

        // Use a per-chatId instance so the gateway is isolated from the main
        // app's cancel operations (which call invalidateAllExecutionContexts
        // on the singleton).
        val service = EnhancedAIService.getChatInstance(appContext, historyChatId)
        val history = ChatHistoryManager.getInstance(appContext)

        // Handle /new command: clear chat history for this session so the
        // next request starts with a clean context.
        val trimmedText = text.trim()
        if (trimmedText.equals("/new", ignoreCase = true) ||
            trimmedText.equals("新话题", ignoreCase = true)) {
            try {
                history.clearChatMessages(historyChatId)
                GatewayFileLogger.i(TAG, "  /new command — cleared chat history")
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  /new command — failed to clear history: ${e.message}")
            }
            GatewayFileLogger.i(TAG, "═══ runHermesAgent END ═══\n")
            return "好的，已切换到新话题。"
        }

        // Persist the inbound user message
        try {
            history.ensureChatWithId(historyChatId, title = gatewayChatTitle(sessionKey, chatId))
            history.addMessage(historyChatId, ChatMessage(sender = "user", content = text))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist inbound gateway message: ${e.message}")
            GatewayFileLogger.w(TAG, "failed to persist inbound message: ${e.message}")
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
            // Prune tool result content from AI messages to save tokens.
            // Tool results (especially get_page_info) can be 30k+ chars each;
            // in history they are useless bloat — we keep names & status only.
            val prunedMsgs = pruneToolResultsFromHistory(recentMsgs)
            AIMessageManager.getMemoryFromMessages(messages = prunedMsgs)
        } catch (e: Throwable) {
            Log.w(TAG, "failed to load chat history, proceeding without: ${e.message}")
            GatewayFileLogger.w(TAG, "failed to load chat history: ${e.message}")
            emptyList()
        }
        GatewayFileLogger.i(TAG, "  history turns: ${chatHistory.size}")

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
        val rawContextLength = if (modelConfig != null) {
            if (modelConfig.enableMaxContextMode) modelConfig.maxContextLength
            else modelConfig.contextLength
        } else {
            128.0f // fallback default — gateway needs headroom for tool results
        }
        // Gateway tool calls (especially get_page_info) produce very large
        // responses.  Enforce a minimum context budget of 128k tokens so the
        // agent loop does not abort after a single tool call.
        val contextLength = rawContextLength.coerceAtLeast(GATEWAY_MIN_CONTEXT_LENGTH)
        val maxTokens = (contextLength * 1024).toInt()
        val tokenUsageThreshold = modelConfig?.summaryTokenThreshold?.toDouble() ?: 0.70

        GatewayFileLogger.i(TAG, "  model config: rawContext=${rawContextLength}k effectiveContext=${contextLength}k " +
            "maxTokens=$maxTokens threshold=$tokenUsageThreshold thinking=$enableThinking " +
            "thinkingGuidance=$thinkingGuidance memoryQuery=$enableMemoryQuery " +
            "maxTurns=$maxTurns timeoutMs=$timeoutMs")

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

        GatewayFileLogger.i(TAG, "  roleCard: id=$roleCardId name=$characterName " +
            "configOverride=$chatModelConfigIdOverride indexOverride=$chatModelIndexOverride")

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
        GatewayFileLogger.i(TAG, "  augmented history turns: ${augmentedHistory.size}")
        GatewayFileLogger.i(TAG, "  calling service.sendMessage...")

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
        var chunkCount = 0
        val completed = withTimeoutOrNull(timeoutMs) {
            stream.collect { chunk ->
                raw.append(chunk)
                chunkCount++
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "runHermesAgent: chunk len=${chunk.length} totalLen=${raw.length}")
                }
                // Log tool calls as they stream in
                if (chunk.contains("<tool ") || chunk.contains("<tool_result")) {
                    GatewayFileLogger.d(TAG, "  chunk#$chunkCount contains tool markup (totalLen=${raw.length})")
                }
            }
            true
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        if (completed == null) {
            Log.w(TAG, "runHermesAgent: TIMED OUT after ${elapsedMs}ms " +
                "collectedLen=${raw.length}")
            GatewayFileLogger.w(TAG, "  TIMED OUT after ${elapsedMs}ms chunks=$chunkCount rawLen=${raw.length}")
        } else {
            Log.i(TAG, "runHermesAgent: completed in ${elapsedMs}ms " +
                "rawLen=${raw.length}")
            GatewayFileLogger.i(TAG, "  completed in ${elapsedMs}ms chunks=$chunkCount rawLen=${raw.length}")
        }

        val rawText = raw.toString()

        // Log tool call names found in the raw output for diagnostics
        val toolNames = ChatMarkupRegex.toolCallPattern.findAll(rawText)
            .mapNotNull { it.groupValues.getOrNull(2)?.ifEmpty { null } }
            .toList()
        if (toolNames.isNotEmpty()) {
            GatewayFileLogger.i(TAG, "  tool calls in response: $toolNames")
        }
        // Log tool results found
        val toolResultCount = ChatMarkupRegex.toolResultTag.findAll(rawText).count()
        if (toolResultCount > 0) {
            GatewayFileLogger.i(TAG, "  tool results in response: $toolResultCount")
        }

        // Save the full raw AI response (with tool markup) to history so
        // future calls can reconstruct the complete conversation context.
        val strippedReply = stripInternalMarkup(rawText).trim().ifEmpty {
            if (completed == null) "(agent timed out)" else "(empty response)"
        }

        GatewayFileLogger.i(TAG, "  stripped reply length: ${strippedReply.length}")
        if (strippedReply == "(empty response)") {
            GatewayFileLogger.w(TAG, "  ⚠ EMPTY RESPONSE — raw text was: ${rawText.take(500)}")
        } else if (strippedReply == "(agent timed out)") {
            GatewayFileLogger.w(TAG, "  ⚠ AGENT TIMED OUT — raw text tail: ${rawText.takeLast(500)}")
        } else {
            GatewayFileLogger.i(TAG, "  full reply (${strippedReply.length} chars): ${strippedReply.take(2000)}${if (strippedReply.length > 2000) "…[truncated]" else ""}")
        }
        GatewayFileLogger.i(TAG, "═══ runHermesAgent END ═══\n")

        try {
            history.addMessage(historyChatId, ChatMessage(sender = "ai", content = rawText))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to persist outbound gateway reply: ${e.message}")
        }

        // Trigger memory extraction asynchronously (same as main app's
        // handleTaskCompletion).  Uses the MEMORY-configured AI service for
        // a secondary analysis call, so it does not block the reply.
        if (enableMemoryQuery) {
            try {
                val conversationPairs = chatHistory.map { turn ->
                    val role = when (turn.kind) {
                        PromptTurnKind.USER -> "user"
                        PromptTurnKind.ASSISTANT -> "assistant"
                        else -> "system"
                    }
                    role to turn.content
                } + listOf("user" to text, "assistant" to rawText)
                service.saveConversationToMemoryAsync(
                    conversationHistory = conversationPairs,
                    lastContent = rawText,
                    onSuccess = {
                        GatewayFileLogger.i(TAG, "  memory save completed for $historyChatId")
                    },
                    onError = { e ->
                        GatewayFileLogger.w(TAG, "  memory save failed: ${e.message}")
                    }
                )
                GatewayFileLogger.i(TAG, "  memory save triggered (async)")
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  failed to trigger memory save: ${e.message}")
            }
        }

        // Return stripped text to the platform (no XML markup)
        return strippedReply
    }

    /**
     * Prune large tool-result content from AI messages in [msgs] so that
     * chat history does not blow up the token budget.
     *
     * Strategy:
     * - User messages: kept as-is (usually short).
     * - AI messages: replace the *body* of every `<tool_result…>…</tool_result>`
     *   with a short placeholder, preserving the tag name, attributes (name,
     *   status) so the model still knows what happened.  Also truncate
     *   oversized `<param>` values inside `<tool>` blocks.
     */
    private fun pruneToolResultsFromHistory(msgs: List<ChatMessage>): List<ChatMessage> {
        var totalBefore = 0L
        var totalAfter = 0L
        val result = msgs.map { msg ->
            totalBefore += msg.content.length
            if (msg.sender != "ai") {
                totalAfter += msg.content.length
                return@map msg
            }
            val pruned = pruneAiMessageContent(msg.content)
            totalAfter += pruned.length
            if (pruned.length == msg.content.length) msg
            else msg.copy(content = pruned)
        }
        if (totalBefore != totalAfter) {
            GatewayFileLogger.i(TAG, "  history pruned: ${totalBefore} → ${totalAfter} chars " +
                "(saved ${totalBefore - totalAfter})")
        }
        return result
    }

    /**
     * Strip the body of `<tool_result>` tags and truncate large `<param>`
     * values inside a single AI message's content string.
     */
    private fun pruneAiMessageContent(content: String): String {
        if (content.isEmpty()) return content

        // Phase 1: Replace tool_result body with placeholder.
        // Uses the existing pruneToolResultContentPattern which captures:
        //   group 1 = tag name, group 2 = attributes, group 3 = status, group 4 = body
        var pruned = ChatMarkupRegex.pruneToolResultContentPattern.replace(content) { m ->
            val tagName = m.groupValues[1]
            val attrs = m.groupValues[2]
            val status = m.groupValues[3]
            "<$tagName$attrs>[result omitted]</$tagName>"
        }

        // Phase 2: Truncate <param> values longer than 200 chars.
        pruned = ChatMarkupRegex.toolParamPattern.replace(pruned) { m ->
            val paramName = m.groupValues[1]
            val paramValue = m.groupValues[2]
            if (paramValue.length > 200) {
                "<param name=\"$paramName\">${paramValue.take(200)}…[truncated]</param>"
            } else {
                m.value
            }
        }

        return pruned
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
         * Keep only 6 messages (~1-2 user interactions) to stay well within
         * the token budget — large tool results (e.g. get_page_info) can
         * easily consume 30k+ tokens on their own.
         */
        private const val MAX_HISTORY_MESSAGES = 6

        /**
         * Minimum context length (in "k" units, multiplied by 1024 for tokens)
         * enforced for gateway calls.  Tool results like get_page_info can
         * easily be 30-40k tokens, so we need at least 128k total budget.
         */
        private const val GATEWAY_MIN_CONTEXT_LENGTH = 128.0f

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
2. LOAD SKILLS FIRST: If the system prompt lists Available packages/skills, ALWAYS call use_package to load the relevant skill BEFORE starting the task. Skills contain parameter formats and step-by-step instructions that are critical for correct execution. Never skip this step.
3. APP LAUNCHING: The `start_app` tool requires an Android package name (e.g., com.meituan.hotel), NOT a Chinese app name. If you only know the Chinese name, first call `list_installed_apps` to look up the correct package name, then use `start_app` with the package name. Similarly, package_proxy's `app_launch` tool also requires a package name.
4. PREFER DIRECT METHODS: Always try `execute_shell` or `modify_system_setting` FIRST before resorting to UI navigation (get_page_info + click). Examples: adjust brightness via execute_shell("settings put system screen_brightness 50"), query WiFi status via execute_shell("dumpsys wifi"), etc. Only fall back to UI automation when no direct command/setting exists.
5. BUILT-IN vs PACKAGE TOOLS: Tools like sleep, execute_shell, start_app, list_installed_apps, get_page_info, tap, click_element, set_input_text, swipe, press_key, capture_screenshot, modify_system_setting, get_system_setting are BUILT-IN — call them directly. Only use package_proxy for package-specific tools (e.g., Automatic_ui_base:app_launch).
6. RETRY LIMIT: If the same operation or approach fails 4 times, STOP retrying. Report what you tried and what failed using <status type="complete">, do NOT keep looping.
7. RESPECT USER INSTRUCTIONS: If the user's message contains explicit constraints (e.g., "try at most 2 times", "don't use app X", "stop if it fails"), those constraints OVERRIDE these rules. Always follow user-specified limits.
8. After each tool call, proceed to the NEXT step immediately.
9. Use get_page_info to observe the current screen state when doing UI automation.
10. If waiting for content to appear (e.g., AI response, page loading), use sleep(duration_ms=3000) then get_page_info to check. Repeat until content appears (but respect rule 6).
11. Only use <status type="complete"> when the entire task is finished or when you must stop due to rule 6/7.
12. Be concise — focus on DOING, not explaining.
13. PACKAGE_PROXY FORMAT: When calling package tools via package_proxy, set tool_name to "PackageName:tool_name" and put all arguments in params as a JSON string. Example: <tool name="package_proxy"><param name="tool_name">Automatic_ui_base:app_launch</param><param name="params">{"package_name":"com.example.app"}</param></tool>"""

        /**
         * Synthetic "warmup" assistant reply injected into the first
         * message of a new gateway conversation. Replicates the effect
         * of a user first asking "what tools do you have?" — which
         * testing showed dramatically improves task completion rates
         * because the AI's self-generated capability summary persists
         * in chat history and guides subsequent tool usage decisions.
         */
        private const val WARMUP_CAPABILITY_SUMMARY = """我可以操作手机上的 App。以下是我的工具和能力：

**内置工具（直接调用，不需要 package_proxy）**
- `execute_shell`：执行设备 shell 命令（如 settings put、dumpsys、am start 等）
- `modify_system_setting` / `get_system_setting`：修改/读取系统设置（亮度、音量等）
- `start_app`：启动应用（需要 Android 包名，如 com.meituan.hotel）
- `list_installed_apps`：列出已安装应用（查找中文名对应的包名）
- `get_page_info`：读取当前界面 UI 结构
- `capture_screenshot`：截取屏幕截图
- `click_element`：点击界面元素（通过 text/id 选择器）
- `set_input_text`：在输入框中输入文字
- `tap` / `long_press`：按坐标操作
- `swipe`：滑动屏幕
- `press_key`：按键（返回、主页等）
- `sleep`：等待指定时间
- `use_package`：加载技能包
- `visit_web`：访问网页获取内容
- `http_request`：发送 HTTP 请求

**包工具（通过 package_proxy 调用）**
- 需要先 `use_package` 加载包，再用 `package_proxy` 调用包内工具

**操作策略**
- 系统设置类任务（亮度/音量/WiFi等），优先用 `execute_shell` 或 `modify_system_setting`
- 启动应用前如只知道中文名，先 `list_installed_apps` 查找包名
- UI 操作时每步后用 `get_page_info` 确认结果
- 页面加载中时用 `sleep(duration_ms=3000)` 等待
- 任务完成后用 `<status type="complete">` 结束

我现在可以帮你操作 App。"""

        @Volatile private var INSTANCE: HermesGatewayController? = null

        fun getInstance(context: Context): HermesGatewayController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HermesGatewayController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
