package com.xiaomo.hermes.hermes

import android.util.Log
import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

/**
 * Mistral tool call parser.
 *
 * Supports two formats depending on tokenizer version:
 * - Pre-v11: content[TOOL_CALLS] [{"name": ..., "arguments": {...}}, ...]
 * - v11+:    content[TOOL_CALLS]tool_name1{"arg": "val"}[TOOL_CALLS]tool_name2{"arg": "val"}
 *
 * Based on VLLM's MistralToolParser.extract_tool_calls()
 * The [TOOL_CALLS] token is the bot_token used by Mistral models.
 */
class MistralToolCallParser : ToolCallParser() {

    override val supportedModels: List<String> = listOf("mistral")

    companion object {
        private const val _TAG = "MistralParser"
        private const val BOT_TOKEN = "[TOOL_CALLS]"

        /** Mistral tool call IDs are 9-char alphanumeric strings. */
        private fun _generateMistralId(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..9).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }

        private val _findMatchingBrace: (String, Int) -> Int = { s, start ->
            var depth = 0
            var inString = false
            var escape = false
            var result = -1
            loop@ for (i in start until s.length) {
                val c = s[i]
                if (escape) { escape = false; continue@loop }
                if (c == '\\') { escape = true; continue@loop }
                if (c == '"') { inString = !inString; continue@loop }
                if (inString) continue@loop
                if (c == '{') depth++
                if (c == '}') { depth--; if (depth == 0) { result = i; break@loop } }
            }
            result
        }

        private val _parseArgsToMap: (String) -> Map<String, Any> = { argsStr ->
            try {
                val obj = JSONObject(argsStr)
                val map = mutableMapOf<String, Any>()
                obj.keys().forEach { key -> map[key] = obj.get(key) }
                map as Map<String, Any>
            } catch (_: JSONException) {
                emptyMap()
            }
        }
    }

    override fun parse(response: String): ParseResult {
        if (!response.contains(BOT_TOKEN)) {
            return ParseResult(response, null)
        }

        try {
            val parts = response.split(BOT_TOKEN)
            val content = parts[0].trim()
            val rawToolCalls = parts.drop(1)

            // Detect format: if the first raw part starts with '[', it's pre-v11
            val firstRaw = rawToolCalls.firstOrNull()?.trim() ?: ""
            val isPreV11 = firstRaw.startsWith("[") || firstRaw.startsWith("{")

            val toolCalls = mutableListOf<ParsedToolCall>()

            if (!isPreV11) {
                // v11+ format: [TOOL_CALLS]tool_name{args}[TOOL_CALLS]tool_name2{args2}
                for (raw in rawToolCalls) {
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty() || !trimmed.contains("{")) continue

                    val braceIdx = trimmed.indexOf("{")
                    val toolName = trimmed.substring(0, braceIdx).trim()
                    var argsStr = trimmed.substring(braceIdx)

                    try {
                        val parsedArgs = JSONObject(argsStr)
                        argsStr = parsedArgs.toString()
                    } catch (_: JSONException) {
                        // Keep raw if parsing fails
                    }

                    toolCalls.add(
                        ParsedToolCall(
                            id = _generateMistralId(),
                            name = toolName,
                            arguments = _parseArgsToMap(argsStr),
                            rawArguments = argsStr
                        )
                    )
                }
            } else {
                // Pre-v11 format: [TOOL_CALLS] [{"name": ..., "arguments": {...}}]
                try {
                    val jsonArray: JSONArray
                    val trimmed = firstRaw.trim()
                    if (trimmed.startsWith("[")) {
                        jsonArray = JSONArray(trimmed)
                    } else {
                        jsonArray = JSONArray().put(JSONObject(trimmed))
                    }

                    for (i in 0 until jsonArray.length()) {
                        val tc = jsonArray.getJSONObject(i)
                        if (!tc.has("name")) continue
                        val name = tc.getString("name")
                        val args = tc.opt("arguments")
                        val argsStr = when (args) {
                            is JSONObject -> args.toString()
                            is String -> args
                            is Map<*, *> -> JSONObject(args).toString()
                            else -> "{}"
                        }

                        toolCalls.add(
                            ParsedToolCall(
                                id = _generateMistralId(),
                                name = name,
                                arguments = _parseArgsToMap(argsStr),
                                rawArguments = argsStr
                            )
                        )
                    }
                } catch (_: JSONException) {
                    // Fallback: extract JSON objects using a simple brace counter
                    var idx = 0
                    while (idx < firstRaw.length) {
                        val start = firstRaw.indexOf('{', idx)
                        if (start < 0) break
                        try {
                            val end = _findMatchingBrace(firstRaw, start)
                            if (end < 0) break
                            val objStr = firstRaw.substring(start, end + 1)
                            val obj = JSONObject(objStr)
                            if (obj.has("name")) {
                                val name = obj.getString("name")
                                val args = obj.opt("arguments")
                                val argsStr = when (args) {
                                    is JSONObject -> args.toString()
                                    is String -> args
                                    else -> "{}"
                                }
                                toolCalls.add(
                                    ParsedToolCall(
                                        id = _generateMistralId(),
                                        name = name,
                                        arguments = _parseArgsToMap(argsStr),
                                        rawArguments = argsStr
                                    )
                                )
                            }
                            idx = end + 1
                        } catch (_: JSONException) {
                            idx = start + 1
                        }
                    }
                }
            }

            if (toolCalls.isEmpty()) {
                return ParseResult(response, null)
            }

            return ParseResult(if (content.isNotEmpty()) content else null, toolCalls)
        } catch (e: Exception) {
            Log.d(_TAG, "parse error: ${e.message}")
            return ParseResult(response, null)
        }
    }
}
