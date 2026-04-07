package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/task-registry.types.ts + task-flow-registry.types.ts
 *
 * Aligned 1:1 with TS type definitions.
 */

// ---------------------------------------------------------------------------
// TaskRuntime — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskRuntime(val value: String) {
    SUBAGENT("subagent"),
    ACP("acp"),
    CLI("cli"),
    CRON("cron");

    companion object {
        fun fromString(raw: String?): TaskRuntime = when (raw?.trim()?.lowercase()) {
            "subagent" -> SUBAGENT
            "acp" -> ACP
            "cli" -> CLI
            "cron" -> CRON
            else -> ACP
        }
    }
}

// ---------------------------------------------------------------------------
// TaskStatus — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskStatus(val value: String) {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    TIMED_OUT("timed_out"),
    CANCELLED("cancelled"),
    LOST("lost");

    companion object {
        fun fromString(raw: String?): TaskStatus? = entries.find {
            it.value.equals(raw?.trim(), ignoreCase = true)
        }
    }
}

// ---------------------------------------------------------------------------
// TaskDeliveryStatus — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskDeliveryStatus(val value: String) {
    PENDING("pending"),
    DELIVERED("delivered"),
    SESSION_QUEUED("session_queued"),
    FAILED("failed"),
    PARENT_MISSING("parent_missing"),
    NOT_APPLICABLE("not_applicable");

    companion object {
        fun fromString(raw: String?): TaskDeliveryStatus = when (raw?.trim()?.lowercase()) {
            "delivered" -> DELIVERED
            "session_queued" -> SESSION_QUEUED
            "failed" -> FAILED
            "parent_missing" -> PARENT_MISSING
            "not_applicable" -> NOT_APPLICABLE
            else -> PENDING
        }
    }
}

// ---------------------------------------------------------------------------
// TaskNotifyPolicy — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskNotifyPolicy(val value: String) {
    DONE_ONLY("done_only"),
    STATE_CHANGES("state_changes"),
    SILENT("silent");

    companion object {
        fun fromString(raw: String?): TaskNotifyPolicy = when (raw?.trim()?.lowercase()) {
            "state_changes" -> STATE_CHANGES
            "silent" -> SILENT
            else -> DONE_ONLY
        }
    }
}

// ---------------------------------------------------------------------------
// TaskTerminalOutcome — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskTerminalOutcome(val value: String) {
    SUCCEEDED("succeeded"),
    BLOCKED("blocked");
}

// ---------------------------------------------------------------------------
// TaskScopeKind — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskScopeKind(val value: String) {
    SESSION("session"),
    SYSTEM("system");
}

// ---------------------------------------------------------------------------
// TaskEventKind — from task-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskEventKind(val value: String) {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    TIMED_OUT("timed_out"),
    CANCELLED("cancelled"),
    LOST("lost"),
    PROGRESS("progress");
}

// ---------------------------------------------------------------------------
// TaskEventRecord — from task-registry.types.ts
// ---------------------------------------------------------------------------

data class TaskEventRecord(
    val at: Long,
    val kind: TaskEventKind,
    val summary: String? = null,
)

// ---------------------------------------------------------------------------
// DeliveryContext — from utils/delivery-context.ts (simplified)
// ---------------------------------------------------------------------------

data class DeliveryContext(
    val channel: String? = null,
    val sessionKey: String? = null,
    val accountId: String? = null,
)

// ---------------------------------------------------------------------------
// TaskDeliveryState — from task-registry.types.ts
// ---------------------------------------------------------------------------

data class TaskDeliveryState(
    val taskId: String,
    val requesterOrigin: DeliveryContext? = null,
    val lastNotifiedEventAt: Long? = null,
)

// ---------------------------------------------------------------------------
// TaskRecord — from task-registry.types.ts
// ---------------------------------------------------------------------------

data class TaskRecord(
    val taskId: String,
    val runtime: TaskRuntime,
    val taskKind: String? = null,
    val sourceId: String? = null,
    val requesterSessionKey: String = "",
    val ownerKey: String = "",
    val scopeKind: TaskScopeKind = TaskScopeKind.SESSION,
    val childSessionKey: String? = null,
    val parentFlowId: String? = null,
    val parentTaskId: String? = null,
    val agentId: String? = null,
    val runId: String? = null,
    val label: String? = null,
    val task: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val deliveryStatus: TaskDeliveryStatus = TaskDeliveryStatus.PENDING,
    val notifyPolicy: TaskNotifyPolicy = TaskNotifyPolicy.DONE_ONLY,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val lastEventAt: Long? = null,
    val cleanupAfter: Long? = null,
    val error: String? = null,
    val progressSummary: String? = null,
    val terminalSummary: String? = null,
    val terminalOutcome: TaskTerminalOutcome? = null,
)

