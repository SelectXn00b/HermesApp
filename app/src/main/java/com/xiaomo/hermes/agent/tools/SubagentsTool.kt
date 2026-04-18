/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/subagents-tool.ts
 * - ../openclaw/src/agents/subagent-control.ts (resolveControlledSubagentTarget, killAll, steer)
 *
 * Hermes adaptation: meta-tool for subagent orchestration (list/kill/steer).
 * Aligned with OpenClaw subagents tool — single tool with action parameter.
 */
package com.xiaomo.hermes.agent.tools

import com.xiaomo.hermes.agent.loop.AgentLoop
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.agent.subagent.DEFAULT_RECENT_MINUTES
import com.xiaomo.hermes.agent.subagent.MAX_STEER_MESSAGE_CHARS
import com.xiaomo.hermes.agent.subagent.SubagentRegistry
import com.xiaomo.hermes.agent.subagent.SubagentSpawner
import com.xiaomo.hermes.agent.subagent.getSubagentSessionStartedAt
import com.xiaomo.hermes.agent.subagent.isActiveSubagentRun
import com.xiaomo.hermes.agent.subagent.countPendingDescendantRuns
import com.xiaomo.hermes.agent.subagent.countPendingDescendantRunsExcludingRun
import com.xiaomo.hermes.agent.subagent.listRunsForController
import com.xiaomo.hermes.agent.subagent.resolveSubagentLabel
import com.xiaomo.hermes.agent.subagent.resolveTarget
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * subagents — List, kill, or steer spawned sub-agents for this requester session.
 * Aligned with OpenClaw createSubagentsTool.
 */
