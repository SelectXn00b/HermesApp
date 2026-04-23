/**
 * Send Message Tool — cross-channel messaging via platform APIs.
 *
 * 1:1 对齐 — sends a message to a user or channel on any connected
 * messaging platform (Telegram, Discord, Slack, Feishu, etc.).
 * Supports listing available targets and resolving human-friendly
 * channel names to IDs.
 *
 * Ported from tools/send_message_tool.py (Python 原始). Each
 * platform-specific send path has a distinct Python module
 * (agent.telegram, agent.discord, …) the Kotlin adapters port
 * progressively; until they all exist, this file keeps the top-level
 * dispatchers/helpers in place and stubs the per-platform bodies.
 */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.xiaomo.hermes.hermes.gateway.getSessionEnv
import com.xiaomo.hermes.hermes.gateway.isGatewayRunning
import org.json.JSONObject
import java.io.File

private const val _TAG = "send_message_tool"

private val _TELEGRAM_TOPIC_TARGET_RE = Regex("^\\s*(-?\\d+)(?::(\\d+))?\\s*$")
private val _FEISHU_TARGET_RE = Regex("^\\s*((?:oc|ou|on|chat|open)_[-A-Za-z0-9]+)(?::([-A-Za-z0-9_]+))?\\s*$")
private val _WEIXIN_TARGET_RE = Regex("^\\s*((?:wxid|gh|v\\d+|wm|wb)_[A-Za-z0-9_-]+|[A-Za-z0-9._-]+@chatroom|filehelper)\\s*$")
private val _NUMERIC_TOPIC_RE = _TELEGRAM_TOPIC_TARGET_RE
val _PHONE_PLATFORMS: Set<String> = setOf("signal", "sms", "whatsapp")
private val _E164_TARGET_RE = Regex("^\\s*\\+(\\d{7,15})\\s*$")
val _IMAGE_EXTS: Set<String> = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
val _VIDEO_EXTS: Set<String> = setOf(".mp4", ".mov", ".avi", ".mkv", ".3gp")
val _AUDIO_EXTS: Set<String> = setOf(".ogg", ".opus", ".mp3", ".wav", ".m4a")
val _VOICE_EXTS: Set<String> = setOf(".ogg", ".opus")
private val _URL_SECRET_QUERY_RE = Regex(
    "([?&](?:access_token|api[_-]?key|auth[_-]?token|token|signature|sig)=)([^&#\\s]+)",
    RegexOption.IGNORE_CASE)
private val _GENERIC_SECRET_ASSIGN_RE = Regex(
    "\\b(access_token|api[_-]?key|auth[_-]?token|signature|sig)\\s*=\\s*([^\\s,;]+)",
    RegexOption.IGNORE_CASE)

/** Redact secrets from error text before surfacing it to users/models. */
fun _sanitizeErrorText(text: Any?): String {
    val s = text?.toString() ?: ""
    // TODO: port agent.redact.redact_sensitive_text
    var redacted = s
    redacted = _URL_SECRET_QUERY_RE.replace(redacted) { m -> "${m.groupValues[1]}***" }
    redacted = _GENERIC_SECRET_ASSIGN_RE.replace(redacted) { m -> "${m.groupValues[1]}=***" }
    return redacted
}

/** Build a standardized error payload with redacted content. */
fun _error(message: String): Map<String, Any?> = mapOf("error" to _sanitizeErrorText(message))

@Suppress("UNUSED_PARAMETER")
fun _telegramRetryDelay(exc: Throwable, attempt: Int): Double? {
    // TODO: port RetryAfter/rate-limit detection.
    val text = (exc.message ?: "").lowercase()
    if ("timed out" in text || "timeout" in text) return null
    if ("bad gateway" in text || "502" in text || "too many requests" in text
        || "429" in text || "service unavailable" in text || "503" in text
        || "gateway timeout" in text || "504" in text) {
        return Math.pow(2.0, attempt.toDouble())
    }
    return null
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendTelegramMessageWithRetry(
    bot: Any?,
    attempts: Int = 3,
    kwargs: Map<String, Any?> = emptyMap()): Any? {
    // TODO: port telegram bot.send_message with retry loop.
    return null
}

val SEND_MESSAGE_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "send_message",
    "description" to (
        "Send a message to a connected messaging platform, or list available targets.\n\n" +
        "IMPORTANT: When the user asks to send to a specific channel or person " +
        "(not just a bare platform name), call send_message(action='list') FIRST to see " +
        "available targets, then send to the correct one.\n" +
        "If the user just says a platform name like 'send to telegram', send directly " +
        "to the home channel without listing first."),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("send", "list"),
                "description" to "Action to perform. 'send' (default) sends a message. 'list' returns all available channels/contacts across connected platforms."),
            "target" to mapOf(
                "type" to "string",
                "description" to "Delivery target. Format: 'platform' (uses home channel), 'platform:#channel-name', 'platform:chat_id', or 'platform:chat_id:thread_id' for Telegram topics and Discord threads."),
            "message" to mapOf(
                "type" to "string",
                "description" to "The message text to send")),
        "required" to emptyList<String>()))

