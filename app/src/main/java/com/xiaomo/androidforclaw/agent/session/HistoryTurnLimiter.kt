package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/history.ts
 *   (limitHistoryTurns, getHistoryLimitFromSessionKey, getDmHistoryLimitFromSessionKey)
 *
 * AndroidForClaw adaptation: per-channel, per-chatType history turn limiting.
 * Resolves the correct historyLimit based on session key and channel config.
 */

import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.logging.Log

/**
 * HistoryTurnLimiter — Resolve per-channel history turn limits.
 * Aligned with OpenClaw pi-embedded-runner/history.ts.
 */
object HistoryTurnLimiter {

    private const val TAG = "HistoryTurnLimiter"

    /** Default turn limit when no config is specified */
    const val DEFAULT_HISTORY_LIMIT = 30

    /**
     * Limit history turns by keeping only the last N user turns and their responses.
     * Aligned with OpenClaw limitHistoryTurns.
     *
     * Walks backward counting "user" role messages. When user count exceeds limit,
     * slices from that user message index onward.
     *
     * @param messages Full message history
     * @param limit Max number of user turns to keep (null or <= 0 means no limit)
     * @return Trimmed message list
     */
    fun <T> limitHistoryTurns(
        messages: List<T>,
        limit: Int?,
        roleSelector: (T) -> String
    ): List<T> {
        if (limit == null || limit <= 0 || messages.isEmpty()) return messages
        if (messages.size <= 1) return messages

        var userCount = 0
        var lastUserIndex = 0

        for (i in messages.indices.reversed()) {
            if (roleSelector(messages[i]) == "user") {
                userCount++
                if (userCount > limit) {
                    // We've found more than `limit` user turns;
                    // keep from the next user message forward
                    lastUserIndex = i + 1
                    // Find the actual next user message
                    while (lastUserIndex < messages.size &&
                        roleSelector(messages[lastUserIndex]) != "user") {
                        lastUserIndex++
                    }
                    break
                }
            }
        }

        return if (lastUserIndex > 0 && lastUserIndex < messages.size) {
            messages.subList(lastUserIndex, messages.size)
        } else {
            messages
        }
    }

    /** Thread/topic suffix pattern — stripped from session keys for DM lookup */
    private val THREAD_SUFFIX_REGEX = Regex("^(.*)(?::(?:thread|topic):\\d+)$", RegexOption.IGNORE_CASE)

    /**
     * Resolve history limit from session key and config.
     * Aligned with OpenClaw getHistoryLimitFromSessionKey.
     *
     * Session key formats:
     * - Feishu: "group:oc_xxx" / "p2p:ou_xxx" / "group:oc_xxx:user:ou_xxx"
     * - Gateway: "feishu:dm:ou_xxx" / "feishu:group:oc_xxx"
     * - Telegram: "telegram:dm:123" / "telegram:g-123"
     * - Discord: "discord:dm:456" / "discord:guild:789"
     *
     * Resolution order (aligned with OpenClaw):
     * 1. Per-DM override (dmHistoryLimit) for direct chats
     * 2. Channel-level historyLimit for group chats
     * 3. Default (30)
     */
    fun getHistoryLimitFromSessionKey(
        sessionKey: String?,
        configLoader: ConfigLoader?
    ): Int {
        if (sessionKey == null || configLoader == null) return DEFAULT_HISTORY_LIMIT

        val config = try {
            configLoader.loadOpenClawConfig()
        } catch (_: Exception) {
            return DEFAULT_HISTORY_LIMIT
        } ?: return DEFAULT_HISTORY_LIMIT

        val key = sessionKey.trim()

        // Extract channel and chat kind from session key
        val parsed = parseSessionKeyForChannel(key)
        if (parsed == null) {
            Log.d(TAG, "Cannot parse session key for history limit: $key")
            return DEFAULT_HISTORY_LIMIT
        }

        val (channel, isDm) = parsed

        // Resolve channel config
        val channelConfig = when (channel) {
            "feishu" -> config.channels?.feishu
            "telegram" -> config.channels?.telegram
            "discord" -> config.channels?.discord
            "slack" -> config.channels?.slack
            "whatsapp" -> config.channels?.whatsapp
            "signal" -> config.channels?.signal
            else -> null
        }

        if (channelConfig == null) return DEFAULT_HISTORY_LIMIT

        // DM → dmHistoryLimit takes priority
        if (isDm) {
            val dmLimit = getChannelDmHistoryLimit(channelConfig)
            if (dmLimit != null) {
                Log.d(TAG, "Using dmHistoryLimit=$dmLimit for $channel DM session")
                return dmLimit
            }
        }

        // Fall back to channel historyLimit
        val limit = getChannelHistoryLimit(channelConfig)
        if (limit != null) {
            Log.d(TAG, "Using historyLimit=$limit for $channel ${if (isDm) "DM" else "group"} session")
            return limit
        }

        return DEFAULT_HISTORY_LIMIT
    }

