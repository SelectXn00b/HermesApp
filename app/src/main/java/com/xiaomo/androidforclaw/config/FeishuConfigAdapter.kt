package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: bridge Feishu config into app channel model.
 */


import com.xiaomo.feishu.FeishuConfig

/**
 * Feishu Config Adapter
 *
 * Converts FeishuChannelConfig from OpenClawConfig to FeishuConfig for feishu-channel module
 */
object FeishuConfigAdapter {

    /**
     * Convert from FeishuChannelConfig to FeishuConfig
     */
    fun toFeishuConfig(channelConfig: FeishuChannelConfig): FeishuConfig {
        return FeishuConfig(
            enabled = channelConfig.enabled,
            appId = channelConfig.appId,
            appSecret = channelConfig.appSecret,
            encryptKey = channelConfig.encryptKey,
            verificationToken = channelConfig.verificationToken,
            domain = channelConfig.domain,
            connectionMode = when (channelConfig.connectionMode) {
                "websocket" -> FeishuConfig.ConnectionMode.WEBSOCKET
                "webhook" -> FeishuConfig.ConnectionMode.WEBHOOK
                else -> FeishuConfig.ConnectionMode.WEBSOCKET
            },
            webhookPath = channelConfig.webhookPath,
            webhookPort = channelConfig.webhookPort ?: 8765,
            dmPolicy = when (channelConfig.dmPolicy) {
                "open" -> FeishuConfig.DmPolicy.OPEN
                "pairing" -> FeishuConfig.DmPolicy.PAIRING
                "allowlist" -> FeishuConfig.DmPolicy.ALLOWLIST
                else -> FeishuConfig.DmPolicy.PAIRING
            },
            allowFrom = channelConfig.allowFrom,
            groupPolicy = when (channelConfig.groupPolicy) {
                "open" -> FeishuConfig.GroupPolicy.OPEN
                "allowlist" -> FeishuConfig.GroupPolicy.ALLOWLIST
                "disabled" -> FeishuConfig.GroupPolicy.DISABLED
                else -> FeishuConfig.GroupPolicy.ALLOWLIST
            },
            groupAllowFrom = channelConfig.groupAllowFrom,
            requireMention = channelConfig.requireMention,
            groupCommandMentionBypass = FeishuConfig.MentionBypass.NEVER,
            allowMentionlessInMultiBotGroup = false,
            topicSessionMode = when (channelConfig.topicSessionMode) {
                "enabled" -> FeishuConfig.TopicSessionMode.ENABLED
                "disabled" -> FeishuConfig.TopicSessionMode.DISABLED
                else -> FeishuConfig.TopicSessionMode.DISABLED
            },
            historyLimit = channelConfig.historyLimit ?: 0,
            dmHistoryLimit = channelConfig.dmHistoryLimit ?: 0,
            textChunkLimit = channelConfig.textChunkLimit,
            chunkMode = when (channelConfig.chunkMode) {
                "length" -> FeishuConfig.ChunkMode.LENGTH
                "newline" -> FeishuConfig.ChunkMode.NEWLINE
                else -> FeishuConfig.ChunkMode.LENGTH
            },
            mediaMaxMb = channelConfig.mediaMaxMb,
            audioMaxDurationSec = 300,
            enableDocTools = channelConfig.tools.doc,
            enableWikiTools = channelConfig.tools.wiki,
            enableDriveTools = channelConfig.tools.drive,
            enableBitableTools = channelConfig.tools.bitable,
            enableTaskTools = channelConfig.tools.task,
            enableChatTools = channelConfig.tools.chat,
            enablePermTools = channelConfig.tools.perm,
            enableUrgentTools = channelConfig.tools.urgent,
            typingIndicator = channelConfig.typingIndicator,
            reactionDedup = channelConfig.reactionDedup,
            debugMode = channelConfig.debugMode
        )
    }

    /**
     * Convert from FeishuConfig to FeishuChannelConfig
     */
    fun fromFeishuConfig(feishuConfig: FeishuConfig): FeishuChannelConfig {
        return FeishuChannelConfig(
            enabled = feishuConfig.enabled,
            appId = feishuConfig.appId,
            appSecret = feishuConfig.appSecret,
            encryptKey = feishuConfig.encryptKey,
            verificationToken = feishuConfig.verificationToken,
            domain = feishuConfig.domain,
            connectionMode = when (feishuConfig.connectionMode) {
                FeishuConfig.ConnectionMode.WEBSOCKET -> "websocket"
                FeishuConfig.ConnectionMode.WEBHOOK -> "webhook"
            },
            webhookPath = feishuConfig.webhookPath,
            webhookPort = feishuConfig.webhookPort,
            dmPolicy = when (feishuConfig.dmPolicy) {
                FeishuConfig.DmPolicy.OPEN -> "open"
                FeishuConfig.DmPolicy.PAIRING -> "pairing"
                FeishuConfig.DmPolicy.ALLOWLIST -> "allowlist"
            },
            allowFrom = feishuConfig.allowFrom,
            groupPolicy = when (feishuConfig.groupPolicy) {
                FeishuConfig.GroupPolicy.OPEN -> "open"
                FeishuConfig.GroupPolicy.ALLOWLIST -> "allowlist"
                FeishuConfig.GroupPolicy.DISABLED -> "disabled"
            },
            groupAllowFrom = feishuConfig.groupAllowFrom,
            requireMention = feishuConfig.requireMention,
            topicSessionMode = when (feishuConfig.topicSessionMode) {
                FeishuConfig.TopicSessionMode.ENABLED -> "enabled"
                FeishuConfig.TopicSessionMode.DISABLED -> "disabled"
            },
            historyLimit = feishuConfig.historyLimit,
            dmHistoryLimit = feishuConfig.dmHistoryLimit,
            textChunkLimit = feishuConfig.textChunkLimit,
            chunkMode = when (feishuConfig.chunkMode) {
                FeishuConfig.ChunkMode.LENGTH -> "length"
                FeishuConfig.ChunkMode.NEWLINE -> "newline"
            },
            mediaMaxMb = feishuConfig.mediaMaxMb,
            tools = FeishuToolsConfig(
                doc = feishuConfig.enableDocTools,
                wiki = feishuConfig.enableWikiTools,
                drive = feishuConfig.enableDriveTools,
                bitable = feishuConfig.enableBitableTools,
                task = feishuConfig.enableTaskTools,
                chat = feishuConfig.enableChatTools,
                perm = feishuConfig.enablePermTools,
                urgent = feishuConfig.enableUrgentTools
            ),
            typingIndicator = feishuConfig.typingIndicator,
            reactionDedup = feishuConfig.reactionDedup,
            debugMode = feishuConfig.debugMode
        )
    }
}
