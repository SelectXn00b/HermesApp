/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/sessions-send-tool.ts
 * - ../openclaw/src/agents/subagent-control.ts (steerControlledSubagentRun, sendControlledSubagentMessage)
 *
 * Hermes adaptation: LLM-facing tool to send messages to running subagents.
 * Supports multi-strategy target resolution and fire-and-forget / wait modes.
 * Steer semantics: abort current run + restart with new message (aligned with OpenClaw).
 */
package com.xiaomo.hermes.agent.tools

import com.xiaomo.hermes.agent.loop.AgentLoop
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.agent.subagent.SessionAccessResult
import com.xiaomo.hermes.agent.subagent.SessionVisibilityGuard
import com.xiaomo.hermes.agent.subagent.SpawnMode
import com.xiaomo.hermes.agent.subagent.getRunByChildSessionKey
import com.xiaomo.hermes.agent.subagent.resolveTarget
import com.xiaomo.hermes.agent.subagent.SubagentSpawner
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * sessions_send — Send a message to a running subagent (steer = abort + restart).
 * Aligned with OpenClaw steerControlledSubagentRun + sendControlledSubagentMessage.
 *
 * Target resolution supports: session_key, label, or generic target token
 * ("last", numeric index, label prefix, runId prefix).
 */
class SessionsSendTool(
    private val spawner: SubagentSpawner,
    private val parentSessionKey: String,
    private val parentAgentLoop: AgentLoopInterface,
) : Tool {

    override val name = "sessions_send"
    override val description = "Send a message to a running subagent to steer or redirect its work. " +
        "This aborts the subagent's current run and restarts it with the new message. " +
        "Identify the target via session_key, label, or the generic target token."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "session_key" to PropertySchema(
                            type = "string",
                            description = "Target session key (alternative to target)"
                        ),
                        "label" to PropertySchema(
                            type = "string",
                            description = "Target subagent label (alternative to target)"
                        ),
                        "agent_id" to PropertySchema(
                            type = "string",
                            description = "Target agent ID for cross-agent messaging"
                        ),
                        "target" to PropertySchema(
                            type = "string",
                            description = "Target subagent: 'last', numeric index (1-based), label prefix, run ID, or session key."
                        ),
                        "message" to PropertySchema(
                            type = "string",
                            description = "The message to send to the subagent."
                        ),
                        "timeout_seconds" to PropertySchema(
                            type = "number",
                            description = "Wait timeout in seconds. 0 = fire-and-forget. >0 = wait for completion. Default: 30."
                        ),
                    ),
                    required = listOf("message")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val sessionKey = args["session_key"] as? String
        val label = args["label"] as? String
        val agentId = args["agent_id"] as? String
        val target = args["target"] as? String
        val message = args["message"] as? String
        if (message.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: message")
        }
        val timeoutSeconds = (args["timeout_seconds"] as? Number)?.toInt() ?: 30

        // Resolve target: session_key > label > target, error if none provided
        val record = when {
            !sessionKey.isNullOrBlank() -> {
                spawner.registry.getRunByChildSessionKey(sessionKey)
                    ?: return ToolResult(success = false, content = "No subagent found for session_key: $sessionKey")
            }
            !label.isNullOrBlank() -> {
                spawner.registry.resolveTarget(label, parentSessionKey)
                    ?: return ToolResult(success = false, content = "No subagent found for label: $label")
            }
            !target.isNullOrBlank() -> {
                spawner.registry.resolveTarget(target, parentSessionKey)
                    ?: return ToolResult(success = false, content = "No matching subagent found for target: $target")
            }
            else -> {
                return ToolResult.error("Must provide one of: session_key, label, or target")
            }
        }

        // Visibility guard (aligned with OpenClaw controlScope)
        val visibility = SessionVisibilityGuard.resolveVisibility(parentSessionKey, spawner.registry)
        val access = SessionVisibilityGuard.checkAccess(
            "send to", parentSessionKey, record.childSessionKey, visibility, spawner.registry
        )
        if (access is SessionAccessResult.Denied) {
            return ToolResult(success = false, content = access.reason)
        }

        // If target is completed SESSION mode, reactivate instead of steer
        if (!record.isActive && record.spawnMode == SpawnMode.SESSION) {
            val (reactivateSuccess, reactivateInfo) = spawner.reactivateSession(
                childSessionKey = record.childSessionKey,
                message = message,
                callerSessionKey = parentSessionKey,
                parentAgentLoop = parentAgentLoop,
            )
            if (!reactivateSuccess) {
                return ToolResult(success = false, content = "Session reactivation failed: $reactivateInfo")
            }
            return ToolResult(success = true, content = "Session '${record.label}' reactivated: $reactivateInfo")
        }

        // Steer (abort + restart, aligned with OpenClaw)
        val (success, info) = spawner.steer(
            runId = record.runId,
            message = message,
            callerSessionKey = parentSessionKey,
            parentAgentLoop = parentAgentLoop,
        )

        if (!success) {
            return ToolResult(success = false, content = "Steer failed: $info")
        }

        // Fire-and-forget mode
        if (timeoutSeconds <= 0) {
            return ToolResult(success = true, content = "Message sent to ${record.label}: $info")
        }

        // Wait mode: poll for child completion
        val waitMs = timeoutSeconds * 1000L
        val startWait = System.currentTimeMillis()
        while (System.currentTimeMillis() - startWait < waitMs) {
            val latestRecord = spawner.registry.getRunByChildSessionKey(record.childSessionKey)
            if (latestRecord != null && !latestRecord.isActive) {
                return ToolResult(
                    success = true,
                    content = buildString {
                        appendLine("Subagent '${latestRecord.label}' completed after steer.")
                        appendLine("Status: ${latestRecord.outcome?.status?.wireValue ?: "unknown"}")
                        latestRecord.frozenResultText?.let { text ->
                            appendLine("Result: ${text.take(4000)}")
                        }
                    }
                )
            }
            kotlinx.coroutines.delay(500)
        }

        return ToolResult(
            success = true,
            content = "Steer sent to ${record.label}. Child still running after ${timeoutSeconds}s wait."
        )
    }
}
