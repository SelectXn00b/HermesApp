/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.executor

import com.forclaw.browser.control.model.ToolResult
import com.forclaw.browser.control.tools.*

/**
 * 浏览器工具执行器
 *
 * 职责:
 * - 注册所有可用的浏览器工具
 * - 根据工具名称路由到具体工具
 * - 统一处理异常
 */
object BrowserToolsExecutor {

    private val tools = mutableMapOf<String, BrowserTool>()

    /**
     * 初始化执行器，注册所有工具
     *
     * 应该在 Application.onCreate() 中调用
     */
    fun init() {
        // 核心 5 个工具 (v0.3.0)
        register(BrowserNavigateTool())
        register(BrowserClickTool())
        register(BrowserTypeTool())
        register(BrowserScrollTool())
        register(BrowserGetContentTool())

        // 新增 7 个工具 (v0.4.0)
        register(BrowserWaitTool())
        register(BrowserExecuteTool())
        register(BrowserPressTool())
        register(BrowserHoverTool())
        register(BrowserSelectTool())
        register(BrowserScreenshotTool())
        register(BrowserGetCookiesTool())
        register(BrowserSetCookiesTool())
    }

    /**
     * 注册工具
     *
     * @param tool 要注册的工具实例
     */
    private fun register(tool: BrowserTool) {
        tools[tool.name] = tool
    }

    /**
     * 执行工具
     *
     * @param toolName 工具名称
     * @param args 参数 Map
     * @return 执行结果
     */
    suspend fun execute(toolName: String, args: Map<String, Any?>): ToolResult {
        // 1. 查找工具
        val tool = tools[toolName]
            ?: return ToolResult.error("Unknown tool: $toolName")

        // 2. 执行工具
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }

    /**
     * 获取所有可用工具的名称
     *
     * @return 工具名称列表
     */
    fun getAvailableTools(): List<String> {
        return tools.keys.toList()
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return true 如果工具存在
     */
    fun hasT(toolName: String): Boolean {
        return tools.containsKey(toolName)
    }
}
