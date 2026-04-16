package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Per-platform display preferences (formatting, length limits, etc.)
 *
 * Ported from gateway/display_config.py
 */

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Display/formatting configuration for a single platform channel.
 *
 * Controls how the agent's response is formatted before it is sent to the
 * end-user: markdown vs plain-text, maximum message length, whether to
 * split long messages, etc.
 */
data class DisplayConfig(
    /** Platform identifier (e.g. "telegram", "discord", "feishu"). */
    val platform: String = "",
    /** Whether the platform supports markdown formatting. */
    val markdown: Boolean = true,
    /** Maximum length of a single outbound message (characters). */
    val maxMessageLength: Int = 4000,
    /** Whether to split messages that exceed [maxMessageLength]. */
    val splitLongMessages: Boolean = true,
    /** Character used to join split chunks (empty string = no joiner). */
    val splitJoiner: String = "",
    /** Whether the platform supports inline images. */
    val supportsImages: Boolean = true,
    /** Whether the platform supports file attachments. */
    val supportsDocuments: Boolean = true,
    /** Whether the platform supports audio/voice messages. */
    val supportsAudio: Boolean = false,
    /** Whether the platform supports reactions/emoji responses. */
    val supportsReactions: Boolean = false,
    /** Whether the platform supports threaded replies. */
    val supportsThreads: Boolean = false,
    /** Whether the platform supports typing indicators. */
    val supportsTyping: Boolean = true,
    /** Whether the platform supports edit-in-place. */
    val supportsEdit: Boolean = false,
    /** Whether the platform supports message deletion. */
    val supportsDelete: Boolean = false,
    /** Whether the platform supports reply-to-message references. */
    val supportsReplyTo: Boolean = true,
    /** Whether the platform supports link previews. */
    val supportsLinkPreview: Boolean = true,
    /** Whether the platform supports code blocks. */
    val supportsCodeBlocks: Boolean = true,
    /** Whether the platform supports tables. */
    val supportsTables: Boolean = false,
    /** Whether the platform supports horizontal rules. */
    val supportsHorizontalRules: Boolean = true,
    /** Whether the platform supports ordered/unordered lists. */
    val supportsLists: Boolean = true,
    /** Whether the platform supports bold/italic/strikethrough. */
    val supportsFormatting: Boolean = true,
    /** Whether the platform supports headers. */
    val supportsHeaders: Boolean = true,
    /** Whether the platform supports blockquotes. */
    val supportsBlockquotes: Boolean = true,
    /** Whether the platform supports spoiler text. */
    val supportsSpoilers: Boolean = false,
    /** Whether the platform supports custom emoji. */
    val supportsCustomEmoji: Boolean = false,
    /** Whether the platform supports polls. */
    val supportsPolls: Boolean = false,
    /** Whether the platform supports location sharing. */
    val supportsLocation: Boolean = false,
    /** Whether the platform supports contact sharing. */
    val supportsContact: Boolean = false,
    /** Whether the platform supports stickers. */
    val supportsStickers: Boolean = false,
    /** Whether the platform supports video messages. */
    val supportsVideo: Boolean = false,
    /** Whether the platform supports voice messages. */
    val supportsVoice: Boolean = false,
    /** Whether the platform supports animations/GIFs. */
    val supportsAnimation: Boolean = false,
    /** Whether the platform supports inline keyboards/buttons. */
    val supportsInlineKeyboard: Boolean = false,
    /** Whether the platform supports callback queries. */
    val supportsCallbackQuery: Boolean = false,
    /** Whether the platform supports inline queries. */
    val supportsInlineQuery: Boolean = false,
    /** Whether the platform supports shipping queries. */
    val supportsShippingQuery: Boolean = false,
    /** Whether the platform supports pre-checkout queries. */
    val supportsPreCheckoutQuery: Boolean = false,
    /** Whether the platform supports passport. */
    val supportsPassport: Boolean = false,
    /** Whether the platform supports game. */
    val supportsGame: Boolean = false,
    /** Whether the platform supports invoice. */
    val supportsInvoice: Boolean = false,
    /** Whether the platform supports successful payment. */
    val supportsSuccessfulPayment: Boolean = false,
    /** Whether the platform supports video note. */
    val supportsVideoNote: Boolean = false,
    /** Whether the platform supports dice. */
    val supportsDice: Boolean = false,
    /** Whether the platform supports forum/topic. */
    val supportsForum: Boolean = false,
    /** Whether the platform supports general forum topic. */
    val supportsGeneralForumTopic: Boolean = false,
    /** Whether the platform supports write access allowed. */
    val supportsWriteAccessAllowed: Boolean = false,
    /** Whether the platform supports user shared. */
    val supportsUserShared: Boolean = false,
    /** Whether the platform supports chat shared. */
    val supportsChatShared: Boolean = false,
    /** Whether the platform supports story. */
    val supportsStory: Boolean = false) {
    /** Build a summary string for logging. */
    fun summary(): String = buildString {
        append("DisplayConfig(")
        append("platform=$platform, ")
        append("markdown=$markdown, ")
        append("maxLen=$maxMessageLength, ")
        append("split=$splitLongMessages")
        append(")")
    }

    /** Convert to JSON for serialization. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("platform", platform)
        put("markdown", markdown)
        put("max_message_length", maxMessageLength)
        put("split_long_messages", splitLongMessages)
        put("split_joiner", splitJoiner)
        put("supports_images", supportsImages)
        put("supports_documents", supportsDocuments)
        put("supports_audio", supportsAudio)
        put("supports_reactions", supportsReactions)
        put("supports_threads", supportsThreads)
        put("supports_typing", supportsTyping)
        put("supports_edit", supportsEdit)
        put("supports_delete", supportsDelete)
        put("supports_reply_to", supportsReplyTo)
        put("supports_link_preview", supportsLinkPreview)
        put("supports_code_blocks", supportsCodeBlocks)
        put("supports_tables", supportsTables)
        put("supports_horizontal_rules", supportsHorizontalRules)
        put("supports_lists", supportsLists)
        put("supports_formatting", supportsFormatting)
        put("supports_headers", supportsHeaders)
        put("supports_blockquotes", supportsBlockquotes)
        put("supports_spoilers", supportsSpoilers)
        put("supports_custom_emoji", supportsCustomEmoji)
        put("supports_polls", supportsPolls)
        put("supports_location", supportsLocation)
        put("supports_contact", supportsContact)
        put("supports_stickers", supportsStickers)
        put("supports_video", supportsVideo)
        put("supports_voice", supportsVoice)
        put("supports_animation", supportsAnimation)
        put("supports_inline_keyboard", supportsInlineKeyboard)
        put("supports_callback_query", supportsCallbackQuery)
        put("supports_inline_query", supportsInlineQuery)
        put("supports_shipping_query", supportsShippingQuery)
        put("supports_pre_checkout_query", supportsPreCheckoutQuery)
        put("supports_passport", supportsPassport)
        put("supports_game", supportsGame)
        put("supports_invoice", supportsInvoice)
        put("supports_successful_payment", supportsSuccessfulPayment)
        put("supports_video_note", supportsVideoNote)
        put("supports_dice", supportsDice)
        put("supports_forum", supportsForum)
        put("supports_general_forum_topic", supportsGeneralForumTopic)
        put("supports_write_access_allowed", supportsWriteAccessAllowed)
        put("supports_user_shared", supportsUserShared)
        put("supports_chat_shared", supportsChatShared)
        put("supports_story", supportsStory)
    }

    companion object {
        /** Parse from JSON. */
        fun fromJson(json: JSONObject): DisplayConfig = DisplayConfig(
            platform = json.optString("platform", ""),
            markdown = json.optBoolean("markdown", true),
            maxMessageLength = json.optInt("max_message_length", 4000),
            splitLongMessages = json.optBoolean("split_long_messages", true),
            splitJoiner = json.optString("split_joiner", ""),
            supportsImages = json.optBoolean("supports_images", true),
            supportsDocuments = json.optBoolean("supports_documents", true),
            supportsAudio = json.optBoolean("supports_audio", false),
            supportsReactions = json.optBoolean("supports_reactions", false),
            supportsThreads = json.optBoolean("supports_threads", false),
            supportsTyping = json.optBoolean("supports_typing", true),
            supportsEdit = json.optBoolean("supports_edit", false),
            supportsDelete = json.optBoolean("supports_delete", false),
            supportsReplyTo = json.optBoolean("supports_reply_to", true),
            supportsLinkPreview = json.optBoolean("supports_link_preview", true),
            supportsCodeBlocks = json.optBoolean("supports_code_blocks", true),
            supportsTables = json.optBoolean("supports_tables", false),
            supportsHorizontalRules = json.optBoolean("supports_horizontal_rules", true),
            supportsLists = json.optBoolean("supports_lists", true),
            supportsFormatting = json.optBoolean("supports_formatting", true),
            supportsHeaders = json.optBoolean("supports_headers", true),
            supportsBlockquotes = json.optBoolean("supports_blockquotes", true),
            supportsSpoilers = json.optBoolean("supports_spoilers", false),
            supportsCustomEmoji = json.optBoolean("supports_custom_emoji", false),
            supportsPolls = json.optBoolean("supports_polls", false),
            supportsLocation = json.optBoolean("supports_location", false),
            supportsContact = json.optBoolean("supports_contact", false),
            supportsStickers = json.optBoolean("supports_stickers", false),
            supportsVideo = json.optBoolean("supports_video", false),
            supportsVoice = json.optBoolean("supports_voice", false),
            supportsAnimation = json.optBoolean("supports_animation", false),
            supportsInlineKeyboard = json.optBoolean("supports_inline_keyboard", false),
            supportsCallbackQuery = json.optBoolean("supports_callback_query", false),
            supportsInlineQuery = json.optBoolean("supports_inline_query", false),
            supportsShippingQuery = json.optBoolean("supports_shipping_query", false),
            supportsPreCheckoutQuery = json.optBoolean("supports_pre_checkout_query", false),
            supportsPassport = json.optBoolean("supports_passport", false),
            supportsGame = json.optBoolean("supports_game", false),
            supportsInvoice = json.optBoolean("supports_invoice", false),
            supportsSuccessfulPayment = json.optBoolean("supports_successful_payment", false),
            supportsVideoNote = json.optBoolean("supports_video_note", false),
            supportsDice = json.optBoolean("supports_dice", false),
            supportsForum = json.optBoolean("supports_forum", false),
            supportsGeneralForumTopic = json.optBoolean("supports_general_forum_topic", false),
            supportsWriteAccessAllowed = json.optBoolean("supports_write_access_allowed", false),
            supportsUserShared = json.optBoolean("supports_user_shared", false),
            supportsChatShared = json.optBoolean("supports_chat_shared", false),
            supportsStory = json.optBoolean("supports_story", false))
    }
}

/**
 * Registry of per-platform display configs.
 *
 * Thread-safe.  Platform adapters register their config on startup.
 */
class DisplayConfigRegistry {
    private val _configs: ConcurrentHashMap<String, DisplayConfig> = ConcurrentHashMap()

    /** Register a display config for [config.platform]. */
    fun register(config: DisplayConfig) {
        _configs[config.platform] = config
    }

    /** Get the display config for [platform], or a sensible default. */
    fun get(platform: String): DisplayConfig =
        _configs[platform] ?: DisplayConfig(platform = platform)

    /** Remove the config for [platform]. */
    fun remove(platform: String) {
        _configs.remove(platform)
    }

    /** True when at least one platform is registered. */
    val isNotEmpty: Boolean get() = _configs.isNotEmpty()

    /** Return all registered platform names. */
    val platforms: Set<String> get() = _configs.keys.toSet()


}
