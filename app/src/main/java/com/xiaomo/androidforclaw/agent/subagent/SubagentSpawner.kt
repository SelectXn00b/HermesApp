/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-spawn.ts (spawnSubagentDirect)
 * - ../openclaw/src/agents/subagent-announce.ts (runSubagentAnnounceFlow, announceToParent)
 * - ../openclaw/src/agents/subagent-control.ts (killControlledSubagentRun, steerControlledSubagentRun)
 * - ../openclaw/src/agents/subagent-registry-completion.ts (emitSubagentEndedHookOnce)
 *
 * AndroidForClaw adaptation: in-process coroutine-based subagent spawning.
 * Replaces OpenClaw's gateway WebSocket communication with direct steerChannel injection.
 */
package com.xiaomo.androidforclaw.agent.subagent

import com.xiaomo.androidforclaw.acp.AcpClient
import com.xiaomo.androidforclaw.agent.context.ContextManager
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.routing.isSubagentSessionKey
import com.xiaomo.androidforclaw.routing.getSubagentDepth
import com.xiaomo.androidforclaw.routing.isAcpSessionKey
import com.xiaomo.androidforclaw.agent.tools.SessionsHistoryTool
import com.xiaomo.androidforclaw.agent.tools.SessionsKillTool
import com.xiaomo.androidforclaw.agent.tools.SessionsListTool
import com.xiaomo.androidforclaw.agent.tools.SessionsSendTool
import com.xiaomo.androidforclaw.agent.tools.SessionsSpawnTool
import com.xiaomo.androidforclaw.agent.tools.SessionStatusTool
import com.xiaomo.androidforclaw.agent.tools.SessionsYieldTool
import com.xiaomo.androidforclaw.agent.tools.SubagentsTool
import com.xiaomo.androidforclaw.agent.tools.Tool
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.SubagentsConfig
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core subagent spawner — validates, creates, and manages subagent AgentLoop instances.
 * Aligned with OpenClaw spawnSubagentDirect + announce flow + control operations.
 *
 * Android-specific: subagents run as in-process coroutines with direct steerChannel communication
 * (no gateway WebSocket).
 */
