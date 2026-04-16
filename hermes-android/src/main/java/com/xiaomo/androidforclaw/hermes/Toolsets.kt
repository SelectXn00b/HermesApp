package com.xiaomo.androidforclaw.hermes

import com.google.gson.Gson

/**
 * Toolsets Module
 * 1:1 对齐 hermes-agent/toolsets.py
 *
 * 工具集管理：定义、组合、解析工具集。
 */

// ── 核心工具列表 ──────────────────────────────────────────────────────────

val HERMES_CORE_TOOLS = listOf(
    // Web
    "web_search", "web_extract",
    // Terminal + process management
    "terminal", "process",
    // File manipulation
    "read_file", "write_file", "patch", "search_files",
    // Vision + image generation
    "vision_analyze", "image_generate",
    // Skills
    "skills_list", "skill_view", "skill_manage",
    // Browser automation
    "browser_navigate", "browser_snapshot", "browser_click",
    "browser_type", "browser_scroll", "browser_back",
    "browser_press", "browser_get_images",
    "browser_vision", "browser_console",
    // Text-to-speech
    "text_to_speech",
    // Planning & memory
    "todo", "memory",
    // Session history search
    "session_search",
    // Clarifying questions
    "clarify",
    // Code execution + delegation
    "execute_code", "delegate_task",
    // Cronjob management
    "cronjob",
    // Cross-platform messaging
    "send_message",
    // Home Assistant
    "ha_list_entities", "ha_get_state", "ha_list_services", "ha_call_service")

// ── Toolset 定义 ──────────────────────────────────────────────────────────

data class ToolsetDefinition(
    val description: String,
    val tools: List<String>,
    val includes: List<String> = emptyList())

val TOOLSETS: Map<String, ToolsetDefinition> = mapOf(
    "web" to ToolsetDefinition(
        description = "Web research and content extraction tools",
        tools = listOf("web_search", "web_extract")),
    "search" to ToolsetDefinition(
        description = "Web search only (no content extraction/scraping)",
        tools = listOf("web_search")),
    "vision" to ToolsetDefinition(
        description = "Image analysis and vision tools",
        tools = listOf("vision_analyze")),
    "image_gen" to ToolsetDefinition(
        description = "Creative generation tools (images)",
        tools = listOf("image_generate")),
    "terminal" to ToolsetDefinition(
        description = "Terminal/command execution and process management tools",
        tools = listOf("terminal", "process")),
    "moa" to ToolsetDefinition(
        description = "Advanced reasoning and problem-solving tools",
        tools = listOf("mixture_of_agents")),
    "skills" to ToolsetDefinition(
        description = "Access, create, edit, and manage skill documents",
        tools = listOf("skills_list", "skill_view", "skill_manage")),
    "browser" to ToolsetDefinition(
        description = "Browser automation for web interaction",
        tools = listOf(
            "browser_navigate", "browser_snapshot", "browser_click",
            "browser_type", "browser_scroll", "browser_back",
            "browser_press", "browser_get_images",
            "browser_vision", "browser_console", "web_search")),
    "cronjob" to ToolsetDefinition(
        description = "Cronjob management tool",
        tools = listOf("cronjob")),
    "messaging" to ToolsetDefinition(
        description = "Cross-platform messaging",
        tools = listOf("send_message")),
    "rl" to ToolsetDefinition(
        description = "RL training tools",
        tools = listOf(
            "rl_list_environments", "rl_select_environment",
            "rl_get_current_config", "rl_edit_config",
            "rl_start_training", "rl_check_status",
            "rl_stop_training", "rl_get_results",
            "rl_list_runs", "rl_test_inference")),
    "file" to ToolsetDefinition(
        description = "File manipulation tools",
        tools = listOf("read_file", "write_file", "patch", "search_files")),
    "tts" to ToolsetDefinition(
        description = "Text-to-speech",
        tools = listOf("text_to_speech")),
    "todo" to ToolsetDefinition(
        description = "Task planning and tracking",
        tools = listOf("todo")),
    "memory" to ToolsetDefinition(
        description = "Persistent memory across sessions",
        tools = listOf("memory")),
    "session_search" to ToolsetDefinition(
        description = "Search and recall past conversations",
        tools = listOf("session_search")),
    "clarify" to ToolsetDefinition(
        description = "Ask the user clarifying questions",
        tools = listOf("clarify")),
    "code_execution" to ToolsetDefinition(
        description = "Run Python scripts that call tools programmatically",
        tools = listOf("execute_code")),
    "delegation" to ToolsetDefinition(
        description = "Spawn subagents with isolated context",
        tools = listOf("delegate_task")),
    "homeassistant" to ToolsetDefinition(
        description = "Home Assistant smart home control",
        tools = listOf("ha_list_entities", "ha_get_state", "ha_list_services", "ha_call_service")),
    // Scenario-specific toolsets
    "debugging" to ToolsetDefinition(
        description = "Debugging and troubleshooting toolkit",
        tools = listOf("terminal", "process"),
        includes = listOf("web", "file")),
    "safe" to ToolsetDefinition(
        description = "Safe toolkit without terminal access",
        tools = emptyList(),
        includes = listOf("web", "vision", "image_gen")),
    // Full Hermes toolsets
    "hermes-cli" to ToolsetDefinition(
        description = "Full interactive CLI toolset",
        tools = HERMES_CORE_TOOLS),
    "hermes-android" to ToolsetDefinition(
        description = "Android toolset - full access for Android app",
        tools = HERMES_CORE_TOOLS))

