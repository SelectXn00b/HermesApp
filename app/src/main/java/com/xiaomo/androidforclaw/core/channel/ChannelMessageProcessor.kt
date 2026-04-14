package com.xiaomo.androidforclaw.core.channel

import com.xiaomo.androidforclaw.agent.context.ContextSecurityGuard
import com.xiaomo.androidforclaw.shared.chunkTextByBreakResolver
import com.xiaomo.androidforclaw.shared.normalizeString
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.autoreply.isHeartbeatUserMessage
import com.xiaomo.androidforclaw.autoreply.isSilentReplyText
import com.xiaomo.androidforclaw.autoreply.stripSilentToken
import com.xiaomo.androidforclaw.commands.CommandRegistry
import com.xiaomo.androidforclaw.core.MainEntryNew
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.llm.toLegacyMessage
import com.xiaomo.androidforclaw.providers.llm.toNewMessage
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import com.xiaomo.androidforclaw.util.ReplyTagFilter

/**
 * Shared message processing pipeline for Discord, Telegram, Slack, and Signal.
 *
 * Each channel provides a [ChannelAdapter] that captures the per-channel differences
 * (reactions, typing, chunk sizes, system prompt, send API). This class runs the
 * common 10-step pipeline.
 */
class ChannelMessageProcessor(private val app: MyApplication) {

    companion object {
        private const val TAG = "ChannelMessageProcessor"

        fun splitMessageIntoChunks(message: String, maxChunkSize: Int): List<String> {
            if (message.length <= maxChunkSize) {
                return listOf(message)
            }

            // Delegate to shared.chunkTextByBreakResolver() with a break resolver
            // that prefers newline > period > space boundaries (same logic as before)
            return chunkTextByBreakResolver(message, maxChunkSize) { window ->
                val lastNewline = window.lastIndexOf('\n')
                if (lastNewline > maxChunkSize / 2) {
                    lastNewline + 1
                } else {
                    val lastPeriod = window.lastIndexOf('\u3002')
                    if (lastPeriod > maxChunkSize / 2) {
                        lastPeriod + 1
                    } else {
                        val lastSpace = window.lastIndexOf(' ')
                        if (lastSpace > maxChunkSize / 2) {
                            lastSpace + 1
                        } else {
                            maxChunkSize
                        }
                    }
                }
            }
        }
    }

