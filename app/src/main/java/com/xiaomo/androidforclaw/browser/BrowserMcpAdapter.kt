/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.xiaomo.androidforclaw.browser

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.mcp.McpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Browser MCP Adapter
 *
 * 将 BrowserForClaw 的 MCP 接口适配为 BrowserToolClient 兼容的接口
 * 支持自动 fallback 到旧 REST API
 */
class BrowserMcpAdapter(
    private val context: Context,
    private val useMcp: Boolean = true  // 是否使用 MCP 协议
) {
    companion object {
        private const val TAG = "BrowserMcpAdapter"
        private const val BROWSER_BASE_URL = "http://localhost:8765"
    }

    private val mcpClient = McpClient(BROWSER_BASE_URL, "AndroidForClaw", "1.0.0")
    private val restClient = BrowserToolClient(context)
    private var mcpAvailable = false

    /**
     * Initialize connection
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (useMcp) {
            // Try MCP first
            val result = mcpClient.initialize()
            if (result.isSuccess) {
                mcpAvailable = true
                Log.i(TAG, "✅ MCP protocol initialized")
                Log.i(TAG, "   Server: ${result.getOrNull()?.serverInfo?.name} ${result.getOrNull()?.serverInfo?.version}")
                return@withContext true
            } else {
                Log.w(TAG, "❌ MCP initialization failed, falling back to REST API")
                Log.w(TAG, "   Reason: ${result.exceptionOrNull()?.message}")
                mcpAvailable = false
            }
        }

        // Check REST API availability
        val healthCheck = mcpClient.checkHealth()
        Log.i(TAG, if (healthCheck) "✅ REST API available" else "❌ REST API not available")
        healthCheck
    }

    /**
     * Execute browser tool
     */
    suspend fun executeTool(
        tool: String,
        args: Map<String, Any?>,
        timeout: Long = 30000L
    ): BrowserToolClient.ToolResult = withContext(Dispatchers.IO) {
        if (useMcp && mcpAvailable) {
            // Use MCP protocol
            try {
                Log.d(TAG, "🔷 Using MCP protocol: $tool")
                val result = mcpClient.callTool(tool, args)

                if (result.isSuccess) {
                    val mcpResult = result.getOrNull()!!

                    if (mcpResult.isError) {
                        // MCP returned error
                        val errorText = mcpResult.content.firstOrNull()?.text ?: "Unknown error"
                        BrowserToolClient.ToolResult(
                            success = false,
                            error = errorText
                        )
                    } else {
                        // MCP returned success
                        val contentMap = mcpResult.content.associate {
                            (it.type to (it.text ?: it.data ?: ""))
                        }
                        BrowserToolClient.ToolResult(
                            success = true,
                            data = if (contentMap.size == 1 && contentMap.containsKey("text")) {
                                mapOf("content" to contentMap["text"])
                            } else {
                                contentMap
                            }
                        )
                    }
                } else {
                    // MCP call failed, fallback to REST
                    Log.w(TAG, "MCP call failed, falling back to REST: ${result.exceptionOrNull()?.message}")
                    mcpAvailable = false
                    restClient.executeToolAsync(tool, args, timeout)
                }
            } catch (e: Exception) {
                Log.e(TAG, "MCP execution error, falling back to REST", e)
                mcpAvailable = false
                restClient.executeToolAsync(tool, args, timeout)
            }
        } else {
            // Use REST API
            Log.d(TAG, "🔶 Using REST API: $tool")
            restClient.executeToolAsync(tool, args, timeout)
        }
    }

    /**
     * List available tools
     */
    suspend fun listTools(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (useMcp && mcpAvailable) {
            val result = mcpClient.listTools()
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!.map { it.name })
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("List tools failed"))
            }
        } else {
            // REST API doesn't have list tools endpoint
            Result.success(
                listOf(
                    "browser_navigate",
                    "browser_click",
                    "browser_type",
                    "browser_get_content",
                    "browser_screenshot",
                    "browser_scroll",
                    "browser_wait",
                    "browser_execute",
                    "browser_hover",
                    "browser_select",
                    "browser_press",
                    "browser_get_cookies",
                    "browser_set_cookies"
                )
            )
        }
    }

    /**
     * Get content (convenience method)
     */
    suspend fun getContent(format: String = "text", selector: String? = null): BrowserToolClient.ToolResult {
        val args = mutableMapOf<String, Any?>("format" to format)
        if (selector != null) {
            args["selector"] = selector
        }
        return executeTool("browser_get_content", args)
    }

    /**
     * Wait (convenience method)
     */
    suspend fun waitTime(timeMs: Long): BrowserToolClient.ToolResult {
        return executeTool("browser_wait", mapOf("timeMs" to timeMs))
    }

    /**
     * Wait for selector (convenience method)
     */
    suspend fun waitForSelector(selector: String, timeout: Long = 10000L): BrowserToolClient.ToolResult {
        return executeTool("browser_wait", mapOf("selector" to selector, "timeout" to timeout), timeout)
    }

    /**
     * Check if using MCP protocol
     */
    fun isUsingMcp(): Boolean = mcpAvailable
}
