package com.xiaomo.androidforclaw.tasks

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-registry.ts + runtime-internal.ts
 *
 * In-memory task registry with ConcurrentHashMap.
 * Aligned 1:1 with TS task-registry.ts public API.
 * Android adaptation: skip SQLite store for now, use ConcurrentHashMap.
 */
object TaskRegistry {

    // ---------------------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------------------

    /** taskId -> TaskRecord */
    private val tasks = ConcurrentHashMap<String, TaskRecord>()

    /** runId -> taskId (for findTaskByRunId) */
    private val runIdIndex = ConcurrentHashMap<String, String>()

    /** ownerKey -> list of taskIds */
    private val ownerIndex = ConcurrentHashMap<String, MutableList<String>>()

    /** parentFlowId -> list of taskIds */
    private val flowIndex = ConcurrentHashMap<String, MutableList<String>>()

    /** sessionKey -> list of taskIds (both requester and child) */
    private val sessionIndex = ConcurrentHashMap<String, MutableList<String>>()

    /** agentId -> list of taskIds */
    private val agentIndex = ConcurrentHashMap<String, MutableList<String>>()

    /** taskId -> TaskDeliveryState */
    private val deliveryStates = ConcurrentHashMap<String, TaskDeliveryState>()

    // ---------------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------------

    fun createTaskRecord(params: CreateTaskParams): TaskRecord {
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val record = TaskRecord(
            taskId = taskId,
            runtime = params.runtime,
            taskKind = params.taskKind,
            sourceId = params.sourceId,
            requesterSessionKey = params.requesterSessionKey ?: "",
            ownerKey = params.ownerKey ?: "",
            scopeKind = params.scopeKind ?: TaskScopeKind.SESSION,
            childSessionKey = params.childSessionKey,
            parentFlowId = params.parentFlowId,
            parentTaskId = params.parentTaskId,
            agentId = params.agentId,
            runId = params.runId ?: UUID.randomUUID().toString(),
            label = params.label,
            task = params.task,
            status = params.status,
            deliveryStatus = params.deliveryStatus ?: TaskDeliveryStatus.PENDING,
            notifyPolicy = params.notifyPolicy ?: TaskNotifyPolicy.DONE_ONLY,
            createdAt = now,
            startedAt = if (params.status == TaskStatus.RUNNING) (params.startedAt ?: now) else null,
            lastEventAt = params.lastEventAt,
            progressSummary = params.progressSummary,
        )

        // Validate parent flow link
        if (record.parentFlowId != null) {
            val flow = TaskFlowRegistry.getTaskFlowById(record.parentFlowId)
            if (flow == null) {
                throw ParentFlowLinkError("parent_flow_not_found")
            }
            if (flow.cancelRequestedAt != null) {
                throw ParentFlowLinkError("cancel_requested")
            }
            if (isTerminalFlowStatus(flow.status)) {
                throw ParentFlowLinkError("terminal", mapOf("status" to flow.status.value))
            }
        }

        tasks[taskId] = record
        indexTask(record)

        return record
    }

    // ---------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------

    fun getTaskById(taskId: String): TaskRecord? = tasks[taskId]

    fun findTaskByRunId(runId: String): TaskRecord? {
        val taskId = runIdIndex[runId] ?: return null
        return tasks[taskId]
    }

    fun listTaskRecords(): List<TaskRecord> = tasks.values.toList()

    fun listTasksForOwnerKey(ownerKey: String): List<TaskRecord> {
        return ownerIndex[ownerKey]?.mapNotNull { tasks[it] } ?: emptyList()
    }

    fun listTasksForFlowId(flowId: String): List<TaskRecord> {
        return flowIndex[flowId]?.mapNotNull { tasks[it] } ?: emptyList()
    }

    fun listTasksForRelatedSessionKey(sessionKey: String): List<TaskRecord> {
        return sessionIndex[sessionKey]?.mapNotNull { tasks[it] } ?: emptyList()
    }

