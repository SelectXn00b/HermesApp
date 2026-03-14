package com.xiaomo.androidforclaw.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


/**
 * Skill 接口 (Self-Control Module 内部副本)
 *
 * 为了避免循环依赖，这里复制了 app module 中的 Skill 接口。
 * 在 app module 集成时，这些类会被替换为 app module 的实际类型。
 */

/**
 * Tool Definition (简化版本，兼容 OpenAI function calling)
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
    val enum: List<String>? = null
)

/**
 * Skill 接口
 */
interface Skill {
    val name: String
    val description: String

    fun getToolDefinition(): ToolDefinition
    suspend fun execute(args: Map<String, Any?>): SkillResult
}

/**
 * Skill 执行结果
 */
data class SkillResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any?> = emptyMap()) =
            SkillResult(true, content, metadata)

        fun error(message: String) =
            SkillResult(false, "Error: $message")
    }

    override fun toString(): String = content
}
