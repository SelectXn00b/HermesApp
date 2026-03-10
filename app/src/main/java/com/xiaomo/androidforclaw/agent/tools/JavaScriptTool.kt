package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.quickjs.QuickJSExecutor

/**
 * JavaScript Execution Tool
 *
 * Provides QuickJS JavaScript runtime for data processing and computation.
 *
 * Capabilities:
 * - ES6+ syntax (const, let, arrow functions, map, filter, reduce, etc.)
 * - JSON manipulation
 * - String/Array/Object operations
 * - Math calculations
 * - Data transformation
 */
class JavaScriptTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "JavaScriptTool"
    }

    private val executor = QuickJSExecutor(context)

    override val name = "javascript"
    override val description = "Execute JavaScript code for data processing"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "code" to PropertySchema(
                            "string",
                            "JavaScript code to execute. The last expression value will be returned."
                        )
                    ),
                    required = listOf("code")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val code = args["code"] as? String

        if (code.isNullOrBlank()) {
            return ToolResult.error("JavaScript code is required")
        }

        return try {
            Log.d(TAG, "Executing JavaScript: ${code.take(100)}...")

            val result = executor.execute(code)

            if (result.success) {
                Log.d(TAG, "JavaScript executed successfully")
                ToolResult.success(
                    result.result ?: "undefined",
                    mapOf(
                        "executionTime" to result.metadata["executionTime"],
                        "codeLength" to result.metadata["codeLength"]
                    )
                )
            } else {
                Log.e(TAG, "JavaScript execution failed: ${result.error}")
                ToolResult.error(result.error ?: "Unknown error")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute JavaScript", e)
            ToolResult.error("Execution failed: ${e.message}")
        }
    }

    fun cleanup() {
        executor.cleanup()
    }
}
