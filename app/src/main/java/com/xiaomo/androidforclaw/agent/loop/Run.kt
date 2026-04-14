package com.xiaomo.androidforclaw.agent.loop

import com.xiaomo.androidforclaw.agent.subagent.SubagentPromptBuilder
import com.xiaomo.androidforclaw.infra.BackoffPolicy
import com.xiaomo.androidforclaw.infra.computeBackoff
import com.xiaomo.androidforclaw.infra.sleepWithAbort
import com.xiaomo.androidforclaw.infra.GlobalEventBus
import com.xiaomo.androidforclaw.routing.parseAgentSessionKey
import com.xiaomo.androidforclaw.util.ReasoningTagFilter

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/run.ts (core: runEmbeddedPiAgent loop, overflow recovery, auth failover)
 *
 * AndroidForClaw adaptation: iterative agent loop, tool calling, progress updates.
 * Types (AgentResult, ProgressUpdate) → AgentCommand.kt (agent-command.ts)
 * Streaming helpers (sanitizeToolCalls, scrubRefusal) → Subscribe.kt (pi-embedded-subscribe.ts)
 */


import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.agent.context.ContextErrors
import com.xiaomo.androidforclaw.agent.context.ContextManager
import com.xiaomo.androidforclaw.agent.context.ContextRecoveryResult
import com.xiaomo.androidforclaw.agent.context.ContextWindowGuard
import com.xiaomo.androidforclaw.agent.context.ToolResultContextGuard
import com.xiaomo.androidforclaw.agent.session.HistorySanitizer
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.agent.tools.Tool
import com.xiaomo.androidforclaw.agent.tools.ToolCallDispatcher
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.providers.ChunkType
import com.xiaomo.androidforclaw.providers.LLMResponse
import com.xiaomo.androidforclaw.providers.LLMToolCall
import com.xiaomo.androidforclaw.providers.LLMUsage
import com.xiaomo.androidforclaw.providers.StreamChunk
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.llm.ImageBlock
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall
import com.xiaomo.androidforclaw.providers.llm.systemMessage
import com.xiaomo.androidforclaw.providers.llm.userMessage
import com.xiaomo.androidforclaw.providers.llm.assistantMessage
import com.xiaomo.androidforclaw.providers.llm.toolMessage
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agent Loop - Core execution engine
 * Reference: OpenClaw's Agent Loop implementation
 *
 * Execution flow:
 * 1. Receive user message + system prompt
 * 2. Call LLM (with reasoning support)
 * 3. LLM selects tools via function calling
 * 4. Execute tools selected by LLM directly
 * 5. Repeat steps 2-4 until LLM returns final result or reaches max iterations
 *
 * Architecture (reference: OpenClaw pi-tools):
 * - ToolRegistry: Universal tools (read, write, exec, web_fetch)
 * - AndroidToolRegistry: Android platform tools (tap, screenshot, open_app)
 * - SkillsLoader: Skills documents (mobile-operations.md)
 */
