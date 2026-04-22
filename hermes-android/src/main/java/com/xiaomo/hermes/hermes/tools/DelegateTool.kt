package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * Delegate Tool — spawn a sub-agent to handle a task.
 *
 * 1:1 对齐 hermes/tools/delegate_tool.py
 *
 * Android: true sub-process / sub-agent spawning is not available, so each
 * function below mirrors the Python signature but returns a safe "delegation
 * unavailable" fallback. The module-level constants are kept 1:1 so that
 * callers and introspection tooling see the same API surface as the desktop
 * Python build.
 */

// ── Module-level constants ───────────────────────────────────────────────

val DELEGATE_BLOCKED_TOOLS: Set<String> = setOf(
    "delegate_task",
    "delegate_status",
    "delegate_cancel",
)

val _EXCLUDED_TOOLSET_NAMES: Set<String> = setOf(
    "debugging", "safe", "delegation", "moa", "rl"
)

val _SUBAGENT_TOOLSETS: List<String> = listOf("terminal", "file", "web")

val _TOOLSET_LIST_STR: String = _SUBAGENT_TOOLSETS.joinToString(", ") { "'$it'" }

const val _DEFAULT_MAX_CONCURRENT_CHILDREN: Int = 3

const val MAX_DEPTH: Int = 2

const val DEFAULT_MAX_ITERATIONS: Int = 50

const val _HEARTBEAT_INTERVAL: Int = 30

val DEFAULT_TOOLSETS: List<String> = listOf("terminal", "file", "web")

val DELEGATE_TASK_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "goal" to mapOf("type" to "string"),
        "toolsets" to mapOf(
            "type" to "array",
            "items" to mapOf("type" to "string"),
        ),
    ),
    "required" to listOf("goal"),
)

private val _gson = Gson()


// ── Module-level functions (Android fallbacks) ───────────────────────────

fun _getMaxConcurrentChildren(): Int {
    val raw = System.getenv("HERMES_DELEGATE_MAX_CONCURRENT")?.trim()
    return raw?.toIntOrNull()?.coerceAtLeast(1) ?: _DEFAULT_MAX_CONCURRENT_CHILDREN
}

fun checkDelegateRequirements(): Boolean = false

fun _buildChildSystemPrompt(
    goal: String,
    toolsets: List<String> = DEFAULT_TOOLSETS,
    workspaceHint: String? = null,
): String {
    val hint = workspaceHint?.let { "\nWorkspace: $it" } ?: ""
    val tsetLine = toolsets.joinToString(", ") { "'$it'" }
    return "You are a Hermes delegated sub-agent.$hint\nGoal: $goal\nAllowed toolsets: $tsetLine\n"
}

fun _resolveWorkspaceHint(parentAgent: Any?): String? = null

fun _stripBlockedTools(toolsets: List<String>): List<String> {
    return toolsets.filter { it !in DELEGATE_BLOCKED_TOOLS && it !in _EXCLUDED_TOOLSET_NAMES }
}

fun _buildChildProgressCallback(
    taskIndex: Int,
    goal: String,
    parentAgent: Any?,
    taskCount: Int = 1,
): ((String) -> Unit)? = null

fun _buildChildAgent(
    goal: String,
    toolsets: List<String> = DEFAULT_TOOLSETS,
    parentAgent: Any? = null,
    model: String? = null,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
): Any? = null

fun _runSingleChild(
    goal: String,
    toolsets: List<String> = DEFAULT_TOOLSETS,
    parentAgent: Any? = null,
    model: String? = null,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    taskIndex: Int = 0,
    taskCount: Int = 1,
): Map<String, Any?> {
    return mapOf(
        "ok" to false,
        "error" to "Delegate tool is not available on Android",
        "goal" to goal,
    )
}

fun delegateTask(
    task: String,
    context: String? = null,
    model: String? = null,
): String {
    if (task.isBlank()) {
        return _gson.toJson(mapOf("error" to "Task description is required"))
    }
    return _gson.toJson(mapOf("error" to "Delegate tool is not available on Android"))
}

fun _resolveChildCredentialPool(
    effectiveProvider: String?,
    parentAgent: Any?,
): Any? = null

fun _resolveDelegationCredentials(
    cfg: Map<String, Any?>,
    parentAgent: Any?,
): Map<String, Any?> = emptyMap()

fun _loadConfig(): Map<String, Any?> = emptyMap()
