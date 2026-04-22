package com.xiaomo.hermes.hermes

/**
 * Model Tools Module
 *
 * Thin orchestration layer over the tool registry. Each tool file in
 * `tools/` self-registers its schema, handler, and metadata via
 * `registry.register()`. This module triggers discovery (by importing all
 * tool modules), then provides the public API that AgentLoop, Cli,
 * BatchRunner, and the RL environments consume.
 *
 * Ported from model_tools.py
 */

import android.util.Log
import com.xiaomo.hermes.hermes.tools.registry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private const val _TAG = "model_tools"

// =============================================================================
// Async Bridging  (single source of truth -- used by registry.dispatch too)
// =============================================================================

/**
 * Run an async coroutine from a sync context.
 *
 * Kotlin coroutines handle the dispatcher/loop story differently from
 * Python's asyncio — `runBlocking` is the sync↔async bridge and is safe to
 * call from either the main thread or a worker thread.
 */
fun <T> _runAsync(block: suspend () -> T): T {
    return runBlocking { block() }
}

// =============================================================================
// Tool Discovery  (importing each module triggers its registry.register calls)
// =============================================================================

/**
 * Trigger module-level tool registrations.  Kotlin has no Python-style
 * import side-effects, so each tool file must be explicitly touched once at
 * startup for its top-level `registry.register(...)` calls to run.
 *
 * TODO: enumerate each tool module here once they're all ported.
 */
fun _discoverBuiltinTools() {
    // TODO: port tools.registry.discover_builtin_tools — each ToolXxx.kt
    // must be referenced once to run its module init.
}

// =============================================================================
// Backward-compat constants  (built once after discovery)
// =============================================================================

val TOOL_TO_TOOLSET_MAP: Map<String, String>
    get() = registry.getToolToToolsetMap()

val TOOLSET_REQUIREMENTS: Map<String, Map<String, Any?>>
    get() = registry.getToolsetRequirements()

/**
 * Resolved tool names from the last `getToolDefinitions()` call. Used by
 * code_execution_tool to know which tools are available in this session.
 */
private var _lastResolvedToolNames: List<String> = emptyList()

// =============================================================================
// Legacy toolset name mapping  (old _tools-suffixed names -> tool name lists)
// =============================================================================

val _LEGACY_TOOLSET_MAP: Map<String, List<String>> = mapOf(
    "web_tools" to listOf("web_search", "web_extract"),
    "terminal_tools" to listOf("terminal"),
    "vision_tools" to listOf("vision_analyze"),
    "moa_tools" to listOf("mixture_of_agents"),
    "image_tools" to listOf("image_generate"),
    "skills_tools" to listOf("skills_list", "skill_view", "skill_manage"),
    "browser_tools" to listOf(
        "browser_navigate", "browser_snapshot", "browser_click",
        "browser_type", "browser_scroll", "browser_back",
        "browser_press", "browser_get_images",
        "browser_vision", "browser_console"),
    "cronjob_tools" to listOf("cronjob"),
    "rl_tools" to listOf(
        "rl_list_environments", "rl_select_environment",
        "rl_get_current_config", "rl_edit_config",
        "rl_start_training", "rl_check_status",
        "rl_stop_training", "rl_get_results",
        "rl_list_runs", "rl_test_inference"),
    "file_tools" to listOf("read_file", "write_file", "patch", "search_files"),
    "tts_tools" to listOf("text_to_speech"))

// =============================================================================
// getToolDefinitions  (the main schema provider)
// =============================================================================

/**
 * Get tool definitions for model API calls with toolset-based filtering.
 *
 * All tools must be part of a toolset to be accessible.
 */