    /**
     * Deprecated alias for getHistoryLimitFromSessionKey.
     * Aligned with OpenClaw getDmHistoryLimitFromSessionKey.
     */
    @Deprecated("Use getHistoryLimitFromSessionKey", ReplaceWith("getHistoryLimitFromSessionKey(sessionKey, configLoader)"))
    fun getDmHistoryLimitFromSessionKey(
        sessionKey: String?,
        configLoader: ConfigLoader?
    ): Int = getHistoryLimitFromSessionKey(sessionKey, configLoader)

    /**
     * Parse session key to extract channel name and whether it's a DM.
     * Returns (channelName, isDm) or null if unparseable.
     */
    internal fun parseSessionKeyForChannel(sessionKey: String): Pair<String, Boolean>? {
        val key = stripThreadSuffix(sessionKey)

        // Feishu extension format: "p2p:xxx" / "group:xxx"
        if (key.startsWith("p2p:")) return Pair("feishu", true)
        if (key.startsWith("group:")) return Pair("feishu", false)

        // Gateway format: "channel:kind:id" (e.g. "feishu:dm:ou_xxx", "telegram:g-123")
        val parts = key.split(":")
        if (parts.size >= 2) {
            val channel = parts[0].lowercase()
            val kind = parts[1].lowercase()
            val isDm = kind == "dm" || kind == "direct" || kind == "p2p"
            val isGroup = kind.startsWith("g-") || kind == "group" || kind == "guild" || kind == "channel"
            if (isDm || isGroup) return Pair(channel, isDm)
        }

        // Gateway underscore format: "xxx_group" / "xxx_p2p"
        if (key.endsWith("_p2p") || key.endsWith("_dm") || key.endsWith("_direct")) {
            return Pair(guessChannelFromKey(key), true)
        }
        if (key.endsWith("_group")) {
            return Pair(guessChannelFromKey(key), false)
        }

        return null
    }

    /**
     * Strip thread/topic suffix from session key.
     * Aligned with OpenClaw THREAD_SUFFIX_REGEX.
     */
    private fun stripThreadSuffix(key: String): String {
        val match = THREAD_SUFFIX_REGEX.find(key)
        return match?.groupValues?.get(1) ?: key
    }

    /** Best-effort channel guess from legacy key format */
    private fun guessChannelFromKey(key: String): String {
        return when {
            key.contains("oc_") || key.contains("ou_") -> "feishu"
            key.startsWith("tg_") || key.matches(Regex("^-?\\d+_.*")) -> "telegram"
            else -> "unknown"
        }
    }

    /** Extract historyLimit from any channel config (reflection-free) */
    private fun getChannelHistoryLimit(config: Any): Int? {
        return when (config) {
            is com.xiaomo.androidforclaw.config.FeishuChannelConfig -> config.historyLimit
            is com.xiaomo.androidforclaw.config.TelegramChannelConfig -> config.historyLimit
            is com.xiaomo.androidforclaw.config.DiscordChannelConfig -> config.historyLimit
            is com.xiaomo.androidforclaw.config.SlackChannelConfig -> config.historyLimit
            is com.xiaomo.androidforclaw.config.WhatsAppChannelConfig -> config.historyLimit
            is com.xiaomo.androidforclaw.config.SignalChannelConfig -> config.historyLimit
            else -> null
        }
    }

    /** Extract dmHistoryLimit from any channel config */
    private fun getChannelDmHistoryLimit(config: Any): Int? {
        return when (config) {
            is com.xiaomo.androidforclaw.config.FeishuChannelConfig -> config.dmHistoryLimit
            is com.xiaomo.androidforclaw.config.TelegramChannelConfig -> config.dmHistoryLimit
            is com.xiaomo.androidforclaw.config.DiscordChannelConfig -> config.dmHistoryLimit
            is com.xiaomo.androidforclaw.config.SlackChannelConfig -> config.dmHistoryLimit
            is com.xiaomo.androidforclaw.config.WhatsAppChannelConfig -> config.dmHistoryLimit
            is com.xiaomo.androidforclaw.config.SignalChannelConfig -> config.dmHistoryLimit
            else -> null
        }
    }
}
