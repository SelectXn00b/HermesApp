/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-chat.ts
 */
package com.xiaomo.androidforclaw.gateway

import android.app.Application
import com.xiaomo.androidforclaw.logging.Log
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
 * AgentHandler implementation - connects GatewayService and AgentLoop
 *
 * Responsibilities:
 * 1. Receive Gateway RPC requests
 * 2. Call AgentLoop to execute tasks
 * 3. Send back progress and results
 */
class MainEntryAgentHandler(
    private val application: Application
) : AgentHandler {

    companion object {
        private const val TAG = "MainEntryAgentHandler"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()

    // Core components - use unified LLM Provider
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
            taskDataManager = taskDataManager,
            cameraCaptureManager = com.xiaomo.androidforclaw.core.MyApplication.getCameraCaptureManager(),
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
                // 1. Build system prompt (if not provided)
                val finalSystemPrompt = systemPrompt ?: contextBuilder.buildSystemPrompt(
                    userGoal = userMessage,
                    packageName = "",
                    testMode = "exploration"
                )

                Log.d(TAG, "System prompt ready (${finalSystemPrompt.length} chars)")

                // 2. Create AgentLoop (with context management)
                val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
                val agentLoop = AgentLoop(
                    llmProvider = llmProvider,
                    toolRegistry = toolRegistry,
                    androidToolRegistry = androidToolRegistry,
                    contextManager = contextManager,
                    maxIterations = maxIterations,
                    modelRef = null  // Use default model, can be read from config
                )

                // 3. Listen to progress
                val progressJob = launch {
                    agentLoop.progressFlow.collect { update ->
                        val progressData = convertProgressToMap(update)
                        progressCallback(progressData)
                    }
                }

                // 4. Execute Agent
                Log.d(TAG, "Starting AgentLoop execution...")
                val result = agentLoop.run(
                    systemPrompt = finalSystemPrompt,
                    userMessage = userMessage,
                    reasoningEnabled = true  // Enable reasoning by default
                )

                Log.d(TAG, "AgentLoop completed: ${result.iterations} iterations")

                // 5. Return result
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
     * Convert ProgressUpdate to Map (for JSON serialization)
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
            is ProgressUpdate.BlockReply -> mapOf(
                "type" to "block_reply",
                "text" to update.text,
                "iteration" to update.iteration
            )
            is ProgressUpdate.SteerMessageInjected -> mapOf(
                "type" to "steer_message_injected",
                "content" to update.content
            )
        }
    }
}
