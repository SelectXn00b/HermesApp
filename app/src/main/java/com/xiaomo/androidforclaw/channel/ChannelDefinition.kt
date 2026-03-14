/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/(all)
 *
 * AndroidForClaw adaptation: channel management.
 */
package com.xiaomo.androidforclaw.channel

/**
 * Channel Definition - Define Android App Channel according to OpenClaw architecture
 *
 * OpenClaw Channel core concepts:
 * - Channel: Communication channel（WhatsApp, Telegram, Discord, etc.）
 * - Account: Account within channel (multi-account support)
 * - Session: Session instance (conversation with user/device)
 * - Capabilities: Channel capabilities（polls, threads, media, etc.）
 *
 * Android App Channel characteristics:
 * - Device control channel (non-social messaging channel)
 * - Single device direct execution mode (no groups, no threads)
 * - Tool-intensive（tap, swipe, screenshot, etc.）
 * - Authentication: ADB/Accessibility pairing (not token)
 */

/**
 * Channel ID - Channel unique identifier
 */
const val CHANNEL_ID = "android-app"

/**
 * Channel Meta - Channel metadata
 */
data class ChannelMeta(
    val label: String,               // Display name
    val emoji: String,               // Icon emoji
    val description: String,         // Description
    val systemImage: String? = null  // System icon path
)

val CHANNEL_META = ChannelMeta(
    label = "Android App",
    emoji = "📱",
    description = "AndroidForClaw Android device control channel"
)

/**
 * Channel Capabilities - Channel capability definition (reference OpenClaw)
 */
data class ChannelCapabilities(
    val chatTypes: List<ChatType>,    // Supported chat types
    val polls: Boolean = false,       // Polls
    val reactions: Boolean = false,   // Reactions/emoji
    val edit: Boolean = false,        // Edit messages
    val unsend: Boolean = false,      // Unsend messages
    val reply: Boolean = false,       // Reply to messages
    val effects: Boolean = false,     // Visual effects
    val groupManagement: Boolean = false,  // Group management
    val threads: Boolean = false,     // Threads/nested conversations
    val media: Boolean = false,       // Media (images/files)
    val nativeCommands: Boolean = false,   // Native commands
    val blockStreaming: Boolean = false    // Block streaming response
) {
    enum class ChatType {
        DIRECT,      // Direct conversation
        GROUP,       // Group
        CHANNEL,     // Channel
        THREAD       // Thread
    }
}

/**
 * Android App Channel capability configuration
 *
 * Comparison with other OpenClaw channels:
 * - WhatsApp: direct, group, polls, reactions, media
 * - Telegram: direct, group, channel, thread, polls, reactions, media, nativeCommands, blockStreaming
 * - Discord: direct, channel, thread, polls, reactions, media, nativeCommands, blockStreaming
 * - Slack: direct, channel, thread, reactions, media, nativeCommands, blockStreaming
 * - Signal: direct, group, reactions, media
 *
 * Android App: Minimal capabilities (device control only)
 */
val ANDROID_CHANNEL_CAPABILITIES = ChannelCapabilities(
    chatTypes = listOf(ChannelCapabilities.ChatType.DIRECT),  // Direct execution only
    polls = false,
    reactions = false,
    edit = false,
    unsend = false,
    reply = false,
    effects = false,
    groupManagement = false,
    threads = false,
    media = true,                    // ✓ Screenshot/screen recording
    nativeCommands = true,           // ✓ Device operation commands
    blockStreaming = true            // ✓ Block streaming response（等待完整结果）
)

/**
 * Channel Account - Account configuration (corresponds to OpenClaw's ChannelAccountSnapshot)
 */