// ── 公开 API ──────────────────────────────────────────────────────────────

/**
 * 获取工具集定义
 * Python: get_toolset(name)
 */
fun getToolset(name: String): ToolsetDefinition? {
    return TOOLSETS[name]
}

/**
 * 解析工具集（递归）
 * Python: resolve_toolset(name, visited)
 */
fun resolveToolset(name: String, visited: MutableSet<String> = mutableSetOf()): List<String> {
    // 特殊别名
    if (name in setOf("all", "*")) {
        val allTools = mutableSetOf<String>()
        for (toolsetName in getToolsetNames()) {
            allTools.addAll(resolveToolset(toolsetName, mutableSetOf()))
        }
        return allTools.toList()
    }

    // 循环检测
    if (name in visited) return emptyList()
    visited.add(name)

    val toolset = TOOLSETS[name] ?: return emptyList()

    val tools = toolset.tools.toMutableSet()

    // 递归解析 includes
    for (includedName in toolset.includes) {
        tools.addAll(resolveToolset(includedName, visited))
    }

    return tools.toList()
}

/**
 * 解析多个工具集
 * Python: resolve_multiple_toolsets(toolset_names)
 */
fun resolveMultipleToolsets(toolsetNames: List<String>): List<String> {
    val allTools = mutableSetOf<String>()
    for (name in toolsetNames) {
        allTools.addAll(resolveToolset(name))
    }
    return allTools.toList()
}

/**
 * 获取所有工具集
 * Python: get_all_toolsets()
 */
fun getAllToolsets(): Map<String, ToolsetDefinition> {
    return TOOLSETS.toMap()
}

/**
 * 获取工具集名称
 * Python: get_toolset_names()
 */
fun getToolsetNames(): List<String> {
    return TOOLSETS.keys.toList().sorted()
}

/**
 * 验证工具集
 * Python: validate_toolset(name)
 */
fun validateToolset(name: String): Boolean {
    if (name in setOf("all", "*")) return true
    return name in TOOLSETS
}

/**
 * 创建自定义工具集（运行时）
 * Python: create_custom_toolset(...)
 */
val customToolsets = mutableMapOf<String, ToolsetDefinition>()

fun createCustomToolset(
    name: String,
    description: String,
    tools: List<String> = emptyList(),
    includes: List<String> = emptyList()) {
    customToolsets[name] = ToolsetDefinition(description, tools, includes)
}

/**
 * 获取工具集信息
 * Python: get_toolset_info(name)
 */
data class ToolsetInfo(
    val name: String,
    val description: String,
    val directTools: List<String>,
    val includes: List<String>,
    val resolvedTools: List<String>,
    val toolCount: Int,
    val isComposite: Boolean)

fun getToolsetInfo(name: String): ToolsetInfo? {
    val toolset = getToolset(name) ?: return null
    val resolved = resolveToolset(name)

    return ToolsetInfo(
        name = name,
        description = toolset.description,
        directTools = toolset.tools,
        includes = toolset.includes,
        resolvedTools = resolved,
        toolCount = resolved.size,
        isComposite = toolset.includes.isNotEmpty())


}
