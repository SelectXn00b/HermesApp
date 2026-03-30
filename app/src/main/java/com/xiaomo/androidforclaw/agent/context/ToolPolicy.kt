package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/extensions/feishu/.../policy/FeishuPolicy.kt (resolveToolPolicy)
 * - ../openclaw/extensions/discord/.../policy/DiscordPolicy.kt (resolveToolPolicy)
 *
 * AndroidForClaw adaptation: centralized tool policy resolution by chat context.
 * Determines which tools are available based on whether the conversation is
 * a private DM or a shared group chat.
 */

/**
 * Tool access policy levels.
 * Aligned with OpenClaw's tool policy concept from channel policies.
 */
enum class ToolPolicyLevel {
    /** All tools available (DM / local Android app) */
    FULL,
    /** Sensitive tools blocked (group chats) */
    RESTRICTED,
    /** No tools available (future use) */
    NONE,
}

/**
 * ToolPolicyResolver — Resolve tool access policy based on chat context.
 * Aligned with OpenClaw resolveToolPolicy.
 */
object ToolPolicyResolver {

    /**
     * Tools restricted in group/shared contexts.
     * These tools access personal data or sensitive configuration that
     * should not be exposed in multi-user environments.
     */
    private val GROUP_RESTRICTED_TOOLS = setOf(
        // Memory tools — personal context that should not leak to strangers
        "memory_search",
        "memory_get",
        // Config tools — may expose API keys, tokens, credentials
        "config_get",
        "config_set",
    )

    /**
     * Resolve tool policy level for a given chat type.
     * Aligned with OpenClaw resolveToolPolicy.
     *
     * @param chatType The chat type string ("p2p"/"direct"/"group"/"channel"/"thread", or null)
     * @return ToolPolicyLevel determining which tools are available
     */
    fun resolveToolPolicy(chatType: String?): ToolPolicyLevel {
        return when (chatType?.lowercase()) {
            null, "p2p", "direct" -> ToolPolicyLevel.FULL
            "group", "channel", "thread" -> ToolPolicyLevel.RESTRICTED
            else -> ToolPolicyLevel.FULL
        }
    }

    /**
     * Check if a specific tool is allowed under the given policy.
     *
     * @param toolName The tool name to check
     * @param policy The resolved policy level
     * @return true if the tool is allowed
     */
    fun isToolAllowed(toolName: String, policy: ToolPolicyLevel): Boolean {
        return when (policy) {
            ToolPolicyLevel.FULL -> true
            ToolPolicyLevel.RESTRICTED -> toolName !in GROUP_RESTRICTED_TOOLS
            ToolPolicyLevel.NONE -> false
        }
    }

    /**
     * Get the set of restricted tool names (for prompt generation).
     */
    fun getRestrictedToolNames(): Set<String> = GROUP_RESTRICTED_TOOLS
}
