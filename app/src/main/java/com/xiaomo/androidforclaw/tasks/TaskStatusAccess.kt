package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-status-access.ts
 *
 * Convenience accessors for task status by session key or agent ID.
 * Aligned 1:1 with TS task-status-access.ts.
 */
object TaskStatusAccess {

    /**
     * List tasks for a session key (for status display).
     * Aligned with TS listTasksForSessionKeyForStatus.
     */
    fun listTasksForSessionKeyForStatus(sessionKey: String): List<TaskRecord> {
        return TaskRegistry.listTasksForSessionKey(sessionKey)
    }

    /**
     * List tasks for an agent ID (for status display).
     * Aligned with TS listTasksForAgentIdForStatus.
     */
    fun listTasksForAgentIdForStatus(agentId: String): List<TaskRecord> {
        return TaskRegistry.listTasksForAgentId(agentId)
    }
}
