package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/bash-tools.ts
 */

import android.content.Context
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.termux.EmbeddedTermuxRuntime
import java.io.File

/**
 * Single `exec` tool entry.
 *
 * Routing: auto-selects the best available shell backend.
 * Implementation details (embedded runtime vs Android shell) are invisible to the agent.
 */
class ExecFacadeTool(
    context: Context,
    workingDir: String? = null
) : Tool {

    private val internalExec: Tool = ExecTool(workingDir = workingDir)
    private val defaultWorkingDir: File? = workingDir?.let { File(it) }

    override val name: String = "exec"
    override val description: String =
        "Run shell commands on this device. Supports bash, coreutils, grep, find, sed, awk, Python, and other common Linux tools."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "command" to PropertySchema("string", "Shell command to execute"),
                        "working_dir" to PropertySchema("string", "Optional working directory"),
                        "timeout" to PropertySchema("integer", "Timeout in milliseconds (default 120000)"),
                    ),
                    required = listOf("command")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.error("Missing required parameter: command")
        val timeout = (args["timeout"] as? Number)?.toLong() ?: 120_000

        // Auto: prefer embedded runtime when ready
        return if (EmbeddedTermuxRuntime.isReady()) {
            executeEmbedded(command, timeout, args)
        } else {
            internalExec.execute(args)
        }
    }

    private suspend fun executeEmbedded(command: String, timeout: Long, args: Map<String, Any?>): ToolResult {
        val workDir = (args["working_dir"] as? String)?.let { File(it) } ?: defaultWorkingDir

        val result = EmbeddedTermuxRuntime.exec(command, timeout, workingDir = workDir)

        if (result.timedOut) {
            return ToolResult.error("Command timed out after ${timeout / 1000}s")
        }

        val rendered = buildString {
            append(result.output)
            if (result.exitCode != 0) {
                if (isNotEmpty()) append("\n")
                append("Exit code: ${result.exitCode}")
            }
        }

        val metadata = mapOf(
            "exitCode" to result.exitCode,
            "command" to command
        )

        return if (result.output.startsWith("Execution failed:") || result.output.startsWith("Runtime not")) {
            ToolResult.error(rendered)
        } else {
            ToolResult.success(rendered, metadata)
        }
    }
}
