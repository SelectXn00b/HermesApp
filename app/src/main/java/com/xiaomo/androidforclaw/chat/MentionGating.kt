package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/mention-gating.ts
 *
 * Mention gating logic: determines whether an inbound message should be
 * skipped based on mention requirements and bypass rules.
 */

// ---------------------------------------------------------------------------
// Types  (aligned with TS MentionGateParams, MentionGateResult, etc.)
// ---------------------------------------------------------------------------

data class MentionGateParams(
    val requireMention: Boolean,
    val canDetectMention: Boolean,
    val wasMentioned: Boolean,
    val implicitMention: Boolean = false,
    val shouldBypassMention: Boolean = false,
)

data class MentionGateResult(
    val effectiveWasMentioned: Boolean,
    val shouldSkip: Boolean,
)

data class MentionGateWithBypassParams(
    val isGroup: Boolean,
    val requireMention: Boolean,
    val canDetectMention: Boolean,
    val wasMentioned: Boolean,
    val implicitMention: Boolean = false,
    val hasAnyMention: Boolean = false,
    val allowTextCommands: Boolean,
    val hasControlCommand: Boolean,
    val commandAuthorized: Boolean,
)

data class MentionGateWithBypassResult(
    val effectiveWasMentioned: Boolean,
    val shouldSkip: Boolean,
    val shouldBypassMention: Boolean,
)

// ---------------------------------------------------------------------------
// Core mention gating  (aligned with TS resolveMentionGating)
// ---------------------------------------------------------------------------

fun resolveMentionGating(params: MentionGateParams): MentionGateResult {
    val effectiveWasMentioned =
        params.wasMentioned || params.implicitMention || params.shouldBypassMention
    val shouldSkip =
        params.requireMention && params.canDetectMention && !effectiveWasMentioned
    return MentionGateResult(effectiveWasMentioned, shouldSkip)
}

// ---------------------------------------------------------------------------
// Mention gating with command bypass  (aligned with TS resolveMentionGatingWithBypass)
// ---------------------------------------------------------------------------

fun resolveMentionGatingWithBypass(params: MentionGateWithBypassParams): MentionGateWithBypassResult {
    val shouldBypassMention = params.isGroup &&
        params.requireMention &&
        !params.wasMentioned &&
        !params.hasAnyMention &&
        params.allowTextCommands &&
        params.commandAuthorized &&
        params.hasControlCommand

    val baseResult = resolveMentionGating(
        MentionGateParams(
            requireMention = params.requireMention,
            canDetectMention = params.canDetectMention,
            wasMentioned = params.wasMentioned,
            implicitMention = params.implicitMention,
            shouldBypassMention = shouldBypassMention,
        )
    )

    return MentionGateWithBypassResult(
        effectiveWasMentioned = baseResult.effectiveWasMentioned,
        shouldSkip = baseResult.shouldSkip,
        shouldBypassMention = shouldBypassMention,
    )
}
