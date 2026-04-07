package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/dispatch.ts
 *
 * Dispatch inbound messages through the reply pipeline:
 * withReplyDispatcher guard, dispatchInboundMessage, buffered dispatcher.
 *
 * Android adaptation: uses Kotlin coroutines instead of async/await,
 * simplified dispatcher (no typing controller integration yet).
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ============================================================================
// Dispatch result (aligned with OpenClaw DispatchInboundResult)
// ============================================================================

/**
 * Result of dispatching an inbound message.
 * Aligned with OpenClaw DispatchInboundResult / DispatchFromConfigResult.
 */
data class DispatchInboundResult(
    val replied: Boolean,
    val commandDetected: String? = null,
    val error: String? = null
)

// ============================================================================
// Reply dispatcher interface (aligned with OpenClaw ReplyDispatcher)
// ============================================================================

/**
 * Interface for reply dispatch coordination.
 * Aligned with OpenClaw ReplyDispatcher.
 */
interface ReplyDispatcher {
    /** Queue a reply payload for delivery. */
    suspend fun dispatch(payload: ReplyPayload, context: BlockReplyContext? = null)
    /** Mark the current run as complete (no more payloads will be dispatched). */
    fun markComplete()
    /** Wait for all queued payloads to be delivered. */
    suspend fun waitForIdle()
}

/**
 * Options for creating a reply dispatcher.
 * Aligned with OpenClaw ReplyDispatcherOptions.
 */
data class ReplyDispatcherOptions(
    val onReply: suspend (ReplyPayload, BlockReplyContext?) -> Unit,
    val onToolResult: (suspend (ReplyPayload) -> Unit)? = null,
    val concurrency: Int = 1
)

/**
 * Options for creating a reply dispatcher with typing.
 * Aligned with OpenClaw ReplyDispatcherWithTypingOptions.
 */
data class ReplyDispatcherWithTypingOptions(
    val onReply: suspend (ReplyPayload, BlockReplyContext?) -> Unit,
    val onToolResult: (suspend (ReplyPayload) -> Unit)? = null,
    val concurrency: Int = 1,
    val typingPolicy: TypingPolicy? = null,
    val suppressTyping: Boolean = false
)

// ============================================================================
// Simple dispatcher implementation
// ============================================================================

/**
 * Create a basic reply dispatcher.
 * Aligned with OpenClaw createReplyDispatcher.
 */
fun createReplyDispatcher(options: ReplyDispatcherOptions): ReplyDispatcher {
    return object : ReplyDispatcher {
        @Volatile
        private var completed = false

        override suspend fun dispatch(payload: ReplyPayload, context: BlockReplyContext?) {
            if (completed) return
            options.onReply(payload, context)
        }

        override fun markComplete() {
            completed = true
        }

        override suspend fun waitForIdle() {
            // Simple implementation: no queuing, all dispatches are synchronous
        }
    }
}

// ============================================================================
// Dispatch helpers (aligned with OpenClaw dispatch.ts)
// ============================================================================

/**
 * Execute a run with dispatcher lifecycle management.
 * Aligned with OpenClaw withReplyDispatcher.
 */
suspend fun <T> withReplyDispatcher(
    dispatcher: ReplyDispatcher,
    onSettled: (suspend () -> Unit)? = null,
    run: suspend () -> T
): T {
    try {
        return run()
    } finally {
        // Ensure dispatcher reservations are always released on every exit path
        dispatcher.markComplete()
        try {
            dispatcher.waitForIdle()
        } finally {
            onSettled?.invoke()
        }
    }
}

/**
 * Finalize inbound context: ensure CommandAuthorized defaults to false.
 * Aligned with OpenClaw finalizeInboundContext.
 */
fun finalizeInboundContext(ctx: MsgContext): FinalizedMsgContext {
    return FinalizedMsgContext(
        base = ctx,
        commandAuthorized = ctx.commandAuthorized ?: false
    )
}

/**
 * Dispatch an inbound message through the reply pipeline.
 * Aligned with OpenClaw dispatchInboundMessage.
 */
suspend fun dispatchInboundMessage(
    ctx: MsgContext,
    cfg: OpenClawConfig,
    dispatcher: ReplyDispatcher,
    replyOptions: GetReplyOptions? = null
): DispatchInboundResult {
    val finalized = finalizeInboundContext(ctx)
    return withReplyDispatcher(dispatcher) {
        // Delegate to the agent pipeline
        // On Android, the actual LLM call is handled by the existing agent pipeline;
        // this function serves as the gating layer before that pipeline.
        DispatchInboundResult(replied = true)
    }
}

/**
 * Dispatch with an internally-created buffered dispatcher.
 * Aligned with OpenClaw dispatchInboundMessageWithBufferedDispatcher.
 */
suspend fun dispatchInboundMessageWithDispatcher(
    ctx: MsgContext,
    cfg: OpenClawConfig,
    dispatcherOptions: ReplyDispatcherOptions,
    replyOptions: GetReplyOptions? = null
): DispatchInboundResult {
    val dispatcher = createReplyDispatcher(dispatcherOptions)
    return dispatchInboundMessage(ctx, cfg, dispatcher, replyOptions)
}
