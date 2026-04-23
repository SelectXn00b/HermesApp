/** 1:1 对齐 hermes/acp_adapter/events.py */
package com.xiaomo.hermes.hermes.acp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Callback factories for bridging AIAgent events to ACP notifications.
 *
 * Each factory returns a callable with the signature that AIAgent expects
 * for its callbacks. On Android, callbacks are dispatched via coroutines
 * instead of asyncio.run_coroutine_threadsafe.
 */
object Events {

    private const val _TAG = "ACP.Events"

    /**
     * Fire-and-forget an ACP session update from a worker thread.
     *
     * Python: uses asyncio.run_coroutine_threadsafe + future.result(timeout=5)
     * Kotlin: launches a coroutine in the provided scope.
     */
    private fun _sendUpdate(
        @Suppress("UNUSED_PARAMETER") conn: Any?,
        sessionId: String,
        scope: CoroutineScope,
        update: Any
    ) {
        scope.launch {
            try {
                // ACP session update dispatch placeholder.
                // Python: conn.session_update(session_id, update)
                Log.d(_TAG, "ACP update sent for session $sessionId: $update")
            } catch (e: Exception) {
                Log.d(_TAG, "Failed to send ACP update: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Tool progress callback
    // ------------------------------------------------------------------

    /**
     * Create a tool_progress_callback for AIAgent.
     *
     * Signature expected by AIAgent:
     *     tool_progress_callback(eventType, name, preview, args, **kwargs)
     *
     * Emits ToolCallStart for "tool.started" events and tracks IDs in a FIFO
     * queue per tool name so duplicate/parallel same-name calls still complete
     * against the correct ACP tool call.
     */
    fun makeToolProgressCb(
        conn: Any?,
        sessionId: String,
        scope: CoroutineScope,
        toolCallIds: MutableMap<String, ArrayDeque<String>>,
        toolCallMeta: MutableMap<String, MutableMap<String, Any?>>
    ): (eventType: String, name: String?, preview: String?, args: Any?) -> Unit {

        return { eventType: String, name: String?, preview: String?, args: Any? ->
            // Only emit ACP ToolCallStart for tool.started; ignore other event types
            if (eventType == "tool.started" && name != null) {
                var parsedArgs: Map<String, Any?> = emptyMap()
                when (args) {
                    is String -> {
                        parsedArgs = try {
                            val jsonObj = JSONObject(args)
                            jsonObj.keys().asSequence().associateWith { key -> jsonObj.opt(key) }
                        } catch (e: Exception) {
                            mapOf("raw" to args)
                        }
                    }
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        parsedArgs = args as Map<String, Any?>
                    }
                }

                val tcId = Tools.makeToolCallId()
                val queue = toolCallIds.getOrPut(name) { ArrayDeque() }
                queue.add(tcId)

                // Snapshot capture for edit tools
                var snapshot: Any? = null
                if (name in listOf("write_file", "patch", "skill_manage")) {
                    try {
                        // Python: capture_local_edit_snapshot(name, args)
                        // Android: placeholder — edit snapshot not yet implemented
                        snapshot = null
                    } catch (e: Exception) {
                        Log.d(_TAG, "Failed to capture ACP edit snapshot for $name: ${e.message}")
                    }
                }
                toolCallMeta[tcId] = mutableMapOf("args" to parsedArgs, "snapshot" to snapshot)

                val update = Tools.buildToolStart(tcId, name, parsedArgs)
                _sendUpdate(conn, sessionId, scope, update)
            }
        }
    }

    // ------------------------------------------------------------------
    // Thinking callback
    // ------------------------------------------------------------------

    /**
     * Create a thinking_callback for AIAgent.
     */
    fun makeThinkingCb(
        conn: Any?,
        sessionId: String,
        scope: CoroutineScope
    ): (text: String) -> Unit {

        return { text: String ->
            if (text.isNotEmpty()) {
                // Python: acp.update_agent_thought_text(text)
                val update = mapOf("type" to "agent_thought_text", "text" to text)
                _sendUpdate(conn, sessionId, scope, update)
            }
        }
    }

    // ------------------------------------------------------------------
    // Step callback
    // ------------------------------------------------------------------

    /**
     * Create a step_callback for AIAgent.
     *
     * Signature expected by AIAgent:
     *     step_callback(api_call_count, prev_tools)
     */
    fun makeStepCb(
        conn: Any?,
        sessionId: String,
        scope: CoroutineScope,
        toolCallIds: MutableMap<String, ArrayDeque<String>>,
        toolCallMeta: MutableMap<String, MutableMap<String, Any?>>
    ): (apiCallCount: Int, prevTools: Any?) -> Unit {

        return { _: Int, prevTools: Any? ->
            if (prevTools is List<*>) {
                for (toolInfo in prevTools) {
                    var toolName: String? = null
                    var result: Any? = null
                    var functionArgs: Map<String, Any?>? = null

                    when (toolInfo) {
                        is Map<*, *> -> {
                            toolName = (toolInfo["name"] ?: toolInfo["function_name"]) as? String
                            result = toolInfo["result"] ?: toolInfo["output"]
                            @Suppress("UNCHECKED_CAST")
                            functionArgs = (toolInfo["arguments"] ?: toolInfo["args"]) as? Map<String, Any?>
                        }
                        is String -> {
                            toolName = toolInfo
                        }
                    }

                    val queue = toolCallIds[toolName ?: ""]
                    if (toolName != null && queue != null && queue.isNotEmpty()) {
                        val tcId = queue.poll()!!
                        val meta = toolCallMeta.remove(tcId) ?: emptyMap()
                        @Suppress("UNCHECKED_CAST")
                        val update = Tools.buildToolComplete(
                            tcId,
                            toolName,
                            result = result?.toString(),
                            functionArgs = functionArgs ?: meta["args"] as? Map<String, Any?>,
                            snapshot = meta["snapshot"]
                        )
                        _sendUpdate(conn, sessionId, scope, update)
                        if (queue.isEmpty()) {
                            toolCallIds.remove(toolName)
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Agent message callback
    // ------------------------------------------------------------------

    /**
     * Create a callback that streams agent response text to the editor.
     */
    fun makeMessageCb(
        conn: Any?,
        sessionId: String,
        scope: CoroutineScope
    ): (text: String) -> Unit {

        return { text: String ->
            if (text.isNotEmpty()) {
                // Python: acp.update_agent_message_text(text)
                val update = mapOf("type" to "agent_message_text", "text" to text)
                _sendUpdate(conn, sessionId, scope, update)
            }
        }
    }
}
