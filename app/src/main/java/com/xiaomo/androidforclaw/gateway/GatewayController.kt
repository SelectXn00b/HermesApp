package com.xiaomo.androidforclaw.gateway

import android.content.Context
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.gateway.methods.AgentMethods
import com.xiaomo.androidforclaw.gateway.methods.HealthMethods
import com.xiaomo.androidforclaw.gateway.methods.SessionMethods
import com.xiaomo.androidforclaw.gateway.protocol.AgentParams
import com.xiaomo.androidforclaw.gateway.protocol.AgentWaitParams
import com.xiaomo.androidforclaw.gateway.security.TokenAuth
import com.xiaomo.androidforclaw.gateway.websocket.GatewayWebSocketServer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log
import java.io.IOException

/**
 * Main Gateway controller that integrates all components:
 * - WebSocket RPC server (Protocol v45)
 * - Agent methods
 * - Session methods
 * - Health methods
 * - Token authentication
 *
 * Aligned with OpenClaw Gateway architecture
 */
class GatewayController(
    private val context: Context,
    private val agentLoop: AgentLoop,
    private val sessionManager: SessionManager,
    private val port: Int = 8765,
    private val authToken: String? = null
) {
    private val TAG = "GatewayController"
    private var server: GatewayWebSocketServer? = null
    private var tokenAuth: TokenAuth? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var agentMethods: AgentMethods
    private lateinit var sessionMethods: SessionMethods
    private lateinit var healthMethods: HealthMethods

    var isRunning = false
        private set

    /**
     * Start the Gateway WebSocket server
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG,"Gateway already running")
            return
        }

        try {
            // Initialize token auth if configured
            if (authToken != null) {
                tokenAuth = TokenAuth(authToken)
                Log.i(TAG,"Token authentication enabled")
            } else {
                Log.w(TAG,"Token authentication disabled - running in insecure mode")
            }

            // Create WebSocket server
            server = GatewayWebSocketServer(
                context = context,
                port = port,
                tokenAuth = tokenAuth
            ).apply {
                // Initialize method handlers
                agentMethods = AgentMethods(context, agentLoop, sessionManager, this)
                sessionMethods = SessionMethods(sessionManager)
                healthMethods = HealthMethods()

                // Register Agent methods
                registerMethod("agent") { params ->
                    val agentParams = parseAgentParams(params)
                    agentMethods.agent(agentParams)
                }

                registerMethod("agent.wait") { params ->
                    val waitParams = parseAgentWaitParams(params)
                    agentMethods.agentWait(waitParams)
                }

                registerMethod("agent.identity") { _ ->
                    agentMethods.agentIdentity()
                }

                // Register Session methods
                registerMethod("sessions.list") { params ->
                    sessionMethods.sessionsList(params)
                }

                registerMethod("sessions.preview") { params ->
                    sessionMethods.sessionsPreview(params)
                }

                registerMethod("sessions.reset") { params ->
                    sessionMethods.sessionsReset(params)
                }

                registerMethod("sessions.delete") { params ->
                    sessionMethods.sessionsDelete(params)
                }

                registerMethod("sessions.patch") { params ->
                    sessionMethods.sessionsPatch(params)
                }

                // Register Health methods
                registerMethod("health") { _ ->
                    healthMethods.health()
                }

                registerMethod("status") { _ ->
                    healthMethods.status()
                }

                Log.i(TAG,"Registered ${getMethodCount()} RPC methods")
            }

            // Start server in background
            serviceScope.launch(Dispatchers.IO) {
                try {
                    server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    isRunning = true
                    Log.i(TAG,"Gateway WebSocket server started on port $port")
                    Log.i(TAG,"Access UI at http://localhost:$port/")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start Gateway server", e)
                    isRunning = false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gateway", e)
            throw e
        }
    }

    /**
     * Stop the Gateway WebSocket server
     */
    fun stop() {
        if (!isRunning) {
            Log.w(TAG,"Gateway not running")
            return
        }

        try {
            server?.stop()
            server = null
            isRunning = false
            Log.i(TAG, "Gateway WebSocket server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Gateway", e)
        }
    }

    /**
     * Generate a new authentication token
     */
    fun generateToken(label: String = "generated", ttlMs: Long? = null): String? {
        return tokenAuth?.generateToken(label, ttlMs)
    }

    /**
     * Revoke an authentication token
     */
    fun revokeToken(token: String): Boolean {
        return tokenAuth?.revokeToken(token) ?: false
    }

    /**
     * Get server info
     */
    fun getInfo(): Map<String, Any> {
        return mapOf(
            "running" to isRunning,
            "port" to port,
            "authenticated" to (tokenAuth != null),
            "connections" to (server?.getActiveConnections() ?: 0),
            "url" to "ws://localhost:$port"
        )
    }

    // Helper methods to parse params

    private fun parseAgentParams(params: Map<String, Any?>?): AgentParams {
        requireNotNull(params) { "params required for agent method" }

        return AgentParams(
            sessionKey = params["sessionKey"] as? String
                ?: throw IllegalArgumentException("sessionKey required"),
            message = params["message"] as? String
                ?: throw IllegalArgumentException("message required"),
            thinking = params["thinking"] as? String,
            model = params["model"] as? String
        )
    }

    private fun parseAgentWaitParams(params: Map<String, Any?>?): AgentWaitParams {
        requireNotNull(params) { "params required for agent.wait method" }

        return AgentWaitParams(
            runId = params["runId"] as? String
                ?: throw IllegalArgumentException("runId required"),
            timeout = (params["timeout"] as? Number)?.toLong()
        )
    }
}
