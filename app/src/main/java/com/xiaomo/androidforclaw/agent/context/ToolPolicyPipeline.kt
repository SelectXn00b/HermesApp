package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-policy-pipeline.ts (buildDefaultToolPolicyPipelineSteps, applyToolPolicyPipeline)
 * - ../openclaw/src/agents/tool-policy.ts (isOwnerOnlyToolName, applyOwnerOnlyToolPolicy, ToolPolicyLike, ToolProfileId)
 * - ../openclaw/src/agents/tool-policy-shared.ts (TOOL_NAME_ALIASES, normalizeToolName, expandToolGroups)
 * - ../openclaw/src/security/dangerous-tools.ts (DEFAULT_GATEWAY_HTTP_TOOL_DENY, DANGEROUS_ACP_TOOLS)
 *
 * AndroidForClaw adaptation: multi-step tool policy pipeline.
 * Filters tools through ordered policy steps: profile, byProvider.profile, allow, byProvider.allow,
 * agent, agent.byProvider, group, owner-only, subagent.
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * Tool policy definition (allow/deny lists).
 * Aligned with OpenClaw ToolPolicyLike.
 */
data class ToolPolicyLike(
    val allow: List<String>? = null,
    val deny: List<String>? = null
)

/**
 * Tool profile IDs for preset tool sets.
 * Aligned with OpenClaw ToolProfileId.
 */
enum class ToolProfileId(val id: String) {
    MINIMAL("minimal"),
    CODING("coding"),
    MESSAGING("messaging"),
    FULL("full");

    companion object {
        fun fromString(s: String?): ToolProfileId? =
            entries.find { it.id == s?.lowercase() }
    }
}

/**
 * A single step in the tool policy pipeline.
 * Aligned with OpenClaw ToolPolicyPipelineStep.
 */
data class ToolPolicyPipelineStep(
    val policy: ToolPolicyLike?,
    val label: String,
    val stripPluginOnlyAllowlist: Boolean = false
)

/**
 * Tool name aliases for normalization.
 * Aligned with OpenClaw TOOL_NAME_ALIASES.
 */
object ToolNameAliases {
    private val ALIASES = mapOf(
        "bash" to "exec",
        "apply-patch" to "apply_patch"
    )

    /** Normalize a tool name: trim, lowercase, resolve aliases. */
    fun normalizeToolName(name: String): String {
        val normalized = name.trim().lowercase()
        return ALIASES[normalized] ?: normalized
    }

    /** Normalize a list of tool names. */
    fun normalizeToolList(list: List<String>?): List<String>? {
        return list?.map { normalizeToolName(it) }
    }
}

/**
 * Built-in tool groups for policy expansion.
 * Aligned with OpenClaw TOOL_GROUPS / CORE_TOOL_GROUPS.
 */
object ToolGroups {
    val GROUPS: Map<String, List<String>> = mapOf(
        "files" to listOf("read_file", "write_file", "edit_file", "list_dir"),
        "runtime" to listOf("exec"),
        "web" to listOf("web_search", "web_fetch"),
        "memory" to listOf("memory_search", "memory_get"),
        "sessions" to listOf("sessions_list", "sessions_history", "sessions_send", "sessions_spawn", "sessions_yield", "sessions_kill", "session_status", "subagents"),
        "ui" to listOf("canvas", "browser"),
        "media" to listOf("tts", "eye", "feishu_send_image"),
        "config" to listOf("config_get", "config_set"),
        "automation" to listOf("cron")
    )

    /** Expand group references in tool names list */
    fun expandToolGroups(names: List<String>?): List<String>? {
        if (names == null) return null
        val expanded = mutableListOf<String>()
        for (name in names) {
            val groupName = name.removePrefix("group:")
            val group = GROUPS[groupName]
            if (group != null) {
                expanded.addAll(group)
            } else {
                expanded.add(ToolNameAliases.normalizeToolName(name))
            }
        }
        return expanded.distinct()
    }
}

/**
 * Owner-only tools that require sender to be the device owner.
 * Aligned with OpenClaw OWNER_ONLY_TOOL_NAME_FALLBACKS.
 */
object OwnerOnlyTools {
    private val OWNER_ONLY_TOOL_NAMES = setOf(
        "whatsapp_login",
        "cron",
        "gateway",
        "nodes"
    )

    fun isOwnerOnlyToolName(name: String): Boolean =
        ToolNameAliases.normalizeToolName(name) in OWNER_ONLY_TOOL_NAMES

    /**
     * Filter tools based on owner status.
     * Aligned with OpenClaw applyOwnerOnlyToolPolicy.
     */
    fun filterByOwnerStatus(
        toolNames: List<String>,
        senderIsOwner: Boolean
    ): List<String> {
        if (senderIsOwner) return toolNames
        return toolNames.filter { !isOwnerOnlyToolName(it) }
    }
}

/**
 * Dangerous tools that should be restricted in certain contexts.
 * Aligned with OpenClaw dangerous-tools.ts.
 */
