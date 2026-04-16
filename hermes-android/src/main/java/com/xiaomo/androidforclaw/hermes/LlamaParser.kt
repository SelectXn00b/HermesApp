package com.xiaomo.androidforclaw.hermes

import android.util.Log
import com.xiaomo.androidforclaw.hermes.ParseResult
import com.xiaomo.androidforclaw.hermes.ParsedToolCall
import com.xiaomo.androidforclaw.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Llama 3.x / 4 tool call parser.
 *
 * Finds JSON objects containing "name" + ("arguments" or "parameters") keys.
 * May be preceded by <|python_tag|> token.
 * Based on VLLM's Llama3JsonToolParser.extract_tool_calls()
 */
class LlamaToolCallParser : ToolCallParser() {

    override val supportedModels: List<String> = listOf("llama3_json", "llama4_json")

    companion object {
        private const val TAG = "LlamaParser"
        private const val BOT_TOKEN = "<|python_tag|>"
        private val JSON_START = Regex("\\{")
    }

    override fun parse(response: String): ParseResult {
        if (BOT_TOKEN !in response && "{" !in response) {
            return ParseResult(content = response, toolCalls = null)
        }

        return try {
            val toolCalls = mutableListOf<ParsedToolCall>()
            var endIndex = -1

            for (match in JSON_START.findAll(response)) {
                val start = match.range.first
                if (start <= endIndex) continue

                try {
                    val subStr = response.substring(start)
                    val (obj, consumed) = parseJsonObject(subStr)
                    endIndex = start + consumed

                    if (obj == null) continue

                    // Must have "name" and either "arguments" or "parameters"
                    val name = obj.optString("name", "")
                    if (name.isEmpty()) continue

                    val args = obj.optJSONObject("arguments") ?: obj.optJSONObject("parameters")
                    val argsMap = if (args != null) jsonToMap(args) else emptyMap()
                    val argsStr = args?.toString() ?: "{}"

                    toolCalls.add(
                        ParsedToolCall(
                            id = "call_${UUID.randomUUID().toString().take(8)}",
                            name = name,
                            arguments = argsMap,
                            rawArguments = argsStr
                        )
                    )
                } catch (e: Exception) {
                    // Not valid JSON, skip
                }
            }

            if (toolCalls.isEmpty()) {
                ParseResult(content = response, toolCalls = null)
            } else {
                val content = response.substringBefore("{").trim()
                ParseResult(content = content.ifEmpty { null }, toolCalls = toolCalls)
            }
        } catch (e: Exception) {
            ParseResult(content = response, toolCalls = null)
        }
    }

    override fun formatToolCalls(toolCalls: List<ParsedToolCall>): String {
        val sb = StringBuilder(BOT_TOKEN)
        for (tc in toolCalls) {
            sb.append(JSONObject().put("name", tc.name).put("arguments", JSONObject(tc.arguments)))
            sb.append(";")
        }
        return sb.toString()
    }

    override fun hasToolCall(response: String): Boolean {
        return BOT_TOKEN in response || "{" in response
    }

    /**
     * Parse a JSON object from the start of a string.
     * Returns the parsed object and the number of characters consumed.
     */
    private fun parseJsonObject(text: String): Pair<JSONObject?, Int> {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in text.indices) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        val jsonStr = text.substring(0, i + 1)
                        return try {
                            Pair(JSONObject(jsonStr), i + 1)
                        } catch (e: Exception) {
                            Pair(null, 0)
                        }
                    }
                }
            }
        }
        return Pair(null, 0)
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
