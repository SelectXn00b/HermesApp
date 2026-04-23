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
