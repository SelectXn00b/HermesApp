package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: single exec entry with backend routing.
 */

import android.content.Context
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Single `exec` tool entry.
 *
 * Routing policy:
 * - backend=termux -> force Termux
 * - backend=internal -> force internal ExecTool
 * - backend=auto / omitted -> prefer Termux when available, otherwise fallback internal
 */
class ExecFacadeTool private constructor(
    private val internalExec: Tool,
    private val termuxExec: Tool,
    private val termuxAvailable: () -> Boolean
) : Tool {

    constructor(context: Context, workingDir: String? = null) : this(
        internalExec = ExecTool(workingDir = workingDir),
        termuxExec = TermuxBridgeTool(context),
        termuxAvailable = {
            val termux = TermuxBridgeTool(context)
            termux.isAvailable()
        }
    )

    internal constructor(
        internalExec: Tool,
        termuxExec: Tool,
        termuxAvailable: Boolean
    ) : this(internalExec, termuxExec, { termuxAvailable })

    override val name: String = "exec"
    override val description: String = "Run shell commands. Prefer Termux when available; fallback to internal Android exec."

    override fun getToolDefinition(): ToolDefinition {
        val base = termuxExec.getToolDefinition()
        return ToolDefinition(
            type = base.type,
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = base.function.parameters.type,
                    properties = base.function.parameters.properties + mapOf(
                        "backend" to PropertySchema(
                            type = "string",
                            description = "Execution backend: auto | termux | internal",
                            enum = listOf("auto", "termux", "internal")
                        )
                    ),
                    required = base.function.parameters.required
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val backend = (args["backend"] as? String)?.lowercase() ?: "auto"
        return when (backend) {
            "termux" -> termuxExec.execute(args)
            "internal" -> internalExec.execute(args)
            else -> if (termuxAvailable()) termuxExec.execute(args) else internalExec.execute(args)
        }
    }
}
