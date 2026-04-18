/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-announce.ts (runSubagentAnnounceFlow, announceToParent)
 * - ../openclaw/src/agents/subagent-registry-completion.ts (emitSubagentEndedHookOnce)
 *
 * Hermes adaptation: announce subagent completion to parent via steerChannel.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.agent.loop.AgentLoop
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SubagentAnnounce"

// ==================== Announce ====================

/**
 * Announce subagent completion to parent via steerChannel.
 * Aligned with OpenClaw runSubagentAnnounceFlow:
 * 1. Check suppressed announce (steer-restart, killed)
 * 2. Check pending descendants -> defer if > 0
 * 3. Check expectsCompletionMessage
 * 4. Collect child completion findings
 * 5. Retry with exponential backoff
 * 6. Complete parent's yield signal if present
 */
internal suspend fun SubagentSpawner.announceToParent(
    parentAgentLoop: AgentLoopInterface,
    record: SubagentRunRecord,
    outcome: SubagentRunOutcome,
) {
    // Check if announce is suppressed (steer-restart or killed)
    if (record.suppressAnnounceReason == "steer-restart" || record.suppressAnnounceReason == "killed") {
        Log.d(TAG, "Announce suppressed for ${record.runId}: ${record.suppressAnnounceReason}")
        return
    }

    // Check if post-completion announce should be ignored
    if (registry.shouldIgnorePostCompletionAnnounceForSession(record.childSessionKey)) {
        Log.d(TAG, "Ignoring post-completion announce for ${record.runId}")
        return
    }

    // 1. Check pending descendants -- if > 0, defer announce
    val pendingDescendants = registry.countPendingDescendantRunsExcludingRun(record.childSessionKey, record.runId)
    if (pendingDescendants > 0) {
        Log.i(TAG, "Deferring announce for ${record.runId}: $pendingDescendants pending descendants")
        record.suppressAnnounceReason = "pending_descendants:$pendingDescendants"
        record.wakeOnDescendantSettle = true
        return
    }

    // 2. Check expectsCompletionMessage -- if false, only freeze text
    if (!record.expectsCompletionMessage) {
        Log.d(TAG, "Skipping steerChannel announce for ${record.runId}: expectsCompletionMessage=false")
        record.cleanupCompletedAt = System.currentTimeMillis()
        registry.sweepArchived()
        return
    }

    // 3. Collect child completion findings
    val children = registry.listRunsForRequester(record.childSessionKey)
    val findings = SubagentPromptBuilder.buildChildCompletionFindings(children)

    // 4. Build announcement using output text selection
    // Determine if requester is itself a subagent (for reply instruction)
    val requesterIsSubagent = registry.getRunByChildSessionKey(record.requesterSessionKey) != null
    val announcement = SubagentPromptBuilder.buildAnnouncement(
        record, outcome, findings, requesterIsSubagent
    )

    // 5. Retry with fixed delay table (aligned with OpenClaw DIRECT_ANNOUNCE_TRANSIENT_RETRY_DELAYS_MS)
    var sent = false
    val maxAttempts = ANNOUNCE_RETRY_DELAYS_MS.size + 1
    for (attempt in 0 until maxAttempts) {
        val result = parentAgentLoop.steerChannel.trySend(announcement)
        if (result.isSuccess) {
            sent = true
            record.announceRetryCount = attempt
            Log.i(TAG, "Announced ${record.runId} to parent (attempt ${attempt + 1}/$maxAttempts)")
            break
        }

        record.lastAnnounceRetryAt = System.currentTimeMillis()
        val delayMs = computeAnnounceRetryDelayMs(attempt)
        if (delayMs != null) {
            Log.w(TAG, "Announce retry ${attempt + 1}/$maxAttempts for ${record.runId}, waiting ${delayMs}ms")
            delay(delayMs)
        } else {
            break // No more retries
        }
    }

    if (!sent) {
        Log.e(TAG, "Failed to announce ${record.runId} after $maxAttempts attempts")
        record.suppressAnnounceReason = "channel_full_after_retries"
    }

    // Mark cleanup completed
    record.cleanupCompletedAt = System.currentTimeMillis()

    // 6. Complete parent's yield signal if present (sessions_yield)
    parentAgentLoop.yieldSignal?.let { deferred ->
        if (!deferred.isCompleted) {
            deferred.complete(announcement)
            Log.i(TAG, "Completed yield signal for parent after announcing ${record.runId}")
        }
    }

    // Sweep old archived runs
    registry.sweepArchived()
}

/**
 * Check if any ancestor has wakeOnDescendantSettle and all descendants
 * are now settled. If so, re-announce the ancestor with collected findings.
 * Called after every run completion.
 * Aligned with OpenClaw descendant settle wake logic.
 */
internal suspend fun SubagentSpawner.checkDescendantSettle(
    completedRecord: SubagentRunRecord,
    parentAgentLoop: AgentLoopInterface,
) {
    // Walk up: find the parent run that spawned the completed child
    val parentRun = registry.getRunByChildSessionKey(completedRecord.requesterSessionKey) ?: return

    if (!parentRun.wakeOnDescendantSettle) return

    val remaining = registry.countPendingDescendantRunsExcludingRun(
        parentRun.childSessionKey,
        parentRun.runId
    )
    if (remaining > 0) {
        Log.d(TAG, "Parent ${parentRun.runId} still has $remaining pending descendants")
        return
    }

    // All descendants settled -- collect findings and announce
    Log.i(TAG, "All descendants settled for ${parentRun.runId}, triggering deferred announce")
    parentRun.wakeOnDescendantSettle = false
    parentRun.suppressAnnounceReason = null

    val outcome = parentRun.outcome ?: SubagentRunOutcome(SubagentRunStatus.OK)
    announceToParent(parentAgentLoop, parentRun, outcome)
}

// ==================== Lifecycle Hook ====================

/**
 * Emit subagent_ended hook exactly once per run.
 * Aligned with OpenClaw emitSubagentEndedHookOnce.
 */
internal fun SubagentSpawner.emitSubagentEndedHookOnce(
    record: SubagentRunRecord,
    outcome: SubagentRunOutcome,
    reason: SubagentLifecycleEndedReason,
) {
    if (record.endedHookEmittedAt != null) return
    record.endedHookEmittedAt = System.currentTimeMillis()
    Log.d(TAG, "Subagent ended hook: ${record.runId} status=${outcome.status} reason=$reason")

    // Fire hooks asynchronously (aligned with OpenClaw emitSubagentEndedHookOnce)
    scope.launch {
        try {
            hooks.runEnded(SubagentEndedEvent(
                targetSessionKey = record.childSessionKey,
                targetKind = SubagentLifecycleTargetKind.SUBAGENT,
                reason = reason.wireValue,
                runId = record.runId,
                endedAt = record.endedHookEmittedAt,
                outcome = resolveLifecycleOutcome(outcome),
                error = outcome.error,
            ))
        } catch (e: Exception) {
            Log.w(TAG, "Error running subagent ended hook: ${e.message}")
        }
    }
}
