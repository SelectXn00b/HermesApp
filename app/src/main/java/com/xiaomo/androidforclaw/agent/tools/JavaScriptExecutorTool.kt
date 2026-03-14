package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.quickjs.QuickJSExecutor

/**
 * JavaScript Executor Tool - Based on QuickJS Module
 *
 * Provides complete JavaScript runtime environment with support for:
 * - ES6+ syntax (const, let, arrow functions, etc.)
 * - async/await asynchronous programming
 * - Promise
 * - Built-in utility library (lodash-like)
 * - Android bridge (file, HTTP, system calls)
 *
 * Use cases:
 * - Data processing and analysis (JSON, CSV, etc.)
 * - String manipulation and text processing
 * - Array and object operations
 * - Simple network requests
 * - File read/write
 */
class JavaScriptExecutorTool(private val context: Context) : Tool {
    override val name = "javascript_exec"
    override val description = "Execute JavaScript code with QuickJS engine (ES6+, async/await, Node.js-like APIs)"

    private val executor = QuickJSExecutor(context)

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "code" to PropertySchema("string", "JavaScript code to execute (ES6+, async/await supported)"),
                        "timeout" to PropertySchema("number", "Execution timeout in milliseconds (default: 30000)")
                    ),
                    required = listOf("code")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val code = args["code"] as? String
        if (code.isNullOrBlank()) {
            return ToolResult.error("Missing or empty 'code' parameter")
        }

        val timeout = (args["timeout"] as? Number)?.toLong() ?: 30000L

        val result = executor.execute(code, timeout)

        return if (result.success) {
            ToolResult.success(
                content = result.result ?: "undefined",
                metadata = result.metadata
            )
        } else {
            ToolResult.error(result.error ?: "Unknown error")
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        executor.cleanup()
    }
}
