package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.xiaomo.hermes.hermes.AssistantMessage
import com.xiaomo.hermes.hermes.ChatCompletionResponse
import com.xiaomo.hermes.hermes.ChatCompletionServer
import com.xiaomo.hermes.hermes.Choice
import com.xiaomo.hermes.hermes.ToolCall
import com.xiaomo.hermes.hermes.ToolCallFunction
import org.json.JSONObject
import java.util.UUID

/**
 * Reverse bridge that lets the Hermes agent loop drive an Operit [AIService]
 * provider.
 *
 * Operit providers do NOT emit OpenAI-spec `tool_calls`: they stream
 * `<tool name="X"><param>…</param></tool>` XML inside the assistant text.
 * For HermesAgentLoop's standard tool-calling path to work we:
 *   1. Collect the provider's [com.ai.assistance.operit.util.stream.Stream] into a full string.
 *   2. Regex-parse `<tool>` blocks and synthesize [ToolCall] entries.
 *   3. Rebuild the tool_call_id → tool_name map so subsequent
 *      `role="tool"` messages round-trip back into `<tool_result>` XML
 *      the provider recognizes.
 */
class OperitChatCompletionServer(
    private val context: Context,
    private val service: AIService,
    private val modelParameters: List<ModelParameter<*>> = emptyList(),
    private val enableThinking: Boolean = false,
    private val availableTools: List<ToolPrompt>? = null,
    private val streamFromProvider: Boolean = false,
    private val onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit =
        { _, _, _ -> },
    private val onTurnComplete: suspend (
        inputFinal: Int,
        cachedInputFinal: Int,
        outputFinal: Int
    ) -> Unit = { _, _, _ -> },
    private val onNonFatalError: suspend (error: String) -> Unit = {}
) : ChatCompletionServer {

    override suspend fun chatCompletion(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>?,
        temperature: Double,
        maxTokens: Int?,
        extraBody: Map<String, Any?>?
    ): ChatCompletionResponse? {
        val roleCounts = messages.groupingBy { it["role"] as? String ?: "?" }.eachCount()
        Log.d(TAG, "chatCompletion IN: msgs=${messages.size} roles=$roleCounts " +
            "tools=${tools?.size ?: 0} temp=$temperature maxTokens=$maxTokens " +
            "stream=$streamFromProvider thinking=$enableThinking")

        val toolCallIdToName = buildToolCallIdToNameMap(messages)
        if (toolCallIdToName.isNotEmpty()) {
            Log.d(TAG, "chatCompletion IN: toolCallIdToName=$toolCallIdToName")
        }
        val chatHistory = messages.map { it.toPromptTurn(toolCallIdToName) }

        val aggregated = StringBuilder()
        val apiStartMs = System.currentTimeMillis()
        Log.d(TAG, "chatCompletion: calling service.sendMessage...")
        service.sendMessage(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters,
            enableThinking = enableThinking,
            stream = streamFromProvider,
            availableTools = availableTools,
            onTokensUpdated = onTokensUpdated,
            onNonFatalError = onNonFatalError
        ).collect { chunk -> aggregated.append(chunk) }
        val apiElapsedMs = System.currentTimeMillis() - apiStartMs
        Log.d(TAG, "chatCompletion: service.sendMessage completed in ${apiElapsedMs}ms")

        onTurnComplete(
            service.inputTokenCount,
            service.cachedInputTokenCount,
            service.outputTokenCount
        )

        val fullText = aggregated.toString()
        val toolCalls = extractToolCalls(fullText)

        Log.d(TAG, "chatCompletion OUT: textLen=${fullText.length} " +
            "toolCalls=${toolCalls?.size ?: 0} " +
            "tokens(in=${service.inputTokenCount} cached=${service.cachedInputTokenCount} " +
            "out=${service.outputTokenCount})")
        if (!toolCalls.isNullOrEmpty()) {
            Log.d(TAG, "chatCompletion OUT: toolNames=${toolCalls.map { it.function.name }}")
        }

        return ChatCompletionResponse(
            choices = listOf(
                Choice(
                    message = AssistantMessage(
                        content = fullText,
                        toolCalls = toolCalls
                    )
                )
            )
        )
    }

    internal fun buildToolCallIdToNameMap(
        messages: List<Map<String, Any?>>
    ): Map<String, String> {
        val out = HashMap<String, String>()
        for (msg in messages) {
            if (msg["role"] != "assistant") continue
            val toolCalls = msg["tool_calls"] as? List<*> ?: continue
            for (tc in toolCalls) {
                val tcMap = tc as? Map<*, *> ?: continue
                val id = tcMap["id"] as? String ?: continue
                val function = tcMap["function"] as? Map<*, *> ?: continue
                val name = function["name"] as? String ?: continue
                out[id] = name
            }
        }
        return out
    }

    internal fun extractToolCalls(text: String): List<ToolCall>? {
        val matches = ChatMarkupRegex.toolCallPattern.findAll(text).toList()
        if (matches.isEmpty()) return null
        return matches.map { match ->
            val toolName = match.groupValues[2]
            val body = match.groupValues[3]
            val paramsJson = JSONObject()
        ChatMarkupRegex.toolParamPattern.findAll(body).forEach { paramMatch ->
                val key = paramMatch.groupValues[1]
                val value = unescapeXml(paramMatch.groupValues[2])
                paramsJson.put(key, value)
            }
            ToolCall(
                id = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                type = "function",
                function = ToolCallFunction(
                    name = toolName,
                    arguments = paramsJson.toString()
                )
            )
        }
    }

    internal fun Map<String, Any?>.toPromptTurn(
        toolCallIdToName: Map<String, String>
    ): PromptTurn {
        val role = (this["role"] as? String) ?: "user"
        val rawContent = when (val c = this["content"]) {
            is String -> c
            is List<*> -> c.filterIsInstance<Map<*, *>>()
                .mapNotNull { it["text"] as? String }
                .joinToString("\n")
            null -> ""
            else -> c.toString()
        }
        if (role == "tool") {
            val toolCallId = this["tool_call_id"] as? String
            val toolName = toolCallId?.let { toolCallIdToName[it] } ?: "tool"
            val wrapped = if (rawContent.trimStart().startsWith("<tool_result")) {
                rawContent
            } else {
                val status = detectToolResultStatus(rawContent)
                "<tool_result name=\"${escapeAttr(toolName)}\" status=\"$status\">" +
                    "<content>${escapeXmlText(rawContent)}</content>" +
                    "</tool_result>"
            }
            return PromptTurn(
                kind = PromptTurnKind.TOOL_RESULT,
                content = wrapped,
                toolName = toolName
            )
        }

        // When the assistant message carries structured tool_calls, rebuild
        // the content as clean XML from the structured data so the provider
        // re-parses the exact same set of tool calls on the next turn.
        // This prevents the "User cancelled" injection that happens when
        // positional matching between re-parsed tool calls and tool results
        // gets out of sync due to regex mismatches on the raw content.
        val toolCalls = this["tool_calls"] as? List<*>
        if (role == "assistant" && toolCalls != null && toolCalls.isNotEmpty()) {
            // Strip XML tool tags from content — keep only plain text
            val textOnly = ChatMarkupRegex.toolTag.replace(rawContent, "").trim()
            // Rebuild tool XML from the structured tool_calls list
            val toolXml = StringBuilder()
            for (tc in toolCalls) {
                val tcMap = tc as? Map<*, *> ?: continue
                val function = tcMap["function"] as? Map<*, *> ?: continue
                val name = function["name"] as? String ?: continue
                val argsStr = function["arguments"] as? String ?: "{}"
                toolXml.append("<tool name=\"").append(escapeAttr(name)).append("\">")
                try {
                    val argsJson = JSONObject(argsStr)
                    argsJson.keys().forEach { key ->
                        val value = argsJson.opt(key)?.toString().orEmpty()
                        toolXml.append("<param name=\"").append(escapeAttr(key)).append("\">")
                        toolXml.append(escapeXmlText(value))
                        toolXml.append("</param>")
                    }
                } catch (_: Exception) {
                    toolXml.append("<param name=\"raw\">")
                    toolXml.append(escapeXmlText(argsStr))
                    toolXml.append("</param>")
                }
                toolXml.append("</tool>")
            }
            val rebuiltContent = if (textOnly.isNotEmpty()) {
                "$textOnly\n$toolXml"
            } else {
                toolXml.toString()
            }
            return PromptTurn(
                kind = PromptTurnKind.TOOL_CALL,
                content = rebuiltContent
            )
        }

        val kind = PromptTurnKind.fromRole(role)
        val toolName = this["name"] as? String
        return PromptTurn(kind = kind, content = rawContent, toolName = toolName)
    }

    internal fun detectToolResultStatus(json: String): String {
        return try {
            val obj = JSONObject(json)
            val success = obj.optBoolean("success", true)
            if (success && obj.optString("error").isNullOrBlank()) "success" else "error"
        } catch (_: Exception) {
            "success"
        }
    }

    private fun escapeAttr(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun escapeXmlText(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val TAG = "HermesBridge/Server"

        /** Unescape XML entities so param values round-trip correctly. */
        private fun unescapeXml(text: String): String =
            text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
    }
}
