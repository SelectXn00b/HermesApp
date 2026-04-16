package com.xiaomo.androidforclaw.hermes

import android.util.Log
import com.xiaomo.androidforclaw.hermes.ParseResult
import com.xiaomo.androidforclaw.hermes.ParsedToolCall
import com.xiaomo.androidforclaw.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * GLM 4.5 tool call parser.
 * Uses <tool_call> tags with <arg_key>/<arg_value> pairs.
 * Based on VLLM's Glm4MoeModelToolParser.extract_tool_calls()
 */
open class Glm45ToolCallParser : ToolCallParser() {
    override val supportedModels: List<String> = listOf("glm45")
    companion object {
        private const val TAG = "Glm45Parser"
        internal val FUNC_CALL_REGEX = Pattern.compile("<tool_call>.*?</tool_call>", Pattern.DOTALL)
        internal val FUNC_DETAIL_REGEX = Pattern.compile("<tool_call>([^\\n]*)\\n(.*)</tool_call>", Pattern.DOTALL)
        internal val FUNC_ARG_REGEX = Pattern.compile("<arg_key>(.*?)</arg_key>\\s*<arg_value>(.*?)</arg_value>", Pattern.DOTALL)
    }
    protected fun deserializeValue(value: String): Any {
        try { return JSONObject(value) } catch (_: Exception) {}
        try { return java.math.BigDecimal(value) } catch (_: Exception) {}
        if (value == "true") return true
        if (value == "false") return false
        if (value == "null") return JSONObject.NULL
        return value
    }
    override fun parse(response: String): ParseResult {
        if (!response.contains("<tool_call>")) return ParseResult(response, null)
        return try {
            val funcMatcher = FUNC_CALL_REGEX.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()
            while (funcMatcher.find()) {
                val funcCall = funcMatcher.group()
                val detailMatcher = FUNC_DETAIL_REGEX.matcher(funcCall)
                if (!detailMatcher.find()) continue
                val functionName = detailMatcher.group(1).trim()
                val argBody = detailMatcher.group(2) ?: ""
                val arguments = mutableMapOf<String, Any>()
                val argMatcher = FUNC_ARG_REGEX.matcher(argBody)
                while (argMatcher.find()) {
                    arguments[argMatcher.group(1).trim()] = deserializeValue(argMatcher.group(2))
                }
                toolCalls.add(ParsedToolCall(
                    id = "call_${UUID.randomUUID().toString().take(8)}",
                    name = functionName,
                    arguments = arguments,
                    rawArguments = JSONObject(arguments as Map<*, *>).toString()
                ))
            }
            if (toolCalls.isEmpty()) ParseResult(response, null)
            else ParseResult(response.substringBefore("<tool_call>").trim().ifEmpty { null }, toolCalls)
        } catch (e: Exception) { ParseResult(response, null) }
    }
    override fun formatToolCalls(toolCalls: List<ParsedToolCall>): String {
        val sb = StringBuilder()
        for (tc in toolCalls) {
            sb.append("<tool_call>${tc.name}\n")
            for ((k, v) in tc.arguments) sb.append("<arg_key>$k</arg_key><arg_value>$v</arg_value>")
            sb.append("</tool_call>")
        }
        return sb.toString()
    }
    override fun hasToolCall(response: String): Boolean = response.contains("<tool_call>")
}
