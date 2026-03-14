package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.util.Log

/**
 * Adapter: Bridges Feishu extension tools into the main ToolRegistry
 *
 * Problem: FeishuToolBase (extensions/feishu) and Tool (app) have similar interfaces
 * but different type systems (different ToolDefinition, ToolResult classes).
 * This adapter converts between them so feishu tools (doc, wiki, drive, bitable, etc.)
 * are available to the AgentLoop.
 *
 * Aligned with OpenClaw: extension tools are automatically registered when the channel starts.
 */
class FeishuToolAdapter(
    private val feishuTool: com.xiaomo.feishu.tools.FeishuToolBase
) : Tool {

    companion object {
        private const val TAG = "FeishuToolAdapter"
    }

    override val name: String = feishuTool.name

    override val description: String = feishuTool.description

    override fun getToolDefinition(): com.xiaomo.androidforclaw.providers.ToolDefinition {
        val feishuDef = feishuTool.getToolDefinition()
        return com.xiaomo.androidforclaw.providers.ToolDefinition(
            type = feishuDef.type,
            function = com.xiaomo.androidforclaw.providers.FunctionDefinition(
                name = feishuDef.function.name,
                description = feishuDef.function.description,
                parameters = convertParametersSchema(feishuDef.function.parameters)
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        return try {
            val feishuResult = feishuTool.execute(args)

            if (feishuResult.success) {
                SkillResult.success(
                    content = feishuResult.data?.toString() ?: "OK",
                    metadata = feishuResult.metadata.mapValues { it.value as Any? }
                )
            } else {
                SkillResult.error(
                    message = feishuResult.error ?: "Unknown error"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feishu tool execution failed: $name", e)
            SkillResult.error("Feishu tool error: ${e.message}")
        }
    }

    private fun convertParametersSchema(
        feishuSchema: com.xiaomo.feishu.tools.ParametersSchema
    ): com.xiaomo.androidforclaw.providers.ParametersSchema {
        return com.xiaomo.androidforclaw.providers.ParametersSchema(
            type = feishuSchema.type,
            properties = feishuSchema.properties.mapValues { (_, prop) ->
                convertPropertySchema(prop)
            },
            required = feishuSchema.required
        )
    }

    private fun convertPropertySchema(
        prop: com.xiaomo.feishu.tools.PropertySchema
    ): com.xiaomo.androidforclaw.providers.PropertySchema {
        return com.xiaomo.androidforclaw.providers.PropertySchema(
            type = prop.type,
            description = prop.description,
            enum = prop.enum,
            items = prop.items?.let { convertPropertySchema(it) },
            properties = prop.properties?.mapValues { (_, child) -> convertPropertySchema(child) }
        )
    }
}

/**
 * Register all enabled feishu tools into a ToolRegistry
 *
 * @param registry The main ToolRegistry to register into
 * @param feishuToolRegistry The feishu extension's tool registry
 * @return Number of tools registered
 */
fun registerFeishuTools(
    registry: ToolRegistry,
    feishuToolRegistry: com.xiaomo.feishu.tools.FeishuToolRegistry
): Int {
    var count = 0
    for (tool in feishuToolRegistry.getAllTools()) {
        if (tool.isEnabled()) {
            val adapter = FeishuToolAdapter(tool)
            registry.register(adapter)
            count++
        }
    }
    Log.i("FeishuToolAdapter", "✅ Registered $count feishu tools into ToolRegistry")
    return count
}
