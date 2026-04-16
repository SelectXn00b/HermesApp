package com.xiaomo.androidforclaw.hermes

import android.util.Log
import com.xiaomo.androidforclaw.agent.tools.ToolCallDispatcher
import com.xiaomo.androidforclaw.providers.LLMResponse
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.llm.userMessage
import com.xiaomo.androidforclaw.providers.llm.assistantMessage
import com.xiaomo.androidforclaw.providers.llm.toolMessage
import com.xiaomo.androidforclaw.providers.llm.systemMessage

/**
 * Adapter: app's UnifiedLLMProvider → hermes ChatCompletionServer interface.
 *
 * Converts between app's message/tool types and hermes's OpenAI-format maps,
 * enabling [HermesAgentLoop] to drive the conversation using app's LLM infrastructure.
 */
class AppChatCompletionAdapter(
    private val llmProvider: UnifiedLLMProvider,
) : ChatCompletionServer {

    companion object {
        private const val TAG = "AppCCAdapter"
    }

    override suspend fun chatCompletion(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>?,
        temperature: Double,
        maxTokens: Int?,
        extraBody: Map<String, Any?>?,
    ): ChatCompletionResponse? {
        // Convert OpenAI-format message maps → app Message objects
        val appMessages = messages.mapNotNull { msgMap ->
            val role = msgMap["role"] as? String ?: return@mapNotNull null
            val content = msgMap["content"] as? String ?: ""
            val name = msgMap["name"] as? String
            val toolCallId = msgMap["tool_call_id"] as? String

            // Parse tool_calls for assistant messages → app's LLM ToolCall
            val toolCalls = (msgMap["tool_calls"] as? List<*>)?.mapNotNull { tc ->
                tc as? Map<*, *> ?: return@mapNotNull null
                val id = tc["id"] as? String ?: ""
                val func = tc["function"] as? Map<*, *>
                val fnName = func?.get("name") as? String ?: ""
                val fnArgs = func?.get("arguments") as? String ?: "{}"
                com.xiaomo.androidforclaw.providers.llm.ToolCall(id = id, name = fnName, arguments = fnArgs)
            }

            when (role) {
                "system" -> systemMessage(content)
                "user" -> userMessage(content)
                "assistant" -> assistantMessage(content = content, toolCalls = toolCalls)
                "tool" -> toolMessage(toolCallId = toolCallId ?: "", content = content, name = name)
                else -> {
                    Log.w(TAG, "Unknown role: $role, skipping")
                    null
                }
            }
        }

        // Convert OpenAI-format tool maps → app ToolDefinition objects
        val appTools = tools?.mapNotNull { toolMap ->
            val type = toolMap["type"] as? String ?: "function"
            val func = toolMap["function"] as? Map<*, *> ?: return@mapNotNull null
            val funcName = func["name"] as? String ?: return@mapNotNull null
            val funcDesc = func["description"] as? String ?: ""
            val params = func["parameters"] as? Map<*, *>
            val paramProps = params?.get("properties") as? Map<String, Any?> ?: emptyMap()
            val paramRequired = (params?.get("required") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            // Convert property schemas
            val properties = paramProps.mapValues { (_, v) ->
                val propMap = v as? Map<String, Any?> ?: emptyMap()
                PropertySchema(
                    type = propMap["type"] as? String ?: "string",
                    description = propMap["description"] as? String ?: "",
                    enum = (propMap["enum"] as? List<*>)?.filterIsInstance<String>(),
                )
            }

            ToolDefinition(
                type = type,
                function = FunctionDefinition(
                    name = funcName,
                    description = funcDesc,
                    parameters = ParametersSchema(
                        type = params?.get("type") as? String ?: "object",
                        properties = properties,
                        required = paramRequired,
                    )
                )
            )
        }

        // Call app's LLM provider
        val reasoningEnabled = extraBody?.get("reasoning") != null ||
            extraBody?.get("reasoning_enabled") == true

        val response = try {
            llmProvider.chatWithTools(
                messages = appMessages,
                tools = appTools,
                temperature = temperature,
                maxTokens = maxTokens,
                reasoningEnabled = reasoningEnabled,
            )
        } catch (e: Exception) {
            Log.e(TAG, "LLM call failed: ${e.message}", e)
            return null
        }

        // Convert app LLMResponse → hermes ChatCompletionResponse
        return toChatCompletionResponse(response)
    }

    private fun toChatCompletionResponse(response: LLMResponse): ChatCompletionResponse {
        // Convert app's ToolCall → hermes's ToolCall (different structure: app has name/arguments directly,
        // hermes wraps them in ToolCallFunction)
        val hermesToolCalls = response.toolCalls?.map { tc ->
            ToolCall(
                id = tc.id,
                function = ToolCallFunction(name = tc.name, arguments = tc.arguments),
            )
        }

        val assistantMsg = AssistantMessage(
            content = response.content,
            toolCalls = hermesToolCalls,
            reasoningContent = response.thinkingContent,
        )

        return ChatCompletionResponse(
            choices = listOf(Choice(message = assistantMsg))
        )
    }
}

/**
 * Adapter: app's ToolCallDispatcher → hermes ToolDispatcher interface.
 *
 * Routes tool execution through app's unified dispatcher, which resolves
 * to universal tools (file, exec, web), Android tools (tap, screenshot),
 * or extra tools (subagent, sessions).
 */
class AppToolDispatcherAdapter(
    private val dispatcher: ToolCallDispatcher,
) : ToolDispatcher {

    companion object {
        private const val TAG = "AppToolDispatcher"
    }

    override suspend fun dispatch(
        toolName: String,
        args: Map<String, Any?>,
        taskId: String,
        userTask: String?,
    ): String {
        return try {
            val result = dispatcher.execute(toolName, args)
            result.content ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Tool '$toolName' failed: ${e.message}", e)
            """{"error":"${e.message?.replace("\"", "\\\"") ?: "Tool execution failed"}"}"""
        }
    }
}

/**
 * Bridge utilities for wiring app's agent infrastructure into hermes.
 */
object HermesBridge {

    /**
     * Convert app's Message list → OpenAI-format message maps for hermes.
     */
    fun convertMessagesToHermes(messages: List<com.xiaomo.androidforclaw.providers.llm.Message>): MutableList<Map<String, Any?>> {
        return messages.map { msg ->
            val map = mutableMapOf<String, Any?>("role" to msg.role, "content" to msg.content)
            if (msg.toolCallId != null) map["tool_call_id"] = msg.toolCallId
            if (msg.toolCalls != null) {
                map["tool_calls"] = msg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to "function",
                        "function" to mapOf("name" to tc.name, "arguments" to tc.arguments)
                    )
                }
            }
            map
        }.toMutableList()
    }

    /**
     * Convert app's tool definitions to OpenAI-format maps for hermes toolSchemas.
     */
    fun convertToolDefsToHermes(
        definitions: List<com.xiaomo.androidforclaw.providers.ToolDefinition>,
    ): List<Map<String, Any?>> {
        return definitions.map { def ->
            val props = mutableMapOf<String, Any?>()
            for ((key, prop) in def.function.parameters.properties) {
                val propMap = mutableMapOf<String, Any?>(
                    "type" to prop.type,
                    "description" to prop.description,
                )
                if (prop.enum != null) propMap["enum"] = prop.enum
                props[key] = propMap
            }
            mapOf(
                "type" to def.type,
                "function" to mapOf(
                    "name" to def.function.name,
                    "description" to def.function.description,
                    "parameters" to mapOf(
                        "type" to def.function.parameters.type,
                        "properties" to props,
                        "required" to def.function.parameters.required,
                    )
                )
            )
        }
    }

    /**
     * Extract the final text content from hermes AgentResult.
     */
    fun extractFinalContent(result: com.xiaomo.androidforclaw.hermes.AgentResult): String {
        val lastAssistant = result.messages.lastOrNull { it["role"] == "assistant" }
        return (lastAssistant?.get("content") as? String) ?: ""
    }
}
