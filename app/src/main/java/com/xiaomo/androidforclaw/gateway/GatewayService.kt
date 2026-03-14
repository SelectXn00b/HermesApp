/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.IOException

/**
 * Gateway Service - WebSocket RPC service
 *
 * Features:
 * - Provide WebSocket connection
 * - RPC interface (agent, agent.wait, health)
 * - Session management
 * - Remote control capability
 *
 * Reference: OpenClaw Gateway architecture
 */
class GatewayService(port: Int = 8765) : NanoWSD(null, port) {  // null = listen on all network interfaces (0.0.0.0)
    
    companion object {
        private const val TAG = "GatewayService"
    }

    private val gson = Gson()
    private val sessions = mutableMapOf<String, GatewaySession>()
    private var agentHandler: AgentHandler? = null

    /**
     * Set Agent handler
     */
    fun setAgentHandler(handler: AgentHandler) {
        this.agentHandler = handler
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return GatewayWebSocket(handshake)
    }

    /**
     * WebSocket connection handling
     */
    inner class GatewayWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        
        private var sessionId: String? = null

        override fun onOpen() {
            sessionId = generateSessionId()
            val session = GatewaySession(sessionId!!, this)
            sessions[sessionId!!] = session
            
            Log.i(TAG, "✅ WebSocket 连接建立: session=$sessionId")
            
            // Send welcome message
            sendMessage(JsonObject().apply {
                addProperty("type", "connected")
                addProperty("sessionId", sessionId)
                addProperty("message", "Welcome to AndroidForClaw Gateway")
            })
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String?,
            initiatedByRemote: Boolean
        ) {
            sessionId?.let { sessions.remove(it) }
            Log.i(TAG, "❌ WebSocket 连接关闭: session=$sessionId, reason=$reason")
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                val text = message.textPayload
                Log.d(TAG, "📥 收到消息: $text")

                val request: RpcRequest = gson.fromJson(text, RpcRequest::class.java)
                handleRpcRequest(request)
                
            } catch (e: Exception) {
                Log.e(TAG, "处理消息失败", e)
                sendError("Invalid request: ${e.message}")
            }
        }

        override fun onPong(pong: WebSocketFrame) {
            // Heartbeat response
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "WebSocket 异常", exception)
        }

        /**
         * Handle RPC request
         */
        private fun handleRpcRequest(request: RpcRequest) {
            when (request.method) {
                "agent" -> handleAgentRequest(request)
                "agent.wait" -> handleAgentWaitRequest(request)
                "health" -> handleHealthRequest(request)
                "session.list" -> handleSessionListRequest(request)
                "session.reset" -> handleSessionResetRequest(request)
                "session.listAll" -> handleSessionListAllRequest(request)
                else -> sendError("Unknown method: ${request.method}")
            }
        }

        /**
         * agent() - Execute Agent task
         */
        private fun handleAgentRequest(request: RpcRequest) {
            val params = request.params ?: run {
                sendError("Missing params")
                return
            }

            val userMessage = params.message ?: run {
                sendError("Missing message")
                return
            }

            val systemPrompt = params.systemPrompt
            val tools = params.tools
            val maxIterations = params.maxIterations ?: 20

            // 🆔 Support specifying sessionId to switch to another channel's session
            // If sessionId is specified in params, use it; otherwise use current WebSocket's sessionId
            val targetSessionId = params.sessionId ?: sessionId!!

            Log.d(TAG, "🆔 [Agent Request] Target Session: $targetSessionId")
            if (params.sessionId != null) {
                Log.d(TAG, "   ↳ Switch to external session: ${params.sessionId}")
            } else {
                Log.d(TAG, "   ↳ Use current WebSocket session")
            }

            // Execute Agent asynchronously
            Thread {
                try {
                    agentHandler?.executeAgent(
                        sessionId = targetSessionId,
                        userMessage = userMessage,
                        systemPrompt = systemPrompt,
                        tools = tools,
                        maxIterations = maxIterations,
                        progressCallback = { progress ->
                            // Send progress update (in new thread to avoid NetworkOnMainThreadException)
                            Thread {
                                try {
                                    sendMessage(JsonObject().apply {
                                        addProperty("type", "progress")
                                        addProperty("requestId", request.id)
                                        add("data", gson.toJsonTree(progress))
                                    })
                                } catch (e: Exception) {
                                    Log.w(TAG, "发送进度失败: ${e.message}")
                                }
                            }.start()
                        },
                        completeCallback = { result ->
                            // Send completion result (in new thread to avoid NetworkOnMainThreadException)
                            Thread {
                                try {
                                    sendResponse(request.id, result)
                                } catch (e: Exception) {
                                    Log.w(TAG, "发送结果失败: ${e.message}")
                                }
                            }.start()
                        }
                    )
                } catch (e: Exception) {
                    sendError("Agent execution failed: ${e.message}", request.id)
                }
            }.start()
        }

        /**
         * agent.wait() - Wait for Agent completion
         */
        private fun handleAgentWaitRequest(request: RpcRequest) {
            val params = request.params ?: run {
                sendError("Missing params")
                return
            }

            val runId = params.runId ?: run {
                sendError("Missing runId")
                return
            }

            // TODO: implement agent.wait logic
            sendResponse(request.id, mapOf(
                "status" to "completed",
                "runId" to runId
            ))
        }

        /**
         * health() - Health check
         */
        private fun handleHealthRequest(request: RpcRequest) {
            sendResponse(request.id, mapOf(
                "status" to "healthy",
                "timestamp" to System.currentTimeMillis(),
                "sessions" to sessions.size
            ))
        }

        /**
         * session.list() - List all sessions (including those created by channels)
         */
        private fun handleSessionListRequest(request: RpcRequest) {
            try {
                val sessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
                if (sessionManager == null) {
                    // If SessionManager is not initialized, only return WebSocket sessions
                    val sessionList = sessions.keys.map { mapOf("id" to it) }
                    sendResponse(request.id, mapOf("sessions" to sessionList, "total" to sessionList.size))
                    return
                }

                // Get all sessions (Feishu, Discord, WebSocket)
                val allKeys = sessionManager.getAllKeys()
                val sessionList = allKeys.map { key ->
                    val session = sessionManager.get(key)
                    mapOf(
                        "id" to key,
                        "messageCount" to (session?.messageCount() ?: 0),
                        "createdAt" to (session?.createdAt ?: ""),
                        "updatedAt" to (session?.updatedAt ?: ""),
                        "type" to when {
                            key.startsWith("discord_") -> "discord"
                            key.contains("_p2p") || key.contains("_group") -> "feishu"
                            key.startsWith("session_") -> "websocket"
                            else -> "other"
                        }
                    )
                }

                sendResponse(request.id, mapOf(
                    "sessions" to sessionList,
                    "total" to sessionList.size
                ))

                Log.d(TAG, "📋 [Session List] 返回 ${sessionList.size} 个会话")

            } catch (e: Exception) {
                Log.e(TAG, "列出会话失败", e)
                sendError("Failed to list sessions: ${e.message}", request.id)
            }
        }

        /**
         * session.reset() - Reset session
         */
        private fun handleSessionResetRequest(request: RpcRequest) {
            val params = request.params
            val targetSessionId = params?.sessionId ?: sessionId

            targetSessionId?.let {
                sessions[it]?.reset()
                sendResponse(request.id, mapOf("success" to true))
            } ?: sendError("Session not found")
        }

        /**
         * session.listAll() - List all sessions (including those created by channels)
         */
        private fun handleSessionListAllRequest(request: RpcRequest) {
            try {
                val sessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
                if (sessionManager == null) {
                    sendResponse(request.id, mapOf(
                        "sessions" to emptyList<Map<String, Any>>(),
                        "total" to 0
                    ))
                    return
                }

                val allKeys = sessionManager.getAllKeys()
                val sessionList = allKeys.map { key ->
                    val session = sessionManager.get(key)
                    mapOf(
                        "id" to key,
                        "messageCount" to (session?.messageCount() ?: 0),
                        "createdAt" to (session?.createdAt ?: ""),
                        "updatedAt" to (session?.updatedAt ?: ""),
                        "type" to when {
                            key.startsWith("discord_") -> "discord"
                            key.contains("_p2p") || key.contains("_group") -> "feishu"
                            else -> "other"
                        }
                    )
                }

                sendResponse(request.id, mapOf(
                    "sessions" to sessionList,
                    "total" to sessionList.size
                ))

                Log.d(TAG, "📋 [Session List] 返回 ${sessionList.size} 个会话")

            } catch (e: Exception) {
                Log.e(TAG, "列出会话失败", e)
                sendError("Failed to list sessions: ${e.message}", request.id)
            }
        }

        /**
         * Send response
         */
        private fun sendResponse(requestId: String?, data: Any) {
            sendMessage(JsonObject().apply {
                addProperty("type", "response")
                requestId?.let { addProperty("id", it) }
                add("data", gson.toJsonTree(data))
            })
        }

        /**
         * Send error
         */
        private fun sendError(message: String, requestId: String? = null) {
            sendMessage(JsonObject().apply {
                addProperty("type", "error")
                requestId?.let { addProperty("id", it) }
                addProperty("message", message)
            })
        }

        /**
         * Send message
         */
        private fun sendMessage(json: JsonObject) {
            try {
                send(gson.toJson(json))
            } catch (e: IOException) {
                Log.e(TAG, "发送消息失败", e)
            }
        }
    }

    /**
     * Generate session ID
     */
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

/**
 * RPC request
 */
data class RpcRequest(
    val id: String?,
    val method: String,
    val params: RpcParams?
)

/**
 * RPC parameters
 */
data class RpcParams(
    val message: String?,
    val systemPrompt: String?,
    val tools: List<Any>?,
    val maxIterations: Int?,
    val runId: String?,
    val sessionId: String?
)

/**
 * Gateway Session
 */
data class GatewaySession(
    val id: String,
    val webSocket: NanoWSD.WebSocket,
    var lastActivity: Long = System.currentTimeMillis()
) {
    fun reset() {
        lastActivity = System.currentTimeMillis()
    }
}

/**
 * Agent handler interface
 */
interface AgentHandler {
    fun executeAgent(
        sessionId: String,
        userMessage: String,
        systemPrompt: String?,
        tools: List<Any>?,
        maxIterations: Int,
        progressCallback: (Map<String, Any>) -> Unit,
        completeCallback: (Map<String, Any>) -> Unit
    )
}
