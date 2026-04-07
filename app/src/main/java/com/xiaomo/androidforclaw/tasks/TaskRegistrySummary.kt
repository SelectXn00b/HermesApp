package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-registry.summary.ts
 *
 * Summarize task records into a TaskRegistrySummary.
 * Aligned 1:1 with TS task-registry.summary.ts.
 */
object TaskRegistrySummaryHelper {

    fun createEmptyTaskStatusCounts(): TaskStatusCounts = TaskStatusCounts()

    fun createEmptyTaskRuntimeCounts(): TaskRuntimeCounts = TaskRuntimeCounts()

    fun createEmptyTaskRegistrySummary(): TaskRegistrySummary = TaskRegistrySummary()

    /**
     * Summarize a collection of task records.
     * Aligned with TS summarizeTaskRecords.
     */
    fun summarizeTaskRecords(records: Iterable<TaskRecord>): TaskRegistrySummary {
        var total = 0
        var active = 0
        var terminal = 0
        var failures = 0
        var byStatus = TaskStatusCounts()
        var byRuntime = TaskRuntimeCounts()

        for (task in records) {
            total += 1
            byStatus = byStatus.increment(task.status)
            byRuntime = byRuntime.increment(task.runtime)
            if (task.status == TaskStatus.QUEUED || task.status == TaskStatus.RUNNING) {
                active += 1
            } else {
                terminal += 1
            }
            if (task.status == TaskStatus.FAILED || task.status == TaskStatus.TIMED_OUT || task.status == TaskStatus.LOST) {
                failures += 1
            }
        }

        return TaskRegistrySummary(
            total = total,
            active = active,
            terminal = terminal,
            failures = failures,
            byStatus = byStatus,
            byRuntime = byRuntime,
        )
    }
}
