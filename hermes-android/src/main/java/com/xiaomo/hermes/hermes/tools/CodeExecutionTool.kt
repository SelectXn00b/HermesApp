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
