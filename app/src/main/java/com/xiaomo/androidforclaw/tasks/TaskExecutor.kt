package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-executor.ts
 *
 * Task execution engine: create, start, complete, fail, cancel task runs.
 * Coordinates between TaskRegistry and TaskFlowRegistry.
 * Aligned 1:1 with TS task-executor.ts.
 */
object TaskExecutor {

    // ---------------------------------------------------------------------------
    // Create queued task run — aligned with TS createQueuedTaskRun
    // ---------------------------------------------------------------------------

    fun createQueuedTaskRun(params: CreateTaskRunParams): TaskRecord {
        val task = TaskRegistry.createTaskRecord(
            CreateTaskParams(
                runtime = params.runtime,
                taskKind = params.taskKind,
                sourceId = params.sourceId,
                requesterSessionKey = params.requesterSessionKey,
                ownerKey = params.ownerKey,
                scopeKind = params.scopeKind,
                parentFlowId = params.parentFlowId,
                childSessionKey = params.childSessionKey,
                parentTaskId = params.parentTaskId,
                agentId = params.agentId,
                runId = params.runId,
                label = params.label,
                task = params.task,
                status = TaskStatus.QUEUED,
                notifyPolicy = params.notifyPolicy,
                deliveryStatus = params.deliveryStatus,
                preferMetadata = params.preferMetadata,
            )
        )
        return ensureSingleTaskFlow(task, params.requesterOrigin)
    }

    // ---------------------------------------------------------------------------
    // Create running task run — aligned with TS createRunningTaskRun
    // ---------------------------------------------------------------------------

    fun createRunningTaskRun(params: CreateRunningTaskRunParams): TaskRecord {
        val task = TaskRegistry.createTaskRecord(
            CreateTaskParams(
                runtime = params.runtime,
                taskKind = params.taskKind,
                sourceId = params.sourceId,
                requesterSessionKey = params.requesterSessionKey,
                ownerKey = params.ownerKey,
                scopeKind = params.scopeKind,
                parentFlowId = params.parentFlowId,
                childSessionKey = params.childSessionKey,
                parentTaskId = params.parentTaskId,
                agentId = params.agentId,
                runId = params.runId,
                label = params.label,
                task = params.task,
                status = TaskStatus.RUNNING,
                notifyPolicy = params.notifyPolicy,
                deliveryStatus = params.deliveryStatus,
                preferMetadata = params.preferMetadata,
                startedAt = params.startedAt,
                lastEventAt = params.lastEventAt,
                progressSummary = params.progressSummary,
            )
        )
        return ensureSingleTaskFlow(task, params.requesterOrigin)
    }

    // ---------------------------------------------------------------------------
    // Start / progress / complete / fail — proxy to TaskRegistry
    // ---------------------------------------------------------------------------

    fun startTaskRunByRunId(params: MarkRunningParams): TaskRecord? {
        return TaskRegistry.markTaskRunningByRunId(params)
    }

    fun recordTaskRunProgressByRunId(params: RecordProgressParams): TaskRecord? {
        return TaskRegistry.recordTaskProgressByRunId(params)
    }

    fun completeTaskRunByRunId(params: CompleteTaskRunParams): TaskRecord? {
        return TaskRegistry.markTaskTerminalByRunId(
            MarkTerminalByRunIdParams(
                runId = params.runId,
                runtime = params.runtime,
                sessionKey = params.sessionKey,
                status = TaskStatus.SUCCEEDED,
                endedAt = params.endedAt,
                lastEventAt = params.lastEventAt,
                progressSummary = params.progressSummary,
                terminalSummary = params.terminalSummary,
                terminalOutcome = params.terminalOutcome,
            )
        )
    }

    fun failTaskRunByRunId(params: FailTaskRunParams): TaskRecord? {
        return TaskRegistry.markTaskTerminalByRunId(
            MarkTerminalByRunIdParams(
                runId = params.runId,
                runtime = params.runtime,
                sessionKey = params.sessionKey,
                status = params.status ?: TaskStatus.FAILED,
                endedAt = params.endedAt,
                lastEventAt = params.lastEventAt,
                error = params.error,
                progressSummary = params.progressSummary,
                terminalSummary = params.terminalSummary,
            )
        )
    }

