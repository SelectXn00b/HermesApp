/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import android.webkit.CookieManager
import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult

/**
 * 浏览器 Cookie 获取工具
 *
 * 获取当前页面的 Cookies
 *
 * 参数: 无
 *
 * 返回:
 * - cookies: String - Cookie 字符串
 * - url: String - 当前 URL
 */
class BrowserGetCookiesTool : BrowserTool {
    override val name = "browser_get_cookies"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 2. 获取当前 URL (通过 JavaScript 避免线程问题)
        val url = BrowserManager.evaluateJavascript("window.location.href")
            ?.trim('"') ?: return ToolResult.error("No active page")

        // 3. 获取 Cookies
        try {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url) ?: ""

            // 4. 返回结果
            return ToolResult.success(
                "cookies" to cookies
            )
        } catch (e: Exception) {
            return ToolResult.error("Get cookies failed: ${e.message}")
        }
    }
}

/**
 * 浏览器 Cookie 设置工具
 *
 * 设置 Cookies
 *
 * 参数:
 * - url: String (可选) - 目标 URL，默认使用当前 URL
 * - cookies: List<String> (必需) - Cookie 列表，格式: "name=value; path=/; domain=.example.com"
 *
 * 返回:
 * - url: String - 设置的 URL
 * - count: Int - 设置的 Cookie 数量
 */
class BrowserSetCookiesTool : BrowserTool {
    override val name = "browser_set_cookies"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        @Suppress("UNCHECKED_CAST")
        val cookieList = (args["cookies"] as? List<*>)?.mapNotNull { it as? String }
            ?: return ToolResult.error("Missing required parameter: cookies")

        if (cookieList.isEmpty()) {
            return ToolResult.error("Parameter 'cookies' cannot be empty")
        }

        // 2. 获取 URL
        val url = (args["url"] as? String)
            ?: BrowserManager.evaluateJavascript("window.location.href")?.trim('"')
            ?: return ToolResult.error("No URL specified and no active page")

        // 3. 设置 Cookies
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            cookieList.forEach { cookie ->
                cookieManager.setCookie(url, cookie)
            }

            // 确保持久化
            cookieManager.flush()

            // 4. 返回结果
            return ToolResult.success(
                "url" to url,
                "count" to cookieList.size
            )
        } catch (e: Exception) {
            return ToolResult.error("Set cookies failed: ${e.message}")
        }
    }
}
