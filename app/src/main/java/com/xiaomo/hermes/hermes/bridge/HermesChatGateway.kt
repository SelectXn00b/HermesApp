package com.xiaomo.hermes.hermes.bridge

/**
 * HermesChatGateway — app-level entry point for starting the Hermes
 * GatewayRunner with AppChatAdapter for local chat.
 *
 * Usage:
 *   val gateway = HermesChatGateway.getInstance(context)
 *   gateway.start()   // Start Hermes gateway with AppChatAdapter
 *   gateway.send("Hello!")  // Send a message
 *   gateway.responses  // Collect responses via Flow
 *   gateway.stop()
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.GatewayConfig
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import com.xiaomo.hermes.hermes.gateway.platforms.AppChat
import com.xiaomo.hermes.hermes.gateway.platforms.ChatResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File

class HermesChatGateway private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HermesChatGateway"

        @Volatile
        private var instance: HermesChatGateway? = null

        fun getInstance(context: Context): HermesChatGateway =
            instance ?: synchronized(this) {
                instance ?: HermesChatGateway(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var gatewayRunner: GatewayRunner? = null
    private var appChatAdapter: AppChat? = null

    /** Expose the GatewayRunner for wiring into HermesGatewayChannel. */
    val runner: GatewayRunner? get() = gatewayRunner

    /** Responses from the gateway (for Chat UI to collect). */
    val responses: Flow<ChatResponse>
        get() = appChatAdapter?.responses ?: MutableSharedFlow()

    /** Whether the gateway is running. */
    val isRunning: Boolean
        get() = gatewayRunner?.isRunning?.get() == true

    /**
     * Start the Hermes gateway with AppChatAdapter (local mode).
     */
    suspend fun start() {
        if (isRunning) {
            Log.w(TAG, "Gateway already running")
            return
        }

        val config = GatewayConfig(
            hermesHome = File(context.filesDir, "hermes").absolutePath,
            platforms = mapOf(
                Platform.APP_CHAT to PlatformConfig(platform = Platform.APP_CHAT, enabled = true)
            )
        )

        val runner = GatewayRunner(context, config)

        // Inject the agent loop
        runner.agentLoop = HermesAgentLoop(context)

        // Start in local mode (only AppChatAdapter)
        runner.startLocal()

        gatewayRunner = runner
        appChatAdapter = runner.getAppChatAdapter()

        Log.i(TAG, "Hermes chat gateway started")
    }

    /**
     * Send a message from the Chat UI.
     */
    suspend fun send(text: String, chatId: String = "local") {
        val adapter = appChatAdapter
        if (adapter == null) {
            Log.e(TAG, "Gateway not started, cannot send message")
            return
        }
        adapter.pushMessage(text = text, chatId = chatId)
    }

    /**
     * Stop the gateway.
     */
    suspend fun stop() {
        gatewayRunner?.stop()
        gatewayRunner = null
        appChatAdapter = null
        Log.i(TAG, "Hermes chat gateway stopped")
    }
}
