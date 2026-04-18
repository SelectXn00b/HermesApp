package com.xiaomo.hermes.hermes.bridge

/**
 * HermesAgentLoop — bridges hermes GatewayRunner's AgentLoop interface
 * to hermes-android's own AgentLoop + ToolDispatcher.
 *
 * 批次②：从 app AgentLoop 切到 hermes AgentLoop 体系。
 * - ChatCompletionServer 适配：UnifiedLLMProvider → hermes ChatCompletionServer
 * - ToolDispatcher 适配：hermes ModelTools.ToolRegistry.dispatch
 * - 工具定义：hermes ModelTools.getToolDefinitions
 *
 * Lives in the app module because it needs Android Context + UnifiedLLMProvider。
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.agent.context.ContextBuilder
import com.xiaomo.hermes.agent.tools.AndroidToolRegistry
import com.xiaomo.hermes.agent.tools.ToolRegistry as AppToolRegistry
import com.xiaomo.hermes.config.ConfigLoader
import com.xiaomo.hermes.data.model.TaskDataManager
import com.xiaomo.hermes.hermes.AgentResult
import com.xiaomo.hermes.hermes.AssistantMessage
import com.xiaomo.hermes.hermes.ChatCompletionResponse
import com.xiaomo.hermes.hermes.ChatCompletionServer
import com.xiaomo.hermes.hermes.Choice
import com.xiaomo.hermes.hermes.HermesAgentLoop as HermesLoop
import com.xiaomo.hermes.hermes.ToolCall as HermesToolCall
import com.xiaomo.hermes.hermes.ToolCallFunction
import com.xiaomo.hermes.hermes.ToolDispatcher
import com.xiaomo.hermes.hermes.ToolRegistry as HermesToolRegistry
import com.xiaomo.hermes.hermes.getToolDefinitions
import com.xiaomo.hermes.hermes.handleFunctionCall
import com.xiaomo.hermes.hermes.registerDefaultTools
import com.xiaomo.hermes.hermes.gateway.AgentLoop as GatewayAgentLoopInterface
import com.xiaomo.hermes.providers.UnifiedLLMProvider
import com.xiaomo.hermes.providers.llm.Message
import com.xiaomo.hermes.providers.llm.ToolCall as AppToolCall
import com.xiaomo.hermes.providers.ToolDefinition as LegacyToolDefinition
import com.xiaomo.hermes.providers.FunctionDefinition as LegacyFunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema as LegacyParametersSchema
import com.xiaomo.hermes.providers.PropertySchema as LegacyPropertySchema
import kotlinx.coroutines.withTimeout

/**
 * Implements hermes gateway AgentLoop by delegating to hermes-android's own HermesAgentLoop.
 */
class HermesAgentLoop(private val context: Context) : GatewayAgentLoopInterface {

    companion object {
        private const val TAG = "HermesAgentLoop"
        private const val AGENT_TIMEOUT_MS = 300_000L  // 5 minutes
        @Volatile
        private var toolsRegistered = false
    }

    private val llmProvider by lazy { UnifiedLLMProvider(context) }
    private val appToolRegistry by lazy {
        AppToolRegistry(context = context, taskDataManager = TaskDataManager.getInstance())
    }
    private val androidToolRegistry by lazy {
        AndroidToolRegistry(
            context = context,
            taskDataManager = TaskDataManager.getInstance(),
            cameraCaptureManager = null
        )
    }
    private val contextBuilder by lazy {
        ContextBuilder(context = context, toolRegistry = appToolRegistry, androidToolRegistry = androidToolRegistry)
    }
    private val configLoader by lazy { ConfigLoader(context) }

    init {
        // 确保 hermes 工具只注册一次
        synchronized(Companion) {
            if (!toolsRegistered) {
                registerDefaultTools()
                toolsRegistered = true
            }
        }
    }

