package com.xiaomo.hermes.hermes.tools.environments

/**
 * 1:1 port of tools/environments/local.py — module-level helpers.
 *
 * The Python LocalEnvironment class itself is not ported because Android
 * subprocess semantics differ from Python: session snapshots, shell init
 * sourcing, and process-group kills are handled by the shell backend in
 * [BaseEnvironment] subclasses.  Only the pure helpers are aligned here.
 */

import java.io.File

// ── Platform constants ─────────────────────────────────────────────────────

/** True if the JVM is running on Windows.  Mirrors `_IS_WINDOWS` in Python. */
val _IS_WINDOWS: Boolean = (System.getProperty("os.name") ?: "").lowercase().contains("windows")

/** Hermes-internal env prefix that *forces* a variable into the subprocess env. */
const val _HERMES_PROVIDER_ENV_FORCE_PREFIX: String = "_HERMES_FORCE_"

/** Standard PATH entries for environments with minimal PATH. */
const val _SANE_PATH: String =
    "/opt/homebrew/bin:/opt/homebrew/sbin:" +
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

// ── Env blocklist ──────────────────────────────────────────────────────────

/**
 * Derive the set of env-var names that must never leak into terminal
 * subprocesses.  On Android there is no PROVIDER_REGISTRY / OPTIONAL_ENV_VARS
 * module to introspect, so we fall back to the explicit static set that
 * Python amends via `blocked.update({...})`.
 */
fun _buildProviderEnvBlocklist(): Set<String> {
    return setOf(
        "OPENAI_BASE_URL",
        "OPENAI_API_KEY",
        "OPENAI_API_BASE",
        "OPENAI_ORG_ID",
        "OPENAI_ORGANIZATION",
        "OPENROUTER_API_KEY",
        "ANTHROPIC_BASE_URL",
        "ANTHROPIC_TOKEN",
        "CLAUDE_CODE_OAUTH_TOKEN",
        "LLM_MODEL",
        "GOOGLE_API_KEY",
        "DEEPSEEK_API_KEY",
        "MISTRAL_API_KEY",
        "GROQ_API_KEY",
        "TOGETHER_API_KEY",
        "PERPLEXITY_API_KEY",
        "COHERE_API_KEY",
        "FIREWORKS_API_KEY",
        "XAI_API_KEY",
        "HELICONE_API_KEY",
        "PARALLEL_API_KEY",
        "FIRECRAWL_API_KEY",
        "FIRECRAWL_API_URL",
        "TELEGRAM_HOME_CHANNEL",
        "TELEGRAM_HOME_CHANNEL_NAME",
        "DISCORD_HOME_CHANNEL",
        "DISCORD_HOME_CHANNEL_NAME",
        "DISCORD_REQUIRE_MENTION",
        "DISCORD_FREE_RESPONSE_CHANNELS",
        "DISCORD_AUTO_THREAD",
        "SLACK_HOME_CHANNEL",
        "SLACK_HOME_CHANNEL_NAME",
        "SLACK_ALLOWED_USERS",
        "WHATSAPP_ENABLED",
        "WHATSAPP_MODE",
        "WHATSAPP_ALLOWED_USERS",
        "SIGNAL_HTTP_URL",
        "SIGNAL_ACCOUNT",
        "SIGNAL_ALLOWED_USERS",
        "SIGNAL_GROUP_ALLOWED_USERS",
        "SIGNAL_HOME_CHANNEL",
        "SIGNAL_HOME_CHANNEL_NAME",
        "SIGNAL_IGNORE_STORIES",
        "HASS_TOKEN",
        "HASS_URL",
        "EMAIL_ADDRESS",
        "EMAIL_PASSWORD",
        "EMAIL_IMAP_HOST",
        "EMAIL_SMTP_HOST",
        "EMAIL_HOME_ADDRESS",
        "EMAIL_HOME_ADDRESS_NAME",
        "GATEWAY_ALLOWED_USERS",
        "GH_TOKEN",
        "GITHUB_APP_ID",
        "GITHUB_APP_PRIVATE_KEY_PATH",
        "GITHUB_APP_INSTALLATION_ID",
        "MODAL_TOKEN_ID",
        "MODAL_TOKEN_SECRET",
        "DAYTONA_API_KEY"
    )
}