    fun markTaskRunLostById(params: MarkLostParams): TaskRecord? {
        return TaskRegistry.markTaskLostById(params)
    }

    fun setDetachedTaskDeliveryStatusByRunId(params: SetDeliveryStatusParams): TaskRecord? {
        return TaskRegistry.setTaskRunDeliveryStatusByRunId(params)
    }

    // ---------------------------------------------------------------------------
    // Flow task summary
    // ---------------------------------------------------------------------------

    fun getFlowTaskSummary(flowId: String): TaskRegistrySummary {
        return TaskRegistrySummaryHelper.summarizeTaskRecords(TaskRegistry.listTasksForFlowId(flowId))
    }

    // ---------------------------------------------------------------------------
    // Run task in flow — aligned with TS runTaskInFlow
    // ---------------------------------------------------------------------------

    fun runTaskInFlow(params: RunTaskInFlowParams): RunTaskInFlowResult {
        val flow = TaskFlowRegistry.getTaskFlowById(params.flowId)
            ?: return RunTaskInFlowResult(found = false, created = false, reason = "Flow not found.")
        if (flow.syncMode != TaskFlowSyncMode.MANAGED) {
            return RunTaskInFlowResult(found = true, created = false, reason = "Flow does not accept managed child tasks.", flow = flow)
        }
        if (flow.cancelRequestedAt != null) {
            return RunTaskInFlowResult(found = true, created = false, reason = "Flow cancellation has already been requested.", flow = flow)
        }
        if (isTerminalFlowStatus(flow.status)) {
            return RunTaskInFlowResult(found = true, created = false, reason = "Flow is already ${flow.status.value}.", flow = flow)
        }

        val task: TaskRecord
        try {
            task = if (params.status == TaskStatus.RUNNING) {
                createRunningTaskRun(
                    CreateRunningTaskRunParams(
                        runtime = params.runtime,
                        sourceId = params.sourceId,
                        ownerKey = flow.ownerKey,
                        scopeKind = TaskScopeKind.SESSION,
                        requesterOrigin = flow.requesterOrigin,
                        parentFlowId = flow.flowId,
                        childSessionKey = params.childSessionKey,
                        parentTaskId = params.parentTaskId,
                        agentId = params.agentId,
                        runId = params.runId,
                        label = params.label,
                        task = params.task,
                        notifyPolicy = params.notifyPolicy,
                        deliveryStatus = params.deliveryStatus ?: TaskDeliveryStatus.PENDING,
                        startedAt = params.startedAt,
                        lastEventAt = params.lastEventAt,
                        progressSummary = params.progressSummary,
                    )
                )
            } else {
                createQueuedTaskRun(
                    CreateTaskRunParams(
                        runtime = params.runtime,
                        sourceId = params.sourceId,
                        ownerKey = flow.ownerKey,
                        scopeKind = TaskScopeKind.SESSION,
                        requesterOrigin = flow.requesterOrigin,
                        parentFlowId = flow.flowId,
                        childSessionKey = params.childSessionKey,
                        parentTaskId = params.parentTaskId,
                        agentId = params.agentId,
                        runId = params.runId,
                        label = params.label,
                        task = params.task,
                        notifyPolicy = params.notifyPolicy,
                        deliveryStatus = params.deliveryStatus ?: TaskDeliveryStatus.PENDING,
                    )
                )
            }
        } catch (error: ParentFlowLinkError) {
            return mapRunTaskInFlowCreateError(error, params.flowId)
        }

        return RunTaskInFlowResult(
            found = true,
            created = true,
            flow = TaskFlowRegistry.getTaskFlowById(flow.flowId) ?: flow,
            task = task,
        )
    }