object DangerousTools {
    /**
     * Tools denied on Gateway HTTP by default.
     * Aligned with OpenClaw DEFAULT_GATEWAY_HTTP_TOOL_DENY.
     */
    val DEFAULT_GATEWAY_HTTP_TOOL_DENY = setOf(
        "sessions_spawn", "sessions_send", "cron", "gateway", "whatsapp_login"
    )

    /**
     * Tools dangerous for ACP (inter-agent) calls.
     * Aligned with OpenClaw DANGEROUS_ACP_TOOL_NAMES.
     */
    val DANGEROUS_ACP_TOOLS = setOf(
        "exec", "spawn", "shell",
        "sessions_spawn", "sessions_send", "gateway",
        "fs_write", "fs_delete", "fs_move", "apply_patch"
    )
}

/**
 * Subagent tool restrictions.
 * Aligned with OpenClaw subagent tool policy.
 */
object SubagentToolPolicy {
    /** Tools that subagents (non-root agents) should not have access to */
    private val SUBAGENT_RESTRICTED_TOOLS = setOf(
        "cron",
        "config_set",
        "config_get"
    )

    fun filterForSubagent(
        toolNames: List<String>,
        isSubagent: Boolean
    ): List<String> {
        if (!isSubagent) return toolNames
        return toolNames.filter { it !in SUBAGENT_RESTRICTED_TOOLS }
    }
}

/**
 * Resolve tool profile policy (preset tool sets).
 * Aligned with OpenClaw resolveToolProfilePolicy.
 */
fun resolveToolProfilePolicy(profileId: ToolProfileId?): ToolPolicyLike? {
    return when (profileId) {
        ToolProfileId.MINIMAL -> ToolPolicyLike(
            allow = listOf("read_file", "list_dir", "web_search", "web_fetch")
        )
        ToolProfileId.CODING -> ToolPolicyLike(
            allow = listOf("read_file", "write_file", "edit_file", "list_dir", "exec", "web_search", "web_fetch")
        )
        ToolProfileId.MESSAGING -> ToolPolicyLike(
            allow = listOf("read_file", "list_dir", "web_search", "web_fetch",
                "memory_search", "memory_get", "sessions_list", "sessions_history",
                "sessions_send", "tts", "canvas")
        )
        ToolProfileId.FULL, null -> null  // null = no restriction
    }
}

/**
 * ToolPolicyPipeline — Multi-step tool policy pipeline.
 * Aligned with OpenClaw tool-policy-pipeline.ts.
 */
object ToolPolicyPipeline {

    private const val TAG = "ToolPolicyPipeline"

    /**
     * Build default pipeline steps (7 steps).
     * Aligned with OpenClaw buildDefaultToolPolicyPipelineSteps.
     */
    fun buildDefaultSteps(
        profilePolicy: ToolPolicyLike? = null,
        providerProfilePolicy: ToolPolicyLike? = null,
        globalPolicy: ToolPolicyLike? = null,
        globalProviderPolicy: ToolPolicyLike? = null,
        agentPolicy: ToolPolicyLike? = null,
        agentProviderPolicy: ToolPolicyLike? = null,
        groupPolicy: ToolPolicyLike? = null
    ): List<ToolPolicyPipelineStep> {
        return listOf(
            ToolPolicyPipelineStep(profilePolicy, "tools.profile", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(providerProfilePolicy, "tools.byProvider.profile", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(globalPolicy, "tools.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(globalProviderPolicy, "tools.byProvider.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(agentPolicy, "agents.{id}.tools.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(agentProviderPolicy, "agents.{id}.tools.byProvider.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(groupPolicy, "group tools.allow", stripPluginOnlyAllowlist = true)
        )
    }

    /**
     * Apply pipeline: filter tools through ordered steps.
     * Aligned with OpenClaw applyToolPolicyPipeline.
     */
    fun apply(
        toolNames: List<String>,
        steps: List<ToolPolicyPipelineStep>
    ): List<String> {
        var remaining = toolNames

        for (step in steps) {
            val policy = step.policy ?: continue
            remaining = filterByPolicy(remaining, policy)
            if (remaining.isEmpty()) {
                Log.w(TAG, "All tools filtered out at step '${step.label}'")
                break
            }
        }

        return remaining
    }

    /**
     * Filter tool names by a single policy.
     * Aligned with OpenClaw filterToolsByPolicy.
     */
    fun filterByPolicy(toolNames: List<String>, policy: ToolPolicyLike): List<String> {
        var result = toolNames

        // Apply allowlist: keep only allowed tools
        val expandedAllow = ToolGroups.expandToolGroups(policy.allow)
        if (expandedAllow != null && expandedAllow.isNotEmpty()) {
            val allowSet = expandedAllow.map { it.lowercase() }.toSet()
            result = result.filter { it.lowercase() in allowSet ||
                // Special: apply_patch is allowed if exec is allowed
                (it.lowercase() == "apply_patch" && "exec" in allowSet)
            }
        }

        // Apply denylist: remove denied tools
        val expandedDeny = ToolGroups.expandToolGroups(policy.deny)
        if (expandedDeny != null) {
            val denySet = expandedDeny.map { it.lowercase() }.toSet()
            result = result.filter { it.lowercase() !in denySet }
        }

        return result
    }
}
