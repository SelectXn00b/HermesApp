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
import kotlinx.coroutines.withTimeout

/**
 * 浏览器等待工具
 *
 * 等待页面满足特定条件
 *
 * 参数:
 * - timeMs: Int (可选) - 简单等待指定毫秒数
 * - selector: String (可选) - 等待元素出现
 * - text: String (可选) - 等待文本出现
 * - textGone: String (可选) - 等待文本消失
 * - url: String (可选) - 等待 URL 变化
 * - jsCondition: String (可选) - 自定义 JavaScript 条件
 * - timeout: Int (可选) - 超时时间（毫秒），默认 30000ms
 *
 * 返回:
 * - waitType: String - 等待类型
 * - success: Boolean - 是否成功
 */
class BrowserWaitTool : BrowserTool {
    override val name = "browser_wait"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 获取参数
        val timeMs = (args["timeMs"] as? Number)?.toLong()
        val selector = args["selector"] as? String
        val text = args["text"] as? String
        val textGone = args["textGone"] as? String
        val url = args["url"] as? String
        val jsCondition = args["jsCondition"] as? String
        val timeout = (args["timeout"] as? Number)?.toLong() ?: 30000L

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 根据参数类型执行不同的等待
        try {
            return withTimeout(timeout) {
                when {
                    // 简单等待
                    timeMs != null -> {
                        delay(timeMs)
                        ToolResult.success(
                            "waitType" to "time",
                            "timeMs" to timeMs
                        )
                    }

                    // 等待元素出现
                    selector != null -> {
                        waitForSelector(selector)
                        ToolResult.success(
                            "waitType" to "selector",
                            "selector" to selector
                        )
                    }

                    // 等待文本出现
                    text != null -> {
                        waitForText(text)
                        ToolResult.success(
                            "waitType" to "text",
                            "text" to text
                        )
                    }

                    // 等待文本消失
                    textGone != null -> {
                        waitForTextGone(textGone)
                        ToolResult.success(
                            "waitType" to "textGone",
                            "text" to textGone
                        )
                    }

                    // 等待 URL 变化
                    url != null -> {
                        waitForUrl(url)
                        ToolResult.success(
                            "waitType" to "url",
                            "url" to url
                        )
                    }

                    // 自定义 JavaScript 条件
                    jsCondition != null -> {
                        waitForJsCondition(jsCondition)
                        ToolResult.success(
                            "waitType" to "jsCondition",
                            "condition" to jsCondition
                        )
                    }

                    else -> {
                        ToolResult.error("No wait condition specified")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return ToolResult.error("Wait timeout after ${timeout}ms")
        } catch (e: Exception) {
            return ToolResult.error("Wait failed: ${e.message}")
        }
    }

    /**
     * 等待元素出现
     */
    private suspend fun waitForSelector(selector: String) {
        val escapedSelector = selector.replace("'", "\\'")
        val script = """
            (function() {
                const el = document.querySelector('$escapedSelector');
                return el !== null;
            })()
        """.trimIndent()

        waitUntilTrue(script, checkInterval = 200L)
    }

    /**
     * 等待文本出现
     */
    private suspend fun waitForText(text: String) {
        val escapedText = text.replace("'", "\\'")
        val script = """
            (function() {
                const bodyText = document.body.innerText || document.body.textContent || '';
                return bodyText.includes('$escapedText');
            })()
        """.trimIndent()

        waitUntilTrue(script, checkInterval = 200L)
    }

    /**
     * 等待文本消失
     */
    private suspend fun waitForTextGone(text: String) {
        val escapedText = text.replace("'", "\\'")
        val script = """
            (function() {
                const bodyText = document.body.innerText || document.body.textContent || '';
                return !bodyText.includes('$escapedText');
            })()
        """.trimIndent()

        waitUntilTrue(script, checkInterval = 200L)
    }

    /**
     * 等待 URL 变化
     */
    private suspend fun waitForUrl(targetUrl: String) {
        while (true) {
            // 使用 JavaScript 获取 URL，避免跨线程问题
            val currentUrl = BrowserManager.evaluateJavascript("window.location.href")
                ?.trim('"') ?: ""
            if (currentUrl.contains(targetUrl)) {
                break
            }
            delay(200L)
        }
    }

    /**
     * 等待自定义 JavaScript 条件
     */
    private suspend fun waitForJsCondition(jsCondition: String) {
        val script = """
            (function() {
                return ($jsCondition);
            })()
        """.trimIndent()

        waitUntilTrue(script, checkInterval = 200L)
    }

    /**
     * 等待 JavaScript 表达式返回 true
     */
    private suspend fun waitUntilTrue(script: String, checkInterval: Long) {
        while (true) {
            val result = BrowserManager.evaluateJavascript(script)
            if (result?.trim() == "true") {
                break
            }
            delay(checkInterval)
        }
    }
}
