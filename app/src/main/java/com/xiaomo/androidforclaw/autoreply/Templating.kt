package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/templating.ts
 *
 * Message context types and {{Placeholder}} template interpolation.
 *
 * MsgContext holds all inbound message metadata used by the auto-reply
 * pipeline: body, sender, media, session, routing, and threading fields.
 */

import com.xiaomo.androidforclaw.commands.CommandArgs

// ============================================================================
// Sticker context (aligned with OpenClaw StickerContextMetadata)
// ============================================================================

/**
 * Sticker metadata for Telegram/provider stickers.
 * Aligned with OpenClaw StickerContextMetadata.
 */
data class StickerContextMetadata(
    val cachedDescription: String? = null,
    val emoji: String? = null,
    val setName: String? = null,
    val description: String? = null,
    val fileId: String? = null,
    val fileUniqueId: String? = null,
    val uniqueFileId: String? = null,
    val isAnimated: Boolean? = null,
    val isVideo: Boolean? = null
)

// ============================================================================
// MsgContext (aligned with OpenClaw MsgContext)
// ============================================================================

/**
 * Inbound message context for the auto-reply pipeline.
 * Aligned with OpenClaw MsgContext.
 */
data class MsgContext(
    val body: String? = null,
    /** Agent prompt body (may include envelope/history/context). */
    val bodyForAgent: String? = null,
    /** Recent chat history for context (untrusted user content). */
    val inboundHistory: List<InboundHistoryEntry>? = null,
    /** Raw message body without structural context. */
    val rawBody: String? = null,
    /** Prefer for command detection. */
    val commandBody: String? = null,
    /** Command parsing body (clean text, no history/sender context). */
    val bodyForCommands: String? = null,
    val commandArgs: CommandArgs? = null,
    val from: String? = null,
    val to: String? = null,
    val sessionKey: String? = null,
    /** Provider account id (multi-account). */
    val accountId: String? = null,
    val parentSessionKey: String? = null,
    val messageSid: String? = null,
    /** Provider-specific full message id. */
    val messageSidFull: String? = null,
    val messageSids: List<String>? = null,
    val messageSidFirst: String? = null,
    val messageSidLast: String? = null,
    /** Per-turn reply-threading overrides. */
    val replyThreading: ReplyThreadingPolicy? = null,
    val replyToId: String? = null,
    /** Root message id for thread reconstruction. */
    val rootMessageId: String? = null,
    /** Provider-specific full reply-to id. */
    val replyToIdFull: String? = null,
    val replyToBody: String? = null,
    val replyToSender: String? = null,
    val replyToIsQuote: Boolean? = null,
    /** Forward origin from the reply target. */
    val replyToForwardedFrom: String? = null,
    val replyToForwardedFromType: String? = null,
    val replyToForwardedFromId: String? = null,
    val replyToForwardedFromUsername: String? = null,
    val replyToForwardedFromTitle: String? = null,
    val replyToForwardedDate: Long? = null,
    val forwardedFrom: String? = null,
    val forwardedFromType: String? = null,
    val forwardedFromId: String? = null,
    val forwardedFromUsername: String? = null,
    val forwardedFromTitle: String? = null,
    val forwardedFromSignature: String? = null,
    val forwardedFromChatType: String? = null,
    val forwardedFromMessageId: Int? = null,
    val forwardedDate: Long? = null,
    val threadStarterBody: String? = null,
    /** Full thread history when starting a new thread session. */
    val threadHistoryBody: String? = null,
    val isFirstThreadTurn: Boolean? = null,
    val threadLabel: String? = null,
    val mediaPath: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaDir: String? = null,
    val mediaPaths: List<String>? = null,
    val mediaUrls: List<String>? = null,
    val mediaTypes: List<String>? = null,
    /** Sticker metadata. */
    val sticker: StickerContextMetadata? = null,
    /** True when current-turn sticker media is present in MediaPaths. */
    val stickerMediaIncluded: Boolean? = null,
    val outputDir: String? = null,
    val outputBase: String? = null,
    /** Remote host for SCP. */
    val mediaRemoteHost: String? = null,
    val transcript: String? = null,
    val linkUnderstanding: List<String>? = null,
    val prompt: String? = null,
    val maxChars: Int? = null,
    val chatType: String? = null,
    /** Human label for envelope headers (conversation label). */
    val conversationLabel: String? = null,
    val groupSubject: String? = null,
    /** Human label for channel-like group conversations. */
    val groupChannel: String? = null,
    val groupSpace: String? = null,
    val groupMembers: String? = null,
    val groupSystemPrompt: String? = null,
    /** Untrusted metadata. */
    val untrustedContext: List<String>? = null,
    /** Explicit owner allowlist overrides. */
    val ownerAllowFrom: List<String>? = null,
    val senderName: String? = null,
    val senderId: String? = null,
    val senderUsername: String? = null,
    val senderTag: String? = null,
    val senderE164: String? = null,
    val timestamp: Long? = null,
    /** Provider label (e.g. whatsapp, telegram). */
    val provider: String? = null,
    /** Provider surface label. */
    val surface: String? = null,
    /** Platform bot username. */
    val botUsername: String? = null,
    val wasMentioned: Boolean? = null,
    val commandAuthorized: Boolean? = null,
    val commandSource: String? = null,  // "text" | "native"
    val commandTargetSessionKey: String? = null,
    /** Gateway client scopes. */
    val gatewayClientScopes: List<String>? = null,
    /** Trusted system override. */
    val forceSenderIsOwnerFalse: Boolean? = null,
    /** Thread identifier. */
    val messageThreadId: String? = null,
    /** Platform-native channel/conversation id. */
    val nativeChannelId: String? = null,
    /** Stable provider-native direct-peer id. */
    val nativeDirectUserId: String? = null,
    /** Telegram forum supergroup marker. */
    val isForum: Boolean? = null,
    /** Warning: DM has topics enabled but this message is not in a topic. */
    val topicRequiredButMissing: Boolean? = null,
    /** Originating channel for reply routing. */
    val originatingChannel: String? = null,
    /** Originating destination for reply routing. */
    val originatingTo: String? = null,
    /** True when intentionally requested external delivery. */
    val explicitDeliverRoute: Boolean? = null,
    /** Provider-specific parent conversation id for threaded contexts. */
    val threadParentId: String? = null,
    /** Messages from hooks to be included in the response. */
    val hookMessages: List<String>? = null
)

