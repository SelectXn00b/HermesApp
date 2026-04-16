/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-ws-runtime.ts
 */
package com.xiaomo.androidforclaw.gateway.websocket

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import com.xiaomo.androidforclaw.gateway.protocol.Frame
import com.xiaomo.androidforclaw.gateway.protocol.FrameSerializer
import fi.iki.elonen.NanoWSD
import com.xiaomo.androidforclaw.logging.Log

/**
 * Encapsulates a WebSocket connection with metadata
 */
class WebSocketConnection(
    val clientId: String,
    val socket: NanoWSD.WebSocket,
    private val serializer: FrameSerializer
) {
    var isAuthenticated = false
    var metadata: MutableMap<String, Any> = mutableMapOf()
    var lastActivity: Long = System.currentTimeMillis()

    /**
     * Send a frame to the client
     */
    fun send(frame: Frame) {
        try {
            val json: String = serializer.serialize(frame)
            socket.send(json)
            updateActivity()
        } catch (e: Exception) {
            Log.e("WebSocketConnection", "Failed to send frame to $clientId", e)
        }
    }

    /**
     * Update last activity timestamp
     */
    fun updateActivity() {
        lastActivity = System.currentTimeMillis()
    }

    /**
     * Check if connection is idle
     */
    fun isIdle(thresholdMs: Long): Boolean {
        return (System.currentTimeMillis() - lastActivity) > thresholdMs
    }
}
