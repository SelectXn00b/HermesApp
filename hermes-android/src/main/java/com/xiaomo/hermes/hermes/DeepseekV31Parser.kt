package com.xiaomo.hermes.hermes

import com.xiaomo.hermes.hermes.ParsedToolCall
import com.xiaomo.hermes.hermes.ToolCallParser
import com.xiaomo.hermes.hermes.ParseResult
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

class DeepSeekV31ToolCallParser : ToolCallParser() {
    override val supportedModels: List<String> = listOf("deepseek_v3_1", "deepseek_v31")
    companion object {
        private val PATTERN = Pattern.compile("\u2764\ufe0f(.*?)\u2764\ufe0f", Pattern.DOTALL)
    }
    override fun parse(response: String): ParseResult {
        val calls = mutableListOf<ParsedToolCall>()
        val m = PATTERN.matcher(response)
        while (m.find()) {
            val c = m.group(1) ?: continue
            calls.add(ParsedToolCall(id="call_${UUID.randomUUID().toString().take(8)}", name=c.trim(), arguments=emptyMap()))
        }
        return ParseResult(content=response, toolCalls=calls)
    }
}
