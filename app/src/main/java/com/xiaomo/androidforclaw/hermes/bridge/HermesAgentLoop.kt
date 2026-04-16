package com.xiaomo.androidforclaw.hermes.bridge

/**
 * HermesAgentLoop — bridges hermes GatewayRunner's AgentLoop interface
 * to the app's existing AgentLoop (com.xiaomo.androidforclaw.agent.loop).
 *
 * Injected into GatewayRunner.agentLoop to power the chat via Hermes.
 *
 * Lives in the app module (not hermes-android) because it needs to
 * reference app-level classes (UnifiedLLMProvider, ToolRegistry, etc.)
 * while implementing hermes's AgentLoop interface.
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.context.ContextManager
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.hermes.gateway.AgentLoop as HermesAgentLoopInterface
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import kotlinx.coroutines.withTimeout

/**
 * Implements hermes AgentLoop by delegating to the app's AgentLoop.
 */
class HermesAgentLoop(private val context: Context) : HermesAgentLoopInterface {

    companion object {
        private const val TAG = "HermesAgentLoop"
        private const val AGENT_TIMEOUT_MS = 300_000L  // 5 minutes
    }

    private val llmProvider by lazy { UnifiedLLMProvider(context) }
    private val toolRegistry by lazy {
        ToolRegistry(context = context, taskDataManager = TaskDataManager.getInstance())
    }
    private val androidToolRegistry by lazy {
        AndroidToolRegistry(
            context = context,
            taskDataManager = TaskDataManager.getInstance(),
            cameraCaptureManager = null  // Can be wired later
        )
    }
    private val contextBuilder by lazy {
        ContextBuilder(context = context, toolRegistry = toolRegistry, androidToolRegistry = androidToolRegistry)
    }
    private val contextManager by lazy { ContextManager(llmProvider) }
    private val configLoader by lazy { ConfigLoader(context) }

    override suspend fun process(text: String, sessionKey: String, context: Map<String, String>): String? {
        Log.d(TAG, "Processing message (session=$sessionKey): ${text.take(80)}")

        // Resolve model reference for this channel
        val platform = context["platform"] ?: "app_chat"
        val chatId = context["chatId"] ?: sessionKey
        val agentModelRef = try {
            configLoader.resolveAgentModelRef(channel = platform, accountId = chatId)
        } catch (_: Exception) { null }

        // Build system prompt
        val systemPrompt = try {
            contextBuilder.buildSystemPrompt(
                userGoal = text,
                packageName = "",
                testMode = "exploration"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build system prompt, using default", e)
            "You are a helpful AI assistant running on an Android device."
        }

        // Create the app's AgentLoop
        val agentLoop = AgentLoop(
            llmProvider = llmProvider,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry,
            contextManager = contextManager,
            modelRef = agentModelRef,
            configLoader = configLoader
        )

        return try {
            withTimeout(AGENT_TIMEOUT_MS) {
                val result = agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = text,
                    reasoningEnabled = true
                )
                val content = result.finalContent
                Log.d(TAG, "Agent completed: ${result.iterations} iterations, ${content.length} chars")
                content
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop failed", e)
            "Error processing your request: ${e.message}"
        } finally {
            agentLoop.stop()
        }
    }
}
