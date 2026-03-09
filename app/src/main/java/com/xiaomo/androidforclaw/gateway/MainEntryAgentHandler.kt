package com.xiaomo.androidforclaw.gateway

import android.app.Application
import android.util.Log
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AgentHandler 实现 - 连接 GatewayService 和 AgentLoop
 *
 * 负责：
 * 1. 接收 Gateway RPC 请求
 * 2. 调用 AgentLoop 执行任务
 * 3. 回传进度和结果
 */
class MainEntryAgentHandler(
    private val application: Application
) : AgentHandler {

    companion object {
        private const val TAG = "MainEntryAgentHandler"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()

    // 核心组件 - 使用统一 LLM Provider
    private val llmProvider: UnifiedLLMProvider by lazy {
        UnifiedLLMProvider(application)
    }

    private val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(
            context = application,
            taskDataManager = taskDataManager
        )
    }

    private val androidToolRegistry: AndroidToolRegistry by lazy {
        AndroidToolRegistry(
            context = application,
            taskDataManager = taskDataManager
        )
    }

    private val contextBuilder: ContextBuilder by lazy {
        ContextBuilder(
            context = application,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry
        )
    }

    override fun executeAgent(
        sessionId: String,
        userMessage: String,
        systemPrompt: String?,
        tools: List<Any>?,
        maxIterations: Int,
        progressCallback: (Map<String, Any>) -> Unit,
        completeCallback: (Map<String, Any>) -> Unit
    ) {
        Log.d(TAG, "executeAgent called: session=$sessionId, message=$userMessage")

        scope.launch {
            try {
                // 1. 构建系统提示词（如果未提供）
                val finalSystemPrompt = systemPrompt ?: contextBuilder.buildSystemPrompt(
                    userGoal = userMessage,
                    packageName = "",
                    testMode = "exploration"
                )

                Log.d(TAG, "System prompt ready (${finalSystemPrompt.length} chars)")

                // 2. 创建 AgentLoop（带上下文管理）
                val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
                val agentLoop = AgentLoop(
                    llmProvider = llmProvider,
                    toolRegistry = toolRegistry,
                    androidToolRegistry = androidToolRegistry,
                    contextManager = contextManager,
                    maxIterations = maxIterations,
                    modelRef = null  // 使用默认模型，可从配置读取
                )

                // 3. 监听进度
                val progressJob = launch {
                    agentLoop.progressFlow.collect { update ->
                        val progressData = convertProgressToMap(update)
                        progressCallback(progressData)
                    }
                }

                // 4. 执行 Agent
                Log.d(TAG, "Starting AgentLoop execution...")
                val result = agentLoop.run(
                    systemPrompt = finalSystemPrompt,
                    userMessage = userMessage,
                    reasoningEnabled = true  // 默认启用 reasoning
                )

                Log.d(TAG, "AgentLoop completed: ${result.iterations} iterations")

                // 5. 返回结果
                completeCallback(mapOf(
                    "success" to true,
                    "iterations" to result.iterations,
                    "toolsUsed" to result.toolsUsed,
                    "finalContent" to result.finalContent,
                    "sessionId" to sessionId
                ))

                progressJob.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Agent execution failed", e)
                completeCallback(mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error"),
                    "sessionId" to sessionId
                ))
            }
        }
    }

    /**
     * 将 ProgressUpdate 转换为 Map（用于 JSON 序列化）
     */
    private fun convertProgressToMap(update: ProgressUpdate): Map<String, Any> {
        return when (update) {
            is ProgressUpdate.Iteration -> mapOf(
                "type" to "iteration",
                "number" to update.number
            )

            is ProgressUpdate.Thinking -> mapOf(
                "type" to "thinking",
                "iteration" to update.iteration
            )

            is ProgressUpdate.Reasoning -> mapOf(
                "type" to "reasoning",
                "content" to update.content,
                "duration" to update.llmDuration
            )

            is ProgressUpdate.ToolCall -> mapOf(
                "type" to "tool_call",
                "name" to update.name,
                "arguments" to update.arguments
            )

            is ProgressUpdate.ToolResult -> mapOf(
                "type" to "tool_result",
                "result" to update.result,
                "duration" to update.execDuration
            )

            is ProgressUpdate.IterationComplete -> mapOf(
                "type" to "iteration_complete",
                "number" to update.number,
                "iterationDuration" to update.iterationDuration,
                "llmDuration" to update.llmDuration,
                "execDuration" to update.execDuration
            )

            is ProgressUpdate.ContextOverflow -> mapOf(
                "type" to "context_overflow",
                "message" to update.message
            )

            is ProgressUpdate.ContextRecovered -> mapOf(
                "type" to "context_recovered",
                "strategy" to update.strategy,
                "attempt" to update.attempt
            )

            is ProgressUpdate.LoopDetected -> mapOf(
                "type" to "loop_detected",
                "detector" to update.detector,
                "count" to update.count,
                "message" to update.message,
                "critical" to update.critical
            )

            is ProgressUpdate.Error -> mapOf(
                "type" to "error",
                "message" to update.message
            )
        }
    }
}