    fun listTasksForSessionKey(sessionKey: String): List<TaskRecord> {
        return listTasksForRelatedSessionKey(sessionKey)
    }

    fun listTasksForAgentId(agentId: String): List<TaskRecord> {
        return agentIndex[agentId]?.mapNotNull { tasks[it] } ?: emptyList()
    }

    fun findLatestTaskForOwnerKey(ownerKey: String): TaskRecord? {
        return listTasksForOwnerKey(ownerKey).maxByOrNull { it.createdAt }
    }

    fun findLatestTaskForFlowId(flowId: String): TaskRecord? {
        return listTasksForFlowId(flowId).maxByOrNull { it.createdAt }
    }

    fun findLatestTaskForRelatedSessionKey(sessionKey: String): TaskRecord? {
        return listTasksForRelatedSessionKey(sessionKey).maxByOrNull { it.createdAt }
    }

    fun resolveTaskForLookupToken(token: String): TaskRecord? {
        // Try direct taskId first
        tasks[token]?.let { return it }
        // Then by runId
        findTaskByRunId(token)?.let { return it }
        // Then by sessionKey (latest)
        findLatestTaskForRelatedSessionKey(token)?.let { return it }
        return null
    }

    // ---------------------------------------------------------------------------
    // Update: status transitions
    // ---------------------------------------------------------------------------

    fun markTaskRunningByRunId(params: MarkRunningParams): TaskRecord? {
        val task = findTaskByRunId(params.runId) ?: return null
        val now = System.currentTimeMillis()
        val updated = task.copy(
            status = TaskStatus.RUNNING,
            runtime = params.runtime ?: task.runtime,
            startedAt = params.startedAt ?: now,
            lastEventAt = params.lastEventAt ?: now,
            progressSummary = params.progressSummary ?: task.progressSummary,
        )
        tasks[task.taskId] = updated
        return updated
    }

    fun recordTaskProgressByRunId(params: RecordProgressParams): TaskRecord? {
        val task = findTaskByRunId(params.runId) ?: return null
        val updated = task.copy(
            lastEventAt = params.lastEventAt ?: System.currentTimeMillis(),
            progressSummary = params.progressSummary ?: task.progressSummary,
        )
        tasks[task.taskId] = updated
        return updated
    }

    fun markTaskTerminalByRunId(params: MarkTerminalByRunIdParams): TaskRecord? {
        val task = findTaskByRunId(params.runId) ?: return null
        val updated = task.copy(
            status = params.status,
            endedAt = params.endedAt,
            lastEventAt = params.lastEventAt ?: params.endedAt,
            error = params.error ?: task.error,
            progressSummary = params.progressSummary ?: task.progressSummary,
            terminalSummary = params.terminalSummary ?: task.terminalSummary,
            terminalOutcome = params.terminalOutcome ?: task.terminalOutcome,
        )
        tasks[task.taskId] = updated
        return updated
    }

    fun markTaskTerminalById(taskId: String, status: TaskStatus, endedAt: Long, error: String? = null): TaskRecord? {
        val task = tasks[taskId] ?: return null
        val updated = task.copy(
            status = status,
            endedAt = endedAt,
            lastEventAt = endedAt,
            error = error,
        )
        tasks[taskId] = updated
        return updated
    }

    fun markTaskLostById(params: MarkLostParams): TaskRecord? {
        val task = tasks[params.taskId] ?: return null
        val updated = task.copy(
            status = TaskStatus.LOST,
            endedAt = params.endedAt,
            lastEventAt = params.lastEventAt ?: params.endedAt,
            error = params.error,
            cleanupAfter = params.cleanupAfter,
        )
        tasks[task.taskId] = updated
        return updated
    }

