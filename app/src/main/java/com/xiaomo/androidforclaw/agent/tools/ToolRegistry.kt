package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: register app, android, config, and extension tools.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import java.io.File

/**
 * Tool Registry - Manages universal low-level Tools
 * Inspired by OpenClaw's pi-tools (from Pi Coding Agent)
 *
 * Tools are cross-platform universal capabilities:
 * - read_file, write_file, edit_file: File operations
 * - list_dir: Directory listing
 * - exec: Execute shell commands
 * - web_fetch: Web fetching
 * - javascript: JavaScript execution
 *
 * Note: Android-specific capabilities are managed in AndroidToolRegistry
 */
class ToolRegistry(
    private val context: Context,
    private val taskDataManager: TaskDataManager
) {
    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val tools = mutableMapOf<String, Tool>()

    init {
        registerDefaultTools()
    }

    /**
     * Register universal tools (cross-platform capabilities)
     */
    private fun registerDefaultTools() {
        // Use external storage workspace (aligned with OpenClaw ~/.openclaw/workspace/)
        val workspace = File("/sdcard/.androidforclaw/workspace")
        workspace.mkdirs()

        // === File system tools (from Pi Coding Agent) ===
        register(ReadFileTool(workspace = workspace))
        register(WriteFileTool(workspace = workspace))
        register(EditFileTool(workspace = workspace))
        register(ListDirTool(workspace = workspace))

        // === Memory tools (Memory Recall) ===
        // TODO: Fix Memory tools compilation errors
        // register(MemorySearchTool(workspace = workspace))
        // register(MemoryGetTool(workspace = workspace))

        // === Shell tools ===
        register(ExecTool(workingDir = workspace.absolutePath))

        // === Network tools ===
        register(WebFetchTool())

        // === Config tools ===
        val configMethods = ConfigMethods(context)
        register(ConfigGetTool(configMethods))
        register(ConfigSetTool(configMethods))

        // === JavaScript execution tools ===
        register(JavaScriptTool(context))

        // === Local code execution backends ===
        // Termux is closer to OpenClaw's universal execution backends than Android UI skills.
        register(TermuxBridgeTool(context))

        Log.d(TAG, "✅ Registered ${tools.size} universal tools (incl. memory_search, memory_get)")
    }

    /**
     * Register a tool
     */
    fun register(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name}")
    }

    /**
     * Check if the specified tool exists
     */
    fun contains(name: String): Boolean = tools.containsKey(name)

    /**
     * Execute tool
     */
    suspend fun execute(name: String, args: Map<String, Any?>): ToolResult {
        val tool = tools[name]
        if (tool == null) {
            Log.e(TAG, "Unknown tool: $name")
            return ToolResult.error("Unknown tool: $name")
        }

        Log.d(TAG, "Executing tool: $name with args: $args")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            ToolResult.error("Execution failed: ${e.message}")
        }
    }

    /**
     * Get all Tool Definitions (for LLM function calling)
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.getToolDefinition() }
    }

    /**
     * Get all tools description (for building system prompt)
     */
    fun getToolsDescription(): String {
        return buildString {
            appendLine("## Universal Tools")
            appendLine()
            appendLine("跨平台通用工具，来自 Pi Coding Agent 和 OpenClaw：")
            appendLine()
            tools.values.forEach { tool ->
                appendLine("### ${tool.name}")
                appendLine(tool.description)
                appendLine()
            }
        }
    }

    /**
     * Get tool count
     */
    fun getToolCount(): Int = tools.size
}
