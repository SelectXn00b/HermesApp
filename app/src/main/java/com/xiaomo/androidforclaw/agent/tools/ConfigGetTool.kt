package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: explicit config read tool.
 */


import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Read value from /sdcard/.androidforclaw/openclaw.json by path.
 */
class ConfigGetTool(
    private val configMethods: ConfigMethods
) : Tool {
    override val name = "config_get"
    override val description = "Read a configuration value from openclaw.json by dot path"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "Dot path, e.g. channels.feishu.appId")
                    ),
                    required = listOf("path")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String
            ?: return ToolResult.error("Missing required parameter: path")

        val result = configMethods.configGet(mapOf("path" to path))
        return if (result.success) {
            ToolResult.success(result.config?.toString() ?: "null")
        } else {
            ToolResult.error(result.error ?: "Failed to read config")
        }
    }
}
