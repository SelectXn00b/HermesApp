package com.xiaomo.androidforclaw.agent.loop

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
import com.xiaomo.androidforclaw.agent.tools.SkillResult
import com.xiaomo.androidforclaw.agent.tools.ToolCallDispatcher
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall
import com.xiaomo.androidforclaw.providers.llm.systemMessage
import com.xiaomo.androidforclaw.providers.llm.userMessage
import com.xiaomo.androidforclaw.providers.llm.assistantMessage
import com.xiaomo.androidforclaw.providers.llm.toolMessage
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val maxIterations: Int = 40,
    private val modelRef: String? = null,
    private val configLoader: ConfigLoader? = null  // For context window resolution (Gap 2)
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_OVERFLOW_RECOVERY_ATTEMPTS = 3  // Aligned with OpenClaw
        private const val LLM_TIMEOUT_MS = 180_000L  // LLM single call timeout: 180 seconds (free models can be slow)
        private const val MAX_CONSECUTIVE_ERRORS = 3  // Consecutive same error threshold: 3 times
        private const val AGENT_LOOP_TOTAL_TIMEOUT_MS = 30 * 60 * 1000L  // 整个 run() 最大运行 30 分钟
        private const val ITERATION_TIMEOUT_MS = 5 * 60 * 1000L  // 单次 iteration（LLM 调用 + 工具执行）最大 5 分钟

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
    private val toolCallDispatcher = ToolCallDispatcher(toolRegistry, androidToolRegistry)

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
    private val logDir = File("/sdcard/.androidforclaw/workspace/logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private var sessionLogFile: File? = null
    private val logBuffer = StringBuilder()

    // ✅ Cache Tool Definitions to avoid rebuilding on each iteration
    // Merge universal tools and Android platform tools
    private val allToolDefinitions by lazy {
        toolRegistry.getToolDefinitions() + androidToolRegistry.getToolDefinitions()
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

    // Error tracker: used to detect consecutive identical errors
    private val errorTracker = mutableListOf<String>()

    /**
     * Record error and check if threshold is reached
     * @return true if execution should stop
     */
    private fun trackError(errorMessage: String): Boolean {
        errorTracker.add(errorMessage)

        // Keep only recent error records
        if (errorTracker.size > MAX_CONSECUTIVE_ERRORS) {
            errorTracker.removeAt(0)
        }

        // Check if all recent errors are identical
        if (errorTracker.size >= MAX_CONSECUTIVE_ERRORS) {
            val allSame = errorTracker.all { it == errorMessage }
            if (allSame) {
                writeLog("🚨 连续 $MAX_CONSECUTIVE_ERRORS 次相同错误，停止执行")
                writeLog("   错误: $errorMessage")
                return true
            }
        }

        return false
    }

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
        reasoningEnabled: Boolean = true
    ): AgentResult {
        // 🛡️ 全局错误兜底: 确保任何未捕获的错误都能返回给用户
        return try {
            runInternal(systemPrompt, userMessage, contextHistory, reasoningEnabled)
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
        reasoningEnabled: Boolean
    ): AgentResult {
        shouldStop = false
        val messages = mutableListOf<Message>()

        // Initialize session log
        initSessionLog(userMessage)

        // Reset context manager
        contextManager?.reset()

        writeLog("========== Agent Loop 开始 ==========")
        writeLog("Max iterations: $maxIterations")
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

        // 3. Add user message
        messages.add(userMessage(userMessage))
        writeLog("✅ User message: $userMessage")
        writeLog("📤 准备发送第一次 LLM 请求...")

        var iteration = 0
        var finalContent: String? = null
        val toolsUsed = mutableListOf<String>()
        val loopStartTime = System.currentTimeMillis()

        // 4. Main loop
        while (iteration < maxIterations && !shouldStop) {
            // 整体超时检查
            val elapsed = System.currentTimeMillis() - loopStartTime
            if (elapsed > AGENT_LOOP_TOTAL_TIMEOUT_MS) {
                writeLog("⏰ AgentLoop 整体超时 (${elapsed}ms > ${AGENT_LOOP_TOTAL_TIMEOUT_MS}ms)")
                Log.w(TAG, "AgentLoop total timeout after ${elapsed}ms")
                finalContent = "⚠️ 处理超时（已运行 ${elapsed / 1000}s），请简化问题或重试"
                break
            }
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
                // Aligned with OpenClaw limitHistoryTurns
                // Default 30 turns, or use config historyLimit
                val maxTurns = try {
                    configLoader?.loadOpenClawConfig()?.channels?.feishu?.historyLimit ?: 30
                } catch (_: Exception) { 30 }

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
                    val errorMsg = "LLM 调用超时 (${LLM_TIMEOUT_MS}ms)"
                    writeLog("❌ $errorMsg")
                    Log.e(TAG, errorMsg)

                    // Record timeout error
                    if (trackError(errorMsg)) {
                        shouldStop = true
                        finalContent = "任务失败: $errorMsg"
                        break
                    }

                    // Add timeout error message to inform LLM
                    messages.add(userMessage("系统提示: LLM 调用超时，请尝试简化任务或分步执行"))
                    continue
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
                    // Aligned with OpenClaw blockReplyBreak="text_end"
                    val intermediateText = response.content?.trim()
                    if (!intermediateText.isNullOrEmpty()) {
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
                            is ToolLoopDetection.LoopDetectionResult.LoopDetected -> {
                                val logLevel = if (loopDetection.level == ToolLoopDetection.LoopDetectionResult.Level.CRITICAL) "🚨" else "⚠️"
                                writeLog("$logLevel Loop detected: ${loopDetection.detector} (count: ${loopDetection.count})")
                                writeLog("   ${loopDetection.message}")

                                // Critical level: abort execution
                                if (loopDetection.level == ToolLoopDetection.LoopDetectionResult.Level.CRITICAL) {
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
                            ToolLoopDetection.LoopDetectionResult.NoLoop -> {
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

                        // Add timeout protection for tool execution (max 30 seconds)
                        val result = try {
                            kotlinx.coroutines.withTimeout(30_000L) {
                                val target = toolCallDispatcher.resolve(functionName)
                                when (target) {
                                    is ToolCallDispatcher.DispatchTarget.Universal -> writeLog("   → Universal tool")
                                    is ToolCallDispatcher.DispatchTarget.Android -> writeLog("   → Android tool")
                                    null -> writeLog("   ❌ Unknown function: $functionName")
                                }
                                toolCallDispatcher.execute(functionName, args)
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            writeLog("   ⏰ Tool execution timeout after 30s")
                            Log.e(TAG, "Tool execution timeout: $functionName after 30s")
                            SkillResult.error("Tool execution timeout after 30 seconds. The tool may be blocked or unresponsive.")
                        }

                        val execDuration = System.currentTimeMillis() - execStartTime
                        totalExecDuration += execDuration

                        writeLog("   Result: ${result.success}, ${result.content.take(200)}")
                        writeLog("   ⏱️ 执行耗时: ${execDuration}ms")

                        // 🔍 Track tool execution errors
                        if (!result.success) {
                            val errorMsg = result.content
                            writeLog("   ⚠️ 工具执行失败: $errorMsg")

                            // Check if error threshold is reached
                            if (trackError("$functionName: $errorMsg")) {
                                shouldStop = true
                                finalContent = "任务失败: 连续 $MAX_CONSECUTIVE_ERRORS 次相同错误 - $errorMsg"

                                // Add error message
                                messages.add(
                                    toolMessage(
                                        toolCallId = toolCall.id,
                                        content = finalContent ?: "",
                                        name = functionName
                                    )
                                )
                                break
                            }
                        } else {
                            // Successful execution, clear error tracker
                            if (errorTracker.isNotEmpty()) {
                                writeLog("   ✅ 工具执行成功，清空错误追踪")
                                errorTracker.clear()
                            }
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

                        // Add result to message list
                        messages.add(
                            toolMessage(
                                toolCallId = toolCall.id,
                                content = result.toString(),
                                name = functionName
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

                    val iterationDuration = System.currentTimeMillis() - iterationStartTime
                    writeLog("⏱️ 本轮迭代总耗时: ${iterationDuration}ms (LLM: ${llmDuration}ms, 执行: ${totalExecDuration}ms)")

                    // 单次 iteration 超时检查
                    if (iterationDuration > ITERATION_TIMEOUT_MS) {
                        writeLog("⏰ Iteration $iteration 超时 (${iterationDuration}ms > ${ITERATION_TIMEOUT_MS}ms)")
                        Log.w(TAG, "Iteration $iteration exceeded timeout: ${iterationDuration}ms")
                    }

                    // Send iteration complete event (with time statistics)
                    _progressFlow.emit(ProgressUpdate.IterationComplete(iteration, iterationDuration, llmDuration, totalExecDuration))
                    continue
                }

                // 4.4 No tool calls, meaning LLM provided final answer
                finalContent = response.content?.let { ReasoningTagFilter.stripReasoningTags(it) }
                    ?: response.content
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
                    // Non-context overflow error
                    _progressFlow.emit(ProgressUpdate.Error(e.message ?: "Unknown error"))

                    // Try to continue or stop
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        // Timeout error, can retry (but track for consecutive error detection)
                        val errorMsg = "Timeout: ${e.message?.take(100)}"
                        writeLog("Timeout error, retrying... ($errorMsg)")
                        if (trackError(errorMsg)) {
                            shouldStop = true
                            finalContent = "任务失败: 连续超时 - $errorMsg"
                            break
                        }
                        continue
                    } else {
                        // Other errors, stop loop and format error message
                        writeLog("❌ Agent loop failed: ${e.message}")
                        Log.e(TAG, "Agent loop failed", e)

                        // Build friendly error message (aligned with OpenClaw error formatting)
                        finalContent = buildString {
                            append("❌ 执行出错\n\n")

                            // Error type
                            when (e) {
                                is com.xiaomo.androidforclaw.providers.LLMException -> {
                                    append("**错误类型**: API 调用失败\n")
                                    append("**错误信息**: ${e.message}\n\n")
                                    append("**建议**: 请检查模型配置和 API Key 是否正确\n")
                                    append("**配置文件**: /sdcard/.androidforclaw/openclaw.json\n")
                                }
                                else -> {
                                    append("**错误信息**: ${e.message}\n")
                                }
                            }

                            // Add stack trace for debugging (first 800 chars)
                            append("\n**调试信息**:\n```\n")
                            append(e.stackTraceToString().take(800))
                            append("\n```")
                        }
                        break
                    }
                }
            }
        }

        // 5. Handle loop end
        if (finalContent == null && iteration >= maxIterations) {
            writeLog("Max iterations ($maxIterations) reached")
            Log.w(TAG, "Max iterations ($maxIterations) reached")
            finalContent = "达到最大迭代次数 ($maxIterations)，任务未完成。" +
                    "建议将任务拆分为更小的步骤。"
        }

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
}
