package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-owner-access.ts
 *
 * Owner-scoped task access: filters tasks by ownerKey.
 * Aligned 1:1 with TS task-owner-access.ts.
 */
object TaskOwnerAccess {

    private fun normalizeOwnerKey(ownerKey: String?): String? {
        val trimmed = ownerKey?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun canOwnerAccessTask(task: TaskRecord, callerOwnerKey: String): Boolean {
        return task.scopeKind == TaskScopeKind.SESSION &&
            normalizeOwnerKey(task.ownerKey) == normalizeOwnerKey(callerOwnerKey)
    }

    /**
     * Get a task by ID, only if the caller owns it.
     * Aligned with TS getTaskByIdForOwner.
     */
    fun getTaskByIdForOwner(taskId: String, callerOwnerKey: String): TaskRecord? {
        val task = TaskRegistry.getTaskById(taskId) ?: return null
        return if (canOwnerAccessTask(task, callerOwnerKey)) task else null
    }

    /**
     * Find a task by run ID, only if the caller owns it.
     * Aligned with TS findTaskByRunIdForOwner.
     */
    fun findTaskByRunIdForOwner(runId: String, callerOwnerKey: String): TaskRecord? {
        val task = TaskRegistry.findTaskByRunId(runId) ?: return null
        return if (canOwnerAccessTask(task, callerOwnerKey)) task else null
    }

    /**
     * List tasks for a session key, filtered by owner.
     * Aligned with TS listTasksForRelatedSessionKeyForOwner.
     */
    fun listTasksForRelatedSessionKeyForOwner(
        relatedSessionKey: String,
        callerOwnerKey: String,
    ): List<TaskRecord> {
        return TaskRegistry.listTasksForRelatedSessionKey(relatedSessionKey)
            .filter { canOwnerAccessTask(it, callerOwnerKey) }
    }

    /**
     * Build a status snapshot for session tasks owned by caller.
     * Aligned with TS buildTaskStatusSnapshotForRelatedSessionKeyForOwner.
     */
    fun buildTaskStatusSnapshotForRelatedSessionKeyForOwner(
        relatedSessionKey: String,
        callerOwnerKey: String,
    ): TaskStatusSnapshot {
        return TaskStatusHelper.buildTaskStatusSnapshot(
            listTasksForRelatedSessionKeyForOwner(relatedSessionKey, callerOwnerKey)
        )
    }

    /**
     * Find the latest task for a session key, owned by caller.
     * Aligned with TS findLatestTaskForRelatedSessionKeyForOwner.
     */
    fun findLatestTaskForRelatedSessionKeyForOwner(
        relatedSessionKey: String,
        callerOwnerKey: String,
    ): TaskRecord? {
        return listTasksForRelatedSessionKeyForOwner(relatedSessionKey, callerOwnerKey).firstOrNull()
    }

    /**
     * Resolve a task from a lookup token, filtered by owner.
     * Aligned with TS resolveTaskForLookupTokenForOwner.
     */
    fun resolveTaskForLookupTokenForOwner(token: String, callerOwnerKey: String): TaskRecord? {
        getTaskByIdForOwner(token, callerOwnerKey)?.let { return it }
        findTaskByRunIdForOwner(token, callerOwnerKey)?.let { return it }
        findLatestTaskForRelatedSessionKeyForOwner(token, callerOwnerKey)?.let { return it }
        val raw = TaskRegistry.resolveTaskForLookupToken(token) ?: return null
        return if (canOwnerAccessTask(raw, callerOwnerKey)) raw else null
    }
}
