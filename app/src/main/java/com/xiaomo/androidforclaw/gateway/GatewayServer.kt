/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.channel.ChannelManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * Gateway Server - HTTP + WebSocket server
 *
 * Provides WebUI access interface:
 * - HTTP: Static file service (WebUI)
 * - WebSocket: Real-time communication (RPC + Events)
 */
class GatewayServer(
    private val context: Context,
    private val port: Int = 8080
) : NanoWSD(null, port) {  // null = listen on all network interfaces (0.0.0.0)

    companion object {
        private const val TAG = "GatewayServer"

        // Static instance for broadcasting messages from elsewhere
        @Volatile
        private var instance: GatewayServer? = null

        fun getInstance(): GatewayServer? = instance

        /**
         * Broadcast chat message to all connected clients
         */
        fun broadcastChatMessage(sessionId: String, role: String, content: String) {
            instance?.let { server ->
                val payload = JSONObject().apply {
                    put("id", "msg-${System.currentTimeMillis()}")
                    put("sessionId", sessionId)
                    put("role", role)
                    put("content", content)
                    put("timestamp", System.currentTimeMillis())
                }
                server.broadcast("chat.message", payload)
                Log.d(TAG, "📤 [Broadcast] Chat message: $sessionId, $role")
            }
        }
    }

    private val channelManager = ChannelManager(context)
    private val activeConnections = mutableSetOf<GatewayWebSocket>()

    init {
        Log.d(TAG, "Gateway Server initialized on port $port")
        instance = this
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        Log.d(TAG, "HTTP Request: ${session.method} $uri")

        // WebSocket upgrade - check Upgrade header
        val headers = session.headers
        if (headers["upgrade"]?.lowercase()?.contains("websocket") == true) {
            return super.serve(session)
        }

        // HTTP API
        if (uri.startsWith("/api/")) {
            return handleApiRequest(session)
        }

        // Static files (WebUI)
        return serveWebUI(uri)
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        Log.d(TAG, "WebSocket connection opened")
        return GatewayWebSocket(handshake, this)
    }

    /**
     * Handle API requests
     */
    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/api")

        return when {
            uri == "/health" -> {
                val json = JSONObject().apply {
                    put("status", "ok")
                    put("version", "3.0.0")
                    put("timestamp", System.currentTimeMillis())
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

            uri == "/device/status" -> {
                val status = channelManager.getCurrentAccount()
                val json = JSONObject().apply {
                    put("connected", status.connected)
                    put("deviceId", status.deviceId)
                    put("deviceModel", status.deviceModel)
                    put("androidVersion", status.androidVersion)
                    put("apiLevel", status.apiLevel)
                    put("permissions", JSONObject().apply {
                        put("accessibility", status.accessibilityEnabled)
                        put("overlay", status.overlayPermission)
                        put("mediaProjection", status.mediaProjection)
                    })
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "API not found: $uri")
            }
        }
    }

    /**
     * Serve WebUI static files
     */
    private fun serveWebUI(uri: String): Response {
        var path = uri
        if (path == "/") {
            path = "/index.html"
        }

        try {
            val assetPath = "webui$path"
            val inputStream = context.assets.open(assetPath)
            val mimeType = getMimeType(path)

            return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            Log.w(TAG, "WebUI file not found: $path", e)
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html",
                """
                <html>
                <body>
                    <h1>404 - File Not Found</h1>
                    <p>File: $path</p>
                    <p>WebUI not built yet. Run: <code>cd ui && npm run build</code></p>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }

    /**
     * Get MIME type
     */
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    /**
     * WebSocket connection
     */
    inner class GatewayWebSocket(
        handshakeRequest: IHTTPSession,
        private val server: GatewayServer
    ) : NanoWSD.WebSocket(handshakeRequest) {

        private val connectionId = UUID.randomUUID().toString()
        private var pingTimer: java.util.Timer? = null

        override fun onOpen() {
            Log.d(TAG, "🔗 [Gateway] WebSocket opened: $connectionId")
            activeConnections.add(this)
            Log.d(TAG, "📊 [Gateway] Active connections: ${activeConnections.size}")

            // Send hello message
            val hello = JSONObject().apply {
                put("type", "event")
                put("event", "hello")
                put("payload", JSONObject().apply {
                    put("version", "3.0.0")
                    put("agent", "AndroidForClaw")
                    put("channel", "android-app")
                    put("deviceId", channelManager.getCurrentAccount().deviceId)
                    put("capabilities", listOf("screenshot", "tap", "swipe", "type"))
                })
            }
            Log.d(TAG, "👋 [Gateway] Sending hello message: ${hello.toString()}")
            send(hello.toString())
            Log.d(TAG, "✅ [Gateway] Hello message sent")

            // Start heartbeat - send ping every 30 seconds
            startHeartbeat()
        }

        private fun startHeartbeat() {
            pingTimer = java.util.Timer()
            pingTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        if (isOpen) {
                            ping("ping".toByteArray())
                            Log.d(TAG, "💓 [Gateway] Heartbeat sent: $connectionId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Gateway] Heartbeat failed: $connectionId", e)
                        cancel()
                    }
                }
            }, 30000, 30000) // 30 second interval
            Log.d(TAG, "💓 [Gateway] Heartbeat started: $connectionId")
        }

        private fun stopHeartbeat() {
            pingTimer?.cancel()
            pingTimer = null
            Log.d(TAG, "💓 [Gateway] Heartbeat stopped: $connectionId")
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            Log.d(TAG, "🔌 [Gateway] WebSocket closed: $connectionId")
            Log.d(TAG, "📊 [Gateway] Close info - code: $code, reason: $reason, initiatedByRemote: $initiatedByRemote")
            stopHeartbeat()
            activeConnections.remove(this)
            Log.d(TAG, "📊 [Gateway] Active connections: ${activeConnections.size}")
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            try {
                val text = message.textPayload
                Log.d(TAG, "📨 [Gateway] WebSocket message received")
                Log.d(TAG, "📦 [Gateway] Message content: $text")

                val frame = JSONObject(text)
                val type = frame.optString("type")
                Log.d(TAG, "🔍 [Gateway] Frame type: $type")

                when (type) {
                    "req" -> {
                        Log.d(TAG, "🎯 [Gateway] Handling request...")
                        handleRequest(frame)
                    }
                    "ping" -> {
                        // Received client heartbeat
                        Log.d(TAG, "💓 [Gateway] Received ping from client: $connectionId")
                        // NanoWSD will automatically send pong
                    }
                    else -> {
                        Log.w(TAG, "⚠️ [Gateway] Unknown frame type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to handle WebSocket message", e)
                Log.e(TAG, "🔍 [Gateway] Error details: ${e.message}")
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {
            Log.d(TAG, "💓 [Gateway] Pong received: $connectionId")
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "WebSocket exception: $connectionId", exception)
        }

        /**
         * Handle RPC request
         */
        private fun handleRequest(frame: JSONObject) {
            val id = frame.getString("id")
            val method = frame.getString("method")
            val params = frame.optJSONObject("params")

            Log.d(TAG, "🎯 [Gateway] RPC Request:")
            Log.d(TAG, "  📋 ID: $id")
            Log.d(TAG, "  🔧 Method: $method")
            Log.d(TAG, "  📦 Params: $params")

            try {
                val result = when (method) {
                    "device.status" -> {
                        Log.d(TAG, "📱 [Gateway] Handling device.status...")
                        handleDeviceStatus()
                    }
                    "channel.status" -> {
                        Log.d(TAG, "📡 [Gateway] Handling channel.status...")
                        handleChannelStatus()
                    }
                    "sessions.list" -> {
                        Log.d(TAG, "📋 [Gateway] Handling sessions.list...")
                        handleSessionsList()
                    }
                    "sessions.history" -> {
                        Log.d(TAG, "📋 [Gateway] Handling sessions.history...")
                        handleSessionsHistory(params)
                    }
                    "chat.send" -> {
                        Log.d(TAG, "💬 [Gateway] Handling chat.send...")
                        handleChatSend(params)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ [Gateway] Unknown method: $method")
                        throw IllegalArgumentException("Unknown method: $method")
                    }
                }

                Log.d(TAG, "✅ [Gateway] Request successful: $method")
                Log.d(TAG, "📦 [Gateway] Result: $result")

                val response = JSONObject().apply {
                    put("type", "res")
                    put("id", id)
                    put("ok", true)
                    put("payload", result)
                }
                Log.d(TAG, "📤 [Gateway] Sending response: ${response.toString()}")
                send(response.toString())
                Log.d(TAG, "✅ [Gateway] Response sent")

            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Request failed: $method", e)
                Log.e(TAG, "🔍 [Gateway] Error details: ${e.message}")

                val response = JSONObject().apply {
                    put("type", "res")
                    put("id", id)
                    put("ok", false)
                    put("error", JSONObject().apply {
                        put("code", "internal_error")
                        put("message", e.message ?: "Unknown error")
                    })
                }
                Log.d(TAG, "📤 [Gateway] Sending error response: ${response.toString()}")
                send(response.toString())
            }
        }

        private fun handleDeviceStatus(): JSONObject {
            val account = channelManager.getCurrentAccount()
            return JSONObject().apply {
                put("connected", account.connected)
                put("deviceId", account.deviceId)
                put("deviceModel", account.deviceModel)
                put("androidVersion", account.androidVersion)
                put("apiLevel", account.apiLevel)
                put("architecture", account.architecture)
                put("permissions", JSONObject().apply {
                    put("accessibility", account.accessibilityEnabled)
                    put("overlay", account.overlayPermission)
                    put("mediaProjection", account.mediaProjection)
                })
            }
        }

        private fun handleChannelStatus(): JSONObject {
            val status = channelManager.getChannelStatus()
            return JSONObject().apply {
                put("timestamp", status.timestamp)
                put("channelId", status.channelId)
                put("accounts", status.accounts.map { account ->
                    JSONObject().apply {
                        put("accountId", account.accountId)
                        put("name", account.name)
                        put("connected", account.connected)
                        put("deviceId", account.deviceId)
                        put("deviceModel", account.deviceModel)
                    }
                })
            }
        }

        private fun handleSessionsList(): JSONObject {
            // Return all session list
            return JSONObject().apply {
                put("sessions", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "default")
                        put("name", "默认会话")
                        put("lastActivity", System.currentTimeMillis())
                    })
                })
            }
        }

        private fun handleSessionsHistory(params: JSONObject?): JSONObject {
            val sessionId = params?.optString("sessionId") ?: "default"
            Log.d(TAG, "📋 [Gateway] 获取会话历史: $sessionId")

            try {
                // Get SessionManager from MainEntryNew
                val sessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
                val session = sessionManager?.get(sessionId)

                if (session != null) {
                    Log.d(TAG, "✅ [Gateway] Found session: ${session.messageCount()} messages")
                    return JSONObject().apply {
                        put("sessionId", sessionId)
                        put("messages", org.json.JSONArray().apply {
                            session.messages.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content ?: "")
                                    // Ignore complex fields like tool_calls, only return basic conversation
                                })
                            }
                        })
                    }
                } else {
                    Log.w(TAG, "⚠️ [Gateway] Session not found: $sessionId")
                    return JSONObject().apply {
                        put("sessionId", sessionId)
                        put("messages", org.json.JSONArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to get session history", e)
                throw e
            }
        }

        private fun handleChatSend(params: JSONObject?): JSONObject {
            val message = params?.optString("message") ?: throw IllegalArgumentException("Missing message")
            val sessionId = params.optString("sessionId")

            Log.d(TAG, "💬 [Gateway] Chat message received:")
            Log.d(TAG, "  📝 Message: $message")
            Log.d(TAG, "  🆔 Session ID: $sessionId")

            // Send ADB command to trigger agent execution
            val adbCommand = if (sessionId.isNotEmpty()) {
                "adb shell am broadcast -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT --es message \"$message\" --es sessionId \"$sessionId\""
            } else {
                "adb shell am broadcast -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT --es message \"$message\""
            }

            Log.d(TAG, "📤 [Gateway] Broadcasting intent to agent...")
            Log.d(TAG, "🔧 [Gateway] ADB command: $adbCommand")

            try {
                // Broadcast to local app
                val intent = android.content.Intent("com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT").apply {
                    putExtra("message", message)
                    if (sessionId.isNotEmpty()) {
                        putExtra("sessionId", sessionId)
                    }
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "✅ [Gateway] Intent broadcast sent")

                return JSONObject().apply {
                    put("status", "queued")
                    put("message", "Message queued for processing")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to broadcast intent", e)
                throw e
            }
        }
    }

    /**
     * Broadcast event to all connections
     */
    fun broadcast(event: String, payload: Any) {
        val message = JSONObject().apply {
            put("type", "event")
            put("event", event)
            put("payload", payload)
        }

        val text = message.toString()
        activeConnections.forEach { connection ->
            try {
                connection.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast to connection", e)
            }
        }
    }

    /**
     * Get server address
     */
    fun getServerUrl(): String {
        return "http://0.0.0.0:$port"
    }

    fun getActiveConnectionsCount(): Int {
        return activeConnections.size
    }
}
