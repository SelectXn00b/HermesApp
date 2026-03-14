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
 * 浏览器滚动工具
 *
 * 滚动页面
 *
 * 参数:
 * - direction: String (可选) - 滚动方向: "down", "up", "top", "bottom"，默认 "down"
 * - amount: Int (可选) - 滚动像素数 (仅对 "down" 和 "up" 有效)
 *
 * 返回:
 * - direction: String - 滚动方向
 * - scrolled: Boolean - 是否成功滚动
 */
class BrowserScrollTool : BrowserTool {
    override val name = "browser_scroll"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 获取参数
        val direction = (args["direction"] as? String)?.lowercase() ?: "down"
        val amount = (args["amount"] as? Number)?.toInt()

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val script = when (direction) {
            "down" -> {
                val scrollAmount = amount ?: "window.innerHeight"
                """
                    (function() {
                        try {
                            window.scrollBy(0, $scrollAmount);
                            return true;
                        } catch (e) {
                            return false;
                        }
                    })()
                """
            }
            "up" -> {
                val scrollAmount = amount ?: "window.innerHeight"
                """
                    (function() {
                        try {
                            window.scrollBy(0, -$scrollAmount);
                            return true;
                        } catch (e) {
                            return false;
                        }
                    })()
                """
            }
            "top" -> {
                """
                    (function() {
                        try {
                            window.scrollTo(0, 0);
                            return true;
                        } catch (e) {
                            return false;
                        }
                    })()
                """
            }
            "bottom" -> {
                """
                    (function() {
                        try {
                            window.scrollTo(0, document.body.scrollHeight);
                            return true;
                        } catch (e) {
                            return false;
                        }
                    })()
                """
            }
            else -> return ToolResult.error("Invalid direction: $direction (must be 'down', 'up', 'top', or 'bottom')")
        }.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val scrolled = result?.trim() == "true"

            // 5. 返回结果
            return if (scrolled) {
                ToolResult.success(
                    "direction" to direction,
                    "scrolled" to true
                )
            } else {
                ToolResult.error("Scroll failed")
            }
        } catch (e: Exception) {
            return ToolResult.error("Scroll failed: ${e.message}")
        }
    }
}
