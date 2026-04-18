/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-registry.ts (in-memory registry, lifecycle listener, completion flow)
 * - ../openclaw/src/agents/subagent-registry-state.ts (persistence, orphan reconciliation)
 *
 * Query/lookup functions: see SubagentRegistryQueries.kt (subagent-registry-queries.ts)
 *
 * Hermes adaptation: ConcurrentHashMap-based registry tracking active/completed subagent runs.
 * Includes registration, completion, cascade kill, run replacement,
 * disk persistence, listener interface, and bulk termination.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener interface for registry lifecycle events.
 * Aligned with OpenClaw's event-driven architecture (lifecycle listener).
 */
interface SubagentRegistryListener {
    fun onRunRegistered(record: SubagentRunRecord) {}
    fun onRunCompleted(record: SubagentRunRecord) {}
    fun onRunReleased(runId: String) {}
}

/**
 * Central registry for all subagent runs.
 * Aligned with OpenClaw's in-memory SubagentRunRecord map + query functions.
 */
class SubagentRegistry(
    private val store: SubagentRegistryStore? = null,
) {
    companion object {
        private const val TAG = "SubagentRegistry"
    }

    /** runId -> SubagentRunRecord */
    internal val runs = ConcurrentHashMap<String, SubagentRunRecord>()

    /** runId -> coroutine Job */
    private val jobs = ConcurrentHashMap<String, Job>()

    /** runId -> child AgentLoop (for steer/kill/history) */
    private val agentLoops = ConcurrentHashMap<String, AgentLoopInterface>()

    /** Registry event listeners */
    private val listeners = mutableListOf<SubagentRegistryListener>()

    fun addListener(listener: SubagentRegistryListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SubagentRegistryListener) {
        listeners.remove(listener)
    }

    // ==================== Initialization & Persistence ====================

    /**
     * Restore runs from disk on startup.
     * Active runs without Job are orphans -- mark as error.
     * Aligned with OpenClaw restoreSubagentRunsOnce + reconcileOrphanedRestoredRuns.
     */
    fun restoreFromDisk() {
        val loaded = store?.load() ?: return
        if (loaded.isEmpty()) return

        // Step 1: Merge restored runs (aligned with OpenClaw restoreSubagentRunsFromDisk mergeOnly)
        var added = 0
        for ((runId, record) in loaded) {
            if (!runs.containsKey(runId)) {
                runs[runId] = record
                added++
            }
        }
        if (added == 0) return

        // Step 2: Reconcile orphans -- separate step aligned with OpenClaw reconcileOrphanedRestoredRuns.
        // OpenClaw verifies against session store (gateway sessions.get); on Android there is no
        // external session store, so all active runs without a Job are orphaned.
        // These orphans are flagged here; SubagentOrphanRecovery.scheduleOrphanRecovery() handles
        // the actual recovery/cleanup with retries.
        var orphanCount = 0
        for ((_, record) in runs) {
            if (record.isActive && jobs[record.runId] == null) {
                record.endedAt = System.currentTimeMillis()
                record.outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "orphaned after process restart")
                record.endedReason = SubagentLifecycleEndedReason.SUBAGENT_ERROR
                orphanCount++
            }
        }
        if (orphanCount > 0) {
            Log.w(TAG, "Reconciled $orphanCount orphaned subagent runs from disk")
        }
        Log.i(TAG, "Restored $added subagent runs from disk (orphans=$orphanCount)")
        persistToDisk()
    }

    private fun persistToDisk() {
        store?.save(runs)
    }

    // ==================== Registration ====================

    fun registerRun(record: SubagentRunRecord, loop: AgentLoopInterface, job: Job) {
        runs[record.runId] = record
        agentLoops[record.runId] = loop
        jobs[record.runId] = job
        Log.i(TAG, "Registered subagent run: ${record.runId} label=${record.label} child=${record.childSessionKey}")
        persistToDisk()
        listeners.forEach { it.onRunRegistered(record) }
    }

    // ==================== Completion ====================

    fun markCompleted(
        runId: String,
        outcome: SubagentRunOutcome,
        endedReason: SubagentLifecycleEndedReason,
        frozenResult: String?,
    ) {
        val record = runs[runId] ?: return
        record.endedAt = System.currentTimeMillis()
        record.outcome = outcome
        record.endedReason = endedReason
        record.frozenResultText = capFrozenResultText(frozenResult)
        record.frozenResultCapturedAt = if (frozenResult != null) System.currentTimeMillis() else null
        record.archiveAtMs = System.currentTimeMillis() + ARCHIVE_AFTER_MS
        // Clean up runtime references
        agentLoops.remove(runId)
        jobs.remove(runId)
        Log.i(TAG, "Completed subagent run: $runId status=${outcome.status} reason=$endedReason")
        persistToDisk()
        listeners.forEach { it.onRunCompleted(record) }
    }

    // ==================== Basic Accessors ====================

    fun getRunById(runId: String): SubagentRunRecord? = runs[runId]

    fun getAgentLoop(runId: String): AgentLoopInterface? = agentLoops[runId]

    fun getJob(runId: String): Job? = jobs[runId]

    // ==================== Steer Restart Management ====================

    /**
     * Clear steer-restart suppression on a run.
     * If the run already ended while suppression was active, resume cleanup.
     * Aligned with OpenClaw clearSubagentRunSteerRestart.
     */
    fun clearSubagentRunSteerRestart(runId: String): Boolean {
        val entry = runs[runId] ?: return false
        if (entry.suppressAnnounceReason != "steer-restart") return true
        entry.suppressAnnounceReason = null
        persistToDisk()
        // If the run already finished while suppression was active, it needs cleanup
        if (entry.endedAt != null && entry.cleanupCompletedAt == null) {
            Log.i(TAG, "Resuming cleanup for run $runId after clearing steer-restart suppression")
        }
        return true
    }

    // ==================== Bulk Termination ====================

    /**
     * Mark matching runs as terminated (killed).
     * Can match by runId and/or childSessionKey.
     * Aligned with OpenClaw markSubagentRunTerminated.
     * @return Number of runs updated.
     */
    fun markSubagentRunTerminated(
        runId: String? = null,
        childSessionKey: String? = null,
        reason: String = "killed",
    ): Int {
        val targetRunIds = mutableSetOf<String>()
        if (!runId.isNullOrBlank()) targetRunIds.add(runId)
        if (!childSessionKey.isNullOrBlank()) {
            runs.values.filter { it.childSessionKey == childSessionKey }.forEach {
                targetRunIds.add(it.runId)
            }
        }
        if (targetRunIds.isEmpty()) return 0

        val now = System.currentTimeMillis()
        var updated = 0
        for (rid in targetRunIds) {
            val entry = runs[rid] ?: continue
            if (entry.endedAt != null) continue // already ended
            entry.endedAt = now
            entry.outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, reason)
            entry.endedReason = SubagentLifecycleEndedReason.SUBAGENT_KILLED
            entry.cleanupHandled = true
            entry.cleanupCompletedAt = now
            entry.suppressAnnounceReason = "killed"
            // Cancel job if exists
            jobs[rid]?.cancel()
            jobs.remove(rid)
            agentLoops.remove(rid)
            updated++
            listeners.forEach { it.onRunCompleted(entry) }
        }
        if (updated > 0) {
            Log.i(TAG, "markSubagentRunTerminated: $updated runs terminated (reason=$reason)")
            persistToDisk()
        }
        return updated
    }

    // ==================== Control ====================

    /**
     * Kill a running subagent by cancelling its coroutine Job.
     * Aligned with OpenClaw killControlledSubagentRun (single target).
     */
    fun killRun(runId: String): Boolean {
        val job = jobs[runId] ?: return false
        val record = runs[runId] ?: return false
        if (!record.isActive) return false

        Log.i(TAG, "Killing subagent run: $runId")

        // Set suppressAnnounceReason and cleanupHandled BEFORE cancelling
        // Aligned with OpenClaw markSubagentRunTerminated
        record.suppressAnnounceReason = "killed"
        record.cleanupHandled = true

        job.cancel()

        markCompleted(
            runId,
            SubagentRunOutcome(SubagentRunStatus.ERROR, "Killed by parent"),
            SubagentLifecycleEndedReason.SUBAGENT_KILLED,
            frozenResult = null,
        )
        return true
    }

    /**
     * Cascade kill: kill a run and all its descendants (BFS).
     * Aligned with OpenClaw cascadeKillChildren.
     * Returns list of killed runIds.
     */
    fun cascadeKill(runId: String): List<String> {
        val killed = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(runId)

        while (queue.isNotEmpty()) {
            val currentRunId = queue.removeAt(0)
            if (currentRunId in visited) continue
            visited.add(currentRunId)

            val record = runs[currentRunId] ?: continue

            // Find children of this run's session
            val children = runs.values.filter {
                it.requesterSessionKey == record.childSessionKey && it.isActive
            }
            for (child in children) {
                queue.add(child.runId)
            }

            // Kill this run if still active
            if (record.isActive) {
                // Set suppressAnnounceReason/cleanupHandled BEFORE cancel (aligned with killRun)
                record.suppressAnnounceReason = "killed"
                record.cleanupHandled = true
                val job = jobs[currentRunId]
                job?.cancel()
                markCompleted(
                    currentRunId,
                    SubagentRunOutcome(SubagentRunStatus.ERROR, "Killed by parent (cascade)"),
                    SubagentLifecycleEndedReason.SUBAGENT_KILLED,
                    frozenResult = null,
                )
                killed.add(currentRunId)
            }
        }

        if (killed.isNotEmpty()) {
            Log.i(TAG, "Cascade killed ${killed.size} runs starting from $runId")
        }
        return killed
    }

    // ==================== Run Replacement (Steer Restart) ====================

    /**
     * Replace a run record with a new one after steer restart.
     * Old run stays in registry for history; new run takes over runtime references.
     * Aligned with OpenClaw replaceSubagentRunAfterSteer.
     */
    fun replaceRun(oldRunId: String, newRecord: SubagentRunRecord, loop: AgentLoopInterface, job: Job) {
        // Preserve frozen result as fallback (aligned with OpenClaw preserveFrozenResultFallback)
        val oldRecord = runs[oldRunId]
        if (oldRecord?.frozenResultText != null && newRecord.fallbackFrozenResultText == null) {
            newRecord.fallbackFrozenResultText = oldRecord.frozenResultText
            newRecord.fallbackFrozenResultCapturedAt = oldRecord.frozenResultCapturedAt
        }

        // Remove old runtime references (record stays for history)
        agentLoops.remove(oldRunId)
        jobs.remove(oldRunId)

        // Register new run
        runs[newRecord.runId] = newRecord
        agentLoops[newRecord.runId] = loop
        jobs[newRecord.runId] = job
        Log.i(TAG, "Replaced run: $oldRunId -> ${newRecord.runId} (session ${newRecord.childSessionKey})")
        persistToDisk()
    }

    // ==================== Release ====================

    /**
     * Fully remove a run from all maps.
     * Aligned with OpenClaw releaseSubagentRun.
     */
    fun releaseSubagentRun(runId: String) {
        runs.remove(runId)
        agentLoops.remove(runId)
        jobs.remove(runId)
        Log.d(TAG, "Released subagent run: $runId")
        listeners.forEach { it.onRunReleased(runId) }
        persistToDisk()
    }

    // ==================== Cleanup ====================

    /**
     * Remove archived runs whose archiveAtMs has passed.
     * Aligned with OpenClaw sweeper that runs periodically.
     */
    fun sweepArchived() {
        val now = System.currentTimeMillis()
        val toRemove = runs.values.filter { record ->
            !record.isActive && record.archiveAtMs != null && now >= record.archiveAtMs!!
        }
        // Fallback: also sweep runs completed longer than ARCHIVE_AFTER_MS without archiveAtMs
        val legacySweep = runs.values.filter { record ->
            !record.isActive && record.archiveAtMs == null &&
                record.endedAt != null && (now - record.endedAt!!) > ARCHIVE_AFTER_MS
        }
        val allToRemove = (toRemove + legacySweep).distinctBy { it.runId }
        for (record in allToRemove) {
            runs.remove(record.runId)
            agentLoops.remove(record.runId)
            jobs.remove(record.runId)
        }
        if (allToRemove.isNotEmpty()) {
            Log.i(TAG, "Swept ${allToRemove.size} archived subagent runs")
            persistToDisk()
        }
    }
}