data class InboundHistoryEntry(
    val sender: String,
    val body: String,
    val timestamp: Long? = null
)

// ============================================================================
// FinalizedMsgContext (aligned with OpenClaw FinalizedMsgContext)
// ============================================================================

/**
 * MsgContext with CommandAuthorized guaranteed set (default-deny: false).
 * Aligned with OpenClaw FinalizedMsgContext.
 */
data class FinalizedMsgContext(
    val base: MsgContext,
    /** Always set by finalizeInboundContext(). Default-deny: false. */
    val commandAuthorized: Boolean
) {
    val body get() = base.body
    val bodyForAgent get() = base.bodyForAgent
    val rawBody get() = base.rawBody
    val commandBody get() = base.commandBody
    val bodyForCommands get() = base.bodyForCommands
    val commandArgs get() = base.commandArgs
    val from get() = base.from
    val to get() = base.to
    val sessionKey get() = base.sessionKey
    val provider get() = base.provider
    val surface get() = base.surface
    val messageSid get() = base.messageSid
    val originatingChannel get() = base.originatingChannel
    val originatingTo get() = base.originatingTo
}

// ============================================================================
// TemplateContext (aligned with OpenClaw TemplateContext)
// ============================================================================

/**
 * Extended context for template interpolation.
 * Aligned with OpenClaw TemplateContext.
 */
data class TemplateContext(
    val msg: MsgContext,
    val bodyStripped: String? = null,
    val sessionId: String? = null,
    val isNewSession: String? = null
)

// ============================================================================
// Template interpolation (aligned with OpenClaw applyTemplate)
// ============================================================================

/**
 * Format a template value to string.
 * Aligned with OpenClaw formatTemplateValue.
 */