@Suppress("UNCHECKED_CAST")
fun getToolDefinitions(
    enabledToolsets: List<String>? = null,
    disabledToolsets: List<String>? = null,
    quietMode: Boolean = false): List<Map<String, Any?>> {
    val toolsToInclude = mutableSetOf<String>()

    if (enabledToolsets != null) {
        for (toolsetName in enabledToolsets) {
            if (validateToolset(toolsetName)) {
                val resolved = resolveToolset(toolsetName)
                toolsToInclude.addAll(resolved)
                if (!quietMode) {
                    val msg = if (resolved.isNotEmpty()) resolved.joinToString(", ") else "no tools"
                    println("✅ Enabled toolset '$toolsetName': $msg")
                }
            } else if (toolsetName in _LEGACY_TOOLSET_MAP) {
                val legacyTools = _LEGACY_TOOLSET_MAP[toolsetName] ?: emptyList()
                toolsToInclude.addAll(legacyTools)
                if (!quietMode) {
                    println("✅ Enabled legacy toolset '$toolsetName': ${legacyTools.joinToString(", ")}")
                }
            } else {
                if (!quietMode) println("⚠️  Unknown toolset: $toolsetName")
            }
        }
    } else if (!disabledToolsets.isNullOrEmpty()) {
        for (tsName in getAllToolsets().keys) {
            toolsToInclude.addAll(resolveToolset(tsName))
        }
        for (toolsetName in disabledToolsets) {
            if (validateToolset(toolsetName)) {
                val resolved = resolveToolset(toolsetName)
                toolsToInclude.removeAll(resolved.toSet())
                if (!quietMode) {
                    val msg = if (resolved.isNotEmpty()) resolved.joinToString(", ") else "no tools"
                    println("🚫 Disabled toolset '$toolsetName': $msg")
                }
            } else if (toolsetName in _LEGACY_TOOLSET_MAP) {
                val legacyTools = _LEGACY_TOOLSET_MAP[toolsetName] ?: emptyList()
                toolsToInclude.removeAll(legacyTools.toSet())
                if (!quietMode) {
                    println("🚫 Disabled legacy toolset '$toolsetName': ${legacyTools.joinToString(", ")}")
                }
            } else {
                if (!quietMode) println("⚠️  Unknown toolset: $toolsetName")
            }
        }
    } else {
        for (tsName in getAllToolsets().keys) {
            toolsToInclude.addAll(resolveToolset(tsName))
        }
    }

    val filteredTools = registry.getDefinitions(toolsToInclude, quiet = quietMode).toMutableList()

    val availableToolNames = filteredTools.mapNotNull { t ->
        ((t["function"] as? Map<String, Any?>)?.get("name")) as? String
    }.toMutableSet()

    // TODO: execute_code dynamic schema rebuild (requires build_execute_code_schema port).
    // TODO: discord_server dynamic schema rebuild (requires discord_tool.get_dynamic_schema port).
    // TODO: browser_navigate description stripping when web tools are absent.

    if (!quietMode) {
        if (filteredTools.isNotEmpty()) {
            val toolNames = filteredTools.mapNotNull { t ->
                ((t["function"] as? Map<String, Any?>)?.get("name")) as? String
            }
            println("🛠️  Final tool selection (${filteredTools.size} tools): ${toolNames.joinToString(", ")}")
        } else {
            println("🛠️  No tools selected (all filtered out or unavailable)")
        }
    }

    _lastResolvedToolNames = availableToolNames.toList()
    return filteredTools
}

// =============================================================================
// handleFunctionCall  (the main dispatcher)
// =============================================================================

/**
 * Tools whose execution is intercepted by the agent loop (AgentLoop.kt)
 * because they need agent-level state (TodoStore, MemoryStore, etc.).
 */
private val _AGENT_LOOP_TOOLS = setOf("todo", "memory", "session_search", "delegate_task")
private val _READ_SEARCH_TOOLS = setOf("read_file", "search_files")

// =========================================================================
// Tool argument type coercion
// =========================================================================

/**
 * Coerce tool call arguments to match their JSON Schema types.
 *
 * LLMs frequently return numbers as strings (`"42"` instead of `42`) and
 * booleans as strings (`"true"` instead of `true`).
 */
