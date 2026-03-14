package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-(all)
 *
 * AndroidForClaw adaptation: provider dispatch and compatibility.
 */


import com.google.gson.annotations.SerializedName

/**
 * Legacy LLM API 数据模型
 * Supports Claude Opus 4.6 with Extended Thinking
 */

// ============= Request Models =============

data class LegacyMessage(
    val role: String,  // "system", "user", "assistant", "tool"
    val content: Any?,  // String or List<ContentBlock>
    val name: String? = null,  // tool name for tool role
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,  // for tool role
    @SerializedName("tool_calls")
    val toolCalls: List<LegacyToolCall>? = null  // for assistant with tool calls
)

data class ContentBlock(
    val type: String,  // "text", "image_url"
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String  // base64 data URL
)

data class LegacyToolCall(
    val id: String,
    val type: String = "function",
    val function: LegacyFunction
)

data class LegacyFunction(
    val name: String,
    val arguments: String  // JSON string
)

data class LegacyRequest(
    val model: String = "ppio/pa/claude-opus-4-6",
    val messages: List<LegacyMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val temperature: Double = 0.1,
    val thinking: LegacyThinking? = null,  // Extended Thinking support
    val stream: Boolean = false
)

data class LegacyThinking(
    val type: String = "enabled",  // "enabled" or "disabled"
    @SerializedName("budget_tokens")
    val budgetTokens: Int = 10000  // Max tokens for reasoning
)

// ============= Tool Definition Models =============

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
    val type: String,  // "string", "number", "boolean", "array", "object"
    val description: String,
    val enum: List<String>? = null,
    val items: PropertySchema? = null,  // for array type
    val properties: Map<String, PropertySchema>? = null  // for object type
)

// ============= Response Models =============

data class LegacyResponse(
    val id: String,
    val model: String,
    val choices: List<LegacyChoice>,
    val usage: LegacyUsage? = null,
    val created: Long? = null,
    @SerializedName("object")
    val objectType: String = "chat.completion"
)

data class LegacyChoice(
    val index: Int = 0,
    val message: LegacyResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String  // "stop", "tool_calls", "length", "error"
)

data class LegacyResponseMessage(
    val role: String,
    val content: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<LegacyToolCall>? = null,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null  // Extended Thinking 思考过程
)

data class LegacyUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0,
    @SerializedName("reasoning_tokens")
    val reasoningTokens: Int? = null  // Reasoning 消耗的 token 数
)

// ============= Error Models =============

data class LegacyError(
    val error: ErrorDetail
)

data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
