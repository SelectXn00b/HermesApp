/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-spawn.ts (spawnSubagentDirect)
 *
 * Hermes adaptation: in-process coroutine-based subagent spawning.
 * Replaces OpenClaw's gateway WebSocket communication with direct steerChannel injection.
 *
 * Announce logic: see SubagentAnnounce.kt (subagent-announce.ts)
 * Control logic: see SubagentControl.kt (subagent-control.ts)
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.acp.AcpClient
import com.xiaomo.hermes.agent.context.ContextManager
import com.xiaomo.hermes.agent.loop.AgentLoopInterface
import com.xiaomo.hermes.hermes.bridge.AgentLoopAdapter
import com.xiaomo.hermes.agent.tools.AndroidToolRegistry
import com.xiaomo.hermes.routing.isSubagentSessionKey
import com.xiaomo.hermes.routing.getSubagentDepth
import com.xiaomo.hermes.routing.isAcpSessionKey
import com.xiaomo.hermes.agent.tools.SessionsHistoryTool
import com.xiaomo.hermes.agent.tools.SessionsKillTool
import com.xiaomo.hermes.agent.tools.SessionsListTool
import com.xiaomo.hermes.agent.tools.SessionsSendTool
import com.xiaomo.hermes.agent.tools.SessionsSpawnTool
import com.xiaomo.hermes.agent.tools.SessionStatusTool
import com.xiaomo.hermes.agent.tools.SessionsYieldTool
import com.xiaomo.hermes.agent.tools.SubagentsTool
import com.xiaomo.hermes.agent.tools.Tool
import com.xiaomo.hermes.agent.tools.ToolRegistry
import com.xiaomo.hermes.config.ConfigLoader
import com.xiaomo.hermes.config.SubagentsConfig
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.UnifiedLLMProvider
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
 * Core subagent spawner -- validates, creates, and manages subagent AgentLoop instances.
 * Aligned with OpenClaw spawnSubagentDirect + announce flow + control operations.
 *
 * Android-specific: subagents run as in-process coroutines with direct steerChannel communication
 * (no gateway WebSocket).
 */
class SubagentSpawner(
    val registry: SubagentRegistry,
    internal val configLoader: ConfigLoader,
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
            parentAgentLoop: AgentLoopInterface,
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

    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Rate limit: last steer time per (caller, target) pair. Aligned with OpenClaw steerRateLimit Map. */
    internal val lastSteerTime = ConcurrentHashMap<String, Long>()

    // ==================== Spawn ====================

    /**
     * Spawn a subagent.
     * Aligned with OpenClaw spawnSubagentDirect validation + launch flow.
     */
    suspend fun spawn(
        params: SpawnSubagentParams,
        parentSessionKey: String,
        parentAgentLoop: AgentLoopInterface,
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

        // ACP session key check -- if parent is an ACP session, log it
        if (isAcpSessionKey(parentSessionKey)) {
            Log.d(TAG, "Spawn requested from ACP session: $parentSessionKey")
        }

        // ACP runtime check -- not supported on Android
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

        // 1. Depth check -- use routing.getSubagentDepth() for cross-validation
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

        // 2. Active children check (aligned with OpenClaw: activeChildren >= maxChildrenPerAgent -> forbidden)
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

        // 7. Create child AgentLoop (via AgentLoopAdapter → hermes)
        val childContextManager = ContextManager(llmProvider)
        val childLoop = AgentLoopAdapter(
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
                // Check for transient errors -- delay before marking terminal
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

        Log.i(TAG, "Subagent spawned: $runId -> $childSessionKey depth=$childDepth role=${childCapabilities.role}")

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

    // ==================== Control Delegation ====================
    // These delegate to extension functions in SubagentControl.kt for 1:1 TS alignment.

    /**
     * Kill a running subagent, optionally with cascade.
     * Delegates to SubagentControl.kt killSubagent extension.
     */
    fun kill(runId: String, cascade: Boolean = false, callerSessionKey: String? = null): Pair<Boolean, List<String>> {
        return killSubagent(runId, cascade, callerSessionKey)
    }

    /**
     * Admin kill: kill a subagent by session key without ownership check.
     * Delegates to SubagentControl.kt killSubagentAdmin extension.
     */
    fun killAdmin(sessionKey: String): Map<String, Any?> {
        return killSubagentAdmin(sessionKey)
    }

    /**
     * Steer a running subagent: abort current run and restart with new message.
     * Delegates to SubagentControl.kt steerSubagent extension.
     */
    suspend fun steer(
        runId: String,
        message: String,
        callerSessionKey: String,
        parentAgentLoop: AgentLoopInterface,
    ): Pair<Boolean, String?> {
        return steerSubagent(runId, message, callerSessionKey, parentAgentLoop)
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
        parentAgentLoop: AgentLoopInterface,
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

        Log.i(TAG, "Session reactivated: $childSessionKey -> new run $newRunId")
        return Pair(true, "Session reactivated as run $newRunId")
    }
}
