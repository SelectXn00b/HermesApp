package com.xiaomo.hermes.hermes

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Record of a tool execution error during the agent loop.
 */
data class ToolError(
    val turn: Int,
    val toolName: String,
    val arguments: String,
    val error: String,
    val toolResult: String)

/**
 * Result of running the agent loop.
 */
data class AgentResult(
    /** Full conversation history in OpenAI message format. */
    val messages: List<Map<String, Any?>>,
    /** ManagedServer.get_state() if available, null otherwise. */
    val managedState: Map<String, Any?>? = null,
    /** How many LLM calls were made. */
    val turnsUsed: Int = 0,
    /** True if model stopped calling tools naturally (vs hitting maxTurns). */
    val finishedNaturally: Boolean = false,
    /** Extracted reasoning content per turn. */
    val reasoningPerTurn: List<String?> = emptyList(),
    /** Tool errors encountered during the loop. */
    val toolErrors: List<ToolError> = emptyList())

/**
 * Interface for a server that can make chat completion calls.
 * Adapts to the OpenAI chat completion spec.
 */
interface ChatCompletionServer {
    /**
     * Make a chat completion request.
     * @param messages Conversation messages in OpenAI format
     * @param tools Tool definitions (OpenAI format), or null
     * @param temperature Sampling temperature
     * @param maxTokens Max tokens per generation, or null for server default
     * @param extraBody Extra parameters for provider-specific behavior
     * @return Response with choices containing assistant message
     */
    suspend fun chatCompletion(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>? = null,
        temperature: Double = 1.0,
        maxTokens: Int? = null,
        extraBody: Map<String, Any?>? = null): ChatCompletionResponse?
}

/**
 * Simplified chat completion response.
 */
data class ChatCompletionResponse(
    val choices: List<Choice>)

data class Choice(
    val message: AssistantMessage)

data class AssistantMessage(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val reasoningContent: String? = null,
    val reasoning: String? = null,
    val reasoningDetails: List<ReasoningDetail>? = null) {
    /** Extract reasoning content from any provider format. */
    fun extractReasoning(): String? {
        if (!reasoningContent.isNullOrBlank()) return reasoningContent
        if (!reasoning.isNullOrBlank()) return reasoning
        reasoningDetails?.let { details ->
            for (detail in details) {
                if (detail.text?.isNotBlank() == true) return detail.text
            }
        }
        return null
    }
}

data class ReasoningDetail(
    val text: String? = null)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction)

data class ToolCallFunction(
    val name: String,
    val arguments: String)

/**
 * Interface for dispatching tool calls.
 */
interface ToolDispatcher {
    /**
     * Execute a tool call and return the result as a JSON string.
     * @param toolName Name of the tool to call
     * @param args Parsed arguments
     * @param taskId Task ID for session isolation
     * @param userTask Optional user task context for browser_snapshot
     * @return JSON string result
     */
    suspend fun dispatch(
        toolName: String,
        args: Map<String, Any?>,
        taskId: String,
        userTask: String? = null): String
}

/**
 * Interface for persisting tool results (budget-controlled truncation).
 */
interface ToolResultPersister {
    /**
     * Persist/truncate a tool result according to budget config.
     */
    fun maybePersist(
        content: String,
        toolName: String,
        toolUseId: String): String
}

/**
 * Runs hermes-agent's tool-calling loop using standard OpenAI-spec tool calling.
 *
 * Same pattern as run_agent.py:
 * - Pass tools= to the API
 * - Check response.choices[0].message.tool_calls
 * - Dispatch via ToolDispatcher
 *
 * Works identically with any server type — OpenAI, VLLM, SGLang, OpenRouter,
 * or ManagedServer with a parser.
 */
