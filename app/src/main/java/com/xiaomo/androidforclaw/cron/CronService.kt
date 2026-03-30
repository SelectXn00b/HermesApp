/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/service.ts
 *
 * AndroidForClaw adaptation: cron scheduling.
 */
package com.xiaomo.androidforclaw.cron

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CronService(private val context: Context, private val config: CronConfig) {
    companion object {
        private const val TAG = "CronService"
        private const val MIN_TIMER_MS = 1000L
        private const val MAX_TIMER_MS = 60000L
    }

    private val store = CronStore(config.storePath)
    private val runLog = CronRunLog(
        "${config.storePath.substringBeforeLast("/")}/runs",
        config.runLog
    )

    private val lock = ReentrantLock()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var jobs = mutableListOf<CronJob>()
    private var isStarted = false
    private var timerRunnable: Runnable? = null
    private var concurrentRuns = 0

    var onEvent: ((CronEvent) -> Unit)? = null

    fun start() {
        lock.withLock {
            if (isStarted) return
            Log.d(TAG, "Starting CronService...")

            val storeFile = store.load()
            jobs = storeFile.jobs.toMutableList()

            jobs.forEach { it.state.runningAtMs = null }
            recomputeAllNextRuns()
            persist()

            isStarted = true
            armTimer()
            Log.d(TAG, "CronService started with ${jobs.size} jobs")
        }
    }

    fun stop() {
        lock.withLock {
            if (!isStarted) return
            timerRunnable?.let { handler.removeCallbacks(it) }
            scope.coroutineContext.cancelChildren()
            isStarted = false
            Log.d(TAG, "CronService stopped")
        }
    }

    fun add(job: CronJob): CronJob {
        return lock.withLock {
            val newJob = job.copy(
                id = UUID.randomUUID().toString(),
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
            newJob.state.nextRunAtMs = computeJobNextRun(newJob)
            
            jobs.add(newJob)
            persist()
            armTimer()
            
            emitEvent(CronEvent(jobId = newJob.id, action = "added"))
            newJob
        }
    }

    fun update(jobId: String, patch: (CronJob) -> CronJob): CronJob? {
        return lock.withLock {
            val idx = jobs.indexOfFirst { it.id == jobId }
            if (idx == -1) return null

            val updated = patch(jobs[idx]).copy(updatedAtMs = System.currentTimeMillis())
            updated.state.nextRunAtMs = computeJobNextRun(updated)
            
            jobs[idx] = updated
            persist()
            armTimer()
            
            emitEvent(CronEvent(jobId = updated.id, action = "updated"))
            updated
        }
    }

    fun remove(jobId: String): Boolean {
        return lock.withLock {
            val removed = jobs.removeIf { it.id == jobId }
            if (removed) {
                persist()
                runLog.delete(jobId)
                armTimer()
                emitEvent(CronEvent(jobId = jobId, action = "removed"))
            }
            removed
        }
    }

    fun list(includeDisabled: Boolean = true, enabled: Boolean? = null): List<CronJob> {
        return lock.withLock {
            jobs.filter {
                when {
                    enabled == true -> it.enabled
                    enabled == false -> !it.enabled
                    !includeDisabled -> it.enabled
                    else -> true
                }
            }
        }
    }

    fun get(jobId: String): CronJob? = lock.withLock { jobs.find { it.id == jobId } }

    /**
     * Query run history for a given job.
     * @param jobId the cron job ID
     * @param limit maximum number of entries to return (most recent first)
     * @param status optional filter by RunStatus
     */
    fun queryRuns(jobId: String, limit: Int = 100, status: RunStatus? = null): List<CronRunLogEntry> {
        return runLog.query(jobId, limit, status)
    }

    fun run(jobId: String, force: Boolean = false): Boolean {
        val job = get(jobId) ?: return false
        if (!force && job.state.nextRunAtMs?.let { it > System.currentTimeMillis() } == true) {
            return false
        }
        scope.launch { executeJob(job) }
        return true
    }

    fun status(): Map<String, Any?> {
        return lock.withLock {
            mapOf(
                "enabled" to config.enabled,
                "jobs" to jobs.size,
                "nextWakeAtMs" to (jobs.mapNotNull { it.state.nextRunAtMs }.minOrNull() as Any?),
                "isStarted" to isStarted,
                "concurrentRuns" to concurrentRuns
            )
        }
    }

    private fun armTimer() {
        if (!isStarted) return
        timerRunnable?.let { handler.removeCallbacks(it) }

        val nowMs = System.currentTimeMillis()
        val nextRunAtMs = jobs.filter { it.enabled }
            .mapNotNull { it.state.nextRunAtMs }
            .filter { it > nowMs }
            .minOrNull() ?: return

        val delayMs = (nextRunAtMs - nowMs).coerceIn(MIN_TIMER_MS, MAX_TIMER_MS)
        timerRunnable = Runnable { onTimerTick() }
        handler.postDelayed(timerRunnable!!, delayMs)
    }

    private fun onTimerTick() {
        scope.launch {
            lock.withLock {
                val nowMs = System.currentTimeMillis()
                jobs.filter {
                    it.enabled &&
                    it.state.runningAtMs == null &&
                    it.state.nextRunAtMs?.let { t -> t <= nowMs } == true
                }.forEach { job ->
                    launch { executeJob(job) }
                }
                armTimer()
            }
        }
    }

    private suspend fun executeJob(job: CronJob) {
        if (concurrentRuns >= config.maxConcurrentRuns) return

        lock.withLock {
            if (job.state.runningAtMs != null) return
            job.state.runningAtMs = System.currentTimeMillis()
            concurrentRuns++
        }

        val startMs = System.currentTimeMillis()
        emitEvent(CronEvent(job.id, "started", runAtMs = startMs))

        try {
            val result = when (job.sessionTarget) {
                SessionTarget.MAIN -> executeMainJob(job)
                SessionTarget.ISOLATED -> executeIsolatedJob(job)
            }

            val durationMs = System.currentTimeMillis() - startMs

            lock.withLock {
                job.state.apply {
                    lastRunAtMs = startMs
                    lastRunStatus = result.status
                    lastDurationMs = durationMs
                    lastDeliveryStatus = result.deliveryStatus
                    runningAtMs = null
                    consecutiveErrors = if (result.status == RunStatus.OK) 0 else consecutiveErrors + 1
                    nextRunAtMs = if (result.status == RunStatus.OK) {
                        computeJobNextRun(job)
                    } else {
                        System.currentTimeMillis() + 
                            CronScheduleParser.errorBackoffMs(consecutiveErrors, config.retry.backoffMs)
                    }
                }
                concurrentRuns--
                persist()
            }

            runLog.append(CronRunLogEntry(
                ts = System.currentTimeMillis(),
                jobId = job.id,
                status = result.status,
                summary = result.summary,
                runAtMs = startMs,
                durationMs = durationMs,
                nextRunAtMs = job.state.nextRunAtMs
            ))

            emitEvent(CronEvent(
                job.id, "finished",
                runAtMs = startMs,
                durationMs = durationMs,
                status = result.status,
                summary = result.summary
            ))

        } catch (e: Exception) {
            lock.withLock {
                job.state.runningAtMs = null
                job.state.consecutiveErrors++
                concurrentRuns--
                persist()
            }
            Log.e(TAG, "Job failed: ${job.id}", e)
        }

        armTimer()
    }

    private suspend fun executeMainJob(job: CronJob): CronRunResult {
        return when (val payload = job.payload) {
            is CronPayload.SystemEvent -> {
                // System events: broadcast or log
                CronRunResult(RunStatus.OK, "System event: ${payload.text}")
            }
            is CronPayload.AgentTurn -> {
                val delivery = CronDeliveryResolver.resolveDeliveryPlan(job)
                CronAgentTurnExecutor.execute(
                    context = context,
                    sessionId = "cron_${job.name}",
                    userMessage = payload.message,
                    model = payload.model,
                    channel = delivery.channel,
                    to = delivery.to,
                    isolated = false
                )
            }
            else -> CronRunResult(RunStatus.ERROR, "Invalid payload for main session")
        }
    }

    private suspend fun executeIsolatedJob(job: CronJob): CronRunResult {
        return when (val payload = job.payload) {
            is CronPayload.AgentTurn -> {
                val delivery = CronDeliveryResolver.resolveDeliveryPlan(job)
                CronAgentTurnExecutor.execute(
                    context = context,
                    sessionId = "cron_isolated_${job.id}",
                    userMessage = payload.message,
                    model = payload.model,
                    channel = delivery.channel,
                    to = delivery.to,
                    isolated = true
                )
            }
            else -> CronRunResult(RunStatus.ERROR, "Invalid payload for isolated session")
        }
    }

    private fun computeJobNextRun(job: CronJob): Long? {
        return CronScheduleParser.computeNextRunAtMs(job.schedule, System.currentTimeMillis())
    }

    private fun recomputeAllNextRuns() {
        jobs.forEach { it.state.nextRunAtMs = computeJobNextRun(it) }
    }

    private fun persist() {
        store.save(CronStoreFile(jobs = jobs))
    }

    private fun emitEvent(event: CronEvent) {
        onEvent?.invoke(event)
    }
}
