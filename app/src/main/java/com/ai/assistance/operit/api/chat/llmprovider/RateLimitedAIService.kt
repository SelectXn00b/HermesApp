package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.sync.Semaphore

class RateLimitedAIService(
    private val delegate: AIService,
    private val rateLimiter: SlidingWindowRateLimiter?,
    private val concurrencySemaphore: Semaphore?
) : AIService by delegate {

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean
    ): Stream<String> = com.ai.assistance.operit.util.stream.stream {
        rateLimiter?.acquire()
        concurrencySemaphore?.acquire()

        try {
            delegate.sendMessage(
                context = context,
                chatHistory = chatHistory,
                modelParameters = modelParameters,
                enableThinking = enableThinking,
                stream = stream,
                availableTools = availableTools,
                preserveThinkInHistory = preserveThinkInHistory,
                onTokensUpdated = onTokensUpdated,
                onNonFatalError = onNonFatalError,
                enableRetry = enableRetry
            ).collect { chunk ->
                emit(chunk)
            }
        } finally {
            concurrencySemaphore?.release()
        }
    }
}
