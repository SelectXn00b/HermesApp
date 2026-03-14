package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: explicit config write tool.
 */


import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Write value to /sdcard/.androidforclaw/openclaw.json by path.
 */
class ConfigSetTool(
    private val configMethods: ConfigMethods
) : Tool {
    override val name = "config_set"
    override val description = "Set a configuration value in openclaw.json by dot path"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "path" to PropertySchema("string", "Dot path, e.g. channels.feishu.enabled"),
                        "value" to PropertySchema("string", "Value to write; strings like true/false are also accepted")
                    ),
                    required = listOf("path", "value")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val path = args["path"] as? String
            ?: return ToolResult.error("Missing required parameter: path")
        val rawValue = args["value"]
            ?: return ToolResult.error("Missing required parameter: value")

        val value: Any? = when (rawValue) {
            is String -> when (rawValue.lowercase()) {
                "true" -> true
                "false" -> false
                else -> rawValue
            }
            else -> rawValue
        }

        val result = configMethods.configSet(mapOf("path" to path, "value" to value))
        return if (result.success) {
            ToolResult.success(result.message)
        } else {
            ToolResult.error(result.message)
        }
    }
}
