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
 * Agent Loop - 核心循环引擎
 * 参考 OpenClaw 的 Agent Loop 实现
 *
 * 执行流程:
 * 1. 接收用户消息 + 系统提示词
 * 2. 调用 LLM (支持 reasoning)
 * 3. LLM 通过 function calling 选择工具
 * 4. 直接执行 LLM 选择的工具
 * 5. 重复步骤 2-4，直到 LLM 返回最终结果或达到最大迭代次数
 *
 * 架构（参考 OpenClaw pi-tools）:
 * - ToolRegistry: 通用工具（read, write, exec, web_fetch）
 * - AndroidToolRegistry: Android 平台工具（tap, screenshot, open_app）
 * - SkillsLoader: Skills 文档（mobile-operations.md）
 */
class AgentLoop(
    private val llmProvider: UnifiedLLMProvider,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    private val contextManager: ContextManager? = null,  // 可选的上下文管理器
    private val maxIterations: Int = 40,
    private val modelRef: String? = null
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_OVERFLOW_RECOVERY_ATTEMPTS = 3  // 对齐 OpenClaw
        private const val LLM_TIMEOUT_MS = 60_000L  // LLM 单次调用超时: 60 秒
        private const val MAX_CONSECUTIVE_ERRORS = 3  // 连续相同错误阈值: 3 次
    }

    private val gson = Gson()

    // 日志文件配置
    private val logDir = File("/sdcard/.androidforclaw/workspace/logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private var sessionLogFile: File? = null
    private val logBuffer = StringBuilder()

    // ✅ 缓存 Tool Definitions，避免每次迭代重复构建
    // 合并通用工具和 Android 平台工具
    private val allToolDefinitions by lazy {
        toolRegistry.getToolDefinitions() + androidToolRegistry.getToolDefinitions()
    }

    // 进度更新流
    private val _progressFlow = MutableSharedFlow<ProgressUpdate>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val progressFlow: SharedFlow<ProgressUpdate> = _progressFlow.asSharedFlow()

    // 停止标志
    @Volatile
    private var shouldStop = false

    // 循环检测器状态
    private val loopDetectionState = ToolLoopDetection.SessionState()

    // 错误追踪: 用于检测连续相同错误
    private val errorTracker = mutableListOf<String>()

    /**
     * 记录错误并检查是否达到阈值
     * @return true 如果应该停止执行
     */
    private fun trackError(errorMessage: String): Boolean {
        errorTracker.add(errorMessage)

        // 只保留最近的错误记录
        if (errorTracker.size > MAX_CONSECUTIVE_ERRORS) {
            errorTracker.removeAt(0)
        }

        // 检查最近的错误是否都相同
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
     * 写入日志到文件和缓冲区
     */
    private fun writeLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] $message"

        // 添加到缓冲区
        logBuffer.appendLine(logLine)

        // 写入到文件（如果文件已创建）
        sessionLogFile?.let { file ->
            try {
                file.appendText(logLine + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to file", e)
            }
        }

        // 同时输出到 logcat
        Log.d(TAG, message)
    }

    /**
     * 初始化会话日志文件
     */
    private fun initSessionLog(userMessage: String) {
        try {
            // 确保日志目录存在
            logDir.mkdirs()

            // 创建日志文件（使用时间戳 + 用户消息前缀作为文件名）
            val timestamp = dateFormat.format(Date())
            val messagePrefix = userMessage.take(20).replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
            val filename = "agentloop_${timestamp}_${messagePrefix}.log"
            sessionLogFile = File(logDir, filename)

            // 清空缓冲区
            logBuffer.clear()

            // 写入会话头部
            sessionLogFile?.writeText("========== Agent Loop Session ==========\n")
            sessionLogFile?.appendText("Start time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            sessionLogFile?.appendText("User message: $userMessage\n")
            sessionLogFile?.appendText("========================================\n\n")

            Log.i(TAG, "📝 Session log initialized: ${sessionLogFile?.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize session log (will continue without file logging): ${e.message}")
            sessionLogFile = null  // 禁用文件日志，但继续执行
        }
    }

    /**
     * 完成会话日志
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
     * 运行 Agent Loop
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param contextHistory 历史对话记录
     * @param reasoningEnabled 是否启用 reasoning
     * @return AgentResult 包含最终内容、使用的工具、所有消息
     */
    suspend fun run(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message> = emptyList(),
        reasoningEnabled: Boolean = true
    ): AgentResult {
        shouldStop = false
        val messages = mutableListOf<Message>()

        // 初始化会话日志
        initSessionLog(userMessage)

        // 重置上下文管理器
        contextManager?.reset()

        writeLog("========== Agent Loop 开始 ==========")
        writeLog("Max iterations: $maxIterations")
        writeLog("Model: ${modelRef ?: "default"}")
        writeLog("Reasoning: ${if (reasoningEnabled) "enabled" else "disabled"}")
        writeLog("🔧 Universal tools: ${toolRegistry.getToolCount()}")
        writeLog("📱 Android tools: ${androidToolRegistry.getToolCount()}")
        writeLog("🔄 Context manager: ${if (contextManager != null) "enabled" else "disabled"}")

        // 1. 添加系统提示词
        messages.add(systemMessage(systemPrompt))
        writeLog("✅ System prompt added (${systemPrompt.length} chars)")

        // 2. 添加历史对话
        messages.addAll(contextHistory)
        if (contextHistory.isNotEmpty()) {
            writeLog("✅ Context history added: ${contextHistory.size} messages")
        }

        // 3. 添加用户消息
        messages.add(userMessage(userMessage))
        writeLog("✅ User message: $userMessage")
        writeLog("📤 准备发送第一次 LLM 请求...")

        var iteration = 0
        var finalContent: String? = null
        val toolsUsed = mutableListOf<String>()

        // 4. 主循环
        while (iteration < maxIterations && !shouldStop) {
            iteration++
            val iterationStartTime = System.currentTimeMillis()
            writeLog("========== Iteration $iteration ==========")

            try {
                // 4.1 调用 LLM
                writeLog("📢 发送迭代进度更新...")
                _progressFlow.emit(ProgressUpdate.Iteration(iteration))
                writeLog("✅ 迭代进度已发送")

                writeLog("📤 调用 UnifiedLLMProvider.chatWithTools...")
                writeLog("   Messages: ${messages.size}, Tools+Skills: ${allToolDefinitions.size}")

                // 🔔 发送中间反馈: 正在思考第 X 步
                _progressFlow.emit(ProgressUpdate.Thinking(iteration))

                val llmStartTime = System.currentTimeMillis()

                // ⏱️ 添加超时保护
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

                    // 记录超时错误
                    if (trackError(errorMsg)) {
                        shouldStop = true
                        finalContent = "任务失败: $errorMsg"
                        break
                    }

                    // 添加超时错误消息，让 LLM 知道
                    messages.add(userMessage("系统提示: LLM 调用超时，请尝试简化任务或分步执行"))
                    continue
                }

                val llmDuration = System.currentTimeMillis() - llmStartTime

                writeLog("✅ LLM 响应已收到 [耗时: ${llmDuration}ms]")

                // ⚠️ 如果响应时间过长，记录警告
                if (llmDuration > 30_000) {
                    writeLog("⚠️ LLM 响应耗时较长: ${llmDuration}ms")
                }

                // 4.2 显示 reasoning 思考过程
                response.thinkingContent?.let { reasoning ->
                    writeLog("🧠 Reasoning (${reasoning.length} chars):")
                    writeLog("   ${reasoning.take(500)}${if (reasoning.length > 500) "..." else ""}")
                    _progressFlow.emit(ProgressUpdate.Reasoning(reasoning, llmDuration))
                }

                // 4.3 检查是否有 function calls
                if (response.toolCalls != null && response.toolCalls.isNotEmpty()) {
                    writeLog("Function calls: ${response.toolCalls.size}")

                    // 添加 assistant 消息（包含 function calls）
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

                    // 执行每个 tool/skill (直接执行 LLM 选择的能力)
                    var totalExecDuration = 0L
                    for (toolCall in response.toolCalls) {
                        val functionName = toolCall.name
                        val argsJson = toolCall.arguments

                        writeLog("🔧 Function: $functionName")
                        writeLog("   Args: $argsJson")

                        // 解析参数
                        val args = try {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(argsJson, Map::class.java) as Map<String, Any?>
                        } catch (e: Exception) {
                            writeLog("Failed to parse arguments: ${e.message}")
                            Log.e(TAG, "Failed to parse arguments", e)
                            mapOf<String, Any?>()
                        }

                        // ✅ 检测工具调用循环 (在执行前)
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

                                // Critical 级别: 中断执行
                                if (loopDetection.level == ToolLoopDetection.LoopDetectionResult.Level.CRITICAL) {
                                    writeLog("🛑 Critical loop detected, stopping execution")
                                    Log.e(TAG, "🛑 Critical loop detected: ${loopDetection.message}")

                                    // 添加错误消息到对话
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

                                    // 中断整个循环
                                    shouldStop = true
                                    finalContent = "Task failed: ${loopDetection.message}"
                                    break
                                }

                                // Warning 级别: 注入警告但继续执行
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

                                // Warning 后跳过本次工具调用
                                continue
                            }
                            ToolLoopDetection.LoopDetectionResult.NoLoop -> {
                                // 无循环,继续执行
                            }
                        }

                        // 记录工具调用 (在执行前)
                        ToolLoopDetection.recordToolCall(
                            state = loopDetectionState,
                            toolName = functionName,
                            params = args,
                            toolCallId = toolCall.id
                        )

                        toolsUsed.add(functionName)

                        // 发送调用进度更新
                        _progressFlow.emit(ProgressUpdate.ToolCall(functionName, args))

                        // ✅ 先从通用工具查找，再从 Android 工具查找
                        val execStartTime = System.currentTimeMillis()
                        val result = if (toolRegistry.contains(functionName)) {
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
                        val execDuration = System.currentTimeMillis() - execStartTime
                        totalExecDuration += execDuration

                        writeLog("   Result: ${result.success}, ${result.content.take(200)}")
                        writeLog("   ⏱️ 执行耗时: ${execDuration}ms")

                        // 🔍 追踪工具执行错误
                        if (!result.success) {
                            val errorMsg = result.content
                            writeLog("   ⚠️ 工具执行失败: $errorMsg")

                            // 检查是否达到错误阈值
                            if (trackError("$functionName: $errorMsg")) {
                                shouldStop = true
                                finalContent = "任务失败: 连续 $MAX_CONSECUTIVE_ERRORS 次相同错误 - $errorMsg"

                                // 添加错误消息
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
                            // 成功执行，清空错误追踪
                            if (errorTracker.isNotEmpty()) {
                                writeLog("   ✅ 工具执行成功，清空错误追踪")
                                errorTracker.clear()
                            }
                        }

                        // 记录工具调用结果 (用于循环检测)
                        ToolLoopDetection.recordToolCallOutcome(
                            state = loopDetectionState,
                            toolName = functionName,
                            toolParams = args,
                            result = result.toString(),
                            error = if (result.success) null else Exception(result.content),
                            toolCallId = toolCall.id
                        )

                        // 添加结果到消息列表
                        messages.add(
                            toolMessage(
                                toolCallId = toolCall.id,
                                content = result.toString(),
                                name = functionName
                            )
                        )

                        // 发送结果更新
                        _progressFlow.emit(ProgressUpdate.ToolResult(functionName, result.toString(), execDuration))

                        // 检查是否是 stop skill
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

                    // 继续循环，让 LLM 看到 function 结果后决定下一步
                    if (shouldStop) break

                    val iterationDuration = System.currentTimeMillis() - iterationStartTime
                    writeLog("⏱️ 本轮迭代总耗时: ${iterationDuration}ms (LLM: ${llmDuration}ms, 执行: ${totalExecDuration}ms)")

                    // 发送迭代完成事件（带时间统计）
                    _progressFlow.emit(ProgressUpdate.IterationComplete(iteration, iterationDuration, llmDuration, totalExecDuration))
                    continue
                }

                // 4.4 没有工具调用，说明 LLM 给出了最终答案
                finalContent = response.content
                messages.add(assistantMessage(content = finalContent))

                writeLog("Final content received (finish_reason: ${response.finishReason})")
                writeLog("Content: ${finalContent?.take(500)}${if ((finalContent?.length ?: 0) > 500) "..." else ""}")
                break

            } catch (e: Exception) {
                writeLog("Iteration $iteration error: ${e.message}")
                Log.e(TAG, "Iteration $iteration error", e)
                LayoutExceptionLogger.log("AgentLoop#run#iteration$iteration", e)

                // 检查是否是上下文超限错误
                val errorMessage = ContextErrors.extractErrorMessage(e)
                val isContextOverflow = ContextErrors.isLikelyContextOverflowError(errorMessage)

                if (isContextOverflow && contextManager != null) {
                    writeLog("🔄 检测到上下文超限，尝试恢复...")
                    Log.w(TAG, "🔄 检测到上下文超限，尝试恢复...")
                    _progressFlow.emit(ProgressUpdate.ContextOverflow("Context overflow detected, attempting recovery..."))

                    // 尝试恢复
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

                            // 替换消息列表
                            messages.clear()
                            messages.addAll(recoveryResult.messages)

                            // 重试当前迭代
                            continue
                        }
                        is ContextRecoveryResult.CannotRecover -> {
                            writeLog("❌ 上下文恢复失败: ${recoveryResult.reason}")
                            Log.e(TAG, "❌ 上下文恢复失败: ${recoveryResult.reason}")
                            _progressFlow.emit(ProgressUpdate.Error("Context overflow: ${recoveryResult.reason}"))

                            finalContent = "执行异常: ${recoveryResult.reason}"
                            break
                        }
                    }
                } else {
                    // 非上下文超限错误
                    _progressFlow.emit(ProgressUpdate.Error(e.message ?: "Unknown error"))

                    // 尝试继续或停止
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        // 超时错误，可以重试
                        writeLog("Timeout error, retrying...")
                        continue
                    } else {
                        // 其他错误，停止循环
                        finalContent = "执行异常: ${e.message}"
                        break
                    }
                }
            }
        }

        // 5. 处理循环结束
        if (finalContent == null && iteration >= maxIterations) {
            writeLog("Max iterations ($maxIterations) reached")
            Log.w(TAG, "Max iterations ($maxIterations) reached")
            finalContent = "达到最大迭代次数 ($maxIterations)，任务未完成。" +
                    "建议将任务拆分为更小的步骤。"
        }

        writeLog("========== Agent Loop 结束 ==========")
        writeLog("Iterations: $iteration")
        writeLog("Tools used: ${toolsUsed.joinToString(", ")}")

        val result = AgentResult(
            finalContent = finalContent ?: "无响应",
            toolsUsed = toolsUsed,
            messages = messages,
            iterations = iteration
        )

        // 完成会话日志
        finalizeSessionLog(result)

        return result
    }

    /**
     * 停止 Agent Loop
     */
    fun stop() {
        shouldStop = true
        Log.d(TAG, "Stop signal received")
    }
}

