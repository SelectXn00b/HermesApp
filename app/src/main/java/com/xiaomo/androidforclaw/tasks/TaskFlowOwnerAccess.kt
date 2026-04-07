package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-flow-owner-access.ts
 *
 * Owner-scoped task flow access: filters flows by ownerKey.
 * Aligned 1:1 with TS task-flow-owner-access.ts.
 */
object TaskFlowOwnerAccess {

    private fun normalizeOwnerKey(ownerKey: String?): String? {
        val trimmed = ownerKey?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun canOwnerAccessFlow(flow: TaskFlowRecord, callerOwnerKey: String): Boolean {
        return normalizeOwnerKey(flow.ownerKey) == normalizeOwnerKey(callerOwnerKey)
    }

    /**
     * Get a flow by ID, only if the caller owns it.
     * Aligned with TS getTaskFlowByIdForOwner.
     */
    fun getTaskFlowByIdForOwner(flowId: String, callerOwnerKey: String): TaskFlowRecord? {
        val flow = TaskFlowRegistry.getTaskFlowById(flowId) ?: return null
        return if (canOwnerAccessFlow(flow, callerOwnerKey)) flow else null
    }

    /**
     * List flows owned by caller.
     * Aligned with TS listTaskFlowsForOwner.
     */
    fun listTaskFlowsForOwner(callerOwnerKey: String): List<TaskFlowRecord> {
        val ownerKey = normalizeOwnerKey(callerOwnerKey) ?: return emptyList()
        return TaskFlowRegistry.listTaskFlowsForOwnerKey(ownerKey)
    }

    /**
     * Find latest flow owned by caller.
     * Aligned with TS findLatestTaskFlowForOwner.
     */
    fun findLatestTaskFlowForOwner(callerOwnerKey: String): TaskFlowRecord? {
        val ownerKey = normalizeOwnerKey(callerOwnerKey) ?: return null
        return TaskFlowRegistry.findLatestTaskFlowForOwnerKey(ownerKey)
    }

    /**
     * Resolve a flow from a lookup token, filtered by owner.
     * Aligned with TS resolveTaskFlowForLookupTokenForOwner.
     */
    fun resolveTaskFlowForLookupTokenForOwner(token: String, callerOwnerKey: String): TaskFlowRecord? {
        val direct = getTaskFlowByIdForOwner(token, callerOwnerKey)
        if (direct != null) return direct

        val normalizedToken = normalizeOwnerKey(token)
        val normalizedCallerOwnerKey = normalizeOwnerKey(callerOwnerKey)
        if (normalizedToken == null || normalizedToken != normalizedCallerOwnerKey) return null
        return findLatestTaskFlowForOwner(normalizedCallerOwnerKey)
    }
}
