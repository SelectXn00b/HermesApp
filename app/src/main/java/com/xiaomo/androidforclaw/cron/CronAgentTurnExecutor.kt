/**
 * AndroidForClaw CronAgentTurnExecutor
 *
 * Executes an agent loop turn from a cron job, then delivers the result
 * via the specified channel (feishu / weixin).
 *
 * Aligned with OpenClaw cron AgentTurn execution.
 */
package com.xiaomo.androidforclaw.cron

import android.content.Context
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.context.ContextManager
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.session.HistorySanitizer
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MainEntryNew
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.llm.toNewMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CronAgentTurnExecutor {
    private const val TAG = "CronAgentTurn"

    /**
     * Execute a cron agent turn.
     *
     * @param context Application context
     * @param sessionId Session identifier (e.g. "cron_heartbeat" or "cron_isolated_<jobId>")
     * @param userMessage The message to send to the agent
     * @param model Optional model override
     * @param channel Delivery channel ("feishu" / "weixin" / null)
     * @param to Delivery target (chat_id for feishu, user_id for weixin)
     * @param isolated If true, pass empty history (isolated session)
     * @return CronRunResult
     */
    suspend fun execute(
        context: Context,
        sessionId: String,
        userMessage: String,
        model: String? = null,
        channel: String? = null,
        to: String? = null,
        isolated: Boolean = false
    ): CronRunResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "⏰ Executing cron turn: session=$sessionId channel=$channel to=$to")

            // Build agent loop (same setup as processFeishuMessage)
            val taskDataManager = TaskDataManager.getInstance()
            val toolRegistry = ToolRegistry(
                context = context,
                taskDataManager = taskDataManager
            )
            val androidToolRegistry = AndroidToolRegistry(
                context = context,
                taskDataManager = taskDataManager,
                cameraCaptureManager = null
            )
            val configLoader = ConfigLoader(context)
            val contextBuilder = ContextBuilder(
                context = context,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                configLoader = configLoader
            )
            val llmProvider = UnifiedLLMProvider(context)
            val contextManager = ContextManager(llmProvider)

            val config = configLoader.loadOpenClawConfig()
            val maxIterations = config.agent.maxIterations

            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = maxIterations,
                modelRef = model
            )

            // Build context history
            if (MainEntryNew.getSessionManager() == null) {
                MainEntryNew.initialize(context as android.app.Application)
            }
            val sessionManager = MainEntryNew.getSessionManager()
                ?: return@withContext CronRunResult(RunStatus.ERROR, "SessionManager not initialized")
            val session = sessionManager.getOrCreate(sessionId)
            val contextHistory = if (isolated) {
                emptyList()
            } else {
                val rawHistory = session.getRecentMessages(20)
                cleanupToolMessages(rawHistory).map { it.toNewMessage() }
            }

            // Build system prompt
            val channelCtx = ContextBuilder.ChannelContext(
                channel = channel ?: "cron",
                chatId = to ?: "",
                chatType = "p2p",
                senderId = "",
                messageId = ""
            )
            val systemPrompt = contextBuilder.buildSystemPrompt(
                userGoal = userMessage,
                packageName = "",
                testMode = "cron",
                channelContext = channelCtx
            )

            // Run agent loop
            val result = agentLoop.run(
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                contextHistory = contextHistory
            )

            // Save to session history
            session.addMessage(LegacyMessage(
                role = "user", content = userMessage
            ))
            session.addMessage(LegacyMessage(
                role = "assistant", content = result.finalContent
            ))

            // Deliver result
            val response = result.finalContent.trim()
            if (response != "NO_REPLY" && response != "HEARTBEAT_OK" && response.isNotBlank()) {
                val sanitized = HistorySanitizer
                    .stripControlTokensFromText(response)
                    .replace(Regex("(?:^|\\s+|\\*+)NO_REPLY\\s*$"), "")
                    .replace(Regex("(?:^|\\s+|\\*+)HEARTBEAT_OK\\s*$"), "")
                    .trim()

                if (sanitized.isNotBlank() && channel != null && to != null) {
                    deliver(context, channel, to, sanitized)
                }
            }

            CronRunResult(
                status = RunStatus.OK,
                summary = result.finalContent.take(200),
                delivered = channel != null && to != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cron agent turn failed", e)
            CronRunResult(
                status = RunStatus.ERROR,
                summary = e.message
            )
        }
    }

    /**
     * Deliver a message to the specified channel.
     */
    private suspend fun deliver(context: Context, channel: String, to: String, text: String) {
        when (channel) {
            "feishu" -> {
                val feishuChannel = MyApplication.getFeishuChannel()
                if (feishuChannel != null) {
                    try {
                        feishuChannel.sender.sendTextMessage(to, text)
                        Log.i(TAG, "📨 Delivered to feishu $to")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to deliver to feishu", e)
                    }
                } else {
                    Log.w(TAG, "Feishu channel not available")
                }
            }
            "weixin" -> {
                val weixinChannel = MyApplication.getWeixinChannel()
                if (weixinChannel != null) {
                    try {
                        weixinChannel.sender?.sendText(to, text)
                        Log.i(TAG, "📨 Delivered to weixin $to")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to deliver to weixin", e)
                    }
                } else {
                    Log.w(TAG, "Weixin channel not available")
                }
            }
            else -> Log.w(TAG, "Unknown channel: $channel")
        }
    }

    /**
     * Clean up tool messages from history (same as MyApplication.cleanupToolMessages).
     */
    private fun cleanupToolMessages(
        messages: List<LegacyMessage>
    ): List<LegacyMessage> {
        return messages.filter { message ->
            when (message.role) {
                "user" -> true
                "assistant" -> message.content != null && message.toolCalls == null
                else -> false
            }
        }
    }
}
