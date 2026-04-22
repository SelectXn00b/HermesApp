package com.xiaomo.hermes.hermes.agent

/**
 * Shell-script hooks bridge.
 *
 * Reads the `hooks:` block from `cli-config.yaml`, prompts the user for
 * consent on first use of each `(event, command)` pair, and registers
 * callbacks on the existing plugin hook manager so every existing
 * `invokeHook()` site dispatches to the configured shell scripts — with
 * zero changes to call sites.
 *
 * Ported from agent/shell_hooks.py
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val _TAG = "shell_hooks"

const val DEFAULT_TIMEOUT_SECONDS = 60
const val MAX_TIMEOUT_SECONDS = 300
const val ALLOWLIST_FILENAME = "shell-hooks-allowlist.json"

private val _registered: MutableSet<Triple<String, String?, String>> = mutableSetOf()
private val _registeredLock = ReentrantLock()
private val _allowlistWriteLock = ReentrantLock()

/** Parsed and validated representation of a single `hooks:` entry. */
data class ShellHookSpec(
    val event: String,
    val command: String,
    val matcher: String? = null,
    val timeout: Int = DEFAULT_TIMEOUT_SECONDS) {

    val compiledMatcher: Regex? by lazy {
        val m = matcher?.trim()?.takeIf { it.isNotEmpty() } ?: return@lazy null
        try {
            Regex(m)
        } catch (exc: Exception) {
            Log.w(_TAG, "shell hook matcher '$m' is invalid (${exc.message}) — treating as literal equality")
            null
        }
    }

    fun matchesTool(toolName: String?): Boolean {
        if (matcher.isNullOrBlank()) return true
        if (toolName == null) return false
        val pat = compiledMatcher
        if (pat != null) return pat.matchEntire(toolName) != null
        return toolName == matcher
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Register every configured shell hook on the plugin manager.
 */
@Suppress("UNCHECKED_CAST")
fun registerFromConfig(
    cfg: Map<String, Any?>?,
    acceptHooks: Boolean = false): List<ShellHookSpec> {
    if (cfg == null) return emptyList()

    val effectiveAccept = _resolveEffectiveAccept(cfg, acceptHooks)
    val specs = _parseHooksBlock(cfg["hooks"])
    if (specs.isEmpty()) return emptyList()

    val registered = mutableListOf<ShellHookSpec>()

    // TODO: port hermes_cli.plugins.get_plugin_manager

    for (spec in specs) {
        val key = Triple(spec.event, spec.matcher, spec.command)
        val alreadyAllowlisted = _registeredLock.withLock {
            if (key in _registered) return@withLock null
            _isAllowlisted(spec.event, spec.command)
        } ?: continue

        if (!alreadyAllowlisted) {
            if (!_promptAndRecord(spec.event, spec.command, acceptHooks = effectiveAccept)) {
                Log.w(_TAG,
                    "shell hook for ${spec.event} (${spec.command}) not allowlisted — skipped.")
                continue
            }
        }

        _registeredLock.withLock {
            if (key in _registered) return@withLock
            // TODO: manager._hooks[spec.event].append(_makeCallback(spec))
            _registered.add(key)
            registered.add(spec)
            Log.i(_TAG,
                "shell hook registered: ${spec.event} -> ${spec.command} (matcher=${spec.matcher}, timeout=${spec.timeout}s)")
        }
    }

    return registered
}

/** Return the parsed ShellHookSpec entries from config without registering anything. */
fun iterConfiguredHooks(cfg: Map<String, Any?>?): List<ShellHookSpec> {
    if (cfg == null) return emptyList()
    return _parseHooksBlock(cfg["hooks"])
}

/** Clear the idempotence set. Test-only helper. */
fun resetForTests() {
    _registeredLock.withLock { _registered.clear() }
}

// ---------------------------------------------------------------------------
// Config parsing
// ---------------------------------------------------------------------------

/** Valid hook event names — mirrors Python hermes_cli.plugins.VALID_HOOKS. */
private val _VALID_HOOKS: Set<String> = setOf(
    "gateway_startup", "gateway_shutdown",
    "pre_llm_call", "post_llm_call",
    "pre_tool_call", "post_tool_call", "transform_tool_result",
    "on_session_start", "on_session_end")

@Suppress("UNCHECKED_CAST")
private fun _parseHooksBlock(hooksCfg: Any?): List<ShellHookSpec> {
    val cfg = hooksCfg as? Map<String, Any?> ?: return emptyList()
    val specs = mutableListOf<ShellHookSpec>()

    for ((eventName, entries) in cfg) {
        if (eventName !in _VALID_HOOKS) {
            Log.w(_TAG, "unknown hook event '$eventName' in hooks: config (valid: ${_VALID_HOOKS.sorted().joinToString(", ")})")
            continue
        }
        if (entries == null) continue
        val list = entries as? List<Any?>
        if (list == null) {
            Log.w(_TAG, "hooks.$eventName must be a list of hook definitions; got ${entries.javaClass.simpleName}")
            continue
        }
        for ((i, raw) in list.withIndex()) {
            val spec = _parseSingleEntry(eventName, i, raw)
            if (spec != null) specs.add(spec)
        }
    }
    return specs
}

@Suppress("UNCHECKED_CAST")
private fun _parseSingleEntry(event: String, index: Int, raw: Any?): ShellHookSpec? {
    val d = raw as? Map<String, Any?>
    if (d == null) {
        Log.w(_TAG, "hooks.$event[$index] must be a mapping with a 'command' key; got ${raw?.javaClass?.simpleName}")
        return null
    }
    val cmdRaw = d["command"]
    val command = (cmdRaw as? String)?.trim()
    if (command.isNullOrEmpty()) {
        Log.w(_TAG, "hooks.$event[$index] is missing a non-empty 'command' field")
        return null
    }

    var matcher = d["matcher"] as? String
    if (d["matcher"] != null && matcher == null) {
        Log.w(_TAG, "hooks.$event[$index].matcher must be a string regex; ignoring")
    }
    if (matcher != null && event !in setOf("pre_tool_call", "post_tool_call")) {
        Log.w(_TAG, "hooks.$event[$index].matcher='$matcher' will be ignored at runtime — matcher is only honored for pre_tool_call/post_tool_call.")
        matcher = null
    }

    val timeoutRaw = d["timeout"] ?: DEFAULT_TIMEOUT_SECONDS
    var timeout: Int = try {
        when (timeoutRaw) {
            is Int -> timeoutRaw
            is Number -> timeoutRaw.toInt()
            is String -> timeoutRaw.toInt()
            else -> DEFAULT_TIMEOUT_SECONDS
        }
    } catch (_: Exception) {
        Log.w(_TAG, "hooks.$event[$index].timeout must be an int (got $timeoutRaw); using default ${DEFAULT_TIMEOUT_SECONDS}s")
        DEFAULT_TIMEOUT_SECONDS
    }

    if (timeout < 1) {
        Log.w(_TAG, "hooks.$event[$index].timeout must be >=1; using default ${DEFAULT_TIMEOUT_SECONDS}s")
        timeout = DEFAULT_TIMEOUT_SECONDS
    }
    if (timeout > MAX_TIMEOUT_SECONDS) {
        Log.w(_TAG, "hooks.$event[$index].timeout=${timeout}s exceeds max ${MAX_TIMEOUT_SECONDS}s; clamping")
        timeout = MAX_TIMEOUT_SECONDS
    }

    return ShellHookSpec(event = event, command = command, matcher = matcher, timeout = timeout)
}

// ---------------------------------------------------------------------------
// Subprocess callback
// ---------------------------------------------------------------------------

private val _TOP_LEVEL_PAYLOAD_KEYS = setOf("tool_name", "args", "session_id", "parent_session_id")

/** Run spec.command as a subprocess with stdinJson on stdin. */
fun _spawn(spec: ShellHookSpec, stdinJson: String): MutableMap<String, Any?> {
    val result = mutableMapOf<String, Any?>(
        "returncode" to null,
        "stdout" to "",
        "stderr" to "",
        "timed_out" to false,
        "elapsed_seconds" to 0.0,
        "error" to null)

    val argv = try {
        _shlexSplit(_expandUser(spec.command))
    } catch (exc: Exception) {
        result["error"] = "command '${spec.command}' cannot be parsed: ${exc.message}"
        return result
    }
    if (argv.isEmpty()) {
        result["error"] = "empty command"
        return result
    }

    val t0 = System.nanoTime()
    try {
        val pb = ProcessBuilder(argv)
        val proc = pb.start()
        proc.outputStream.use { it.write(stdinJson.toByteArray()) }
        val finished = proc.waitFor(spec.timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            result["timed_out"] = true
            result["elapsed_seconds"] = ((System.nanoTime() - t0) / 1_000_000_000.0)
            return result
        }
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        result["returncode"] = proc.exitValue()
        result["stdout"] = stdout
        result["stderr"] = stderr
        result["elapsed_seconds"] = ((System.nanoTime() - t0) / 1_000_000_000.0)
    } catch (exc: Exception) {
        result["error"] = exc.message ?: exc.javaClass.simpleName
    }
    return result
}

private fun _makeCallback(spec: ShellHookSpec): (Map<String, Any?>) -> Map<String, Any?>? {
    return callback@ { kwargs ->
        if (spec.event in setOf("pre_tool_call", "post_tool_call")) {
            if (!spec.matchesTool(kwargs["tool_name"] as? String)) return@callback null
        }

        val r = _spawn(spec, _serializePayload(spec.event, kwargs))
        val err = r["error"] as? String
        if (err != null) {
            Log.w(_TAG, "shell hook failed (event=${spec.event} command=${spec.command}): $err")
            return@callback null
        }
        if (r["timed_out"] == true) {
            Log.w(_TAG, "shell hook timed out after ${r["elapsed_seconds"]}s (event=${spec.event} command=${spec.command})")
            return@callback null
        }
        val rc = r["returncode"] as? Int ?: 0
        val stderr = (r["stderr"] as? String)?.trim().orEmpty()
        if (rc != 0) {
            Log.w(_TAG, "shell hook exited $rc (event=${spec.event} command=${spec.command}); stderr=${stderr.take(400)}")
        }
        _parseResponse(spec.event, r["stdout"] as? String ?: "")
    }
}

private fun _serializePayload(event: String, kwargs: Map<String, Any?>): String {
    val extras = kwargs.filterKeys { it !in _TOP_LEVEL_PAYLOAD_KEYS }
    val cwd = try { File(".").canonicalPath } catch (_: Exception) { "" }
    val toolInput = kwargs["args"] as? Map<*, *>
    val sessionId = (kwargs["session_id"] as? String)?.takeIf { it.isNotEmpty() }
        ?: (kwargs["parent_session_id"] as? String)?.takeIf { it.isNotEmpty() }
        ?: ""
    val payload = JSONObject()
    payload.put("hook_event_name", event)
    payload.put("tool_name", kwargs["tool_name"] ?: JSONObject.NULL)
    payload.put("tool_input", if (toolInput != null) JSONObject(toolInput as Map<*, *>) else JSONObject.NULL)
    payload.put("session_id", sessionId)
    payload.put("cwd", cwd)
    payload.put("extra", JSONObject(extras))
    return payload.toString()
}

/** Translate stdout JSON into a Hermes wire-shape dict. */
fun _parseResponse(event: String, stdout: String): Map<String, Any?>? {
    val trimmed = stdout.trim()
    if (trimmed.isEmpty()) return null
    val data = try {
        JSONObject(trimmed)
    } catch (_: Exception) {
        Log.w(_TAG, "shell hook stdout was not valid JSON (event=$event): ${trimmed.take(200)}")
        return null
    }

    if (event == "pre_tool_call") {
        if (data.optString("action") == "block") {
            val message = data.optString("message").ifEmpty { data.optString("reason") }
            if (message.isNotEmpty()) return mapOf("action" to "block", "message" to message)
        }
        if (data.optString("decision") == "block") {
            val message = data.optString("reason").ifEmpty { data.optString("message") }
            if (message.isNotEmpty()) return mapOf("action" to "block", "message" to message)
        }
        return null
    }

    val context = data.optString("context").trim()
    if (context.isNotEmpty()) return mapOf("context" to context)
    return null
}

// ---------------------------------------------------------------------------
// Allowlist / consent
// ---------------------------------------------------------------------------

fun allowlistPath(): File = File(getHermesHome(), ALLOWLIST_FILENAME)

fun loadAllowlist(): MutableMap<String, Any?> {
    return try {
        val text = allowlistPath().readText()
        val obj = JSONObject(text)
        val m = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = obj.get(k)
        }
        if (m["approvals"] !is JSONArray) m["approvals"] = JSONArray()
        m
    } catch (_: Exception) {
        mutableMapOf("approvals" to JSONArray())
    }
}

fun saveAllowlist(data: Map<String, Any?>) {
    val p = allowlistPath()
    try {
        p.parentFile?.mkdirs()
        val tmp = File.createTempFile("${p.name}.", ".tmp", p.parentFile)
        try {
            FileOutputStream(tmp).use { it.write(JSONObject(data).toString(2).toByteArray()) }
            if (!tmp.renameTo(p)) {
                p.writeBytes(tmp.readBytes())
                tmp.delete()
            }
        } catch (e: Exception) {
            try { tmp.delete() } catch (_: Exception) {}
            throw e
        }
    } catch (exc: Exception) {
        Log.w(_TAG, "Failed to persist shell hook allowlist to ${p.absolutePath}: ${exc.message}")
    }
}

private fun _isAllowlisted(event: String, command: String): Boolean {
    val data = loadAllowlist()
    val approvals = data["approvals"] as? JSONArray ?: return false
    for (i in 0 until approvals.length()) {
        val e = approvals.optJSONObject(i) ?: continue
        if (e.optString("event") == event && e.optString("command") == command) return true
    }
    return false
}

/** Serialise read-modify-write on the allowlist across processes. */
private fun _lockedUpdateApprovals(block: (MutableMap<String, Any?>) -> Unit) {
    // TODO: port fcntl.flock — on Android a FileChannel.lock would be the
    // cross-process equivalent.  For now rely on the in-process lock.
    _allowlistWriteLock.withLock {
        val data = loadAllowlist()
        block(data)
        saveAllowlist(data)
    }
}

private fun _promptAndRecord(event: String, command: String, acceptHooks: Boolean): Boolean {
    if (acceptHooks) {
        _recordApproval(event, command)
        Log.i(_TAG, "shell hook auto-approved via accept-hooks / env / config: $event -> $command")
        return true
    }
    // TODO: port sys.stdin.isatty() + input() prompt — Android has no TTY.
    return false
}

private fun _recordApproval(event: String, command: String) {
    val entry = JSONObject()
    entry.put("event", event)
    entry.put("command", command)
    entry.put("approved_at", _utcNowIso())
    entry.put("script_mtime_at_approval", scriptMtimeIso(command) ?: JSONObject.NULL)

    _lockedUpdateApprovals { data ->
        val existing = (data["approvals"] as? JSONArray) ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until existing.length()) {
            val e = existing.optJSONObject(i) ?: continue
            if (e.optString("event") == event && e.optString("command") == command) continue
            filtered.put(e)
        }
        filtered.put(entry)
        data["approvals"] = filtered
    }
}

