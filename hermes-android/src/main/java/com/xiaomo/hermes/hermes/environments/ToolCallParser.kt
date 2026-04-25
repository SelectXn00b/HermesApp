package com.xiaomo.hermes.hermes

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

/**
 * Tool Call Parser - 模型 tool call 格式解析器
 * 1:1 对齐 hermes/environments/tool_call_parsers/
 *
 * 支持 12 种模型的非标准 tool call 格式解析：
 * - deepseek_v3, deepseek_v3_1
 * - glm45, glm47
 * - qwen, qwen3_coder
 * - kimi_k2, longcat, llama, mistral
 * - hermes (标准格式)
 */
abstract class ToolCallParser {

    /** 支持的模型标识 */
    abstract val supportedModels: List<String>

    /**
     * 从 LLM 响应中解析 tool calls
     *
     * @param response LLM 原始响应文本
     * @return ParseResult(content, toolCalls)
     */
    abstract fun parse(response: String): ParseResult

    /**
     * 便捷方法：从 LLM 响应中解析 tool calls (旧接口兼容)
     */
    fun parseToolCalls(response: String): List<ParsedToolCall> {
        return parse(response).toolCalls ?: emptyList()
    }
}

/**
 * 解析结果
 */
data class ParseResult(
    val content: String?,
    val toolCalls: List<ParsedToolCall>?
)

/**
 * 解析出的 tool call
 */
data class ParsedToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>,
    val rawArguments: String? = null,  // 原始参数字符串
    val type: String = "function"
)

/**
 * Parser 注册表
 * 对应 hermes/environments/tool_call_parsers/__init__.py
 */
class ToolCallParserRegistry {

    private val parsers: MutableMap<String, ToolCallParser> = mutableMapOf()

    /**
     * 注册 parser
     */
    fun register(parser: ToolCallParser) {
        for (model in parser.supportedModels) {
            parsers[model] = parser
        }
    }

    /**
     * 获取模型对应的 parser
     */
    fun getParser(modelName: String): ToolCallParser? {
        return parsers[modelName]
    }

    /**
     * 根据模型名自动匹配 parser
     */
    fun findParser(modelName: String): ToolCallParser? {
        // 精确匹配
        parsers[modelName]?.let { return it }

        // 模糊匹配
        for ((key, parser) in parsers) {
            if (modelName.contains(key, ignoreCase = true)) {
                return parser
            }
        }

        // 默认返回 null (模型不支持 tool call 解析)
        return null
    }

    /**
     * 列出所有已注册的 parser 名称
     */
    fun listParsers(): List<String> = parsers.keys.sorted()

    companion object {
        /** 内置 parsers */
        val BUILT_IN_PARSERS: List<ToolCallParser> = listOf(
            HermesToolCallParser(),
            LongcatToolCallParser(),
            MistralToolCallParser(),
            LlamaToolCallParser(),
            QwenToolCallParser(),
            Qwen3CoderToolCallParser(),
            KimiK2ToolCallParser(),
            Glm45ToolCallParser(),
            Glm47ToolCallParser())

        /** 创建并填充内置 parsers 的注册表 */
        fun createDefault(): ToolCallParserRegistry {
            val registry = ToolCallParserRegistry()
            BUILT_IN_PARSERS.forEach { registry.register(it) }
            return registry
        }
    }
}
