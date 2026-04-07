package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/types.ts
 *
 * Core type definitions for the auto-reply system:
 * reply payloads, block contexts, typing policies, model callbacks.
 */

import com.xiaomo.androidforclaw.interactive.InteractiveReply

// ============================================================================
// Enums (aligned with OpenClaw types.ts)
// ============================================================================

/**
 * Typing indicator policy for different run classes.
 * Aligned with OpenClaw TypingPolicy.
 */
enum class TypingPolicy(val value: String) {
    AUTO("auto"),
    USER_MESSAGE("user_message"),
    SYSTEM_EVENT("system_event"),
    INTERNAL_WEBCHAT("internal_webchat"),
    HEARTBEAT("heartbeat");

    companion object {
        fun fromString(value: String?): TypingPolicy? =
            entries.find { it.value == value?.lowercase() }
    }
}

// ============================================================================
// Context types (aligned with OpenClaw types.ts)
// ============================================================================

/**
 * Context passed to block reply callbacks.
 * Aligned with OpenClaw BlockReplyContext.
 */
data class BlockReplyContext(
    val timeoutMs: Int? = null,
    /** Source assistant message index from the upstream stream, when available. */
    val assistantMessageIndex: Int? = null
)

/**
 * Context passed to onModelSelected callback with actual model used.
 * Aligned with OpenClaw ModelSelectedContext.
 */
data class ModelSelectedContext(
    val provider: String,
    val model: String,
    val thinkLevel: String? = null
)

/**
 * Per-turn reply-threading overrides.
 * Aligned with OpenClaw ReplyThreadingPolicy.
 */
data class ReplyThreadingPolicy(
    /** Override implicit reply-to-current behavior for the current turn. */
    val implicitCurrentMessage: String? = null  // "default" | "allow" | "deny"
)

// ============================================================================
// Reply payload (aligned with OpenClaw ReplyPayload)
// ============================================================================

/**
 * Payload describing a single reply message (text, media, interactive, etc.).
 * Aligned with OpenClaw ReplyPayload.
 */
data class ReplyPayload(
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaUrls: List<String>? = null,
    val interactive: InteractiveReply? = null,
    val btw: BtwPayload? = null,
    val replyToId: String? = null,
    val replyToTag: Boolean? = null,
    /** True when [[reply_to_current]] was present but not yet mapped to a message id. */
    val replyToCurrent: Boolean? = null,
    /** Send audio as voice message (bubble) instead of audio file. */
    val audioAsVoice: Boolean? = null,
    val isError: Boolean? = null,
    /** Marks this payload as a reasoning/thinking block. */
    val isReasoning: Boolean? = null,
    /** Marks this payload as a compaction status notice. */
    val isCompactionNotice: Boolean? = null,
    /** Channel-specific payload data (per-channel envelope). */
    val channelData: Map<String, Any?>? = null
)

data class BtwPayload(
    val question: String
)

/**
 * Metadata associated with a reply payload.
 * Aligned with OpenClaw ReplyPayloadMetadata.
 */
data class ReplyPayloadMetadata(
    val assistantMessageIndex: Int? = null
)

// ============================================================================
// Reply payload metadata (WeakHashMap-based, aligned with OpenClaw WeakMap approach)
// ============================================================================

private val replyPayloadMetadataMap = java.util.WeakHashMap<Any, ReplyPayloadMetadata>()

fun setReplyPayloadMetadata(payload: Any, metadata: ReplyPayloadMetadata): Any {
    val previous = replyPayloadMetadataMap[payload]
    replyPayloadMetadataMap[payload] = ReplyPayloadMetadata(
        assistantMessageIndex = metadata.assistantMessageIndex ?: previous?.assistantMessageIndex
    )
    return payload
}

fun getReplyPayloadMetadata(payload: Any): ReplyPayloadMetadata? {
    return replyPayloadMetadataMap[payload]
}

// ============================================================================
// GetReplyOptions (aligned with OpenClaw GetReplyOptions)
// ============================================================================

/**
 * Options for the getReply / auto-reply system.
 * Aligned with OpenClaw GetReplyOptions.
 */
