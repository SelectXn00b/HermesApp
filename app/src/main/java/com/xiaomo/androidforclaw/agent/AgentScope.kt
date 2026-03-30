package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/agent-scope.ts
 *
 * AndroidForClaw adaptation: per-agent configuration resolution.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * Resolved agent configuration.
 * Aligned with OpenClaw ResolvedAgentConfig.
 */
data class ResolvedAgentConfig(
    val name: String? = null,
    val workspace: String? = null,
    val agentDir: String? = null,
    val model: String? = null,
    val memorySearch: Boolean = true,
    val identity: IdentityConfig? = null
)

/**
 * Default agent ID.
 */
const val DEFAULT_AGENT_ID = "default"

/**
 * Agent scope — per-agent configuration resolution.
 * Aligned with OpenClaw agent-scope.ts.
 */
object AgentScope {

    /**
     * Resolve the default agent ID from config.
     * Aligned with OpenClaw resolveDefaultAgentId.
     */
    fun resolveDefaultAgentId(cfg: OpenClawConfig): String {
        // Android currently supports single-agent mode
        // Future: read from agents list in config
        return DEFAULT_AGENT_ID
    }

    /**
     * Resolve agent configuration by ID.
     * Aligned with OpenClaw resolveAgentConfig.
     */
    fun resolveAgentConfig(cfg: OpenClawConfig, agentId: String?): ResolvedAgentConfig {
        val identity = AgentIdentity.resolveAgentIdentity(cfg, agentId)
        val model = resolveAgentEffectiveModelPrimary(cfg, agentId)

        return ResolvedAgentConfig(
            name = identity.name,
            model = model,
            identity = identity
        )
    }

    /**
     * Resolve the effective primary model for an agent.
     * Agent-level override takes precedence over global default.
     *
     * Aligned with OpenClaw resolveAgentEffectiveModelPrimary.
     */
    fun resolveAgentEffectiveModelPrimary(cfg: OpenClawConfig, agentId: String?): String? {
        // Agent-level model override (future: read from agents[agentId].model)
        // Fall back to global default
        return cfg.agents?.defaults?.model?.primary
    }

    /**
     * Resolve effective model fallbacks combining agent and global config.
     * Aligned with OpenClaw resolveEffectiveModelFallbacks.
     */
    fun resolveEffectiveModelFallbacks(
        cfg: OpenClawConfig,
        agentId: String?,
        hasSessionModelOverride: Boolean = false
    ): List<String> {
        // If session has a model override, don't use global fallbacks
        // (the user explicitly chose a model)
        if (hasSessionModelOverride) return emptyList()

        // Agent-level fallbacks override (future: agents[agentId].model.fallbacks)
        // Fall back to global
        return cfg.agents?.defaults?.model?.fallbacks ?: emptyList()
    }

    /**
     * Resolve session-specific agent IDs from a session key.
     * Aligned with OpenClaw resolveSessionAgentIds.
     */
    fun resolveSessionAgentIds(
        sessionKey: String?,
        cfg: OpenClawConfig,
        agentId: String? = null
    ): Pair<String, String> {
        val defaultId = resolveDefaultAgentId(cfg)
        val sessionId = agentId ?: defaultId
        return defaultId to sessionId
    }
}
