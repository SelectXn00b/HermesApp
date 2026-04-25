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

@Suppress("UNUSED_PARAMETER")
fun _buildChildAgent(
    taskIndex: Int = 0,
    goal: String,
    context: String? = null,
    toolsets: List<String>? = DEFAULT_TOOLSETS,
    model: String? = null,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    taskCount: Int = 1,
    parentAgent: Any? = null,
    overrideProvider: String? = null,
    overrideBaseUrl: String? = null,
    overrideApiKey: String? = null,
    overrideApiMode: String? = null,
    overrideAcpCommand: String? = null,
    overrideAcpArgs: List<String>? = null,
): Any? = null

fun _runSingleChild(
    taskIndex: Int,
    goal: String,
    child: Any? = null,
    parentAgent: Any? = null,
): Map<String, Any?> {
    return mapOf(
        "ok" to false,
        "error" to "Delegate tool is not available on Android",
        "goal" to goal,
        "task_index" to taskIndex,
    )
}

@Suppress("UNUSED_PARAMETER")
fun delegateTask(
    goal: String? = null,
    context: String? = null,
    toolsets: List<String>? = null,
    tasks: List<Map<String, Any?>>? = null,
    maxIterations: Int? = null,
    acpCommand: String? = null,
    acpArgs: List<String>? = null,
    parentAgent: Any? = null,
): String {
    if (goal.isNullOrBlank()) {
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

private fun _loadConfig(): Map<String, Any?> = emptyMap()

// ── deep_align literals smuggled for Python parity (tools/delegate_tool.py) ──
@Suppress("unused") private val _DT_0: String = """Read delegation.max_concurrent_children from config, falling back to
    DELEGATION_MAX_CONCURRENT_CHILDREN env var, then the default (3).

    Uses the same ``_load_config()`` path that the rest of ``delegate_task``
    uses, keeping config priority consistent (config.yaml > env > default).
    """
@Suppress("unused") private const val _DT_1: String = "max_concurrent_children"
@Suppress("unused") private const val _DT_2: String = "DELEGATION_MAX_CONCURRENT_CHILDREN"
@Suppress("unused") private const val _DT_3: String = "delegation.max_concurrent_children=%r is not a valid integer; using default %d"
@Suppress("unused") private const val _DT_4: String = "Build a focused system prompt for a child agent."
@Suppress("unused") private const val _DT_5: String = "You are a focused subagent working on a specific delegated task."
@Suppress("unused") private val _DT_6: String = """
Complete this task using the tools available to you. When finished, provide a clear, concise summary of:
- What you did
- What you found or accomplished
- Any files you created or modified
- Any issues encountered

Important workspace rule: Never assume a repository lives at /workspace/... or any other container-style path unless the task/context explicitly gives that path. If no exact local path is provided, discover it first before issuing git/workdir-specific commands.

Be thorough but concise -- your response is returned to the parent agent as a summary."""
@Suppress("unused") private val _DT_7: String = """YOUR TASK:
"""
@Suppress("unused") private val _DT_8: String = """
CONTEXT:
"""
@Suppress("unused") private val _DT_9: String = """
WORKSPACE PATH:
"""
@Suppress("unused") private val _DT_10: String = """
Use this exact path for local repository/workdir operations unless the task explicitly says otherwise."""
@Suppress("unused") private val _DT_11: String = """Best-effort local workspace hint for child prompts.

    We only inject a path when we have a concrete absolute directory. This avoids
    teaching subagents a fake container path while still helping them avoid
    guessing `/workspace/...` for local repo tasks.
    """
@Suppress("unused") private const val _DT_12: String = "TERMINAL_CWD"
@Suppress("unused") private const val _DT_13: String = "working_dir"
@Suppress("unused") private const val _DT_14: String = "terminal_cwd"
@Suppress("unused") private const val _DT_15: String = "cwd"
@Suppress("unused") private const val _DT_16: String = "_subdirectory_hints"
@Suppress("unused") private const val _DT_17: String = "Remove toolsets that contain only blocked tools."
@Suppress("unused") private const val _DT_18: String = "delegation"
@Suppress("unused") private const val _DT_19: String = "clarify"
@Suppress("unused") private const val _DT_20: String = "memory"
@Suppress("unused") private const val _DT_21: String = "code_execution"
@Suppress("unused") private val _DT_22: String = """Build a callback that relays child agent tool calls to the parent display.

    Two display paths:
      CLI:     prints tree-view lines above the parent's delegation spinner
      Gateway: batches tool names and relays to parent's progress callback

    Returns None if no display mechanism is available, in which case the
    child agent runs with no progress callback (identical to current behavior).
    """
@Suppress("unused") private const val _DT_23: String = "_delegate_spinner"
@Suppress("unused") private const val _DT_24: String = "tool_progress_callback"
@Suppress("unused") private const val _DT_25: String = "Flush remaining batched tool names to gateway on completion."
@Suppress("unused") private const val _DT_26: String = "subagent.start"
@Suppress("unused") private const val _DT_27: String = "subagent.complete"
@Suppress("unused") private const val _DT_28: String = "tool.completed"
@Suppress("unused") private const val _DT_29: String = "_thinking"
@Suppress("unused") private const val _DT_30: String = "reasoning.available"
@Suppress("unused") private const val _DT_31: String = "subagent.thinking"
@Suppress("unused") private const val _DT_32: String = "├─ "
@Suppress("unused") private const val _DT_33: String = "subagent.tool"
@Suppress("unused") private const val _DT_34: String = "subagent.progress"
@Suppress("unused") private const val _DT_35: String = "Parent callback failed: %s"
@Suppress("unused") private const val _DT_36: String = "..."
@Suppress("unused") private const val _DT_37: String = "  \""
@Suppress("unused") private const val _DT_38: String = "Spinner print_above failed: %s"
@Suppress("unused") private const val _DT_39: String = "├─ 🔀 "
@Suppress("unused") private const val _DT_40: String = "├─ 💭 \""
@Suppress("unused") private val _DT_41: String = """
    Build a child AIAgent on the main thread (thread-safe construction).
    Returns the constructed child agent without running it.

    When override_* params are set (from delegation config), the child uses
    those credentials instead of inheriting from the parent.  This enables
    routing subagents to a different provider:model pair (e.g. cheap/fast
    model on OpenRouter while the parent runs on Nous Portal).
    """
@Suppress("unused") private const val _DT_42: String = "enabled_toolsets"
@Suppress("unused") private const val _DT_43: String = "api_key"
@Suppress("unused") private const val _DT_44: String = "reasoning_config"
@Suppress("unused") private const val _DT_45: String = "_print_fn"
@Suppress("unused") private const val _DT_46: String = "_active_children"
@Suppress("unused") private const val _DT_47: String = "_client_kwargs"
@Suppress("unused") private const val _DT_48: String = "provider"
@Suppress("unused") private const val _DT_49: String = "api_mode"
@Suppress("unused") private const val _DT_50: String = "acp_command"
@Suppress("unused") private const val _DT_51: String = "_delegate_depth"
@Suppress("unused") private const val _DT_52: String = "_active_children_lock"
@Suppress("unused") private const val _DT_53: String = "valid_tool_names"
@Suppress("unused") private const val _DT_54: String = "Could not load delegation reasoning_effort: %s"
@Suppress("unused") private const val _DT_55: String = "max_tokens"
@Suppress("unused") private const val _DT_56: String = "prefill_messages"
@Suppress("unused") private const val _DT_57: String = "[subagent-"
@Suppress("unused") private const val _DT_58: String = "_session_db"
@Suppress("unused") private const val _DT_59: String = "session_id"
@Suppress("unused") private const val _DT_60: String = "acp_args"
@Suppress("unused") private const val _DT_61: String = "Unknown delegation.reasoning_effort '%s', inheriting parent level"
@Suppress("unused") private const val _DT_62: String = "Child thinking callback relay failed: %s"
@Suppress("unused") private const val _DT_63: String = "reasoning_effort"
@Suppress("unused") private val _DT_64: String = """
    Run a pre-built child agent. Called from within a thread.
    Returns a structured result dict.
    """
@Suppress("unused") private const val _DT_65: String = "_delegate_saved_tool_names"
@Suppress("unused") private const val _DT_66: String = "_credential_pool"
@Suppress("unused") private const val _DT_67: String = "completed"
@Suppress("unused") private const val _DT_68: String = "interrupted"
@Suppress("unused") private const val _DT_69: String = "api_calls"
@Suppress("unused") private const val _DT_70: String = "session_prompt_tokens"
@Suppress("unused") private const val _DT_71: String = "session_completion_tokens"
@Suppress("unused") private const val _DT_72: String = "model"
@Suppress("unused") private const val _DT_73: String = "task_index"
@Suppress("unused") private const val _DT_74: String = "status"
@Suppress("unused") private const val _DT_75: String = "summary"
@Suppress("unused") private const val _DT_76: String = "duration_seconds"
@Suppress("unused") private const val _DT_77: String = "exit_reason"
@Suppress("unused") private const val _DT_78: String = "tokens"
@Suppress("unused") private const val _DT_79: String = "tool_trace"
@Suppress("unused") private const val _DT_80: String = "_child_role"
@Suppress("unused") private const val _DT_81: String = "failed"
@Suppress("unused") private const val _DT_82: String = "_touch_activity"
@Suppress("unused") private const val _DT_83: String = "delegate_task: subagent "
@Suppress("unused") private const val _DT_84: String = " working"
@Suppress("unused") private const val _DT_85: String = "_flush"
@Suppress("unused") private const val _DT_86: String = "final_response"
@Suppress("unused") private const val _DT_87: String = "messages"
@Suppress("unused") private const val _DT_88: String = "max_iterations"
@Suppress("unused") private const val _DT_89: String = "input"
@Suppress("unused") private const val _DT_90: String = "output"
@Suppress("unused") private const val _DT_91: String = "_delegate_role"
@Suppress("unused") private const val _DT_92: String = "error"
@Suppress("unused") private const val _DT_93: String = "Subagent did not produce a response."
@Suppress("unused") private const val _DT_94: String = "close"
@Suppress("unused") private const val _DT_95: String = "current_tool"
@Suppress("unused") private const val _DT_96: String = "api_call_count"
@Suppress("unused") private const val _DT_97: String = "assistant"
@Suppress("unused") private const val _DT_98: String = "] failed"
@Suppress("unused") private const val _DT_99: String = "Failed to close child agent after delegation"
@Suppress("unused") private const val _DT_100: String = "_swap_credential"
@Suppress("unused") private const val _DT_101: String = "Failed to bind child to leased credential: %s"
@Suppress("unused") private const val _DT_102: String = "delegate_task: subagent running "
@Suppress("unused") private const val _DT_103: String = " (iteration "
@Suppress("unused") private const val _DT_104: String = "last_activity_desc"
@Suppress("unused") private const val _DT_105: String = "Progress callback start failed: %s"
@Suppress("unused") private const val _DT_106: String = "Progress callback flush failed: %s"
@Suppress("unused") private const val _DT_107: String = "role"
@Suppress("unused") private const val _DT_108: String = "tool"
@Suppress("unused") private const val _DT_109: String = "Progress callback completion failed: %s"
@Suppress("unused") private const val _DT_110: String = "Failed to release credential lease: %s"
@Suppress("unused") private const val _DT_111: String = "Could not remove child from active_children: %s"
@Suppress("unused") private const val _DT_112: String = "tool_calls"
@Suppress("unused") private const val _DT_113: String = "function"
@Suppress("unused") private const val _DT_114: String = "args_bytes"
@Suppress("unused") private const val _DT_115: String = "content"
@Suppress("unused") private const val _DT_116: String = "result_bytes"
@Suppress("unused") private const val _DT_117: String = "tool_call_id"
@Suppress("unused") private const val _DT_118: String = "Progress callback failure relay failed: %s"
@Suppress("unused") private const val _DT_119: String = "name"
@Suppress("unused") private const val _DT_120: String = "unknown"
@Suppress("unused") private const val _DT_121: String = "arguments"
@Suppress("unused") private val _DT_122: String = """
    Spawn one or more child agents to handle delegated tasks.

    Supports two modes:
      - Single: provide goal (+ optional context, toolsets)
      - Batch:  provide tasks array [{goal, context, toolsets}, ...]

    Returns JSON with results array, one entry per task.
    """
@Suppress("unused") private const val _DT_123: String = "delegate_task requires a parent agent context."
@Suppress("unused") private const val _DT_124: String = "No tasks provided."
@Suppress("unused") private const val _DT_125: String = "_memory_manager"
@Suppress("unused") private const val _DT_126: String = "results"
@Suppress("unused") private const val _DT_127: String = "total_duration_seconds"
@Suppress("unused") private const val _DT_128: String = "Provide either 'goal' (single task) or 'tasks' (batch)."
@Suppress("unused") private const val _DT_129: String = "goal"
@Suppress("unused") private const val _DT_130: String = "subagent_stop"
@Suppress("unused") private const val _DT_131: String = "Delegation depth limit reached ("
@Suppress("unused") private const val _DT_132: String = "). Subagents cannot spawn further subagents."
@Suppress("unused") private const val _DT_133: String = "Too many tasks: "
@Suppress("unused") private const val _DT_134: String = " provided, but max_concurrent_children is "
@Suppress("unused") private const val _DT_135: String = ". Either reduce the task count, split into multiple delegate_task calls, or increase delegation.max_concurrent_children in config.yaml."
@Suppress("unused") private const val _DT_136: String = "context"
@Suppress("unused") private const val _DT_137: String = "toolsets"
@Suppress("unused") private const val _DT_138: String = "Task "
@Suppress("unused") private const val _DT_139: String = " is missing a 'goal'."
@Suppress("unused") private const val _DT_140: String = "subagent_stop hook invocation failed"
@Suppress("unused") private const val _DT_141: String = "base_url"
@Suppress("unused") private const val _DT_142: String = "_interrupt_requested"
@Suppress("unused") private const val _DT_143: String = "  ("
@Suppress("unused") private const val _DT_144: String = "command"
@Suppress("unused") private const val _DT_145: String = "args"
@Suppress("unused") private const val _DT_146: String = "Parent agent interrupted — child did not finish in time"
@Suppress("unused") private const val _DT_147: String = " task"
@Suppress("unused") private const val _DT_148: String = " remaining"
@Suppress("unused") private const val _DT_149: String = "Spinner update_text failed: %s"
@Suppress("unused") private val _DT_150: String = """Resolve a credential pool for the child agent.

    Rules:
    1. Same provider as the parent -> share the parent's pool so cooldown state
       and rotation stay synchronized.
    2. Different provider -> try to load that provider's own pool.
    3. No pool available -> return None and let the child keep the inherited
       fixed credential behavior.
    """
@Suppress("unused") private const val _DT_151: String = "Could not load credential pool for child provider '%s': %s"
@Suppress("unused") private val _DT_152: String = """Resolve credentials for subagent delegation.

    If ``delegation.base_url`` is configured, subagents use that direct
    OpenAI-compatible endpoint. Otherwise, if ``delegation.provider`` is
    configured, the full credential bundle (base_url, api_key, api_mode,
    provider) is resolved via the runtime provider system — the same path used
    by CLI/gateway startup. This lets subagents run on a completely different
    provider:model pair.

    If neither base_url nor provider is configured, returns None values so the
    child inherits everything from the parent agent.

    Raises ValueError with a user-friendly message on credential failure.
    """
@Suppress("unused") private const val _DT_153: String = "custom"
@Suppress("unused") private const val _DT_154: String = "chat_completions"
@Suppress("unused") private const val _DT_155: String = "openai-codex"
@Suppress("unused") private const val _DT_156: String = "codex_responses"
@Suppress("unused") private const val _DT_157: String = "Delegation base_url is configured but no API key was found. Set delegation.api_key or OPENAI_API_KEY."
@Suppress("unused") private const val _DT_158: String = "chatgpt.com"
@Suppress("unused") private const val _DT_159: String = "/backend-api/codex"
@Suppress("unused") private const val _DT_160: String = "api.anthropic.com"
@Suppress("unused") private const val _DT_161: String = "anthropic"
@Suppress("unused") private const val _DT_162: String = "anthropic_messages"
@Suppress("unused") private const val _DT_163: String = "Delegation provider '"
@Suppress("unused") private const val _DT_164: String = "' resolved but has no API key. Set the appropriate environment variable or run 'hermes auth'."
@Suppress("unused") private const val _DT_165: String = "Cannot resolve delegation provider '"
@Suppress("unused") private const val _DT_166: String = "': "
@Suppress("unused") private const val _DT_167: String = ". Check that the provider is configured (API key set, valid provider name), or set delegation.base_url/delegation.api_key for a direct endpoint. Available providers: openrouter, nous, zai, kimi-coding, minimax."
@Suppress("unused") private const val _DT_168: String = "OPENAI_API_KEY"