@Suppress("UNCHECKED_CAST")
fun coerceToolArgs(toolName: String, args: MutableMap<String, Any?>): MutableMap<String, Any?> {
    if (args.isEmpty()) return args

    val schema = registry.getSchema(toolName) ?: return args
    val parameters = schema["parameters"] as? Map<String, Any?> ?: return args
    val properties = parameters["properties"] as? Map<String, Map<String, Any?>> ?: return args

    val keys = args.keys.toList()
    for (key in keys) {
        val value = args[key] ?: continue
        if (value !is String) continue
        val propSchema = properties[key] ?: continue
        val expected = propSchema["type"] ?: continue
        val coerced = _coerceValue(value, expected)
        if (coerced !== value) {
            args[key] = coerced
        }
    }
    return args
}

/**
 * Attempt to coerce a string [value] to [expectedType]. Returns the original
 * string when coercion is not applicable or fails.
 */
private fun _coerceValue(value: String, expectedType: Any?): Any {
    if (expectedType is List<*>) {
        for (t in expectedType) {
            val result = _coerceValue(value, t)
            if (result !== value) return result
        }
        return value
    }

    if (expectedType == "integer" || expectedType == "number") {
        return _coerceNumber(value, integerOnly = (expectedType == "integer"))
    }
    if (expectedType == "boolean") {
        return _coerceBoolean(value)
    }
    return value
}

/** Try to parse [value] as a number. Returns original string on failure. */
private fun _coerceNumber(value: String, integerOnly: Boolean = false): Any {
    val f = try {
        value.toDouble()
    } catch (_: NumberFormatException) {
        return value
    }
    if (f.isNaN() || f.isInfinite()) return f
    if (f == f.toLong().toDouble()) {
        return f.toLong()
    }
    if (integerOnly) return value
    return f
}

/** Try to parse [value] as a boolean. Returns original string on failure. */
private fun _coerceBoolean(value: String): Any {
    return when (value.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> value
    }
}

/**
 * Main function call dispatcher that routes calls to the tool registry.
 */
@Suppress("UNUSED_PARAMETER")
fun handleFunctionCall(
    functionName: String,
    functionArgs: MutableMap<String, Any?>,
    taskId: String? = null,
    toolCallId: String? = null,
    sessionId: String? = null,
    userTask: String? = null,
    enabledTools: List<String>? = null,
    skipPreToolCallHook: Boolean = false): String {
    coerceToolArgs(functionName, functionArgs)

    return try {
        if (functionName in _AGENT_LOOP_TOOLS) {
            return JSONObject(mapOf<String, Any?>("error" to "$functionName must be handled by the agent loop")).toString()
        }

        // TODO: port hermes_cli.plugins.get_pre_tool_call_block_message and
        // invoke_hook observer events.

        // TODO: port tools.file_tools.notify_other_tool_call once FileTools
        // exposes a direct entry point.

        val dispatchArgs: Map<String, Any> = functionArgs.mapNotNull { (k, v) ->
            if (v != null) k to v else null
        }.toMap()

        @Suppress("UNUSED_VARIABLE")
        val sandboxEnabled = if (functionName == "execute_code") {
            enabledTools ?: _lastResolvedToolNames
        } else null

        val result = registry.dispatch(functionName, dispatchArgs)

        // TODO: invoke post_tool_call / transform_tool_result hooks.

        result
    } catch (e: Exception) {
        val errorMsg = "Error executing $functionName: ${e.message}"
        Log.e(_TAG, errorMsg)
        JSONObject(mapOf<String, Any?>("error" to errorMsg)).toString()
    }
}

// =============================================================================
// Backward-compat wrapper functions
// =============================================================================

fun getAllToolNames(): List<String> = registry.getAllToolNames()

fun getToolsetForTool(toolName: String): String? = registry.getToolsetForTool(toolName)

fun getAvailableToolsets(): Map<String, Map<String, Any?>> = registry.getAvailableToolsets()

fun checkToolsetRequirements(): Map<String, Boolean> = registry.checkToolsetRequirements()

fun checkToolAvailability(quiet: Boolean = false): Pair<List<String>, List<Map<String, Any?>>> =
    registry.checkToolAvailability(quiet = quiet)

/** Python `_get_tool_loop` — stub. */
private fun _getToolLoop(): Any? = null

/** Python `_get_worker_loop` — stub. */
private fun _getWorkerLoop(): Any? = null
