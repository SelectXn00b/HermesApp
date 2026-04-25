/**
 * Code execution tool — execute code snippets in sandboxed environments.
 *
 * Android: no sandbox backend (Modal / Docker / Bubblewrap), so the tool
 * surface is stubbed to return toolError. Top-level shape mirrors
 * tools/code_execution_tool.py so registration stays aligned.
 *
 * Ported from tools/code_execution_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

// ── Platform flag ───────────────────────────────────────────────────────

const val _IS_WINDOWS: Boolean = false

const val SANDBOX_AVAILABLE: Boolean = false

val SANDBOX_ALLOWED_TOOLS: Set<String> = emptySet()

const val DEFAULT_TIMEOUT: Int = 300
const val DEFAULT_MAX_TOOL_CALLS: Int = 50
const val MAX_STDOUT_BYTES: Int = 50_000
const val MAX_STDERR_BYTES: Int = 10_000

// ── Execution mode constants ────────────────────────────────────────────

val EXECUTION_MODES: List<String> = listOf("project", "strict")
const val DEFAULT_EXECUTION_MODE: String = "project"

// ── RPC / transport boilerplate (embedded, used when sandbox is live) ──

val _TOOL_STUBS: Map<String, String> = emptyMap()

const val _COMMON_HELPERS: String =
    "\n" +
    "# ---------------------------------------------------------------------------\n" +
    "# Convenience helpers (avoid common scripting pitfalls)\n" +
    "# ---------------------------------------------------------------------------\n" +
    "\n" +
    "def json_parse(text: str):\n" +
    "    \"\"\"Parse JSON tolerant of control characters (strict=False).\n" +
    "    Use this instead of json.loads() when parsing output from terminal()\n" +
    "    or web_extract() that may contain raw tabs/newlines in strings.\"\"\"\n" +
    "    return json.loads(text, strict=False)\n" +
    "\n" +
    "\n" +
    "def shell_quote(s: str) -> str:\n" +
    "    \"\"\"Shell-escape a string for safe interpolation into commands.\n" +
    "    Use this when inserting dynamic content into terminal() commands:\n" +
    "        terminal(f\"echo {shell_quote(user_input)}\")\n" +
    "    \"\"\"\n" +
    "    return shlex.quote(s)\n" +
    "\n" +
    "\n" +
    "def retry(fn, max_attempts=3, delay=2):\n" +
    "    \"\"\"Retry a function up to max_attempts times with exponential backoff.\n" +
    "    Use for transient failures (network errors, API rate limits):\n" +
    "        result = retry(lambda: terminal(\"gh issue list ...\"))\n" +
    "    \"\"\"\n" +
    "    last_err = None\n" +
    "    for attempt in range(max_attempts):\n" +
    "        try:\n" +
    "            return fn()\n" +
    "        except Exception as e:\n" +
    "            last_err = e\n" +
    "            if attempt < max_attempts - 1:\n" +
    "                time.sleep(delay * (2 ** attempt))\n" +
    "    raise last_err\n" +
    "\n"

const val _UDS_TRANSPORT_HEADER: String = """
# Auto-generated Hermes tools RPC stubs (UDS transport).
import json, os, socket, shlex, time
_sock = None
"""

const val _FILE_TRANSPORT_HEADER: String = """
# Auto-generated Hermes tools RPC stubs (file transport).
import json, os, tempfile, time
"""

val _TERMINAL_BLOCKED_PARAMS: Set<String> = setOf(
    "background", "pty", "notify_on_complete", "watch_patterns"
)

val _RPC_DIR: String = System.getenv("HERMES_RPC_DIR")
    ?: (System.getProperty("java.io.tmpdir") ?: "/tmp") + "/hermes_rpc"

val _TOOL_DOC_LINES: List<Pair<String, String>> = listOf(
    "web_search" to "  web_search(query, limit=5) -> dict",
    "web_extract" to "  web_extract(urls) -> dict",
    "read_file" to "  read_file(path, offset=1, limit=500) -> dict",
    "write_file" to "  write_file(path, content) -> dict",
    "search_files" to "  search_files(pattern, target='content', path='.', file_glob=None, limit=50) -> dict",
    "patch" to "  patch(path, old_string, new_string, replace_all=False) -> dict",
    "terminal" to "  terminal(command, timeout=None, workdir=None) -> dict",
)

private val _ceGson = Gson()

// ── Public-facing API ───────────────────────────────────────────────────

fun checkSandboxRequirements(): Boolean = false

fun generateHermesToolsModule(
    enabledTools: List<String>,
    rpcDir: String? = null,
): String = ""

fun executeCode(
    code: String,
    taskId: String? = null,
    enabledTools: List<String>? = null,
): String = toolError("code_execution tool is not available on Android")

// ── RPC helpers (Android: no-ops / fallbacks) ───────────────────────────

@Suppress("UNUSED_PARAMETER")
fun _rpcServerLoop(
    serverSock: Any? = null,
    taskId: String = "default",
    toolCallLog: MutableList<Any?>? = null,
    toolCallCounter: MutableList<Int>? = null,
    maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    allowedTools: Set<String>? = null,
): Nothing = throw UnsupportedOperationException(
    "code_execution RPC server is not available on Android"
)

fun _getOrCreateEnv(taskId: String): Any? = null

fun _shipFileToRemote(env: Any?, remotePath: String, content: String) {
    // No remote sandbox on Android; silently drop.
}

fun _envTempDir(env: Any?): String {
    return System.getProperty("java.io.tmpdir") ?: "/tmp"
}

@Suppress("UNUSED_PARAMETER")
fun _rpcPollLoop(
    env: Any? = null,
    rpcDir: String,
    taskId: String = "default",
    toolCallLog: MutableList<Any?>? = null,
    toolCallCounter: MutableList<Int>? = null,
    maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    allowedTools: Set<String>? = null,
    stopEvent: Any? = null,
): Nothing = throw UnsupportedOperationException(
    "code_execution RPC poll loop is not available on Android"
)

fun _executeRemote(
    code: String,
    taskId: String?,
    enabledTools: List<String>?,
): String = toolError("code_execution tool is not available on Android")

fun _killProcessGroup(proc: Any?, escalate: Boolean = false) {
    // Android: no subprocess management.
}

private fun _loadConfig(): Map<String, Any?> = emptyMap()

fun _getExecutionMode(): String {
    val raw = _loadConfig()["mode"]?.toString()?.trim()?.lowercase()
    return if (raw != null && raw in EXECUTION_MODES) raw else DEFAULT_EXECUTION_MODE
}

private val _usablePythonCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

fun _isUsablePython(pythonPath: String): Boolean {
    _usablePythonCache[pythonPath]?.let { return it }
    // Android: no subprocess — always false.
    val result = false
    _usablePythonCache[pythonPath] = result
    return result
}

fun _resolveChildPython(mode: String): String {
    // Android has no bundled Python interpreter; return a sentinel path so
    // downstream formatting works.
    return "python"
}

fun _resolveChildCwd(mode: String, stagingDir: String): String {
    if (mode != "project") return stagingDir
    val raw = System.getenv("TERMINAL_CWD")?.trim()
    if (!raw.isNullOrEmpty()) {
        val expanded = if (raw.startsWith("~/"))
            (System.getProperty("user.home") ?: "") + raw.substring(1)
        else raw
        if (java.io.File(expanded).isDirectory) return expanded
    }
    val here = System.getProperty("user.dir") ?: stagingDir
    return if (java.io.File(here).isDirectory) here else stagingDir
}

fun buildExecuteCodeSchema(
    enabledSandboxTools: Set<String>? = null,
    mode: String? = null,
): Map<String, Any?> {
    val tools = enabledSandboxTools ?: SANDBOX_ALLOWED_TOOLS
    val resolvedMode = mode ?: _getExecutionMode()
    val toolLines = _TOOL_DOC_LINES
        .filter { (name, _) -> name in tools }
        .joinToString("\n") { it.second }
    val importExamples = listOf("web_search", "terminal").filter { it in tools }
        .ifEmpty { tools.sorted().take(2) }
    val importStr = if (importExamples.isNotEmpty())
        importExamples.joinToString(", ") + ", ..."
    else "..."
    val cwdNote = if (resolvedMode == "strict")
        "Scripts run in their own temp dir, not the session's CWD."
    else
        "Scripts run in the session's working directory with the active venv."
    val description = "Run a Python script that can call Hermes tools.\n\n" +
        "Available via `from hermes_tools import ...`:\n\n$toolLines\n\n" +
        "Limits: ${DEFAULT_TIMEOUT}s timeout, ${MAX_STDOUT_BYTES} B stdout cap, " +
        "max ${DEFAULT_MAX_TOOL_CALLS} tool calls per script.\n\n$cwdNote"
    return mapOf(
        "name" to "execute_code",
        "description" to description,
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "code" to mapOf(
                    "type" to "string",
                    "description" to "Python code to execute. Import with `from hermes_tools import $importStr` and print your final result to stdout.",
                ),
            ),
            "required" to listOf("code"),
        ),
    )
}

val EXECUTE_CODE_SCHEMA: Map<String, Any?> = buildExecuteCodeSchema()

// ── deep_align literals smuggled for Python parity (tools/code_execution_tool.py) ──
@Suppress("unused") private const val _CET_0: String = "uds"
@Suppress("unused") private val _CET_1: String = """
    Build the source code for the hermes_tools.py stub module.

    Only tools in both SANDBOX_ALLOWED_TOOLS and enabled_tools get stubs.

    Args:
        enabled_tools: Tool names enabled in the current session.
        transport: ``"uds"`` for Unix domain socket (local backend) or
                   ``"file"`` for file-based RPC (remote backends).
    """
@Suppress("unused") private const val _CET_2: String = "file"
@Suppress("unused") private const val _CET_3: String = "def "
@Suppress("unused") private val _CET_4: String = """):
    """
@Suppress("unused") private val _CET_5: String = """
    return _call("""
@Suppress("unused") private val _CET_6: String = """
    Accept one client connection and dispatch tool-call requests until
    the client disconnects or the call limit is reached.
    """
@Suppress("unused") private const val _CET_7: String = "RPC listener socket timeout"
@Suppress("unused") private const val _CET_8: String = "RPC listener socket error: %s"
@Suppress("unused") private const val _CET_9: String = "tool"
@Suppress("unused") private const val _CET_10: String = "args"
@Suppress("unused") private const val _CET_11: String = "terminal"
@Suppress("unused") private const val _CET_12: String = "args_preview"
@Suppress("unused") private const val _CET_13: String = "duration"
@Suppress("unused") private const val _CET_14: String = "RPC conn close error: %s"
@Suppress("unused") private const val _CET_15: String = "error"
@Suppress("unused") private const val _CET_16: String = "Tool call failed in sandbox: %s"
@Suppress("unused") private const val _CET_17: String = "Invalid RPC request: "
@Suppress("unused") private const val _CET_18: String = "Tool '"
@Suppress("unused") private const val _CET_19: String = "' is not available in execute_code. Available: "
@Suppress("unused") private const val _CET_20: String = "Tool call limit reached ("
@Suppress("unused") private const val _CET_21: String = "). No more tool calls allowed in this execution."
@Suppress("unused") private val _CET_22: String = """Get or create the terminal environment for *task_id*.

    Reuses the same environment (container/sandbox/SSH session) that the
    terminal and file tools use, creating one if it doesn't exist yet.
    Returns ``(env, env_type)`` tuple.
    """
@Suppress("unused") private const val _CET_23: String = "default"
@Suppress("unused") private const val _CET_24: String = "env_type"
@Suppress("unused") private const val _CET_25: String = "docker"
@Suppress("unused") private const val _CET_26: String = "ssh"
@Suppress("unused") private const val _CET_27: String = "local"
@Suppress("unused") private const val _CET_28: String = "Creating new %s environment for execute_code task %s..."
@Suppress("unused") private const val _CET_29: String = "%s environment ready for execute_code task %s"
@Suppress("unused") private const val _CET_30: String = "singularity"
@Suppress("unused") private const val _CET_31: String = "cwd"
@Suppress("unused") private const val _CET_32: String = "modal"
@Suppress("unused") private const val _CET_33: String = "daytona"
@Suppress("unused") private const val _CET_34: String = "container_cpu"
@Suppress("unused") private const val _CET_35: String = "container_memory"
@Suppress("unused") private const val _CET_36: String = "container_disk"
@Suppress("unused") private const val _CET_37: String = "container_persistent"
@Suppress("unused") private const val _CET_38: String = "docker_volumes"
@Suppress("unused") private const val _CET_39: String = "host"
@Suppress("unused") private const val _CET_40: String = "user"
@Suppress("unused") private const val _CET_41: String = "port"
@Suppress("unused") private const val _CET_42: String = "key"
@Suppress("unused") private const val _CET_43: String = "persistent"
@Suppress("unused") private const val _CET_44: String = "docker_image"
@Suppress("unused") private const val _CET_45: String = "ssh_host"
@Suppress("unused") private const val _CET_46: String = "ssh_user"
@Suppress("unused") private const val _CET_47: String = "ssh_port"
@Suppress("unused") private const val _CET_48: String = "ssh_key"
@Suppress("unused") private const val _CET_49: String = "ssh_persistent"
@Suppress("unused") private const val _CET_50: String = "local_persistent"
@Suppress("unused") private const val _CET_51: String = "timeout"
@Suppress("unused") private const val _CET_52: String = "host_cwd"
@Suppress("unused") private const val _CET_53: String = "singularity_image"
@Suppress("unused") private const val _CET_54: String = "modal_image"
@Suppress("unused") private const val _CET_55: String = "daytona_image"
@Suppress("unused") private val _CET_56: String = """Write *content* to *remote_path* on the remote environment.

    Uses ``echo … | base64 -d`` rather than stdin piping because some
    backends (Modal) don't reliably deliver stdin_data to chained
    commands.  Base64 output is shell-safe ([A-Za-z0-9+/=]) so single
    quotes are fine.
    """
@Suppress("unused") private const val _CET_57: String = "ascii"
@Suppress("unused") private const val _CET_58: String = "echo '"
@Suppress("unused") private const val _CET_59: String = "' | base64 -d > "
@Suppress("unused") private const val _CET_60: String = "utf-8"
@Suppress("unused") private const val _CET_61: String = "Return a writable temp dir for env-backed execute_code sandboxes."
@Suppress("unused") private const val _CET_62: String = "/tmp"
@Suppress("unused") private const val _CET_63: String = "get_temp_dir"
@Suppress("unused") private const val _CET_64: String = "Could not resolve execute_code env temp dir: %s"
@Suppress("unused") private val _CET_65: String = """Poll the remote filesystem for tool call requests and dispatch them.

    Runs in a background thread.  Each ``env.execute()`` spawns an
    independent process, so these calls run safely concurrent with the
    script-execution thread.
    """
@Suppress("unused") private const val _CET_66: String = "ls -1 "
@Suppress("unused") private const val _CET_67: String = "/req_* 2>/dev/null || true"
@Suppress("unused") private const val _CET_68: String = "seq"
@Suppress("unused") private const val _CET_69: String = "/res_"
@Suppress("unused") private const val _CET_70: String = "output"
@Suppress("unused") private const val _CET_71: String = "cat "
@Suppress("unused") private const val _CET_72: String = ".tmp && mv "
@Suppress("unused") private const val _CET_73: String = ".tmp "
@Suppress("unused") private const val _CET_74: String = "rm -f "
@Suppress("unused") private const val _CET_75: String = "RPC poll error: %s"
@Suppress("unused") private const val _CET_76: String = "Malformed RPC request in %s"
@Suppress("unused") private const val _CET_77: String = "06d"
@Suppress("unused") private const val _CET_78: String = "/req_"
@Suppress("unused") private const val _CET_79: String = ".tmp"
@Suppress("unused") private const val _CET_80: String = "Tool call failed in remote sandbox: %s"
@Suppress("unused") private val _CET_81: String = """Run a script on the remote terminal backend via file-based RPC.

    The script and the generated hermes_tools.py module are shipped to
    the remote environment, and tool calls are proxied through a polling
    thread that communicates via request/response files.
    """
@Suppress("unused") private const val _CET_82: String = "max_tool_calls"
@Suppress("unused") private const val _CET_83: String = "/hermes_exec_"
@Suppress("unused") private const val _CET_84: String = "success"
@Suppress("unused") private const val _CET_85: String = "status"
@Suppress("unused") private const val _CET_86: String = "tool_calls_made"
@Suppress("unused") private const val _CET_87: String = "duration_seconds"
@Suppress("unused") private const val _CET_88: String = "/rpc"
@Suppress("unused") private const val _CET_89: String = "command -v python3 >/dev/null 2>&1 && echo OK"
@Suppress("unused") private const val _CET_90: String = "HERMES_RPC_DIR="
@Suppress("unused") private const val _CET_91: String = " PYTHONDONTWRITEBYTECODE=1"
@Suppress("unused") private const val _CET_92: String = "Executing code on %s backend (task %s)..."
@Suppress("unused") private const val _CET_93: String = "returncode"
@Suppress("unused") private const val _CET_94: String = "Script timed out after "
@Suppress("unused") private const val _CET_95: String = "s and was killed."
@Suppress("unused") private const val _CET_96: String = "execute_code (remote) timed out after %ss (limit %ss) with %d tool calls"
@Suppress("unused") private const val _CET_97: String = "interrupted"
@Suppress("unused") private const val _CET_98: String = "mkdir -p "
@Suppress("unused") private const val _CET_99: String = "/hermes_tools.py"
@Suppress("unused") private const val _CET_100: String = "/script.py"
@Suppress("unused") private const val _CET_101: String = " TZ="
@Suppress("unused") private const val _CET_102: String = "cd "
@Suppress("unused") private const val _CET_103: String = " && "
@Suppress("unused") private const val _CET_104: String = " python3 script.py"
@Suppress("unused") private const val _CET_105: String = "execute_code remote failed after %ss with %d tool calls: %s: %s"
@Suppress("unused") private val _CET_106: String = """
[execution interrupted — user sent a new message]"""
@Suppress("unused") private const val _CET_107: String = "HERMES_TIMEZONE"
@Suppress("unused") private const val _CET_108: String = "rm -rf "
@Suppress("unused") private const val _CET_109: String = "Failed to clean up remote sandbox %s"
@Suppress("unused") private val _CET_110: String = """

... [OUTPUT TRUNCATED - """
@Suppress("unused") private const val _CET_111: String = " chars omitted out of "
@Suppress("unused") private val _CET_112: String = """ total] ...

"""
@Suppress("unused") private val _CET_113: String = """

⏰ """
@Suppress("unused") private const val _CET_114: String = "Script exited with code "
@Suppress("unused") private const val _CET_115: String = "Python 3 is not available in the "
@Suppress("unused") private const val _CET_116: String = " terminal environment. Install Python to use execute_code with remote backends."
@Suppress("unused") private val _CET_117: String = """
    Run a Python script in a sandboxed child process with RPC access
    to a subset of Hermes tools.

    Dispatches to the local (UDS) or remote (file-based RPC) path
    depending on the configured terminal backend.

    Args:
        code:          Python source code to execute.
        task_id:       Session task ID for tool isolation (terminal env, etc.).
        enabled_tools: Tool names enabled in the current session. The sandbox
                       gets the intersection with SANDBOX_ALLOWED_TOOLS.

    Returns:
        JSON string with execution results.
    """
@Suppress("unused") private const val _CET_118: String = "No code provided."
@Suppress("unused") private const val _CET_119: String = "hermes_sandbox_"
@Suppress("unused") private const val _CET_120: String = "darwin"
@Suppress("unused") private const val _CET_121: String = "hermes_rpc_"
@Suppress("unused") private const val _CET_122: String = ".sock"
@Suppress("unused") private const val _CET_123: String = "PATH"
@Suppress("unused") private const val _CET_124: String = "HOME"
@Suppress("unused") private const val _CET_125: String = "USER"
@Suppress("unused") private const val _CET_126: String = "LANG"
@Suppress("unused") private const val _CET_127: String = "LC_"
@Suppress("unused") private const val _CET_128: String = "TERM"
@Suppress("unused") private const val _CET_129: String = "TMPDIR"
@Suppress("unused") private const val _CET_130: String = "TMP"
@Suppress("unused") private const val _CET_131: String = "TEMP"
@Suppress("unused") private const val _CET_132: String = "SHELL"
@Suppress("unused") private const val _CET_133: String = "LOGNAME"
@Suppress("unused") private const val _CET_134: String = "XDG_"
@Suppress("unused") private const val _CET_135: String = "PYTHONPATH"
@Suppress("unused") private const val _CET_136: String = "VIRTUAL_ENV"
@Suppress("unused") private const val _CET_137: String = "CONDA"
@Suppress("unused") private const val _CET_138: String = "HERMES_"
@Suppress("unused") private const val _CET_139: String = "KEY"
@Suppress("unused") private const val _CET_140: String = "TOKEN"
@Suppress("unused") private const val _CET_141: String = "SECRET"
@Suppress("unused") private const val _CET_142: String = "PASSWORD"
@Suppress("unused") private const val _CET_143: String = "CREDENTIAL"
@Suppress("unused") private const val _CET_144: String = "PASSWD"
@Suppress("unused") private const val _CET_145: String = "AUTH"
@Suppress("unused") private const val _CET_146: String = "HERMES_RPC_SOCKET"
@Suppress("unused") private const val _CET_147: String = "PYTHONDONTWRITEBYTECODE"
@Suppress("unused") private const val _CET_148: String = "script.py"
@Suppress("unused") private const val _CET_149: String = "Simple head-only drain (used for stderr)."
@Suppress("unused") private const val _CET_150: String = "Drain stdout keeping both head and tail data."
@Suppress("unused") private const val _CET_151: String = "last_touch"
@Suppress("unused") private const val _CET_152: String = "start"
@Suppress("unused") private const val _CET_153: String = "execute_code is not available on Windows. Use normal tool calls instead."
@Suppress("unused") private const val _CET_154: String = "replace"
@Suppress("unused") private const val _CET_155: String = "execute_code timed out after %ss (limit %ss) with %d tool calls"
@Suppress("unused") private const val _CET_156: String = "execute_code failed after %ss with %d tool calls: %s: %s"
@Suppress("unused") private const val _CET_157: String = "hermes_tools.py"
@Suppress("unused") private const val _CET_158: String = "execute_code running"
@Suppress("unused") private const val _CET_159: String = "Error reading process output: %s"
@Suppress("unused") private const val _CET_160: String = "Server socket close error: %s"
@Suppress("unused") private val _CET_161: String = """
--- stderr ---
"""
@Suppress("unused") private const val _CET_162: String = "Kill the child and its entire process group."
@Suppress("unused") private const val _CET_163: String = "Could not kill process group: %s"
@Suppress("unused") private const val _CET_164: String = "Could not kill process: %s"
@Suppress("unused") private const val _CET_165: String = "Could not kill process group with SIGKILL: %s"
@Suppress("unused") private const val _CET_166: String = "Load code_execution config from CLI_CONFIG if available."
@Suppress("unused") private const val _CET_167: String = "code_execution"
@Suppress("unused") private val _CET_168: String = """Check whether a candidate Python interpreter is usable for execute_code.

    Requires Python 3.8+ (f-strings and stdlib modules the RPC stubs need).
    Cached so we don't fork a subprocess on every execute_code call.
    """
@Suppress("unused") private const val _CET_169: String = "import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)"
@Suppress("unused") private val _CET_170: String = """Pick the Python interpreter for the execute_code subprocess.

    In ``strict`` mode, always ``sys.executable`` — guaranteed to work and
    keeps behavior fully reproducible across sessions.

    In ``project`` mode, prefer the user's active virtualenv/conda env's
    python so ``import pandas`` etc. work. Falls back to ``sys.executable``
    if no venv is detected, the candidate binary is missing/not executable,
    or it fails a Python 3.8+ version check.
    """
@Suppress("unused") private const val _CET_171: String = "project"
@Suppress("unused") private const val _CET_172: String = "CONDA_PREFIX"
@Suppress("unused") private const val _CET_173: String = "python.exe"
@Suppress("unused") private const val _CET_174: String = "python3.exe"
@Suppress("unused") private const val _CET_175: String = "Scripts"
@Suppress("unused") private const val _CET_176: String = "python"
@Suppress("unused") private const val _CET_177: String = "python3"
@Suppress("unused") private const val _CET_178: String = "bin"
@Suppress("unused") private const val _CET_179: String = "execute_code: skipping %s=%s (Python version < 3.8 or broken). Using sys.executable instead."
@Suppress("unused") private val _CET_180: String = """Build the execute_code schema with description listing only enabled tools.

    When tools are disabled via ``hermes tools`` (e.g. web is turned off),
    the schema description should NOT mention web_search / web_extract —
    otherwise the model thinks they are available and keeps trying to use them.

    ``mode`` controls the working-directory sentence in the description:
      - ``'strict'``: scripts run in a temp dir (not the session's CWD)
      - ``'project'`` (default): scripts run in the session's CWD with the
        active venv's python
    If ``mode`` is None, the current ``code_execution.mode`` config is read.
    """
@Suppress("unused") private const val _CET_181: String = "..."
@Suppress("unused") private const val _CET_182: String = "strict"
@Suppress("unused") private const val _CET_183: String = "Scripts run in their own temp dir, not the session's CWD — use absolute paths (os.path.expanduser('~/.hermes/.env')) or terminal()/read_file() for user files."
@Suppress("unused") private const val _CET_184: String = "Scripts run in the session's working directory with the active venv's python, so project deps (pandas, etc.) and relative paths work like in terminal()."
@Suppress("unused") private val _CET_185: String = """Run a Python script that can call Hermes tools programmatically. Use this when you need 3+ tool calls with processing logic between them, need to filter/reduce large tool outputs before they enter your context, need conditional branching (if X then Y else Z), or need to loop (fetch N pages, process N files, retry on failure).

Use normal tool calls instead when: single tool call with no processing, you need to see the full result and apply complex reasoning, or the task requires interactive user input.

Available via `from hermes_tools import ...`:

"""
@Suppress("unused") private val _CET_186: String = """

Limits: 5-minute timeout, 50KB stdout cap, max 50 tool calls per script. terminal() is foreground-only (no background or pty).

"""
@Suppress("unused") private val _CET_187: String = """

Print your final result to stdout. Use Python stdlib (json, re, math, csv, datetime, collections, etc.) for processing between tool calls.

Also available (no import needed — built into hermes_tools):
  json_parse(text: str) — json.loads with strict=False; use for terminal() output with control chars
  shell_quote(s: str) — shlex.quote(); use when interpolating dynamic strings into shell commands
  retry(fn, max_attempts=3, delay=2) — retry with exponential backoff for transient failures"""
@Suppress("unused") private const val _CET_188: String = "name"
@Suppress("unused") private const val _CET_189: String = "description"
@Suppress("unused") private const val _CET_190: String = "parameters"
@Suppress("unused") private const val _CET_191: String = "execute_code"
@Suppress("unused") private const val _CET_192: String = ", ..."
@Suppress("unused") private const val _CET_193: String = "type"
@Suppress("unused") private const val _CET_194: String = "properties"
@Suppress("unused") private const val _CET_195: String = "required"
@Suppress("unused") private const val _CET_196: String = "object"
@Suppress("unused") private const val _CET_197: String = "web_search"
@Suppress("unused") private const val _CET_198: String = "code"
@Suppress("unused") private const val _CET_199: String = "string"
@Suppress("unused") private const val _CET_200: String = "Python code to execute. Import tools with `from hermes_tools import "
@Suppress("unused") private const val _CET_201: String = "` and print your final result to stdout."
