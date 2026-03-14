/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult
import kotlinx.coroutines.delay

/**
 * 浏览器按键工具
 *
 * 模拟键盘按键
 *
 * 参数:
 * - key: String (必需) - 按键名称 (如 "Enter", "Tab", "Escape", "ArrowDown")
 * - delayMs: Int (可选) - 按键后延迟毫秒数，默认 100ms
 *
 * 返回:
 * - key: String - 按下的键
 * - pressed: Boolean - 是否成功
 */
class BrowserPressTool : BrowserTool {
    override val name = "browser_press"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val key = args["key"] as? String
            ?: return ToolResult.error("Missing required parameter: key")

        if (key.isBlank()) {
            return ToolResult.error("Parameter 'key' cannot be empty")
        }

        val delayMs = (args["delayMs"] as? Number)?.toLong() ?: 100L

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val escapedKey = key.replace("'", "\\'")
        val script = """
            (function() {
                try {
                    // 获取当前焦点元素，如果没有则使用 body
                    const target = document.activeElement || document.body;

                    // 触发 keydown 事件
                    const keydownEvent = new KeyboardEvent('keydown', {
                        key: '$escapedKey',
                        bubbles: true,
                        cancelable: true
                    });
                    target.dispatchEvent(keydownEvent);

                    // 触发 keypress 事件 (某些场景需要)
                    const keypressEvent = new KeyboardEvent('keypress', {
                        key: '$escapedKey',
                        bubbles: true,
                        cancelable: true
                    });
                    target.dispatchEvent(keypressEvent);

                    // 触发 keyup 事件
                    const keyupEvent = new KeyboardEvent('keyup', {
                        key: '$escapedKey',
                        bubbles: true,
                        cancelable: true
                    });
                    target.dispatchEvent(keyupEvent);

                    return true;
                } catch (e) {
                    return false;
                }
            })()
        """.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val pressed = result?.trim() == "true"

            // 5. 等待延迟
            if (pressed && delayMs > 0) {
                delay(delayMs)
            }

            // 6. 返回结果
            return if (pressed) {
                ToolResult.success(
                    "key" to key,
                    "pressed" to true
                )
            } else {
                ToolResult.error("Press key failed: $key")
            }
        } catch (e: Exception) {
            return ToolResult.error("Press failed: ${e.message}")
        }
    }
}
