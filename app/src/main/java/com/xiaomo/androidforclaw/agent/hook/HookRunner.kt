package com.xiaomo.androidforclaw.agent.hook

import com.xiaomo.androidforclaw.hooks.HookEntry
import com.xiaomo.androidforclaw.hooks.InternalHookEventType
import com.xiaomo.androidforclaw.hooks.InternalHooks
import com.xiaomo.androidforclaw.hooks.createHookEvent

/**
 * Hook system for agent lifecycle events.
 *
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/compaction-hooks.ts (compaction lifecycle hooks)
 * - ../openclaw/src/agents/pi-embedded-runner/compact.hooks.harness.ts (hook harness)
 * - ../openclaw/src/agents/bootstrap-hooks.ts (bootstrap hooks)
 *
 * Provides registration and execution of lifecycle hooks:
 * - before_compaction: runs before context compaction
 * - after_compaction: runs after context compaction
 * - before_tool_call: runs before each tool execution
 * - after_tool_call: runs after each tool execution
 * - on_error: runs on error conditions
 */

/**
 * Hook event data passed to hook handlers.
 */
data class HookEvent(
    val phase: String,
    val data: Map<String, Any?> = emptyMap()
)

/**
 * Hook context passed to hook handlers.
 * Provides access to session state that hooks can inspect/modify.
 */
data class HookContext(
    val sessionKey: String?,
    val agentId: String?,
    val provider: String,
    val model: String
)

/**
 * Result of a hook execution.
 */
data class HookResult(
    val success: Boolean = true,
    val shouldCancel: Boolean = false,
    val modifiedData: Map<String, Any?>? = null
)

/**
 * Hook handler function type.
 * Returns HookResult; if shouldCancel=true, the caller should abort the operation.
 */
typealias HookHandler = suspend (HookEvent, HookContext) -> HookResult

/**
 * Hook runner — registers and executes lifecycle hooks.
 * Aligned with OpenClaw EmbeddedAgentHookRunner.
 */
class HookRunner {

    private val hooks = mutableMapOf<String, MutableList<HookHandler>>()

    companion object {
        const val BEFORE_COMPACTION = "before_compaction"
        const val AFTER_COMPACTION = "after_compaction"
        const val BEFORE_TOOL_CALL = "before_tool_call"
        const val AFTER_TOOL_CALL = "after_tool_call"
        const val ON_ERROR = "on_error"
    }

    /** Registered HookEntry objects (from the ported hooks module). */
    private val hookEntries = mutableListOf<HookEntry>()

    /**
     * Register a hook handler for a given phase.
     */
    fun on(phase: String, handler: HookHandler) {
        hooks.getOrPut(phase) { mutableListOf() }.add(handler)
    }

    /**
     * Register a [HookEntry] from the ported hooks type system.
     * Hooks matching agent-lifecycle events are mapped to the corresponding phase.
     */
    fun registerEntry(entry: HookEntry) {
        hookEntries.add(entry)
        val events = entry.metadata?.events ?: return
        for (event in events) {
            val phase = when (event) {
                "before_compaction" -> BEFORE_COMPACTION
                "after_compaction" -> AFTER_COMPACTION
                "before_tool_call" -> BEFORE_TOOL_CALL
                "after_tool_call" -> AFTER_TOOL_CALL
                "on_error" -> ON_ERROR
                else -> null
            }
            if (phase != null) {
                android.util.Log.d("HookRunner", "Registered HookEntry '${entry.hook.name}' for phase=$phase")
            }
        }
    }

    /**
     * Get all registered [HookEntry] objects.
     */
    fun getRegisteredEntries(): List<HookEntry> = hookEntries.toList()

    /**
     * Check if any hooks are registered for a phase.
     * Aligned with OpenClaw hookRunner.hasHooks().
     */
    fun hasHooks(phase: String): Boolean {
        return hooks[phase]?.isNotEmpty() == true
    }

    /**
     * Run all hooks for a given phase.
     * Returns the last result, or a default success result if no hooks exist.
     * Aligned with OpenClaw hookRunner.runBeforeCompaction().
     */
    suspend fun run(phase: String, event: HookEvent, context: HookContext): HookResult {
        // Also fire internal hook event for cross-module listeners
        if (InternalHooks.hasListeners(InternalHookEventType.AGENT, phase)) {
            val internalEvent = createHookEvent(
                type = InternalHookEventType.AGENT,
                action = phase,
                sessionKey = context.sessionKey ?: "",
                context = event.data.toMutableMap()
            )
            InternalHooks.trigger(internalEvent)
        }

        val phaseHooks = hooks[phase] ?: return HookResult(success = true)
        var lastResult = HookResult(success = true)

        for (handler in phaseHooks) {
            try {
                val result = handler(event, context)
                lastResult = result
                if (result.shouldCancel) {
                    return result
                }
            } catch (e: Exception) {
                // Log but don't fail — aligned with OpenClaw hook error handling
                android.util.Log.w("HookRunner", "Hook $phase failed: ${e.message}")
                lastResult = HookResult(success = false)
            }
        }

        return lastResult
    }

    /**
     * Run before_compaction hook (convenience method).
     * Aligned with OpenClaw hookRunner.runBeforeCompaction().
     */
    suspend fun runBeforeCompaction(
        data: Map<String, Any?>,
        context: HookContext
    ): HookResult {
        return run(BEFORE_COMPACTION, HookEvent(phase = BEFORE_COMPACTION, data = data), context)
    }

    /**
     * Run after_compaction hook (convenience method).
     */
    suspend fun runAfterCompaction(
        data: Map<String, Any?>,
        context: HookContext
    ): HookResult {
        return run(AFTER_COMPACTION, HookEvent(phase = AFTER_COMPACTION, data = data), context)
    }

    /**
     * Run before_tool_call hook.
     */
    suspend fun runBeforeToolCall(
        toolName: String,
        arguments: String,
        context: HookContext
    ): HookResult {
        return run(BEFORE_TOOL_CALL, HookEvent(
            phase = BEFORE_TOOL_CALL,
            data = mapOf("toolName" to toolName, "arguments" to arguments)
        ), context)
    }

    /**
     * Run after_tool_call hook.
     */
    suspend fun runAfterToolCall(
        toolName: String,
        arguments: String,
        result: String,
        success: Boolean,
        context: HookContext
    ): HookResult {
        return run(AFTER_TOOL_CALL, HookEvent(
            phase = AFTER_TOOL_CALL,
            data = mapOf(
                "toolName" to toolName,
                "arguments" to arguments,
                "result" to result,
                "success" to success
            )
        ), context)
    }
}
