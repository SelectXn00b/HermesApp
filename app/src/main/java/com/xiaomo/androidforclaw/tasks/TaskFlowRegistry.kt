package com.xiaomo.androidforclaw.tasks

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-flow-registry.ts + task-flow-runtime-internal.ts
 *
 * In-memory task flow registry with ConcurrentHashMap.
 * Aligned 1:1 with TS task-flow-registry.ts public API.
 * Android adaptation: skip SQLite store for now.
 */
object TaskFlowRegistry {

    // ---------------------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------------------

    /** flowId -> TaskFlowRecord */
    private val flows = ConcurrentHashMap<String, TaskFlowRecord>()

    /** ownerKey -> list of flowIds */
    private val ownerIndex = ConcurrentHashMap<String, MutableList<String>>()

    // ---------------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------------

    fun createFlowRecord(params: CreateFlowParams): TaskFlowRecord {
        val flowId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val record = TaskFlowRecord(
            flowId = flowId,
            syncMode = params.syncMode,
            ownerKey = params.ownerKey,
            requesterOrigin = params.requesterOrigin,
            controllerId = params.controllerId,
            revision = 0,
            status = params.status ?: TaskFlowStatus.QUEUED,
            notifyPolicy = params.notifyPolicy ?: TaskNotifyPolicy.DONE_ONLY,
            goal = params.goal,
            currentStep = params.currentStep,
            stateJson = params.stateJson,
            waitJson = params.waitJson,
            createdAt = now,
            updatedAt = now,
        )
        flows[flowId] = record
        if (record.ownerKey.isNotEmpty()) {
            ownerIndex.getOrPut(record.ownerKey) { mutableListOf() }.add(flowId)
        }
        return record
    }

    /**
     * Create a one-task flow automatically for a detached task.
     * Aligned with TS createTaskFlowForTask.
     */
    fun createTaskFlowForTask(params: CreateFlowForTaskParams): TaskFlowRecord {
        val task = params.task
        return createFlowRecord(
            CreateFlowParams(
                syncMode = TaskFlowSyncMode.TASK_MIRRORED,
                ownerKey = task.ownerKey,
                requesterOrigin = params.requesterOrigin,
                goal = task.task,
                notifyPolicy = task.notifyPolicy,
                status = when (task.status) {
                    TaskStatus.RUNNING -> TaskFlowStatus.RUNNING
                    else -> TaskFlowStatus.QUEUED
                },
            )
        )
    }

