package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-(all)
 *
 * AndroidForClaw adaptation: legacy Anthropic provider compatibility layer.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Anthropic Messages API Provider
 * Uses Anthropic native Messages API format
 *
 * API Documentation: https://docs.anthropic.com/en/api/messages
 */
class LegacyProviderAnthropic(
    private val apiKey: String,
    private val apiBase: String = "https://api.anthropic.com"
) {
    companion object {
        private const val TAG = "LegacyProviderAnthropic"
        private const val DEFAULT_TIMEOUT_SECONDS = 300L
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()  // 避免转义 HTML 字符
        .serializeNulls()        // 序列化 null 值
        .create()

    /**
     * 发送聊天请求 (Anthropic Messages API 格式)
     */
    suspend fun chat(
        messages: List<LegacyMessage>,
        tools: List<ToolDefinition>? = null,
        model: String = "claude-opus-4",
        maxTokens: Int = 8192,
        temperature: Double = 0.7,
        thinkingEnabled: Boolean = false,
        thinkingBudget: Int? = null
    ): LegacyResponse = withContext(Dispatchers.IO) {

        // Anthropic restriction: temperature must be 1 when Extended Thinking is enabled
        val actualTemperature = if (thinkingEnabled) 1.0 else temperature

        // Separate system message
        val systemMessage = messages.firstOrNull { it.role == "system" }?.content?.toString()
        val conversationMessages = messages.filter { it.role != "system" }

        // Build Anthropic format request
        val requestBody = AnthropicRequest(
            model = model,
            messages = conversationMessages.map { convertToAnthropicMessage(it) },
            maxTokens = maxTokens,
            temperature = actualTemperature,
            system = systemMessage,
            tools = tools?.map { convertToolToAnthropicFormat(it) },
            thinking = if (thinkingEnabled && thinkingBudget != null) {
                ThinkingConfig(budgetTokens = thinkingBudget)
            } else if (thinkingEnabled) {
                ThinkingConfig()
            } else {
                null
            }
        )

        val jsonBody = gson.toJson(requestBody)
        Log.d(TAG, "Request to $apiBase/v1/messages")
        Log.d(TAG, "Model: $model")
        Log.d(TAG, "Messages: ${conversationMessages.size}")
        Log.d(TAG, "Tools: ${tools?.size ?: 0}")
        Log.d(TAG, "Thinking enabled: $thinkingEnabled")

        val request = Request.Builder()
            .url("$apiBase/v1/messages")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw LLMException("Empty response from Anthropic API")

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: $responseBody")
                throw LLMException("HTTP ${response.code}: $responseBody")
            }

            // Parse Anthropic response
            val anthropicResponse = gson.fromJson(responseBody, AnthropicResponse::class.java)
            Log.d(TAG, "Response received: ${anthropicResponse.stopReason}")

            // 转换回 LegacyResponse 格式
            convertFromAnthropicResponse(anthropicResponse)

        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            throw LLMException("Network error: ${e.message}", cause = e)
        }
    }

    /**
     * 转换消息到 Anthropic 格式
     */
    private fun convertToAnthropicMessage(msg: LegacyMessage): AnthropicMessage {
        return when (msg.role) {
            "user" -> {
                AnthropicMessage(
                    role = "user",
                    content = msg.content?.toString() ?: ""
                )
            }
            "assistant" -> {
                if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                    // Assistant message contains tool calls
                    val contentBlocks = mutableListOf<AnthropicContentBlock>()

                    // Add text content (if any)
                    msg.content?.toString()?.let { text ->
                        if (text.isNotBlank()) {
                            contentBlocks.add(
                                AnthropicContentBlock(
                                    type = "text",
                                    text = text
                                )
                            )
                        }
                    }

                    // Add tool calls
                    msg.toolCalls.forEach { tc ->
                        contentBlocks.add(
                            AnthropicContentBlock(
                                type = "tool_use",
                                id = tc.id,
                                name = tc.function.name,
                                input = parseToolArguments(tc.function.arguments)
                            )
                        )
                    }

                    AnthropicMessage(
                        role = "assistant",
                        content = contentBlocks
                    )
                } else {
                    AnthropicMessage(
                        role = "assistant",
                        content = msg.content?.toString() ?: ""
                    )
                }
            }
            "tool" -> {
                // Tool result message - Anthropic uses "user" role + tool_result block
                AnthropicMessage(
                    role = "user",
                    content = listOf(
                        AnthropicContentBlock(
                            type = "tool_result",
                            toolUseId = msg.toolCallId ?: "",
                            content = msg.content?.toString() ?: ""
                        )
                    )
                )
            }
            else -> {
                AnthropicMessage(
                    role = "user",
                    content = msg.content?.toString() ?: ""
                )
            }
        }
    }

    /**
     * 解析工具参数 JSON 字符串为 Map
     */
    private fun parseToolArguments(jsonStr: String): Map<String, Any?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(jsonStr, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool arguments: $jsonStr", e)
            emptyMap()
        }
    }

    /**
     * 转换工具定义到 Anthropic 格式
     */
    private fun convertToolToAnthropicFormat(tool: ToolDefinition): AnthropicTool {
        // Convert ParametersSchema to InputSchema
        val params = tool.function.parameters
        val properties = params.properties.mapValues { (_, prop) ->
            PropertyDef(
                type = prop.type,
                description = prop.description ?: "",
                enum = prop.enum
            )
        }

        return AnthropicTool(
            name = tool.function.name,
            description = tool.function.description,
            inputSchema = InputSchema(
                type = "object",
                properties = properties,
                required = params.required
            )
        )
    }

    /**
     * 转换 Anthropic 响应到 LegacyResponse 格式
     */
    private fun convertFromAnthropicResponse(response: AnthropicResponse): LegacyResponse {
        // Extract text content and tool calls
        var textContent: String? = null
        val toolCalls = mutableListOf<LegacyToolCall>()
        var reasoningContent: String? = null

        response.content.forEach { block ->
            when (block.type) {
                "text" -> {
                    textContent = block.text
                }
                "tool_use" -> {
                    toolCalls.add(
                        LegacyToolCall(
                            id = block.id ?: "",
                            type = "function",
                            function = LegacyFunction(
                                name = block.name ?: "",
                                arguments = gson.toJson(block.input)
                            )
                        )
                    )
                }
                else -> {
                    // Ignore other types for now
                }
            }
        }

        val choice = LegacyChoice(
            index = 0,
            message = LegacyResponseMessage(
                role = "assistant",
                content = textContent,
                toolCalls = if (toolCalls.isNotEmpty()) toolCalls else null,
                reasoningContent = reasoningContent
            ),
            finishReason = response.stopReason ?: "stop"
        )

        return LegacyResponse(
            id = response.id,
            model = response.model,
            choices = listOf(choice),
            usage = LegacyUsage(
                promptTokens = response.usage.inputTokens,
                completionTokens = response.usage.outputTokens,
                totalTokens = response.usage.inputTokens + response.usage.outputTokens
            )
        )
    }

    /**
     * 简化的聊天方法
     */
    suspend fun simpleChat(
        userMessage: String,
        systemPrompt: String? = null
    ): String {
        val messages = mutableListOf<LegacyMessage>()

        if (systemPrompt != null) {
            messages.add(LegacyMessage("system", systemPrompt))
        }

        messages.add(LegacyMessage("user", userMessage))

        val response = chat(messages = messages)

        return response.choices.firstOrNull()?.message?.content
            ?: "No response from model"
    }
}

// Data models defined in AnthropicModels.kt
