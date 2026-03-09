package com.xiaomo.androidforclaw.gateway.methods

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.loop.AgentResult
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.gateway.protocol.*
import com.xiaomo.androidforclaw.gateway.websocket.GatewayWebSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate

/**
 * Agent RPC methods implementation with async execution
 */
class AgentMethods(
    private val context: Context,
    private val agentLoop: AgentLoop,
    private val sessionManager: SessionManager,
    private val gateway: GatewayWebSocketServer
) {
    private val TAG = "AgentMethods"
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 存储运行中的 agent 任务
    private val runningTasks = ConcurrentHashMap<String, AgentTask>()

    /**
     * agent() - Execute an agent run asynchronously
     */
    suspend fun agent(params: AgentParams): AgentRunResponse {
        val runId = "run_${UUID.randomUUID()}"
        val acceptedAt = System.currentTimeMillis()

        // 创建任务
        val task = AgentTask(
            runId = runId,
            sessionKey = params.sessionKey,
            message = params.message,
            status = "running"
        )
        runningTasks[runId] = task

        // 发送 agent.start 事件
        broadcastEvent("agent.start", mapOf(
            "runId" to runId,
            "sessionKey" to params.sessionKey,
            "message" to params.message,
            "acceptedAt" to acceptedAt
        ))

        // 异步执行 agent
        agentScope.launch {
            try {
                executeAgent(runId, params)
            } catch (e: Exception) {
                Log.e(TAG, "Agent execution failed: $runId", e)
                task.status = "error"
                task.error = e.message

                // 发送 agent.error 事件
                broadcastEvent("agent.error", mapOf(
                    "runId" to runId,
                    "error" to e.message
                ))
            } finally {
                // 任务完成后保留一段时间供 wait() 查询
                // 实际应该有 TTL 清理机制
            }
        }

        return AgentRunResponse(
            runId = runId,
            acceptedAt = acceptedAt
        )
    }

    /**
     * agent.wait() - Wait for agent run completion
     */
    suspend fun agentWait(params: AgentWaitParams): AgentWaitResponse {
        val task = runningTasks[params.runId]
            ?: return AgentWaitResponse(
                runId = params.runId,
                status = "not_found",
                result = null
            )

        val timeout = params.timeout ?: 30000L

        // 等待任务完成
        val result = withTimeoutOrNull(timeout) {
            task.resultChannel.receive()
        }

        return if (result != null) {
            AgentWaitResponse(
                runId = params.runId,
                status = "completed",
                result = mapOf(
                    "content" to result.finalContent,
                    "iterations" to result.iterations,
                    "toolsUsed" to result.toolsUsed
                )
            )
        } else {
            AgentWaitResponse(
                runId = params.runId,
                status = if (task.status == "error") "error" else "timeout",
                result = if (task.status == "error") mapOf("error" to task.error) else null
            )
        }
    }

    /**
     * agent.identity() - Get agent identity
     */
    fun agentIdentity(): AgentIdentityResult {
        return AgentIdentityResult(
            name = "androidforclaw",
            version = "1.0.0",
            platform = "android",
            capabilities = listOf(
                "screenshot",
                "tap",
                "swipe",
                "type",
                "navigation",
                "app_control",
                "accessibility"
            )
        )
    }

    /**
     * 执行 agent 任务
     */
    private suspend fun executeAgent(runId: String, params: AgentParams) {
        val task = runningTasks[runId] ?: return

        try {
            // 使用简单的系统提示词
            val systemPrompt = """
You are an AI agent controlling an Android device.

Available tools:
- screenshot(): Capture screen
- tap(x, y): Tap at coordinates
- swipe(startX, startY, endX, endY, duration): Swipe gesture
- type(text): Input text
- home(): Press home button
- back(): Press back button
- open_app(package): Open application

Instructions:
1. Always screenshot before and after actions
2. Verify results after each operation
3. Be precise with coordinates
4. Use stop() when task is complete
            """.trimIndent()

            // 获取或创建 session
            val session = sessionManager.getOrCreate(params.sessionKey)

            // 订阅 AgentLoop 进度更新并转发为 Gateway Events
            val progressJob = agentLoop.progressFlow
                .onEach { progress ->
                    when (progress) {
                        is ProgressUpdate.Iteration -> {
                            broadcastEvent("agent.iteration", mapOf(
                                "runId" to runId,
                                "iteration" to progress.number
                            ))
                        }
                        is ProgressUpdate.Thinking -> {
                            // 中间反馈: 正在思考第 X 步
                            broadcastEvent("agent.thinking", mapOf(
                                "runId" to runId,
                                "iteration" to progress.iteration,
                                "message" to "正在处理第 ${progress.iteration} 步..."
                            ))
                        }
                        is ProgressUpdate.ToolCall -> {
                            broadcastEvent("agent.tool_call", mapOf(
                                "runId" to runId,
                                "tool" to progress.name,
                                "arguments" to progress.arguments
                            ))
                        }
                        is ProgressUpdate.ToolResult -> {
                            broadcastEvent("agent.tool_result", mapOf(
                                "runId" to runId,
                                "tool" to progress.name,
                                "result" to progress.result,
                                "duration" to progress.execDuration
                            ))
                        }
                        is ProgressUpdate.Reasoning -> {
                            // Extended thinking 进度 (可选)
                            broadcastEvent("agent.thinking", mapOf(
                                "runId" to runId,
                                "content" to progress.content.take(200), // 限制长度
                                "duration" to progress.llmDuration
                            ))
                        }
                        is ProgressUpdate.IterationComplete -> {
                            // 迭代完成统计 (可选)
                            Log.d(TAG, "Iteration ${progress.number} complete: ${progress.iterationDuration}ms")
                        }
                        is ProgressUpdate.ContextOverflow -> {
                            broadcastEvent("agent.context_overflow", mapOf(
                                "runId" to runId,
                                "message" to progress.message
                            ))
                        }
                        is ProgressUpdate.ContextRecovered -> {
                            broadcastEvent("agent.context_recovered", mapOf(
                                "runId" to runId,
                                "strategy" to progress.strategy,
                                "attempt" to progress.attempt
                            ))
                        }
                        is ProgressUpdate.LoopDetected -> {
                            broadcastEvent("agent.loop_detected", mapOf(
                                "runId" to runId,
                                "detector" to progress.detector,
                                "count" to progress.count,
                                "message" to progress.message,
                                "critical" to progress.critical
                            ))
                        }
                        is ProgressUpdate.Error -> {
                            // Error 已通过 agent.error 事件发送
                            Log.w(TAG, "Progress error: ${progress.message}")
                        }
                    }
                }
                .launchIn(agentScope)

            // 执行 agent loop
            val result = agentLoop.run(
                systemPrompt = systemPrompt,
                userMessage = params.message,
                contextHistory = emptyList(),
                reasoningEnabled = true
            )

            // 取消进度订阅
            progressJob.cancel()

            // 更新任务状态
            task.status = "completed"
            task.result = result

            // 发送完成信号
            task.resultChannel.send(result)

            // 发送 agent.complete 事件
            broadcastEvent("agent.complete", mapOf(
                "runId" to runId,
                "status" to "completed",
                "iterations" to result.iterations,
                "toolsUsed" to result.toolsUsed,
                "content" to result.finalContent
            ))

            Log.i(TAG, "Agent completed: $runId, iterations=${result.iterations}")

        } catch (e: Exception) {
            task.status = "error"
            task.error = e.message
            throw e
        }
    }

    /**
     * 广播事件 (OpenClaw Protocol v45: uses "payload" not "data")
     */
    private var eventSeq = 0L

    private fun broadcastEvent(event: String, data: Any?) {
        try {
            gateway.broadcast(EventFrame(
                event = event,
                payload = data,  // OpenClaw uses "payload" not "data"
                seq = eventSeq++  // Add sequence number
            ))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast event: $event", e)
        }
    }
}

/**
 * Agent 任务
 */
private data class AgentTask(
    val runId: String,
    val sessionKey: String,
    val message: String,
    var status: String,
    var result: AgentResult? = null,
    var error: String? = null,
    val resultChannel: Channel<AgentResult> = Channel(1)
)