// ---------------------------------------------------------------------------
// TaskStatusCounts / TaskRuntimeCounts / TaskRegistrySummary
// ---------------------------------------------------------------------------

data class TaskStatusCounts(
    val queued: Int = 0,
    val running: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val timedOut: Int = 0,
    val cancelled: Int = 0,
    val lost: Int = 0,
) {
    operator fun get(status: TaskStatus): Int = when (status) {
        TaskStatus.QUEUED -> queued
        TaskStatus.RUNNING -> running
        TaskStatus.SUCCEEDED -> succeeded
        TaskStatus.FAILED -> failed
        TaskStatus.TIMED_OUT -> timedOut
        TaskStatus.CANCELLED -> cancelled
        TaskStatus.LOST -> lost
    }

    fun increment(status: TaskStatus): TaskStatusCounts = when (status) {
        TaskStatus.QUEUED -> copy(queued = queued + 1)
        TaskStatus.RUNNING -> copy(running = running + 1)
        TaskStatus.SUCCEEDED -> copy(succeeded = succeeded + 1)
        TaskStatus.FAILED -> copy(failed = failed + 1)
        TaskStatus.TIMED_OUT -> copy(timedOut = timedOut + 1)
        TaskStatus.CANCELLED -> copy(cancelled = cancelled + 1)
        TaskStatus.LOST -> copy(lost = lost + 1)
    }
}

data class TaskRuntimeCounts(
    val subagent: Int = 0,
    val acp: Int = 0,
    val cli: Int = 0,
    val cron: Int = 0,
) {
    fun increment(runtime: TaskRuntime): TaskRuntimeCounts = when (runtime) {
        TaskRuntime.SUBAGENT -> copy(subagent = subagent + 1)
        TaskRuntime.ACP -> copy(acp = acp + 1)
        TaskRuntime.CLI -> copy(cli = cli + 1)
        TaskRuntime.CRON -> copy(cron = cron + 1)
    }
}

data class TaskRegistrySummary(
    val total: Int = 0,
    val active: Int = 0,
    val terminal: Int = 0,
    val failures: Int = 0,
    val byStatus: TaskStatusCounts = TaskStatusCounts(),
    val byRuntime: TaskRuntimeCounts = TaskRuntimeCounts(),
)

// ---------------------------------------------------------------------------
// TaskRegistrySnapshot — from task-registry.types.ts
// ---------------------------------------------------------------------------

data class TaskRegistrySnapshot(
    val tasks: List<TaskRecord>,
    val deliveryStates: List<TaskDeliveryState>,
)

// ---------------------------------------------------------------------------
// TaskFlowSyncMode — from task-flow-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskFlowSyncMode(val value: String) {
    TASK_MIRRORED("task_mirrored"),
    MANAGED("managed");
}

// ---------------------------------------------------------------------------
// TaskFlowStatus — from task-flow-registry.types.ts
// ---------------------------------------------------------------------------

enum class TaskFlowStatus(val value: String) {
    QUEUED("queued"),
    RUNNING("running"),
    WAITING("waiting"),
    BLOCKED("blocked"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    LOST("lost");

    companion object {
        fun fromString(raw: String?): TaskFlowStatus? = entries.find {
            it.value.equals(raw?.trim(), ignoreCase = true)
        }
    }
}

// ---------------------------------------------------------------------------
// JsonValue — from task-flow-registry.types.ts (simplified)
// ---------------------------------------------------------------------------

typealias JsonValue = Any?

// ---------------------------------------------------------------------------
// TaskFlowRecord — from task-flow-registry.types.ts
// ---------------------------------------------------------------------------

data class TaskFlowRecord(
    val flowId: String,
    val syncMode: TaskFlowSyncMode,
    val ownerKey: String,
    val requesterOrigin: DeliveryContext? = null,
    val controllerId: String? = null,
    val revision: Int = 0,
    val status: TaskFlowStatus = TaskFlowStatus.QUEUED,
    val notifyPolicy: TaskNotifyPolicy = TaskNotifyPolicy.DONE_ONLY,
    val goal: String,
    val currentStep: String? = null,
    val blockedTaskId: String? = null,
    val blockedSummary: String? = null,
    val stateJson: JsonValue = null,
    val waitJson: JsonValue = null,
    val cancelRequestedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
)