    fun linkTaskToFlowById(taskId: String, flowId: String): TaskRecord? {
        val task = tasks[taskId] ?: return null
        if (task.parentFlowId != null) return task // Already linked

        val flow = TaskFlowRegistry.getTaskFlowById(flowId) ?: return null
        if (flow.cancelRequestedAt != null) {
            throw ParentFlowLinkError("cancel_requested")
        }
        if (isTerminalFlowStatus(flow.status)) {
            throw ParentFlowLinkError("terminal", mapOf("status" to flow.status.value))
        }

        val updated = task.copy(parentFlowId = flowId)
        tasks[taskId] = updated
        flowIndex.getOrPut(flowId) { mutableListOf() }.add(taskId)
        return updated
    }

    fun setTaskRunDeliveryStatusByRunId(params: SetDeliveryStatusParams): TaskRecord? {
        val task = findTaskByRunId(params.runId) ?: return null
        val updated = task.copy(deliveryStatus = params.deliveryStatus)
        tasks[task.taskId] = updated
        return updated
    }

    fun setTaskCleanupAfterById(taskId: String, cleanupAfter: Long): TaskRecord? {
        val task = tasks[taskId] ?: return null
        val updated = task.copy(cleanupAfter = cleanupAfter)
        tasks[taskId] = updated
        return updated
    }

    fun setTaskProgressById(taskId: String, progressSummary: String?): TaskRecord? {
        val task = tasks[taskId] ?: return null
        val updated = task.copy(
            progressSummary = progressSummary,
            lastEventAt = System.currentTimeMillis(),
        )
        tasks[taskId] = updated
        return updated
    }

    fun setTaskTimingById(taskId: String, startedAt: Long? = null, endedAt: Long? = null): TaskRecord? {
        val task = tasks[taskId] ?: return null
        val updated = task.copy(
            startedAt = startedAt ?: task.startedAt,
            endedAt = endedAt ?: task.endedAt,
        )
        tasks[taskId] = updated
        return updated
    }

    fun updateTaskNotifyPolicyById(taskId: String, notifyPolicy: TaskNotifyPolicy): TaskRecord? {
        val task = tasks[taskId] ?: return null
        val updated = task.copy(notifyPolicy = notifyPolicy)
        tasks[taskId] = updated
        return updated
    }

    suspend fun cancelTaskById(taskId: String): TaskRecord? {
        val task = tasks[taskId] ?: return null
        if (isTerminalTaskStatus(task.status)) return task
        return markTaskTerminalById(taskId, TaskStatus.CANCELLED, System.currentTimeMillis())
    }

    // ---------------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------------

    fun deleteTaskRecordById(taskId: String): Boolean {
        val task = tasks.remove(taskId) ?: return false
        deindexTask(task)
        deliveryStates.remove(taskId)
        return true
    }

    // ---------------------------------------------------------------------------
    // Delivery states
    // ---------------------------------------------------------------------------

    fun getDeliveryState(taskId: String): TaskDeliveryState? = deliveryStates[taskId]

    fun setDeliveryState(state: TaskDeliveryState) {
        deliveryStates[state.taskId] = state
    }

    // ---------------------------------------------------------------------------
    // Snapshot / Summary
    // ---------------------------------------------------------------------------

    fun getTaskRegistrySnapshot(): TaskRegistrySnapshot {
        return TaskRegistrySnapshot(
            tasks = tasks.values.toList(),
            deliveryStates = deliveryStates.values.toList(),
        )
    }

    fun getTaskRegistrySummary(): TaskRegistrySummary {
        return TaskRegistrySummaryHelper.summarizeTaskRecords(tasks.values)
    }

    // ---------------------------------------------------------------------------
    // Reset (for testing)
    // ---------------------------------------------------------------------------

    fun resetForTests() {
        tasks.clear()
        runIdIndex.clear()
        ownerIndex.clear()
        flowIndex.clear()
        sessionIndex.clear()
        agentIndex.clear()
        deliveryStates.clear()
    }

    // ---------------------------------------------------------------------------
    // Indexing helpers
    // ---------------------------------------------------------------------------

