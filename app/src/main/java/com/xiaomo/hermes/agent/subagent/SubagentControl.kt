/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-control.ts (killControlledSubagentRun, steerControlledSubagentRun)
 *
 * Hermes adaptation: control operations (kill/steer) for running subagents.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.agent.loop.AgentLoop
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val TAG = "SubagentControl"

// ==================== Ownership Check ====================

/**
 * Verify that the caller owns (controls) the given run.
 * Aligned with OpenClaw ensureControllerOwnsRun.
 * Returns error message if not authorized, null if ok.
 */
internal fun ensureControllerOwnsRun(callerSessionKey: String, record: SubagentRunRecord): String? {
    val controller = record.controllerSessionKey ?: record.requesterSessionKey
    return if (callerSessionKey != controller) {
        "Caller $callerSessionKey does not control run ${record.runId} (controller: $controller)"
    } else null
}

// ==================== Control Operations ====================

/**
 * Kill a running subagent, optionally with cascade.
 * Aligned with OpenClaw killControlledSubagentRun + cascadeKillChildren.
 *
 * @return Pair of (success, list of killed runIds)
 */
internal fun SubagentSpawner.killSubagent(
    runId: String,
    cascade: Boolean = false,
    callerSessionKey: String? = null,
): Pair<Boolean, List<String>> {
    // ControlScope check (aligned with OpenClaw: leaf subagents cannot kill)
    if (callerSessionKey != null) {
        val callerRun = registry.getRunByChildSessionKey(callerSessionKey)
        if (callerRun != null) {
            val callerCaps = resolveSubagentCapabilities(callerRun.depth)
            if (callerCaps.controlScope == SubagentControlScope.NONE) {
                Log.w(TAG, "Kill denied: leaf subagents cannot control other sessions")
                return Pair(false, emptyList())
            }
        }
    }

    // Ownership check
    if (callerSessionKey != null) {
        val record = registry.getRunById(runId) ?: return Pair(false, emptyList())
        val error = ensureControllerOwnsRun(callerSessionKey, record)
        if (error != null) {
            Log.w(TAG, "Kill denied: $error")
            return Pair(false, emptyList())
        }
    }

    return if (cascade) {
        val killed = registry.cascadeKill(runId)
        Pair(killed.isNotEmpty(), killed)
    } else {
        val success = registry.killRun(runId)
        Pair(success, if (success) listOf(runId) else emptyList())
    }
}

/**
 * Admin kill: kill a subagent by session key without ownership check.
 * Includes cascade to descendants.
 * Aligned with OpenClaw killSubagentRunAdmin.
 */
internal fun SubagentSpawner.killSubagentAdmin(sessionKey: String): Map<String, Any?> {
    val entry = registry.getRunByChildSessionKey(sessionKey)
        ?: return mapOf("found" to false, "killed" to false)

    val (killed, killedIds) = killSubagent(entry.runId, cascade = true, callerSessionKey = null)
    val cascadeKilled = if (killedIds.size > 1) killedIds.size - 1 else 0

    return mapOf(
        "found" to true,
        "killed" to killed,
        "runId" to entry.runId,
        "sessionKey" to entry.childSessionKey,
        "cascadeKilled" to cascadeKilled,
    )
}

/**
 * Steer a running subagent: abort current run and restart with new message.
 * Aligned with OpenClaw steerControlledSubagentRun (abort + restart semantics).
 *
 * Flow:
 * 1. Ownership check
 * 2. Self-steer prevention
 * 3. Rate limit check (2s per caller-target pair)
 * 4. Mark for steer-restart (suppress announce)
 * 5. Cancel the child coroutine Job (abort)
 * 6. Clear steer channel
 * 7. Wait for abort to settle (5s, aligned with OpenClaw STEER_ABORT_SETTLE_TIMEOUT_MS)
 * 8. Reset AgentLoop internal state
 * 9. Accumulate old runtime
 * 10. Mark old run completed
 * 11. Create new run record (preserving session key)
 * 12. Launch new run() with steer message
 * 13. Replace run record in registry
 */