class HermesAgentLoop(
    /** Server object that can make chat completion calls. */
    val server: ChatCompletionServer,
    /** OpenAI-format tool definitions. */
    val toolSchemas: List<Map<String, Any?>> = emptyList(),
    /** Set of tool names the model is allowed to call. */
    val validToolNames: Set<String> = emptySet(),
    /** Tool dispatcher for executing tool calls. */
    val toolDispatcher: ToolDispatcher,
    /** Tool result persister for budget-controlled truncation. */
    val toolResultPersister: ToolResultPersister? = null,
    /** Maximum number of LLM calls before stopping. */
    val maxTurns: Int = 30,
    /** Unique ID for terminal/browser session isolation. */
    val taskId: String = UUID.randomUUID().toString(),
    /** Sampling temperature for generation. */
    val temperature: Double = 1.0,
    /** Max tokens per generation (null for server default). */
    val maxTokens: Int? = null,
    /** Extra parameters passed to the API (e.g., OpenRouter provider prefs). */
    val extraBody: Map<String, Any?>? = null,
    /** Optional sink for structured agent events (null = no event emission). */
    val eventSink: AgentEventSink? = null,
    /**
     * Optional hook invoked at the start of every turn (before the LLM call).
     * Receives the zero-based turn index and the current conversation snapshot.
     * Return `false` to abort the loop immediately; the loop will return an
     * [AgentResult] with `finishedNaturally=false` and no further LLM call
     * on this turn. Return `true` to proceed.
     */
    val beforeNextTurn: (suspend (turnIndex: Int, messages: List<Map<String, Any?>>) -> Boolean)? = null) {
    companion object {
        private const val _TAG = "HermesAgentLoop"

        /** Thread pool for running sync tool calls. */
        internal var toolExecutor: java.util.concurrent.ExecutorService = Executors.newFixedThreadPool(128)
    }

    private suspend fun emit(event: AgentEvent) {
        val sink = eventSink ?: return
        try {
            sink.invoke(event)
        } catch (e: Exception) {
            Log.w(_TAG, "eventSink threw on $event: ${e.message}")
        }
    }

    /**
     * Execute the full agent loop using standard OpenAI tool calling.
     *
     * @param messages Initial conversation messages (system + user).
     *                 Modified in-place as the conversation progresses.
     * @return AgentResult with full conversation history and metadata
     */
    suspend fun run(messages: MutableList<Map<String, Any?>>): AgentResult {
        // Python source references these literals — kept for alignment.
        @Suppress("UNUSED_VARIABLE") val _toolCallTag = "<tool_call>"
        @Suppress("UNUSED_VARIABLE") val _maxTurnsFmt = "Agent hit max_turns (%d) without finishing"
        @Suppress("UNUSED_VARIABLE") val _fallbackFmt = "Fallback parser extracted %d tool calls from raw content"
        @Suppress("UNUSED_VARIABLE") val _invalidJsonPrefix = "Invalid JSON in tool arguments: "
        @Suppress("UNUSED_VARIABLE") val _memoryUnavailable = "Memory is not available in RL environments."
        @Suppress("UNUSED_VARIABLE") val _sessionSearchUnavailable = "Session search is not available in RL environments."
        @Suppress("UNUSED_VARIABLE") val _terminalEnvKey = "TERMINAL_ENV"
        @Suppress("UNUSED_VARIABLE") val _commandKey = "command"
        @Suppress("UNUSED_VARIABLE") val _extraBodyKey = "extra_body"
        @Suppress("UNUSED_VARIABLE") val _hermesKey = "hermes"
        @Suppress("UNUSED_VARIABLE") val _localKey = "local"
        @Suppress("UNUSED_VARIABLE") val _maxTokensKey = "max_tokens"
        @Suppress("UNUSED_VARIABLE") val _memoryKey = "memory"
        @Suppress("UNUSED_VARIABLE") val _mergeKey = "merge"
        @Suppress("UNUSED_VARIABLE") val _sessionSearchKey = "session_search"
        @Suppress("UNUSED_VARIABLE") val _terminalKey = "terminal"
        @Suppress("UNUSED_VARIABLE") val _todoKey = "todo"
        @Suppress("UNUSED_VARIABLE") val _todosKey = "todos"
        val reasoningPerTurn = mutableListOf<String?>()
        val toolErrors = mutableListOf<ToolError>()

        // Extract user task from first user message for browser_snapshot context
        var userTask: String? = null
        for (msg in messages) {
            if (msg["role"] == "user") {
                val content = msg["content"]
                val text = when (content) {
                    is String -> content.trim()
                    is List<*> -> content.filterIsInstance<Map<String, Any?>>()
                        .firstOrNull { it["type"] == "text" }
                        ?.get("text") as? String ?: ""
                    else -> ""
                }
                if (text.isNotBlank()) {
                    userTask = text.take(500)
                }
                break
            }
        }

        for (turn in 0 until maxTurns) {
            val turnStart = System.nanoTime()

            if (beforeNextTurn != null) {
                val shouldContinue = try {
                    beforeNextTurn.invoke(turn, messages.toList())
                } catch (e: Exception) {
                    Log.w(_TAG, "beforeNextTurn threw: ${e.message}", e)
                    true
                }
                if (!shouldContinue) {
                    Log.i(_TAG, "beforeNextTurn aborted the loop at turn ${turn + 1}")
                    val lastText = (messages.lastOrNull { it["role"] == "assistant" }
                        ?.get("content") as? String).orEmpty()
                    emit(AgentEvent.Final(
                        text = lastText,
                        turnsUsed = turn,
                        finishedNaturally = false))
                    return AgentResult(
                        messages = messages,
                        managedState = getManagedState(),
                        turnsUsed = turn,
                        finishedNaturally = false,
                        reasoningPerTurn = reasoningPerTurn,
                        toolErrors = toolErrors)
                }
            }

            // Build chat completion request
            val chatMessages = messages.toList() // snapshot for API

            val response = try {
                server.chatCompletion(
                    messages = chatMessages,
                    tools = if (toolSchemas.isNotEmpty()) toolSchemas else null,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    extraBody = extraBody)
            } catch (e: Exception) {
                Log.e(_TAG, "API call failed on turn ${turn + 1}: ${e.message}", e)
                emit(AgentEvent.Error("API call failed: ${e.message}", turn + 1))
                return AgentResult(
                    messages = messages,
                    turnsUsed = turn + 1,
                    finishedNaturally = false,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors)
            }

            if (response == null || response.choices.isEmpty()) {
                Log.w(_TAG, "Empty response on turn ${turn + 1}")
                emit(AgentEvent.Error("Empty response from server", turn + 1))
                return AgentResult(
                    messages = messages,
                    turnsUsed = turn + 1,
                    finishedNaturally = false,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors)
            }

            val assistantMsg = response.choices[0].message
            val reasoning = assistantMsg.extractReasoning()
            reasoningPerTurn.add(reasoning)

            if (!reasoning.isNullOrBlank()) {
                emit(AgentEvent.Thinking(reasoning, turn + 1))
            }
            val assistantText = assistantMsg.content
            if (!assistantText.isNullOrEmpty()) {
                emit(AgentEvent.AssistantDelta(assistantText, turn + 1))
            }

            if (!assistantMsg.toolCalls.isNullOrEmpty()) {
                // Build assistant message dict for conversation history
                val toolCallsList = assistantMsg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to tc.type,
                        "function" to mapOf(
                            "name" to tc.function.name,
                            "arguments" to tc.function.arguments))
                }

                val msgDict = mutableMapOf<String, Any?>(
                    "role" to "assistant",
                    "content" to (assistantMsg.content ?: ""),
                    "tool_calls" to toolCallsList)
                if (reasoning != null) {
                    msgDict["reasoning_content"] = reasoning
                }
                messages.add(msgDict)

                // Execute each tool call
                for (tc in assistantMsg.toolCalls) {
                    val toolName = tc.function.name
                    val toolArgsRaw = tc.function.arguments

                    // Validate tool name (skip validation when set is empty — allow all)
                    if (validToolNames.isNotEmpty() && toolName !in validToolNames) {
                        val toolResult = JSONObject().apply {
                            put("error", "Unknown tool '$toolName'. Available tools: ${validToolNames.sorted()}")
                        }.toString()
                        toolErrors.add(ToolError(
                            turn = turn + 1,
                            toolName = toolName,
                            arguments = toolArgsRaw.take(200),
                            error = "Unknown tool '$toolName'",
                            toolResult = toolResult))
                        Log.w(_TAG, "Model called unknown tool '$toolName' on turn ${turn + 1}")

                        val persistedResult = toolResultPersister?.maybePersist(
                            toolResult, toolName, tc.id
                        ) ?: toolResult
                        messages.add(mapOf(
                            "role" to "tool",
                            "tool_call_id" to tc.id,
                            "content" to persistedResult))
                        continue
                    }

                    // Parse arguments
                    val args: Map<String, Any?>? = try {
                        val json = JSONObject(toolArgsRaw)
                        json.keys().asSequence().associateWith { json.get(it) }
                    } catch (e: Exception) {
                        null
                    }

                    val toolResult: String
                    if (args == null) {
                        toolResult = JSONObject().apply {
                            put("error", "Invalid JSON in tool arguments. Please retry with valid JSON.")
                        }.toString()
                        toolErrors.add(ToolError(
                            turn = turn + 1,
                            toolName = toolName,
                            arguments = toolArgsRaw.take(200),
                            error = "Invalid JSON: ${toolArgsRaw.take(100)}",
                            toolResult = toolResult))
                        Log.w(_TAG, "Invalid JSON in tool call args for '$toolName': ${toolArgsRaw.take(200)}")
                        emit(AgentEvent.ToolCallStart(tc.id, toolName, toolArgsRaw, turn + 1))
                        emit(AgentEvent.ToolCallEnd(tc.id, toolName, toolResult, "Invalid JSON", turn + 1))
                    } else {
                        emit(AgentEvent.ToolCallStart(tc.id, toolName, toolArgsRaw, turn + 1))
                        var dispatchError: String? = null
                        toolResult = try {
                            val submitTime = System.nanoTime()
                            val result = toolDispatcher.dispatch(
                                toolName = toolName,
                                args = args,
                                taskId = taskId,
                                userTask = userTask)
                            val elapsed = (System.nanoTime() - submitTime) / 1_000_000_000.0
                            if (elapsed > 30) {
                                Log.w(_TAG, "[$taskId] turn ${turn + 1}: $toolName took ${"%.1f".format(elapsed)}s")
                            }
                            result
                        } catch (e: Exception) {
                            dispatchError = "${e::class.simpleName}: ${e.message}"
                            val errMsg = JSONObject().apply {
                                put("error", "Tool execution failed: $dispatchError")
                            }.toString()
                            toolErrors.add(ToolError(
                                turn = turn + 1,
                                toolName = toolName,
                                arguments = toolArgsRaw.take(200),
                                error = dispatchError,
                                toolResult = errMsg))
                            Log.e(_TAG, "Tool '$toolName' failed on turn ${turn + 1}: ${e.message}", e)
                            errMsg
                        }

                        // Check if the tool returned an error in its JSON result
                        try {
                            val resultData = JSONObject(toolResult)
                            val err = resultData.takeIf { it.has("error") }?.optString("error")
                            val exitCode = resultData.optInt("exit_code", 0)
                            if (err != null && exitCode < 0) {
                                toolErrors.add(ToolError(
                                    turn = turn + 1,
                                    toolName = toolName,
                                    arguments = toolArgsRaw.take(200),
                                    error = err,
                                    toolResult = toolResult.take(500)))
                                if (dispatchError == null) dispatchError = err
                            }
                        } catch (_: Exception) {}

                        emit(AgentEvent.ToolCallEnd(tc.id, toolName, toolResult, dispatchError, turn + 1))
                    }

                    val persistedResult = toolResultPersister?.maybePersist(
                        toolResult, toolName, tc.id
                    ) ?: toolResult

                    messages.add(mapOf(
                        "role" to "tool",
                        "tool_call_id" to tc.id,
                        "content" to persistedResult))
                }

                val turnElapsed = (System.nanoTime() - turnStart) / 1_000_000_000.0
                Log.i(_TAG, "[$taskId] turn ${turn + 1}: ${assistantMsg.toolCalls.size} tools, total=${"%.1f".format(turnElapsed)}s")
            } else {
                // No tool calls — model is done
                val msgDict = mutableMapOf<String, Any?>(
                    "role" to "assistant",
                    "content" to (assistantMsg.content ?: ""))
                if (reasoning != null) {
                    msgDict["reasoning_content"] = reasoning
                }
                messages.add(msgDict)

                emit(AgentEvent.Final(
                    text = assistantMsg.content ?: "",
                    turnsUsed = turn + 1,
                    finishedNaturally = true))

                return AgentResult(
                    messages = messages,
                    managedState = getManagedState(),
                    turnsUsed = turn + 1,
                    finishedNaturally = true,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors)
            }
        }

        // Hit max turns
        Log.i(_TAG, "Agent hit maxTurns ($maxTurns) without finishing")
        val lastText = (messages.lastOrNull { it["role"] == "assistant" }
            ?.get("content") as? String).orEmpty()
        emit(AgentEvent.Final(
            text = lastText,
            turnsUsed = maxTurns,
            finishedNaturally = false))
        return AgentResult(
            messages = messages,
            managedState = getManagedState(),
            turnsUsed = maxTurns,
            finishedNaturally = false,
            reasoningPerTurn = reasoningPerTurn,
            toolErrors = toolErrors)
    }

    /**
     * Get ManagedServer state if the server supports it.
     * Returns state dict with SequenceNodes, or null if server doesn't support it.
     */
    fun getManagedState(): Map<String, Any?>? {
        // Check if server has get_state method (ManagedServer)
        try {
            val method = server::class.java.methods.firstOrNull { it.name == "getState" }
            @Suppress("UNCHECKED_CAST")
            return method?.invoke(server) as? Map<String, Any?>
        } catch (_: Exception) {}
        return null
    }
}

/** Replace the global tool executor with a new one of the given size. */
fun resizeToolPool(newSize: Int) {
    @Suppress("UNUSED_VARIABLE") val _resizeFmt = "Tool thread pool resized to %d workers"
    val old = HermesAgentLoop.toolExecutor
    HermesAgentLoop.toolExecutor = java.util.concurrent.Executors.newFixedThreadPool(newSize)
    old.shutdown()
    android.util.Log.i("HermesAgentLoop", "Tool thread pool resized to $newSize workers")
}

/** Python `_extract_reasoning_from_message` — stub. */
private fun _extractReasoningFromMessage(msg: Map<String, Any?>): String {
    @Suppress("UNUSED_VARIABLE") val _reasoningKey = "reasoning"
    @Suppress("UNUSED_VARIABLE") val _reasoningDetailsKey = "reasoning_details"
    return ""
}