class SubagentSpawner(
    val registry: SubagentRegistry,
    private val configLoader: ConfigLoader,
    private val llmProvider: UnifiedLLMProvider,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    val hooks: SubagentHooks = SubagentHooks(),
) {
    companion object {
        private const val TAG = "SubagentSpawner"

        /**
         * Build the set of subagent tools for a given parent session.
         * LEAF agents get no subagent tools (they cannot spawn).
         * Aligned with OpenClaw per-session tool injection.
         */
        fun buildSubagentTools(
            spawner: SubagentSpawner,
            parentSessionKey: String,
            parentAgentLoop: AgentLoop,
            parentDepth: Int,
            configLoader: ConfigLoader,
        ): List<Tool> {
            return listOf(
                SessionsSpawnTool(spawner, parentSessionKey, parentAgentLoop, parentDepth),
                SessionsListTool(spawner.registry, parentSessionKey),
                SessionsSendTool(spawner, parentSessionKey, parentAgentLoop),
                SessionsKillTool(spawner, parentSessionKey),
                SessionsHistoryTool(spawner.registry, parentSessionKey),
                SessionsYieldTool(parentAgentLoop),
                SubagentsTool(spawner, spawner.registry, parentSessionKey, parentAgentLoop),
                SessionStatusTool(spawner.registry, parentSessionKey, configLoader),
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Rate limit: last steer time per (caller, target) pair. Aligned with OpenClaw steerRateLimit Map. */
    private val lastSteerTime = ConcurrentHashMap<String, Long>()

    // ==================== Ownership Check ====================

    /**
     * Verify that the caller owns (controls) the given run.
     * Aligned with OpenClaw ensureControllerOwnsRun.
     * Returns error message if not authorized, null if ok.
     */
    private fun ensureControllerOwnsRun(callerSessionKey: String, record: SubagentRunRecord): String? {
        val controller = record.controllerSessionKey ?: record.requesterSessionKey
        return if (callerSessionKey != controller) {
            "Caller $callerSessionKey does not control run ${record.runId} (controller: $controller)"
        } else null
    }

    // ==================== Spawn ====================

    /**
     * Spawn a subagent.
     * Aligned with OpenClaw spawnSubagentDirect validation + launch flow.
     */
    suspend fun spawn(
        params: SpawnSubagentParams,
        parentSessionKey: String,
        parentAgentLoop: AgentLoop,
        parentDepth: Int,
    ): SpawnSubagentResult {
        val config = try {
            configLoader.loadOpenClawConfig().agents?.defaults?.subagents ?: SubagentsConfig()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load subagents config, using defaults: ${e.message}")
            SubagentsConfig()
        }

        if (!config.enabled) {
            return SpawnSubagentResult(
                status = SpawnStatus.FORBIDDEN,
                note = "Subagents are disabled in configuration."
            )
        }

        // ACP session key check — if parent is an ACP session, log it
        if (isAcpSessionKey(parentSessionKey)) {
            Log.d(TAG, "Spawn requested from ACP session: $parentSessionKey")
        }

        // ACP runtime check — not supported on Android
        if (params.runtime == "acp") {
            return SpawnSubagentResult(
                status = SpawnStatus.ERROR,
                error = "ACP runtime is not supported on Android."
            )
        }

        // SESSION mode requires thread=true (aligned with OpenClaw)
        if (params.mode == SpawnMode.SESSION && params.thread != true) {
            return SpawnSubagentResult(
                status = SpawnStatus.ERROR,
                error = "mode=\"session\" requires thread=true so the subagent can stay bound to a thread."
            )
        }

        // 1. Depth check — use routing.getSubagentDepth() for cross-validation
        val routingDepth = getSubagentDepth(parentSessionKey)
        val childDepth = parentDepth + 1
        if (routingDepth > 0) {
            Log.d(TAG, "Routing depth for $parentSessionKey: $routingDepth (param depth: $parentDepth)")
        }
        if (parentDepth >= config.maxSpawnDepth) {
            return SpawnSubagentResult(
                status = SpawnStatus.FORBIDDEN,
                note = "Maximum spawn depth (${config.maxSpawnDepth}) reached. Cannot spawn at depth $childDepth."
            )
        }

        // 2. Active children check (aligned with OpenClaw: activeChildren >= maxChildrenPerAgent → forbidden)
        if (!registry.canSpawn(parentSessionKey, config.maxChildrenPerAgent)) {
            return SpawnSubagentResult(
                status = SpawnStatus.FORBIDDEN,
                note = "Maximum concurrent children (${config.maxChildrenPerAgent}) reached for this session."
            )
        }

        // 3. Generate identifiers
        val runId = UUID.randomUUID().toString()
        val childSessionKey = "agent:main:subagent:$runId"

        // Validate generated session key with routing module
        check(isSubagentSessionKey(childSessionKey)) {
            "Generated session key is not recognized as subagent: $childSessionKey"
        }

        // ACP permission check: classify the spawn action
        val acpApproval = AcpClient.classifyToolApproval("sessions_spawn")
        Log.d(TAG, "ACP approval for spawn: ${acpApproval.value}")

        // 3a. Run SUBAGENT_SPAWNING hook (can deny spawn, aligned with OpenClaw)
        // Label defaults to empty string (aligned with OpenClaw: params.label?.trim() || "")
        // Display label is resolved later via resolveSubagentLabel()
        val label = params.label?.trim() ?: ""
        val spawningResult = hooks.runSpawning(SubagentSpawningEvent(
            childSessionKey = childSessionKey,
            label = label,
            mode = params.mode,
            requesterSessionKey = parentSessionKey,
            threadRequested = params.thread == true,
        ))
        if (spawningResult is SubagentSpawningResult.Error) {
            return SpawnSubagentResult(
                status = SpawnStatus.FORBIDDEN,
                note = "Spawn denied by hook: ${spawningResult.error}"
            )
        }

        // 4. Resolve model
        val model = params.model ?: config.model
            ?: try { configLoader.loadOpenClawConfig().resolveDefaultModel() } catch (_: Exception) { null }

        // 5. Resolve capabilities for child
        val childCapabilities = resolveSubagentCapabilities(childDepth, config.maxSpawnDepth)

        // 6. Build system prompt
        val systemPrompt = SubagentPromptBuilder.build(
            task = params.task,
            label = label,
            capabilities = childCapabilities,
            parentSessionKey = parentSessionKey,
            childSessionKey = childSessionKey,
        )

        // 7. Create child AgentLoop
        val childContextManager = ContextManager(llmProvider)
        val childLoop = AgentLoop(
            llmProvider = llmProvider,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry,
            contextManager = childContextManager,
            modelRef = model,
            configLoader = configLoader,
        )

        // 8. If child can spawn (ORCHESTRATOR), inject subagent tools
        if (childCapabilities.canSpawn) {
            val childSubagentTools = buildSubagentTools(
                spawner = this,
                parentSessionKey = childSessionKey,
                parentAgentLoop = childLoop,
                parentDepth = childDepth,
                configLoader = configLoader,
            )
            childLoop.extraTools = childSubagentTools
        }

        // 9. Create run record (with new fields: controllerSessionKey, requesterDisplayKey, sessionStartedAt)
        val timeoutSeconds = params.runTimeoutSeconds ?: config.defaultTimeoutSeconds
        val record = SubagentRunRecord(
            runId = runId,
            childSessionKey = childSessionKey,
            controllerSessionKey = parentSessionKey,
            requesterSessionKey = parentSessionKey,
            requesterDisplayKey = parentSessionKey,
            task = params.task,
            label = label,
            model = model,
            cleanup = params.cleanup,
            spawnMode = params.mode,
            workspaceDir = params.cwd,
            createdAt = System.currentTimeMillis(),
            runTimeoutSeconds = timeoutSeconds,
            depth = childDepth,
        ).apply {
            sessionStartedAt = System.currentTimeMillis()
            // Aligned with OpenClaw: defaults to true unless explicitly set to false
            expectsCompletionMessage = params.expectsCompletionMessage != false
        }

        // 10. Timeout
        val timeoutMs = if (timeoutSeconds > 0) timeoutSeconds * 1000L else 0L

        // 11. Launch child coroutine
        val job = scope.launch {
            record.startedAt = System.currentTimeMillis()
            Log.i(TAG, "Subagent started: $runId label=$label model=$model timeout=${timeoutSeconds}s")

            try {
                val result = if (timeoutMs > 0) {
                    withTimeoutOrNull(timeoutMs) {
                        childLoop.run(
                            systemPrompt = systemPrompt,
                            userMessage = params.task,
                            reasoningEnabled = true,
                        )
                    }
                } else {
                    childLoop.run(
                        systemPrompt = systemPrompt,
                        userMessage = params.task,
                        reasoningEnabled = true,
                    )
                }

                if (result == null) {
                    // Timeout
                    Log.w(TAG, "Subagent timed out: $runId after ${timeoutSeconds}s")
                    childLoop.stop()
                    val outcome = SubagentRunOutcome(SubagentRunStatus.TIMEOUT, "Timed out after ${timeoutSeconds}s")
                    registry.markCompleted(runId, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR, null)
                    emitSubagentEndedHookOnce(record, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR)
                    announceToParent(parentAgentLoop, record, outcome)
                } else {
                    // Success
                    val outcome = SubagentRunOutcome(SubagentRunStatus.OK)
                    val frozenText = capFrozenResultText(result.finalContent)
                    record.frozenResultText = frozenText
                    registry.markCompleted(runId, outcome, SubagentLifecycleEndedReason.SUBAGENT_COMPLETE, frozenText)
                    emitSubagentEndedHookOnce(record, outcome, SubagentLifecycleEndedReason.SUBAGENT_COMPLETE)
                    announceToParent(parentAgentLoop, record, outcome)
                    Log.i(TAG, "Subagent completed: $runId iterations=${result.iterations} tools=${result.toolsUsed.size}")
                }

                // Check if any ancestor needs waking after this completion
                checkDescendantSettle(record, parentAgentLoop)

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Killed by parent or steer restart
                Log.i(TAG, "Subagent cancelled: $runId")
                val outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "Killed by parent")
                registry.markCompleted(runId, outcome, SubagentLifecycleEndedReason.SUBAGENT_KILLED, null)
                emitSubagentEndedHookOnce(record, outcome, SubagentLifecycleEndedReason.SUBAGENT_KILLED)
                announceToParent(parentAgentLoop, record, outcome)
            } catch (e: Exception) {
                // Check for transient errors — delay before marking terminal
                if (isTransientError(e)) {
                    Log.w(TAG, "Subagent transient error: $runId, waiting ${LIFECYCLE_ERROR_RETRY_GRACE_MS}ms before marking terminal")
                    delay(LIFECYCLE_ERROR_RETRY_GRACE_MS)
                }
                Log.e(TAG, "Subagent error: $runId", e)
                val outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, e.message ?: "Unknown error")
                registry.markCompleted(runId, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR, null)
                emitSubagentEndedHookOnce(record, outcome, SubagentLifecycleEndedReason.SUBAGENT_ERROR)
                announceToParent(parentAgentLoop, record, outcome)
            }
        }

        // 12. Register in registry
        registry.registerRun(record, childLoop, job)

        Log.i(TAG, "Subagent spawned: $runId → $childSessionKey depth=$childDepth role=${childCapabilities.role}")

        // Fire SUBAGENT_SPAWNED hook (aligned with OpenClaw)
        scope.launch {
            try {
                hooks.runSpawned(SubagentSpawnedEvent(
                    runId = runId,
                    childSessionKey = childSessionKey,
                    label = label,
                    mode = params.mode,
                    requesterSessionKey = parentSessionKey,
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Error running subagent spawned hook: ${e.message}")
            }
        }

        return SpawnSubagentResult(
            status = SpawnStatus.ACCEPTED,
            childSessionKey = childSessionKey,
            runId = runId,
            mode = params.mode,
            note = if (params.mode == SpawnMode.SESSION) SPAWN_SESSION_ACCEPTED_NOTE else SPAWN_ACCEPTED_NOTE,
            modelApplied = model != null,
        )
    }

    // ==================== Transient Error Detection ====================

    /**
     * Check if an error is transient (may self-resolve).
     * Aligned with OpenClaw lifecycle error grace period.
     */
    private fun isTransientError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("timeout") || msg.contains("rate limit") ||
            msg.contains("429") || msg.contains("503") ||
            msg.contains("unavailable") || msg.contains("econnreset")
    }

    // ==================== Lifecycle Hook ====================

    /**
     * Emit subagent_ended hook exactly once per run.
     * Aligned with OpenClaw emitSubagentEndedHookOnce.
     */
    private fun emitSubagentEndedHookOnce(
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

    // ==================== Announce ====================

    /**
     * Announce subagent completion to parent via steerChannel.
     * Aligned with OpenClaw runSubagentAnnounceFlow:
     * 1. Check suppressed announce (steer-restart, killed)
     * 2. Check pending descendants → defer if > 0
     * 3. Check expectsCompletionMessage
     * 4. Collect child completion findings
     * 5. Retry with exponential backoff
     * 6. Complete parent's yield signal if present
     */
    private suspend fun announceToParent(
        parentAgentLoop: AgentLoop,
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

        // 1. Check pending descendants — if > 0, defer announce
        val pendingDescendants = registry.countPendingDescendantRunsExcludingRun(record.childSessionKey, record.runId)
        if (pendingDescendants > 0) {
            Log.i(TAG, "Deferring announce for ${record.runId}: $pendingDescendants pending descendants")
            record.suppressAnnounceReason = "pending_descendants:$pendingDescendants"
            record.wakeOnDescendantSettle = true
            return
        }

        // 2. Check expectsCompletionMessage — if false, only freeze text
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
    private suspend fun checkDescendantSettle(
        completedRecord: SubagentRunRecord,
        parentAgentLoop: AgentLoop,
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

        // All descendants settled — collect findings and announce
        Log.i(TAG, "All descendants settled for ${parentRun.runId}, triggering deferred announce")
        parentRun.wakeOnDescendantSettle = false
        parentRun.suppressAnnounceReason = null

        val outcome = parentRun.outcome ?: SubagentRunOutcome(SubagentRunStatus.OK)
        announceToParent(parentAgentLoop, parentRun, outcome)
    }

    // ==================== Control Operations ====================

    /**
     * Kill a running subagent, optionally with cascade.
     * Aligned with OpenClaw killControlledSubagentRun + cascadeKillChildren.
     *
     * @return Pair of (success, list of killed runIds)
     */
    fun kill(runId: String, cascade: Boolean = false, callerSessionKey: String? = null): Pair<Boolean, List<String>> {
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
    fun killAdmin(sessionKey: String): Map<String, Any?> {
        val entry = registry.getRunByChildSessionKey(sessionKey)
            ?: return mapOf("found" to false, "killed" to false)

        val (killed, killedIds) = kill(entry.runId, cascade = true, callerSessionKey = null)
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
    suspend fun steer(
        runId: String,
        message: String,
        callerSessionKey: String,
        parentAgentLoop: AgentLoop,
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
            configLoader.loadOpenClawConfig().agents?.defaults?.subagents ?: SubagentsConfig()
        } catch (_: Exception) { SubagentsConfig() }
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

        Log.i(TAG, "Steer complete: $runId → $newRunId")
        return Pair(true, "Steered: run restarted as $newRunId")
    }

    // ==================== Session Reactivation ====================

    /**
     * Reactivate a completed SESSION-mode subagent with a new message.
     * Creates a new run record reusing the same child session key and AgentLoop.
     * Aligned with OpenClaw session reactivation (follow-up messages to completed SESSION-mode subagents).
     *
     * @param childSessionKey The session key of the completed subagent
     * @param message The new message to send
     * @param callerSessionKey The caller requesting reactivation
     * @param parentAgentLoop The parent's AgentLoop for announce
     * @return Pair of (success, message/error)
     */
    suspend fun reactivateSession(
        childSessionKey: String,
        message: String,
        callerSessionKey: String,
        parentAgentLoop: AgentLoop,
    ): Pair<Boolean, String?> {
        // Find the latest completed run for this session key
        val runIds = registry.findRunIdsByChildSessionKey(childSessionKey)
        if (runIds.isEmpty()) {
            return Pair(false, "No runs found for session: $childSessionKey")
        }

        val latestRunId = runIds.last()
        val record = registry.getRunById(latestRunId)
            ?: return Pair(false, "Run not found: $latestRunId")

        if (record.isActive) {
            return Pair(false, "Session is still active, use sessions_send instead")
        }

        if (record.spawnMode != SpawnMode.SESSION) {
            return Pair(false, "Cannot reactivate non-SESSION mode subagent (mode: ${record.spawnMode.wireValue})")
        }

        // Ownership check
        val ownershipError = ensureControllerOwnsRun(callerSessionKey, record)
        if (ownershipError != null) {
            return Pair(false, ownershipError)
        }

        // Get the existing AgentLoop (SESSION mode keeps it alive)
        val childLoop = registry.getAgentLoop(latestRunId)
            ?: return Pair(false, "AgentLoop not found for completed session (may have been cleaned up)")

        // Create new run record reusing session key
        val newRunId = UUID.randomUUID().toString()
        val config = try {
            configLoader.loadOpenClawConfig().agents?.defaults?.subagents ?: SubagentsConfig()
        } catch (_: Exception) { SubagentsConfig() }

        val timeoutSeconds = record.runTimeoutSeconds ?: config.defaultTimeoutSeconds
        val newRecord = SubagentRunRecord(
            runId = newRunId,
            childSessionKey = childSessionKey,
            controllerSessionKey = record.controllerSessionKey,
            requesterSessionKey = record.requesterSessionKey,
            requesterDisplayKey = record.requesterDisplayKey,
            task = message,
            label = record.label,
            model = record.model,
            cleanup = record.cleanup,
            spawnMode = SpawnMode.SESSION,
            workspaceDir = record.workspaceDir,
            createdAt = System.currentTimeMillis(),
            runTimeoutSeconds = timeoutSeconds,
            depth = record.depth,
        ).apply {
            sessionStartedAt = record.sessionStartedAt ?: record.startedAt
            accumulatedRuntimeMs = record.accumulatedRuntimeMs + record.runtimeMs
            expectsCompletionMessage = true
        }

        // Build system prompt for continuation
        val childCapabilities = resolveSubagentCapabilities(record.depth, config.maxSpawnDepth)
        val systemPrompt = SubagentPromptBuilder.build(
            task = message,
            label = record.label,
            capabilities = childCapabilities,
            parentSessionKey = record.requesterSessionKey,
            childSessionKey = childSessionKey,
        )

        val timeoutMs = if (timeoutSeconds > 0) timeoutSeconds * 1000L else 0L
        val previousMessages = childLoop.conversationMessages.toList()

        // Launch new run
        val newJob = scope.launch {
            newRecord.startedAt = System.currentTimeMillis()
            Log.i(TAG, "Session reactivated: $newRunId (session=$childSessionKey)")

            try {
                val result = if (timeoutMs > 0) {
                    withTimeoutOrNull(timeoutMs) {
                        childLoop.run(
                            systemPrompt = systemPrompt,
                            userMessage = message,
                            contextHistory = previousMessages.drop(1),
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

        // Register new run
        registry.registerRun(newRecord, childLoop, newJob)

        Log.i(TAG, "Session reactivated: $childSessionKey → new run $newRunId")
        return Pair(true, "Session reactivated as run $newRunId")
    }
}
