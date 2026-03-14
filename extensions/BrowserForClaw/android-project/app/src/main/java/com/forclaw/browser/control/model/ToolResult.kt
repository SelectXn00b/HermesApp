/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.model

/**
 * 工具执行结果
 *
 * @property success 是否成功
 * @property data 返回数据
 * @property error 错误信息 (仅当 success = false)
 */
data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null
) {
    companion object {
        /**
         * 创建成功结果
         */
        fun success(data: Map<String, Any?> = emptyMap()): ToolResult {
            return ToolResult(success = true, data = data, error = null)
        }

        /**
         * 创建成功结果 (便捷方法)
         */
        fun success(vararg pairs: Pair<String, Any?>): ToolResult {
            return success(mapOf(*pairs))
        }

        /**
         * 创建错误结果
         */
        fun error(message: String): ToolResult {
            return ToolResult(success = false, data = emptyMap(), error = message)
        }
    }

    /**
     * 转换为 JSON 字符串 (用于 Broadcast 传输)
     */
    fun toJson(): String {
        val dataJson = data.entries.joinToString(",") { (key, value) ->
            """"$key":${valueToJson(value)}"""
        }
        return """{"success":$success,"data":{$dataJson}${if (error != null) ""","error":"${error.escape()}"""" else ""}}"""
    }

    private fun valueToJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> """"${value.escape()}""""
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> """"${value.toString().escape()}""""
    }

    private fun String.escape(): String {
        return this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
