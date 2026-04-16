package com.xiaomo.androidforclaw.hermes

import com.xiaomo.androidforclaw.hermes.ParseResult
import com.xiaomo.androidforclaw.hermes.ToolCallParser
import java.util.regex.Pattern

/**
 * GLM 4.7 tool call parser.
 * Extends GLM 4.5 with updated regex patterns.
 */
class Glm47ToolCallParser : Glm45ToolCallParser() {
    override val supportedModels: List<String> = listOf("glm47")
    companion object {
        private const val TAG = "Glm47Parser"
        private val GLM47_FUNC_DETAIL_REGEX = Pattern.compile("<tool_call>(.*?)(<arg_key>.*?)?</tool_call>", Pattern.DOTALL)
        private val GLM47_FUNC_ARG_REGEX = Pattern.compile("<arg_key>(.*?)</arg_key>(?:\\n|\\s)*<arg_value>(.*?)</arg_value>", Pattern.DOTALL)
    }
    override fun parse(response: String): ParseResult {
        if (!response.contains("<tool_call>")) return ParseResult(response, null)
        return try {
            val funcMatcher = FUNC_CALL_REGEX.matcher(response)
            val toolCalls = mutableListOf<com.xiaomo.androidforclaw.hermes.ParsedToolCall>()
            while (funcMatcher.find()) {
                val funcCall = funcMatcher.group()
                val detailMatcher = GLM47_FUNC_DETAIL_REGEX.matcher(funcCall)
                if (!detailMatcher.find()) continue
                val functionName = detailMatcher.group(1).trim()
                val argBody = detailMatcher.group(2) ?: ""
                val arguments = mutableMapOf<String, Any>()
                val argMatcher = GLM47_FUNC_ARG_REGEX.matcher(argBody)
                while (argMatcher.find()) {
                    arguments[argMatcher.group(1).trim()] = deserializeValue(argMatcher.group(2))
                }
                toolCalls.add(com.xiaomo.androidforclaw.hermes.ParsedToolCall(
                    id = "call_${java.util.UUID.randomUUID().toString().take(8)}",
                    name = functionName,
                    arguments = arguments,
                    rawArguments = org.json.JSONObject(arguments as Map<*, *>).toString()
                ))
            }
            if (toolCalls.isEmpty()) ParseResult(response, null)
            else ParseResult(response.substringBefore("<tool_call>").trim().ifEmpty { null }, toolCalls)
        } catch (e: Exception) { ParseResult(response, null) }
    }
}
