package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.stream
import com.xiaomo.hermes.hermes.AgentEvent
import com.xiaomo.hermes.hermes.AgentEventSink
import com.xiaomo.hermes.hermes.HermesAgentLoop
import org.json.JSONObject

/**
 * Entry point Operit UI code calls instead of [EnhancedAIService.sendMessage].
 *
 * Streams structured XML chunks (`<think>`, `<tool>`, `<tool_result>`, plain
 * assistant text) into Operit's [Stream] so [CustomXmlRenderer] can render
 * them incrementally, exactly as it would for the legacy provider output.
 */
class HermesAdapter private constructor(private val context: Context) {

    private val serviceManager = MultiServiceManager(context.applicationContext)

    suspend fun sendMessage(
        message: String,
        chatId: String,
        chatHistory: List<PromptTurn> = emptyList(),
        functionType: FunctionType = FunctionType.CHAT,
        maxTokens: Int? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): Stream<String> {
        val resolvedService = if (chatModelConfigIdOverride != null && chatModelIndexOverride != null) {
            serviceManager.getServiceForConfig(chatModelConfigIdOverride, chatModelIndexOverride)
        } else {
            serviceManager.getServiceForFunction(functionType)
        }
        val server = OperitChatCompletionServer(
            context = context.applicationContext,
            service = resolvedService
        )
        val dispatcher = OperitToolDispatcher(context.applicationContext)

        val openAiMessages = buildOpenAiMessages(chatHistory, message)

        Log.d(TAG, "sendMessage: chatId=$chatId historyTurns=${chatHistory.size} " +
            "msgLen=${message.length} functionType=$functionType maxTokens=$maxTokens " +
            "configOverride=$chatModelConfigIdOverride")

        return stream {
            val collector: StreamCollector<String> = this
            val sink: AgentEventSink = { event -> collector.emitAgentEvent(event) }

            val loop = HermesAgentLoop(
                server = server,
                toolSchemas = emptyList(),
                validToolNames = emptySet(),
                toolDispatcher = dispatcher,
                maxTurns = 30,
                taskId = chatId,
                maxTokens = maxTokens,
                eventSink = sink
            )

            loop.run(openAiMessages.toMutableList())
        }
    }

    private suspend fun StreamCollector<String>.emitAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Thinking -> {
                Log.v(TAG, "event Thinking turn=${event.turn} len=${event.text.length}")
                emit("<think>${escapeXml(event.text)}</think>")
            }
            is AgentEvent.AssistantDelta -> {
                if (event.text.isNotEmpty()) {
                    Log.v(TAG, "event AssistantDelta turn=${event.turn} len=${event.text.length}")
                    emit(event.text)
                }
            }
            is AgentEvent.ToolCallStart -> {
                Log.d(TAG, "event ToolCallStart turn=${event.turn} id=${event.toolCallId} name=${event.name}")
                emit(renderToolCallXml(event.name, event.argsJson))
            }
            is AgentEvent.ToolCallEnd -> {
                Log.d(TAG, "event ToolCallEnd turn=${event.turn} id=${event.toolCallId} " +
                    "name=${event.name} error=${event.error?.take(100)}")
                val synthetic = ToolResult(
                    toolName = event.name,
                    success = event.error == null,
                    result = StringResultData(event.resultJson),
                    error = event.error
                )
                emit(ConversationMarkupManager.formatToolResultForMessage(synthetic))
            }
            is AgentEvent.Error -> {
                Log.w(TAG, "event Error turn=${event.turn} msg=${event.message}")
                emit("<status type=\"error\">${escapeXml(event.message)}</status>")
            }
            is AgentEvent.Final -> {
                Log.i(TAG, "event Final turn=${event.turnsUsed} finishedNaturally=${event.finishedNaturally}")
            }
        }
    }

    private fun renderToolCallXml(toolName: String, argsJson: String): String {
        val params = try {
            val json = JSONObject(argsJson)
            buildString {
                json.keys().forEach { key ->
                    val value = json.opt(key)?.toString().orEmpty()
                    append("<param name=\"").append(escapeXml(key)).append("\">")
                    append(escapeXml(value))
                    append("</param>")
                }
            }
        } catch (_: Exception) {
            "<param name=\"raw\">${escapeXml(argsJson)}</param>"
        }
        return "<tool name=\"${escapeXml(toolName)}\">$params</tool>"
    }

    private fun escapeXml(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun buildOpenAiMessages(
        history: List<PromptTurn>,
        currentUserMessage: String
    ): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>(history.size + 1)
        for (turn in history) {
            val role = when (turn.kind) {
                PromptTurnKind.SYSTEM -> "system"
                PromptTurnKind.USER -> "user"
                PromptTurnKind.ASSISTANT -> "assistant"
                PromptTurnKind.TOOL_CALL -> "assistant"
                PromptTurnKind.TOOL_RESULT -> "tool"
                PromptTurnKind.SUMMARY -> "system"
            }
            out.add(mapOf("role" to role, "content" to turn.content))
        }
        if (currentUserMessage.isNotEmpty() && out.lastOrNull()?.get("role") != "user") {
            out.add(mapOf("role" to "user", "content" to currentUserMessage))
        }
        return out
    }

    companion object {
        private const val TAG = "HermesBridge/Adapter"

        @Volatile
        private var instance: HermesAdapter? = null

        fun getInstance(context: Context): HermesAdapter {
            return instance ?: synchronized(this) {
                instance ?: HermesAdapter(context.applicationContext).also { instance = it }
            }
        }
    }
}
