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
 * 浏览器悬停工具
 *
 * 触发鼠标悬停事件
 *
 * 参数:
 * - selector: String (必需) - CSS 选择器
 *
 * 返回:
 * - selector: String - 使用的选择器
 * - hovered: Boolean - 是否成功悬停
 */
class BrowserHoverTool : BrowserTool {
    override val name = "browser_hover"

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
                    if (!el) return false;

                    // 滚动到元素可见
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });

                    // 触发 mouseenter 事件
                    const mouseenterEvent = new MouseEvent('mouseenter', {
                        bubbles: true,
                        cancelable: true,
                        view: window
                    });
                    el.dispatchEvent(mouseenterEvent);

                    // 触发 mouseover 事件
                    const mouseoverEvent = new MouseEvent('mouseover', {
                        bubbles: true,
                        cancelable: true,
                        view: window
                    });
                    el.dispatchEvent(mouseoverEvent);

                    // 触发 mousemove 事件
                    const mousemoveEvent = new MouseEvent('mousemove', {
                        bubbles: true,
                        cancelable: true,
                        view: window
                    });
                    el.dispatchEvent(mousemoveEvent);

                    return true;
                } catch (e) {
                    return false;
                }
            })()
        """.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val hovered = result?.trim() == "true"

            // 5. 返回结果
            return if (hovered) {
                ToolResult.success(
                    "selector" to selector,
                    "hovered" to true
                )
            } else {
                ToolResult.error("Element not found or hover failed: $selector")
            }
        } catch (e: Exception) {
            return ToolResult.error("Hover failed: ${e.message}")
        }
    }
}