data class ChannelAccount(
    val accountId: String,                     // Account ID（Android: device-{uuid}）
    val name: String? = null,                  // Account name (device name)
    val enabled: Boolean = true,               // Is enabled
    val configured: Boolean = false,           // Is configured
    val linked: Boolean = false,               // Is linked
    val running: Boolean = false,              // Is running
    val connected: Boolean = false,            // Is linked
    val reconnectAttempts: Int = 0,            // Reconnect attempt count
    val lastConnectedAt: Long? = null,         // Last connected time
    val lastError: String? = null,             // Last error
    val lastStartAt: Long? = null,             // Last start time
    val lastStopAt: Long? = null,              // Last stop time
    val lastInboundAt: Long? = null,           // Last inbound message time
    val lastOutboundAt: Long? = null,          // Last outbound message time
    val lastProbeAt: Long? = null,             // Last probe time

    // Android-specific fields
    val deviceId: String? = null,              // Device ID
    val deviceModel: String? = null,           // Device model
    val androidVersion: String? = null,        // Android version
    val apiLevel: Int? = null,                 // API Level
    val architecture: String? = null,          // CPU architecture
    val accessibilityEnabled: Boolean = false, // Accessibility service status
    val overlayPermission: Boolean = false,    // Overlay permission
    val mediaProjection: Boolean = false       // Screen recording permission
)

/**
 * Channel Status - Channel status snapshot (corresponds to OpenClaw's ChannelsStatusSnapshot)
 */
data class ChannelStatus(
    val timestamp: Long = System.currentTimeMillis(),
    val channelId: String = CHANNEL_ID,
    val meta: ChannelMeta = CHANNEL_META,
    val capabilities: ChannelCapabilities = ANDROID_CHANNEL_CAPABILITIES,
    val accounts: List<ChannelAccount> = emptyList(),
    val defaultAccountId: String? = null
)

/**
 * Agent Prompt Hints - System prompt hints (corresponds to OpenClaw's agentPrompt.messageToolHints)
 */
object AndroidChannelPromptHints {

    /**
     * Generate channel-specific system prompt hints
     */
    fun getMessageToolHints(account: ChannelAccount? = null): List<String> {
        val hints = mutableListOf<String>()

        // Basic hints
        hints.add("You are running on an Android device with direct access to device controls")
        hints.add("Use tools to observe and control the device:")
        hints.add("  - Observation: screenshot, get_ui_tree")
        hints.add("  - Actions: tap, swipe, type, long_press")
        hints.add("  - Navigation: home, back, open_app")
        hints.add("  - System: wait, stop, notification")

        // Device-specific hints
        if (account != null) {
            hints.add("")
            hints.add("Device Information:")
            hints.add("  - Model: ${account.deviceModel ?: "Unknown"}")
            hints.add("  - Android: ${account.androidVersion ?: "Unknown"} (API ${account.apiLevel ?: "Unknown"})")
            hints.add("  - Architecture: ${account.architecture ?: "Unknown"}")

            // Permissions status hints
            hints.add("")
            hints.add("Permissions Status:")
            hints.add("  - Accessibility: ${if (account.accessibilityEnabled) "✓ Enabled" else "✗ Disabled"}")
            hints.add("  - Overlay: ${if (account.overlayPermission) "✓ Granted" else "✗ Not granted"}")
            hints.add("  - Screen Capture: ${if (account.mediaProjection) "✓ Granted" else "✗ Not granted"}")
        }

        // Best practices hints
        hints.add("")
        hints.add("Best Practices:")
        hints.add("  - Always screenshot before and after actions")
        hints.add("  - Verify state changes after operations")
        hints.add("  - Use wait() for loading states")
        hints.add("  - Try alternative approaches when blocked")

        return hints
    }

    /**
     * Generate Runtime Section Channel information
     */
    fun getRuntimeChannelInfo(account: ChannelAccount? = null): String {
        return buildString {
            appendLine("channel: $CHANNEL_ID")
            appendLine("channel_label: ${CHANNEL_META.label}")
            if (account != null) {
                appendLine("account_id: ${account.accountId}")
                appendLine("device_id: ${account.deviceId ?: "unknown"}")
                appendLine("device_model: ${account.deviceModel ?: "unknown"}")
            }
        }.trim()
    }
}

/**
 * Channel Config - Channel configuration
 */
data class ChannelConfig(
    val enabled: Boolean = true,
    val defaultAccount: String? = null,
    val accounts: Map<String, ChannelAccount> = emptyMap()
)
