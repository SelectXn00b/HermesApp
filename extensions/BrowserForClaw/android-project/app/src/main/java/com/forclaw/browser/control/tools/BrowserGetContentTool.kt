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
 * 浏览器获取内容工具
 *
 * 获取当前页面的文本内容
 *
 * 参数:
 * - format: String (可选) - 内容格式: "text" (纯文本) 或 "html"，默认 "text"
 * - waitForContent: Boolean (可选) - 是否等待内容加载完成，默认 true
 * - timeout: Int (可选) - 等待超时时间（毫秒），默认 5000
 *
 * 返回:
 * - content: String - 页面内容
 * - length: Int - 内容长度
 * - url: String - 当前页面 URL
 * - title: String - 当前页面标题
 */
class BrowserGetContentTool : BrowserTool {
    override val name = "browser_get_content"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 获取参数
        val format = (args["format"] as? String)?.lowercase() ?: "text"
        val waitForContent = (args["waitForContent"] as? Boolean) ?: true
        val timeout = (args["timeout"] as? Number)?.toLong() ?: 5000L

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 如果需要等待内容，先检查页面是否加载完成
        if (waitForContent) {
            val loadCheckScript = """
                (function() {
                    return document.readyState === 'complete' &&
                           (document.body.innerText || document.body.textContent || '').length > 0;
                })()
            """.trimIndent()

            // 等待内容加载，最多等待 timeout 毫秒
            val startTime = System.currentTimeMillis()
            var contentReady = false

            while (!contentReady && (System.currentTimeMillis() - startTime) < timeout) {
                try {
                    // 必须在主线程执行 evaluateJavascript
                    val result = withContext(Dispatchers.Main) {
                        BrowserManager.evaluateJavascript(loadCheckScript)
                    }
                    contentReady = result?.trim() == "true"

                    if (!contentReady) {
                        kotlinx.coroutines.delay(200) // 每 200ms 检查一次
                    }
                } catch (e: Exception) {
                    // 继续等待
                    kotlinx.coroutines.delay(200)
                }
            }
        }

        // 4. 构造 JavaScript 代码
        val script = when (format) {
            "text" -> {
                """
                    (function() {
                        try {
                            return document.body.innerText || document.body.textContent || '';
                        } catch (e) {
                            return '';
                        }
                    })()
                """
            }
            "html" -> {
                """
                    (function() {
                        try {
                            return document.documentElement.outerHTML || '';
                        } catch (e) {
                            return '';
                        }
                    })()
                """
            }
            else -> return ToolResult.error("Invalid format: $format (must be 'text' or 'html')")
        }.trimIndent()

        // 5. 执行 JavaScript (必须在主线程)
        try {
            val rawResult = withContext(Dispatchers.Main) {
                BrowserManager.evaluateJavascript(script)
            }
            // evaluateJavascript 返回的字符串是 JSON 编码的，需要去掉首尾引号
            val content = rawResult?.trim()?.removeSurrounding("\"")?.let {
                // 解码 JSON 转义
                it.replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } ?: ""

            // 6. 限制内容长度，避免 HTTP 响应过大
            val maxLength = 10000 // 最大 10000 字符
            val truncated = content.length > maxLength
            val finalContent = if (truncated) {
                content.substring(0, maxLength) + "\n...(truncated)"
            } else {
                content
            }

            // 7. 返回结果
            return ToolResult.success(
                "content" to finalContent,
                "length" to content.length,
                "truncated" to truncated,
                "format" to format,
                "url" to (BrowserManager.getCurrentUrl() ?: ""),
                "title" to (BrowserManager.getCurrentTitle() ?: "")
            )
        } catch (e: Exception) {
            return ToolResult.error("Get content failed: ${e.message}")
        }
    }
}
