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
 * 浏览器导航工具
 *
 * 打开指定 URL
 *
 * 参数:
 * - url: String (必需) - 目标 URL
 * - waitMs: Int (可选) - 等待页面开始加载的毫秒数，默认 500ms
 *
 * 返回:
 * - url: String - 请求的 URL
 * - currentUrl: String - 当前实际 URL
 */
class BrowserNavigateTool : BrowserTool {
    override val name = "browser_navigate"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val url = args["url"] as? String
            ?: return ToolResult.error("Missing required parameter: url")

        if (url.isBlank()) {
            return ToolResult.error("Parameter 'url' cannot be empty")
        }

        // 2. 规范化 URL (添加 https:// 如果缺少协议)
        val fullUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("file://") || url.startsWith("about:") || url.startsWith("data:") -> url
            else -> "https://$url"
        }

        // 3. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 4. 执行导航
        try {
            BrowserManager.navigate(fullUrl)

            // 5. 等待页面开始加载
            val waitMs = (args["waitMs"] as? Number)?.toLong() ?: 500L
            if (waitMs > 0) {
                delay(waitMs)
            }

            // 6. 返回结果
            return ToolResult.success(
                "url" to fullUrl,
                "status" to "navigating"
            )
        } catch (e: Exception) {
            return ToolResult.error("Navigation failed: ${e.message}")
        }
    }
}
