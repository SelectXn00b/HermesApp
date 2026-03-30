package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-catalog.ts
 *
 * AndroidForClaw adaptation: canonical tool definitions and profiles.
 */

/**
 * Tool profile IDs.
 * Aligned with OpenClaw ToolProfileId.
 */
enum class ToolProfileId {
    MINIMAL, CODING, MESSAGING, FULL
}

/**
 * Tool section for display grouping.
 * Aligned with OpenClaw CoreToolSection.
 */
data class CoreToolSection(
    val id: String,
    val label: String,
    val tools: List<CoreToolDefinition>
)

/**
 * Single tool definition in the catalog.
 * Aligned with OpenClaw CORE_TOOL_DEFINITIONS entries.
 */
data class CoreToolDefinition(
    val id: String,
    val label: String,
    val description: String,
    val sectionId: String,
    val profiles: List<ToolProfileId>,
    val includeInOpenClawGroup: Boolean = false
)

/**
 * Tool policy from a profile.
 */
data class ToolProfilePolicy(
    val allow: Set<String>? = null,
    val deny: Set<String>? = null
)

/**
 * Tool catalog — canonical tool definitions and grouping.
 * Aligned with OpenClaw tool-catalog.ts.
 */
object ToolCatalog {

    /**
     * Section ordering.
     * Aligned with OpenClaw CORE_TOOL_SECTION_ORDER.
     */
    val SECTION_ORDER = listOf(
        "fs", "runtime", "web", "memory", "sessions",
        "ui", "messaging", "automation", "nodes", "agents", "media"
    )

    /**
     * All 27 core tool definitions.
     * Aligned with OpenClaw CORE_TOOL_DEFINITIONS.
     */
    val CORE_TOOL_DEFINITIONS: List<CoreToolDefinition> = listOf(
        // fs
        CoreToolDefinition("read", "read", "Read file contents", "fs", listOf(ToolProfileId.CODING)),
        CoreToolDefinition("write", "write", "Create or overwrite files", "fs", listOf(ToolProfileId.CODING)),
        CoreToolDefinition("edit", "edit", "Make precise edits", "fs", listOf(ToolProfileId.CODING)),
        CoreToolDefinition("apply_patch", "apply_patch", "Patch files (OpenAI)", "fs", listOf(ToolProfileId.CODING)),
        // runtime
        CoreToolDefinition("exec", "exec", "Run shell commands", "runtime", listOf(ToolProfileId.CODING)),
        CoreToolDefinition("process", "process", "Manage background processes", "runtime", listOf(ToolProfileId.CODING)),
        // web
        CoreToolDefinition("web_search", "web_search", "Search the web", "web", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("web_fetch", "web_fetch", "Fetch web content", "web", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        // memory
        CoreToolDefinition("memory_search", "memory_search", "Semantic search", "memory", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("memory_get", "memory_get", "Read memory files", "memory", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        // sessions
        CoreToolDefinition("sessions_list", "sessions_list", "List sessions", "sessions", listOf(ToolProfileId.CODING, ToolProfileId.MESSAGING), includeInOpenClawGroup = true),
        CoreToolDefinition("sessions_history", "sessions_history", "Session history", "sessions", listOf(ToolProfileId.CODING, ToolProfileId.MESSAGING), includeInOpenClawGroup = true),
        CoreToolDefinition("sessions_send", "sessions_send", "Send to session", "sessions", listOf(ToolProfileId.CODING, ToolProfileId.MESSAGING), includeInOpenClawGroup = true),
        CoreToolDefinition("sessions_spawn", "sessions_spawn", "Spawn sub-agent", "sessions", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("sessions_yield", "sessions_yield", "End turn to receive sub-agent results", "sessions", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("subagents", "subagents", "Manage sub-agents", "sessions", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("session_status", "session_status", "Session status", "sessions", listOf(ToolProfileId.MINIMAL, ToolProfileId.CODING, ToolProfileId.MESSAGING), includeInOpenClawGroup = true),
        // ui
        CoreToolDefinition("browser", "browser", "Control web browser", "ui", emptyList(), includeInOpenClawGroup = true),
        CoreToolDefinition("canvas", "canvas", "Control canvases", "ui", emptyList(), includeInOpenClawGroup = true),
        // messaging
        CoreToolDefinition("message", "message", "Send messages", "messaging", listOf(ToolProfileId.MESSAGING), includeInOpenClawGroup = true),
        // automation
        CoreToolDefinition("cron", "cron", "Schedule tasks", "automation", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("gateway", "gateway", "Gateway control", "automation", emptyList(), includeInOpenClawGroup = true),
        // nodes
        CoreToolDefinition("nodes", "nodes", "Nodes + devices", "nodes", emptyList(), includeInOpenClawGroup = true),
        // agents
        CoreToolDefinition("agents_list", "agents_list", "List agents", "agents", emptyList(), includeInOpenClawGroup = true),
        // media
        CoreToolDefinition("image", "image", "Image understanding", "media", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("image_generate", "image_generate", "Image generation", "media", listOf(ToolProfileId.CODING), includeInOpenClawGroup = true),
        CoreToolDefinition("tts", "tts", "Text-to-speech conversion", "media", emptyList(), includeInOpenClawGroup = true)
    )

    /**
     * Tool groups.
     * Aligned with OpenClaw CORE_TOOL_GROUPS.
     */
    val CORE_TOOL_GROUPS: Map<String, Set<String>> by lazy {
        buildCoreToolGroupMap()
    }

    /**
     * Check if a tool ID is a known core tool.
     * Aligned with OpenClaw isKnownCoreToolId.
     */
    fun isKnownCoreToolId(toolId: String): Boolean {
        return CORE_TOOL_DEFINITIONS.any { it.id == toolId }
    }

    /**
     * List core tool sections with their tools.
     * Aligned with OpenClaw listCoreToolSections.
     */
    fun listCoreToolSections(): List<CoreToolSection> {
        val bySection = CORE_TOOL_DEFINITIONS.groupBy { it.sectionId }
        return SECTION_ORDER.mapNotNull { sectionId ->
            val tools = bySection[sectionId] ?: return@mapNotNull null
            CoreToolSection(id = sectionId, label = sectionId, tools = tools)
        }
    }

    /**
     * Resolve tool policy for a profile.
     * Aligned with OpenClaw resolveCoreToolProfilePolicy.
     */
    fun resolveCoreToolProfilePolicy(profile: ToolProfileId?): ToolProfilePolicy? {
        if (profile == null || profile == ToolProfileId.FULL) return null

        val allowedTools = CORE_TOOL_DEFINITIONS
            .filter { it.profiles.contains(profile) }
            .map { it.id }
            .toSet()

        return if (allowedTools.isEmpty()) null else ToolProfilePolicy(allow = allowedTools)
    }

    private fun buildCoreToolGroupMap(): Map<String, Set<String>> {
        val groups = mutableMapOf<String, Set<String>>()

        // Per-section groups
        val bySection = CORE_TOOL_DEFINITIONS.groupBy { it.sectionId }
        for ((sectionId, tools) in bySection) {
            groups["group:$sectionId"] = tools.map { it.id }.toSet()
        }

        // OpenClaw group: all tools with includeInOpenClawGroup
        groups["group:openclaw"] = CORE_TOOL_DEFINITIONS
            .filter { it.includeInOpenClawGroup }
            .map { it.id }
            .toSet()

        return groups
    }
}
