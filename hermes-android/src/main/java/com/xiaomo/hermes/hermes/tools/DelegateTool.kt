package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * Delegate Tool — spawn a sub-agent to handle a task.
 *
 * Android stub: the Python version spawns child AIAgent instances with
 * their own terminal sessions (delegate_tool.py). Sub-agent spawning is
 * not available on Android, so this is a no-op.
 *
 * Ported from tools/delegate_tool.py
 */

private val _gson = Gson()

fun checkDelegateRequirements(): Boolean = false

fun delegateTask(
    task: String,
    context: String? = null,
    model: String? = null): String {
    if (task.isBlank()) {
        return _gson.toJson(mapOf("error" to "Task description is required"))
    }
    return _gson.toJson(mapOf("error" to "Delegate tool is not available on Android"))
}
