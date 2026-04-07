package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-status.ts
 *
 * Task status snapshot and formatting.
 * Aligned 1:1 with TS task-status.ts.
 */

// ---------------------------------------------------------------------------
// Constants — from task-status.ts
// ---------------------------------------------------------------------------

const val TASK_STATUS_RECENT_WINDOW_MS = 5 * 60_000L
const val TASK_STATUS_TITLE_MAX_CHARS = 80
const val TASK_STATUS_DETAIL_MAX_CHARS = 120

// ---------------------------------------------------------------------------
// TaskStatusHelper — text sanitization + formatting
// ---------------------------------------------------------------------------

object TaskStatusHelper {

    private val ACTIVE_STATUSES = setOf(TaskStatus.QUEUED, TaskStatus.RUNNING)
    private val FAILURE_STATUSES = setOf(TaskStatus.FAILED, TaskStatus.TIMED_OUT, TaskStatus.LOST)

    /**
     * Sanitize a task status text value: collapse whitespace, trim.
     * Aligned with TS sanitizeTaskStatusText.
     */
    fun sanitizeTaskStatusText(
        value: Any?,
        errorContext: Boolean = false,
        maxChars: Int? = null,
    ): String {
        val raw = when (value) {
            is String -> value
            null -> ""
            else -> value.toString()
        }
        val sanitized = raw.replace(Regex("\\s+"), " ").trim()
        if (sanitized.isEmpty()) return ""
        if (maxChars != null) return truncateTaskStatusText(sanitized, maxChars)
        return sanitized
    }

    /**
     * Format a task title text with max chars.
     * Aligned with TS formatTaskStatusTitleText.
     */
    fun formatTaskStatusTitleText(value: Any?, fallback: String = "Background task"): String {
        return sanitizeTaskStatusText(value, maxChars = TASK_STATUS_TITLE_MAX_CHARS).ifEmpty { fallback }
    }

    /**
     * Format the task status title from a TaskRecord.
     * Aligned with TS formatTaskStatusTitle.
     */
    fun formatTaskStatusTitle(task: TaskRecord): String {
        return formatTaskStatusTitleText(task.label?.trim()?.ifEmpty { null } ?: task.task.trim())
    }

    /**
     * Format a detail string (progress or error).
     * Aligned with TS formatTaskStatusDetail.
     */
    fun formatTaskStatusDetail(task: TaskRecord): String? {
        if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
            return sanitizeTaskStatusText(task.progressSummary, maxChars = TASK_STATUS_DETAIL_MAX_CHARS).ifEmpty { null }
        }
        val sanitizedError = sanitizeTaskStatusText(task.error, errorContext = true, maxChars = TASK_STATUS_DETAIL_MAX_CHARS)
        if (sanitizedError.isNotEmpty()) return sanitizedError
        return sanitizeTaskStatusText(task.terminalSummary, errorContext = true, maxChars = TASK_STATUS_DETAIL_MAX_CHARS).ifEmpty { null }
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private fun truncateTaskStatusText(value: String, maxChars: Int): String {
        val trimmed = value.trim()
        if (trimmed.length <= maxChars) return trimmed
        return "${trimmed.take(maxOf(0, maxChars - 1)).trimEnd()}\u2026"
    }

    private fun isActiveTask(task: TaskRecord): Boolean = task.status in ACTIVE_STATUSES

    private fun isFailureTask(task: TaskRecord): Boolean = task.status in FAILURE_STATUSES

    private fun resolveTaskReferenceAt(task: TaskRecord): Long {
        return if (isActiveTask(task)) {
            task.lastEventAt ?: task.startedAt ?: task.createdAt
        } else {
            task.endedAt ?: task.lastEventAt ?: task.startedAt ?: task.createdAt
        }
    }

    private fun isExpiredTask(task: TaskRecord, now: Long): Boolean {
        val cleanup = task.cleanupAfter ?: return false
        return cleanup <= now
    }

    private fun isRecentTerminalTask(task: TaskRecord, now: Long): Boolean {
        if (isActiveTask(task)) return false
        return now - resolveTaskReferenceAt(task) <= TASK_STATUS_RECENT_WINDOW_MS
    }

    /**
     * Build a snapshot of visible tasks for status display.
     * Aligned with TS buildTaskStatusSnapshot.
     */
    fun buildTaskStatusSnapshot(tasks: List<TaskRecord>, now: Long = System.currentTimeMillis()): TaskStatusSnapshot {
        val visibleCandidates = tasks.filter { !isExpiredTask(it, now) }
        val active = visibleCandidates.filter { isActiveTask(it) }
        val recentTerminal = visibleCandidates.filter { isRecentTerminalTask(it, now) }
        val visible = if (active.isNotEmpty()) active + recentTerminal else recentTerminal
        val focus = active.firstOrNull()
            ?: recentTerminal.firstOrNull { isFailureTask(it) }
            ?: recentTerminal.firstOrNull()

        return TaskStatusSnapshot(
            latest = active.firstOrNull() ?: recentTerminal.firstOrNull(),
            focus = focus,
            visible = visible,
            active = active,
            recentTerminal = recentTerminal,
            activeCount = active.size,
            totalCount = visible.size,
            recentFailureCount = recentTerminal.count { isFailureTask(it) },
        )
    }
}

// ---------------------------------------------------------------------------
// TaskStatusSnapshot — from task-status.ts
// ---------------------------------------------------------------------------

data class TaskStatusSnapshot(
    val latest: TaskRecord? = null,
    val focus: TaskRecord? = null,
    val visible: List<TaskRecord> = emptyList(),
    val active: List<TaskRecord> = emptyList(),
    val recentTerminal: List<TaskRecord> = emptyList(),
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val recentFailureCount: Int = 0,
)
