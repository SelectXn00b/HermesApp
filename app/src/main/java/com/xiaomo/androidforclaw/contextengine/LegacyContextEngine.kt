package com.xiaomo.androidforclaw.contextengine

/**
 * OpenClaw module: context-engine
 * Source: OpenClaw/src/context-engine/legacy.ts
 *
 * The legacy (pass-through) context engine. It does minimal work:
 * - ingest / afterTurn are no-ops
 * - assemble returns the messages as-is
 * - compact delegates to the runtime (stubbed for now)
 */
class LegacyContextEngine : ContextEngine {

    override val info: ContextEngineInfo = ContextEngineInfo(
        id = "legacy",
        name = "Legacy Context Engine",
        version = "1.0.0"
    )

    // ── bootstrap ──

    override suspend fun bootstrap(
        sessionId: String,
        sessionKey: String?,
        sessionFile: String?
    ): BootstrapResult {
        return BootstrapResult(bootstrapped = false, reason = "legacy-noop")
    }

    // ── maintain ──

    override suspend fun maintain(
        sessionId: String,
        sessionKey: String?,
        sessionFile: String?,
        runtimeContext: ContextEngineRuntimeContext?
    ) {
        // no-op
    }

    // ── ingest ──

    override suspend fun ingest(
        sessionId: String,
        sessionKey: String?,
        message: Map<String, Any?>,
        isHeartbeat: Boolean?
    ): IngestResult {
        return IngestResult(ingested = false)
    }

    // ── ingestBatch ──

    override suspend fun ingestBatch(
        sessionId: String,
        sessionKey: String?,
        messages: List<Map<String, Any?>>,
        isHeartbeat: Boolean?
    ): IngestBatchResult {
        return IngestBatchResult(ingestedCount = 0)
    }

    // ── afterTurn ──

    override suspend fun afterTurn(
        sessionId: String,
        sessionKey: String?,
        sessionFile: String?,
        messages: List<Map<String, Any?>>?,
        prePromptMessageCount: Int?,
        autoCompactionSummary: String?,
        isHeartbeat: Boolean?,
        tokenBudget: Int?,
        runtimeContext: ContextEngineRuntimeContext?
    ) {
        // no-op
    }

    // ── assemble ──

    override suspend fun assemble(
        sessionId: String,
        sessionKey: String?,
        messages: List<Map<String, Any?>>?,
        tokenBudget: Int?,
        model: String?,
        prompt: String?
    ): AssembleResult {
        // Pass-through: return messages as-is with zero estimated tokens.
        return AssembleResult(
            messages = messages ?: emptyList(),
            estimatedTokens = 0
        )
    }

    // ── compact ──

    override suspend fun compact(
        sessionId: String,
        sessionKey: String?,
        sessionFile: String?,
        tokenBudget: Int?,
        force: Boolean?,
        currentTokenCount: Int?,
        compactionTarget: Int?,
        customInstructions: String?,
        runtimeContext: ContextEngineRuntimeContext?
    ): CompactResult {
        // Legacy engine delegates compaction to the runtime.
        // Stubbed as not-implemented until runtime integration is wired.
        return CompactResult(
            ok = true,
            compacted = false,
            reason = "not-implemented"
        )
    }

    // ── subagent lifecycle ──

    override suspend fun prepareSubagentSpawn(
        parentSessionKey: String,
        childSessionKey: String,
        ttlMs: Long?
    ): SubagentSpawnPreparation {
        return SubagentSpawnPreparation(
            parentSessionKey = parentSessionKey,
            childSessionKey = childSessionKey,
            ttlMs = ttlMs
        )
    }

    override suspend fun onSubagentEnded(
        childSessionKey: String,
        reason: SubagentEndReason
    ) {
        // no-op
    }

    // ── dispose ──

    override suspend fun dispose() {
        // no-op
    }
}

/**
 * Registers the legacy context engine with "core" ownership.
 * Called from [ensureContextEnginesInitialized].
 */
fun registerLegacyContextEngine() {
    val result = registerContextEngineForOwner(
        id = "legacy",
        factory = { LegacyContextEngine() },
        owner = "core"
    )
    if (result is ContextEngineRegistrationResult.Error) {
        // This should never happen — legacy is registered first.
        error("Failed to register legacy context engine: already owned by '${result.existingOwner}'")
    }
}
