package com.xiaomo.androidforclaw.agent.loop

/**
 * Channel Capabilities — 对齐 OpenClaw channel-capabilities.ts
 *
 * 定义各消息频道（Telegram/Discord/Feishu 等）的可用操作能力：
 * - 是否支持回复
 * - 是否支持编辑
 * - 是否支持图片
 * - 是否支持文件
 * - 是否支持 reaction
 * - 是否支持 thread
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/channel-capabilities.ts
 *
 * Android 适配：Android AgentLoop 本身不管理频道，但需要知道当前会话的能力
 * 以调整 LLM 的行为（例如告诉 LLM "你可以在当前频道回复"）。
 */

// ── Types ──

/**
 * 频道能力定义。
 * OpenClaw: ChannelCapabilities
 */
data class ChannelCapabilities(
    /** 是否可以发送文本回复 */
    val canReply: Boolean = true,
    /** 是否可以编辑已发送的消息 */
    val canEdit: Boolean = false,
    /** 是否可以发送图片 */
    val canSendImage: Boolean = false,
    /** 是否可以发送文件/文档 */
    val canSendFile: Boolean = false,
    /** 是否可以发送语音消息 */
    val canSendVoice: Boolean = false,
    /** 是否支持 emoji reaction */
    val canReact: Boolean = false,
    /** 是否支持话题/thread 回复 */
    val canThread: Boolean = false,
    /** 是否支持消息卡片 */
    val canSendCard: Boolean = false,
    /** 是否支持投票 */
    val canPoll: Boolean = false,
    /** 是否支持富文本 */
    val canSendRichText: Boolean = false,
    /** 频道 ID/类型标识 */
    val channelType: String = "unknown"
)

// ── Presets ──

/** 飞书频道能力。 */
val CAPABILITIES_FEISHU = ChannelCapabilities(
    canReply = true,
    canEdit = true,
    canSendImage = true,
    canSendFile = true,
    canSendVoice = false,
    canReact = true,
    canThread = true,
    canSendCard = true,
    canPoll = false,
    canSendRichText = true,
    channelType = "feishu"
)

/** Telegram 频道能力。 */
val CAPABILITIES_TELEGRAM = ChannelCapabilities(
    canReply = true,
    canEdit = true,
    canSendImage = true,
    canSendFile = true,
    canSendVoice = true,
    canReact = false,
    canThread = false,
    canSendCard = false,
    canPoll = true,
    canSendRichText = false,
    channelType = "telegram"
)

/** Discord 频道能力。 */
val CAPABILITIES_DISCORD = ChannelCapabilities(
    canReply = true,
    canEdit = true,
    canSendImage = true,
    canSendFile = true,
    canSendVoice = false,
    canReact = true,
    canThread = true,
    canSendCard = false,
    canPoll = false,
    canSendRichText = false,
    channelType = "discord"
)

/** WhatsApp 频道能力。 */
val CAPABILITIES_WHATSAPP = ChannelCapabilities(
    canReply = true,
    canEdit = false,
    canSendImage = true,
    canSendFile = true,
    canSendVoice = true,
    canReact = false,
    canThread = false,
    canSendCard = false,
    canPoll = false,
    canSendRichText = false,
    channelType = "whatsapp"
)

/** Android 本地会话能力（最完整）。 */
val CAPABILITIES_ANDROID_LOCAL = ChannelCapabilities(
    canReply = true,
    canEdit = false,
    canSendImage = true,
    canSendFile = true,
    canSendVoice = false,
    canReact = false,
    canThread = false,
    canSendCard = false,
    canPoll = false,
    canSendRichText = true,
    channelType = "android_local"
)

/**
 * 根据频道类型解析能力。
 * OpenClaw: resolveChannelCapabilities
 */
fun resolveChannelCapabilities(channelType: String?): ChannelCapabilities {
    return when (channelType?.lowercase()?.trim()) {
        "feishu", "lark" -> CAPABILITIES_FEISHU
        "telegram" -> CAPABILITIES_TELEGRAM
        "discord" -> CAPABILITIES_DISCORD
        "whatsapp" -> CAPABILITIES_WHATSAPP
        "android_local", "android", null, "" -> CAPABILITIES_ANDROID_LOCAL
        else -> CAPABILITIES_ANDROID_LOCAL
    }
}

/**
 * 构建频道能力的系统提示文本。
 * 告知 LLM 当前会话支持哪些操作。
 *
 * @param capabilities 频道能力
 * @return 提示文本（如果所有能力都默认则返回空字符串）
 */
fun buildChannelCapabilitiesHint(capabilities: ChannelCapabilities): String {
    if (capabilities == CAPABILITIES_ANDROID_LOCAL) return ""  // Default, no hint needed

    val hints = mutableListOf<String>()

    if (capabilities.canReact) hints.add("You can react to messages using emoji reactions.")
    if (capabilities.canThread) hints.add("You can reply in threads/topics.")
    if (capabilities.canSendCard) hints.add("You can send interactive message cards.")
    if (capabilities.canEdit) hints.add("You can edit your previous messages.")
    if (capabilities.canPoll) hints.add("You can create polls.")
    if (capabilities.canSendVoice) hints.add("You can send voice messages.")
    if (!capabilities.canEdit) hints.add("Note: You cannot edit messages in this channel — each response is final.")
    if (!capabilities.canReact) hints.add("Note: Emoji reactions are not available in this channel.")

    return if (hints.isEmpty()) "" else "## Channel Capabilities\n${hints.joinToString("\n") { "- $it" }}"
}