data class GetReplyOptions(
    /** Override run id for agent events (defaults to random UUID). */
    val runId: String? = null,
    val isHeartbeat: Boolean? = null,
    /** Policy-level typing control for run classes. */
    val typingPolicy: TypingPolicy? = null,
    /** Force-disable typing indicators for this run. */
    val suppressTyping: Boolean? = null,
    /** Resolved heartbeat model override. */
    val heartbeatModelOverride: String? = null,
    /** Controls bootstrap workspace context injection. */
    val bootstrapContextMode: String? = null,  // "full" | "lightweight"
    /** If true, suppress tool error warning payloads for this run. */
    val suppressToolErrorWarnings: Boolean? = null,
    val disableBlockStreaming: Boolean? = null,
    /** Timeout for block reply delivery (ms). */
    val blockReplyTimeoutMs: Int? = null,
    /** If provided, only load these skills for this session. */
    val skillFilter: List<String>? = null,
    /** Override agent timeout in seconds (0 = no timeout). */
    val timeoutOverrideSeconds: Int? = null,
    // Callbacks
    val onAgentRunStart: ((String) -> Unit)? = null,
    val onReplyStart: (suspend () -> Unit)? = null,
    val onTypingCleanup: (() -> Unit)? = null,
    val onPartialReply: (suspend (ReplyPayload) -> Unit)? = null,
    val onReasoningStream: (suspend (ReplyPayload) -> Unit)? = null,
    val onReasoningEnd: (suspend () -> Unit)? = null,
    val onAssistantMessageStart: (suspend () -> Unit)? = null,
    val onBlockReplyQueued: (suspend (ReplyPayload, BlockReplyContext?) -> Unit)? = null,
    val onBlockReply: (suspend (ReplyPayload, BlockReplyContext?) -> Unit)? = null,
    val onToolResult: (suspend (ReplyPayload) -> Unit)? = null,
    val onToolStart: (suspend (ToolStartPayload) -> Unit)? = null,
    val onItemEvent: (suspend (ItemEventPayload) -> Unit)? = null,
    val onPlanUpdate: (suspend (PlanUpdatePayload) -> Unit)? = null,
    val onApprovalEvent: (suspend (ApprovalEventPayload) -> Unit)? = null,
    val onCommandOutput: (suspend (CommandOutputPayload) -> Unit)? = null,
    val onPatchSummary: (suspend (PatchSummaryPayload) -> Unit)? = null,
    val onCompactionStart: (suspend () -> Unit)? = null,
    val onCompactionEnd: (suspend () -> Unit)? = null,
    val onModelSelected: ((ModelSelectedContext) -> Unit)? = null
)

// ============================================================================
// Callback payload types (aligned with OpenClaw GetReplyOptions callback shapes)
// ============================================================================

data class ToolStartPayload(
    val name: String? = null,
    val phase: String? = null
)

data class ItemEventPayload(
    val itemId: String? = null,
    val kind: String? = null,
    val title: String? = null,
    val name: String? = null,
    val phase: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val progressText: String? = null,
    val approvalId: String? = null,
    val approvalSlug: String? = null
)

data class PlanUpdatePayload(
    val phase: String? = null,
    val title: String? = null,
    val explanation: String? = null,
    val steps: List<String>? = null,
    val source: String? = null
)

data class ApprovalEventPayload(
    val phase: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val title: String? = null,
    val itemId: String? = null,
    val toolCallId: String? = null,
    val approvalId: String? = null,
    val approvalSlug: String? = null,
    val command: String? = null,
    val host: String? = null,
    val reason: String? = null,
    val message: String? = null
)

data class CommandOutputPayload(
    val itemId: String? = null,
    val phase: String? = null,
    val title: String? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val output: String? = null,
    val status: String? = null,
    val exitCode: Int? = null,
    val durationMs: Int? = null,
    val cwd: String? = null
)

data class PatchSummaryPayload(
    val itemId: String? = null,
    val phase: String? = null,
    val title: String? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val added: List<String>? = null,
    val modified: List<String>? = null,
    val deleted: List<String>? = null,
    val summary: String? = null
)
