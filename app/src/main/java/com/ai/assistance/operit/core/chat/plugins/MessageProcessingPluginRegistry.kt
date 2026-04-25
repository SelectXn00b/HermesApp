package com.ai.assistance.operit.core.chat.plugins

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.util.stream.Stream
import java.util.concurrent.CopyOnWriteArrayList

data class MessageProcessingHookParams(
    val context: Context,
    val enhancedAIService: EnhancedAIService,
    val messageContent: String,
    val chatHistory: List<PromptTurn>,
    val workspacePath: String?,
    val maxTokens: Int,
    val tokenUsageThreshold: Double,
    val onNonFatalError: suspend (error: String) -> Unit
)

interface MessageProcessingController {
    fun cancel()
}

data class MessageProcessingExecution(
    val controller: MessageProcessingController,
    val stream: Stream<String>
)

interface MessageProcessingPlugin {
    val id: String

    suspend fun createExecutionIfMatched(
        params: MessageProcessingHookParams
    ): MessageProcessingExecution?
}

object MessageProcessingPluginRegistry {
    private val plugins = CopyOnWriteArrayList<MessageProcessingPlugin>()

    @Synchronized
    fun register(plugin: MessageProcessingPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
    }

    @Synchronized
    fun unregister(pluginId: String) {
        plugins.removeAll { it.id == pluginId }
    }

    suspend fun createExecutionIfMatched(
        params: MessageProcessingHookParams
    ): MessageProcessingExecution? {
        for (plugin in plugins) {
            val execution = plugin.createExecutionIfMatched(params)
            if (execution != null) {
                return execution
            }
        }
        return null
    }
}
