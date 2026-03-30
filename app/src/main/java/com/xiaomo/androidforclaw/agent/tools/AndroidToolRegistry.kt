package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-catalog.ts
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.agent.memory.MemoryManager
import com.xiaomo.androidforclaw.camera.CameraCaptureManager
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.xiaomo.androidforclaw.agent.tools.memory.MemoryGetSkill
import com.xiaomo.androidforclaw.agent.tools.memory.MemorySearchSkill
import com.xiaomo.androidforclaw.agent.tools.device.DeviceToolSkillAdapter
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * Android Tool Registry
 *
 * Manages Android platform-specific tools (Platform-specific tools)
 *
 * Aligned with OpenClaw architecture:
 * - ToolRegistry: Universal tools (read, write, exec, web_fetch)
 * - AndroidToolRegistry: Android platform tools (tap, screenshot, open_app, memory)
 * - SkillsLoader: Markdown Skills (mobile-operations.md)
 *
 * Reference: Platform-specific capabilities in OpenClaw's pi-tools.ts
 */
class AndroidToolRegistry(
    private val context: Context,
    private val taskDataManager: TaskDataManager,
    private val memoryManager: MemoryManager? = null,
    private val workspacePath: String = StoragePaths.workspace.absolutePath,
    private val cameraCaptureManager: CameraCaptureManager? = null,
) {
    companion object {
        private const val TAG = "AndroidToolRegistry"
    }

    private val tools = mutableMapOf<String, Skill>()

    init {
        registerAndroidTools()
        registerMemoryTools()
    }

    /**
     * Register Android platform-specific tools
     */
    private fun registerAndroidTools() {
        // === Unified device tool (Playwright-aligned) ===
        // Single entry point for ALL screen operations via ref-based interaction
        // Replaces: screenshot, get_view_tree, tap, swipe, type, long_press, home, back, open_app, wait
        register(DeviceToolSkillAdapter(context))

        // === App management tools ===
        register(ListInstalledAppsSkill(context))  // List apps
        register(InstallAppSkill(context))         // Install APK
        register(StartActivityTool(context))       // Start Activity

        // === Control tools ===
        register(StopSkill(taskDataManager)) // Stop
        register(LogSkill())                 // Log

        // === Feishu image (kept as direct tool — media upload needs special handling) ===
        register(FeishuSendImageSkill(context))

        // === Eye (对齐 OpenClaw camera — 手机摄像头作为 Agent 的眼睛) ===
        if (cameraCaptureManager != null) {
            register(EyeSkill(context, cameraCaptureManager))
        } else {
            Log.d(TAG, "⚠️ CameraCaptureManager not provided, skipping eye skill")
        }

        Log.d(TAG, "✅ Registered ${tools.size} Android platform tools")
    }

    /**
     * Register memory tools
     */
    private fun registerMemoryTools() {
        if (memoryManager == null) {
            Log.d(TAG, "⚠️ MemoryManager not provided, skipping memory tools")
            return
        }

        // === Memory tools (Memory) ===
        register(MemoryGetSkill(memoryManager, workspacePath))
        register(MemorySearchSkill(memoryManager, workspacePath))

        Log.d(TAG, "✅ Registered memory tools")
    }

    /**
     * Register a tool
     */
    private fun register(tool: Skill) {
        tools[tool.name] = tool
        Log.d(TAG, "  📱 ${tool.name}")
    }

    /**
     * Check if the specified tool exists
     */
    fun contains(name: String): Boolean = tools.containsKey(name)

    /**
     * Execute tool
     */
    suspend fun execute(name: String, args: Map<String, Any?>): SkillResult {
        val tool = tools[name]
        if (tool == null) {
            Log.e(TAG, "Unknown Android tool: $name")
            return SkillResult.error("Unknown Android tool: $name")
        }

        Log.d(TAG, "Executing Android tool: $name with args: $args")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            Log.e(TAG, "Android tool execution failed: $name", e)
            SkillResult.error("Execution failed: ${e.message}")
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
            appendLine("## Android Platform Tools")
            appendLine()
            appendLine("Android 设备专属能力,通过 AccessibilityService 和系统 API 提供：")
            appendLine()

            // Organize by category
            val categories = mapOf(
                "观察" to listOf("screenshot", "get_view_tree"),
                "交互" to listOf("tap", "swipe", "type", "long_press"),
                "导航" to listOf("home", "back", "open_app"),
                "应用管理" to listOf("list_installed_apps", "install_app", "start_activity"),
                "控制" to listOf("wait", "stop", "log"),
                "浏览器" to listOf("browser")
            )

            categories.forEach { (category, toolNames) ->
                val filtered = toolNames.filter { it !in excludeTools }
                if (filtered.isNotEmpty()) {
                    appendLine("### $category")
                    filtered.forEach { name ->
                        tools[name]?.let { tool ->
                            appendLine("- **${tool.name}**: ${tool.description.lines().first()}")
                        }
                    }
                    appendLine()
                }
            }
        }
    }

    /**
     * Get tool count
     */
    fun getToolCount(): Int = tools.size
}
