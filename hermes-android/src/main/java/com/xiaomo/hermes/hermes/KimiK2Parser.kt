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
                    val obj = JSONObject(functionArguments)
                    val m = mutableMapOf<String, Any>()
                    val keys = obj.keys()
                    while (keys.hasNext()) { val k = keys.next(); m[k] = obj.get(k) }
                    m as Map<String, Any>
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
}
