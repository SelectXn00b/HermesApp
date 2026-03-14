/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult

/**
 * 浏览器点击工具
 *
 * 点击页面上的元素
 *
 * 参数:
 * - selector: String (必需) - CSS 选择器
 *
 * 返回:
 * - selector: String - 使用的选择器
 * - clicked: Boolean - 是否成功点击
 */
class BrowserClickTool : BrowserTool {
    override val name = "browser_click"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val selector = args["selector"] as? String
            ?: return ToolResult.error("Missing required parameter: selector")

        if (selector.isBlank()) {
            return ToolResult.error("Parameter 'selector' cannot be empty")
        }

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val escapedSelector = selector.replace("'", "\\'")
        val script = """
            (function() {
                try {
                    const el = document.querySelector('$escapedSelector');
                    if (el) {
                        el.click();
                        return true;
                    }
                    return false;
                } catch (e) {
                    return false;
                }
            })()
        """.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val clicked = result?.trim()?.let {
                // evaluateJavascript 返回的是字符串 "true" 或 "false"
                it == "true"
            } ?: false

            // 5. 返回结果
            return if (clicked) {
                ToolResult.success(
                    "selector" to selector,
                    "clicked" to true
                )
            } else {
                ToolResult.error("Element not found or not clickable: $selector")
            }
        } catch (e: Exception) {
            return ToolResult.error("Click failed: ${e.message}")
        }
    }
}
