package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: single exec entry with backend routing.
 */

import android.content.Context

/**
 * Single `exec` tool entry.
 *
 * Routing policy:
 * - backend=termux -> force Termux
 * - backend=internal -> force internal ExecTool
 * - backend=auto / omitted -> prefer Termux when available, otherwise fallback internal
 */
class ExecFacadeTool(
    context: Context,
    workingDir: String? = null
) : Tool {
    private val internalExec = ExecTool(workingDir = workingDir)
    private val termuxExec = TermuxBridgeTool(context)

    override val name: String = "exec"
    override val description: String = "Run shell commands. Prefer Termux when available; fallback to internal Android exec."

    override fun getToolDefinition() = termuxExec.getToolDefinition().copy(
        function = termuxExec.getToolDefinition().function.copy(
            description = description,
            parameters = termuxExec.getToolDefinition().function.parameters.copy(
                properties = termuxExec.getToolDefinition().function.parameters.properties + mapOf(
                    "backend" to com.xiaomo.androidforclaw.providers.PropertySchema(
                        type = "string",
                        description = "Execution backend: auto | termux | internal",
                        enum = listOf("auto", "termux", "internal")
                    )
                )
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val backend = (args["backend"] as? String)?.lowercase() ?: "auto"
        return when (backend) {
            "termux" -> termuxExec.execute(args)
            "internal" -> internalExec.execute(args)
            else -> if (termuxExec.isAvailable()) termuxExec.execute(args) else internalExec.execute(args)
        }
    }
}
