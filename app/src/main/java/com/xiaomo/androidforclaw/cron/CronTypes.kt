/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/types.ts, types-shared.ts
 *
 * AndroidForClaw adaptation: cron scheduling.
 */
package com.xiaomo.androidforclaw.cron

/**
 * Cron 系统数据模型 - 对齐 OpenClaw
 */

// Schedule Types
sealed class CronSchedule {
    data class At(val at: String) : CronSchedule()
    data class Every(val everyMs: Long, val anchorMs: Long? = null) : CronSchedule()
    data class Cron(val expr: String, val tz: String? = null, val staggerMs: Long? = null) : CronSchedule()
}

// Payload Types
sealed class CronPayload {
    data class SystemEvent(val text: String) : CronPayload()
    data class AgentTurn(
        val message: String,
        val model: String? = null,
        val fallbacks: List<String>? = null,
        val thinking: String? = null,
        val timeoutSeconds: Int? = null,
        val deliver: Boolean? = null,
        val channel: String? = null,
        val to: String? = null,
        val bestEffortDeliver: Boolean? = null,
        val lightContext: Boolean? = null,
        val allowUnsafeExternalContent: Boolean? = null
    ) : CronPayload()
}

// Enums
enum class SessionTarget { MAIN, ISOLATED }
enum class WakeMode { NOW, NEXT_HEARTBEAT }
enum class RunStatus { OK, ERROR, SKIPPED }
enum class DeliveryStatus { DELIVERED, NOT_DELIVERED, UNKNOWN, NOT_REQUESTED }
enum class DeliveryMode { NONE, ANNOUNCE, WEBHOOK }

// Job State
data class CronJobState(
    var nextRunAtMs: Long? = null,
    var runningAtMs: Long? = null,
    var lastRunAtMs: Long? = null,
    var lastRunStatus: RunStatus? = null,
    var lastError: String? = null,
    var lastDurationMs: Long? = null,
    var consecutiveErrors: Int = 0,
    var lastFailureAlertAtMs: Long? = null,
    var scheduleErrorCount: Int = 0,
    var lastDeliveryStatus: DeliveryStatus? = null,
    var lastDelivered: Boolean? = null
)

// Delivery
data class CronDelivery(
    val mode: DeliveryMode,
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null,
    val failureDestination: CronFailureDestination? = null
)

data class CronFailureDestination(
    val mode: String? = null,
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null
)

// Failure Alert
data class CronFailureAlert(
    val after: Int,
    val cooldownMs: Long,
    val channel: String? = null
)

// Cron Job
data class CronJob(
    val id: String,
    val name: String,
    val description: String? = null,
    val schedule: CronSchedule,
    val sessionTarget: SessionTarget,
    val wakeMode: WakeMode,
    val payload: CronPayload,
    val delivery: CronDelivery? = null,
    val failureAlert: CronFailureAlert? = null,
    var enabled: Boolean,
    val deleteAfterRun: Boolean? = null,
    val createdAtMs: Long,
    var updatedAtMs: Long,
    val state: CronJobState = CronJobState()
)

// Store File
data class CronStoreFile(
    val version: Int = 1,
    val jobs: List<CronJob> = emptyList()
)

// Config
data class CronConfig(
    val enabled: Boolean = true,
    val storePath: String,
    val maxConcurrentRuns: Int = 1,
    val retry: CronRetryConfig = CronRetryConfig(),
    val sessionRetention: String = "24h",
    val runLog: CronRunLogConfig = CronRunLogConfig(),
    val failureAlert: CronFailureAlertConfig = CronFailureAlertConfig()
)

data class CronRetryConfig(
    val backoffMs: List<Long> = listOf(30000, 60000, 300000)  // aligned with OpenClaw default
)

data class CronRunLogConfig(
    val maxBytes: Long = 2 * 1024 * 1024,
    val keepLines: Int = 2000
)

data class CronFailureAlertConfig(
    val enabled: Boolean = true,
    val after: Int = 2,
    val cooldownMs: Long = 3600000,
    val mode: DeliveryMode = DeliveryMode.ANNOUNCE
)

// Run Result
data class CronRunResult(
    val status: RunStatus,
    val summary: String? = null,
    val delivered: Boolean? = null,
    val deliveryStatus: DeliveryStatus? = null,
    val deliveryError: String? = null,
    val model: String? = null
)

// Run Log Entry
data class CronRunLogEntry(
    val ts: Long,
    val jobId: String,
    val action: String = "finished",
    val status: RunStatus? = null,
    val error: String? = null,
    val summary: String? = null,
    val runAtMs: Long? = null,
    val durationMs: Long? = null,
    val nextRunAtMs: Long? = null
)

// Event
data class CronEvent(
    val jobId: String,
    val action: String,
    val runAtMs: Long? = null,
    val durationMs: Long? = null,
    val status: RunStatus? = null,
    val summary: String? = null
)
