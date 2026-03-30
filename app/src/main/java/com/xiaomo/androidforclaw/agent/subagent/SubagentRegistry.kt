/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-registry.ts (in-memory registry, lifecycle listener, completion flow)
 * - ../openclaw/src/agents/subagent-registry-queries.ts (descendant counting, BFS traversal, controller queries)
 * - ../openclaw/src/agents/subagent-control.ts (resolveControlledSubagentTarget)
 * - ../openclaw/src/agents/subagent-registry-state.ts (persistence, orphan reconciliation)
 *
 * AndroidForClaw adaptation: ConcurrentHashMap-based registry tracking active/completed subagent runs.
 * Includes target resolution, cascade kill, descendant tracking, run replacement,
 * disk persistence, listener interface, and controller-based queries.
 */
package com.xiaomo.androidforclaw.agent.subagent

import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.logging.Log
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

    /** runId → SubagentRunRecord */
    private val runs = ConcurrentHashMap<String, SubagentRunRecord>()

    /** runId → coroutine Job */
    private val jobs = ConcurrentHashMap<String, Job>()

    /** runId → child AgentLoop (for steer/kill/history) */
    private val agentLoops = ConcurrentHashMap<String, AgentLoop>()

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
     * Active runs without Job are orphans — mark as error.
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

        // Step 2: Reconcile orphans — separate step aligned with OpenClaw reconcileOrphanedRestoredRuns.
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

    fun registerRun(record: SubagentRunRecord, loop: AgentLoop, job: Job) {
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

    // ==================== Basic Queries ====================

    fun getRunById(runId: String): SubagentRunRecord? = runs[runId]

    fun getAgentLoop(runId: String): AgentLoop? = agentLoops[runId]

    fun getJob(runId: String): Job? = jobs[runId]

    /**
     * Find run by child session key.
     * Returns active run first, fallback to any matching run (latest).
     * Aligned with OpenClaw getSubagentRunByChildSessionKey.
     */
    fun getRunByChildSessionKey(childSessionKey: String): SubagentRunRecord? {
        return runs.values.find { it.childSessionKey == childSessionKey && it.isActive }
            ?: runs.values
                .filter { it.childSessionKey == childSessionKey }
                .maxByOrNull { it.createdAt }
    }

    fun getActiveRunsForParent(parentSessionKey: String): List<SubagentRunRecord> {
        return runs.values.filter { it.requesterSessionKey == parentSessionKey && it.isActive }
    }

    fun getAllRuns(parentSessionKey: String): List<SubagentRunRecord> {
        return runs.values
            .filter { it.requesterSessionKey == parentSessionKey }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Get a snapshot of all runs (keyed by runId).
     * Used for orphan recovery scanning.
     */
    fun getRunsSnapshot(): Map<String, SubagentRunRecord> {
        return runs.toMap()
    }

    /**
     * Build indexed list: active runs first (sorted by createdAt desc),
     * then completed runs (sorted by endedAt desc).
     * Used for numeric index resolution and list display.
     * Aligned with OpenClaw buildSubagentList ordering.
     */
    fun buildIndexedList(parentSessionKey: String): List<SubagentRunRecord> {
        val allRuns = runs.values.filter { it.requesterSessionKey == parentSessionKey }
        val active = allRuns.filter { it.isActive }.sortedByDescending { it.createdAt }
        val completed = allRuns.filter { !it.isActive }.sortedByDescending { it.endedAt }
        return active + completed
    }

    /**
     * List all runs spawned by a given requester session key (direct children).
     * Optional requesterRunId provides time-window scoping.
     * Aligned with OpenClaw listRunsForRequesterFromRuns.
     */
    fun listRunsForRequester(
        requesterSessionKey: String,
        requesterRunId: String? = null,
    ): List<SubagentRunRecord> {
        val key = requesterSessionKey.trim()
        if (key.isEmpty()) return emptyList()

        // Time-window scoping from requester run (aligned with OpenClaw)
        val requesterRun = requesterRunId?.trim()?.let { rid -> runs[rid] }
        val scopedRun = if (requesterRun != null && requesterRun.childSessionKey == key) requesterRun else null
        val lowerBound = scopedRun?.startedAt ?: scopedRun?.createdAt
        val upperBound = scopedRun?.endedAt

        return runs.values
            .filter { entry ->
                if (entry.requesterSessionKey != key) return@filter false
                if (lowerBound != null && entry.createdAt < lowerBound) return@filter false
                if (upperBound != null && entry.createdAt > upperBound) return@filter false
                true
            }
            .sortedByDescending { it.createdAt }
    }

    /**
     * List runs where controllerSessionKey matches.
     * Falls back to requesterSessionKey if controllerSessionKey is null.
     * Aligned with OpenClaw listRunsForControllerFromRuns.
     */
    fun listRunsForController(controllerKey: String): List<SubagentRunRecord> {
        return runs.values
            .filter {
                val key = it.controllerSessionKey ?: it.requesterSessionKey
                key == controllerKey
            }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Count active runs for a session (using controllerSessionKey).
     * Active = not ended OR has pending descendants.
     * Aligned with OpenClaw countActiveRunsForSessionFromRuns.
     */
    fun countActiveRunsForSession(controllerSessionKey: String): Int {
        return runs.values.count { record ->
            val key = record.controllerSessionKey ?: record.requesterSessionKey
            key == controllerSessionKey && isActiveSubagentRun(record) { sessionKey ->
                countPendingDescendantRuns(sessionKey)
            }
        }
    }

    fun activeChildCount(parentSessionKey: String): Int {
        return countActiveRunsForSession(parentSessionKey)
    }

    /**
     * Check if parent can spawn more children.
     * Aligned with OpenClaw active children check in spawnSubagentDirect.
     */
    fun canSpawn(parentSessionKey: String, maxChildren: Int): Boolean {
        return activeChildCount(parentSessionKey) < maxChildren
    }

    // ==================== Advanced Queries ====================

    /**
     * Find all runIds associated with a child session key.
     * Aligned with OpenClaw findRunIdsByChildSessionKeyFromRuns.
     */
    fun findRunIdsByChildSessionKey(childSessionKey: String): List<String> {
        return runs.values
            .filter { it.childSessionKey == childSessionKey }
            .map { it.runId }
    }

    /**
     * Resolve requester for a child session.
     * Returns requesterSessionKey of the latest run for the child.
     * Aligned with OpenClaw resolveRequesterForChildSessionFromRuns.
     */
    fun resolveRequesterForChildSession(childSessionKey: String): String? {
        return runs.values
            .filter { it.childSessionKey == childSessionKey }
            .maxByOrNull { it.createdAt }
            ?.requesterSessionKey
    }

    /**
     * Check if any run for the given child session key is active.
     * Aligned with OpenClaw isSubagentSessionRunActive.
     */
    fun isSubagentSessionRunActive(childSessionKey: String): Boolean {
        return runs.values.any { it.childSessionKey == childSessionKey && it.isActive }
    }

    /**
     * Check if post-completion announce should be ignored for a session.
     * True if the session's run mode is RUN and cleanup has already been completed.
     * Aligned with OpenClaw shouldIgnorePostCompletionAnnounceForSessionFromRuns.
     */
    fun shouldIgnorePostCompletionAnnounceForSession(childSessionKey: String): Boolean {
        val latestRun = runs.values
            .filter { it.childSessionKey == childSessionKey }
            .maxByOrNull { it.createdAt } ?: return false
        return latestRun.spawnMode != SpawnMode.SESSION &&
            latestRun.endedAt != null &&
            latestRun.cleanupCompletedAt != null &&
            latestRun.cleanupCompletedAt!! >= latestRun.endedAt!!
    }

    // ==================== Target Resolution ====================

    /**
     * Resolve a target token to a SubagentRunRecord.
     * Resolution order aligned with OpenClaw resolveControlledSubagentTarget:
     * 1. "last" keyword → most recently started active run (or most recent)
     * 2. Numeric index → 1-based index into buildIndexedList
     * 3. Contains ":" → session key exact match
     * 4. Exact label match (case-insensitive)
     * 5. Label prefix match (case-insensitive)
     * 6. RunId prefix match
     */
    fun resolveTarget(token: String, parentSessionKey: String): SubagentRunRecord? {
        if (token.isBlank()) return null

        val parentRuns = getAllRuns(parentSessionKey)
        if (parentRuns.isEmpty()) return null

        // 1. "last" keyword
        if (token.equals("last", ignoreCase = true)) {
            return parentRuns.firstOrNull { it.isActive }
                ?: parentRuns.firstOrNull()
        }

        // 2. Numeric index (1-based)
        token.toIntOrNull()?.let { index ->
            val indexed = buildIndexedList(parentSessionKey)
            return indexed.getOrNull(index - 1)
        }

        // 3. Session key (contains ":")
        if (":" in token) {
            return parentRuns.find { it.childSessionKey == token }
        }

        // 4. Exact label match (case-insensitive)
        val exactLabel = parentRuns.filter { it.label.equals(token, ignoreCase = true) }
        if (exactLabel.size == 1) return exactLabel[0]

        // 5. Label prefix match (case-insensitive)
        val prefixLabel = parentRuns.filter { it.label.startsWith(token, ignoreCase = true) }
        if (prefixLabel.size == 1) return prefixLabel[0]

        // 6. RunId prefix match
        val prefixRunId = parentRuns.filter { it.runId.startsWith(token) }
        if (prefixRunId.size == 1) return prefixRunId[0]

        return null
    }

    // ==================== Descendant Tracking ====================

    /**
     * Count pending (active or not-cleanup-completed) descendant runs.
     * BFS traversal through the spawn tree.
     * Aligned with OpenClaw countPendingDescendantRunsFromRuns.
     */
    fun countPendingDescendantRuns(sessionKey: String): Int {
        var count = 0
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(sessionKey)
        visited.add(sessionKey)

        while (queue.isNotEmpty()) {
            val currentKey = queue.removeAt(0)
            val children = runs.values.filter { it.requesterSessionKey == currentKey }
            for (child in children) {
                if (child.isActive || child.cleanupCompletedAt == null) count++
                if (child.childSessionKey !in visited) {
                    visited.add(child.childSessionKey)
                    queue.add(child.childSessionKey)
                }
            }
        }
        return count
    }

    /**
     * Same as countPendingDescendantRuns but excluding a specific runId.
     * Used during announce to exclude the run being announced.
     * Aligned with OpenClaw countPendingDescendantRunsExcludingRunFromRuns.
     */
    fun countPendingDescendantRunsExcludingRun(sessionKey: String, excludeRunId: String): Int {
        var count = 0
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(sessionKey)
        visited.add(sessionKey)

        while (queue.isNotEmpty()) {
            val currentKey = queue.removeAt(0)
            val children = runs.values.filter { it.requesterSessionKey == currentKey }
            for (child in children) {
                if (child.runId != excludeRunId && (child.isActive || child.cleanupCompletedAt == null)) {
                    count++
                }
                if (child.childSessionKey !in visited) {
                    visited.add(child.childSessionKey)
                    queue.add(child.childSessionKey)
                }
            }
        }
        return count
    }

    /**
     * Count active (not ended) descendant runs.
     * Aligned with OpenClaw countActiveDescendantRunsFromRuns.
     */
    fun countActiveDescendantRuns(sessionKey: String): Int {
        var count = 0
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(sessionKey)
        visited.add(sessionKey)

        while (queue.isNotEmpty()) {
            val currentKey = queue.removeAt(0)
            val children = runs.values.filter { it.requesterSessionKey == currentKey }
            for (child in children) {
                if (child.isActive) count++
                if (child.childSessionKey !in visited) {
                    visited.add(child.childSessionKey)
                    queue.add(child.childSessionKey)
                }
            }
        }
        return count
    }

    /**
     * List all descendant runs recursively.
     * Aligned with OpenClaw listDescendantRunsForRequesterFromRuns.
     */
    fun listDescendantRuns(sessionKey: String): List<SubagentRunRecord> {
        val result = mutableListOf<SubagentRunRecord>()
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(sessionKey)
        visited.add(sessionKey)

        while (queue.isNotEmpty()) {
            val currentKey = queue.removeAt(0)
            val children = runs.values.filter { it.requesterSessionKey == currentKey }
            for (child in children) {
                result.add(child)
                if (child.childSessionKey !in visited) {
                    visited.add(child.childSessionKey)
                    queue.add(child.childSessionKey)
                }
            }
        }
        return result
    }

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
    fun replaceRun(oldRunId: String, newRecord: SubagentRunRecord, loop: AgentLoop, job: Job) {
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
        Log.i(TAG, "Replaced run: $oldRunId → ${newRecord.runId} (session ${newRecord.childSessionKey})")
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
