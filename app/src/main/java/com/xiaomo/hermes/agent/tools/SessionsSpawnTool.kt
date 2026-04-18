/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/sessions-spawn-tool.ts
 *
 * Hermes adaptation: LLM-facing tool to spawn subagent sessions.
 */
package com.xiaomo.hermes.agent.tools

import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.agent.subagent.InlineAttachment
import com.xiaomo.hermes.agent.subagent.SPAWN_ACCEPTED_NOTE
import com.xiaomo.hermes.agent.subagent.SPAWN_SESSION_ACCEPTED_NOTE
import com.xiaomo.hermes.agent.subagent.SpawnMode
import com.xiaomo.hermes.agent.subagent.SpawnStatus
import com.xiaomo.hermes.agent.subagent.SpawnSubagentParams
import com.xiaomo.hermes.agent.subagent.SubagentSpawner
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * sessions_spawn — Spawn an isolated subagent to handle a task.
 * Aligned with OpenClaw createSessionsSpawnTool.
 */
class SessionsSpawnTool(
    private val spawner: SubagentSpawner,
    private val parentSessionKey: String,
    private val parentAgentLoop: AgentLoopInterface,
    private val parentDepth: Int,
) : Tool {
    companion object {
        private const val TAG = "SessionsSpawnTool"
    }

    override val name = "sessions_spawn"
    override val description = "Spawn an isolated subagent session to handle a specific task in parallel. " +
        "The subagent runs independently with its own context and tools, and automatically reports " +
        "results back when complete. Use this to parallelize independent tasks."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "task" to PropertySchema(
                            type = "string",
                            description = "The task description for the subagent to execute."
                        ),
                        "label" to PropertySchema(
                            type = "string",
                            description = "Short display label for this subagent (e.g. 'research-api', 'analyze-logs')."
                        ),
                        "model" to PropertySchema(
                            type = "string",
                            description = "Model override for this subagent (format: 'provider/model-id'). Defaults to parent model."
                        ),
                        "timeout_seconds" to PropertySchema(
                            type = "number",
                            description = "Run timeout in seconds. Default: 300."
                        ),
                        "mode" to PropertySchema(
                            type = "string",
                            description = "Spawn mode: 'run' (one-shot, default) or 'session' (persistent).",
                            enum = listOf("run", "session")
                        ),
                        "thinking" to PropertySchema(
                            type = "string",
                            description = "Thinking/reasoning level: 'none', 'brief', 'verbose'. Default: inherit from parent."
                        ),
                        "runtime" to PropertySchema(
                            type = "string",
                            description = "Runtime: 'subagent' (default) or 'acp' (not supported on Android)."
                        ),
                        "thread" to PropertySchema(
                            type = "boolean",
                            description = "If true, spawn as thread-bound session."
                        ),
                        "cleanup" to PropertySchema(
                            type = "string",
                            description = "Cleanup strategy after completion. Default: 'keep'.",
                            enum = listOf("delete", "keep")
                        ),
                        "sandbox" to PropertySchema(
                            type = "string",
                            description = "Sandbox mode. Default: 'inherit'.",
                            enum = listOf("inherit", "require")
                        ),
                        "attachments" to PropertySchema(
                            type = "array",
                            description = "Inline file attachments for the subagent (max 50 items)."
                        ),
                        "cwd" to PropertySchema(
                            type = "string",
                            description = "Working directory for the subagent."
                        ),
                        "resume_session_id" to PropertySchema(
                            type = "string",
                            description = "Resume an existing session (not yet supported)."
                        ),
                        "stream_to" to PropertySchema(
                            type = "string",
                            description = "Stream output to parent (ACP only, not supported)."
                        ),
                    ),
                    required = listOf("task")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val task = args["task"] as? String
        if (task.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: task")
        }

        val label = (args["label"] as? String)?.trim()?.ifBlank { null }
            ?: task.take(40).replace('\n', ' ')
        val model = args["model"] as? String
        val timeoutSeconds = (args["timeout_seconds"] as? Number)?.toInt()
        val modeStr = args["mode"] as? String
        val mode = when (modeStr?.lowercase()) {
            "session" -> SpawnMode.SESSION
            else -> SpawnMode.RUN
        }
        val thinking = args["thinking"] as? String

        // New parameters aligned with OpenClaw
        val runtime = args["runtime"] as? String
        val thread = args["thread"] as? Boolean
        val cleanup = (args["cleanup"] as? String)?.lowercase() ?: "keep"
        val sandbox = args["sandbox"] as? String
        val cwd = args["cwd"] as? String

        // Parse inline attachments if present
        val attachments = (args["attachments"] as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val content = map["content"] as? String ?: return@mapNotNull null
            val encoding = map["encoding"] as? String ?: "utf8"
            val mimeType = map["mime_type"] as? String ?: map["mimeType"] as? String
            InlineAttachment(
                name = name,
                content = content,
                encoding = encoding,
                mimeType = mimeType,
            )
        }?.takeIf { it.isNotEmpty() }

        // Parse attachAs.mountPath if present
        val attachMountPath = (args["attachAs"] as? Map<*, *>)?.get("mountPath") as? String

        // Warn about unsupported parameters
        val resumeSessionId = args["resume_session_id"] as? String
        if (resumeSessionId != null) {
            Log.w(TAG, "resume_session_id is not yet supported, ignoring: $resumeSessionId")
        }
        val streamTo = args["stream_to"] as? String
        if (streamTo != null) {
            Log.w(TAG, "stream_to is not supported on Android, ignoring: $streamTo")
        }

        Log.i(TAG, "Spawning subagent: label=$label, model=$model, timeout=$timeoutSeconds, mode=$mode, runtime=$runtime, thread=$thread, cleanup=$cleanup, sandbox=$sandbox")

        val params = SpawnSubagentParams(
            task = task,
            label = label,
            model = model,
            thinking = thinking,
            runTimeoutSeconds = timeoutSeconds,
            mode = mode,
            runtime = runtime,
            thread = thread,
            cleanup = cleanup,
            sandbox = sandbox,
            attachments = attachments,
            attachMountPath = attachMountPath,
            cwd = cwd,
        )

        val result = spawner.spawn(params, parentSessionKey, parentAgentLoop, parentDepth)

        return when (result.status) {
            SpawnStatus.ACCEPTED -> ToolResult(
                success = true,
                content = buildString {
                    appendLine("Subagent spawned successfully.")
                    appendLine("Run ID: ${result.runId}")
                    appendLine("Session: ${result.childSessionKey}")
                    appendLine("Mode: ${result.mode?.wireValue ?: "run"}")
                    if (result.modelApplied != null) {
                        appendLine("Model: ${result.modelApplied}")
                    }
                    appendLine()
                    if (mode == SpawnMode.SESSION) {
                        appendLine(SPAWN_SESSION_ACCEPTED_NOTE)
                    } else {
                        appendLine(SPAWN_ACCEPTED_NOTE)
                    }
                },
                metadata = mapOf(
                    "status" to "accepted",
                    "run_id" to (result.runId ?: ""),
                    "child_session_key" to (result.childSessionKey ?: ""),
                )
            )

            SpawnStatus.FORBIDDEN -> ToolResult(
                success = false,
                content = "Spawn forbidden: ${result.note ?: result.error ?: "unknown reason"}",
                metadata = mapOf("status" to "forbidden")
            )

            SpawnStatus.ERROR -> ToolResult(
                success = false,
                content = "Spawn error: ${result.error ?: "unknown error"}",
                metadata = mapOf("status" to "error")
            )
        }
    }
}
