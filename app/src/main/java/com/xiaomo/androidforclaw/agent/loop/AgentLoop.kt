package com.xiaomo.androidforclaw.agent.loop

import com.xiaomo.androidforclaw.agent.subagent.SubagentPromptBuilder
import com.xiaomo.androidforclaw.util.ReasoningTagFilter

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/run.ts (core: runEmbeddedPiAgent loop, overflow recovery, auth failover)
 * - ../openclaw/src/agents/agent-command.ts (session entry, model resolve, fallback orchestration)
 * - ../openclaw/src/agents/pi-embedded-subscribe.ts (streaming tool execution callbacks — not yet implemented here)
 *
 * AndroidForClaw adaptation: iterative agent loop, tool calling, progress updates.
 * Note: OpenClaw splits the loop across run.ts (retry/overflow) and subscribe.ts (streaming/tool dispatch);
 * AndroidForClaw merges both into this single class with non-streaming batch calls.
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
         * Iteration warn threshold (no hard limit).
         * OpenClaw has no per-iteration timeout; this is Android-only observability.
         */
        private const val ITERATION_WARN_THRESHOLD_MS = 5 * 60 * 1000L

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
        val messages = mutableListOf<Message>()
        conversationMessages = messages  // Expose for sessions_history

        // Initialize session log
        initSessionLog(userMessage)

        // Reset context manager
        contextManager?.reset()

        writeLog("========== Agent Loop 开始 ==========")
        writeLog("Model: ${modelRef ?: "default"}")
        writeLog("Reasoning: ${if (reasoningEnabled) "enabled" else "disabled"}")
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
        if (!images.isNullOrEmpty()) {
            messages.add(userMessage(userMessage, images))
            writeLog("✅ User message: $userMessage [+${images.size} image(s)]")
        } else {
            messages.add(userMessage(userMessage))
            writeLog("✅ User message: $userMessage")
        }

        // 3b. Detect image references in user message text (aligned with OpenClaw detectAndLoadPromptImages)
        val imageRefs = com.xiaomo.androidforclaw.agent.tools.ImageLoader.detectImageReferences(userMessage)
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

        // 4. Main loop — no iteration limit, no overall timeout (aligned with OpenClaw)
        // OpenClaw's inner loop is while(true), terminates when LLM returns
        // final answer (no tool_calls) or abort/error.
        while (!shouldStop) {
            iteration++
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

                writeLog("📤 调用 UnifiedLLMProvider.chatWithTools...")
                writeLog("   Messages: ${messages.size}, Tools+Skills: ${allToolDefinitions.size}")

                // 🔔 Send intermediate feedback: thinking step X
                _progressFlow.emit(ProgressUpdate.Thinking(iteration))

                val llmStartTime = System.currentTimeMillis()

                // ⏱️ Add timeout protection
                val response = try {
                    kotlinx.coroutines.withTimeout(LLM_TIMEOUT_MS) {
                        llmProvider.chatWithTools(
                            messages = messages,
                            tools = allToolDefinitions,
                            modelRef = modelRef,
                            reasoningEnabled = reasoningEnabled
                        )
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    val errorMsg = "LLM 调用超时 (${LLM_TIMEOUT_MS / 1000}s)"
                    writeLog("⏰ $errorMsg")
                    Log.w(TAG, errorMsg)

                    // ── Timeout compaction (aligned with OpenClaw) ──
                    // When LLM times out with high context usage, try compacting
                    // before retrying to break the timeout death spiral.
                    val totalCharsNow = ToolResultContextGuard.estimateContextChars(messages)
                    val budgetCharsNow = (contextWindowTokens * 4 * 0.75).toInt()
                    val tokenUsedRatio = if (budgetCharsNow > 0) totalCharsNow.toFloat() / budgetCharsNow else 0f

                    if (timeoutCompactionAttempts < MAX_TIMEOUT_COMPACTION_ATTEMPTS &&
                        tokenUsedRatio > TIMEOUT_COMPACTION_TOKEN_RATIO
                    ) {
                        timeoutCompactionAttempts++
                        writeLog("🔄 Timeout compaction attempt $timeoutCompactionAttempts/$MAX_TIMEOUT_COMPACTION_ATTEMPTS " +
                            "(context usage: ${(tokenUsedRatio * 100).toInt()}%)")

                        // Try context recovery via compaction
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

                        // Fallback: aggressive prune old tool results
                        pruneOldToolResults(messages, contextWindowTokens)
                        aggressiveTrimMessages(messages, budgetCharsNow)
                        writeLog("✅ Timeout compaction fallback: pruned context")
                        continue
                    }

                    // No compaction possible — surface timeout to user
                    // (aligned with OpenClaw: surface error when compaction exhausted)
                    writeLog("❌ LLM timeout after $timeoutCompactionAttempts compaction attempts, surfacing error")
                    finalContent = "⏰ LLM 调用超时。请简化问题或使用 /new 开始新对话。"
                    break
                }

                val llmDuration = System.currentTimeMillis() - llmStartTime

                writeLog("✅ LLM 响应已收到 [耗时: ${llmDuration}ms]")

                // ⚠️ Log warning if response time is too long
                if (llmDuration > 30_000) {
                    writeLog("⚠️ LLM 响应耗时较长: ${llmDuration}ms")
                }

                // 4.2 Display reasoning thinking process
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
                // Filter SILENT_REPLY_TOKEN (aligned with OpenClaw normalizeStreamingText)
                val rawContent = response.content?.let { ReasoningTagFilter.stripReasoningTags(it) }
                    ?: response.content
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
                            writeLog("❌ 上下文恢复失败: ${recoveryResult.reason}")
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
                        didRetryTransientHttpError = true
                        writeLog("⚠️ Transient HTTP error, retrying in ${TRANSIENT_HTTP_RETRY_DELAY_MS}ms... ($errorMessage)")
                        Log.w(TAG, "Transient HTTP error, retrying: $errorMessage")
                        kotlinx.coroutines.delay(TRANSIENT_HTTP_RETRY_DELAY_MS)
                        continue
                    }

                    _progressFlow.emit(ProgressUpdate.Error(e.message ?: "Unknown error"))

                    // Timeout error: retry (no delay — already timed out)
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        writeLog("⏰ Timeout error, retrying... (${e.message?.take(100)})")
                        continue
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

        val result = AgentResult(
            finalContent = effectiveFinalContent,
            toolsUsed = toolsUsed,
            messages = messages,
            iterations = iteration
        )

        // Finalize session log
        finalizeSessionLog(result)

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

/**
 * Agent execution result
 */
data class AgentResult(
    val finalContent: String,
    val toolsUsed: List<String>,
    val messages: List<Message>,
    val iterations: Int
)

/**
 * Progress update
 */
sealed class ProgressUpdate {
    /** Start new iteration */
    data class Iteration(val number: Int) : ProgressUpdate()

    /** Thinking step X (intermediate feedback) */
    data class Thinking(val iteration: Int) : ProgressUpdate()

    /** Reasoning thinking process */
    data class Reasoning(val content: String, val llmDuration: Long) : ProgressUpdate()

    /** Tool call */
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : ProgressUpdate()

    /** Tool result */
    data class ToolResult(val name: String, val result: String, val execDuration: Long) : ProgressUpdate()

    /** Iteration complete */
    data class IterationComplete(val number: Int, val iterationDuration: Long, val llmDuration: Long, val execDuration: Long) : ProgressUpdate()

    /** Context overflow */
    data class ContextOverflow(val message: String) : ProgressUpdate()

    /** Context recovered successfully */
    data class ContextRecovered(val strategy: String, val attempt: Int) : ProgressUpdate()

    /** Error */
    data class Error(val message: String) : ProgressUpdate()

    /** Loop detected */
    data class LoopDetected(
        val detector: String,
        val count: Int,
        val message: String,
        val critical: Boolean
    ) : ProgressUpdate()

    /**
     * Intermediate text reply (block reply).
     *
     * Aligned with OpenClaw's blockReplyBreak="text_end" mechanism:
     * When LLM returns text + tool_calls in the same response,
     * the text is emitted immediately as an intermediate reply
     * (not held until the final answer).
     */
    data class BlockReply(val text: String, val iteration: Int) : ProgressUpdate()

    /** A steer message was injected into the conversation mid-run */
    data class SteerMessageInjected(val content: String) : ProgressUpdate()

    /** A subagent was spawned (for observability) */
    data class SubagentSpawned(val runId: String, val label: String, val childSessionKey: String) : ProgressUpdate()

    /** A subagent completed and its result was announced to the parent */
    data class SubagentAnnounced(val runId: String, val label: String, val status: String) : ProgressUpdate()

    /** The agent loop yielded (sessions_yield) to wait for subagent results */
    data object Yielded : ProgressUpdate()
}
