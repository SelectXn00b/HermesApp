package com.xiaomo.androidforclaw.contextengine

/**
 * OpenClaw module: context-engine
 * Source: OpenClaw/src/context-engine/types.ts
 *
 * Full type definitions aligned 1:1 with the TypeScript source.
 * AgentMessage is represented as Map<String, Any?> since pi-agent-core types
 * may not be available.
 */

// ── Result types ──

data class AssembleResult(
    val messages: List<Map<String, Any?>>,
    val estimatedTokens: Int,
    val systemPromptAddition: String? = null
)

data class CompactResultDetail(
    val summary: String? = null,
    val firstKeptEntryId: String? = null,
    val tokensBefore: Int,
    val tokensAfter: Int? = null
)

data class CompactResult(
    val ok: Boolean,
    val compacted: Boolean,
    val reason: String? = null,
    val result: CompactResultDetail? = null
)

data class IngestResult(val ingested: Boolean)

data class IngestBatchResult(val ingestedCount: Int)

data class BootstrapResult(
    val bootstrapped: Boolean,
    val importedMessages: Int? = null,
    val reason: String? = null
)

// ── Engine metadata ──

data class ContextEngineInfo(
    val id: String,
    val name: String,
    val version: String? = null,
    val ownsCompaction: Boolean? = null
)

// ── Subagent types ──

enum class SubagentEndReason {
    DELETED,
    COMPLETED,
    SWEPT,
    RELEASED
}

data class SubagentSpawnPreparation(
    val parentSessionKey: String,
    val childSessionKey: String,
    val ttlMs: Long? = null
)

// ── Transcript rewrite types ──

data class TranscriptRewriteReplacement(
    val entryId: String,
    val newContent: Map<String, Any?>? = null,
    val delete: Boolean? = null
)

data class TranscriptRewriteRequest(
    val sessionId: String,
    val replacements: List<TranscriptRewriteReplacement>
)

data class TranscriptRewriteResult(
    val applied: Int,
    val skipped: Int,
    val errors: List<String>? = null
)

/**
 * ContextEngineMaintenanceResult — same shape as TranscriptRewriteResult
 * in the TS source this is a type alias.
 */
typealias ContextEngineMaintenanceResult = TranscriptRewriteResult

// ── Runtime context ──

/**
 * ContextEngineRuntimeContext — runtime hooks the host passes into the engine.
 * In TS this carries `rewriteTranscriptEntries` callback; in Kotlin we model it
 * as a data class with a suspend function property.
 */
data class ContextEngineRuntimeContext(
    val rewriteTranscriptEntries: (suspend (List<TranscriptRewriteReplacement>) -> TranscriptRewriteResult)? = null
)

// ── ContextEngine interface ──

/**
 * Full ContextEngine interface aligned with OpenClaw/src/context-engine/types.ts.
 *
 * All message parameters use `Map<String, Any?>` as the AgentMessage representation.
 */
interface ContextEngine {
    val info: ContextEngineInfo

    /**
     * Called once at session start to bootstrap the engine with prior state.
     */
    suspend fun bootstrap(
        sessionId: String,
        sessionKey: String? = null,
        sessionFile: String? = null
    ): BootstrapResult

    /**
     * Called periodically for maintenance tasks (e.g. transcript rewrites).
     */
    suspend fun maintain(
        sessionId: String,
        sessionKey: String? = null,
        sessionFile: String? = null,
        runtimeContext: ContextEngineRuntimeContext? = null
    )

    /**
     * Ingest a single message into the engine's state.
     */
    suspend fun ingest(
        sessionId: String,
        sessionKey: String? = null,
        message: Map<String, Any?>,
        isHeartbeat: Boolean? = null
    ): IngestResult

    /**
     * Ingest a batch of messages.
     */
    suspend fun ingestBatch(
        sessionId: String,
        sessionKey: String? = null,
        messages: List<Map<String, Any?>>,
        isHeartbeat: Boolean? = null
    ): IngestBatchResult

    /**
     * Called after each turn completes.
     */
    suspend fun afterTurn(
        sessionId: String,
        sessionKey: String? = null,
        sessionFile: String? = null,
        messages: List<Map<String, Any?>>? = null,
        prePromptMessageCount: Int? = null,
        autoCompactionSummary: String? = null,
        isHeartbeat: Boolean? = null,
        tokenBudget: Int? = null,
        runtimeContext: ContextEngineRuntimeContext? = null
    )

    /**
     * Assemble messages for the next model call.
     */
    suspend fun assemble(
        sessionId: String,
        sessionKey: String? = null,
        messages: List<Map<String, Any?>>? = null,
        tokenBudget: Int? = null,
        model: String? = null,
        prompt: String? = null
    ): AssembleResult

    /**
     * Compact / summarize the conversation history.
     */
    suspend fun compact(
        sessionId: String,
        sessionKey: String? = null,
        sessionFile: String? = null,
        tokenBudget: Int? = null,
        force: Boolean? = null,
        currentTokenCount: Int? = null,
        compactionTarget: Int? = null,
        customInstructions: String? = null,
        runtimeContext: ContextEngineRuntimeContext? = null
    ): CompactResult

    /**
     * Prepare state for a subagent spawn.
     */
    suspend fun prepareSubagentSpawn(
        parentSessionKey: String,
        childSessionKey: String,
        ttlMs: Long? = null
    ): SubagentSpawnPreparation

    /**
     * Notify the engine that a subagent has ended.
     */
    suspend fun onSubagentEnded(
        childSessionKey: String,
        reason: SubagentEndReason
    )

    /**
     * Release resources held by the engine.
     */
    suspend fun dispose()
}
