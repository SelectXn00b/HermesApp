package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * Delegate Tool — spawn a sub-agent to handle a task.
 * Ported from delegate_tool.py
 */
object DelegateTool {

    private val gson = Gson()

    data class DelegateResult(
        val success: Boolean = false,
        val output: String = "",
        val error: String? = null)

    /**
     * Callback interface for spawning sub-agents.
     */
    fun interface Delegator {
        fun delegate(task: String, context: String?, model: String?): DelegateResult
    }

    /**
     * Delegate a task to a sub-agent.
     */
    fun delegate(
        task: String,
        context: String? = null,
        model: String? = null,
        delegator: Delegator? = null): String {
        if (task.isBlank()) {
            return gson.toJson(mapOf("error" to "Task description is required"))
        }
        if (delegator == null) {
            return gson.toJson(mapOf("error" to "Delegate tool is not available in this execution context"))
        }
        return try {
            val result = delegator.delegate(task, context, model)
            if (result.success) {
                gson.toJson(mapOf("output" to result.output))
            } else {
                gson.toJson(mapOf("error" to (result.error ?: "Delegation failed")))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Delegation failed: ${e.message}"))
        }
    }


}
