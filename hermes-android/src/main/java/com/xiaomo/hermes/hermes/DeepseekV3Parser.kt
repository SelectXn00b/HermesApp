package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import com.xiaomo.hermes.hermes.ParseResult
import java.util.UUID
import java.util.regex.Pattern

/**
 * DeepSeek V3 tool call parser.
 *
 * Format uses special unicode tokens:
 *     <｜tool▁calls▁begin｜>
 *     <｜tool▁call▁begin｜>type<｜tool▁sep｜>function_name
 *     ```json
 *     {"arg": "value"}
 *     ```
 *     <｜tool▁call▁end｜>
 *     <｜tool▁calls▁end｜>
 *
 * 1:1 对齐 hermes/environments/tool_call_parsers/deepseek_v3_parser.py
 */
class DeepSeekV3ToolCallParser : ToolCallParser() {
    override val supportedModels: List<String> = listOf("deepseek_v3")

    companion object {
        private const val _TAG = "DeepseekV3Parser"
        private val PATTERN = Pattern.compile(
            "<｜tool▁call▁begin｜>(?<type>.*?)<｜tool▁sep｜>(?<function_name>.*?)\\s*```json\\s*(?<function_arguments>.*?)\\s*```\\s*<｜tool▁call▁end｜>",
            Pattern.DOTALL
        )
    }

    override fun parse(response: String): ParseResult {
        val startToken = "<｜tool▁calls▁begin｜>"
        if (!response.contains(startToken)) return ParseResult(response, null)

        return try {
            val matcher = PATTERN.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()
            while (matcher.find()) {
                val funcName = matcher.group("function_name")?.trim() ?: continue
                val funcArgs = matcher.group("function_arguments")?.trim() ?: ""
                toolCalls.add(
                    ParsedToolCall(
                        id = "call_${UUID.randomUUID().toString().take(8)}",
                        type = "function",
                        name = funcName,
                        arguments = emptyMap(),
                        rawArguments = funcArgs
                    )
                )
            }

            if (toolCalls.isNotEmpty()) {
                val contentIndex = response.indexOf(startToken)
                val content = response.substring(0, contentIndex).trim()
                ParseResult(if (content.isNotEmpty()) content else null, toolCalls)
            } else {
                ParseResult(response, null)
            }
        } catch (e: Exception) {
            Log.e(_TAG, "Error parsing DeepSeek V3 tool calls: ${e.message}")
            ParseResult(response, null)
        }
    }
}