private fun _utcNowIso(): String {
    return OffsetDateTime.now(ZoneOffset.UTC).toString().replace("+00:00", "Z")
}

/** Remove every allowlist entry matching [command]. Returns the number removed. */
fun revoke(command: String): Int {
    var removed = 0
    _lockedUpdateApprovals { data ->
        val existing = (data["approvals"] as? JSONArray) ?: JSONArray()
        val before = existing.length()
        val filtered = JSONArray()
        for (i in 0 until existing.length()) {
            val e = existing.optJSONObject(i) ?: continue
            if (e.optString("command") == command) continue
            filtered.put(e)
        }
        val after = filtered.length()
        removed = before - after
        data["approvals"] = filtered
    }
    return removed
}

private val _SCRIPT_EXTENSIONS: List<String> = listOf(
    ".sh", ".bash", ".zsh", ".fish",
    ".py", ".pyw",
    ".rb", ".pl", ".lua",
    ".js", ".mjs", ".cjs", ".ts")

private fun _commandScriptPath(command: String): String {
    val parts = try {
        _shlexSplit(command)
    } catch (_: Exception) {
        return command
    }
    if (parts.isEmpty()) return command
    for (part in parts) {
        if (_SCRIPT_EXTENSIONS.any { part.lowercase().endsWith(it) }) return part
    }
    for (part in parts) {
        if ("/" in part || part.startsWith("~")) return part
    }
    return parts[0]
}