    /**
     * Adapter: UnifiedLLMProvider → hermes ChatCompletionServer
     *
     * 把 hermes 的 Map<String, Any?> 消息格式转成 app 的 Message 类型，
     * 调用 UnifiedLLMProvider.chatWithTools，再把结果转回 hermes 格式。
     */
    private inner class LLMServerAdapter(
        private val modelRef: String?
    ) : ChatCompletionServer {

        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?
        ): ChatCompletionResponse? {
            // 转换消息格式：Map → Message
            val appMessages = messages.map { msg ->
                val role = msg["role"] as? String ?: "user"
                val content = when (val c = msg["content"]) {
                    is String -> c
                    else -> c?.toString() ?: ""
                }
                val toolCallId = msg["tool_call_id"] as? String
                val name = msg["name"] as? String

                // 解析 assistant 的 tool_calls
                @Suppress("UNCHECKED_CAST")
                val toolCalls = (msg["tool_calls"] as? List<Map<String, Any?>>)?.map { tc ->
                    val fn = tc["function"] as? Map<String, Any?>
                    AppToolCall(
                        id = tc["id"] as? String ?: "",
                        name = fn?.get("name") as? String ?: "",
                        arguments = fn?.get("arguments") as? String ?: "{}"
                    )
                }

                Message(
                    role = role,
                    content = content,
                    name = name,
                    toolCallId = toolCallId,
                    toolCalls = toolCalls
                )
            }

            // 转换工具定义：Map → AppToolDefinition
            val appTools = tools?.mapNotNull { toolMap ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val fn = toolMap["function"] as? Map<String, Any?> ?: return@mapNotNull null
                    val fnName = fn["name"] as? String ?: return@mapNotNull null
                    val fnDesc = fn["description"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val params = fn["parameters"] as? Map<String, Any?> ?: emptyMap()

                    LegacyToolDefinition(
                        function = LegacyFunctionDefinition(
                            name = fnName,
                            description = fnDesc,
                            parameters = convertParamsSchema(params)
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to convert tool definition: ${e.message}")
                    null
                }
            }

            // 调用 UnifiedLLMProvider
            val response = llmProvider.chatWithTools(
                messages = appMessages,
                tools = appTools,
                modelRef = modelRef,
                temperature = temperature,
                maxTokens = maxTokens,
                reasoningEnabled = true
            )

            // 转回 hermes 格式
            val hermesToolCalls = response.toolCalls?.map { tc ->
                HermesToolCall(
                    id = tc.id,
                    type = "function",
                    function = ToolCallFunction(name = tc.name, arguments = tc.arguments)
                )
            }

            return ChatCompletionResponse(
                choices = listOf(
                    Choice(
                        message = AssistantMessage(
                            content = response.content,
                            toolCalls = hermesToolCalls,
                            reasoningContent = response.thinkingContent
                        )
                    )
                )
            )
        }

        /**
         * 把 Map 格式的 parameters schema 转成 ParametersSchema
         */
        @Suppress("UNCHECKED_CAST")
        private fun convertParamsSchema(params: Map<String, Any?>): LegacyParametersSchema {
            val type = params["type"] as? String ?: "object"
            val properties = (params["properties"] as? Map<String, Map<String, Any?>>)
                ?.mapValues { (_, v) -> convertPropertySchema(v) }
                ?: emptyMap()
            val required = (params["required"] as? List<String>) ?: emptyList()
            return LegacyParametersSchema(type = type, properties = properties, required = required)
        }

        @Suppress("UNCHECKED_CAST")
        private fun convertPropertySchema(prop: Map<String, Any?>): LegacyPropertySchema {
            return LegacyPropertySchema(
                type = prop["type"] as? String ?: "string",
                description = prop["description"] as? String ?: "",
                enum = prop["enum"] as? List<String>,
                items = (prop["items"] as? Map<String, Any?>)?.let { convertPropertySchema(it) }
            )
        }
    }

    /**
     * ToolDispatcher 适配：委托给 hermes ModelTools.ToolRegistry
     */
    private val hermesToolDispatcher = object : ToolDispatcher {
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?
        ): String {
            @Suppress("UNCHECKED_CAST")
            return handleFunctionCall(
                functionName = toolName,
                functionArgs = args as Map<String, Any>,
                taskId = taskId
            )
        }
    }

    override suspend fun process(text: String, sessionKey: String, context: Map<String, String>): String? {
        Log.d(TAG, "Processing via hermes AgentLoop (session=$sessionKey): ${text.take(80)}")

        // Resolve model
        val platform = context["platform"] ?: "app_chat"
        val chatId = context["chatId"] ?: sessionKey
        val modelRef = try {
            configLoader.resolveAgentModelRef(channel = platform, accountId = chatId)
        } catch (_: Exception) { null }

        // Build system prompt
        val systemPrompt = try {
            contextBuilder.buildSystemPrompt(
                userGoal = text,
                packageName = "",
                testMode = "exploration"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build system prompt, using default", e)
            "You are a helpful AI assistant running on an Android device."
        }

        // 获取 hermes 工具定义（OpenAI format Map）
        val toolDefs = getToolDefinitions()
        val toolSchemas = toolDefs.map { td ->
            mapOf<String, Any?>(
                "type" to td.type,
                "function" to mapOf(
                    "name" to td.function.name,
                    "description" to td.function.description,
                    "parameters" to td.function.parameters
                )
            )
        }
        val validToolNames = toolDefs.map { it.function.name }.toSet()

        // 创建 hermes AgentLoop
        val hermesLoop = HermesLoop(
            server = LLMServerAdapter(modelRef),
            toolSchemas = toolSchemas,
            validToolNames = validToolNames,
            toolDispatcher = hermesToolDispatcher,
            maxTurns = 30,
            taskId = sessionKey
        )

        // 构建初始消息
        val messages = mutableListOf<Map<String, Any?>>(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to text)
        )

        return try {
            withTimeout(AGENT_TIMEOUT_MS) {
                val result: AgentResult = hermesLoop.run(messages)

                // 从最后一条 assistant 消息提取 content
                val lastAssistant = result.messages.lastOrNull { it["role"] == "assistant" }
                val content = lastAssistant?.get("content") as? String ?: ""

                Log.d(TAG, "Hermes agent completed: ${result.turnsUsed} turns, " +
                    "natural=${result.finishedNaturally}, content=${content.length} chars, " +
                    "errors=${result.toolErrors.size}")
                content
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hermes agent loop failed", e)
            "Error processing your request: ${e.message}"
        }
    }
}
