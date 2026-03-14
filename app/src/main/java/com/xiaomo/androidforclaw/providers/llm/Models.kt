package com.xiaomo.androidforclaw.providers.llm

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-(all)
 *
 * AndroidForClaw adaptation: LLM request/response model definitions.
 */


import com.google.gson.annotations.SerializedName

/**
 * LLM 通用数据模型
 * Used to unify interfaces of different API providers
 *
 * Reference: OpenClaw src/agents/llm-types.ts
 */

// ============= Message Models =============

/**
 * 通用消息格式
 */
data class Message(
    val role: String,  // "system", "user", "assistant", "tool"
    val content: String,
    val name: String? = null,  // tool name for tool role
    val toolCallId: String? = null,  // for tool role
    val toolCalls: List<ToolCall>? = null  // for assistant with tool calls
)

/**
 * Tool Call（工具调用）
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String  // JSON string
)

// ============= Tool Definition Models =============

/**
 * 工具定义
 */
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
) {
    override fun toString(): String {
        return """{"type":"$type","function":${function}}"""
    }
}

/**
 * 函数定义
 */
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: ParametersSchema
) {
    override fun toString(): String {
        return """{"name":"$name","description":"$description","parameters":${parameters}}"""
    }
}

/**
 * 参数 Schema
 */
data class ParametersSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList()
) {
    override fun toString(): String {
        val props = properties.entries.joinToString(",") { (key, value) ->
            """"$key":${value}"""
        }
        val req = required.joinToString(",") { """"$it"""" }
        return """{"type":"$type","properties":{$props},"required":[$req]}"""
    }
}

/**
 * 属性 Schema
 */
data class PropertySchema(
    val type: String,  // "string", "number", "boolean", "array", "object"
    val description: String,
    val enum: List<String>? = null,
    val items: PropertySchema? = null,  // for array type
    val properties: Map<String, PropertySchema>? = null  // for object type
) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        parts.add(""""type":"$type"""")
        parts.add(""""description":"$description"""")
        enum?.let {
            val enumStr = it.joinToString(",") { v -> """"$v"""" }
            parts.add(""""enum":[$enumStr]""")
        }
        items?.let {
            parts.add(""""items":${it}""")
        }
        properties?.let { props ->
            val propsStr = props.entries.joinToString(",") { (k, v) ->
                """"$k":${v}"""
            }
            parts.add(""""properties":{$propsStr}""")
        }
        return "{${parts.joinToString(",")}}"
    }
}

// ============= Response Models =============

/**
 * LLM 响应（通用格式）
 */
data class LLMResponse(
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val thinkingContent: String? = null,  // Extended Thinking content
    val usage: TokenUsage? = null,
    val finishReason: String? = null
)

/**
 * Token 使用统计
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// ============= Helper Extensions =============

/**
 * 将 Message 转换为适用于日志的简短描述
 */
fun Message.toLogString(): String {
    val preview = content.take(50) + if (content.length > 50) "..." else ""
    return "Message(role=$role, content=\"$preview\", toolCalls=${toolCalls?.size ?: 0})"
}

/**
 * 创建系统消息
 */
fun systemMessage(content: String) = Message(
    role = "system",
    content = content
)

/**
 * 创建用户消息
 */
fun userMessage(content: String) = Message(
    role = "user",
    content = content
)

/**
 * 创建助手消息
 */
fun assistantMessage(
    content: String? = null,
    toolCalls: List<ToolCall>? = null
) = Message(
    role = "assistant",
    content = content ?: "",
    toolCalls = toolCalls
)

/**
 * 创建工具结果消息
 */
fun toolMessage(
    toolCallId: String,
    content: String,
    name: String? = null
) = Message(
    role = "tool",
    content = content,
    toolCallId = toolCallId,
    name = name
)

// ============= Compatibility Extensions =============

/**
 * 从旧的 LegacyMessage 转换到新的 Message
 */
fun com.xiaomo.androidforclaw.providers.LegacyMessage.toNewMessage(): Message {
    return Message(
        role = this.role,
        content = when (val c = this.content) {
            is String -> c
            else -> c.toString()
        },
        name = this.name,
        toolCallId = this.toolCallId,
        toolCalls = this.toolCalls?.map { tc ->
            ToolCall(
                id = tc.id,
                name = tc.function.name,
                arguments = tc.function.arguments
            )
        }
    )
}

/**
 * 从新的 Message 转换到旧的 LegacyMessage
 */
fun Message.toLegacyMessage(): com.xiaomo.androidforclaw.providers.LegacyMessage {
    return com.xiaomo.androidforclaw.providers.LegacyMessage(
        role = this.role,
        content = this.content,
        name = this.name,
        toolCallId = this.toolCallId,
        toolCalls = this.toolCalls?.map { tc ->
            com.xiaomo.androidforclaw.providers.LegacyToolCall(
                id = tc.id,
                type = "function",
                function = com.xiaomo.androidforclaw.providers.LegacyFunction(
                    name = tc.name,
                    arguments = tc.arguments
                )
            )
        }
    )
}