// ---------------------------------------------------------------------------
// Helpers for accept-hooks resolution
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun _resolveEffectiveAccept(cfg: Map<String, Any?>, acceptHooksArg: Boolean): Boolean {
    if (acceptHooksArg) return true
    val env = (System.getenv("HERMES_ACCEPT_HOOKS") ?: "").trim().lowercase()
    if (env in setOf("1", "true", "yes", "on")) return true
    val cfgVal = cfg["hooks_auto_accept"]
    return cfgVal == true
}

// ---------------------------------------------------------------------------
// Introspection (used by `hermes hooks` CLI)
// ---------------------------------------------------------------------------

fun allowlistEntryFor(event: String, command: String): Map<String, Any?>? {
    val data = loadAllowlist()
    val approvals = data["approvals"] as? JSONArray ?: return null
    for (i in 0 until approvals.length()) {
        val e = approvals.optJSONObject(i) ?: continue
        if (e.optString("event") == event && e.optString("command") == command) {
            val m = mutableMapOf<String, Any?>()
            val keys = e.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                m[k] = e.get(k)
            }
            return m
        }
    }
    return null
}

fun scriptMtimeIso(command: String): String? {
    val path = _commandScriptPath(command)
    if (path.isEmpty()) return null
    return try {
        val expanded = File(_expandUser(path))
        val mtime = expanded.lastModified()
        if (mtime == 0L) return null
        Instant.ofEpochMilli(mtime).atOffset(ZoneOffset.UTC).toString().replace("+00:00", "Z")
    } catch (_: Exception) {
        null
    }
}

