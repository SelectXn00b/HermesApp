package com.xiaomo.hermes.hermes

import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Qwen3-Coder tool call parser.
 * Format: <tool_call><function=name><parameter=k>v</parameter></function></tool_call>
 * Based on VLLM's Qwen3CoderToolParser.extract_tool_calls()
 */
class Qwen3CoderToolCallParser : ToolCallParser() {
    override val supportedModels: List<String> = listOf("qwen3_coder")
    companion object {
        private const val _TAG = "Qwen3CoderParser"
        private val TOOL_CALL_PATTERN = Pattern.compile("<tool_call>\\s*(.*?)\\s*</tool_call>", Pattern.DOTALL)
        private val FUNC_PATTERN = Pattern.compile("<function=(.*?)>\\s*(.*?)\\s*</function>", Pattern.DOTALL)
        private val PARAM_PATTERN = Pattern.compile("<parameter=(.*?)>(.*?)</parameter>", Pattern.DOTALL)
    }
    private fun tryConvertValue(value: String): Any {
        val s = value.trim()
        if (s.equals("null", true)) return JSONObject.NULL
        try { return JSONObject(s) } catch (_: Exception) {}
        try { return java.math.BigDecimal(s) } catch (_: Exception) {}
        if (s.equals("true", true)) return true
        if (s.equals("false", true)) return false
        return s
    }
    override fun parse(response: String): ParseResult {
        if (!response.contains("<tool_call>")) return ParseResult(response, null)
        return try {
            val toolCalls = mutableListOf<ParsedToolCall>()
            val tcMatcher = TOOL_CALL_PATTERN.matcher(response)
            while (tcMatcher.find()) {
                val tcBody = tcMatcher.group(1) ?: continue
                val funcMatcher = FUNC_PATTERN.matcher(tcBody)
                while (funcMatcher.find()) {
                    val funcName = funcMatcher.group(1) ?: continue
                    val funcBody = funcMatcher.group(2) ?: ""
                    val arguments = mutableMapOf<String, Any>()
                    val paramMatcher = PARAM_PATTERN.matcher(funcBody)
                    while (paramMatcher.find()) {
                        arguments[paramMatcher.group(1)] = tryConvertValue(paramMatcher.group(2))
                    }
                    toolCalls.add(ParsedToolCall(
                        id = "call_${UUID.randomUUID().toString().take(8)}",
                        name = funcName,
                        arguments = arguments,
                        rawArguments = JSONObject(arguments as Map<*, *>).toString()
                    ))
                }
            }
            if (toolCalls.isEmpty()) ParseResult(response, null)
            else ParseResult(response.substringBefore("<tool_call>").trim().ifEmpty { null }, toolCalls)
        } catch (e: Exception) { ParseResult(response, null) }
    }
    override fun formatToolCalls(toolCalls: List<ParsedToolCall>): String {
        val sb = StringBuilder()
        for (tc in toolCalls) {
            sb.append("<tool_call><function=${tc.name}>")
            for ((k, v) in tc.arguments) sb.append("<parameter=$k>$v</parameter>")
            sb.append("</function></tool_call>")
        }
        return sb.toString()
    }
    override fun hasToolCall(response: String): Boolean = response.contains("<tool_call>")


    /**
     * Parse a single <function=name>...</function> block into a tool call.
     * Extracts function name from before '>' and parameters from <parameter=key>value</parameter> tags.
     * Returns null if the function string cannot be parsed.
     */
    fun _parseFunctionCall(functionStr: String): ParsedToolCall? {
        return try {
            val gtIdx = functionStr.indexOf('>')
            if (gtIdx < 0) return null
            val funcName = functionStr.substring(0, gtIdx).trim()
            val paramsStr = functionStr.substring(gtIdx + 1)

            val arguments = mutableMapOf<String, Any>()
            val paramMatcher = PARAM_PATTERN.matcher(paramsStr)
            while (paramMatcher.find()) {
                val paramName = paramMatcher.group(1) ?: continue
                var paramValue = paramMatcher.group(2) ?: ""
                // Clean up whitespace
                if (paramValue.startsWith("\n")) paramValue = paramValue.substring(1)
                if (paramValue.endsWith("\n")) paramValue = paramValue.dropLast(1)
                arguments[paramName] = tryConvertValue(paramValue)
            }

            ParsedToolCall(
                id = "call_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                name = funcName,
                arguments = arguments,
                rawArguments = JSONObject(arguments as Map<*, *>).toString()
            )
        } catch (_: Exception) {
            null
        }
    }
}