class SubagentsTool(
    private val spawner: SubagentSpawner,
    private val registry: SubagentRegistry,
    private val callerSessionKey: String,
    private val parentAgentLoop: AgentLoopInterface,
) : Tool {

    companion object {
        /** Maximum recentMinutes (24 hours). Aligned with OpenClaw MAX_RECENT_MINUTES. */
        private const val MAX_RECENT_MINUTES = 1440
    }

    override val name = "subagents"
    override val description = "List, kill, or steer spawned sub-agents for this requester session. Use this for sub-agent orchestration."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Action: 'list' (default), 'kill', or 'steer'.",
                            enum = listOf("list", "kill", "steer"),
                        ),
                        "target" to PropertySchema(
                            type = "string",
                            description = "Target subagent: numeric index (1-based), label, label prefix, run ID prefix, session key, or 'all'/'*' (for kill)."
                        ),
                        "message" to PropertySchema(
                            type = "string",
                            description = "Steering message (required for 'steer' action). Max $MAX_STEER_MESSAGE_CHARS chars."
                        ),
                        "recentMinutes" to PropertySchema(
                            type = "number",
                            description = "Minutes window for recent completed subagents in list view. Default: $DEFAULT_RECENT_MINUTES, max: $MAX_RECENT_MINUTES."
                        ),
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = (args["action"] as? String) ?: "list"

        return when (action) {
            "list" -> executeList(args)
            "kill" -> executeKill(args)
            "steer" -> executeSteer(args)
            else -> ToolResult.error("Unknown action: $action. Valid actions: list, kill, steer.")
        }
    }

    // ==================== List ====================

    private fun executeList(args: Map<String, Any?>): ToolResult {
        val recentMinutes = ((args["recentMinutes"] as? Number)?.toInt() ?: DEFAULT_RECENT_MINUTES)
            .coerceIn(1, MAX_RECENT_MINUTES)

        val allRuns = registry.listRunsForController(callerSessionKey)
        if (allRuns.isEmpty()) {
            return ToolResult(
                success = true,
                content = "No subagent runs found for this session.",
                metadata = mapOf("action" to "list", "total" to 0)
            )
        }

        val now = System.currentTimeMillis()
        val recentCutoff = now - recentMinutes * 60_000L

        // Use isActiveSubagentRun (aligned with OpenClaw: active = not ended OR has pending descendants)
        val pendingDescendantCount = { sessionKey: String ->
            registry.countPendingDescendantRuns(sessionKey)
        }
        val active = allRuns.filter { isActiveSubagentRun(it, pendingDescendantCount) }
        val recent = allRuns.filter {
            !isActiveSubagentRun(it, pendingDescendantCount) &&
            (it.endedAt ?: 0L) >= recentCutoff
        }

        val text = buildString {
            appendLine("Subagents for session: $callerSessionKey")
            appendLine("Total: ${allRuns.size} (${active.size} active, ${recent.size} recent)")
            appendLine()

            var index = 1
            if (active.isNotEmpty()) {
                appendLine("## Active")
                for (run in active) {
                    val runtime = SessionsListTool.formatDurationCompact(run.runtimeMs)
                    val pendingChildren = registry.countPendingDescendantRunsExcludingRun(run.childSessionKey, run.runId)
                    val status = if (pendingChildren > 0) {
                        "active (waiting on $pendingChildren children)"
                    } else {
                        "running"
                    }
                    val displayLabel = resolveSubagentLabel(run)
                    val task = run.task.take(72).replace('\n', ' ')
                    val taskSuffix = if (task.lowercase() != displayLabel.lowercase()) " - $task" else ""
                    val startedAt = getSubagentSessionStartedAt(run)
                    appendLine("${index}. $displayLabel (${run.model ?: "default"}, $runtime) $status$taskSuffix")
                    appendLine("   runId=${run.runId} session=${run.childSessionKey} startedAt=$startedAt")
                    index++
                }
                appendLine()
            }

            if (recent.isNotEmpty()) {
                appendLine("## Recent (last ${recentMinutes}min)")
                for (run in recent) {
                    val status = when (run.outcome?.status?.wireValue) {
                        "ok" -> "done"
                        "error" -> "failed: ${run.outcome?.error ?: "unknown"}"
                        "timeout" -> "timed out"
                        else -> run.outcome?.status?.wireValue ?: "unknown"
                    }
                    val runtime = SessionsListTool.formatDurationCompact(run.runtimeMs)
                    val displayLabel = resolveSubagentLabel(run)
                    val task = run.task.take(72).replace('\n', ' ')
                    val taskSuffix = if (task.lowercase() != displayLabel.lowercase()) " - $task" else ""
                    appendLine("${index}. $displayLabel (${run.model ?: "default"}, $runtime) $status$taskSuffix")
                    appendLine("   runId=${run.runId} session=${run.childSessionKey} endedAt=${run.endedAt}")
                    index++
                }
            }
        }.trimEnd()

        return ToolResult(
            success = true,
            content = text,
            metadata = mapOf(
                "action" to "list",
                "total" to allRuns.size,
                "active" to active.size,
                "recent" to recent.size,
            )
        )
    }

    // ==================== Kill ====================

    private fun executeKill(args: Map<String, Any?>): ToolResult {
        val target = args["target"] as? String
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter 'target' for kill action.")
        }

        // Kill all
        if (target == "all" || target == "*") {
            val allRuns = registry.listRunsForController(callerSessionKey)
            val activeRuns = allRuns.filter { it.isActive }
            if (activeRuns.isEmpty()) {
                return ToolResult(success = true, content = "No active subagent runs to kill.")
            }

            val killed = mutableListOf<String>()
            val labels = mutableListOf<String>()
            for (run in activeRuns) {
                val (success, killedIds) = spawner.kill(run.runId, cascade = true, callerSessionKey = callerSessionKey)
                if (success) {
                    killed.addAll(killedIds)
                    labels.add(run.label)
                }
            }

            return ToolResult(
                success = true,
                content = "Killed ${killed.size} subagent run(s): ${labels.joinToString(", ")}",
                metadata = mapOf(
                    "action" to "kill",
                    "target" to "all",
                    "killed" to killed.size,
                    "labels" to labels,
                )
            )
        }

        // Kill specific target
        val record = registry.resolveTarget(target, callerSessionKey)
            ?: return ToolResult(success = false, content = "No matching subagent found for target: $target")

        if (!record.isActive) {
            val status = record.outcome?.status?.wireValue ?: "unknown"
            return ToolResult(
                success = true,
                content = "Subagent '${record.label}' already finished (status: $status).",
                metadata = mapOf("action" to "kill", "status" to "done")
            )
        }

        val (success, killedIds) = spawner.kill(record.runId, cascade = true, callerSessionKey = callerSessionKey)
        if (!success) {
            return ToolResult(success = false, content = "Failed to kill subagent '${record.label}'. You may not have permission.")
        }

        val cascadeKilled = killedIds.size - 1
        return ToolResult(
            success = true,
            content = buildString {
                append("Killed subagent '${record.label}'")
                if (cascadeKilled > 0) append(" + $cascadeKilled descendant(s)")
                append(".")
            },
            metadata = mapOf(
                "action" to "kill",
                "runId" to record.runId,
                "sessionKey" to record.childSessionKey,
                "label" to record.label,
                "cascadeKilled" to cascadeKilled,
            )
        )
    }

    // ==================== Steer ====================

    private suspend fun executeSteer(args: Map<String, Any?>): ToolResult {
        val target = args["target"] as? String
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter 'target' for steer action.")
        }

        val message = args["message"] as? String
        if (message.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter 'message' for steer action.")
        }

        if (message.length > MAX_STEER_MESSAGE_CHARS) {
            return ToolResult.error("Message too long: ${message.length} > $MAX_STEER_MESSAGE_CHARS chars.")
        }

        val record = registry.resolveTarget(target, callerSessionKey)
            ?: return ToolResult(success = false, content = "No matching subagent found for target: $target")

        if (!record.isActive) {
            val status = record.outcome?.status?.wireValue ?: "unknown"
            return ToolResult(
                success = false,
                content = "Cannot steer subagent '${record.label}': already finished (status: $status)."
            )
        }

        val (success, resultMsg) = spawner.steer(
            runId = record.runId,
            message = message,
            callerSessionKey = callerSessionKey,
            parentAgentLoop = parentAgentLoop,
        )

        return if (success) {
            ToolResult(
                success = true,
                content = "Steered subagent '${record.label}': run restarted with new instructions.",
                metadata = mapOf(
                    "action" to "steer",
                    "mode" to "restart",
                    "label" to record.label,
                    "sessionKey" to record.childSessionKey,
                )
            )
        } else {
            ToolResult(success = false, content = "Failed to steer subagent '${record.label}': $resultMsg")
        }
    }
}
