package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Hermes tool call parser.
 * Format: <tool_call>{"name": "func", "arguments": {...}}</tool_call>
 * Based on VLLM's Hermes2ProToolParser.extract_tool_calls()
 */
open class HermesToolCallParser : ToolCallParser() {
    override val supportedModels: List<String> = listOf("hermes")
    companion object {
        private const val _TAG = "HermesParser"
        private val PATTERN = Pattern.compile("<tool_call>\\s*(.*?)\\s*</tool_call>|<tool_call>\\s*(.*)", Pattern.DOTALL)
    }
    override fun parse(response: String): ParseResult {
        if (!response.contains("<tool_call>")) return ParseResult(response, null)
        return try {
            val matcher = PATTERN.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()
            while (matcher.find()) {
                val rawJson = matcher.group(1) ?: matcher.group(2) ?: continue
                if (rawJson.isBlank()) continue
                val tcData = JSONObject(rawJson)
                val name = tcData.optString("name", "")
                if (name.isEmpty()) continue
                val arguments = tcData.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any>()
                val keys = arguments.keys()
                while (keys.hasNext()) { val k = keys.next(); argsMap[k] = arguments.get(k) }
                toolCalls.add(ParsedToolCall(
                    id = "call_${UUID.randomUUID().toString().take(8)}",
                    name = name,
                    arguments = argsMap,
                    rawArguments = arguments.toString()
                ))
            }
            if (toolCalls.isEmpty()) ParseResult(response, null)
            else ParseResult(response.substringBefore("<tool_call>").trim().ifEmpty { null }, toolCalls)
        } catch (e: Exception) { ParseResult(response, null) }
    }
}
