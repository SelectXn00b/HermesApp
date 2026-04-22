package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONObject
import java.util.UUID

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
        private const val _TAG = "LlamaParser"
        private const val BOT_TOKEN = "<|python_tag|>"
        private val JSON_START = Regex("\\{")

        // Brace-counting JSON object extractor. Returns (obj, consumedChars) or (null, 0).
        private val _rawDecode: (String) -> Pair<JSONObject?, Int> = lambda@ { text ->
            var depth = 0
            var inString = false
            var escaped = false
            var result: Pair<JSONObject?, Int> = Pair(null, 0)

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
                            result = try { Pair(JSONObject(jsonStr), i + 1) }
                            catch (e: Exception) { Pair(null, 0) }
                            return@lambda result
                        }
                    }
                }
            }
            result
        }
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
                    val (obj, consumed) = _rawDecode(subStr)
                    endIndex = start + consumed

                    if (obj == null) continue

                    val name = obj.optString("name", "")
                    if (name.isEmpty()) continue

                    val args = obj.optJSONObject("arguments") ?: obj.optJSONObject("parameters")
                    val argsMap = if (args != null) {
                        val m = mutableMapOf<String, Any>()
                        val keys = args.keys()
                        while (keys.hasNext()) { val k = keys.next(); m[k] = args.get(k) }
                        m as Map<String, Any>
                    } else emptyMap()
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
}
