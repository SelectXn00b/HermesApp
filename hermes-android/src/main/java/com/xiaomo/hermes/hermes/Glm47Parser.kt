package com.xiaomo.hermes.hermes

import java.util.regex.Pattern

/**
 * GLM 4.7 tool call parser.
 * Extends GLM 4.5 with updated regex patterns. Uses the parent `parse`
 * implementation — only the regex fields change.
 */
class Glm47ToolCallParser : Glm45ToolCallParser() {
    override val supportedModels: List<String> = listOf("glm47")

    override val FUNC_DETAIL_REGEX: Pattern =
        Pattern.compile("<tool_call>(.*?)(<arg_key>.*?)?</tool_call>", Pattern.DOTALL)
    override val FUNC_ARG_REGEX: Pattern =
        Pattern.compile("<arg_key>(.*?)</arg_key>(?:\\n|\\s)*<arg_value>(.*?)</arg_value>", Pattern.DOTALL)
}
