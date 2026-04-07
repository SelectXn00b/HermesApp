package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-executor-policy.ts
 *
 * Task terminal notification / delivery policy functions.
 * Aligned 1:1 with TS task-executor-policy.ts.
 */
object TaskExecutorPolicy {

    /**
     * Format a terminal task message for display.
     * Aligned with TS formatTaskTerminalMessage.
     */
    fun formatTaskTerminalMessage(task: TaskRecord): String {
        val title = resolveTaskDisplayTitle(task)
        val runLabel = resolveTaskRunLabel(task)
        val summary = TaskStatusHelper.sanitizeTaskStatusText(task.terminalSummary,
            errorContext = task.status != TaskStatus.SUCCEEDED || task.terminalOutcome == TaskTerminalOutcome.BLOCKED)

        return when (task.status) {
            TaskStatus.SUCCEEDED -> {
                if (task.terminalOutcome == TaskTerminalOutcome.BLOCKED) {
                    if (summary.isNotEmpty()) "Background task blocked: $title$runLabel. $summary"
                    else "Background task blocked: $title$runLabel."
                } else {
                    if (summary.isNotEmpty()) "Background task done: $title$runLabel. $summary"
                    else "Background task done: $title$runLabel."
                }
            }
            TaskStatus.TIMED_OUT -> "Background task timed out: $title$runLabel."
            TaskStatus.LOST -> {
                val error = TaskStatusHelper.sanitizeTaskStatusText(task.error, errorContext = true)
                val fallback = TaskStatusHelper.sanitizeTaskStatusText(task.terminalSummary, errorContext = true)
                "Background task lost: $title$runLabel. ${error.ifEmpty { fallback.ifEmpty { "Backing session disappeared." } }}"
            }
            TaskStatus.CANCELLED -> "Background task cancelled: $title$runLabel."
            else -> {
                val error = TaskStatusHelper.sanitizeTaskStatusText(task.error, errorContext = true)
                val fallback = TaskStatusHelper.sanitizeTaskStatusText(task.terminalSummary, errorContext = true)
                when {
                    error.isNotEmpty() -> "Background task failed: $title$runLabel. $error"
                    fallback.isNotEmpty() -> "Background task failed: $title$runLabel. $fallback"
                    else -> "Background task failed: $title$runLabel."
                }
            }
        }
    }

    /**
     * Format a blocked follow-up message.
     * Aligned with TS formatTaskBlockedFollowupMessage.
     */
    fun formatTaskBlockedFollowupMessage(task: TaskRecord): String? {
        if (task.status != TaskStatus.SUCCEEDED || task.terminalOutcome != TaskTerminalOutcome.BLOCKED) return null
        val title = resolveTaskDisplayTitle(task)
        val runLabel = resolveTaskRunLabel(task)
        val summary = TaskStatusHelper.sanitizeTaskStatusText(task.terminalSummary, errorContext = true)
            .ifEmpty { "Task is blocked and needs follow-up." }
        return "Task needs follow-up: $title$runLabel. $summary"
    }

    /**
     * Format a state change message (running / progress).
     * Aligned with TS formatTaskStateChangeMessage.
     */
    fun formatTaskStateChangeMessage(task: TaskRecord, event: TaskEventRecord): String? {
        val title = resolveTaskDisplayTitle(task)
        return when (event.kind) {
            TaskEventKind.RUNNING -> "Background task started: $title."
            TaskEventKind.PROGRESS -> {
                val summary = TaskStatusHelper.sanitizeTaskStatusText(event.summary)
                if (summary.isNotEmpty()) "Background task update: $title. $summary" else null
            }
            else -> null
        }
    }

    /**
     * Whether a terminal task update should be auto-delivered.
     * Aligned with TS shouldAutoDeliverTaskTerminalUpdate.
     */
    fun shouldAutoDeliverTaskTerminalUpdate(task: TaskRecord): Boolean {
        if (task.notifyPolicy == TaskNotifyPolicy.SILENT) return false
        if (task.runtime == TaskRuntime.SUBAGENT && task.status != TaskStatus.CANCELLED) return false
        if (!isTerminalTaskStatus(task.status)) return false
        return task.deliveryStatus == TaskDeliveryStatus.PENDING
    }

    /**
     * Whether a state change should be auto-delivered.
     * Aligned with TS shouldAutoDeliverTaskStateChange.
     */
    fun shouldAutoDeliverTaskStateChange(task: TaskRecord): Boolean {
        return task.notifyPolicy == TaskNotifyPolicy.STATE_CHANGES &&
            task.deliveryStatus == TaskDeliveryStatus.PENDING &&
            !isTerminalTaskStatus(task.status)
    }

    /**
     * Whether duplicate terminal delivery should be suppressed.
     * Aligned with TS shouldSuppressDuplicateTerminalDelivery.
     */
    fun shouldSuppressDuplicateTerminalDelivery(task: TaskRecord, preferredTaskId: String?): Boolean {
        if (task.runtime != TaskRuntime.ACP || task.runId.isNullOrBlank()) return false
        return preferredTaskId != null && preferredTaskId != task.taskId
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun resolveTaskDisplayTitle(task: TaskRecord): String {
        val raw = task.label?.trim()?.ifEmpty { null }
            ?: when (task.runtime) {
                TaskRuntime.ACP -> "ACP background task"
                TaskRuntime.SUBAGENT -> "Subagent task"
                else -> task.task.trim().ifEmpty { "Background task" }
            }
        return TaskStatusHelper.formatTaskStatusTitleText(raw)
    }

    private fun resolveTaskRunLabel(task: TaskRecord): String {
        return if (task.runId != null) " (run ${task.runId.take(8)})" else ""
    }
}