/** Pre-computed blocklist — the Kotlin analogue of the module-level Python set. */
val _HERMES_PROVIDER_ENV_BLOCKLIST: Set<String> = _buildProviderEnvBlocklist()

// ── Env sanitization ───────────────────────────────────────────────────────

/**
 * Filter Hermes-managed secrets from a subprocess environment.
 * Keys starting with [_HERMES_PROVIDER_ENV_FORCE_PREFIX] in [extraEnv] are
 * unmasked (prefix stripped); keys on the blocklist are dropped from both
 * [baseEnv] and [extraEnv] unless marked as passthrough by the caller.
 */
fun _sanitizeSubprocessEnv(
    baseEnv: Map<String, String>?,
    extraEnv: Map<String, String>? = null
): MutableMap<String, String> {
    val sanitized = mutableMapOf<String, String>()

    for ((key, value) in (baseEnv ?: emptyMap())) {
        if (key.startsWith(_HERMES_PROVIDER_ENV_FORCE_PREFIX)) continue
        if (key !in _HERMES_PROVIDER_ENV_BLOCKLIST) sanitized[key] = value
    }

    for ((key, value) in (extraEnv ?: emptyMap())) {
        if (key.startsWith(_HERMES_PROVIDER_ENV_FORCE_PREFIX)) {
            val realKey = key.removePrefix(_HERMES_PROVIDER_ENV_FORCE_PREFIX)
            sanitized[realKey] = value
        } else if (key !in _HERMES_PROVIDER_ENV_BLOCKLIST) {
            sanitized[key] = value
        }
    }

    val profileHome = System.getenv("HERMES_SUBPROCESS_HOME")?.takeIf { it.isNotEmpty() }
    if (profileHome != null) sanitized["HOME"] = profileHome

    return sanitized
}

// ── Bash discovery ─────────────────────────────────────────────────────────

/**
 * Find bash for command execution.  Android runtimes rarely ship /bin/bash,
 * so the probe falls back to `/system/bin/sh` which is guaranteed present.
 */
fun _findBash(): String {
    if (!_IS_WINDOWS) {
        val fromPath = _whichExecutable("bash")
        if (fromPath != null) return fromPath
        for (p in listOf("/usr/bin/bash", "/bin/bash", "/system/bin/bash", "/system/bin/sh")) {
            if (File(p).isFile) return p
        }
        val shellEnv = System.getenv("SHELL")
        if (!shellEnv.isNullOrEmpty()) return shellEnv
        return "/bin/sh"
    }

    val custom = System.getenv("HERMES_GIT_BASH_PATH")
    if (!custom.isNullOrEmpty() && File(custom).isFile) return custom

    _whichExecutable("bash")?.let { return it }

    val candidates = listOf(
        File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Git\\bin\\bash.exe").absolutePath,
        File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Git\\bin\\bash.exe").absolutePath,
        File(System.getenv("LOCALAPPDATA") ?: "", "Programs\\Git\\bin\\bash.exe").absolutePath
    )
    for (c in candidates) if (File(c).isFile) return c

    throw RuntimeException(
        "Git Bash not found. Hermes Agent requires Git for Windows on Windows.\n" +
            "Install it from: https://git-scm.com/download/win\n" +
            "Or set HERMES_GIT_BASH_PATH to your bash.exe location."
    )
}