    fun runTaskInFlowForOwner(params: RunTaskInFlowForOwnerParams): RunTaskInFlowResult {
        val flow = TaskFlowOwnerAccess.getTaskFlowByIdForOwner(params.flowId, params.callerOwnerKey)
            ?: return RunTaskInFlowResult(found = false, created = false, reason = "Flow not found.")
        return runTaskInFlow(
            RunTaskInFlowParams(
                flowId = flow.flowId,
                runtime = params.runtime,
                sourceId = params.sourceId,
                childSessionKey = params.childSessionKey,
                parentTaskId = params.parentTaskId,
                agentId = params.agentId,
                runId = params.runId,
                label = params.label,
                task = params.task,
                preferMetadata = params.preferMetadata,
                notifyPolicy = params.notifyPolicy,
                deliveryStatus = params.deliveryStatus,
                status = params.status,
                startedAt = params.startedAt,
                lastEventAt = params.lastEventAt,
                progressSummary = params.progressSummary,
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Cancel flow — aligned with TS cancelFlowById
    // ---------------------------------------------------------------------------

    suspend fun cancelFlowById(flowId: String): CancelFlowResult {
        val flow = TaskFlowRegistry.getTaskFlowById(flowId)
            ?: return CancelFlowResult(found = false, cancelled = false, reason = "Flow not found.")
        if (isTerminalFlowStatus(flow.status)) {
            return CancelFlowResult(
                found = true,
                cancelled = false,
                reason = "Flow is already ${flow.status.value}.",
                flow = flow,
                tasks = TaskRegistry.listTasksForFlowId(flow.flowId),
            )
        }

        // Mark cancel requested
        val cancelResult = if (flow.cancelRequestedAt == null) {
            TaskFlowRegistry.requestFlowCancel(flow.flowId, flow.revision)
        } else null

        if (cancelResult != null && !cancelResult.applied) {
            return CancelFlowResult(
                found = true,
                cancelled = false,
                reason = "Flow changed while cancellation was in progress.",
                flow = cancelResult.current,
                tasks = TaskRegistry.listTasksForFlowId(flow.flowId),
            )
        }

        // Cancel active tasks
        val linkedTasks = TaskRegistry.listTasksForFlowId(flow.flowId)
        val activeTasks = linkedTasks.filter { isActiveTaskStatus(it.status) }
        for (task in activeTasks) {
            TaskRegistry.cancelTaskById(task.taskId)
        }

        // Check if all settled
        val refreshedTasks = TaskRegistry.listTasksForFlowId(flow.flowId)
        val remainingActive = refreshedTasks.filter { isActiveTaskStatus(it.status) }
        if (remainingActive.isNotEmpty()) {
            return CancelFlowResult(
                found = true,
                cancelled = false,
                reason = "One or more child tasks are still active.",
                flow = TaskFlowRegistry.getTaskFlowById(flow.flowId),
                tasks = refreshedTasks,
            )
        }

        // Mark flow cancelled
        val now = System.currentTimeMillis()
        val refreshedFlow = TaskFlowRegistry.getTaskFlowById(flow.flowId) ?: flow
        if (isTerminalFlowStatus(refreshedFlow.status)) {
            return CancelFlowResult(
                found = true,
                cancelled = refreshedFlow.status == TaskFlowStatus.CANCELLED,
                flow = refreshedFlow,
                tasks = refreshedTasks,
            )
        }

        val updated = TaskFlowRegistry.updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flow.flowId,
                expectedRevision = refreshedFlow.revision,
                patch = FlowPatch(
                    status = TaskFlowStatus.CANCELLED,
                    clearBlockedTaskId = true,
                    clearBlockedSummary = true,
                    clearWaitJson = true,
                    endedAt = now,
                    updatedAt = now,
                ),
            )
        )

        return if (updated.applied) {
            CancelFlowResult(found = true, cancelled = true, flow = updated.flow, tasks = refreshedTasks)
        } else {
            CancelFlowResult(
                found = true,
                cancelled = false,
                reason = "Flow changed while cancellation was in progress.",
                flow = updated.current,
                tasks = refreshedTasks,
            )
        }
    }

    suspend fun cancelFlowByIdForOwner(flowId: String, callerOwnerKey: String): CancelFlowResult {
        val flow = TaskFlowOwnerAccess.getTaskFlowByIdForOwner(flowId, callerOwnerKey)
            ?: return CancelFlowResult(found = false, cancelled = false, reason = "Flow not found.")
        return cancelFlowById(flow.flowId)
    }

    suspend fun cancelDetachedTaskRunById(taskId: String): TaskRecord? {
        return TaskRegistry.cancelTaskById(taskId)
    }

    // ---------------------------------------------------------------------------
    // Retry blocked flow — aligned with TS retryBlockedFlowAsQueuedTaskRun / AsRunningTaskRun
    // ---------------------------------------------------------------------------

    fun retryBlockedFlowAsQueuedTaskRun(params: RetryBlockedFlowParams): RetryBlockedFlowResult {
        return retryBlockedFlowTask(params.copy(status = TaskStatus.QUEUED))
    }

    fun retryBlockedFlowAsRunningTaskRun(params: RetryBlockedFlowParams): RetryBlockedFlowResult {
        return retryBlockedFlowTask(params.copy(status = TaskStatus.RUNNING))
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private fun isOneTaskFlowEligible(task: TaskRecord): Boolean {
        if (!task.parentFlowId.isNullOrBlank() || task.scopeKind != TaskScopeKind.SESSION) return false
        if (task.deliveryStatus == TaskDeliveryStatus.NOT_APPLICABLE) return false
        return task.runtime == TaskRuntime.ACP || task.runtime == TaskRuntime.SUBAGENT
    }

    private fun ensureSingleTaskFlow(task: TaskRecord, requesterOrigin: DeliveryContext? = null): TaskRecord {
        if (!isOneTaskFlowEligible(task)) return task
        return try {
            val flow = TaskFlowRegistry.createTaskFlowForTask(
                CreateFlowForTaskParams(task = task, requesterOrigin = requesterOrigin)
            )
            val linked = TaskRegistry.linkTaskToFlowById(task.taskId, flow.flowId)
            if (linked == null) {
                TaskFlowRegistry.deleteTaskFlowRecordById(flow.flowId)
                return task
            }
            if (linked.parentFlowId != flow.flowId) {
                TaskFlowRegistry.deleteTaskFlowRecordById(flow.flowId)
                return linked
            }
            linked
        } catch (_: Exception) {
            task
        }
    }

    private fun retryBlockedFlowTask(params: RetryBlockedFlowParams): RetryBlockedFlowResult {
        val flow = TaskFlowRegistry.getTaskFlowById(params.flowId)
            ?: return RetryBlockedFlowResult(found = false, retried = false, reason = "Flow not found.")
        val latestTask = TaskRegistry.findLatestTaskForFlowId(params.flowId)
            ?: return RetryBlockedFlowResult(found = true, retried = false, reason = "Flow has no retryable task.")
        if (flow.status != TaskFlowStatus.BLOCKED) {
            return RetryBlockedFlowResult(found = true, retried = false, reason = "Flow is not blocked.", previousTask = latestTask)
        }
        if (latestTask.status != TaskStatus.SUCCEEDED || latestTask.terminalOutcome != TaskTerminalOutcome.BLOCKED) {
            return RetryBlockedFlowResult(found = true, retried = false, reason = "Latest TaskFlow task is not blocked.", previousTask = latestTask)
        }

        val task = TaskRegistry.createTaskRecord(
            CreateTaskParams(
                runtime = latestTask.runtime,
                sourceId = params.sourceId ?: latestTask.sourceId,
                ownerKey = flow.ownerKey,
                scopeKind = TaskScopeKind.SESSION,
                parentFlowId = flow.flowId,
                childSessionKey = params.childSessionKey,
                parentTaskId = latestTask.taskId,
                agentId = params.agentId ?: latestTask.agentId,
                runId = params.runId,
                label = params.label ?: latestTask.label,
                task = params.task ?: latestTask.task,
                notifyPolicy = params.notifyPolicy ?: latestTask.notifyPolicy,
                deliveryStatus = params.deliveryStatus ?: TaskDeliveryStatus.PENDING,
                status = params.status,
                startedAt = params.startedAt,
                lastEventAt = params.lastEventAt,
                progressSummary = params.progressSummary,
            )
        )
        return RetryBlockedFlowResult(found = true, retried = true, previousTask = latestTask, task = task)
    }

    private fun mapRunTaskInFlowCreateError(error: ParentFlowLinkError, flowId: String): RunTaskInFlowResult {
        val flow = TaskFlowRegistry.getTaskFlowById(flowId)
        return when (error.code) {
            "cancel_requested" -> RunTaskInFlowResult(
                found = true, created = false,
                reason = "Flow cancellation has already been requested.",
                flow = flow,
            )
            "terminal" -> RunTaskInFlowResult(
                found = true, created = false,
                reason = "Flow is already ${error.details?.get("status") ?: "terminal"}.",
                flow = flow,
            )
            "parent_flow_not_found" -> RunTaskInFlowResult(
                found = false, created = false,
                reason = "Flow not found.",
            )
            else -> throw error
        }
    }
}

// ---------------------------------------------------------------------------
// Params and result types
// ---------------------------------------------------------------------------

data class CreateTaskRunParams(
    val runtime: TaskRuntime,
    val taskKind: String? = null,
    val sourceId: String? = null,
    val requesterSessionKey: String? = null,
    val ownerKey: String? = null,
    val scopeKind: TaskScopeKind? = null,
    val requesterOrigin: DeliveryContext? = null,
    val parentFlowId: String? = null,
    val childSessionKey: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val preferMetadata: Boolean? = null,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val deliveryStatus: TaskDeliveryStatus? = null,
)

data class CreateRunningTaskRunParams(
    val runtime: TaskRuntime,
    val taskKind: String? = null,
    val sourceId: String? = null,
    val requesterSessionKey: String? = null,
    val ownerKey: String? = null,
    val scopeKind: TaskScopeKind? = null,
    val requesterOrigin: DeliveryContext? = null,
    val parentFlowId: String? = null,
    val childSessionKey: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val deliveryStatus: TaskDeliveryStatus? = null,
    val preferMetadata: Boolean? = null,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
)

data class CompleteTaskRunParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val endedAt: Long,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
    val terminalSummary: String? = null,
    val terminalOutcome: TaskTerminalOutcome? = null,
)

data class FailTaskRunParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val status: TaskStatus? = null, // "failed" | "timed_out" | "cancelled"
    val endedAt: Long,
    val lastEventAt: Long? = null,
    val error: String? = null,
    val progressSummary: String? = null,
    val terminalSummary: String? = null,
)

