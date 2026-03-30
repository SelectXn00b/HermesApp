/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-control.ts (resolveSubagentController, controlScope)
 * - ../openclaw/src/agents/tools/sessions-list-tool.ts (visibility filtering)
 *
 * AndroidForClaw adaptation: visibility and access control for session tools.
 * Controls which sessions a caller can see or interact with based on their
 * relationship in the spawn tree.
 */
package com.xiaomo.androidforclaw.agent.subagent

import com.xiaomo.androidforclaw.logging.Log

/**
 * Visibility modes for session tools.
 * Aligned with OpenClaw controlScope / SessionToolsVisibility.
 */
enum class SessionToolsVisibility {
    /** Can only see/control self */
    SELF,
    /** Can see/control self + descendants (default) */
    TREE,
    /** Can see/control all sessions in same agent (Android: single device = always ok) */
    AGENT,
    /** Can see/control all sessions globally */
    ALL,
}

/**
 * Result of an access check.
 */
sealed class SessionAccessResult {
    data object Allowed : SessionAccessResult()
    data class Denied(val reason: String) : SessionAccessResult()
}

/**
 * Guard for session tool access based on visibility scope.
 * Aligned with OpenClaw resolveSubagentController + controlScope.
 *
 * On Android, since all sessions run in-process on a single device,
 * AGENT and ALL are equivalent and always allow access.
 */
object SessionVisibilityGuard {

    private const val TAG = "SessionVisibilityGuard"

    /**
     * Resolve the visibility/control scope for a caller session.
     * Aligned with OpenClaw resolveSubagentController.
     *
     * - Non-subagent callers (main session) → TREE (full control of children)
     * - ORCHESTRATOR subagents → TREE (can control their children)
     * - LEAF subagents → SELF (cannot control others, scope "none" in OpenClaw)
     */
    fun resolveVisibility(
        callerSessionKey: String,
        registry: SubagentRegistry,
        maxSpawnDepth: Int = DEFAULT_MAX_SPAWN_DEPTH,
    ): SessionToolsVisibility {
        // Check if caller is a subagent
        val callerRun = registry.getRunByChildSessionKey(callerSessionKey)

        if (callerRun == null) {
            // Not a subagent — main session, full tree control
            return SessionToolsVisibility.TREE
        }

        // Resolve capabilities using config maxSpawnDepth (aligned with OpenClaw)
        val capabilities = resolveSubagentCapabilities(callerRun.depth, maxSpawnDepth)
        return if (capabilities.controlScope == SubagentControlScope.NONE) {
            // LEAF: controlScope = "none" → SELF visibility
            SessionToolsVisibility.SELF
        } else {
            // MAIN/ORCHESTRATOR: controlScope = "children" → TREE visibility
            SessionToolsVisibility.TREE
        }
    }

    /**
     * Check if a caller has access to a target session.
     *
     * @param action Description of the action for error messages (e.g., "list", "history", "send")
     * @param callerSessionKey The session making the request
     * @param targetSessionKey The session being accessed
     * @param visibility Resolved visibility scope of the caller
     * @param registry SubagentRegistry for spawn tree traversal
     * @return SessionAccessResult.Allowed or SessionAccessResult.Denied
     */
    fun checkAccess(
        action: String,
        callerSessionKey: String,
        targetSessionKey: String,
        visibility: SessionToolsVisibility,
        registry: SubagentRegistry,
    ): SessionAccessResult {
        return when (visibility) {
            SessionToolsVisibility.ALL -> {
                // Can access everything
                SessionAccessResult.Allowed
            }

            SessionToolsVisibility.AGENT -> {
                // Android: single agent, always allowed
                SessionAccessResult.Allowed
            }

            SessionToolsVisibility.TREE -> {
                // Can access self + descendants
                if (callerSessionKey == targetSessionKey) {
                    return SessionAccessResult.Allowed
                }
                if (isDescendant(callerSessionKey, targetSessionKey, registry)) {
                    return SessionAccessResult.Allowed
                }
                SessionAccessResult.Denied(
                    "Session $targetSessionKey is not a descendant of $callerSessionKey. " +
                    "You can only $action your own spawned subagents."
                )
            }

            SessionToolsVisibility.SELF -> {
                // Can only access self
                if (callerSessionKey == targetSessionKey) {
                    SessionAccessResult.Allowed
                } else {
                    SessionAccessResult.Denied(
                        "Leaf subagents cannot $action other sessions."
                    )
                }
            }
        }
    }

    /**
     * Check if targetSessionKey is a descendant of ancestorSessionKey.
     * Uses requester-based BFS (aligned with OpenClaw spawnedBy / requesterSessionKey).
     * OpenClaw uses gateway `sessions.list { spawnedBy }` which traverses by requester,
     * not by controller.
     */
    fun isDescendant(
        ancestorSessionKey: String,
        targetSessionKey: String,
        registry: SubagentRegistry,
    ): Boolean {
        // BFS using requesterSessionKey (aligned with OpenClaw spawnedBy)
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        // Get direct children spawned by ancestor (requester-based)
        val children = registry.listRunsForRequester(ancestorSessionKey)
        for (child in children) {
            if (child.childSessionKey == targetSessionKey) return true
            if (visited.add(child.childSessionKey)) {
                queue.add(child.childSessionKey)
            }
        }

        // BFS through descendants
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            val grandchildren = registry.listRunsForRequester(current)
            for (gc in grandchildren) {
                if (gc.childSessionKey == targetSessionKey) return true
                if (visited.add(gc.childSessionKey)) {
                    queue.add(gc.childSessionKey)
                }
            }
        }

        return false
    }

    /**
     * Filter a list of runs to only those visible to the caller.
     * Convenience method for list-type tools.
     */
    fun filterVisible(
        callerSessionKey: String,
        runs: List<SubagentRunRecord>,
        visibility: SessionToolsVisibility,
        registry: SubagentRegistry,
    ): List<SubagentRunRecord> {
        return when (visibility) {
            SessionToolsVisibility.ALL, SessionToolsVisibility.AGENT -> runs
            SessionToolsVisibility.TREE -> {
                runs.filter { run ->
                    run.childSessionKey == callerSessionKey ||
                    run.requesterSessionKey == callerSessionKey ||
                    run.controllerSessionKey == callerSessionKey ||
                    isDescendant(callerSessionKey, run.childSessionKey, registry)
                }
            }
            SessionToolsVisibility.SELF -> {
                runs.filter { it.childSessionKey == callerSessionKey }
            }
        }
    }
}