    suspend fun processMessage(adapter: ChannelAdapter) {
        val startTime = System.currentTimeMillis()
        var thinkingReactionAdded = false
        var typingStarted = false

        try {
            Log.i(TAG, "Processing ${adapter.channelName} message, session=${adapter.getSessionKey()}")

            // 1. Add thinking reaction
            if (adapter.supportsReactions) {
                try {
                    adapter.addThinkingReaction()
                    thinkingReactionAdded = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add thinking reaction", e)
                }
            }

            // 2. Start typing indicator
            if (adapter.supportsTyping) {
                adapter.startTyping()
                typingStarted = true
            }

            // 3. Session
            val sessionId = adapter.getSessionKey()
            if (MainEntryNew.getSessionManager() == null) {
                MainEntryNew.initialize(app)
            }
            val session = MainEntryNew.getSessionManager()?.getOrCreate(sessionId)
                ?: throw Exception("Cannot create session")

            val rawHistory = session.getRecentMessages(20)
            val contextHistory = cleanupToolMessages(rawHistory)
            Log.i(TAG, "[Session] loaded ${session.messageCount()} msgs, cleaned to ${contextHistory.size}")

            // 4a. Heartbeat filtering — skip heartbeat poll messages
            val userMessage = normalizeString(adapter.getUserMessage())
            if (isHeartbeatUserMessage(role = "user", content = userMessage)) {
                Log.i(TAG, "Heartbeat message detected, skipping agent loop")
                if (thinkingReactionAdded) {
                    try { adapter.removeThinkingReaction() } catch (_: Exception) {}
                }
                if (typingStarted) {
                    try { adapter.stopTyping() } catch (_: Exception) {}
                }
                return
            }

            // 4b. Command detection — short-circuit control commands
            if (CommandRegistry.isCommandMessage(userMessage)) {
                val resolved = CommandRegistry.resolveTextCommand(userMessage)
                if (resolved != null) {
                    Log.i(TAG, "Control command detected: ${resolved.command.key}")
                    if (thinkingReactionAdded) {
                        try { adapter.removeThinkingReaction() } catch (_: Exception) {}
                    }
                    if (typingStarted) {
                        try { adapter.stopTyping() } catch (_: Exception) {}
                    }
                    adapter.sendMessageChunk("Command /${resolved.command.key} received.", isFirstChunk = true)
                    return
                }
            }

            // 4c. System prompt
            val systemPrompt = adapter.buildSystemPrompt()

            // 5. AgentLoop
            val llmProvider = UnifiedLLMProvider(app)
            val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
            val taskDataManager = TaskDataManager.getInstance()
            val toolRegistry = ToolRegistry(app, taskDataManager)
            val androidToolRegistry = AndroidToolRegistry(
                app, taskDataManager,
                cameraCaptureManager = MyApplication.getCameraCaptureManager()
            )

            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = 40,
                modelRef = null
            )

            val result = agentLoop.run(
                systemPrompt = systemPrompt,
                userMessage = adapter.getUserMessage(),
                contextHistory = contextHistory.map { it.toNewMessage() },
                reasoningEnabled = true
            )

            // 6. Stop typing
            if (typingStarted) {
                adapter.stopTyping()
                typingStarted = false
            }

            // 7. Remove thinking reaction
            if (thinkingReactionAdded) {
                try {
                    adapter.removeThinkingReaction()
                } catch (_: Exception) {}
                thinkingReactionAdded = false
            }

            // 8. Save session
            result.messages.forEach { message ->
                session.addMessage(message.toLegacyMessage())
            }
            MainEntryNew.getSessionManager()?.save(session)
            Log.i(TAG, "[Session] saved, total=${session.messageCount()}")

            // 9. Send reply
            var replyContent = ReplyTagFilter.strip(
                ReasoningTagFilter.stripReasoningTags(
                    result.finalContent ?: "\u62b1\u6b49\uff0c\u6211\u65e0\u6cd5\u5904\u7406\u8fd9\u4e2a\u8bf7\u6c42\u3002"
                )
            )

            // 9a. Silent reply detection — suppress NO_REPLY responses
            if (isSilentReplyText(replyContent)) {
                Log.i(TAG, "Silent reply detected, suppressing outbound message")
                if (adapter.supportsReactions) {
                    try { adapter.addCompletionReaction() } catch (_: Exception) {}
                }
                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "${adapter.channelName} silent reply in ${elapsed}ms")
                return
            }

            // 9b. Strip trailing silent tokens from mixed-content replies
            replyContent = stripSilentToken(replyContent)

            if (adapter.isGroupContext()) {
                replyContent = ContextSecurityGuard.redactForSharedContext(replyContent)
            }

            val chunks = splitMessageIntoChunks(replyContent, adapter.messageCharLimit)
            for ((index, chunk) in chunks.withIndex()) {
                adapter.sendMessageChunk(chunk, isFirstChunk = index == 0)
            }

            // 10. Add completion reaction
            if (adapter.supportsReactions) {
                try {
                    adapter.addCompletionReaction()
                } catch (_: Exception) {}
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "${adapter.channelName} processed in ${elapsed}ms, ${result.iterations} iters, ${replyContent.length} chars, ${chunks.size} chunks")

        } catch (e: Exception) {
            Log.e(TAG, "${adapter.channelName} message processing failed", e)

            if (typingStarted) {
                try { adapter.stopTyping() } catch (_: Exception) {}
            }
            if (thinkingReactionAdded) {
                try { adapter.removeThinkingReaction() } catch (_: Exception) {}
            }
            if (adapter.supportsReactions) {
                try { adapter.addErrorReaction() } catch (_: Exception) {}
            }
            try {
                adapter.sendErrorMessage("\u62b1\u6b49\uff0c\u5904\u7406\u60a8\u7684\u6d88\u606f\u65f6\u9047\u5230\u9519\u8bef\uff1a${e.message}")
            } catch (_: Exception) {}
        }
    }

    private fun cleanupToolMessages(messages: List<LegacyMessage>): List<LegacyMessage> {
        return messages.filter { message ->
            when (message.role) {
                "user" -> true
                "assistant" -> message.content != null && message.toolCalls == null
                else -> false
            }
        }
    }
}
