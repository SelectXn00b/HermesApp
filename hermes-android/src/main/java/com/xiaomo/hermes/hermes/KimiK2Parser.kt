package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Kimi K2 tool call parser.
 *
 * Format:
 *     <|tool_calls_section_begin|>
 *     <|tool_call_begin|>function_id:0<|tool_call_argument_begin|>{"arg": "val"}<|tool_call_end|>
 *     <|tool_calls_section_end|>
 *
 * Based on VLLM's KimiK2ToolParser.extract_tool_calls()
 */
class KimiK2ToolCallParser : ToolCallParser() {

    override val supportedModels: List<String> = listOf("kimi_k2")

    companion object {
        private const val _TAG = "KimiK2Parser"
        private val START_TOKENS = listOf(
            "<|tool_calls_section_begin|>",
            "<|tool_call_section_begin|>"
        )
        private val PATTERN = Pattern.compile(
            "<\\|tool_call_begin\\|>\\s*([^<]+:\\d+)\\s*" +
            "<\\|tool_call_argument_begin\\|>\\s*" +
            "((?:(?!<\\|tool_call_begin\\|>).)*?)\\s*" +
            "<\\|tool_call_end\\|>",
            Pattern.DOTALL
        )
    }

    override fun parse(response: String): ParseResult {
        val hasStart = START_TOKENS.any { response.contains(it) }
        if (!hasStart) {
            return ParseResult(content = response, toolCalls = null)
        }

        return try {
            val matcher = PATTERN.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()

            while (matcher.find()) {
                val toolCallId = matcher.group(1) ?: continue
                val functionArguments = matcher.group(2) ?: continue

                // Extract function name from tool_call_id (e.g., "functions.get_weather:0" -> "get_weather")
                val funcName = toolCallId.substringBeforeLast(":").substringAfterLast(".")
                val argsMap = try {
                    jsonToMap(JSONObject(functionArguments))
                } catch (e: Exception) {
                    emptyMap()
                }

                toolCalls.add(
                    ParsedToolCall(
                        id = toolCallId,
                        name = funcName,
                        arguments = argsMap,
                        rawArguments = functionArguments
                    )
                )
            }

            if (toolCalls.isEmpty()) {
                ParseResult(content = response, toolCalls = null)
            } else {
                val content = response.substringBefore(START_TOKENS.first { response.contains(it) }).trim()
                ParseResult(content = content.ifEmpty { null }, toolCalls = toolCalls)
            }
        } catch (e: Exception) {
            ParseResult(content = response, toolCalls = null)
        }
    }

    override fun formatToolCalls(toolCalls: List<ParsedToolCall>): String {
        val sb = StringBuilder("<|tool_calls_section_begin|>")
        for ((i, tc) in toolCalls.withIndex()) {
            sb.append("<|tool_call_begin|>functions.${tc.name}:$i")
            sb.append("<|tool_call_argument_begin|>")
            sb.append(JSONObject(tc.arguments).toString())
            sb.append("<|tool_call_end|>")
        }
        sb.append("<|tool_calls_section_end|>")
        return sb.toString()
    }

    override fun hasToolCall(response: String): Boolean {
        return START_TOKENS.any { response.contains(it) }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.get(key)
        }
        return map
    }
}