fun scriptIsExecutable(command: String): Boolean {
    val path = _commandScriptPath(command)
    if (path.isEmpty()) return false
    val expanded = File(_expandUser(path))
    if (!expanded.isFile) return false
    val argv = try {
        _shlexSplit(command)
    } catch (_: Exception) {
        return false
    }
    val isBareInvocation = argv.isNotEmpty() && argv[0] == path
    return if (isBareInvocation) expanded.canExecute() else expanded.canRead()
}

/** Fire a single shell-hook invocation with a synthetic payload. */
fun runOnce(spec: ShellHookSpec, kwargs: Map<String, Any?>): MutableMap<String, Any?> {
    val stdinJson = _serializePayload(spec.event, kwargs)
    val result = _spawn(spec, stdinJson)
    result["parsed"] = _parseResponse(spec.event, (result["stdout"] as? String) ?: "")
    return result
}

// ---------------------------------------------------------------------------
// Internal helpers (Python stdlib gaps)
// ---------------------------------------------------------------------------

private fun _expandUser(path: String): String {
    if (!path.startsWith("~")) return path
    val home = System.getProperty("user.home") ?: return path
    return home + path.substring(1)
}

/** Minimal shlex.split — handles simple quoted/unquoted tokens. */
private fun _shlexSplit(command: String): List<String> {
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var quote: Char? = null
    var i = 0
    while (i < command.length) {
        val c = command[i]
        when {
            quote != null -> {
                if (c == quote) quote = null
                else if (c == '\\' && i + 1 < command.length && quote == '"') {
                    buf.append(command[i + 1]); i++
                } else buf.append(c)
            }
            c == '"' || c == '\'' -> quote = c
            c == '\\' && i + 1 < command.length -> { buf.append(command[i + 1]); i++ }
            c.isWhitespace() -> {
                if (buf.isNotEmpty()) { out.add(buf.toString()); buf.clear() }
            }
            else -> buf.append(c)
        }
        i++
    }
    if (quote != null) throw IllegalArgumentException("No closing quotation")
    if (buf.isNotEmpty()) out.add(buf.toString())
    return out
}

private fun getHermesHome(): File {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    return if (envVal.isNotEmpty()) File(envVal).canonicalFile
    else File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
}
