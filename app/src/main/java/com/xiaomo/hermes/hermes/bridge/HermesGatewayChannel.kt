package com.xiaomo.hermes.hermes.bridge

/**
 * HermesGatewayChannel — implements IGatewayChannel by routing requests
 * through the Hermes GatewayRunner's AppChatAdapter + AgentLoop.
 *
 * This replaces LocalGatewayChannel when Hermes mode is active.
 *
 * Event format aligned with OpenClaw WebSocket gateway:
 *   event = "chat", payload = { state: "delta"|"final"|"error", runId, sessionKey, ... }
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
import com.xiaomo.hermes.hermes.gateway.platforms.AppChat
import com.xiaomo.hermes.hermes.gateway.platforms.ChatResponse
import com.xiaomo.base.IGatewayChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class HermesGatewayChannel(
    private val context: Context,
    private val gatewayRunner: GatewayRunner
) : IGatewayChannel {

    companion object {
        private const val TAG = "HermesGatewayChannel"
    }

    private var _eventListener: ((event: String, payloadJson: String) -> Unit)? = null
    private val runCounter = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var collectJob: Job? = null
    /** Track active runIds so we can map responses back to pending runs */
    private val activeRunIds = mutableMapOf<String, String>() // chatId → runId
    /** In-memory chat history for chat.history RPC */
    private val chatHistory = mutableListOf<JSONObject>()
    private val historyLock = Any()

    override fun setEventListener(listener: ((event: String, payloadJson: String) -> Unit)?) {
        Log.d(TAG, "setEventListener: listener=${if (listener != null) "non-null" else "null"}, was=${if (_eventListener != null) "non-null" else "null"}")
        _eventListener = listener
        if (listener != null) {
            startCollecting()
        } else {
            collectJob?.cancel()
            collectJob = null
        }
    }

    /**
     * Start collecting responses from AppChatAdapter and forwarding them
     * as events to the ChatController.
     */
    private fun startCollecting() {
        if (collectJob?.isActive == true) {
            Log.d(TAG, "startCollecting: already active, skipping")
            return
        }
        val adapter = gatewayRunner.getAppChatAdapter()
        if (adapter == null) {
            Log.w(TAG, "startCollecting: adapter is null")
            return
        }
        Log.d(TAG, "startCollecting: adapter found, launching collector")
        collectJob = scope.launch {
            Log.d(TAG, "Collector coroutine started")
            adapter.responses.collect { response ->
                Log.d(TAG, "Collected response: text=${response.text.take(80)}, chatId=${response.chatId}, isDone=${response.isDone}, error=${response.error}")
                forwardResponse(response)
            }
        }
        Log.d(TAG, "Started collecting AppChatAdapter responses, job=${collectJob}")
    }

    /**
     * Forward a ChatResponse as gateway events that ChatController understands.
     *
     * ChatController expects event="chat" with payload.state ∈ {delta, final, error}.
     */
    private fun forwardResponse(response: ChatResponse) {
        val chatId = response.chatId
        val runId = synchronized(activeRunIds) { activeRunIds[chatId] }
        Log.d(TAG, "forwardResponse: text=${response.text.take(80)}, chatId=$chatId, runId=$runId, isDone=${response.isDone}, error=${response.error}")

        when {
            response.error != null -> {
                // Error → state=error
                val payload = JSONObject().apply {
                    put("state", "error")
                    put("errorMessage", response.error)
                    put("chatId", chatId)
                    if (runId != null) put("runId", runId)
                    if (response.text.isNotEmpty()) put("text", response.text)
                }
                emitEvent("chat", payload.toString())
                synchronized(activeRunIds) { activeRunIds.remove(chatId) }
            }
            response.isStreaming && !response.isDone -> {
                // Streaming delta
                val payload = JSONObject().apply {
                    put("state", "delta")
                    put("chatId", chatId)
                    if (runId != null) put("runId", runId)
                    put("text", response.text)
                    put("isFinal", false)
                }
                emitEvent("chat", payload.toString())
            }
            response.isDone -> {
                // Final message
                if (response.text.isNotEmpty()) {
                    // Store assistant response in history
                    synchronized(historyLock) {
                        chatHistory.add(JSONObject().apply {
                            put("role", "assistant")
                            put("content", org.json.JSONArray().apply {
                                put(JSONObject().apply { put("type", "text"); put("text", response.text) })
                            })
                            put("timestamp", System.currentTimeMillis())
                        })
                    }
                    val payload = JSONObject().apply {
                        put("state", "final")
                        put("chatId", chatId)
                        if (runId != null) put("runId", runId)
                        put("text", response.text)
                        put("isFinal", true)
                    }
                    emitEvent("chat", payload.toString())
                }
                synchronized(activeRunIds) { activeRunIds.remove(chatId) }
            }
        }
    }

    override suspend fun request(method: String, paramsJson: String?, timeoutMs: Long): String {
        Log.d(TAG, "RPC request: $method")

        return when (method) {
            "chat.send" -> handleChatSend(paramsJson)
            "chat.subscribe" -> {
                startCollecting()
                """{"ok":true}"""
            }
            "chat.history" -> {
                synchronized(historyLock) {
                    val msgs = chatHistory.joinToString(",") { it.toString() }
                    """{"ok":true,"messages":[$msgs]}"""
                }
            }
            "health", "health.check" -> """{"ok":true,"status":"hermes","gateways":1}"""
            "config.get" -> """{"ok":true,"config":{}}"""
            "models.list" -> """{"ok":true,"models":[]}"""
            "sessions.list" -> """{"ok":true,"sessions":[]}"""
            "sessions.delete" -> """{"ok":true}"""
            "session.reset" -> """{"ok":true}"""
            "cron.status" -> """{"ok":true,"enabled":false}"""
            "skills.list" -> """{"ok":true,"skills":[]}"""
            "tools.list" -> """{"ok":true,"tools":[]}"""
            "talk.speak" -> """{"ok":true}"""
            else -> {
                Log.w(TAG, "Unknown RPC method: $method")
                """{"ok":false,"error":"Method not supported in Hermes mode: $method"}"""
            }
        }
    }

    /**
     * Handle chat.send — push message into Hermes and return immediately.
     * Response flows back via events (state=delta → final/error).
     */
    private suspend fun handleChatSend(paramsJson: String?): String {
        val params = try {
            JSONObject(paramsJson ?: "{}")
        } catch (_: Exception) {
            JSONObject("{}")
        }

        val message = params.optString("message", "")
        val chatId = params.optString("chatId", params.optString("sessionKey", "local"))
        val runId = "hermes_run_${runCounter.incrementAndGet()}"

        if (message.isBlank()) {
            return """{"ok":false,"error":"Empty message"}"""
        }

        val adapter = gatewayRunner.getAppChatAdapter()
        if (adapter == null) {
            return """{"ok":false,"error":"AppChatAdapter not available"}"""
        }

        // Ensure we're collecting responses
        startCollecting()

        // Track this run so forwardResponse can map it back
        synchronized(activeRunIds) { activeRunIds[chatId] = runId }

        // Store user message in history
        synchronized(historyLock) {
            chatHistory.add(JSONObject().apply {
                put("role", "user")
                put("content", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("type", "text"); put("text", message) })
                })
                put("timestamp", System.currentTimeMillis())
            })
        }

        // Notify ChatController that a run is starting (matches WebSocket gateway format)
        emitEvent("chat", """{"state":"running","runId":"$runId","chatId":"$chatId"}""")

        // Push message — this triggers the agent loop asynchronously
        adapter.pushMessage(text = message, chatId = chatId)

        // Return immediately — responses flow via events
        return """{"ok":true,"runId":"$runId"}"""
    }

    private fun emitEvent(event: String, payloadJson: String) {
        Log.d(TAG, "emitEvent: event=$event, payload=${payloadJson.take(100)}, listener=${_eventListener != null}")
        try {
            _eventListener?.invoke(event, payloadJson)
            Log.d(TAG, "emitEvent: invoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Event listener error: ${e.message}", e)
        }
    }

    override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean = true
}
