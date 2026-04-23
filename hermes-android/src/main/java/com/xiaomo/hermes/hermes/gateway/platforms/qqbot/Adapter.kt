package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

/**
 * QQ Bot platform adapter — 1:1 对齐 gateway/platforms/qqbot/adapter.py。
 *
 * Android 没有 QQ Bot SDK（SSL WebSocket + 腾讯鉴权），本文件保留全部
 * 方法签名用于 Python → Kotlin 对齐；运行期全部返回安全回退。当需要
 * 真实 QQ Bot 通道时，由上层在真实后端（服务器侧 Python）完成执行，
 * 客户端通过 hermes gateway 连接。
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter
import com.xiaomo.hermes.hermes.gateway.platforms.SendResult
import org.json.JSONObject
import java.time.LocalDateTime

class QQCloseError(val code: Int?, val reason: String = "") :
    RuntimeException("WebSocket closed (code=$code, reason=$reason)")

fun checkQqRequirements(): Boolean {
    // Android 端无法运行 QQ Bot SDK；由服务端执行，始终返回 false。
    return false
}

fun _coerceList(value: Any?): List<String> {
    return when (value) {
        null -> emptyList()
        is String -> if (value.isEmpty()) emptyList() else listOf(value)
        is List<*> -> value.mapNotNull { it?.toString() }
        is Array<*> -> value.mapNotNull { it?.toString() }
        else -> listOf(value.toString())
    }
}

class QQAdapter(
    context: Context,
    config: PlatformConfig
) : BasePlatformAdapter(config, Platform.QQBOT) {

    companion object { private const val _TAG = "QQAdapter" }

    private fun _logTag(): String = _TAG

    private fun _failPending(reason: String) {
        Log.w(_TAG, "_failPending: $reason (Android stub)")
    }

    override fun name(): String = "qqbot"

    private suspend fun _cleanup() { /* Android stub */ }

    private suspend fun _ensureToken(): String = ""

    private suspend fun _getGatewayUrl(): String = ""

    private suspend fun _openWs(gatewayUrl: String) { /* Android stub */ }

    private suspend fun _listenLoop() { /* Android stub */ }

    private suspend fun _reconnect(backoffIdx: Int): Boolean = false

    private suspend fun _readEvents() { /* Android stub */ }

    private suspend fun _heartbeatLoop() { /* Android stub */ }

    private suspend fun _sendIdentify() { /* Android stub */ }

    private suspend fun _sendResume() { /* Android stub */ }

    private fun _createTask(coro: suspend () -> Unit) {
        // Android stub — Python spawns via asyncio.create_task.
    }

    private fun _dispatchPayload(payload: Map<String, Any?>) { /* Android stub */ }

    private fun _handleReady(d: Any?) { /* Android stub */ }

    private fun _parseJson(raw: Any?): Map<String, Any?>? {
        if (raw !is String) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            com.xiaomo.hermes.hermes.gson.fromJson(raw, Map::class.java) as? Map<String, Any?>
        } catch (_: Throwable) {
            null
        }
    }

    private fun _nextMsgSeq(msgId: String): Int = 0

    suspend fun handleMessage(event: Any?) { /* Android stub */ }

    private suspend fun _onMessage(eventType: String, d: Any?) { /* Android stub */ }

    private suspend fun _handleC2cMessage(d: Any?) { /* Android stub */ }

    private suspend fun _handleGroupMessage(d: Any?) { /* Android stub */ }

    private suspend fun _handleGuildMessage(d: Any?) { /* Android stub */ }

    private suspend fun _handleDmMessage(d: Any?) { /* Android stub */ }

    private fun _detectMessageType(
        mediaUrls: List<String>,
        mediaTypes: List<String>
    ): String = ""

    private suspend fun _processAttachments(
        attachments: List<Map<String, Any?>>,
        chatType: String,
        chatId: String
    ): List<Map<String, Any?>> = emptyList()

    private suspend fun _downloadAndCache(url: String, contentType: String): String? = null

    private fun _isVoiceContentType(contentType: String, filename: String): Boolean {
        val ct = contentType.lowercase()
        val fn = filename.lowercase()
        if ("audio" in ct) return true
        return fn.endsWith(".silk") || fn.endsWith(".amr") || fn.endsWith(".wav") ||
            fn.endsWith(".mp3") || fn.endsWith(".opus")
    }

    private fun _qqMediaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Hermes QQ Bot/android"
    )

    @Suppress("UNUSED_PARAMETER")
    private suspend fun _sttVoiceAttachment(
        url: String,
        contentType: String,
        filename: String,
        asrReferText: String? = null,
        voiceWavUrl: String? = null,
    ): String? = null

    private suspend fun _convertAudioToWavFile(
        srcPath: String,
        wavPath: String
    ): String? = null

    private fun _guessExtFromData(data: ByteArray): String {
        if (data.size < 4) return ""
        if (data[0] == 0x02.toByte() && data.size > 9 && data.copyOfRange(1, 9)
                .contentEquals("#!SILK".toByteArray() + byteArrayOf(0, 0))) return "silk"
        if (data[0] == 0x02.toByte() && data.size > 10) return "silk"
        if (data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()) return "mp3"
        if (data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()) return "wav"
        return ""
    }

    private fun _looksLikeSilk(data: ByteArray): Boolean {
        if (data.size < 10) return false
        return (data[0] == 0x02.toByte() && data[1] == '#'.code.toByte()) ||
            String(data.copyOfRange(1, minOf(10, data.size))).startsWith("#!SILK")
    }

    private suspend fun _convertSilkToWav(srcPath: String, wavPath: String): String? = null

    private suspend fun _convertRawToWav(audioData: ByteArray, wavPath: String): String? = null

    private suspend fun _convertFfmpegToWav(srcPath: String, wavPath: String): String? = null

    private fun _resolveSttConfig(): Map<String, String>? = null

    private suspend fun _callStt(wavPath: String): String? = null

    private suspend fun _convertAudioToWav(
        audioData: ByteArray,
        contentType: String,
        filename: String
    ): String? = null

    private suspend fun _apiRequest(
        method: String,
        path: String,
        body: Any? = null
    ): Map<String, Any?>? = null

    private suspend fun _uploadMedia(
        chatType: String,
        chatId: String,
        fileType: Int,
        url: String? = null,
        fileData: ByteArray? = null
    ): Map<String, Any?>? = null

    private suspend fun _waitForReconnection(): Boolean = false

    override suspend fun connect(): Boolean {
        Log.w(_TAG, "QQ Bot adapter is a stub — not implemented on Android")
        return false
    }

    override suspend fun disconnect() { /* Android stub */ }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?
    ): SendResult = SendResult(success = false, error = "QQ Bot not supported on Android")

    private suspend fun _sendChunk(
        chatId: String,
        chunk: String,
        replyTo: String?,
        metadata: Map<String, Any?>?
    ): SendResult = SendResult(success = false, error = "stub")

    private suspend fun _sendC2cText(
        openId: String,
        content: String,
        replyTo: String?
    ): Map<String, Any?>? = null

    private suspend fun _sendGroupText(
        groupId: String,
        content: String,
        replyTo: String?
    ): Map<String, Any?>? = null

    private suspend fun _sendGuildText(
        channelId: String,
        content: String,
        replyTo: String?
    ): Map<String, Any?>? = null

    private fun _buildTextBody(
        content: String,
        replyTo: String?,
        msgSeq: Int
    ): Map<String, Any?> = mapOf(
        "content" to content,
        "msg_id" to (replyTo ?: ""),
        "msg_seq" to msgSeq
    )

    suspend fun sendImage(
        chatId: String,
        imageUrl: String,
        replyTo: String? = null
    ): SendResult = SendResult(success = false, error = "stub")

    suspend fun sendImageFile(
        chatId: String,
        imagePath: String,
        replyTo: String? = null
    ): SendResult = SendResult(success = false, error = "stub")

    suspend fun sendVoice(
        chatId: String,
        voicePath: String,
        replyTo: String? = null
    ): SendResult = SendResult(success = false, error = "stub")

    suspend fun sendVideo(
        chatId: String,
        videoPath: String,
        replyTo: String? = null
    ): SendResult = SendResult(success = false, error = "stub")

    suspend fun sendDocument(
        chatId: String,
        documentPath: String,
        replyTo: String? = null
    ): SendResult = SendResult(success = false, error = "stub")

    private suspend fun _sendMedia(
        chatId: String,
        mediaType: Int,
        source: String,
        replyTo: String?
    ): SendResult = SendResult(success = false, error = "stub")

    private suspend fun _loadMedia(source: String): Pair<ByteArray?, String?> = null to null

    suspend fun sendTyping(chatId: String, metadata: Map<String, Any?>? = null) {
        // no-op on Android
    }

    fun formatMessage(content: String): String = content

    override suspend fun getChatInfo(chatId: String): JSONObject =
        JSONObject().put("id", chatId)

    private fun _isUrl(source: String): Boolean {
        val s = source.lowercase()
        return s.startsWith("http://") || s.startsWith("https://")
    }

    private fun _guessChatType(chatId: String): String {
        if (chatId.startsWith("c2c_")) return "c2c"
        if (chatId.startsWith("group_")) return "group"
        if (chatId.startsWith("guild_")) return "guild"
        if (chatId.startsWith("dm_")) return "dm"
        return "c2c"
    }

    private fun _stripAtMention(content: String): String {
        return content.replace(Regex("<@!?[0-9]+>"), "").trim()
    }

    private fun _isDmAllowed(userId: String): Boolean = true

    private fun _isGroupAllowed(groupId: String, userId: String): Boolean = true

    private fun _entryMatches(entries: List<String>, target: String): Boolean {
        if (entries.isEmpty()) return false
        if ("*" in entries) return true
        return target in entries
    }

    private fun _parseQqTimestamp(raw: String): LocalDateTime {
        return try {
            LocalDateTime.parse(raw)
        } catch (_: Throwable) {
            LocalDateTime.now()
        }
    }

    private fun _isDuplicate(msgId: String): Boolean = false
}
