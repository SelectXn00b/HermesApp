package com.xiaomo.hermes.agent.loop

import com.xiaomo.hermes.agent.tools.Tool
import com.xiaomo.hermes.providers.llm.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow

/**
 * AgentLoopInterface — common surface for AgentLoop and AgentLoopAdapter.
 *
 * Subagent tools (SessionsYield, SubagentsTool, etc.) and SubagentSpawner
 * reference this interface instead of the concrete AgentLoop class.
 */
interface AgentLoopInterface {
    var sessionKey: String?
    var extraTools: List<Tool>
    val progressFlow: SharedFlow<ProgressUpdate>
    val steerChannel: Channel<String>
    var conversationMessages: List<Message>
    var yieldSignal: CompletableDeferred<String?>?
    val hookRunner: com.xiaomo.hermes.agent.hook.HookRunner

    fun stop()
    fun reset()

    /**
     * Run the agent loop.
     * Returns AgentResult with finalContent, toolsUsed, messages, iterations.
     */
    suspend fun run(
        systemPrompt: String,
        userMessage: String,
        contextHistory: List<Message> = emptyList(),
        reasoningEnabled: Boolean = true,
        images: List<com.xiaomo.hermes.providers.llm.ImageBlock>? = null
    ): AgentResult
}
