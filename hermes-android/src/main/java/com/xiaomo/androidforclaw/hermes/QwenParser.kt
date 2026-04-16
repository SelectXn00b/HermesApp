package com.xiaomo.androidforclaw.hermes

import com.xiaomo.androidforclaw.hermes.ParseResult
import com.xiaomo.androidforclaw.hermes.ToolCallParser

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
