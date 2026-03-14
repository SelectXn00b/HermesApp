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
 * 浏览器输入工具
 *
 * 在输入框中输入文本
 *
 * 参数:
 * - selector: String (必需) - CSS 选择器
 * - text: String (必需) - 要输入的文本
 * - submit: Boolean (可选) - 是否提交表单，默认 false
 *
 * 返回:
 * - selector: String - 使用的选择器
 * - text: String - 输入的文本
 * - submitted: Boolean - 是否提交了表单
 */
class BrowserTypeTool : BrowserTool {
    override val name = "browser_type"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val selector = args["selector"] as? String
            ?: return ToolResult.error("Missing required parameter: selector")
        val text = args["text"] as? String
            ?: return ToolResult.error("Missing required parameter: text")
        val submit = (args["submit"] as? Boolean) ?: false

        if (selector.isBlank()) {
            return ToolResult.error("Parameter 'selector' cannot be empty")
        }

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val escapedSelector = selector.replace("'", "\\'")
        val escapedText = text.replace("'", "\\'")
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val script = """
            (function() {
                try {
                    const el = document.querySelector('$escapedSelector');
                    if (!el) return false;

                    // 设置 value
                    el.value = '$escapedText';

                    // 触发 input 事件 (模拟用户输入)
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));

                    ${if (submit) {
                        """
                        // 提交表单
                        const form = el.closest('form');
                        if (form) {
                            form.submit();
                        } else {
                            // 如果没有表单，模拟 Enter 键
                            const event = new KeyboardEvent('keypress', {
                                key: 'Enter',
                                keyCode: 13,
                                which: 13,
                                bubbles: true
                            });
                            el.dispatchEvent(event);
                        }
                        """
                    } else ""
                    }

                    return true;
                } catch (e) {
                    return false;
                }
            })()
        """.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val typed = result?.trim() == "true"

            // 5. 返回结果
            return if (typed) {
                ToolResult.success(
                    "selector" to selector,
                    "text" to text,
                    "submitted" to submit
                )
            } else {
                ToolResult.error("Element not found or not typable: $selector")
            }
        } catch (e: Exception) {
            return ToolResult.error("Type failed: ${e.message}")
        }
    }
}
