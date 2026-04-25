package com.xiaomo.hermes.hermes

import com.xiaomo.hermes.hermes.ParseResult
import com.xiaomo.hermes.hermes.ToolCallParser

/**
 * Qwen 2.5 tool call parser.
 *
 * Uses the same <tool_call> format as Hermes.
 * Registered as a separate parser name for clarity when using --tool-parser=qwen.
 */
class QwenToolCallParser : HermesToolCallParser() {

    override val supportedModels: List<String> = listOf("qwen")
    // Identical format -- inherits everything from Hermes
}
