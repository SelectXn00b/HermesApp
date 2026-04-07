package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-domain-views.ts
 *
 * Domain view mappers for task runs and flows (API-layer serialization).
 * Aligned 1:1 with TS task-domain-views.ts.
 */
object TaskDomainViews {

    // ---------------------------------------------------------------------------
    // View types — simplified from plugins/runtime/task-domain-types.ts
    // ---------------------------------------------------------------------------

    data class TaskRunAggregateSummary(
        val total: Int,
        val active: Int,
        val terminal: Int,
        val failures: Int,
        val byStatus: Map<String, Int>,
        val byRuntime: Map<String, Int>,
    )

    data class TaskRunView(
        val id: String,
        val runtime: String,
        val sourceId: String? = null,
        val sessionKey: String,
        val ownerKey: String,
        val scope: String,
        val childSessionKey: String? = null,
        val flowId: String? = null,
        val parentTaskId: String? = null,
        val agentId: String? = null,
        val runId: String? = null,
        val label: String? = null,
        val title: String,
        val status: String,
        val deliveryStatus: String,
        val notifyPolicy: String,
        val createdAt: Long,
        val startedAt: Long? = null,
        val endedAt: Long? = null,
        val lastEventAt: Long? = null,
        val cleanupAfter: Long? = null,
        val error: String? = null,
        val progressSummary: String? = null,
        val terminalSummary: String? = null,
        val terminalOutcome: String? = null,
    )

    data class TaskFlowView(
        val id: String,
        val ownerKey: String,
        val requesterOrigin: DeliveryContext? = null,
        val status: String,
        val notifyPolicy: String,
        val goal: String,
        val currentStep: String? = null,
        val cancelRequestedAt: Long? = null,
        val createdAt: Long,
        val updatedAt: Long,
        val endedAt: Long? = null,
    )

    data class TaskFlowBlockedInfo(
        val taskId: String? = null,
        val summary: String? = null,
    )

    data class TaskFlowDetail(
        val id: String,
        val ownerKey: String,
        val requesterOrigin: DeliveryContext? = null,
        val status: String,
        val notifyPolicy: String,
        val goal: String,
        val currentStep: String? = null,
        val cancelRequestedAt: Long? = null,
        val createdAt: Long,
        val updatedAt: Long,
        val endedAt: Long? = null,
        val state: JsonValue = null,
        val wait: JsonValue = null,
        val blocked: TaskFlowBlockedInfo? = null,
        val tasks: List<TaskRunView>,
        val taskSummary: TaskRunAggregateSummary,
    )

    // ---------------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------------

    fun mapTaskRunAggregateSummary(summary: TaskRegistrySummary): TaskRunAggregateSummary {
        return TaskRunAggregateSummary(
            total = summary.total,
            active = summary.active,
            terminal = summary.terminal,
            failures = summary.failures,
            byStatus = mapOf(
                "queued" to summary.byStatus.queued,
                "running" to summary.byStatus.running,
                "succeeded" to summary.byStatus.succeeded,
                "failed" to summary.byStatus.failed,
                "timed_out" to summary.byStatus.timedOut,
                "cancelled" to summary.byStatus.cancelled,
                "lost" to summary.byStatus.lost,
            ),
            byRuntime = mapOf(
                "subagent" to summary.byRuntime.subagent,
                "acp" to summary.byRuntime.acp,
                "cli" to summary.byRuntime.cli,
                "cron" to summary.byRuntime.cron,
            ),
        )
    }

    fun mapTaskRunView(task: TaskRecord): TaskRunView {
        return TaskRunView(
            id = task.taskId,
            runtime = task.runtime.value,
            sourceId = task.sourceId,
            sessionKey = task.requesterSessionKey,
            ownerKey = task.ownerKey,
            scope = task.scopeKind.value,
            childSessionKey = task.childSessionKey,
            flowId = task.parentFlowId,
            parentTaskId = task.parentTaskId,
            agentId = task.agentId,
            runId = task.runId,
            label = task.label,
            title = task.task,
            status = task.status.value,
            deliveryStatus = task.deliveryStatus.value,
            notifyPolicy = task.notifyPolicy.value,
            createdAt = task.createdAt,
            startedAt = task.startedAt,
            endedAt = task.endedAt,
            lastEventAt = task.lastEventAt,
            cleanupAfter = task.cleanupAfter,
            error = task.error,
            progressSummary = task.progressSummary,
            terminalSummary = task.terminalSummary,
            terminalOutcome = task.terminalOutcome?.value,
        )
    }

    fun mapTaskRunDetail(task: TaskRecord): TaskRunView = mapTaskRunView(task)

    fun mapTaskFlowView(flow: TaskFlowRecord): TaskFlowView {
        return TaskFlowView(
            id = flow.flowId,
            ownerKey = flow.ownerKey,
            requesterOrigin = flow.requesterOrigin,
            status = flow.status.value,
            notifyPolicy = flow.notifyPolicy.value,
            goal = flow.goal,
            currentStep = flow.currentStep,
            cancelRequestedAt = flow.cancelRequestedAt,
            createdAt = flow.createdAt,
            updatedAt = flow.updatedAt,
            endedAt = flow.endedAt,
        )
    }

    fun mapTaskFlowDetail(
        flow: TaskFlowRecord,
        tasks: List<TaskRecord>,
        summary: TaskRegistrySummary? = null,
    ): TaskFlowDetail {
        val effectiveSummary = summary ?: TaskRegistrySummaryHelper.summarizeTaskRecords(tasks)
        val base = mapTaskFlowView(flow)
        val blocked = if (flow.blockedTaskId != null || flow.blockedSummary != null) {
            TaskFlowBlockedInfo(
                taskId = flow.blockedTaskId,
                summary = flow.blockedSummary,
            )
        } else null

        return TaskFlowDetail(
            id = base.id,
            ownerKey = base.ownerKey,
            requesterOrigin = base.requesterOrigin,
            status = base.status,
            notifyPolicy = base.notifyPolicy,
            goal = base.goal,
            currentStep = base.currentStep,
            cancelRequestedAt = base.cancelRequestedAt,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
            endedAt = base.endedAt,
            state = flow.stateJson,
            wait = flow.waitJson,
            blocked = blocked,
            tasks = tasks.map { mapTaskRunView(it) },
            taskSummary = mapTaskRunAggregateSummary(effectiveSummary),
        )
    }
}
