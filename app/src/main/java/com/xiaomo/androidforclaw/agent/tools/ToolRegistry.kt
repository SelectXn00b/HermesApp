package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-catalog.ts, openclaw-tools.ts
 *
 * AndroidForClaw adaptation: register app, android, config, and extension tools.
 */


import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.workspace.StoragePaths
import java.io.File

/**
 * Tool Registry - Manages universal low-level Tools
 * Inspired by OpenClaw's pi-tools (from Pi Coding Agent)
 *
 * Tools are cross-platform universal capabilities:
 * - read_file, write_file, edit_file: File operations
 * - list_dir: Directory listing
 * - exec: Execute shell commands (auto-routes to embedded Termux or internal shell)
 * - web_fetch: Web fetching
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
        val workspace = StoragePaths.workspace
        workspace.mkdirs()

        // === File system tools (from Pi Coding Agent) ===
        register(ReadFileTool(workspace = workspace))
        register(WriteFileTool(workspace = workspace))
        register(EditFileTool(workspace = workspace))
        register(ListDirTool(workspace = workspace))

        // === Memory tools (Memory Recall) ===
        
        // Memory tools registered in AndroidToolRegistry (MemorySearchSkill/MemoryGetSkill)

        // === Patch tool (OpenClaw apply-patch.ts) ===
        register(ApplyPatchTool(workspace = workspace))

        // === Shell tools ===
        // Single exec entry with backend routing (auto/termux/internal).
        register(ExecFacadeTool(context, workingDir = workspace.absolutePath))

        // === Network tools ===
        register(WebFetchTool())
        register(WebSearchTool {
            // Resolve Brave API key from environment or openclaw.json
            System.getenv("BRAVE_API_KEY") ?: try {
                val json = org.json.JSONObject(
                    StoragePaths.openclawConfig.readText()
                )
                json.optJSONObject("tools")
                    ?.optJSONObject("web")
                    ?.optJSONObject("search")
                    ?.optString("apiKey", null)
            } catch (_: Exception) { null }
        })

        // === Canvas tool (Screen Tab WebView) ===
        register(CanvasTool(context))

        // === Config tools ===
        val configMethods = ConfigMethods(context)
        register(ConfigGetTool(configMethods))
        register(ConfigSetTool(configMethods))

        // === TTS tool (OpenClaw tts-tool.ts) ===
        register(TtsTool(context))

        // === ClawHub skill hub tools ===
        // Aligned with OpenClaw gateway RPC: skills.search / skills.install
        register(SkillsSearchTool(context))
        register(SkillsInstallTool(context))
        register(ClawHubConfigTool(context))

        // === Lark CLI (飞书官方 CLI) ===
        register(LarkCliTool(context))

        Log.d(TAG, "✅ Registered ${tools.size} universal tools (memory tools in AndroidToolRegistry)")
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
    fun getToolsDescription(excludeTools: Set<String> = emptySet()): String {
        return buildString {
            appendLine("## Universal Tools")
            appendLine()
            appendLine("跨平台通用工具，来自 Pi Coding Agent 和 OpenClaw：")
            appendLine()
            tools.values
                .filter { it.name !in excludeTools }
                .forEach { tool ->
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