data class RunTaskInFlowParams(
    val flowId: String,
    val runtime: TaskRuntime,
    val sourceId: String? = null,
    val childSessionKey: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val preferMetadata: Boolean? = null,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val deliveryStatus: TaskDeliveryStatus? = null,
    val status: TaskStatus? = null,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
)

data class RunTaskInFlowForOwnerParams(
    val flowId: String,
    val callerOwnerKey: String,
    val runtime: TaskRuntime,
    val sourceId: String? = null,
    val childSessionKey: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val preferMetadata: Boolean? = null,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val deliveryStatus: TaskDeliveryStatus? = null,
    val status: TaskStatus? = null,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
)

data class RunTaskInFlowResult(
    val found: Boolean,
    val created: Boolean,
    val reason: String? = null,
    val flow: TaskFlowRecord? = null,
    val task: TaskRecord? = null,
)

data class CancelFlowResult(
    val found: Boolean,
    val cancelled: Boolean,
    val reason: String? = null,
    val flow: TaskFlowRecord? = null,
    val tasks: List<TaskRecord>? = null,
)

data class RetryBlockedFlowParams(
    val flowId: String,
    val sourceId: String? = null,
    val requesterOrigin: DeliveryContext? = null,
    val childSessionKey: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String? = null,
    val preferMetadata: Boolean? = null,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val deliveryStatus: TaskDeliveryStatus? = null,
    val status: TaskStatus = TaskStatus.QUEUED,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
)

data class RetryBlockedFlowResult(
    val found: Boolean,
    val retried: Boolean,
    val reason: String? = null,
    val previousTask: TaskRecord? = null,
    val task: TaskRecord? = null,
)
