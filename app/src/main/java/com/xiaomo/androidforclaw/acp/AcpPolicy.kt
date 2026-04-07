package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/policy.ts
 *
 * ACP policy enforcement: checks config for enabled state,
 * dispatch permissions, and agent allowlists.
 */

// ---------------------------------------------------------------------------
// Dispatch policy state  (aligned with TS AcpDispatchPolicyState)
// ---------------------------------------------------------------------------
enum class AcpDispatchPolicyState(val value: String) {
    ENABLED("enabled"),
    ACP_DISABLED("acp_disabled"),
    DISPATCH_DISABLED("dispatch_disabled"),
}

private const val ACP_DISABLED_MESSAGE = "ACP is disabled by policy (`acp.enabled=false`)."
private const val ACP_DISPATCH_DISABLED_MESSAGE =
    "ACP dispatch is disabled by policy (`acp.dispatch.enabled=false`)."

// ---------------------------------------------------------------------------
// ACP runtime error  (aligned with TS AcpRuntimeError)
// ---------------------------------------------------------------------------
class AcpRuntimeError(
    val code: String,
    override val message: String,
) : Exception(message)

// ---------------------------------------------------------------------------
// Policy functions  (aligned with TS policy.ts)
// ---------------------------------------------------------------------------
object AcpPolicy {

    /**
     * Check if ACP is enabled by policy.
     * Aligned with TS isAcpEnabledByPolicy().
     */
    fun isAcpEnabled(cfg: Map<String, Any?>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val acp = cfg["acp"] as? Map<String, Any?> ?: return true
        return acp["enabled"] != false
    }

    /**
     * Resolve the dispatch policy state.
     * Aligned with TS resolveAcpDispatchPolicyState().
     */
    fun resolveDispatchPolicyState(cfg: Map<String, Any?>): AcpDispatchPolicyState {
        if (!isAcpEnabled(cfg)) return AcpDispatchPolicyState.ACP_DISABLED

        @Suppress("UNCHECKED_CAST")
        val acp = cfg["acp"] as? Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val dispatch = acp?.get("dispatch") as? Map<String, Any?>
        if (dispatch?.get("enabled") == false) return AcpDispatchPolicyState.DISPATCH_DISABLED

        return AcpDispatchPolicyState.ENABLED
    }

    /**
     * Check if ACP dispatch is enabled by policy.
     * Aligned with TS isAcpDispatchEnabledByPolicy().
     */
    fun isDispatchEnabled(cfg: Map<String, Any?>): Boolean {
        return resolveDispatchPolicyState(cfg) == AcpDispatchPolicyState.ENABLED
    }

    /**
     * Resolve the dispatch policy message (null if enabled).
     * Aligned with TS resolveAcpDispatchPolicyMessage().
     */
    fun resolveDispatchPolicyMessage(cfg: Map<String, Any?>): String? {
        return when (resolveDispatchPolicyState(cfg)) {
            AcpDispatchPolicyState.ACP_DISABLED -> ACP_DISABLED_MESSAGE
            AcpDispatchPolicyState.DISPATCH_DISABLED -> ACP_DISPATCH_DISABLED_MESSAGE
            AcpDispatchPolicyState.ENABLED -> null
        }
    }

    /**
     * Resolve a dispatch policy error (null if enabled).
     * Aligned with TS resolveAcpDispatchPolicyError().
     */
    fun resolveDispatchPolicyError(cfg: Map<String, Any?>): AcpRuntimeError? {
        val message = resolveDispatchPolicyMessage(cfg) ?: return null
        return AcpRuntimeError("ACP_DISPATCH_DISABLED", message)
    }

    /**
     * Check if an agent ID is allowed by the ACP agent allowlist.
     * Aligned with TS isAcpAgentAllowedByPolicy().
     */
    fun isAgentAllowed(cfg: Map<String, Any?>, agentId: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val acp = cfg["acp"] as? Map<String, Any?> ?: return true

        @Suppress("UNCHECKED_CAST")
        val allowed = (acp["allowedAgents"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.lowercase()?.ifEmpty { null } }
            ?: emptyList()

        if (allowed.isEmpty()) return true
        return allowed.contains(normalizeAgentId(agentId))
    }

    /**
     * Resolve an agent policy error (null if allowed).
     * Aligned with TS resolveAcpAgentPolicyError().
     */
    fun resolveAgentPolicyError(cfg: Map<String, Any?>, agentId: String): AcpRuntimeError? {
        if (isAgentAllowed(cfg, agentId)) return null
        return AcpRuntimeError(
            "ACP_SESSION_INIT_FAILED",
            "ACP agent \"${normalizeAgentId(agentId)}\" is not allowed by policy."
        )
    }

    private fun normalizeAgentId(agentId: String): String =
        agentId.trim().lowercase().ifEmpty { "default" }
}