internal suspend fun SubagentSpawner.steerSubagent(
    runId: String,
    message: String,
    callerSessionKey: String,
    parentAgentLoop: AgentLoopInterface,
): Pair<Boolean, String?> {
    val record = registry.getRunById(runId) ?: return Pair(false, "Run not found: $runId")
    if (!record.isActive) return Pair(false, "Run already completed: $runId")
    val childLoop = registry.getAgentLoop(runId) ?: return Pair(false, "AgentLoop not found for: $runId")
    val job = registry.getJob(runId) ?: return Pair(false, "Job not found for: $runId")

    // 1. ControlScope check (aligned with OpenClaw: leaf subagents cannot steer)
    val callerRun = registry.getRunByChildSessionKey(callerSessionKey)
    if (callerRun != null) {
        val callerCaps = resolveSubagentCapabilities(callerRun.depth)
        if (callerCaps.controlScope == SubagentControlScope.NONE) {
            return Pair(false, "Leaf subagents cannot control other sessions.")
        }
    }

    // 2. Ownership check
    val ownershipError = ensureControllerOwnsRun(callerSessionKey, record)
    if (ownershipError != null) {
        return Pair(false, ownershipError)
    }

    // 3. Self-steer prevention (aligned with OpenClaw)
    if (callerSessionKey == record.childSessionKey) {
        return Pair(false, "Cannot steer own session")
    }

    // 4. Message length check (aligned with OpenClaw MAX_STEER_MESSAGE_CHARS)
    if (message.length > MAX_STEER_MESSAGE_CHARS) {
        return Pair(false, "Message too long: ${message.length} > $MAX_STEER_MESSAGE_CHARS chars")
    }

    // 5. Rate limit check (aligned with OpenClaw: key is caller:childSessionKey, not caller:runId)
    val rateKey = "$callerSessionKey:${record.childSessionKey}"
    val now = System.currentTimeMillis()
    val lastTime = lastSteerTime[rateKey]
    if (lastTime != null && (now - lastTime) < STEER_RATE_LIMIT_MS) {
        val waitMs = STEER_RATE_LIMIT_MS - (now - lastTime)
        return Pair(false, "Rate limited: wait ${waitMs}ms")
    }
    lastSteerTime[rateKey] = now

    // 5. Mark for steer-restart (suppress old run's announce)
    record.suppressAnnounceReason = "steer-restart"

    // 6. Cancel the child coroutine Job (abort)
    Log.i(TAG, "Steer: aborting run $runId for restart")
    job.cancel()

    // 7. Clear steer channel
    while (childLoop.steerChannel.tryReceive().isSuccess) { /* drain */ }

    // 8. Wait for abort to settle (aligned with OpenClaw STEER_ABORT_SETTLE_TIMEOUT_MS = 5s)
    try {
        delay(STEER_ABORT_SETTLE_TIMEOUT_MS)
    } catch (_: Exception) { }

    // 9. Reset AgentLoop state
    childLoop.reset()

    // 10. Accumulate runtime from old run
    val oldRuntimeMs = record.runtimeMs

    // 11. Mark old run as completed (steer-restarted)
    registry.markCompleted(
        runId,
        SubagentRunOutcome(SubagentRunStatus.OK, "Steered (restarted)"),
        SubagentLifecycleEndedReason.SUBAGENT_COMPLETE,
        frozenResult = null,
    )

    // 12. Create new run record (preserving session key, controllerSessionKey, sessionStartedAt)
    val newRunId = UUID.randomUUID().toString()
    val newRecord = SubagentRunRecord(
        runId = newRunId,
        childSessionKey = record.childSessionKey,
        controllerSessionKey = record.controllerSessionKey,
        requesterSessionKey = record.requesterSessionKey,
        requesterDisplayKey = record.requesterDisplayKey,
        task = message,
        label = record.label,
        model = record.model,
        cleanup = record.cleanup,
        spawnMode = record.spawnMode,
        workspaceDir = record.workspaceDir,
        createdAt = System.currentTimeMillis(),
        runTimeoutSeconds = record.runTimeoutSeconds,
        depth = record.depth,
    ).apply {
        accumulatedRuntimeMs = oldRuntimeMs
        sessionStartedAt = record.sessionStartedAt ?: record.startedAt
        expectsCompletionMessage = record.expectsCompletionMessage
    }

    // 13. Rebuild system prompt
    val config = try {
        configLoader.loadOpenClawConfig().agents?.defaults?.subagents ?: com.xiaomo.hermes.config.SubagentsConfig()
    } catch (_: Exception) { com.xiaomo.hermes.config.SubagentsConfig() }
    val childCapabilities = resolveSubagentCapabilities(record.depth, config.maxSpawnDepth)
    val systemPrompt = SubagentPromptBuilder.build(
        task = message,
        label = record.label,
        capabilities = childCapabilities,
        parentSessionKey = record.requesterSessionKey,
        childSessionKey = record.childSessionKey,
    )

    // 14. Launch new coroutine with conversation context from previous run
    val timeoutMs = (record.runTimeoutSeconds ?: config.defaultTimeoutSeconds).let {
        if (it > 0) it * 1000L else 0L
    }
    val previousMessages = childLoop.conversationMessages.toList()

    val newJob = scope.launch {
        newRecord.startedAt = System.currentTimeMillis()
        Log.i(TAG, "Steer restart: $newRunId (was $runId)")

        try {
            val result = if (timeoutMs > 0) {
                withTimeoutOrNull(timeoutMs) {
                    childLoop.run(
                        systemPrompt = systemPrompt,
                        userMessage = message,
                        contextHistory = previousMessages.drop(1), // Skip system prompt
                        reasoningEnabled = true,
                    )
                }
            } else {
                childLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = message,
                    contextHistory = previousMessages.drop(1),
                    reasoningEnabled = true,
                )
            }

            if (result == null) {
                val outcome = SubagentRunOutcome(SubagentRunStatus.TIMEOUT)
                registry.markCompleted(newRunId, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR, null)
                emitSubagentEndedHookOnce(newRecord, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR)
                announceToParent(parentAgentLoop, newRecord, outcome)
            } else {
                val outcome = SubagentRunOutcome(SubagentRunStatus.OK)
                val frozenText = capFrozenResultText(result.finalContent)
                newRecord.frozenResultText = frozenText
                registry.markCompleted(newRunId, outcome, SubagentLifecycleEndedReason.SUBAGENT_COMPLETE, frozenText)
                emitSubagentEndedHookOnce(newRecord, outcome, SubagentLifecycleEndedReason.SUBAGENT_COMPLETE)
                announceToParent(parentAgentLoop, newRecord, outcome)
            }

            checkDescendantSettle(newRecord, parentAgentLoop)
        } catch (e: kotlinx.coroutines.CancellationException) {
            val outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "Killed")
            registry.markCompleted(newRunId, outcome, SubagentLifecycleEndedReason.SUBAGENT_KILLED, null)
            emitSubagentEndedHookOnce(newRecord, outcome, SubagentLifecycleEndedReason.SUBAGENT_KILLED)
            announceToParent(parentAgentLoop, newRecord, outcome)
        } catch (e: Exception) {
            val outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, e.message)
            registry.markCompleted(newRunId, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR, null)
            emitSubagentEndedHookOnce(newRecord, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR)
            announceToParent(parentAgentLoop, newRecord, outcome)
        }
    }

    // 15. Replace in registry
    registry.replaceRun(runId, newRecord, childLoop, newJob)

    Log.i(TAG, "Steer complete: $runId -> $newRunId")
    return Pair(true, "Steered: run restarted as $newRunId")
}