    /**
     * Create a managed multi-task flow.
     * Aligned with TS createManagedTaskFlow.
     */
    fun createManagedTaskFlow(params: CreateManagedFlowParams): TaskFlowRecord {
        return createFlowRecord(
            CreateFlowParams(
                syncMode = TaskFlowSyncMode.MANAGED,
                ownerKey = params.ownerKey,
                requesterOrigin = params.requesterOrigin,
                controllerId = params.controllerId,
                goal = params.goal,
                notifyPolicy = params.notifyPolicy,
                status = params.status ?: TaskFlowStatus.QUEUED,
                stateJson = params.stateJson,
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------

    fun getTaskFlowById(flowId: String): TaskFlowRecord? = flows[flowId]

    fun listTaskFlowRecords(): List<TaskFlowRecord> = flows.values.toList()

    fun listTaskFlowsForOwnerKey(ownerKey: String): List<TaskFlowRecord> {
        return ownerIndex[ownerKey]?.mapNotNull { flows[it] } ?: emptyList()
    }

    fun findLatestTaskFlowForOwnerKey(ownerKey: String): TaskFlowRecord? {
        return listTaskFlowsForOwnerKey(ownerKey).maxByOrNull { it.createdAt }
    }

    fun resolveTaskFlowForLookupToken(token: String): TaskFlowRecord? {
        flows[token]?.let { return it }
        return null
    }

    // ---------------------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------------------

    data class TaskFlowUpdateResult(
        val applied: Boolean,
        val flow: TaskFlowRecord,
        val current: TaskFlowRecord? = null,
        val reason: String? = null,
    )

    fun updateFlowRecordByIdExpectedRevision(params: UpdateFlowParams): TaskFlowUpdateResult {
        val current = flows[params.flowId]
            ?: return TaskFlowUpdateResult(
                applied = false,
                flow = TaskFlowRecord(flowId = params.flowId, syncMode = TaskFlowSyncMode.TASK_MIRRORED, ownerKey = "", goal = ""),
                reason = "not_found",
            )
        if (current.revision != params.expectedRevision) {
            return TaskFlowUpdateResult(
                applied = false,
                flow = current,
                current = current,
                reason = "revision_conflict",
            )
        }
        val now = System.currentTimeMillis()
        val updated = current.copy(
            revision = current.revision + 1,
            status = params.patch.status ?: current.status,
            currentStep = if (params.patch.clearCurrentStep) null else (params.patch.currentStep ?: current.currentStep),
            blockedTaskId = if (params.patch.clearBlockedTaskId) null else (params.patch.blockedTaskId ?: current.blockedTaskId),
            blockedSummary = if (params.patch.clearBlockedSummary) null else (params.patch.blockedSummary ?: current.blockedSummary),
            stateJson = if (params.patch.clearStateJson) null else (params.patch.stateJson ?: current.stateJson),
            waitJson = if (params.patch.clearWaitJson) null else (params.patch.waitJson ?: current.waitJson),
            cancelRequestedAt = params.patch.cancelRequestedAt ?: current.cancelRequestedAt,
            endedAt = params.patch.endedAt ?: current.endedAt,
            updatedAt = params.patch.updatedAt ?: now,
        )
        flows[params.flowId] = updated
        return TaskFlowUpdateResult(applied = true, flow = updated)
    }

    /**
     * Sync flow status from a mirrored task.
     * Aligned with TS syncFlowFromTask.
     */
    fun syncFlowFromTask(flowId: String, task: TaskRecord): TaskFlowUpdateResult? {
        val flow = flows[flowId] ?: return null
        if (flow.syncMode != TaskFlowSyncMode.TASK_MIRRORED) return null

        val flowStatus = when (task.status) {
            TaskStatus.QUEUED -> TaskFlowStatus.QUEUED
            TaskStatus.RUNNING -> TaskFlowStatus.RUNNING
            TaskStatus.SUCCEEDED -> if (task.terminalOutcome == TaskTerminalOutcome.BLOCKED) TaskFlowStatus.BLOCKED else TaskFlowStatus.SUCCEEDED
            TaskStatus.FAILED -> TaskFlowStatus.FAILED
            TaskStatus.TIMED_OUT -> TaskFlowStatus.FAILED
            TaskStatus.CANCELLED -> TaskFlowStatus.CANCELLED
            TaskStatus.LOST -> TaskFlowStatus.LOST
        }

        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = flow.revision,
                patch = FlowPatch(
                    status = flowStatus,
                    endedAt = if (isTerminalFlowStatus(flowStatus)) task.endedAt else null,
                    blockedTaskId = if (task.terminalOutcome == TaskTerminalOutcome.BLOCKED) task.taskId else null,
                    blockedSummary = if (task.terminalOutcome == TaskTerminalOutcome.BLOCKED) task.terminalSummary else null,
                ),
            )
        )
    }

    /**
     * Request cancellation of a flow.
     * Aligned with TS requestFlowCancel.
     */
    fun requestFlowCancel(flowId: String, expectedRevision: Int): TaskFlowUpdateResult {
        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = expectedRevision,
                patch = FlowPatch(
                    cancelRequestedAt = System.currentTimeMillis(),
                ),
            )
        )
    }

    /**
     * Mark flow as waiting.
     * Aligned with TS setFlowWaiting.
     */
    fun setFlowWaiting(flowId: String, expectedRevision: Int, waitJson: JsonValue? = null): TaskFlowUpdateResult {
        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = expectedRevision,
                patch = FlowPatch(
                    status = TaskFlowStatus.WAITING,
                    waitJson = waitJson,
                ),
            )
        )
    }

    /**
     * Resume a waiting flow.
     * Aligned with TS resumeFlow.
     */
    fun resumeFlow(flowId: String, expectedRevision: Int): TaskFlowUpdateResult {
        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = expectedRevision,
                patch = FlowPatch(
                    status = TaskFlowStatus.RUNNING,
                    clearWaitJson = true,
                ),
            )
        )
    }

    /**
     * Complete a flow successfully.
     * Aligned with TS finishFlow.
     */
    fun finishFlow(flowId: String, expectedRevision: Int, endedAt: Long? = null): TaskFlowUpdateResult {
        val now = endedAt ?: System.currentTimeMillis()
        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = expectedRevision,
                patch = FlowPatch(
                    status = TaskFlowStatus.SUCCEEDED,
                    endedAt = now,
                    clearWaitJson = true,
                ),
            )
        )
    }

    /**
     * Fail a flow.
     * Aligned with TS failFlow.
     */
    fun failFlow(flowId: String, expectedRevision: Int, endedAt: Long? = null): TaskFlowUpdateResult {
        val now = endedAt ?: System.currentTimeMillis()
        return updateFlowRecordByIdExpectedRevision(
            UpdateFlowParams(
                flowId = flowId,
                expectedRevision = expectedRevision,
                patch = FlowPatch(
                    status = TaskFlowStatus.FAILED,
                    endedAt = now,
                    clearWaitJson = true,
                ),
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------------

    fun deleteTaskFlowRecordById(flowId: String): Boolean {
        val flow = flows.remove(flowId) ?: return false
        ownerIndex[flow.ownerKey]?.remove(flowId)
        return true
    }

    // ---------------------------------------------------------------------------
    // Reset (for testing)
    // ---------------------------------------------------------------------------

    fun resetForTests() {
        flows.clear()
        ownerIndex.clear()
    }
}

// ---------------------------------------------------------------------------
// Params types
// ---------------------------------------------------------------------------

data class CreateFlowParams(
    val syncMode: TaskFlowSyncMode,
    val ownerKey: String,
    val requesterOrigin: DeliveryContext? = null,
    val controllerId: String? = null,
    val goal: String,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val status: TaskFlowStatus? = null,
    val currentStep: String? = null,
    val stateJson: JsonValue = null,
    val waitJson: JsonValue = null,
)

data class CreateFlowForTaskParams(
    val task: TaskRecord,
    val requesterOrigin: DeliveryContext? = null,
)

data class CreateManagedFlowParams(
    val ownerKey: String,
    val requesterOrigin: DeliveryContext? = null,
    val controllerId: String? = null,
    val goal: String,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val status: TaskFlowStatus? = null,
    val stateJson: JsonValue = null,
)

data class UpdateFlowParams(
    val flowId: String,
    val expectedRevision: Int,
    val patch: FlowPatch,
)

data class FlowPatch(
    val status: TaskFlowStatus? = null,
    val currentStep: String? = null,
    val clearCurrentStep: Boolean = false,
    val blockedTaskId: String? = null,
    val clearBlockedTaskId: Boolean = false,
    val blockedSummary: String? = null,
    val clearBlockedSummary: Boolean = false,
    val stateJson: JsonValue = null,
    val clearStateJson: Boolean = false,
    val waitJson: JsonValue = null,
    val clearWaitJson: Boolean = false,
    val cancelRequestedAt: Long? = null,
    val endedAt: Long? = null,
    val updatedAt: Long? = null,
)
