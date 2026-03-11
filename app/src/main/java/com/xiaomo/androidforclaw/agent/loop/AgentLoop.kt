package com.xiaomo.androidforclaw.agent.loop

import android.util.Log
import com.xiaomo.androidforclaw.agent.context.ContextErrors
import com.xiaomo.androidforclaw.agent.context.ContextManager
import com.xiaomo.androidforclaw.agent.context.ContextRecoveryResult
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.SkillResult
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
    private val modelRef: String? = null
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_OVERFLOW_RECOVERY_ATTEMPTS = 3  // Aligned with OpenClaw
        private const val LLM_TIMEOUT_MS = 60_000L  // LLM single call timeout: 60 seconds
        private const val MAX_CONSECUTIVE_ERRORS = 3  // Consecutive same error threshold: 3 times
    }

    private val gson = Gson()

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
            sessionLogFile?.writeText("========== Agent Loop Session ==========\n")
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

        // 2. Add conversation history
        messages.addAll(contextHistory)
        if (contextHistory.isNotEmpty()) {
            writeLog("✅ Context history added: ${contextHistory.size} messages")
        }

        // 3. Add user message
        messages.add(userMessage(userMessage))
        writeLog("✅ User message: $userMessage")
        writeLog("📤 准备发送第一次 LLM 请求...")

        var iteration = 0
        var finalContent: String? = null
        val toolsUsed = mutableListOf<String>()

        // 4. Main loop
        while (iteration < maxIterations && !shouldStop) {
            iteration++
            val iterationStartTime = System.currentTimeMillis()
            writeLog("========== Iteration $iteration ==========")

            try {
                // 4.1 Call LLM
                writeLog("📢 发送迭代进度更新...")
                _progressFlow.emit(ProgressUpdate.Iteration(iteration))
                writeLog("✅ 迭代进度已发送")

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
                                if (toolRegistry.contains(functionName)) {
                                    writeLog("   → Universal tool")
                                    toolRegistry.execute(functionName, args)
                                } else if (androidToolRegistry.contains(functionName)) {
                                    writeLog("   → Android tool")
                                    androidToolRegistry.execute(functionName, args)
                                } else {
                                    writeLog("   ❌ Unknown function: $functionName")
                                    Log.e(TAG, "   ❌ Unknown function: $functionName")
                                    SkillResult.error("Unknown function: $functionName")
                                }
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

                    val iterationDuration = System.currentTimeMillis() - iterationStartTime
                    writeLog("⏱️ 本轮迭代总耗时: ${iterationDuration}ms (LLM: ${llmDuration}ms, 执行: ${totalExecDuration}ms)")

                    // Send iteration complete event (with time statistics)
                    _progressFlow.emit(ProgressUpdate.IterationComplete(iteration, iterationDuration, llmDuration, totalExecDuration))
                    continue
                }

                // 4.4 No tool calls, meaning LLM provided final answer
                finalContent = response.content
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
                        // Timeout error, can retry
                        writeLog("Timeout error, retrying...")
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
        val effectiveFinalContent = finalContent ?: "无响应"
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
}