class AgentLoop(
    private val llmProvider: UnifiedLLMProvider,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    private val contextManager: ContextManager? = null,  // Optional context manager
    @Deprecated("No longer used — aligned with OpenClaw (no iteration limit)")
    private val maxIterations: Int = Int.MAX_VALUE,  // Kept for call-site compat, ignored
    private val modelRef: String? = null,
    private val configLoader: ConfigLoader? = null  // For context window resolution (Gap 2)
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_OVERFLOW_RECOVERY_ATTEMPTS = 3  // Aligned with OpenClaw MAX_OVERFLOW_COMPACTION_ATTEMPTS

        /**
         * LLM single call timeout.
         * OpenClaw: agents.defaults.timeoutSeconds (configurable, no hard default in loop).
         * Android: 180s default for free/slow models, generous enough for long generations.
         */
        private const val LLM_TIMEOUT_MS = 180_000L

        /**
         * Transient HTTP retry delay (aligned with OpenClaw TRANSIENT_HTTP_RETRY_DELAY_MS).
         */
        private const val TRANSIENT_HTTP_RETRY_DELAY_MS = 2_500L

        /**
         * Timeout compaction: when LLM times out and context usage is high (>65%),
         * try compacting before retrying. Aligned with OpenClaw MAX_TIMEOUT_COMPACTION_ATTEMPTS.
         */
        private const val MAX_TIMEOUT_COMPACTION_ATTEMPTS = 2
        private const val TIMEOUT_COMPACTION_TOKEN_RATIO = 0.65f

        /**
         * Iteration warn threshold.
         * OpenClaw has no per-iteration timeout; this is Android-only observability.
         */
        private const val ITERATION_WARN_THRESHOLD_MS = 5 * 60 * 1000L

        /**
         * Max loop iterations — safety cap aligned with OpenClaw MAX_RUN_LOOP_ITERATIONS.
         * OpenClaw: resolveMaxRunRetryIterations(profileCandidates.length)
         *   = BASE_RUN_RETRY_ITERATIONS(24) + RUN_RETRY_ITERATIONS_PER_PROFILE(8) * numProfiles
         *   clamped to [MIN_RUN_RETRY_ITERATIONS(32), MAX_RUN_RETRY_ITERATIONS(160)]
         * Android: single profile → use MAX (160) for safety headroom since no auth failover.
         */
        private const val MAX_RUN_LOOP_ITERATIONS = 160

        /**
         * Overload backoff: aligned with OpenClaw OVERLOAD_FAILOVER_BACKOFF_POLICY.
         * Used for HTTP 529 (overloaded) and 503+overload message.
         * Delegates to infra.BackoffPolicy / computeBackoff().
         */
        private val OVERLOAD_BACKOFF_POLICY = BackoffPolicy(
            initialMs = 250L,
            maxMs = 1_500L,
            factor = 2.0,
            jitter = 0.2
        )

        // Context pruning constants (aligned with OpenClaw DEFAULT_CONTEXT_PRUNING_SETTINGS)
        private const val SOFT_TRIM_RATIO = 0.3f
        private const val HARD_CLEAR_RATIO = 0.5f
        private const val MIN_PRUNABLE_TOOL_CHARS = 50_000
        private const val KEEP_LAST_ASSISTANTS = 3
        private const val SOFT_TRIM_MAX_CHARS = 4_000
        private const val SOFT_TRIM_HEAD_CHARS = 1_500
        private const val SOFT_TRIM_TAIL_CHARS = 1_500
        private const val HARD_CLEAR_PLACEHOLDER = "[Old tool result content cleared]"

    }

    private val gson = Gson()

    /**
     * Session key for this agent loop instance.
     * Used for per-channel history limit resolution (aligned with OpenClaw getHistoryLimitFromSessionKey).
     * Set by caller (MainEntryNew, SubagentSpawner, Gateway) after construction.
     */
    var sessionKey: String? = null

    /**
     * Extra per-session tools (e.g. subagent tools: sessions_spawn, sessions_list, etc.)
     * Set after construction to resolve circular dependency (tools need AgentLoop ref).
     * Aligned with OpenClaw per-session tool injection.
     */
    var extraTools: List<Tool> = emptyList()
        set(value) {
            field = value
            _extraToolsMap = value.associateBy { it.name }
            // Invalidate cached tool definitions
            _allToolDefinitionsCache = null
        }
    private var _extraToolsMap: Map<String, Tool> = emptyMap()

    private val toolCallDispatcher: ToolCallDispatcher
        get() = ToolCallDispatcher(toolRegistry, androidToolRegistry, _extraToolsMap)

    /**
     * Resolve context window tokens from config (Gap 2).
     * Uses ContextWindowGuard for proper resolution with warn/block thresholds.
     */
    private fun resolveContextWindowTokens(): Int {
        if (configLoader == null) return ContextWindowGuard.DEFAULT_CONTEXT_WINDOW_TOKENS

        // Parse provider/model from modelRef (format: "provider/model" or just "model")
        val parts = modelRef?.split("/", limit = 2)
        val providerName = if (parts != null && parts.size == 2) parts[0] else null
        val modelId = if (parts != null && parts.size == 2) parts[1] else modelRef

        val guard = ContextWindowGuard.resolveAndEvaluate(configLoader, providerName, modelId)
        if (guard.shouldWarn) {
            Log.w(TAG, "Context window below recommended: ${guard.tokens} tokens")
        }
        return guard.tokens
    }

    // Log file configuration
    private val logDir = StoragePaths.workspaceLogs
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private var sessionLogFile: File? = null
    private val logBuffer = StringBuilder()

    // Cache Tool Definitions — invalidated when extraTools changes
    @Volatile private var _allToolDefinitionsCache: List<com.xiaomo.androidforclaw.providers.ToolDefinition>? = null
    private val allToolDefinitions: List<com.xiaomo.androidforclaw.providers.ToolDefinition>
        get() {
            _allToolDefinitionsCache?.let { return it }
            val defs = toolRegistry.getToolDefinitions() + androidToolRegistry.getToolDefinitions() +
                extraTools.map { it.getToolDefinition() }
            _allToolDefinitionsCache = defs
            return defs
        }

    // Progress update flow
    private val _progressFlow = MutableSharedFlow<ProgressUpdate>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val progressFlow: SharedFlow<ProgressUpdate> = _progressFlow.asSharedFlow()

    /**
     * Steer message channel: external code (e.g. MessageQueueManager) can send
     * mid-run user messages into this channel.  The main loop drains it after
     * each tool-execution round and injects the messages into the conversation
     * before the next LLM call.
     *
     * Uses a Channel (not SharedFlow) so that tryReceive() is available for
     * non-suspending drain inside the loop.
     */
    val steerChannel = Channel<String>(capacity = 16)

    // Stop flag
    @Volatile
    private var shouldStop = false

    // Loop detector state
    private val loopDetectionState = ToolLoopDetection.SessionState()

    // Timeout compaction counter (aligned with OpenClaw timeoutCompactionAttempts)
    private var timeoutCompactionAttempts = 0

    // Transient HTTP retry guard (aligned with OpenClaw didRetryTransientHttpError)
    private var didRetryTransientHttpError = false

    // Hook runner (aligned with OpenClaw EmbeddedAgentHookRunner)
    val hookRunner = com.xiaomo.androidforclaw.agent.hook.HookRunner()

    // Memory flush manager (aligned with OpenClaw runMemoryFlushIfNeeded)
    private val memoryFlushManager = com.xiaomo.androidforclaw.agent.memory.MemoryFlushManager()

    /**
     * Live conversation messages, accessible for sessions_history.
     * Set to the mutable list used inside runInternal(). After run() completes,
     * contains the full conversation. Thread-safe for read-only access.
     */
    @Volatile
    var conversationMessages: List<Message> = emptyList()
        private set

    /**
     * Yield signal for sessions_yield tool.
     * When set, the loop pauses after the current tool execution round
     * and waits until the deferred is completed (by announce or timeout).
     * Aligned with OpenClaw sessions_yield behavior.
     */
    @Volatile
    var yieldSignal: CompletableDeferred<String?>? = null

    /**
     * Write log to file and buffer
     */
    private fun writeLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] $message"

        // Add to buffer
        logBuffer.appendLine(logLine)

        // Write to file (if file is created)
        sessionLogFile?.let { file ->
            try {
                file.appendText(logLine + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to file", e)
            }
        }

        // Also output to logcat
        Log.d(TAG, message)
    }

    /**
     * Initialize session log file
     */
    private fun initSessionLog(userMessage: String) {
        try {
            // Ensure log directory exists
            logDir.mkdirs()

            // Create log file (using timestamp + user message prefix as filename)
            val timestamp = dateFormat.format(Date())
            val messagePrefix = userMessage.take(20).replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
            val filename = "agentloop_${timestamp}_${messagePrefix}.log"
            sessionLogFile = File(logDir, filename)

            // Clear buffer
            logBuffer.clear()

            // Write session header
            sessionLogFile?.writeText("========== Agent Loop Session ==========\n", Charsets.UTF_8)
            sessionLogFile?.appendText("Start time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            sessionLogFile?.appendText("User message: $userMessage\n")
            sessionLogFile?.appendText("========================================\n\n")

            Log.i(TAG, "📝 Session log initialized: ${sessionLogFile?.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize session log (will continue without file logging): ${e.message}")
            sessionLogFile = null  // Disable file logging, but continue execution
        }
    }

    /**
     * Finalize session log
     */
    private fun finalizeSessionLog(result: AgentResult) {
        sessionLogFile?.let { file ->
            try {
                file.appendText("\n========================================\n")
                file.appendText("End time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                file.appendText("Total iterations: ${result.iterations}\n")
                file.appendText("Tools used: ${result.toolsUsed.joinToString(", ")}\n")
                file.appendText("Final content length: ${result.finalContent.length} chars\n")
                file.appendText("========================================\n")

                Log.i(TAG, "✅ Session log saved: ${file.absolutePath} (${file.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize session log", e)
            }
        }
    }

    /**
     * Run Agent Loop
     *
     * @param systemPrompt System prompt
     * @param userMessage User message
     * @param contextHistory Historical conversation records
     * @param reasoningEnabled Whether to enable reasoning
     * @return AgentResult containing final content, tools used, and all messages
     */
    suspend fun run(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message> = emptyList(),
        reasoningEnabled: Boolean = true,
        images: List<ImageBlock>? = null
    ): AgentResult {
        // 🛡️ 全局错误兜底: 确保任何未捕获的错误都能返回给用户
        return try {
            runInternal(systemPrompt, userMessage, contextHistory, reasoningEnabled, images)
        } catch (e: Exception) {
            Log.e(TAG, "❌ AgentLoop 未捕获的错误", e)
            LayoutExceptionLogger.log("AgentLoop#run", e)

            // 返回友好的错误信息给用户
            val errorMessage = buildString {
                append("❌ Agent 执行失败\n\n")
                append("**错误信息**: ${e.message ?: "未知错误"}\n\n")
                append("**错误类型**: ${e.javaClass.simpleName}\n\n")
                append("**建议**: \n")
                append("- 请检查网络连接\n")
                append("- 如果问题持续,请使用 /new 重新开始对话\n")
                append("- 查看日志获取更多详细信息")
            }

            AgentResult(
                finalContent = errorMessage,
                toolsUsed = emptyList(),
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userMessage),
                    assistantMessage(errorMessage)
                ),
                iterations = 0
            )
        }
    }

    /**
     * AgentLoop 主执行逻辑 (内部)
     */
    private suspend fun runInternal(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message>,
        reasoningEnabled: Boolean,
        images: List<ImageBlock>? = null
    ): AgentResult {
        shouldStop = false

        // Emit agent run started event via GlobalEventBus
        val agentEventBus = GlobalEventBus.resolve<Map<String, Any?>>("agent.events")
        agentEventBus.emit(mapOf("type" to "agent.run.started", "sessionKey" to sessionKey))

        // Planning-Only Retry / ACK Fast Path state (对齐 OpenClaw run.ts L332-335)
        var planningOnlyRetryAttempts = 0
        var planningOnlyRetryInstruction: String? = null

        // Parse provider/model from modelRef for ack fast path check
        val refParts = modelRef?.split("/", limit = 2)
        val resolvedProviderName = if (refParts != null && refParts.size == 2) refParts[0] else null
        val resolvedModelId = if (refParts != null && refParts.size == 2) refParts[1] else modelRef

        val ackExecutionFastPathInstruction = resolveAckExecutionFastPathInstruction(
            AckExecutionParams(provider = resolvedProviderName, modelId = resolvedModelId, prompt = userMessage)
        )

        val messages = mutableListOf<Message>()
        conversationMessages = messages  // Expose for sessions_history

        // Initialize session log
        initSessionLog(userMessage)

        // Reset context manager
        contextManager?.reset()

        writeLog("========== Agent Loop 开始 ==========")
        writeLog("Model: ${modelRef ?: "default"}")
        writeLog("Reasoning: ${if (reasoningEnabled) "enabled" else "disabled"}")
        // Parse session key for structured logging (routing.SessionKey)
        val parsedSessionKey = parseAgentSessionKey(sessionKey)
        if (parsedSessionKey != null) {
            writeLog("Session: agentId=${parsedSessionKey.agentId}, rest=${parsedSessionKey.rest}")
        }
        writeLog("🔧 Universal tools: ${toolRegistry.getToolCount()}")
        writeLog("📱 Android tools: ${androidToolRegistry.getToolCount()}")
        writeLog("🔄 Context manager: ${if (contextManager != null) "enabled" else "disabled"}")

        // 1. Add system prompt
        messages.add(systemMessage(systemPrompt))
        writeLog("✅ System prompt added (${systemPrompt.length} chars)")

        // 2. Add conversation history (sanitized — aligned with OpenClaw)
        if (contextHistory.isNotEmpty()) {
            val sanitized = HistorySanitizer.sanitize(contextHistory, maxTurns = 20)
            messages.addAll(sanitized)
            if (sanitized.size != contextHistory.size) {
                writeLog("✅ Context history sanitized: ${contextHistory.size} → ${sanitized.size} messages")
            } else {
                writeLog("✅ Context history added: ${sanitized.size} messages")
            }
        }

        // 3. Add user message (with images if present — aligned with OpenClaw native image injection)
        // Append ack/fast path + planning-only instructions if present (对齐 OpenClaw run.ts L508-514)
        val promptAdditions = listOfNotNull(ackExecutionFastPathInstruction, planningOnlyRetryInstruction)
        val effectiveUserMessage = if (promptAdditions.isNotEmpty()) {
            "$userMessage\n\n${promptAdditions.joinToString("\n\n")}"
        } else {
            userMessage
        }
        if (promptAdditions.isNotEmpty()) {
            writeLog("📝 Appended ${promptAdditions.size} prompt addition(s): ${promptAdditions.map { it.take(40) }}")
        }

        if (!images.isNullOrEmpty()) {
            messages.add(userMessage(effectiveUserMessage, images))
            writeLog("✅ User message: $effectiveUserMessage [+${images.size} image(s)]")
        } else {
            messages.add(userMessage(effectiveUserMessage))
            writeLog("✅ User message: $effectiveUserMessage")
        }

        // 3b. Detect image references in user message text (aligned with OpenClaw detectAndLoadPromptImages)
        // Pass workspaceDir so relative paths like "inbox/photo.png" resolve correctly (OpenClaw image-tool fix)
        val workspaceDir = StoragePaths.workspace.absolutePath
        val imageRefs = com.xiaomo.androidforclaw.agent.tools.ImageLoader.detectImageReferences(userMessage, workspaceDir)
        if (imageRefs.isNotEmpty()) {
            writeLog("🖼️ Detected ${imageRefs.size} image reference(s) in user message")
            val loadedImages = imageRefs.mapNotNull { ref ->
                com.xiaomo.androidforclaw.agent.tools.ImageLoader.loadImageFromPath(ref)
            }
            if (loadedImages.isNotEmpty()) {
                // Replace the last user message with one that includes the loaded images
                val lastIdx = messages.size - 1
                if (messages[lastIdx].role == "user") {
                    messages[lastIdx] = userMessage(userMessage, loadedImages)
                    writeLog("🖼️ Loaded ${loadedImages.size} image(s) into user message")
                }
            }
        }

        writeLog("📤 准备发送第一次 LLM 请求...")

        var iteration = 0
        var finalContent: String? = null
        val toolsUsed = mutableListOf<String>()
        val loopStartTime = System.currentTimeMillis()
        // Usage accumulator (aligned with OpenClaw usageAccumulator — accumulate across retries)
        var cumulativePromptTokens = 0L
        var cumulativeCompletionTokens = 0L
        var cumulativeTotalTokens = 0L

        // 4. Main loop — no iteration limit, no overall timeout (aligned with OpenClaw)
        // OpenClaw's inner loop is while(true), terminates when LLM returns
        // final answer (no tool_calls) or abort/error.
        while (!shouldStop) {
            iteration++
            // Safety cap — aligned with OpenClaw MAX_RUN_LOOP_ITERATIONS
            if (iteration > MAX_RUN_LOOP_ITERATIONS) {
                writeLog("🛑 Max loop iterations reached ($MAX_RUN_LOOP_ITERATIONS), stopping")
                finalContent = "❌ Request failed after repeated internal retries (max iterations: $MAX_RUN_LOOP_ITERATIONS)."
                break
            }
            val iterationStartTime = System.currentTimeMillis()
            writeLog("========== Iteration $iteration ==========")

            try {
                // 4.1 Call LLM
                writeLog("📢 发送迭代进度更新...")
                _progressFlow.emit(ProgressUpdate.Iteration(iteration))
                writeLog("✅ 迭代进度已发送")

                // ===== Context Management (aligned with OpenClaw) =====
                val contextWindowTokens = resolveContextWindowTokens()

                // Step 1: Limit history turns — drop old user/assistant turn pairs
                // Aligned with OpenClaw limitHistoryTurns + getHistoryLimitFromSessionKey
                val maxTurns = com.xiaomo.androidforclaw.agent.session.HistoryTurnLimiter
                    .getHistoryLimitFromSessionKey(sessionKey, configLoader)

                val systemMsg = messages.firstOrNull { it.role == "system" }
                val nonSystemMessages = messages.filter { it.role != "system" }.toMutableList()
                val limitedNonSystem = HistorySanitizer.limitHistoryTurns(nonSystemMessages, maxTurns)
                if (limitedNonSystem.size < nonSystemMessages.size) {
                    val dropped = nonSystemMessages.size - limitedNonSystem.size
                    messages.clear()
                    if (systemMsg != null) messages.add(systemMsg)
                    messages.addAll(limitedNonSystem)
                    writeLog("🔄 History limited: dropped $dropped old messages (kept $maxTurns turns)")
                }

                // Step 2: Context pruning — soft trim old large tool results
                // Aligned with OpenClaw context-pruning cache-ttl mode
                pruneOldToolResults(messages, contextWindowTokens)

                // Step 3: Enforce tool result context budget (truncate + compact)
                // Aligned with OpenClaw tool-result-context-guard.ts
                ToolResultContextGuard.enforceContextBudget(messages, contextWindowTokens)

                // Step 4: Final budget check — if still over, aggressively trim
                val totalChars = ToolResultContextGuard.estimateContextChars(messages)
                val budgetChars = (contextWindowTokens * 4 * 0.75).toInt()
                if (totalChars > budgetChars) {
                    writeLog("⚠️ Context still over budget ($totalChars / $budgetChars chars), aggressive trim...")
                    aggressiveTrimMessages(messages, budgetChars)
                }

                writeLog("📤 调用 UnifiedLLMProvider.chatWithToolsStreaming...")
                writeLog("   Messages: ${messages.size}, Tools+Skills: ${allToolDefinitions.size}")

                // 🔔 Send intermediate feedback: thinking step X
                _progressFlow.emit(ProgressUpdate.Thinking(iteration))

                val llmStartTime = System.currentTimeMillis()

                // ⏱️ SSE 流式调用 + timeout 保护
                // 对齐 OpenClaw streamSimple → thinking_delta/text_delta 实时推送
                val response: LLMResponse
                try {
                    val thinkingAccumulated = StringBuilder()
                    val contentAccumulated = StringBuilder()
                    data class ToolCallAccum(
                        var id: String = "",
                        var name: String = "",
                        val args: StringBuilder = StringBuilder()
                    )
                    val toolCallsAccumulated = mutableMapOf<Int, ToolCallAccum>()
                    var finalUsage: LLMUsage? = null
                    var finalFinishReason: String? = null

                    // Scrub Anthropic refusal magic string from system prompt (aligned with OpenClaw scrubAnthropicRefusalMagic)
                    val scrubbedMessages = scrubAnthropicRefusalMagic(messages)

                    kotlinx.coroutines.withTimeout(LLM_TIMEOUT_MS) {
                        llmProvider.chatWithToolsStreaming(
                            messages = scrubbedMessages,
                            tools = allToolDefinitions,
                            modelRef = modelRef,
                            reasoningEnabled = reasoningEnabled
                        ).collect { chunk ->
                            when (chunk.type) {
                                ChunkType.THINKING_DELTA -> {
                                    thinkingAccumulated.append(chunk.text)
                                    _progressFlow.emit(ProgressUpdate.ReasoningDelta(chunk.text))
                                }
                                ChunkType.TEXT_DELTA -> {
                                    contentAccumulated.append(chunk.text)
                                    _progressFlow.emit(ProgressUpdate.ContentDelta(chunk.text))
                                }
                                ChunkType.TOOL_CALL_DELTA -> {
                                    val idx = chunk.toolCallIndex ?: 0
                                    val accum = toolCallsAccumulated.getOrPut(idx) { ToolCallAccum() }
                                    if (!chunk.toolCallId.isNullOrEmpty()) accum.id = chunk.toolCallId
                                    if (!chunk.toolCallName.isNullOrEmpty()) accum.name = chunk.toolCallName
                                    if (!chunk.toolCallArgs.isNullOrEmpty()) accum.args.append(chunk.toolCallArgs)
                                }
                                ChunkType.DONE -> {
                                    finalFinishReason = chunk.finishReason ?: finalFinishReason
                                    chunk.usage?.let { u ->
                                        finalUsage = LLMUsage(u.promptTokens, u.completionTokens, u.totalTokens)
                                    }
                                }
                                ChunkType.USAGE -> {
                                    chunk.usage?.let { u ->
                                        finalUsage = LLMUsage(u.promptTokens, u.completionTokens, u.totalTokens)
                                    }
                                }
                                ChunkType.PING -> { /* ignore */ }
                            }
                        }
                    }

                    // 组装完整 LLMResponse（与后续工具调用逻辑衔接）
                    val rawToolCalls = if (toolCallsAccumulated.isEmpty()) null else {
                        toolCallsAccumulated.entries.sortedBy { it.key }.map { (_, tc) ->
                            LLMToolCall(
                                id = tc.id.ifEmpty { "call_${System.currentTimeMillis()}" },
                                name = tc.name,
                                arguments = tc.args.toString()
                            )
                        }
                    }
                    response = LLMResponse(
                        content = contentAccumulated.toString().ifEmpty { null },
                        toolCalls = rawToolCalls?.let { sanitizeToolCalls(it) },
                        thinkingContent = thinkingAccumulated.toString().ifEmpty { null },
                        usage = finalUsage,
                        finishReason = finalFinishReason
                    )

                    // Accumulate usage across retries (aligned with OpenClaw usageAccumulator)
                    finalUsage?.let { u ->
                        cumulativePromptTokens += u.promptTokens
                        cumulativeCompletionTokens += u.completionTokens
                        cumulativeTotalTokens += u.totalTokens
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    val errorMsg = "LLM 调用超时 (${LLM_TIMEOUT_MS / 1000}s)"
                    writeLog("⏰ $errorMsg")
                    Log.w(TAG, errorMsg)

                    // ── Timeout compaction (aligned with OpenClaw) ──
                    val totalCharsNow = ToolResultContextGuard.estimateContextChars(messages)
                    val budgetCharsNow = (contextWindowTokens * 4 * 0.75).toInt()
                    val tokenUsedRatio = if (budgetCharsNow > 0) totalCharsNow.toFloat() / budgetCharsNow else 0f

                    if (timeoutCompactionAttempts < MAX_TIMEOUT_COMPACTION_ATTEMPTS &&
                        tokenUsedRatio > TIMEOUT_COMPACTION_TOKEN_RATIO
                    ) {
                        timeoutCompactionAttempts++
                        writeLog("🔄 Timeout compaction attempt $timeoutCompactionAttempts/$MAX_TIMEOUT_COMPACTION_ATTEMPTS " +
                            "(context usage: ${(tokenUsedRatio * 100).toInt()}%)")

                        // Run before_compaction hook (aligned with OpenClaw hookRunner.runBeforeCompaction)
                        val hookCtx = com.xiaomo.androidforclaw.agent.hook.HookContext(
                            sessionKey = null,
                            agentId = null,
                            provider = "",
                            model = modelRef ?: ""
                        )
                        val hookResult = hookRunner.runBeforeCompaction(
                            data = mapOf("reason" to "timeout", "tokenUsedRatio" to tokenUsedRatio),
                            context = hookCtx
                        )

                        if (hookResult.shouldCancel) {
                            writeLog("🪝 before_compaction hook cancelled compaction")
                            // Skip compaction, continue with current messages
                        } else {
                            // Memory flush: run before compaction if context is getting full
                            // Aligned with OpenClaw runMemoryFlushIfNeeded()
                            try {
                                if (memoryFlushManager.shouldRunFlush(
                                    tokenCount = (totalCharsNow / 4),
                                    contextWindowTokens = contextWindowTokens
                                )) {
                                    writeLog("🧠 Running memory flush before compaction...")
                                    val flushResult = memoryFlushManager.runFlush(
                                        llmProvider = llmProvider,
                                        modelRef = modelRef ?: "",
                                        messages = messages
                                    )
                                    if (flushResult.success && flushResult.memoriesExtracted) {
                                        writeLog("✅ Memory flush: extracted ${flushResult.memoriesContent?.length ?: 0} chars")
                                        _progressFlow.emit(ProgressUpdate.BlockReply(text = "🧠 记忆提取完成", iteration = iteration))
                                    }
                                }
                            } catch (e: Exception) {
                                writeLog("⚠️ Memory flush failed (non-fatal): ${e.message}")
                            }
                        }

                        if (contextManager != null) {
                            val recoveryResult = contextManager.handleContextOverflow(
                                error = e,
                                messages = messages
                            )
                            if (recoveryResult is ContextRecoveryResult.Recovered) {
                                writeLog("✅ Timeout compaction succeeded: ${recoveryResult.strategy}")
                                messages.clear()
                                messages.addAll(recoveryResult.messages)
                                continue
                            }
                        }

                        pruneOldToolResults(messages, contextWindowTokens)
                        aggressiveTrimMessages(messages, budgetCharsNow)
                        writeLog("✅ Timeout compaction fallback: pruned context")
                        continue
                    }

                    writeLog("❌ LLM timeout after $timeoutCompactionAttempts compaction attempts, surfacing error")
                    finalContent = "⏰ LLM 调用超时。请简化问题或使用 /new 开始新对话。"
                    break
                }

                val llmDuration = System.currentTimeMillis() - llmStartTime

                writeLog("✅ LLM 流式响应完成 [耗时: ${llmDuration}ms]")
                // Debug: log raw LLM response for diagnosing empty/unexpected responses
                writeLog("   Raw content length: ${response.content?.length ?: 0}")
                writeLog("   Raw content: [${response.content?.take(500) ?: "null"}]")
                writeLog("   Tool calls: ${response.toolCalls?.size ?: 0}")
                writeLog("   Finish reason: ${response.finishReason ?: "null"}")
                if (response.content != null && response.content.length > 500) {
                    writeLog("   Raw content tail: ...${response.content.takeLast(200)}")
                }

                if (llmDuration > 30_000) {
                    writeLog("⚠️ LLM 响应耗时较长: ${llmDuration}ms")
                }

                // 4.2 Display reasoning thinking process (完整内容，流式增量已在 collect 中发出)
                response.thinkingContent?.let { reasoning ->
                    writeLog("🧠 Reasoning (${reasoning.length} chars):")
                    writeLog("   ${reasoning.take(500)}${if (reasoning.length > 500) "..." else ""}")
                    _progressFlow.emit(ProgressUpdate.Reasoning(reasoning, llmDuration))
                }

                // 4.3 Check if there are function calls
                if (response.toolCalls != null && response.toolCalls.isNotEmpty()) {
                    writeLog("Function calls: ${response.toolCalls.size}")

                    // ✅ Block Reply: emit intermediate text immediately
                    // Aligned with OpenClaw blockReplyBreak="text_end" + normalizeStreamingText
                    val intermediateText = response.content?.trim()
                    if (!intermediateText.isNullOrEmpty() && !SubagentPromptBuilder.isSilentReplyText(intermediateText)) {
                        writeLog("📤 Block reply (intermediate text): ${intermediateText.take(200)}...")
                        _progressFlow.emit(ProgressUpdate.BlockReply(intermediateText, iteration))
                    }

                    // Add assistant message (containing function calls)
                    messages.add(
                        assistantMessage(
                            content = response.content,
                            toolCalls = response.toolCalls.map {
                                com.xiaomo.androidforclaw.providers.llm.ToolCall(
                                    id = it.id,
                                    name = it.name,
                                    arguments = it.arguments
                                )
                            }
                        )
                    )

                    // Execute each tool/skill (directly execute the capabilities selected by LLM)
                    var totalExecDuration = 0L
                    for (toolCall in response.toolCalls) {
                        val functionName = toolCall.name
                        val argsJson = toolCall.arguments

                        writeLog("🔧 Function: $functionName")
                        writeLog("   Args: $argsJson")

                        // Parse arguments
                        val args = try {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(argsJson, Map::class.java) as Map<String, Any?>
                        } catch (e: Exception) {
                            writeLog("Failed to parse arguments: ${e.message}")
                            Log.e(TAG, "Failed to parse arguments", e)
                            mapOf<String, Any?>()
                        }

                        // ✅ Detect tool call loop (before execution)
                        val loopDetection = ToolLoopDetection.detectToolCallLoop(
                            state = loopDetectionState,
                            toolName = functionName,
                            params = args
                        )

                        when (loopDetection) {
                            is LoopDetectionResult.LoopDetected -> {
                                val logLevel = if (loopDetection.level == LoopDetectionResult.Level.CRITICAL) "🚨" else "⚠️"
                                writeLog("$logLevel Loop detected: ${loopDetection.detector} (count: ${loopDetection.count})")
                                writeLog("   ${loopDetection.message}")

                                // Critical level: abort execution
                                if (loopDetection.level == LoopDetectionResult.Level.CRITICAL) {
                                    writeLog("🛑 Critical loop detected, stopping execution")
                                    Log.e(TAG, "🛑 Critical loop detected: ${loopDetection.message}")

                                    // Add error message to conversation
                                    messages.add(
                                        toolMessage(
                                            toolCallId = toolCall.id,
                                            content = loopDetection.message,
                                            name = functionName
                                        )
                                    )

                                    _progressFlow.emit(ProgressUpdate.LoopDetected(
                                        detector = loopDetection.detector.name,
                                        count = loopDetection.count,
                                        message = loopDetection.message,
                                        critical = true
                                    ))

                                    // Abort entire loop
                                    shouldStop = true
                                    finalContent = "Task failed: ${loopDetection.message}"
                                    break
                                }

                                // Warning level: inject warning but continue execution
                                writeLog("⚠️ Loop warning injected into conversation")
                                messages.add(
                                    toolMessage(
                                        toolCallId = toolCall.id,
                                        content = loopDetection.message,
                                        name = functionName
                                    )
                                )

                                _progressFlow.emit(ProgressUpdate.LoopDetected(
                                    detector = loopDetection.detector.name,
                                    count = loopDetection.count,
                                    message = loopDetection.message,
                                    critical = false
                                ))

                                // Skip this tool call after warning
                                continue
                            }
                            LoopDetectionResult.NoLoop -> {
                                // No loop, continue execution
                            }
                        }

                        // Record tool call (before execution)
                        ToolLoopDetection.recordToolCall(
                            state = loopDetectionState,
                            toolName = functionName,
                            params = args,
                            toolCallId = toolCall.id
                        )

                        toolsUsed.add(functionName)

                        // Send call progress update
                        _progressFlow.emit(ProgressUpdate.ToolCall(functionName, args))

                        // Run before_tool_call hook (aligned with OpenClaw hookRunner.runBeforeToolCall)
                        val toolHookCtx = com.xiaomo.androidforclaw.agent.hook.HookContext(
                            sessionKey = null,
                            agentId = null,
                            provider = "",
                            model = modelRef ?: ""
                        )
                        val beforeToolHook = hookRunner.runBeforeToolCall(
                            toolName = functionName,
                            arguments = toolCall.arguments,
                            context = toolHookCtx
                        )

                        if (beforeToolHook.shouldCancel) {
                            writeLog("🪝 before_tool_call hook cancelled $functionName")
                            messages.add(
                                toolMessage(toolCallId = toolCall.id, content = "Tool execution cancelled by hook", name = functionName)
                            )
                            continue
                        }

                        // ✅ Search universal tools first, then Android tools
                        val execStartTime = System.currentTimeMillis()

                        // Execute tool (no per-tool timeout — aligned with OpenClaw)
                        // Individual tools manage their own timeouts internally.
                        val result = run {
                            val target = toolCallDispatcher.resolve(functionName)
                            when (target) {
                                is ToolCallDispatcher.DispatchTarget.Universal -> writeLog("   → Universal tool")
                                is ToolCallDispatcher.DispatchTarget.Android -> writeLog("   → Android tool")
                                is ToolCallDispatcher.DispatchTarget.Extra -> writeLog("   → Extra tool (subagent)")
                                null -> writeLog("   ❌ Unknown function: $functionName")
                            }
                            toolCallDispatcher.execute(functionName, args)
                        }

                        val execDuration = System.currentTimeMillis() - execStartTime
                        totalExecDuration += execDuration

                        writeLog("   Result: ${result.success}, ${result.content.take(200)}")
                        writeLog("   ⏱️ 执行耗时: ${execDuration}ms")

                        // Log tool execution errors (aligned with OpenClaw: no consecutive error abort)
                        // OpenClaw lets the LLM see tool errors and decide how to proceed.
                        // ToolLoopDetection handles runaway loops separately.
                        if (!result.success) {
                            writeLog("   ⚠️ 工具执行失败: ${result.content.take(200)}")
                        }

                        // Record tool call result (for loop detection)
                        ToolLoopDetection.recordToolCallOutcome(
                            state = loopDetectionState,
                            toolName = functionName,
                            toolParams = args,
                            result = result.toString(),
                            error = if (result.success) null else Exception(result.content),
                            toolCallId = toolCall.id
                        )

                        // Run after_tool_call hook (aligned with OpenClaw hookRunner.runAfterToolCall)
                        hookRunner.runAfterToolCall(
                            toolName = functionName,
                            arguments = toolCall.arguments,
                            result = result.toString(),
                            success = result.success,
                            context = toolHookCtx
                        )

                        // Add result to message list (with images if tool returned any)
                        messages.add(
                            toolMessage(
                                toolCallId = toolCall.id,
                                content = result.toString(),
                                name = functionName,
                                images = result.images
                            )
                        )

                        // Send result update
                        _progressFlow.emit(ProgressUpdate.ToolResult(functionName, result.toString(), execDuration))

                        // Check if it's stop skill
                        if (functionName == "stop") {
                            val metadata = result.metadata
                            val stopped = metadata["stopped"] as? Boolean ?: false
                            if (stopped) {
                                shouldStop = true
                                finalContent = result.content
                                writeLog("Stop function called, ending loop")
                                break
                            }
                        }
                    }

                    // Continue loop, let LLM decide next step after seeing function results
                    if (shouldStop) break

                    // Drain steer messages injected by MessageQueueManager (STEER mode)
                    while (true) {
                        val steerMsg = steerChannel.tryReceive().getOrNull() ?: break
                        Log.i(TAG, "Injecting steer message: ${steerMsg.take(50)}...")
                        writeLog("🎯 [STEER] Injecting mid-run user message: ${steerMsg.take(100)}")
                        messages.add(userMessage(steerMsg))
                        _progressFlow.emit(ProgressUpdate.SteerMessageInjected(steerMsg))
                    }

                    // Check for yield signal (sessions_yield tool)
                    // If set, pause the loop until subagent announcements arrive or timeout.
                    // Aligned with OpenClaw sessions_yield behavior.
                    yieldSignal?.let { deferred ->
                        writeLog("⏸️ Yield signal detected, pausing loop...")
                        _progressFlow.emit(ProgressUpdate.Yielded)
                        // Wait up to 300s to prevent deadlock
                        val yieldMessage = withTimeoutOrNull(300_000L) { deferred.await() }
                        yieldSignal = null
                        if (!yieldMessage.isNullOrBlank()) {
                            messages.add(userMessage(yieldMessage))
                            writeLog("▶️ Resumed from yield with message: ${yieldMessage.take(100)}")
                        } else {
                            writeLog("▶️ Resumed from yield (timeout or no message)")
                        }
                    }

                    val iterationDuration = System.currentTimeMillis() - iterationStartTime
                    writeLog("⏱️ 本轮迭代总耗时: ${iterationDuration}ms (LLM: ${llmDuration}ms, 执行: ${totalExecDuration}ms)")

                    // 单次 iteration 耗时告警（仅 warn，不中断）
                    if (iterationDuration > ITERATION_WARN_THRESHOLD_MS) {
                        writeLog("⚠️ Iteration $iteration 耗时较长 (${iterationDuration}ms > ${ITERATION_WARN_THRESHOLD_MS}ms)")
                        Log.w(TAG, "Iteration $iteration slow: ${iterationDuration}ms")
                    }

                    // Send iteration complete event (with time statistics)
                    _progressFlow.emit(ProgressUpdate.IterationComplete(iteration, iterationDuration, llmDuration, totalExecDuration))
                    continue
                }

                // 4.4 No tool calls, meaning LLM provided final answer

                // 4.4a. Stop Reason Recovery (对齐 OpenClaw attempt.stop-reason-recovery.ts)
                // Detect unhandled stop_reason and convert to friendly error
                val stopReasonError = detectUnhandledStopReason(response.finishReason)
                if (stopReasonError != null) {
                    writeLog("⚠️ Unhandled stop reason detected: ${response.finishReason}")
                    messages.add(assistantMessage(content = stopReasonError))
                    finalContent = stopReasonError
                    break
                }

                // Filter SILENT_REPLY_TOKEN (aligned with OpenClaw normalizeStreamingText)
                var rawContent = response.content?.let { ReasoningTagFilter.stripReasoningTags(it) }
                    ?: response.content

                // Fallback: some models (e.g. o3 on Copilot API) return content in reasoning_content
                // instead of content when reasoning is disabled. Use reasoning as fallback.
                if (rawContent.isNullOrBlank() && !response.thinkingContent.isNullOrBlank()) {
                    writeLog("⚠️ content is empty, falling back to reasoning_content (${response.thinkingContent!!.length} chars)")
                    Log.w(TAG, "⚠️ content empty, using reasoning_content as fallback")
                    rawContent = response.thinkingContent
                }

                // Warn if LLM returned suspicious default text
                if (rawContent == "无响应" || rawContent == "无响应。" || rawContent == "没有响应") {
                    writeLog("⚠️ LLM returned suspicious default text: '$rawContent'")
                    writeLog("   This usually indicates: context too large, model confusion, or corrupted history")
                    writeLog("   Messages count: ${messages.size}, Total context chars: ${ToolResultContextGuard.estimateContextChars(messages)}")
                    Log.w(TAG, "⚠️ LLM returned suspicious default text: '$rawContent' (context may be too large)")
                }

                // 4.4b. Incomplete Turn Detection (对齐 OpenClaw incomplete-turn.ts L1378-1448)
                // Build replay metadata for this attempt
                val attemptReplayMeta = buildAttemptReplayMetadata(
                    toolNames = toolsUsed.toList(),
                    didSendViaMessagingTool = false
                )

                // Check incomplete turn: stop_reason is toolUse/error but no payload
                val incompleteTurnText = resolveIncompleteTurnPayloadText(IncompleteTurnParams(
                    payloadCount = 0,
                    aborted = shouldStop,
                    timedOut = false,
                    hasClientToolCall = false,
                    yieldDetected = false,
                    didSendDeterministicApprovalPrompt = false,
                    lastToolError = null,
                    stopReason = response.finishReason,
                    hadPotentialSideEffects = attemptReplayMeta.hadPotentialSideEffects
                ))

                // 4.4c. Planning-Only Retry (对齐 OpenClaw incomplete-turn.ts L1392-1422)
                if (incompleteTurnText == null && planningOnlyRetryAttempts < 1) {
                    val planningOnlyInstruction = resolvePlanningOnlyRetryInstruction(PlanningOnlyParams(
                        provider = resolvedProviderName,
                        modelId = resolvedModelId,
                        aborted = shouldStop,
                        timedOut = false,
                        hasClientToolCall = false,
                        yieldDetected = false,
                        didSendDeterministicApprovalPrompt = false,
                        didSendViaMessagingTool = false,
                        lastToolError = null,
                        startedToolCount = toolsUsed.size,
                        hadPotentialSideEffects = attemptReplayMeta.hadPotentialSideEffects,
                        stopReason = response.finishReason,
                        assistantText = rawContent
                    ))
                    if (planningOnlyInstruction != null) {
                        planningOnlyRetryAttempts += 1
                        planningOnlyRetryInstruction = planningOnlyInstruction
                        writeLog("🔄 Planning-only turn detected, injecting retry instruction (attempt $planningOnlyRetryAttempts)")
                        Log.i(TAG, "Planning-only retry: attempt=$planningOnlyRetryAttempts")
                        // Add the assistant message and inject retry instruction as user message
                        messages.add(assistantMessage(content = rawContent))
                        messages.add(userMessage(planningOnlyInstruction))
                        _progressFlow.emit(ProgressUpdate.BlockReply(
                            text = "🔄 Planning-only detected, retrying with action...",
                            iteration = iteration
                        ))
                        continue
                    }
                }

                // 4.4d. Incomplete turn recovery
                if (incompleteTurnText != null) {
                    writeLog("⚠️ Incomplete turn detected, injecting warning")
                    Log.w(TAG, "Incomplete turn: finish_reason=${response.finishReason}")
                    messages.add(assistantMessage(content = rawContent))
                    messages.add(userMessage(incompleteTurnText))
                    _progressFlow.emit(ProgressUpdate.BlockReply(
                        text = incompleteTurnText,
                        iteration = iteration
                    ))
                    continue
                }

                // 4.4e. Normal final answer
                finalContent = if (SubagentPromptBuilder.isSilentReplyText(rawContent)) null else rawContent
                messages.add(assistantMessage(content = finalContent))

                writeLog("Final content received (finish_reason: ${response.finishReason})")
                writeLog("Content: ${finalContent?.take(500)}${if ((finalContent?.length ?: 0) > 500) "..." else ""}")
                break

            } catch (e: Exception) {
                writeLog("Iteration $iteration error: ${e.message}")
                Log.e(TAG, "Iteration $iteration error", e)
                LayoutExceptionLogger.log("AgentLoop#run#iteration$iteration", e)

                // Check if it's a context overflow error
                val errorMessage = ContextErrors.extractErrorMessage(e)
                val isContextOverflow = ContextErrors.isLikelyContextOverflowError(errorMessage)

                if (isContextOverflow && contextManager != null) {
                    writeLog("🔄 检测到上下文超限，尝试恢复...")
                    Log.w(TAG, "🔄 检测到上下文超限，尝试恢复...")
                    _progressFlow.emit(ProgressUpdate.ContextOverflow("Context overflow detected, attempting recovery..."))

                    // Attempt recovery
                    val recoveryResult = contextManager.handleContextOverflow(
                        error = e,
                        messages = messages
                    )

                    when (recoveryResult) {
                        is ContextRecoveryResult.Recovered -> {
                            writeLog("✅ 上下文恢复成功: ${recoveryResult.strategy} (attempt ${recoveryResult.attempt})")
                            Log.d(TAG, "✅ 上下文恢复成功: ${recoveryResult.strategy} (attempt ${recoveryResult.attempt})")
                            _progressFlow.emit(ProgressUpdate.ContextRecovered(
                                strategy = recoveryResult.strategy,
                                attempt = recoveryResult.attempt
                            ))

                            // Replace message list
                            messages.clear()
                            messages.addAll(recoveryResult.messages)

                            // Retry current iteration
                            continue
                        }
                        is ContextRecoveryResult.CannotRecover -> {
                            // Step 2: Aggressive tool result truncation (aligned with OpenClaw truncateOversizedToolResultsInSession)
                            // OpenClaw: when contextEngine.compact fails, try truncating oversized tool results before giving up
                            writeLog("⚠️ Compaction failed, trying aggressive tool result truncation...")
                            val ctxTokens = resolveContextWindowTokens()
                            val budgetCharsNow = (ctxTokens * 4 * 0.75).toInt()
                            pruneOldToolResults(messages, ctxTokens)
                            ToolResultContextGuard.enforceContextBudget(messages, ctxTokens)
                            aggressiveTrimMessages(messages, budgetCharsNow)

                            val totalAfterTruncation = ToolResultContextGuard.estimateContextChars(messages)
                            if (totalAfterTruncation <= budgetCharsNow) {
                                writeLog("✅ Tool result truncation succeeded, retrying iteration")
                                _progressFlow.emit(ProgressUpdate.ContextRecovered(
                                    strategy = "tool_result_truncation",
                                    attempt = 0
                                ))
                                continue
                            }

                            writeLog("❌ 上下文恢复失败（含 truncation fallback）: ${recoveryResult.reason}")
                            Log.e(TAG, "❌ 上下文恢复失败: ${recoveryResult.reason}")
                            _progressFlow.emit(ProgressUpdate.Error("Context overflow: ${recoveryResult.reason}"))

                            finalContent = buildString {
                                append("❌ 上下文溢出\n\n")
                                append("**错误**: ${recoveryResult.reason}\n\n")
                                append("**建议**: 对话历史过长，请使用 /new 或 /reset 开始新对话")
                            }
                            break
                        }
                    }
                } else {
                    // Non-context overflow error — classify and decide recovery
                    // Aligned with OpenClaw runAgentTurnWithFallback error classification
                    val isBilling = ContextErrors.isBillingErrorMessage(errorMessage)
                    val isRoleOrdering = ContextErrors.isRoleOrderingError(errorMessage)
                    val isSessionCorruption = ContextErrors.isSessionCorruptionError(errorMessage)
                    val isTransientHttp = ContextErrors.isTransientHttpError(errorMessage)

                    // Role ordering conflict → reset conversation
                    // Aligned with OpenClaw resetSessionAfterRoleOrderingConflict
                    if (isRoleOrdering) {
                        writeLog("⚠️ Role ordering conflict detected, resetting conversation")
                        Log.w(TAG, "Role ordering conflict: $errorMessage")
                        finalContent = "⚠️ Message ordering conflict. Conversation has been reset - please try again."
                        break
                    }

                    // Gemini session corruption → reset conversation
                    // Aligned with OpenClaw isSessionCorruption handling
                    if (isSessionCorruption) {
                        writeLog("⚠️ Session history corrupted (function call ordering), resetting")
                        Log.w(TAG, "Session corruption: $errorMessage")
                        finalContent = "⚠️ Session history was corrupted. Conversation has been reset - please try again!"
                        break
                    }

                    // Transient HTTP error → single retry with delay
                    // Aligned with OpenClaw: TRANSIENT_HTTP_RETRY_DELAY_MS = 2500, retry once
                    if (isTransientHttp && !didRetryTransientHttpError) {
                        // Overloaded (529/503+overload) → exponential backoff (aligned with OpenClaw OVERLOAD_FAILOVER_BACKOFF_POLICY)
                        val isOverloaded = ContextErrors.isOverloadedError(errorMessage)
                        if (isOverloaded) {
                            writeLog("⚠️ Overloaded error, exponential backoff...")
                            val maxRetries = 4  // 250 → 500 → 1000 → 1500ms
                            for (backoffAttempt in 1..maxRetries) {
                                val actualDelay = computeBackoff(OVERLOAD_BACKOFF_POLICY, backoffAttempt)
                                writeLog("   Backoff $backoffAttempt/$maxRetries: ${actualDelay}ms")
                                sleepWithAbort(actualDelay)
                            }
                        } else {
                            writeLog("⚠️ Transient HTTP error, retrying in ${TRANSIENT_HTTP_RETRY_DELAY_MS}ms... ($errorMessage)")
                            sleepWithAbort(TRANSIENT_HTTP_RETRY_DELAY_MS)
                        }
                        didRetryTransientHttpError = true
                        Log.w(TAG, "Transient HTTP error, retrying: $errorMessage")
                        continue
                    }

                    _progressFlow.emit(ProgressUpdate.Error(e.message ?: "Unknown error"))

                    // Timeout error: no bare retry (aligned with OpenClaw — timeout is failover, not retry)
                    // OpenClaw handles timeout via runWithModelFallback → classifyFailoverReason("timeout") → failover.
                    // Android has no model fallback, so surface the error instead of infinite retry.
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        writeLog("⏰ Timeout error, surfacing to user (no infinite retry)")
                        finalContent = "⏰ LLM 调用超时。请简化问题或使用 /new 开始新对话。"
                        break
                    }

                    // Other errors, stop loop and format error message
                    writeLog("❌ Agent loop failed: ${e.message}")
                    Log.e(TAG, "Agent loop failed", e)

                    // Build friendly error message (aligned with OpenClaw error formatting)
                    finalContent = buildString {
                        if (isBilling) {
                            append("⚠️ Billing error — please check your account balance or API key quota.")
                        } else {
                            append("❌ 执行出错\n\n")

                            when (e) {
                                is com.xiaomo.androidforclaw.providers.LLMException -> {
                                    append("**错误类型**: API 调用失败\n")
                                    append("**错误信息**: ${e.message}\n\n")
                                    append("**建议**: 请检查模型配置和 API Key 是否正确\n")
                                    append("**配置文件**: ${StoragePaths.openclawConfig.absolutePath}\n")
                                }
                                else -> {
                                    append("**错误信息**: ${e.message}\n")
                                }
                            }

                            append("\n**调试信息**:\n```\n")
                            append(e.stackTraceToString().take(800))
                            append("\n```")
                        }
                    }
                    break
                }
            }
        }

        // 5. Handle loop end
        // No maxIterations limit (aligned with OpenClaw). Loop only exits when:
        // - LLM returns final answer (no tool_calls)
        // - shouldStop flag set (abort, critical loop, stop tool)
        // - Unrecoverable error

        writeLog("========== Agent Loop 结束 ==========")
        writeLog("Iterations: $iteration")
        writeLog("Tools used: ${toolsUsed.joinToString(", ")}")

        // Add final content as assistant message if not empty
        val effectiveFinalContent = when {
            finalContent != null -> finalContent
            shouldStop -> "✅ 任务已停止"
            else -> "无响应"
        }
        if (effectiveFinalContent.isNotEmpty()) {
            messages.add(com.xiaomo.androidforclaw.providers.llm.Message(
                role = "assistant",
                content = effectiveFinalContent
            ))
        }

        // Log cumulative usage (aligned with OpenClaw usageAccumulator)
        if (cumulativeTotalTokens > 0) {
            writeLog("📊 Cumulative usage: $cumulativePromptTokens prompt + $cumulativeCompletionTokens completion = $cumulativeTotalTokens total tokens")
        }

        val result = AgentResult(
            finalContent = effectiveFinalContent,
            toolsUsed = toolsUsed,
            messages = messages,
            iterations = iteration
        )

        // Finalize session log
        finalizeSessionLog(result)

        // Emit agent run completed event via GlobalEventBus
        agentEventBus.emit(mapOf(
            "type" to "agent.run.completed",
            "sessionKey" to sessionKey,
            "iterations" to iteration
        ))

        return result
    }

    // ===== Context Pruning (aligned with OpenClaw context-pruning cache-ttl) =====

    /**
     * Soft-trim and hard-clear old large tool results.
     * Aligned with OpenClaw DEFAULT_CONTEXT_PRUNING_SETTINGS:
     * - softTrimRatio: 0.3 (start trimming when 30% of context is used)
     * - hardClearRatio: 0.5 (hard clear when 50% is used)
     * - minPrunableToolChars: 50000
     * - keepLastAssistants: 3
     * - softTrim.maxChars: 4000, headChars: 1500, tailChars: 1500
     * - hardClear.placeholder: "[Old tool result content cleared]"
     */
    private fun pruneOldToolResults(
        messages: MutableList<Message>,
        contextWindowTokens: Int
    ) {
        val budgetChars = (contextWindowTokens * 4 * 0.75).toInt()
        val currentChars = ToolResultContextGuard.estimateContextChars(messages)
        val usageRatio = currentChars.toFloat() / budgetChars.toFloat()

        if (usageRatio < SOFT_TRIM_RATIO) return  // Under 30%, no action needed

        // Find the last 3 assistant messages (keep their tool results untouched)
        val keepAfterIndex = findKeepBoundaryIndex(messages, KEEP_LAST_ASSISTANTS)

        var trimmed = 0
        var cleared = 0

        for (i in messages.indices) {
            if (i >= keepAfterIndex) break  // Don't touch recent messages
            val msg = messages[i]
            if (msg.role != "tool") continue

            val content = msg.content ?: continue
            if (content.length < MIN_PRUNABLE_TOOL_CHARS) continue

            if (usageRatio >= HARD_CLEAR_RATIO) {
                // Hard clear
                messages[i] = msg.copy(content = HARD_CLEAR_PLACEHOLDER)
                cleared++
            } else {
                // Soft trim: keep head + tail
                if (content.length > SOFT_TRIM_MAX_CHARS) {
                    val head = content.take(SOFT_TRIM_HEAD_CHARS)
                    val tail = content.takeLast(SOFT_TRIM_TAIL_CHARS)
                    val trimmedContent = "$head\n\n[...${content.length - SOFT_TRIM_HEAD_CHARS - SOFT_TRIM_TAIL_CHARS} chars trimmed...]\n\n$tail"
                    messages[i] = msg.copy(content = trimmedContent)
                    trimmed++
                }
            }
        }

        if (trimmed > 0 || cleared > 0) {
            writeLog("🔄 Context pruning: soft-trimmed $trimmed, hard-cleared $cleared tool results")
        }
    }

    /**
     * Find the message index before which we can prune.
     * Keep the last N assistant messages and their tool results untouched.
     */
    private fun findKeepBoundaryIndex(messages: List<Message>, keepCount: Int): Int {
        var assistantCount = 0
        for (i in messages.indices.reversed()) {
            if (messages[i].role == "assistant") {
                assistantCount++
                if (assistantCount >= keepCount) return i
            }
        }
        return 0  // Keep everything if fewer than keepCount assistants
    }

    /**
     * Aggressive trim when still over budget after pruning + guard.
     * Drops oldest non-system, non-last-user messages until under budget.
     */
    /**
     * Aggressive trim: aligned with OpenClaw pruneHistoryForContextShare.
     * Drop oldest 50% of non-system messages repeatedly until under budget.
     * maxHistoryShare = 0.5 (history can use at most 50% of context window)
     */
    private fun aggressiveTrimMessages(messages: MutableList<Message>, budgetChars: Int) {
        val maxHistoryBudget = (budgetChars * 0.5).toInt() // OpenClaw: maxHistoryShare = 0.5

        // Aligned with OpenClaw pruneHistoryForContextShare:
        // Drop oldest messages (any role) until under budget.
        // Keep: first system message + last 2 messages (user + assistant).
        // History may contain system-role messages from prior session saves — drop those too.
        val totalChars = ToolResultContextGuard.estimateContextChars(messages)
        val roleCounts = messages.groupBy { it.role }.mapValues { it.value.size }
        writeLog("📊 Pruning: total=${messages.size} chars=$totalChars budget=$maxHistoryBudget roles=$roleCounts")

        if (totalChars <= maxHistoryBudget) return

        // Keep first message (system prompt) and last 2 (current user + last response)
        val keep = 3 // first + last 2
        if (messages.size <= keep) return

        var iterations = 0
        while (ToolResultContextGuard.estimateContextChars(messages) > maxHistoryBudget && messages.size > keep && iterations < 15) {
            // Drop oldest half between index 1 and size-2
            val droppableCount = messages.size - keep
            val dropCount = (droppableCount / 2).coerceAtLeast(1)

            writeLog("🗑️ Pruning: dropping $dropCount of $droppableCount droppable messages (iteration ${iterations + 1})")

            repeat(dropCount) {
                if (messages.size > keep) {
                    messages.removeAt(1) // Remove second message (oldest non-first)
                }
            }
            iterations++
        }

        writeLog("✅ Pruned: ${messages.size} messages, ${ToolResultContextGuard.estimateContextChars(messages)} chars after $iterations iterations")
    }

    // scrubAnthropicRefusalMagic moved to Subscribe.kt (aligned with pi-embedded-subscribe.ts)

    // sanitizeToolCalls and repairMalformedJsonFallback moved to Subscribe.kt (aligned with pi-embedded-subscribe.ts)

    /** Delegate to package-level sanitizeToolCalls (Subscribe.kt) */
    private fun sanitizeToolCalls(toolCalls: List<LLMToolCall>): List<LLMToolCall> {
        val allowedToolNames = allToolDefinitions.map { it.function.name }.toSet()
        return com.xiaomo.androidforclaw.agent.loop.sanitizeToolCalls(toolCalls, allowedToolNames, ::writeLog)
    }

    /**
     * Stop Agent Loop
     */
    fun stop() {
        shouldStop = true
        Log.d(TAG, "Stop signal received")
    }

    /**
     * Reset internal loop state for steer-restart.
     * Called after the coroutine Job is cancelled and before re-launching run().
     * Clears: shouldStop, loopDetectionState, timeoutCompactionAttempts, steerChannel.
     * Aligned with OpenClaw steer abort+restart flow.
     */
    fun reset() {
        shouldStop = false
        timeoutCompactionAttempts = 0
        didRetryTransientHttpError = false
        loopDetectionState.toolCallHistory.clear()
        // Drain steer channel to remove stale messages
        while (steerChannel.tryReceive().isSuccess) { /* drain */ }
        // Clear yield signal
        yieldSignal = null
        Log.d(TAG, "AgentLoop reset for steer-restart")
    }
}

// AgentResult and ProgressUpdate moved to AgentCommand.kt (aligned with agent-command.ts)
