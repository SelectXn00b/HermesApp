/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.content.Context
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.gateway.methods.AgentMethods
import com.xiaomo.androidforclaw.gateway.methods.HealthMethods
import com.xiaomo.androidforclaw.gateway.methods.SessionMethods
import com.xiaomo.androidforclaw.gateway.methods.ModelsMethods
import com.xiaomo.androidforclaw.gateway.methods.ToolsMethods
import com.xiaomo.androidforclaw.gateway.methods.SkillsMethods
import com.xiaomo.androidforclaw.gateway.methods.ConfigMethods
import com.xiaomo.androidforclaw.gateway.methods.CronMethods
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
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
import org.json.JSONObject

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
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    private val skillsLoader: SkillsLoader,
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
    private lateinit var modelsMethods: ModelsMethods
    private lateinit var toolsMethods: ToolsMethods
    private lateinit var skillsMethods: SkillsMethods
    private lateinit var configMethods: ConfigMethods

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
                modelsMethods = ModelsMethods(context)
                toolsMethods = ToolsMethods(toolRegistry, androidToolRegistry)
                skillsMethods = SkillsMethods(context)
                configMethods = ConfigMethods(context)

                // Register Agent methods
                registerMethod("agent") { params ->
                    val agentParams = parseAgentParams(params)
                    agentMethods.agent(agentParams)
                }

                registerMethod("agent.wait") { params ->
                    val waitParams = parseAgentWaitParams(params)
                    agentMethods.agentWait(waitParams)
                }

                // OpenClaw uses "agent.identity.get" not "agent.identity"
                registerMethod("agent.identity.get") { _ ->
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

                // Register Models methods
                registerMethod("models.list") { _ ->
                    modelsMethods.modelsList()
                }

                // Register Tools methods
                registerMethod("tools.catalog") { _ ->
                    toolsMethods.toolsCatalog()
                }

                registerMethod("tools.list") { _ ->
                    toolsMethods.toolsList()
                }

                // Register Skills methods
                registerMethod("skills.status") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.status(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.bins") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.bins(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.reload") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.reload(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.install") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.install(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.update") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.update(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.search") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.search(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                registerMethod("skills.uninstall") { params ->
                    val paramsObj = when (params) {
                        is com.google.gson.JsonObject -> params
                        is Map<*, *> -> com.google.gson.Gson().toJsonTree(params).asJsonObject
                        else -> com.google.gson.JsonObject()
                    }
                    val result = skillsMethods.uninstall(paramsObj)
                    if (result.isSuccess) result.getOrNull() else throw result.exceptionOrNull()!!
                }

                // Register Config methods
                registerMethod("config.get") { params ->
                    configMethods.configGet(params)
                }

                registerMethod("config.set") { params ->
                    configMethods.configSet(params)
                }

                registerMethod("config.reload") { _ ->
                    configMethods.configReload()
                }

                // Register Cron methods (OpenClaw alignment)
                registerMethod("cron.list") { params ->
                    CronMethods.list(params as JSONObject)
                }

                registerMethod("cron.status") { params ->
                    CronMethods.status(params as JSONObject)
                }

                registerMethod("cron.add") { params ->
                    CronMethods.add(params as JSONObject)
                }

                registerMethod("cron.update") { params ->
                    CronMethods.update(params as JSONObject)
                }

                registerMethod("cron.remove") { params ->
                    CronMethods.remove(params as JSONObject)
                }

                registerMethod("cron.run") { params ->
                    CronMethods.run(params as JSONObject)
                }

                registerMethod("cron.runs") { params ->
                    CronMethods.runs(params as JSONObject)
                }

                Log.i(TAG,"Registered ${getMethodCount()} RPC methods")
            }

            // Start server in background
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Use 60 second timeout for slow operations (like ClawHub API calls)
                    // NanoHTTPD.SOCKET_READ_TIMEOUT is 5000ms by default, too short
                    server?.start(60000, false)  // 60 seconds
                    isRunning = true
                    Log.i(TAG,"Gateway WebSocket server started on port $port with 60s timeout")
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
    // OpenClaw Protocol v45: params is Any? (can be Map, List, primitive, etc.)

    @Suppress("UNCHECKED_CAST")
    private fun parseAgentParams(params: Any?): AgentParams {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object for agent method")

        return AgentParams(
            sessionKey = paramsMap["sessionKey"] as? String
                ?: throw IllegalArgumentException("sessionKey required"),
            message = paramsMap["message"] as? String
                ?: throw IllegalArgumentException("message required"),
            thinking = paramsMap["thinking"] as? String,
            model = paramsMap["model"] as? String
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAgentWaitParams(params: Any?): AgentWaitParams {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object for agent.wait method")

        return AgentWaitParams(
            runId = paramsMap["runId"] as? String
                ?: throw IllegalArgumentException("runId required"),
            timeout = (paramsMap["timeout"] as? Number)?.toLong()
        )
    }
}