private fun _whichExecutable(name: String): String? {
    val pathVar = System.getenv("PATH") ?: return null
    val sep = if (_IS_WINDOWS) ";" else ":"
    val exts = if (_IS_WINDOWS) listOf("", ".exe", ".cmd", ".bat") else listOf("")
    for (dir in pathVar.split(sep)) {
        if (dir.isEmpty()) continue
        for (ext in exts) {
            val candidate = File(dir, name + ext)
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
    }
    return null
}

// ── Run environment ────────────────────────────────────────────────────────

/** Build a run environment with a sane PATH and provider-var stripping. */
fun _makeRunEnv(env: Map<String, String>): MutableMap<String, String> {
    val merged: MutableMap<String, String> = System.getenv().toMutableMap()
    merged.putAll(env)

    val runEnv = mutableMapOf<String, String>()
    for ((k, v) in merged) {
        if (k.startsWith(_HERMES_PROVIDER_ENV_FORCE_PREFIX)) {
            val realKey = k.removePrefix(_HERMES_PROVIDER_ENV_FORCE_PREFIX)
            runEnv[realKey] = v
        } else if (k !in _HERMES_PROVIDER_ENV_BLOCKLIST) {
            runEnv[k] = v
        }
    }
    val existingPath = runEnv["PATH"] ?: ""
    if ("/usr/bin" !in existingPath.split(":")) {
        runEnv["PATH"] = if (existingPath.isNotEmpty()) "$existingPath:$_SANE_PATH" else _SANE_PATH
    }

    val profileHome = System.getenv("HERMES_SUBPROCESS_HOME")?.takeIf { it.isNotEmpty() }
    if (profileHome != null) runEnv["HOME"] = profileHome

    return runEnv
}

// ── Shell init config ──────────────────────────────────────────────────────

/**
 * Return (shellInitFiles, autoSourceBashrc) from terminal config.
 * Best-effort — returns sensible defaults on any failure so terminal
 * execution never breaks because the config file is unreadable.
 *
 * On Android there is no YAML parser bundled here; the values are read
 * from env vars instead:
 *   HERMES_TERMINAL_SHELL_INIT_FILES — colon-separated list of files
 *   HERMES_TERMINAL_AUTO_SOURCE_BASHRC — "0"/"false" to disable (default true)
 */
fun _readTerminalShellInitConfig(): Pair<List<String>, Boolean> {
    return try {
        val raw = (System.getenv("HERMES_TERMINAL_SHELL_INIT_FILES") ?: "")
        val files = raw.split(':').filter { it.isNotBlank() }.map { it.trim() }
        val autoRaw = (System.getenv("HERMES_TERMINAL_AUTO_SOURCE_BASHRC") ?: "").lowercase()
        val autoBashrc = !(autoRaw == "0" || autoRaw == "false" || autoRaw == "no")
        Pair(files, autoBashrc)
    } catch (_: Exception) {
        Pair(emptyList(), true)
    }
}

/**
 * Resolve the list of files to source before the login-shell snapshot.
 * Expands `~` and `${VAR}` references and drops anything that doesn't
 * exist on disk, so a missing `~/.bashrc` never breaks the snapshot.
 */
fun _resolveShellInitFiles(): List<String> {
    val (explicit, autoBashrc) = _readTerminalShellInitConfig()
    val candidates = mutableListOf<String>()
    when {
        explicit.isNotEmpty() -> candidates.addAll(explicit)
        autoBashrc && !_IS_WINDOWS -> candidates.add("~/.bashrc")
    }

    val resolved = mutableListOf<String>()
    for (raw in candidates) {
        val expanded = try {
            _expandShellPath(raw)
        } catch (_: Exception) {
            continue
        }
        if (expanded.isNotEmpty() && File(expanded).isFile) resolved.add(expanded)
    }
    return resolved
}

private fun _expandShellPath(raw: String): String {
    val home = System.getProperty("user.home") ?: System.getenv("HOME") ?: ""
    var s = when {
        raw == "~" -> home
        raw.startsWith("~/") -> home + raw.substring(1)
        else -> raw
    }
    // Expand ${VAR} references (simple, non-recursive).
    val varPattern = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
    s = varPattern.replace(s) { match ->
        System.getenv(match.groupValues[1]) ?: ""
    }
    return s
}

/**
 * Prepend `source <file>` lines (guarded + silent) to a bash script.
 * Each file is wrapped so a failing rc file doesn't abort the whole
 * bootstrap: `set +e` keeps going on errors, `2>/dev/null` hides
 * noisy prompts, and `|| true` neutralises the exit status.
 */
fun _prependShellInit(cmdString: String, files: List<String>): String {
    if (files.isEmpty()) return cmdString
    val preludeParts = mutableListOf("set +e")
    for (path in files) {
        val safe = path.replace("'", "'\\''")
        preludeParts.add("[ -r '$safe' ] && . '$safe' 2>/dev/null || true")
    }
    val prelude = preludeParts.joinToString("\n") + "\n"
    return prelude + cmdString
}

// ── LocalEnvironment ───────────────────────────────────────────────────────

/**
 * Run commands directly on the host machine.
 *
 * Spawn-per-call: every `execute()` spawns a fresh bash process.
 * Session snapshot preserves env vars across calls.
 * CWD persists via file-based read after each command.
 */
class LocalEnvironment(
    cwd: String = "",
    timeout: Int = 60,
    env: Map<String, String> = emptyMap()
) : BaseEnvironment(
    cwd = if (cwd.isNotEmpty()) cwd else (System.getProperty("user.dir") ?: "/"),
    timeout = timeout,
    env = env
) {

    init {
        initSession()
    }

    /**
     * Return a shell-safe writable temp dir for local execution.
     * Termux doesn't provide /tmp by default; prefer POSIX TMPDIR/TMP/TEMP.
     */
    override fun getTempDir(): String {
        for (envVar in listOf("TMPDIR", "TMP", "TEMP")) {
            val candidate = this.env[envVar] ?: System.getenv(envVar)
            if (!candidate.isNullOrEmpty() && candidate.startsWith("/")) {
                return candidate.trimEnd('/').ifEmpty { "/" }
            }
        }
        val tmp = File("/tmp")
        if (tmp.isDirectory && tmp.canWrite() && tmp.canExecute()) return "/tmp"

        val javaTmp = System.getProperty("java.io.tmpdir") ?: "/tmp"
        if (javaTmp.startsWith("/")) return javaTmp.trimEnd('/').ifEmpty { "/" }
        return "/tmp"
    }

    override fun _runBash(
        cmdString: String,
        login: Boolean,
        timeout: Int,
        stdinData: String?
    ): ProcessHandle {
        val bash = _findBash()

        var effective = cmdString
        if (login) {
            val initFiles = _resolveShellInitFiles()
            if (initFiles.isNotEmpty()) {
                effective = _prependShellInit(cmdString, initFiles)
            }
        }
        val args = if (login) listOf(bash, "-l", "-c", effective) else listOf(bash, "-c", effective)
        val runEnv = _makeRunEnv(this.env)

        val pb = ProcessBuilder(args)
        pb.redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(runEnv)
        val proc = pb.start()

        if (stdinData != null) {
            _pipeStdin(proc, stdinData)
        } else {
            try {
                proc.outputStream.close()
            } catch (_: Exception) {
            }
        }

        return _LocalProcessHandle(proc)
    }

    override fun _killProcess(proc: ProcessHandle) {
        try {
            proc.kill()
        } catch (_: Exception) {
        }
    }

    override fun _updateCwd(result: MutableMap<String, Any?>) {
        try {
            val path = File(cwdFile).readText(Charsets.UTF_8).trim()
            if (path.isNotEmpty()) this.cwd = path
        } catch (_: Exception) {
        }
        _extractCwdFromOutput(result)
    }

    override fun cleanup() {
        for (f in listOf(snapshotPath, cwdFile)) {
            try {
                File(f).delete()
            } catch (_: Exception) {
            }
        }
    }
}

/** ProcessHandle wrapping a real java.lang.Process for the local backend. */
private class _LocalProcessHandle(private val proc: Process) : ProcessHandle {
    override fun poll(): Int? = if (proc.isAlive) null else proc.exitValue()
    override fun kill() {
        try {
            proc.destroy()
        } catch (_: Exception) {
        }
    }

    override fun wait(timeout: Double?): Int {
        if (timeout != null) {
            val ok = proc.waitFor(
                (timeout * 1000).toLong(),
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            if (!ok) return -1
            return proc.exitValue()
        }
        return proc.waitFor()
    }

    override fun stdout(): java.io.InputStream? = proc.inputStream
    override fun returncode(): Int? = if (proc.isAlive) null else proc.exitValue()
}
