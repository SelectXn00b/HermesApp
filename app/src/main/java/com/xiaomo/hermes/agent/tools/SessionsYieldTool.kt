/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/sessions-yield-tool.ts
 *
 * Hermes adaptation: LLM-facing tool to yield the current turn.
 * Sets a CompletableDeferred on the parent AgentLoop that pauses the loop
 * after the current tool execution round until subagent announcements arrive.
 */
package com.xiaomo.hermes.agent.tools

import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition
import kotlinx.coroutines.CompletableDeferred

/**
 * sessions_yield — Yield the current turn to wait for subagent results.
 * Aligned with OpenClaw createSessionsYieldTool.
 *
 * The tool sets a yield signal on the parent AgentLoop. After this tool execution
 * round completes, the loop pauses until the deferred is completed (by subagent
 * announce) or times out (300s).
 */
class SessionsYieldTool(
    private val parentAgentLoop: AgentLoopInterface,
) : Tool {

    override val name = "sessions_yield"
    override val description = "Yield the current turn to wait for subagent completion results. " +
        "The agent loop pauses until subagent announcements arrive via the steer channel, " +
        "or until a 300-second timeout. Use this when you need to wait for spawned subagents " +
        "before continuing."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "message" to PropertySchema(
                            type = "string",
                            description = "Optional status message (e.g. 'Waiting for research results...')."
                        ),
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val message = args["message"] as? String ?: "Turn yielded."

        // Create a deferred that will be completed by announceToParent or timeout
        val deferred = CompletableDeferred<String?>()
        parentAgentLoop.yieldSignal = deferred

        // Tool returns immediately — the actual pause happens in AgentLoop main loop
        // when it checks yieldSignal after this tool execution round completes.
        return ToolResult(
            success = true,
            content = "Yield requested: $message. The loop will pause after this tool execution " +
                "round and resume when subagent results arrive (timeout: 300s).",
            metadata = mapOf("yielded" to true, "message" to message)
        )
    }
}
