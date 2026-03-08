package com.xiaomo.androidforclaw.gateway.websocket

import android.content.Context
import com.xiaomo.androidforclaw.gateway.protocol.*
import com.xiaomo.androidforclaw.gateway.security.TokenAuth
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Gateway WebSocket RPC Server
 *
 * Implements OpenClaw Protocol v45:
 * - Frame-based communication (Request/Response/Event)
 * - RPC method routing
 * - Connection management
 * - Event broadcasting
 * - Token authentication
 * - Homepage UI
 */
class GatewayWebSocketServer(
    private val context: Context,
    private val port: Int = 8765,
    private val tokenAuth: TokenAuth? = null
) : NanoWSD(null, port) {

    private val connections = ConcurrentHashMap<String, WebSocketConnection>()
    private val handlers = ConcurrentHashMap<String, suspend (Map<String, Any?>?) -> Any?>()
    private val serializer = FrameSerializer()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Register an RPC method handler
     */
    fun registerMethod(method: String, handler: suspend (Map<String, Any?>?) -> Any?) {
        handlers[method] = handler
        Log.d("GatewayWebSocketServer","Registered RPC method: $method")
    }

    /**
     * Get method count
     */
    fun getMethodCount(): Int = handlers.size

    /**
     * Get active connection count
     */
    fun getActiveConnections(): Int = connections.size

    /**
     * Broadcast an event to all connected clients
     */
    fun broadcast(event: EventFrame) {
        val json: String = serializer.serialize(event)
        for (conn in connections.values) {
            try {
                conn.socket.send(json)
            } catch (e: Exception) {
                Log.w("GatewayWebSocketServer", "Failed to broadcast to ${conn.clientId}: ${e.message}")
            }
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return GatewayWebSocket(handshake)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Serve homepage
        if (uri == "/" || uri.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/html",
                getHomepageHtml()
            )
        }

        return super.serve(session)
    }

    /**
     * WebSocket connection handler
     */
    inner class GatewayWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        private val clientId = generateClientId()
        private lateinit var connection: WebSocketConnection

        override fun onOpen() {
            connection = WebSocketConnection(clientId, this, serializer)
            connections[clientId] = connection

            Log.i("GatewayWebSocketServer","WebSocket opened: $clientId")

            // Send Hello frame
            val hello = ResponseFrame(
                type = "response",
                id = null,
                result = mapOf(
                    "protocol" to PROTOCOL_VERSION,
                    "clientId" to clientId,
                    "message" to "Welcome to AndroidForClaw Gateway",
                    "authRequired" to (tokenAuth != null)
                )
            )
            connection.send(hello)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String?,
            initiatedByRemote: Boolean
        ) {
            connections.remove(clientId)
            Log.i("GatewayWebSocketServer","WebSocket closed: $clientId, reason=$reason")
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                val text = message.textPayload
                Log.d("GatewayWebSocketServer","Received message from $clientId: $text")

                val frame = serializer.deserialize(text)

                when (frame) {
                    is RequestFrame -> handleRequest(frame)
                    else -> {
                        Log.w("GatewayWebSocketServer","Unexpected frame type: ${frame.type}")
                    }
                }

            } catch (e: Exception) {
                Log.e("GatewayWebSocketServer", "Error processing message from $clientId", e)
                sendError("Invalid frame: ${e.message}")
            }
        }

        override fun onPong(pong: WebSocketFrame) {
            connection.updateActivity()
        }

        override fun onException(exception: IOException) {
            Log.e("GatewayWebSocketServer", "WebSocket exception for $clientId", exception)
        }

        /**
         * Handle RPC request
         */
        private fun handleRequest(request: RequestFrame) {
            serverScope.launch {
                try {
                    // Check authentication
                    if (tokenAuth != null && !connection.isAuthenticated) {
                        val token = request.params?.get("token") as? String

                        if (request.method == "auth" && token != null) {
                            if (tokenAuth.verify(token)) {
                                connection.isAuthenticated = true
                                sendResponse(request.id, mapOf("authenticated" to true))
                                Log.i("GatewayWebSocketServer","Client $clientId authenticated")
                            } else {
                                sendError("Invalid token", request.id)
                            }
                            return@launch
                        } else if (request.method != "health") {
                            sendError("Authentication required", request.id)
                            return@launch
                        }
                    }

                    // Find handler
                    val handler = handlers[request.method]
                    if (handler == null) {
                        sendError("Unknown method: ${request.method}", request.id)
                        return@launch
                    }

                    // Execute handler
                    val result = handler(request.params)

                    // Send response
                    sendResponse(request.id, result)

                } catch (e: Exception) {
                    Log.e("GatewayWebSocketServer", "Error handling request: ${request.method}", e)
                    sendError("Internal error: ${e.message}", request.id)
                }
            }
        }

        /**
         * Send response frame
         */
        private fun sendResponse(requestId: String, result: Any?) {
            val response = ResponseFrame(
                type = "response",
                id = requestId,
                result = result
            )
            connection.send(response)
        }

        /**
         * Send error frame
         */
        private fun sendError(message: String, requestId: String? = null) {
            val response = ResponseFrame(
                type = "response",
                id = requestId,
                error = mapOf("message" to message)
            )
            connection.send(response)
        }
    }

    /**
     * Generate unique client ID
     */
    private fun generateClientId(): String {
        return "client_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Homepage HTML
     */
    private fun getHomepageHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AndroidForClaw Gateway</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #333;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            padding: 40px;
            max-width: 600px;
            width: 90%;
        }
        h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle {
            color: #666;
            margin-bottom: 30px;
            font-size: 1.1em;
        }
        .status {
            display: inline-block;
            padding: 8px 16px;
            background: #10b981;
            color: white;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: 600;
            margin-bottom: 30px;
        }
        .info-box {
            background: #f8f9fa;
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .info-box h3 {
            color: #667eea;
            margin-bottom: 15px;
            font-size: 1.2em;
        }
        .info-item {
            display: flex;
            justify-content: space-between;
            padding: 10px 0;
            border-bottom: 1px solid #e5e7eb;
        }
        .info-item:last-child {
            border-bottom: none;
        }
        .info-label {
            color: #6b7280;
            font-weight: 500;
        }
        .info-value {
            color: #1f2937;
            font-family: 'Courier New', monospace;
        }
        .methods {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
            gap: 10px;
            margin-top: 15px;
        }
        .method {
            background: white;
            padding: 8px 12px;
            border-radius: 6px;
            font-size: 0.85em;
            border: 1px solid #e5e7eb;
            font-family: 'Courier New', monospace;
        }
        .footer {
            text-align: center;
            margin-top: 30px;
            color: #6b7280;
            font-size: 0.9em;
        }
        .footer a {
            color: #667eea;
            text-decoration: none;
        }
        .footer a:hover {
            text-decoration: underline;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🧠 AndroidForClaw</h1>
        <p class="subtitle">AI Agent Runtime for Android</p>
        <div class="status">⚡ Gateway Online</div>

        <div class="info-box">
            <h3>Connection Info</h3>
            <div class="info-item">
                <span class="info-label">WebSocket URL</span>
                <span class="info-value">ws://localhost:$port</span>
            </div>
            <div class="info-item">
                <span class="info-label">Protocol Version</span>
                <span class="info-value">$PROTOCOL_VERSION</span>
            </div>
            <div class="info-item">
                <span class="info-label">Active Connections</span>
                <span class="info-value">${connections.size}</span>
            </div>
            <div class="info-item">
                <span class="info-label">Authentication</span>
                <span class="info-value">${if (tokenAuth != null) "Enabled" else "Disabled"}</span>
            </div>
        </div>

        <div class="info-box">
            <h3>Available Methods</h3>
            <div class="methods">
                ${handlers.keys.sorted().joinToString("\n                ") { "<div class=\"method\">$it</div>" }}
            </div>
        </div>

        <div class="footer">
            Powered by <a href="https://github.com/openclaw/openclaw" target="_blank">OpenClaw</a>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
