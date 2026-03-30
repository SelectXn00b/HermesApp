package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/identity.ts
 * - ../openclaw/src/agents/identity-file.ts
 *
 * AndroidForClaw adaptation: agent identity resolution and IDENTITY.md parsing.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log
import java.io.File

/**
 * Agent identity file data.
 * Aligned with OpenClaw AgentIdentityFile.
 */
data class AgentIdentityFile(
    val name: String? = null,
    val emoji: String? = null,
    val theme: String? = null,
    val creature: String? = null,
    val vibe: String? = null,
    val avatar: String? = null
)

/**
 * Resolved identity config for an agent.
 */
data class IdentityConfig(
    val name: String? = null,
    val emoji: String? = null,
    val theme: String? = null,
    val avatar: String? = null
)

/**
 * Agent identity resolution.
 * Aligned with OpenClaw identity.ts + identity-file.ts.
 */
object AgentIdentity {

    private const val TAG = "AgentIdentity"
    private const val DEFAULT_IDENTITY_FILENAME = "IDENTITY.md"
    private const val DEFAULT_ACK_REACTION = "eyes"

    private val LABEL_PATTERN = Regex("^-\\s*(\\w+):\\s*(.+)$")
    private val PLACEHOLDER_PATTERNS = listOf(
        Regex("^\\[.*]$"),      // [placeholder]
        Regex("^<.*>$"),        // <placeholder>
        Regex("^your\\s", RegexOption.IGNORE_CASE)
    )

    /**
     * Resolve agent identity from config.
     * Aligned with OpenClaw resolveAgentIdentity.
     */
    fun resolveAgentIdentity(cfg: OpenClawConfig, agentId: String? = null): IdentityConfig {
        // For now, Android uses a single default agent
        // Future: look up agents config by agentId
        return IdentityConfig(
            name = "AndroidForClaw",
            emoji = null,
            theme = null,
            avatar = null
        )
    }

    /**
     * Resolve ack reaction emoji.
     * 4-level cascade: channel account > channel > global messages > identity emoji > default "eyes".
     *
     * Aligned with OpenClaw resolveAckReaction.
     */
    fun resolveAckReaction(
        cfg: OpenClawConfig,
        agentId: String? = null,
        channelReaction: String? = null,
        accountReaction: String? = null
    ): String {
        // Level 1: channel account override
        if (!accountReaction.isNullOrBlank()) return accountReaction
        // Level 2: channel override
        if (!channelReaction.isNullOrBlank()) return channelReaction
        // Level 3: global messages.ackReactionScope (not a direct emoji, skip)
        // Level 4: identity emoji
        val identity = resolveAgentIdentity(cfg, agentId)
        if (!identity.emoji.isNullOrBlank()) return identity.emoji
        // Level 5: default
        return DEFAULT_ACK_REACTION
    }

    /**
     * Resolve outgoing message prefix.
     * Aligned with OpenClaw resolveMessagePrefix.
     */
    fun resolveMessagePrefix(cfg: OpenClawConfig, agentId: String? = null): String? {
        val identity = resolveAgentIdentity(cfg, agentId)
        return identity.name?.let { "[$it]" }
    }

    /**
     * Resolve response prefix (for replies).
     * Aligned with OpenClaw resolveResponsePrefix.
     */
    fun resolveResponsePrefix(cfg: OpenClawConfig, agentId: String? = null): String? {
        // Default: no prefix for responses (unlike outgoing messages)
        return null
    }

    /**
     * Resolve identity name for display.
     * Aligned with OpenClaw resolveIdentityName.
     */
    fun resolveIdentityName(cfg: OpenClawConfig, agentId: String? = null): String? {
        return resolveAgentIdentity(cfg, agentId).name
    }

    /**
     * Resolve identity name in bracketed format: "[name]".
     * Aligned with OpenClaw resolveIdentityNamePrefix.
     */
    fun resolveIdentityNamePrefix(cfg: OpenClawConfig, agentId: String? = null): String? {
        val name = resolveIdentityName(cfg, agentId) ?: return null
        return "[$name]"
    }

    // ── IDENTITY.md Parsing (aligned with OpenClaw identity-file.ts) ──

    /**
     * Parse an IDENTITY.md markdown file.
     * Extracts "- label: value" lines, stripping placeholder values.
     *
     * Aligned with OpenClaw parseIdentityMarkdown.
     */
    fun parseIdentityMarkdown(content: String): AgentIdentityFile? {
        val fields = mutableMapOf<String, String>()

        for (line in content.lines()) {
            val match = LABEL_PATTERN.matchEntire(line.trim()) ?: continue
            val label = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()

            // Skip placeholder values
            if (PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }) continue
            if (value.isEmpty()) continue

            fields[label] = value
        }

        if (fields.isEmpty()) return null

        return AgentIdentityFile(
            name = fields["name"],
            emoji = fields["emoji"],
            theme = fields["theme"],
            creature = fields["creature"],
            vibe = fields["vibe"],
            avatar = fields["avatar"]
        )
    }

    /**
     * Load identity from a file path.
     * Aligned with OpenClaw loadIdentityFromFile.
     */
    fun loadIdentityFromFile(path: File): AgentIdentityFile? {
        if (!path.exists()) return null
        return try {
            val content = path.readText()
            parseIdentityMarkdown(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load identity file: ${e.message}")
            null
        }
    }

    /**
     * Load agent identity from workspace.
     * Aligned with OpenClaw loadAgentIdentityFromWorkspace.
     */
    fun loadIdentityFromWorkspace(workspace: File): AgentIdentityFile? {
        val identityFile = File(workspace, DEFAULT_IDENTITY_FILENAME)
        return loadIdentityFromFile(identityFile)
    }

    /**
     * Check if an identity has any non-empty values.
     * Aligned with OpenClaw identityHasValues.
     */
    fun identityHasValues(identity: AgentIdentityFile?): Boolean {
        if (identity == null) return false
        return !identity.name.isNullOrBlank() ||
            !identity.emoji.isNullOrBlank() ||
            !identity.theme.isNullOrBlank() ||
            !identity.creature.isNullOrBlank() ||
            !identity.vibe.isNullOrBlank() ||
            !identity.avatar.isNullOrBlank()
    }
}