/**
 * Agent 执行结果
 */
data class AgentResult(
    val finalContent: String,
    val toolsUsed: List<String>,
    val messages: List<Message>,
    val iterations: Int
)

/**
 * 进度更新
 */
sealed class ProgressUpdate {
    /** 开始新迭代 */
    data class Iteration(val number: Int) : ProgressUpdate()

    /** 正在思考第 X 步（中间反馈） */
    data class Thinking(val iteration: Int) : ProgressUpdate()

    /** Reasoning 思考过程 */
    data class Reasoning(val content: String, val llmDuration: Long) : ProgressUpdate()

    /** 工具调用 */
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : ProgressUpdate()

    /** 工具结果 */
    data class ToolResult(val name: String, val result: String, val execDuration: Long) : ProgressUpdate()

    /** 迭代完成 */
    data class IterationComplete(val number: Int, val iterationDuration: Long, val llmDuration: Long, val execDuration: Long) : ProgressUpdate()

    /** 上下文超限 */
    data class ContextOverflow(val message: String) : ProgressUpdate()

    /** 上下文恢复成功 */
    data class ContextRecovered(val strategy: String, val attempt: Int) : ProgressUpdate()

    /** 错误 */
    data class Error(val message: String) : ProgressUpdate()

    /** 循环检测 */
    data class LoopDetected(
        val detector: String,
        val count: Int,
        val message: String,
        val critical: Boolean
    ) : ProgressUpdate()
}
