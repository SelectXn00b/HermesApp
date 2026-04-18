package com.xiaomo.hermes.hermes.bridge

/**
 * AgentLoopAdapter — Drop-in replacement for app's agent.loop.AgentLoop.
 *
 * Has the same constructor signature and public API so callers just change the import.
 * Internally delegates to hermes-android's HermesAgentLoop.
 *
 * Constructor params kept for compat (some are unused in hermes path):
 * - llmProvider → wrapped as ChatCompletionServer for hermes
 * - toolRegistry, androidToolRegistry → tool definitions + dispatch
 * - contextManager → unused (hermes has its own)
 * - maxIterations → mapped to hermes maxTurns
 * - modelRef → passed through to LLM
 * - configLoader → used for model resolution
 */

import android.util.Log
import com.xiaomo.hermes.agent.context.ContextBuilder
import com.xiaomo.hermes.agent.context.ContextManager
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.agent.loop.AgentResult
import com.xiaomo.hermes.agent.loop.ProgressUpdate
import com.xiaomo.hermes.agent.tools.AndroidToolRegistry
import com.xiaomo.hermes.agent.tools.Tool
import com.xiaomo.hermes.agent.tools.ToolRegistry
import com.xiaomo.hermes.config.ConfigLoader
import com.xiaomo.hermes.hermes.AgentResult as HermesAgentResult
import com.xiaomo.hermes.hermes.AssistantMessage
import com.xiaomo.hermes.hermes.ChatCompletionResponse
import com.xiaomo.hermes.hermes.ChatCompletionServer
import com.xiaomo.hermes.hermes.Choice
import com.xiaomo.hermes.hermes.HermesAgentLoop as HermesLoop
import com.xiaomo.hermes.hermes.ToolCall as HermesToolCall
import com.xiaomo.hermes.hermes.ToolCallFunction
import com.xiaomo.hermes.hermes.ToolDispatcher
import com.xiaomo.hermes.hermes.getToolDefinitions
import com.xiaomo.hermes.hermes.handleFunctionCall
import com.xiaomo.hermes.hermes.registerDefaultTools
import com.xiaomo.hermes.hermes.setPlatformDelegate
import com.xiaomo.hermes.providers.UnifiedLLMProvider
import com.xiaomo.hermes.providers.llm.ImageBlock
import com.xiaomo.hermes.providers.llm.Message
import com.xiaomo.hermes.providers.llm.ToolCall as AppToolCall
import com.xiaomo.hermes.providers.ToolDefinition as LegacyToolDefinition
import com.xiaomo.hermes.providers.FunctionDefinition as LegacyFunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema as LegacyParametersSchema
import com.xiaomo.hermes.providers.PropertySchema as LegacyPropertySchema
import com.xiaomo.hermes.data.model.TaskDataManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Drop-in replacement for com.xiaomo.hermes.agent.loop.AgentLoop.
 * Same constructor, same public API, hermes internals.
 */