private fun formatTemplateValue(value: Any?): String {
    if (value == null) return ""
    return when (value) {
        is String -> value
        is Number, is Boolean -> value.toString()
        is List<*> -> value.filterNotNull().mapNotNull { entry ->
            when (entry) {
                is String -> entry
                is Number, is Boolean -> entry.toString()
                else -> null
            }
        }.joinToString(",")
        else -> ""
    }
}

/**
 * Simple {{Placeholder}} interpolation using inbound message context.
 * Aligned with OpenClaw applyTemplate.
 */
fun applyTemplate(str: String?, ctx: TemplateContext): String {
    if (str.isNullOrEmpty()) return ""

    // Build a map of all available template values
    val values = buildMap<String, Any?> {
        put("Body", ctx.msg.body)
        put("BodyForAgent", ctx.msg.bodyForAgent)
        put("RawBody", ctx.msg.rawBody)
        put("CommandBody", ctx.msg.commandBody)
        put("BodyForCommands", ctx.msg.bodyForCommands)
        put("From", ctx.msg.from)
        put("To", ctx.msg.to)
        put("SessionKey", ctx.msg.sessionKey)
        put("AccountId", ctx.msg.accountId)
        put("ParentSessionKey", ctx.msg.parentSessionKey)
        put("MessageSid", ctx.msg.messageSid)
        put("MessageSidFull", ctx.msg.messageSidFull)
        put("ReplyToId", ctx.msg.replyToId)
        put("ReplyToBody", ctx.msg.replyToBody)
        put("ReplyToSender", ctx.msg.replyToSender)
        put("ForwardedFrom", ctx.msg.forwardedFrom)
        put("ThreadStarterBody", ctx.msg.threadStarterBody)
        put("ThreadHistoryBody", ctx.msg.threadHistoryBody)
        put("ThreadLabel", ctx.msg.threadLabel)
        put("MediaPath", ctx.msg.mediaPath)
        put("MediaUrl", ctx.msg.mediaUrl)
        put("MediaType", ctx.msg.mediaType)
        put("MediaDir", ctx.msg.mediaDir)
        put("OutputDir", ctx.msg.outputDir)
        put("OutputBase", ctx.msg.outputBase)
        put("Transcript", ctx.msg.transcript)
        put("Prompt", ctx.msg.prompt)
        put("MaxChars", ctx.msg.maxChars)
        put("ChatType", ctx.msg.chatType)
        put("ConversationLabel", ctx.msg.conversationLabel)
        put("GroupSubject", ctx.msg.groupSubject)
        put("GroupChannel", ctx.msg.groupChannel)
        put("GroupSpace", ctx.msg.groupSpace)
        put("GroupMembers", ctx.msg.groupMembers)
        put("GroupSystemPrompt", ctx.msg.groupSystemPrompt)
        put("SenderName", ctx.msg.senderName)
        put("SenderId", ctx.msg.senderId)
        put("SenderUsername", ctx.msg.senderUsername)
        put("SenderTag", ctx.msg.senderTag)
        put("SenderE164", ctx.msg.senderE164)
        put("Timestamp", ctx.msg.timestamp)
        put("Provider", ctx.msg.provider)
        put("Surface", ctx.msg.surface)
        put("BotUsername", ctx.msg.botUsername)
        put("WasMentioned", ctx.msg.wasMentioned)
        put("CommandAuthorized", ctx.msg.commandAuthorized)
        put("MessageThreadId", ctx.msg.messageThreadId)
        put("NativeChannelId", ctx.msg.nativeChannelId)
        put("OriginatingChannel", ctx.msg.originatingChannel)
        put("OriginatingTo", ctx.msg.originatingTo)
        // TemplateContext extras
        put("BodyStripped", ctx.bodyStripped)
        put("SessionId", ctx.sessionId)
        put("IsNewSession", ctx.isNewSession)
    }

    return str.replace(Regex("\\{\\{\\s*(\\w+)\\s*}}")) { match ->
        val key = match.groupValues[1]
        formatTemplateValue(values[key])
    }
}
