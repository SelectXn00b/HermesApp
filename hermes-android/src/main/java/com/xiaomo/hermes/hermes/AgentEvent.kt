package com.xiaomo.hermes.hermes

/**
 * Structured events emitted by [HermesAgentLoop] during a turn.
 *
 * Subscribers can render:
 *  - [Thinking]         — model reasoning before the visible reply
 *  - [AssistantDelta]   — assistant text for one turn (may be partial today
 *                         since the underlying [ChatCompletionServer] returns
 *                         a full non-streamed reply; emit as a single chunk)
 *  - [ToolCallStart] / [ToolCallEnd] — bracket each tool invocation
 *  - [Final]            — terminal event for a successful turn
 *  - [Error]            — terminal event for a failed turn
 */
sealed class AgentEvent {
    data class Thinking(val text: String, val turn: Int) : AgentEvent()

    data class AssistantDelta(val text: String, val turn: Int) : AgentEvent()

    data class ToolCallStart(
        val toolCallId: String,
        val name: String,
        val argsJson: String,
        val turn: Int
    ) : AgentEvent()

    data class ToolCallEnd(
        val toolCallId: String,
        val name: String,
        val resultJson: String,
        val error: String?,
        val turn: Int
    ) : AgentEvent()

    data class Final(
        val text: String,
        val turnsUsed: Int,
        val finishedNaturally: Boolean
    ) : AgentEvent()

    data class Error(val message: String, val turn: Int) : AgentEvent()
}

typealias AgentEventSink = suspend (AgentEvent) -> Unit
