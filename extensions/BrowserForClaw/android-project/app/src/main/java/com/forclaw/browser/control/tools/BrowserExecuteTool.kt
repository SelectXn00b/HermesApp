/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 浏览器执行 JavaScript 工具
 *
 * 执行自定义 JavaScript 代码
 *
 * 参数:
 * - script: String (必需) - JavaScript 代码
 * - selector: String (可选) - 在特定元素上执行
 *
 * 返回:
 * - result: String - 执行结果
 * - script: String - 执行的脚本
 */
class BrowserExecuteTool : BrowserTool {
    override val name = "browser_execute"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val script = args["script"] as? String
            ?: return ToolResult.error("Missing required parameter: script")

        if (script.isBlank()) {
            return ToolResult.error("Parameter 'script' cannot be empty")
        }

        val selector = args["selector"] as? String

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val fullScript = if (selector != null) {
            // 在特定元素上执行
            val escapedSelector = selector.replace("'", "\\'")
            """
                (function() {
                    const el = document.querySelector('$escapedSelector');
                    if (!el) return null;
                    return (function(element) {
                        $script
                    })(el);
                })()
            """.trimIndent()
        } else {
            // 在页面上下文执行
            """
                (function() {
                    $script
                })()
            """.trimIndent()
        }

        // 4. 执行 JavaScript (必须在主线程)
        try {
            val rawResult = withContext(Dispatchers.Main) {
                BrowserManager.evaluateJavascript(fullScript)
            }

            // 解析结果
            val result = rawResult?.let {
                // evaluateJavascript 返回 JSON 编码的字符串
                if (it == "null" || it == "undefined") {
                    null
                } else {
                    // 尝试去掉 JSON 字符串的引号
                    it.trim().removeSurrounding("\"")
                }
            }

            // 5. 返回结果
            return ToolResult.success(
                "result" to result,
                "script" to script,
                "selector" to selector
            )
        } catch (e: Exception) {
            return ToolResult.error("Execute failed: ${e.message}")
        }
    }
}