class AgentLoopAdapter(
    private val llmProvider: UnifiedLLMProvider,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    @Suppress("UNUSED_PARAMETER") contextManager: ContextManager? = null,
    @Suppress("DEPRECATION") private val maxIterations: Int = 30,
    private val modelRef: String? = null,
    private val configLoader: ConfigLoader? = null
) : AgentLoopInterface {
    companion object {
        private const val TAG = "AgentLoopAdapter"

        @Volatile
        private var toolsRegistered = false
    }

    init {
        synchronized(Companion) {
            if (!toolsRegistered) {
                registerDefaultTools()
                // PlatformToolDelegate 需要 Android Context，这里无法注入。
                // 由 HermesAgentLoop 或 MyApplication 初始化时通过 setPlatformDelegate 注入。
                // 这里只确保工具注册。
                toolsRegistered = true
            }
        }
    }

    /**
     * 注入 PlatformToolDelegate（由 MyApplication 在初始化时调用）
     */
    fun injectPlatformDelegate(context: android.content.Context) {
        synchronized(Companion) {
            setPlatformDelegate(PlatformToolDelegateImpl(context, toolRegistry, androidToolRegistry))
        }
    }

    // ── Public API matching old AgentLoop ────────────────────────────

    override var sessionKey: String? = null

    override var extraTools: List<Tool> = emptyList()

    private val _progressFlow = MutableSharedFlow<ProgressUpdate>(
        replay = 1,
        extraBufferCapacity = 10
    )
    override val progressFlow: SharedFlow<ProgressUpdate> = _progressFlow.asSharedFlow()

    override val steerChannel = Channel<String>(capacity = 16)

    override var conversationMessages: List<Message> = emptyList()

    override var yieldSignal: CompletableDeferred<String?>? = null

    override val hookRunner = com.xiaomo.hermes.agent.hook.HookRunner()

    @Volatile
    private var shouldStop = false

    override fun stop() {
        shouldStop = true
        Log.d(TAG, "Stop signal received")
    }

    override fun reset() {
        shouldStop = false
        while (steerChannel.tryReceive().isSuccess) { /* drain */ }
        yieldSignal = null
        Log.d(TAG, "AgentLoop reset for steer-restart")
    }

    // ── Core execution ──────────────────────────────────────────────

    override suspend fun run(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message>,
        reasoningEnabled: Boolean,
        images: List<ImageBlock>?
    ): AgentResult {
        return try {
            runInternal(systemPrompt, userMessage, contextHistory, reasoningEnabled, images)
        } catch (e: Exception) {
            Log.e(TAG, "❌ AgentLoop 未捕获的错误", e)
            val errorMessage = buildString {
                append("❌ Agent 执行失败\n\n")
                append("**错误信息**: ${e.message ?: "未知错误"}\n\n")
                append("**建议**: \n")
                append("- 请检查网络连接\n")
                append("- 如果问题持续,请使用 /new 重新开始对话\n")
            }
            AgentResult(
                finalContent = errorMessage,
                toolsUsed = emptyList(),
                messages = listOf(
                    com.xiaomo.hermes.providers.llm.systemMessage(systemPrompt),
                    com.xiaomo.hermes.providers.llm.userMessage(userMessage),
                    com.xiaomo.hermes.providers.llm.assistantMessage(errorMessage)
                ),
                iterations = 0
            )
        }
    }

    private suspend fun runInternal(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message>,
        reasoningEnabled: Boolean,
        images: List<ImageBlock>?
    ): AgentResult {
        _progressFlow.emit(ProgressUpdate.Iteration(1))

        // Build hermes tool definitions
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

        // Create hermes AgentLoop
        val hermesLoop = HermesLoop(
            server = LLMServerAdapter(modelRef, reasoningEnabled),
            toolSchemas = toolSchemas,
            validToolNames = validToolNames,
            toolDispatcher = hermesToolDispatcher,
            maxTurns = maxIterations.coerceAtMost(160),
            taskId = sessionKey ?: "default"
        )

        // Build messages (system + history + user)
        val messages = mutableListOf<Map<String, Any?>>()
        messages.add(mapOf("role" to "system", "content" to systemPrompt))

        // Convert context history
        for (msg in contextHistory) {
            val m = mutableMapOf<String, Any?>(
                "role" to msg.role,
                "content" to msg.content
            )
            msg.toolCallId?.let { m["tool_call_id"] = it }
            msg.name?.let { m["name"] = it }
            msg.toolCalls?.let { tcs ->
                m["tool_calls"] = tcs.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tc.name,
                            "arguments" to tc.arguments
                        )
                    )
                }
            }
            messages.add(m)
        }

        // User message (with images if any)
        if (images != null && images.isNotEmpty()) {
            val content = mutableListOf<Map<String, Any?>>()
            content.add(mapOf("type" to "text", "text" to userMessage))
            for (img in images) {
                content.add(mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf("url" to "data:${img.mimeType};base64,${img.base64}")
                ))
            }
            messages.add(mapOf("role" to "user", "content" to content))
        } else {
            messages.add(mapOf("role" to "user", "content" to userMessage))
        }

        // Run hermes loop
        val hermesResult: HermesAgentResult = hermesLoop.run(messages)

        _progressFlow.emit(ProgressUpdate.IterationComplete(
            number = hermesResult.turnsUsed,
            iterationDuration = 0,
            llmDuration = 0,
            execDuration = 0
        ))

        // Extract final content
        val lastAssistant = hermesResult.messages.lastOrNull { it["role"] == "assistant" }
        val finalContent = lastAssistant?.get("content") as? String ?: ""

        // Extract tools used
        val toolsUsed = hermesResult.messages
            .filter { it["role"] == "assistant" && it["tool_calls"] != null }
            .flatMap {
                @Suppress("UNCHECKED_CAST")
                (it["tool_calls"] as? List<Map<String, Any?>>)
                    ?.mapNotNull { tc ->
                        (tc["function"] as? Map<String, Any?>)?.get("name") as? String
                    } ?: emptyList()
            }
            .distinct()

        // Convert hermes messages back to app Message format
        val appMessages = hermesResult.messages.map { msg ->
            val role = msg["role"] as? String ?: "user"
            val content = when (val c = msg["content"]) {
                is String -> c
                else -> c?.toString() ?: ""
            }
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
                toolCallId = msg["tool_call_id"] as? String,
                name = msg["name"] as? String,
                toolCalls = toolCalls
            )
        }
        conversationMessages = appMessages

        return AgentResult(
            finalContent = finalContent,
            toolsUsed = toolsUsed,
            messages = appMessages,
            iterations = hermesResult.turnsUsed
        )
    }

    // ── LLM Adapter ─────────────────────────────────────────────────

    private inner class LLMServerAdapter(
        private val modelRef: String?,
        private val reasoningEnabled: Boolean
    ) : ChatCompletionServer {

        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?
        ): ChatCompletionResponse? {
            val appMessages = messages.map { msg ->
                val role = msg["role"] as? String ?: "user"
                val content = when (val c = msg["content"]) {
                    is String -> c
                    else -> c?.toString() ?: ""
                }
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
                    toolCallId = msg["tool_call_id"] as? String,
                    name = msg["name"] as? String,
                    toolCalls = toolCalls
                )
            }

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

            val response = llmProvider.chatWithTools(
                messages = appMessages,
                tools = appTools,
                modelRef = modelRef,
                temperature = temperature,
                maxTokens = maxTokens,
                reasoningEnabled = reasoningEnabled
            )

            // Emit progress for tool calls
            response.toolCalls?.forEach { tc ->
                try {
                    val args = com.google.gson.Gson().fromJson(
                        tc.arguments, Map::class.java
                    ) as? Map<String, Any?> ?: emptyMap()
                    _progressFlow.emit(ProgressUpdate.ToolCall(tc.name, args))
                } catch (_: Exception) {}
            }

            // Emit reasoning
            response.thinkingContent?.let {
                if (it.isNotBlank()) {
                    _progressFlow.emit(ProgressUpdate.Reasoning(it, 0))
                }
            }

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

    // ── Tool Dispatcher ─────────────────────────────────────────────

    private val hermesToolDispatcher = object : ToolDispatcher {
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?
        ): String {
            val startTime = System.currentTimeMillis()
            @Suppress("UNCHECKED_CAST")
            val result = handleFunctionCall(
                functionName = toolName,
                functionArgs = args as Map<String, Any>,
                taskId = taskId
            )
            val duration = System.currentTimeMillis() - startTime

            // Emit tool result progress
            try {
                _progressFlow.emit(ProgressUpdate.ToolResult(toolName, result.take(200), duration))
            } catch (_: Exception) {}

            return result
        }
    }
}
