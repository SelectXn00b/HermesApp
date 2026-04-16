package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * App Chat platform adapter — in-process bridge between the app's Chat UI
 * and the Hermes GatewayRunner. No WebSocket/HTTP; messages are pushed
 * and delivered via Kotlin Flows.
 *
 * Created for AndroidForClaw (方案 A: Hermes directly serves chat UI).
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Outgoing message from GatewayRunner to the Chat UI.
 */
data class ChatResponse(
    val text: String,
    val chatId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isDone: Boolean = true,
    val error: String? = null
)

/**
 * In-process adapter: Chat UI pushes messages in, GatewayRunner pushes responses out.
 */
class AppChatAdapter(
    context: Context,
    config: PlatformConfig = PlatformConfig(
        platform = Platform.APP_CHAT,
        enabled = true,
        dmPolicy = "open",
        groupPolicy = "open"
    )
) : BasePlatformAdapter(config, Platform.APP_CHAT) {

    companion object {
        private const val TAG = "AppChatAdapter"
        private val messageIdCounter = AtomicLong(0)
    }

    // ── Outbound flow: GatewayRunner → Chat UI ──────────────────────
    private val _responses = MutableSharedFlow<ChatResponse>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<ChatResponse> = _responses.asSharedFlow()

    // ── Lifecycle ───────────────────────────────────────────────────

    override suspend fun connect(): Boolean {
        markConnected()
        Log.i(TAG, "AppChatAdapter connected (in-process)")
        return true
    }

    override suspend fun disconnect() {
        markDisconnected()
        Log.i(TAG, "AppChatAdapter disconnected")
    }

    // ── Inbound: Chat UI → GatewayRunner ────────────────────────────

    /**
     * Push a user message into the GatewayRunner.
     * Called by the Chat UI (ViewModel or Activity).
     *
     * @param text     User message text.
     * @param chatId   Session identifier (e.g. "local", or a user id).
     * @param userId   Optional user id.
     * @param userName Optional user name.
     */
    suspend fun pushMessage(
        text: String,
        chatId: String = "local",
        userId: String = "app_user",
        userName: String = "You"
    ) {
        val source = buildSource(
            chatId = chatId,
            chatName = "App Chat",
            chatType = "dm",
            userId = userId,
            userName = userName
        )

        val event = MessageEvent(
            text = text,
            messageType = MessageType.TEXT,
            source = source,
            message_id = "app_${messageIdCounter.incrementAndGet()}",
            timestamp = Instant.now()
        )

        Log.d(TAG, "Pushing message: ${text.take(80)}")
        handleMessage(event)
    }

    // ── Outbound: GatewayRunner → Chat UI ───────────────────────────

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?
    ): SendResult {
        val msgId = "app_resp_${messageIdCounter.incrementAndGet()}"
        Log.d(TAG, "Sending response to chat=$chatId: ${content.take(80)}")

        val response = ChatResponse(
            text = content,
            chatId = chatId,
            isDone = true
        )
        val emitted = _responses.emit(response)

        return SendResult(success = true, messageId = msgId)
    }

    override suspend fun sendImage(
        chatId: String,
        imageUrl: String,
        caption: String?,
        replyTo: String?
    ): SendResult {
        val text = if (caption != null) "$caption\n[Image: $imageUrl]" else "[Image: $imageUrl]"
        return send(chatId, text, replyTo)
    }

    override suspend fun sendDocument(
        chatId: String,
        fileUrl: String,
        fileName: String?,
        caption: String?,
        replyTo: String?
    ): SendResult {
        val name = fileName ?: fileUrl.substringAfterLast("/")
        val text = if (caption != null) "$caption\n[File: $name]" else "[File: $name]"
        return send(chatId, text, replyTo)
    }

    override suspend fun sendTyping(chatId: String, metadata: JSONObject?) {
        _responses.emit(ChatResponse(text = "", chatId = chatId, isStreaming = true, isDone = false))
    }

    // ── Streaming support (optional, for future use) ────────────────

    /**
     * Emit a streaming chunk to the Chat UI.
     * Can be called by AgentLoop during streaming LLM responses.
     */
    suspend fun emitChunk(chatId: String, chunk: String) {
        _responses.emit(ChatResponse(
            text = chunk,
            chatId = chatId,
            isStreaming = true,
            isDone = false
        ))
    }

    /**
     * Signal that streaming is complete for a given chat.
     */
    suspend fun emitDone(chatId: String) {
        _responses.emit(ChatResponse(
            text = "",
            chatId = chatId,
            isStreaming = false,
            isDone = true
        ))
    }

    /**
     * Emit an error to the Chat UI.
     */
    suspend fun emitError(chatId: String, error: String) {
        _responses.emit(ChatResponse(
            text = "",
            chatId = chatId,
            isDone = true,
            error = error
        ))
    }
}
