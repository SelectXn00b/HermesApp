/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import com.forclaw.browser.control.model.ToolResult

/**
 * 浏览器工具接口
 *
 * 所有浏览器工具必须实现此接口
 */
interface BrowserTool {
    /**
     * 工具名称 (如 "browser_navigate")
     */
    val name: String

    /**
     * 执行工具
     *
     * @param args 参数 Map
     * @return 执行结果
     */
    suspend fun execute(args: Map<String, Any?>): ToolResult
}
