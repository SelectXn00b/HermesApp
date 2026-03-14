package com.xiaomo.feishu.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig

/**
 * 飞书工具基类
 * 所有飞书工具的通用接口
 */
abstract class FeishuToolBase(
    protected val config: FeishuConfig,
    protected val client: FeishuClient
) {
    /**
     * 工具名称
     */
    abstract val name: String

    /**
     * 工具描述
     */
    abstract val description: String

    /**
     * 工具是否启用
     */
    abstract fun isEnabled(): Boolean

    /**
     * 执行工具
     */
    abstract suspend fun execute(args: Map<String, Any?>): ToolResult

    /**
     * 获取工具定义（用于 LLM）
     */
    abstract fun getToolDefinition(): ToolDefinition
}

/**
 * 工具执行结果
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(data: Any? = null, metadata: Map<String, Any> = emptyMap()) =
            ToolResult(true, data, null, metadata)

        fun error(error: String, metadata: Map<String, Any> = emptyMap()) =
            ToolResult(false, null, error, metadata)
    }
}

/**
 * 工具定义（用于 LLM）
 */
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: ParametersSchema
)

data class ParametersSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList()
)

data class PropertySchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val items: PropertySchema? = null,
    val properties: Map<String, PropertySchema>? = null
)