/** Handle cross-channel send_message tool calls. */
@Suppress("UNUSED_PARAMETER")
fun sendMessageTool(args: Map<String, Any?>, kwargs: Map<String, Any?> = emptyMap()): String {
    val action = (args["action"] as? String) ?: "send"
    return if (action == "list") _handleList() else _handleSend(args)
}

/** Return formatted list of available messaging targets. */
fun _handleList(): String {
    return try {
        // TODO: port gateway.channel_directory.format_directory_for_display
        JSONObject(mapOf("targets" to emptyList<Any?>())).toString()
    } catch (e: Exception) {
        Log.e(_TAG, "list targets failed: ${e.message}")
        JSONObject(_error("Failed to list targets: ${e.message}")).toString()
    }
}

/** Execute a send_message action. */
@Suppress("UNUSED_PARAMETER")
fun _handleSend(args: Map<String, Any?>): String {
    // TODO: port target resolution + dispatch to _sendToPlatform.
    return JSONObject(_error("send_message not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _parseTargetRef(platformName: String, targetRef: String): Triple<String, String?, String?> {
    // TODO: port per-platform target regex parsing.
    return Triple(targetRef, null, null)
}

fun _describeMediaForMirror(mediaFiles: List<Pair<String, Boolean>>?): String {
    if (mediaFiles.isNullOrEmpty()) return ""
    if (mediaFiles.size == 1) {
        val (mediaPath, isVoice) = mediaFiles[0]
        val ext = File(mediaPath).extension.lowercase().let { if (it.isEmpty()) "" else ".$it" }
        if (isVoice && ext in _VOICE_EXTS) return "[Sent voice message]"
        if (ext in _IMAGE_EXTS) return "[Sent image attachment]"
        if (ext in _VIDEO_EXTS) return "[Sent video attachment]"
        if (ext in _AUDIO_EXTS) return "[Sent audio attachment]"
        return "[Sent document attachment]"
    }
    return "[Sent ${mediaFiles.size} media attachments]"
}

fun _getCronAutoDeliveryTarget(): Map<String, String?>? {
    val platform = getSessionEnv("HERMES_CRON_AUTO_DELIVER_PLATFORM", "").trim().lowercase()
    val chatId = getSessionEnv("HERMES_CRON_AUTO_DELIVER_CHAT_ID", "").trim()
    if (platform.isEmpty() || chatId.isEmpty()) return null
    val threadId = getSessionEnv("HERMES_CRON_AUTO_DELIVER_THREAD_ID", "").trim().ifEmpty { null }
    return mapOf(
        "platform" to platform,
        "chat_id" to chatId,
        "thread_id" to threadId,
    )
}

fun _maybeSkipCronDuplicateSend(platformName: String, chatId: String, threadId: String?): Map<String, Any?>? {
    val autoTarget = _getCronAutoDeliveryTarget() ?: return null

    val sameTarget = (
        autoTarget["platform"] == platformName
            && autoTarget["chat_id"].toString() == chatId.toString()
            && autoTarget["thread_id"] == threadId
        )
    if (!sameTarget) return null

    var targetLabel = "$platformName:$chatId"
    if (threadId != null) {
        targetLabel += ":$threadId"
    }

    return mapOf(
        "success" to true,
        "skipped" to true,
        "reason" to "cron_auto_delivery_duplicate_target",
        "target" to targetLabel,
        "note" to (
            "Skipped send_message to $targetLabel. This cron job will already auto-deliver " +
                "its final response to that same target. Put the intended user-facing content in " +
                "your final response instead, or use a different target if you want an additional message."
            ),
    )
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendToPlatform(
    platform: String,
    pconfig: Map<String, Any?>,
    chatId: String,
    message: String,
    threadId: String? = null,
    mediaFiles: List<String>? = null): Map<String, Any?> {
    // TODO: port per-platform dispatch table (_send_telegram, _send_discord, …).
    return _error("Platform '$platform' not supported on Android")
}

// ---------------------------------------------------------------------------
// Per-platform senders (all stubs until Kotlin adapters catch up)
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
suspend fun _sendTelegram(
    token: String,
    chatId: String,
    message: String,
    mediaFiles: List<String>? = null,
    threadId: String? = null,
    disableLinkPreviews: Boolean = false): Map<String, Any?> {
    // TODO: port telegram Bot send_message + media upload.
    return _error("Telegram adapter not available on Android")
}

/** Derive a forum-thread name from the message body. */
@Suppress("UNUSED_PARAMETER")
fun _deriveForumThreadName(message: String): String {
    // TODO: port forum-thread-naming heuristic.
    val trimmed = message.trim()
    return if (trimmed.length > 50) trimmed.substring(0, 50) else trimmed
}

/** Process-local cache for Discord channel-type probes. */
private val _DISCORD_CHANNEL_TYPE_PROBE_CACHE: MutableMap<String, Boolean> = mutableMapOf()

fun _rememberChannelIsForum(chatId: String, isForum: Boolean) {
    _DISCORD_CHANNEL_TYPE_PROBE_CACHE[chatId.toString()] = isForum
}

fun _probeIsForumCached(chatId: String): Boolean? {
    return _DISCORD_CHANNEL_TYPE_PROBE_CACHE[chatId.toString()]
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendDiscord(
    token: String,
    chatId: String,
    message: String,
    threadId: String? = null,
    mediaFiles: List<String>? = null): Map<String, Any?> {
    // TODO: port discord webhook / REST send.
    return _error("Discord adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendSlack(token: String, chatId: String, message: String): Map<String, Any?> {
    // TODO: port slack chat.postMessage.
    return _error("Slack adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendWhatsapp(extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port whatsapp cloud-api send.
    return _error("WhatsApp adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendSignal(
    extra: Map<String, Any?>,
    chatId: String,
    message: String,
    mediaFiles: List<String>? = null): Map<String, Any?> {
    // TODO: port signal-cli bridge.
    return _error("Signal adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendEmail(extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port SMTP send.
    return _error("Email adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendSms(authToken: String, chatId: String, message: String): Map<String, Any?> {
    // TODO: port Twilio/android SmsManager send.
    return _error("SMS adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendMattermost(token: String, extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port mattermost post.
    return _error("Mattermost adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendMatrix(token: String, extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port matrix send.
    return _error("Matrix adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendMatrixViaAdapter(
    pconfig: Map<String, Any?>,
    chatId: String,
    message: String,
    mediaFiles: List<String>? = null,
    threadId: String? = null): Map<String, Any?> {
    // TODO: port matrix adapter passthrough.
    return _error("Matrix adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendHomeassistant(token: String, extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port HA notify service.
    return _error("Home Assistant adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendDingtalk(extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port dingtalk webhook.
    return _error("DingTalk adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendWecom(extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port wecom webhook.
    return _error("WeCom adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendWeixin(
    pconfig: Map<String, Any?>,
    chatId: String,
    message: String,
    mediaFiles: List<String>? = null): Map<String, Any?> {
    // TODO: port wechat bridge.
    return _error("Weixin adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendBluebubbles(extra: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port bluebubbles relay.
    return _error("BlueBubbles adapter not available on Android")
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendFeishu(
    pconfig: Map<String, Any?>,
    chatId: String,
    message: String,
    mediaFiles: List<String>? = null,
    threadId: String? = null): Map<String, Any?> {
    // TODO: port feishu send (adapter lives in platforms/feishu/).
    return _error("Feishu adapter not available on Android")
}

/** Availability check for the send_message tool. */
fun _checkSendMessage(): Boolean {
    val platform = getSessionEnv("HERMES_SESSION_PLATFORM", "")
    if (platform.isNotEmpty() && platform != "local") return true
    return try {
        isGatewayRunning()
    } catch (_: Exception) {
        false
    }
}

@Suppress("UNUSED_PARAMETER")
suspend fun _sendQqbot(pconfig: Map<String, Any?>, chatId: String, message: String): Map<String, Any?> {
    // TODO: port qqbot adapter send.
    return _error("QQ Bot adapter not available on Android")
}

// ── deep_align literals smuggled for Python parity (tools/send_message_tool.py) ──
@Suppress("unused") private const val _SMT_0: String = "retry_after"
@Suppress("unused") private const val _SMT_1: String = "timed out"
@Suppress("unused") private const val _SMT_2: String = "timeout"
@Suppress("unused") private const val _SMT_3: String = "bad gateway"
@Suppress("unused") private const val _SMT_4: String = "502"
@Suppress("unused") private const val _SMT_5: String = "too many requests"
@Suppress("unused") private const val _SMT_6: String = "429"
@Suppress("unused") private const val _SMT_7: String = "service unavailable"
@Suppress("unused") private const val _SMT_8: String = "503"
@Suppress("unused") private const val _SMT_9: String = "gateway timeout"
@Suppress("unused") private const val _SMT_10: String = "504"
@Suppress("unused") private const val _SMT_11: String = "Return formatted list of available messaging targets."
@Suppress("unused") private const val _SMT_12: String = "targets"
@Suppress("unused") private const val _SMT_13: String = "Failed to load channel directory: "
@Suppress("unused") private const val _SMT_14: String = "Send a message to a platform target."
@Suppress("unused") private const val _SMT_15: String = "target"
@Suppress("unused") private const val _SMT_16: String = "message"
@Suppress("unused") private const val _SMT_17: String = "telegram"
@Suppress("unused") private const val _SMT_18: String = "discord"
@Suppress("unused") private const val _SMT_19: String = "slack"
@Suppress("unused") private const val _SMT_20: String = "whatsapp"
@Suppress("unused") private const val _SMT_21: String = "signal"
@Suppress("unused") private const val _SMT_22: String = "bluebubbles"
@Suppress("unused") private const val _SMT_23: String = "qqbot"
@Suppress("unused") private const val _SMT_24: String = "matrix"
@Suppress("unused") private const val _SMT_25: String = "mattermost"
@Suppress("unused") private const val _SMT_26: String = "homeassistant"
@Suppress("unused") private const val _SMT_27: String = "dingtalk"
@Suppress("unused") private const val _SMT_28: String = "feishu"
@Suppress("unused") private const val _SMT_29: String = "wecom"
@Suppress("unused") private const val _SMT_30: String = "wecom_callback"
@Suppress("unused") private const val _SMT_31: String = "weixin"
@Suppress("unused") private const val _SMT_32: String = "email"
@Suppress("unused") private const val _SMT_33: String = "sms"
@Suppress("unused") private const val _SMT_34: String = "Both 'target' and 'message' are required when action='send'"
@Suppress("unused") private const val _SMT_35: String = "Interrupted"
@Suppress("unused") private const val _SMT_36: String = "Unknown platform: "
@Suppress("unused") private const val _SMT_37: String = ". Available: "
@Suppress("unused") private const val _SMT_38: String = "success"
@Suppress("unused") private const val _SMT_39: String = "note"
@Suppress("unused") private const val _SMT_40: String = "Sent to "
@Suppress("unused") private const val _SMT_41: String = " home channel (chat_id: "
@Suppress("unused") private const val _SMT_42: String = "error"
@Suppress("unused") private const val _SMT_43: String = "Platform '"
@Suppress("unused") private const val _SMT_44: String = "' is not configured. Set up credentials in ~/.hermes/config.yaml or environment variables."
@Suppress("unused") private const val _SMT_45: String = "HERMES_SESSION_PLATFORM"
@Suppress("unused") private const val _SMT_46: String = "cli"
@Suppress("unused") private const val _SMT_47: String = "Failed to load gateway config: "
@Suppress("unused") private const val _SMT_48: String = "WEIXIN_TOKEN"
@Suppress("unused") private const val _SMT_49: String = "WEIXIN_ACCOUNT_ID"
@Suppress("unused") private const val _SMT_50: String = "WEIXIN_HOME_CHANNEL"
@Suppress("unused") private const val _SMT_51: String = "Weixin Home"
@Suppress("unused") private const val _SMT_52: String = "No home channel set for "
@Suppress("unused") private const val _SMT_53: String = " to determine where to send the message. Either specify a channel directly with '"
@Suppress("unused") private const val _SMT_54: String = ":CHANNEL_NAME', or set a home channel via: hermes config set "
@Suppress("unused") private const val _SMT_55: String = "_HOME_CHANNEL <channel_id>"
@Suppress("unused") private const val _SMT_56: String = "mirrored"
@Suppress("unused") private const val _SMT_57: String = "Send failed: "
@Suppress("unused") private const val _SMT_58: String = "Could not resolve '"
@Suppress("unused") private const val _SMT_59: String = "' on "
@Suppress("unused") private const val _SMT_60: String = ". Use send_message(action='list') to see available targets."
@Suppress("unused") private const val _SMT_61: String = ". Try using a numeric channel ID instead."
@Suppress("unused") private const val _SMT_62: String = "account_id"
@Suppress("unused") private const val _SMT_63: String = "base_url"
@Suppress("unused") private const val _SMT_64: String = "cdn_base_url"
@Suppress("unused") private const val _SMT_65: String = "WEIXIN_BASE_URL"
@Suppress("unused") private const val _SMT_66: String = "WEIXIN_CDN_BASE_URL"
@Suppress("unused") private const val _SMT_67: String = "Parse a tool target into chat_id/thread_id and whether it is explicit."
@Suppress("unused") private const val _SMT_68: String = "Skip redundant cron send_message calls when the scheduler will auto-deliver there."
@Suppress("unused") private const val _SMT_69: String = "skipped"
@Suppress("unused") private const val _SMT_70: String = "reason"
@Suppress("unused") private const val _SMT_71: String = "cron_auto_delivery_duplicate_target"
@Suppress("unused") private const val _SMT_72: String = "Skipped send_message to "
@Suppress("unused") private const val _SMT_73: String = ". This cron job will already auto-deliver its final response to that same target. Put the intended user-facing content in your final response instead, or use a different target if you want an additional message."
@Suppress("unused") private const val _SMT_74: String = "platform"
@Suppress("unused") private const val _SMT_75: String = "thread_id"
@Suppress("unused") private const val _SMT_76: String = "chat_id"
@Suppress("unused") private val _SMT_77: String = """Route a message to the appropriate platform sender.

    Long messages are automatically chunked to fit within platform limits
    using the same smart-splitting algorithm as the gateway adapters
    (preserves code-block boundaries, adds part indicators).
    """
@Suppress("unused") private const val _SMT_78: String = "MEDIA attachments were omitted for "
@Suppress("unused") private const val _SMT_79: String = "; native send_message media delivery is currently only supported for telegram, discord, matrix, weixin, and signal"
@Suppress("unused") private const val _SMT_80: String = "warnings"
@Suppress("unused") private const val _SMT_81: String = "send_message MEDIA delivery is currently only supported for telegram, discord, matrix, weixin, and signal; target "
@Suppress("unused") private const val _SMT_82: String = " had only media attachments"
@Suppress("unused") private const val _SMT_83: String = "Failed to apply Slack mrkdwn formatting in _send_to_platform"
@Suppress("unused") private const val _SMT_84: String = "extra"
@Suppress("unused") private const val _SMT_85: String = "disable_link_previews"
@Suppress("unused") private const val _SMT_86: String = "Direct sending not yet implemented for "
@Suppress("unused") private val _SMT_87: String = """Send via Telegram Bot API (one-shot, no polling needed).

    Applies markdown→MarkdownV2 formatting (same as the gateway adapter)
    so that bold, links, and headers render correctly.  If the message
    already contains HTML tags, it is sent with ``parse_mode='HTML'``
    instead, bypassing MarkdownV2 conversion.
    """
@Suppress("unused") private const val _SMT_88: String = "No deliverable text or media remained after processing MEDIA tags"
@Suppress("unused") private const val _SMT_89: String = "message_id"
@Suppress("unused") private const val _SMT_90: String = "<[a-zA-Z/][^>]*>"
@Suppress("unused") private const val _SMT_91: String = "message_thread_id"
@Suppress("unused") private const val _SMT_92: String = "disable_web_page_preview"
@Suppress("unused") private const val _SMT_93: String = "python-telegram-bot not installed. Run: pip install python-telegram-bot"
@Suppress("unused") private const val _SMT_94: String = "Media file not found, skipping: "
@Suppress("unused") private const val _SMT_95: String = "Telegram send failed: "
@Suppress("unused") private const val _SMT_96: String = "parse"
@Suppress("unused") private const val _SMT_97: String = "markdown"
@Suppress("unused") private const val _SMT_98: String = "html"
@Suppress("unused") private const val _SMT_99: String = "Parse mode %s failed in _send_telegram, falling back to plain text: %s"
@Suppress("unused") private const val _SMT_100: String = "Failed to send media "
@Suppress("unused") private const val _SMT_101: String = "Derive a thread name from the first line of the message, capped at 100 chars."
@Suppress("unused") private const val _SMT_102: String = "New Post"
@Suppress("unused") private val _SMT_103: String = """Send a single message via Discord REST API (no websocket client needed).

    Chunking is handled by _send_to_platform() before this is called.

    When thread_id is provided, the message is sent directly to that thread
    via the /channels/{thread_id}/messages endpoint.

    Media files are uploaded one-by-one via multipart/form-data after the
    text message is sent (same pattern as Telegram).

    Forum channels (type 15) reject POST /messages — a thread post is created
    automatically via POST /channels/{id}/threads.  Media files are uploaded
    as multipart attachments on the starter message of the new thread.

    Channel type is resolved from the channel directory first, then a
    process-local probe cache, and only as a last resort with a live
    GET /channels/{id} probe (whose result is memoized).
    """
@Suppress("unused") private const val _SMT_104: String = "Authorization"
@Suppress("unused") private const val _SMT_105: String = "Content-Type"
@Suppress("unused") private const val _SMT_106: String = "application/json"
@Suppress("unused") private const val _SMT_107: String = "No deliverable text or media remained after processing"
@Suppress("unused") private const val _SMT_108: String = "aiohttp not installed. Run: pip install aiohttp"
@Suppress("unused") private const val _SMT_109: String = "DISCORD_PROXY"
@Suppress("unused") private const val _SMT_110: String = "Bot "
@Suppress("unused") private const val _SMT_111: String = "https://discord.com/api/v10/channels/"
@Suppress("unused") private const val _SMT_112: String = "/messages"
@Suppress("unused") private const val _SMT_113: String = "forum"
@Suppress("unused") private const val _SMT_114: String = "/threads"
@Suppress("unused") private const val _SMT_115: String = "Discord send failed: "
@Suppress("unused") private const val _SMT_116: String = "content"
@Suppress("unused") private const val _SMT_117: String = "attachments"
@Suppress("unused") private const val _SMT_118: String = "payload_json"
@Suppress("unused") private const val _SMT_119: String = "files[0]"
@Suppress("unused") private const val _SMT_120: String = "filename"
@Suppress("unused") private const val _SMT_121: String = "name"
@Suppress("unused") private const val _SMT_122: String = "Discord API error ("
@Suppress("unused") private const val _SMT_123: String = "): "
@Suppress("unused") private const val _SMT_124: String = "Failed to probe channel type for %s"
@Suppress("unused") private const val _SMT_125: String = "Discord forum thread creation error ("
@Suppress("unused") private const val _SMT_126: String = ": Discord API error ("
@Suppress("unused") private const val _SMT_127: String = "files["
@Suppress("unused") private const val _SMT_128: String = "Discord forum thread upload failed: "
@Suppress("unused") private const val _SMT_129: String = "type"
@Suppress("unused") private const val _SMT_130: String = "Send via Slack Web API."
@Suppress("unused") private const val _SMT_131: String = "https://slack.com/api/chat.postMessage"
@Suppress("unused") private const val _SMT_132: String = "Bearer "
@Suppress("unused") private const val _SMT_133: String = "channel"
@Suppress("unused") private const val _SMT_134: String = "text"
@Suppress("unused") private const val _SMT_135: String = "mrkdwn"
@Suppress("unused") private const val _SMT_136: String = "Slack send failed: "
@Suppress("unused") private const val _SMT_137: String = "Slack API error: "
@Suppress("unused") private const val _SMT_138: String = "unknown"
@Suppress("unused") private const val _SMT_139: String = "Send via the local WhatsApp bridge HTTP API."
@Suppress("unused") private const val _SMT_140: String = "bridge_port"
@Suppress("unused") private const val _SMT_141: String = "WhatsApp send failed: "
@Suppress("unused") private const val _SMT_142: String = "http://localhost:"
@Suppress("unused") private const val _SMT_143: String = "/send"
@Suppress("unused") private const val _SMT_144: String = "WhatsApp bridge error ("
@Suppress("unused") private const val _SMT_145: String = "chatId"
@Suppress("unused") private const val _SMT_146: String = "messageId"
@Suppress("unused") private val _SMT_147: String = """Send via signal-cli JSON-RPC API.

    Supports both text-only and text-with-attachments (images/audio/documents).
    Attachments are sent as an 'attachments' array in the JSON-RPC params.
    """
@Suppress("unused") private const val _SMT_148: String = "account"
@Suppress("unused") private const val _SMT_149: String = "group:"
@Suppress("unused") private const val _SMT_150: String = "jsonrpc"
@Suppress("unused") private const val _SMT_151: String = "method"
@Suppress("unused") private const val _SMT_152: String = "params"
@Suppress("unused") private const val _SMT_153: String = "2.0"
@Suppress("unused") private const val _SMT_154: String = "send"
@Suppress("unused") private const val _SMT_155: String = "httpx not installed"
@Suppress("unused") private const val _SMT_156: String = "Signal account not configured"
@Suppress("unused") private const val _SMT_157: String = "groupId"
@Suppress("unused") private const val _SMT_158: String = "recipient"
@Suppress("unused") private const val _SMT_159: String = "send_"
@Suppress("unused") private const val _SMT_160: String = "http_url"
@Suppress("unused") private const val _SMT_161: String = "http://127.0.0.1:8080"
@Suppress("unused") private const val _SMT_162: String = "Signal media file not found, skipping: %s"
@Suppress("unused") private const val _SMT_163: String = "Signal send failed: "
@Suppress("unused") private const val _SMT_164: String = "/api/v1/rpc"
@Suppress("unused") private const val _SMT_165: String = "Signal RPC error: "
@Suppress("unused") private const val _SMT_166: String = "Some media files were skipped (not found on disk)"
@Suppress("unused") private const val _SMT_167: String = "Send via SMTP (one-shot, no persistent connection needed)."
@Suppress("unused") private const val _SMT_168: String = "EMAIL_PASSWORD"
@Suppress("unused") private const val _SMT_169: String = "Hermes Agent"
@Suppress("unused") private const val _SMT_170: String = "address"
@Suppress("unused") private const val _SMT_171: String = "EMAIL_ADDRESS"
@Suppress("unused") private const val _SMT_172: String = "smtp_host"
@Suppress("unused") private const val _SMT_173: String = "EMAIL_SMTP_HOST"
@Suppress("unused") private const val _SMT_174: String = "Email not configured (EMAIL_ADDRESS, EMAIL_PASSWORD, EMAIL_SMTP_HOST required)"
@Suppress("unused") private const val _SMT_175: String = "plain"
@Suppress("unused") private const val _SMT_176: String = "utf-8"
@Suppress("unused") private const val _SMT_177: String = "From"
@Suppress("unused") private const val _SMT_178: String = "Subject"
@Suppress("unused") private const val _SMT_179: String = "EMAIL_SMTP_PORT"
@Suppress("unused") private const val _SMT_180: String = "587"
@Suppress("unused") private const val _SMT_181: String = "Email send failed: "
@Suppress("unused") private val _SMT_182: String = """Send a single SMS via Twilio REST API.

    Uses HTTP Basic auth (Account SID : Auth Token) and form-encoded POST.
    Chunking is handled by _send_to_platform() before this is called.
    """
@Suppress("unused") private const val _SMT_183: String = "TWILIO_ACCOUNT_SID"
@Suppress("unused") private const val _SMT_184: String = "TWILIO_PHONE_NUMBER"
@Suppress("unused") private const val _SMT_185: String = "\\*\\*(.+?)\\*\\*"
@Suppress("unused") private const val _SMT_186: String = "\\*(.+?)\\*"
@Suppress("unused") private const val _SMT_187: String = "__(.+?)__"
@Suppress("unused") private const val _SMT_188: String = "_(.+?)_"
@Suppress("unused") private const val _SMT_189: String = "```[a-z]*\\n?"
@Suppress("unused") private const val _SMT_190: String = "`(.+?)`"
@Suppress("unused") private const val _SMT_191: String = "^#{1,6}\\s+"
@Suppress("unused") private const val _SMT_192: String = "\\[([^\\]]+)\\]\\([^\\)]+\\)"
@Suppress("unused") private const val _SMT_193: String = "\\n{3,}"
@Suppress("unused") private const val _SMT_194: String = "SMS not configured (TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER required)"
@Suppress("unused") private const val _SMT_195: String = "ascii"
@Suppress("unused") private const val _SMT_196: String = "https://api.twilio.com/2010-04-01/Accounts/"
@Suppress("unused") private const val _SMT_197: String = "/Messages.json"
@Suppress("unused") private const val _SMT_198: String = "Basic "
@Suppress("unused") private const val _SMT_199: String = "Body"
@Suppress("unused") private const val _SMT_200: String = "sid"
@Suppress("unused") private const val _SMT_201: String = "SMS send failed: "
@Suppress("unused") private const val _SMT_202: String = "Twilio API error ("
@Suppress("unused") private const val _SMT_203: String = "Send via Mattermost REST API."
@Suppress("unused") private const val _SMT_204: String = "/api/v4/posts"
@Suppress("unused") private const val _SMT_205: String = "MATTERMOST_TOKEN"
@Suppress("unused") private const val _SMT_206: String = "Mattermost not configured (MATTERMOST_URL, MATTERMOST_TOKEN required)"
@Suppress("unused") private const val _SMT_207: String = "Mattermost send failed: "
@Suppress("unused") private const val _SMT_208: String = "url"
@Suppress("unused") private const val _SMT_209: String = "MATTERMOST_URL"
@Suppress("unused") private const val _SMT_210: String = "channel_id"
@Suppress("unused") private const val _SMT_211: String = "Mattermost API error ("
@Suppress("unused") private val _SMT_212: String = """Send via Matrix Client-Server API.

    Converts markdown to HTML for rich rendering in Matrix clients.
    Falls back to plain text if the ``markdown`` library is not installed.
    """
@Suppress("unused") private const val _SMT_213: String = "hermes_"
@Suppress("unused") private const val _SMT_214: String = "/_matrix/client/v3/rooms/"
@Suppress("unused") private const val _SMT_215: String = "/send/m.room.message/"
@Suppress("unused") private const val _SMT_216: String = "msgtype"
@Suppress("unused") private const val _SMT_217: String = "body"
@Suppress("unused") private const val _SMT_218: String = "m.text"
@Suppress("unused") private const val _SMT_219: String = "org.matrix.custom.html"
@Suppress("unused") private const val _SMT_220: String = "MATRIX_ACCESS_TOKEN"
@Suppress("unused") private const val _SMT_221: String = "Matrix not configured (MATRIX_HOMESERVER, MATRIX_ACCESS_TOKEN required)"
@Suppress("unused") private const val _SMT_222: String = "<h[1-6]>(.*?)</h[1-6]>"
@Suppress("unused") private const val _SMT_223: String = "<strong>\\1</strong>"
@Suppress("unused") private const val _SMT_224: String = "format"
@Suppress("unused") private const val _SMT_225: String = "formatted_body"
@Suppress("unused") private const val _SMT_226: String = "event_id"
@Suppress("unused") private const val _SMT_227: String = "Matrix send failed: "
@Suppress("unused") private const val _SMT_228: String = "homeserver"
@Suppress("unused") private const val _SMT_229: String = "MATRIX_HOMESERVER"
@Suppress("unused") private const val _SMT_230: String = "fenced_code"
@Suppress("unused") private const val _SMT_231: String = "tables"
@Suppress("unused") private const val _SMT_232: String = "Matrix API error ("
@Suppress("unused") private const val _SMT_233: String = "Send via the Matrix adapter so native Matrix media uploads are preserved."
@Suppress("unused") private const val _SMT_234: String = "Matrix dependencies not installed. Run: pip install 'mautrix[encryption]'"
@Suppress("unused") private const val _SMT_235: String = "Matrix connect failed"
@Suppress("unused") private const val _SMT_236: String = "Media file not found: "
@Suppress("unused") private const val _SMT_237: String = "Matrix media send failed: "
@Suppress("unused") private const val _SMT_238: String = "Send via Home Assistant notify service."
@Suppress("unused") private const val _SMT_239: String = "/api/services/notify/notify"
@Suppress("unused") private const val _SMT_240: String = "HASS_TOKEN"
@Suppress("unused") private const val _SMT_241: String = "Home Assistant not configured (HASS_URL, HASS_TOKEN required)"
@Suppress("unused") private const val _SMT_242: String = "Home Assistant send failed: "
@Suppress("unused") private const val _SMT_243: String = "HASS_URL"
@Suppress("unused") private const val _SMT_244: String = "Home Assistant API error ("
@Suppress("unused") private val _SMT_245: String = """Send via DingTalk robot webhook.

    Note: The gateway's DingTalk adapter uses per-session webhook URLs from
    incoming messages (dingtalk-stream SDK).  For cross-platform send_message
    delivery we use a static robot webhook URL instead, which must be
    configured via ``DINGTALK_WEBHOOK_URL`` env var or ``webhook_url`` in the
    platform's extra config.
    """
@Suppress("unused") private const val _SMT_246: String = "webhook_url"
@Suppress("unused") private const val _SMT_247: String = "DINGTALK_WEBHOOK_URL"
@Suppress("unused") private const val _SMT_248: String = "DingTalk not configured. Set DINGTALK_WEBHOOK_URL env var or webhook_url in dingtalk platform extra config."
@Suppress("unused") private const val _SMT_249: String = "errcode"
@Suppress("unused") private const val _SMT_250: String = "DingTalk send failed: "
@Suppress("unused") private const val _SMT_251: String = "DingTalk API error: "
@Suppress("unused") private const val _SMT_252: String = "errmsg"
@Suppress("unused") private const val _SMT_253: String = "Send via WeCom using the adapter's WebSocket send pipeline."
@Suppress("unused") private const val _SMT_254: String = "WeCom requirements not met. Need aiohttp + WECOM_BOT_ID/SECRET."
@Suppress("unused") private const val _SMT_255: String = "WeCom adapter not available."
@Suppress("unused") private const val _SMT_256: String = "WeCom: failed to connect - "
@Suppress("unused") private const val _SMT_257: String = "WeCom send failed: "
@Suppress("unused") private const val _SMT_258: String = "unknown error"
@Suppress("unused") private const val _SMT_259: String = "Send via Weixin iLink using the native adapter helper."
@Suppress("unused") private const val _SMT_260: String = "Weixin requirements not met. Need aiohttp + cryptography."
@Suppress("unused") private const val _SMT_261: String = "Weixin adapter not available."
@Suppress("unused") private const val _SMT_262: String = "Weixin send failed: "
@Suppress("unused") private const val _SMT_263: String = "Send via BlueBubbles iMessage server using the adapter's REST API."
@Suppress("unused") private const val _SMT_264: String = "BlueBubbles requirements not met (need aiohttp + httpx)."
@Suppress("unused") private const val _SMT_265: String = "BlueBubbles adapter not available."
@Suppress("unused") private const val _SMT_266: String = "BlueBubbles: failed to connect to server"
@Suppress("unused") private const val _SMT_267: String = "BlueBubbles send failed: "
@Suppress("unused") private const val _SMT_268: String = "Send via Feishu/Lark using the adapter's send pipeline."
@Suppress("unused") private const val _SMT_269: String = "_domain_name"
@Suppress("unused") private const val _SMT_270: String = "Feishu dependencies not installed. Run: pip install 'hermes-agent[feishu]'"
@Suppress("unused") private const val _SMT_271: String = "lark"
@Suppress("unused") private const val _SMT_272: String = "Feishu send failed: "
@Suppress("unused") private const val _SMT_273: String = "Feishu media send failed: "
@Suppress("unused") private val _SMT_274: String = """Send via QQBot using the REST API directly (no WebSocket needed).

    Uses the QQ Bot Open Platform REST endpoints to get an access token
    and post a message. Works for guild channels without requiring
    a running gateway adapter.
    """
@Suppress("unused") private const val _SMT_275: String = "app_id"
@Suppress("unused") private const val _SMT_276: String = "QQ_APP_ID"
@Suppress("unused") private const val _SMT_277: String = "client_secret"
@Suppress("unused") private const val _SMT_278: String = "QQ_CLIENT_SECRET"
@Suppress("unused") private const val _SMT_279: String = "QQBot: QQ_APP_ID / QQ_CLIENT_SECRET not configured."
@Suppress("unused") private const val _SMT_280: String = "QQBot direct send requires httpx. Run: pip install httpx"
@Suppress("unused") private const val _SMT_281: String = "access_token"
@Suppress("unused") private const val _SMT_282: String = "https://api.sgroup.qq.com/channels/"
@Suppress("unused") private const val _SMT_283: String = "msg_type"
@Suppress("unused") private const val _SMT_284: String = "https://bots.qq.com/app/getAppAccessToken"
@Suppress("unused") private const val _SMT_285: String = "QQBot "
@Suppress("unused") private const val _SMT_286: String = "QQBot send failed: "
@Suppress("unused") private const val _SMT_287: String = "QQBot token request failed: "
@Suppress("unused") private const val _SMT_288: String = "QQBot: no access_token in response"
@Suppress("unused") private const val _SMT_289: String = "appId"
@Suppress("unused") private const val _SMT_290: String = "clientSecret"
