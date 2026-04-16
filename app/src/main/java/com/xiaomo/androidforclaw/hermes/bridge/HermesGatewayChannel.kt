package com.xiaomo.androidforclaw.hermes.bridge

/**
 * HermesGatewayChannel — implements IGatewayChannel by routing requests
 * through the Hermes GatewayRunner's AppChatAdapter + AgentLoop.
 *
 * This replaces LocalGatewayChannel when Hermes mode is active.
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.GatewayRunner
import com.xiaomo.androidforclaw.hermes.gateway.platforms.AppChatAdapter
import com.xiaomo.androidforclaw.hermes.gateway.platforms.ChatResponse
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

    override fun setEventListener(listener: ((event: String, payloadJson: String) -> Unit)?) {
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
        if (collectJob?.isActive == true) return
        val adapter = gatewayRunner.getAppChatAdapter()
        if (adapter == null) {
            Log.w(TAG, "Cannot start collecting: adapter is null")
            return
        }
        collectJob = scope.launch {
            adapter.responses.collect { response -> forwardResponse(response) }
        }
        Log.d(TAG, "Started collecting AppChatAdapter responses")
    }

    /**
     * Forward a ChatResponse as gateway events that ChatController understands.
     */
    private fun forwardResponse(response: ChatResponse) {
        val chatId = response.chatId

        when {
            response.error != null -> {
                emitEvent("chat.message", buildPayload(chatId, response.text, isFinal = true))
                emitEvent("chat.run.error", """{"chatId":"$chatId","error":"${response.error}"}""")
            }
            response.isStreaming && !response.isDone -> {
                emitEvent("chat.delta", buildPayload(chatId, response.text, isFinal = false))
            }
            response.isDone -> {
                if (response.text.isNotEmpty()) {
                    emitEvent("chat.message", buildPayload(chatId, response.text, isFinal = true))
                }
                emitEvent("chat.run.complete", """{"chatId":"$chatId"}""")
            }
        }
    }

    private fun buildPayload(chatId: String, text: String, isFinal: Boolean): String {
        return buildString {
            append("""{"chatId":"""")
            append(chatId)
            append("""","text":""")
            append(JSONObject.quote(text))
            append(""","isFinal":$isFinal}""")
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
            "chat.history" -> """{"ok":true,"messages":[]}"""
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
     * Response flows back via events (chat.delta / chat.message / chat.run.complete).
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

        // Notify start
        emitEvent("chat.run.start", """{"runId":"$runId","chatId":"$chatId"}""")

        // Push message — this triggers the agent loop asynchronously
        adapter.pushMessage(text = message, chatId = chatId)

        // Return immediately — responses flow via events
        return """{"ok":true,"runId":"$runId"}"""
    }

    private fun emitEvent(event: String, payloadJson: String) {
        try {
            _eventListener?.invoke(event, payloadJson)
        } catch (e: Exception) {
            Log.e(TAG, "Event listener error: ${e.message}")
        }
    }

    override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean = true
}
