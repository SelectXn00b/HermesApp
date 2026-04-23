package com.xiaomo.hermes.hermes

import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Longcat Flash Chat tool call parser.
 *
 * Same as Hermes but uses <longcat_tool_call> tags instead of <tool_call>.
 * Based on VLLM's LongcatFlashToolParser (extends Hermes2ProToolParser).
 */
class LongcatToolCallParser : ToolCallParser() {

    override val supportedModels: List<String> = listOf("longcat")

    companion object {
        private const val _TAG = "LongcatParser"
        private val PATTERN = Pattern.compile(
            "<longcat_tool_call>\\s*(.*?)\\s*</longcat_tool_call>|<longcat_tool_call>\\s*(.*)",
            Pattern.DOTALL
        )
    }

    override fun parse(response: String): ParseResult {
        if (!response.contains("<longcat_tool_call>")) {
            return ParseResult(content = response, toolCalls = null)
        }

        return try {
            val matcher = PATTERN.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()

            while (matcher.find()) {
                val rawJson = if (matcher.group(1) != null) matcher.group(1) else matcher.group(2)
                if (rawJson.isNullOrBlank()) continue

                val tcData = JSONObject(rawJson)
                val name = tcData.optString("name", "")
                if (name.isEmpty()) continue

                val arguments = tcData.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any>()
                val keys = arguments.keys()
                while (keys.hasNext()) { val k = keys.next(); argsMap[k] = arguments.get(k) }

                toolCalls.add(
                    ParsedToolCall(
                        id = "call_${UUID.randomUUID().toString().take(8)}",
                        type = "function",
                        name = name,
                        arguments = argsMap,
                        rawArguments = arguments.toString()
                    )
                )
            }

            if (toolCalls.isEmpty()) {
                ParseResult(content = response, toolCalls = null)
            } else {
                val content = response.substringBefore("<longcat_tool_call>").trim()
                ParseResult(content = content.ifEmpty { null }, toolCalls = toolCalls)
            }
        } catch (e: Exception) {
            ParseResult(content = response, toolCalls = null)
        }
    }
}