    private fun indexTask(task: TaskRecord) {
        task.runId?.let { runIdIndex[it] = task.taskId }
        if (task.ownerKey.isNotEmpty()) {
            ownerIndex.getOrPut(task.ownerKey) { mutableListOf() }.add(task.taskId)
        }
        task.parentFlowId?.let {
            flowIndex.getOrPut(it) { mutableListOf() }.add(task.taskId)
        }
        if (task.requesterSessionKey.isNotEmpty()) {
            sessionIndex.getOrPut(task.requesterSessionKey) { mutableListOf() }.add(task.taskId)
        }
        task.childSessionKey?.let {
            sessionIndex.getOrPut(it) { mutableListOf() }.add(task.taskId)
        }
        task.agentId?.let {
            agentIndex.getOrPut(it) { mutableListOf() }.add(task.taskId)
        }
    }

    private fun deindexTask(task: TaskRecord) {
        task.runId?.let { runIdIndex.remove(it) }
        ownerIndex[task.ownerKey]?.remove(task.taskId)
        task.parentFlowId?.let { flowIndex[it]?.remove(task.taskId) }
        sessionIndex[task.requesterSessionKey]?.remove(task.taskId)
        task.childSessionKey?.let { sessionIndex[it]?.remove(task.taskId) }
        task.agentId?.let { agentIndex[it]?.remove(task.taskId) }
    }
}

// ---------------------------------------------------------------------------
// Params types — aligned with TS function param interfaces
// ---------------------------------------------------------------------------

data class CreateTaskParams(
    val runtime: TaskRuntime,
    val taskKind: String? = null,
    val sourceId: String? = null,
    val requesterSessionKey: String? = null,
    val ownerKey: String? = null,
    val scopeKind: TaskScopeKind? = null,
    val parentFlowId: String? = null,
    val childSessionKey: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val deliveryStatus: TaskDeliveryStatus? = null,
    val notifyPolicy: TaskNotifyPolicy? = null,
    val preferMetadata: Boolean? = null,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
)

data class MarkRunningParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val startedAt: Long? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
    val eventSummary: String? = null,
)

data class RecordProgressParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val lastEventAt: Long? = null,
    val progressSummary: String? = null,
    val eventSummary: String? = null,
)

data class MarkTerminalByRunIdParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val status: TaskStatus,
    val endedAt: Long,
    val lastEventAt: Long? = null,
    val error: String? = null,
    val progressSummary: String? = null,
    val terminalSummary: String? = null,
    val terminalOutcome: TaskTerminalOutcome? = null,
)

data class MarkLostParams(
    val taskId: String,
    val endedAt: Long,
    val lastEventAt: Long? = null,
    val error: String? = null,
    val cleanupAfter: Long? = null,
)

data class SetDeliveryStatusParams(
    val runId: String,
    val runtime: TaskRuntime? = null,
    val sessionKey: String? = null,
    val deliveryStatus: TaskDeliveryStatus,
)

// ---------------------------------------------------------------------------
// ParentFlowLinkError — aligned with TS isParentFlowLinkError
// ---------------------------------------------------------------------------

class ParentFlowLinkError(
    val code: String,
    val details: Map<String, String>? = null,
) : Exception("Parent flow link error: $code") {
    companion object {
        fun isParentFlowLinkError(error: Throwable): Boolean = error is ParentFlowLinkError
    }
}

// ---------------------------------------------------------------------------
// Helper: terminal status checks
// ---------------------------------------------------------------------------

fun isTerminalTaskStatus(status: TaskStatus): Boolean {
    return status == TaskStatus.SUCCEEDED ||
        status == TaskStatus.FAILED ||
        status == TaskStatus.TIMED_OUT ||
        status == TaskStatus.CANCELLED ||
        status == TaskStatus.LOST
}

fun isTerminalFlowStatus(status: TaskFlowStatus): Boolean {
    return status == TaskFlowStatus.SUCCEEDED ||
        status == TaskFlowStatus.FAILED ||
        status == TaskFlowStatus.CANCELLED ||
        status == TaskFlowStatus.LOST
}

fun isActiveTaskStatus(status: TaskStatus): Boolean {
    return status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
}
