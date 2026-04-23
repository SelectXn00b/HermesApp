/**
 * Dangerous command approval — detection, prompting, and per-session state.
 *
 * 1:1 对齐 hermes/tools/approval.py (Python 原始)
 *
 * This module is the single source of truth for the dangerous command system:
 * - Pattern detection (DANGEROUS_PATTERNS, detectDangerousCommand)
 * - Per-session approval state (thread-safe, keyed by sessionKey)
 * - Approval prompting (CLI interactive + gateway async)
 * - Smart approval via auxiliary LLM (auto-approve low-risk commands)
 * - Permanent allowlist persistence (config.yaml)
 *
 * Android note: CLI-interactive prompts and auxiliary-LLM smart approval
 * return "deny"/"escalate" by default; gateway notify callbacks are wired
 * through so Android UIs can register their own approval handlers.
 */
package com.xiaomo.hermes.hermes.tools

import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File
import java.text.Normalizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// ── Module-level state (Python module globals) ──────────────────────────

// Per-thread gateway session identity. Python uses contextvars; Android maps
// to ThreadLocal so gateway worker pools still see distinct values.
val _approvalSessionKey: ThreadLocal<String> = ThreadLocal.withInitial { "" }


fun setCurrentSessionKey(sessionKey: String): String {
    val prior = _approvalSessionKey.get() ?: ""
    _approvalSessionKey.set(sessionKey)
    return prior
}


fun resetCurrentSessionKey(token: String) {
    _approvalSessionKey.set(token)
}


fun getCurrentSessionKey(default: String = "default"): String {
    val sessionKey = _approvalSessionKey.get() ?: ""
    if (sessionKey.isNotEmpty()) return sessionKey
    return System.getenv("HERMES_SESSION_KEY") ?: default
}

// Sensitive write targets that should trigger approval even when referenced
// via shell expansions like $HOME or $HERMES_HOME.
const val _SSH_SENSITIVE_PATH: String = "(?:~|\\\$home|\\\$\\{home\\})/\\.ssh(?:/|\$)"
const val _HERMES_ENV_PATH: String =
    "(?:~\\/\\.hermes/|" +
    "(?:\\\$home|\\\$\\{home\\})/\\.hermes/|" +
    "(?:\\\$hermes_home|\\\$\\{hermes_home\\})/)" +
    "\\.env\\b"
val _SENSITIVE_WRITE_TARGET: String =
    "(?:/etc/|/dev/sd|$_SSH_SENSITIVE_PATH|$_HERMES_ENV_PATH)"

// =========================================================================
// Dangerous command patterns
// =========================================================================

val DANGEROUS_PATTERNS: List<Pair<String, String>> = listOf(
    "\\brm\\s+(-[^\\s]*\\s+)*/" to "delete in root path",
    "\\brm\\s+-[^\\s]*r" to "recursive delete",
    "\\brm\\s+--recursive\\b" to "recursive delete (long flag)",
    "\\bchmod\\s+(-[^\\s]*\\s+)*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)\\b" to "world/other-writable permissions",
    "\\bchmod\\s+--recursive\\b.*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)" to "recursive world/other-writable (long flag)",
    "\\bchown\\s+(-[^\\s]*)?R\\s+root" to "recursive chown to root",
    "\\bchown\\s+--recursive\\b.*root" to "recursive chown to root (long flag)",
    "\\bmkfs\\b" to "format filesystem",
    "\\bdd\\s+.*if=" to "disk copy",
    ">\\s*/dev/sd" to "write to block device",
    "\\bDROP\\s+(TABLE|DATABASE)\\b" to "SQL DROP",
    "\\bDELETE\\s+FROM\\b(?!.*\\bWHERE\\b)" to "SQL DELETE without WHERE",
    "\\bTRUNCATE\\s+(TABLE)?\\s*\\w" to "SQL TRUNCATE",
    ">\\s*/etc/" to "overwrite system config",
    "\\bsystemctl\\s+(-[^\\s]+\\s+)*(stop|restart|disable|mask)\\b" to "stop/restart system service",
    "\\bkill\\s+-9\\s+-1\\b" to "kill all processes",
    "\\bpkill\\s+-9\\b" to "force kill processes",
    ":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*\\}\\s*;\\s*:" to "fork bomb",
    "\\b(bash|sh|zsh|ksh)\\s+-[^\\s]*c(\\s+|\$)" to "shell command via -c/-lc flag",
    "\\b(python[23]?|perl|ruby|node)\\s+-[ec]\\s+" to "script execution via -e/-c flag",
    "\\b(curl|wget)\\b.*\\|\\s*(ba)?sh\\b" to "pipe remote content to shell",
    "\\b(bash|sh|zsh|ksh)\\s+<\\s*<?\\s*\\(\\s*(curl|wget)\\b" to "execute remote script via process substitution",
    "\\btee\\b.*[\"']?$_SENSITIVE_WRITE_TARGET" to "overwrite system file via tee",
    ">>?\\s*[\"']?$_SENSITIVE_WRITE_TARGET" to "overwrite system file via redirection",
    "\\bxargs\\s+.*\\brm\\b" to "xargs with rm",
    "\\bfind\\b.*-exec\\s+(/\\S*/)?rm\\b" to "find -exec rm",
    "\\bfind\\b.*-delete\\b" to "find -delete",
    "\\bhermes\\s+gateway\\s+(stop|restart)\\b" to "stop/restart hermes gateway (kills running agents)",
    "\\bhermes\\s+update\\b" to "hermes update (restarts gateway, kills running agents)",
    "gateway\\s+run\\b.*(&\\s*\$|&\\s*;|\\bdisown\\b|\\bsetsid\\b)" to "start gateway outside systemd (use 'systemctl --user restart hermes-gateway')",
    "\\bnohup\\b.*gateway\\s+run\\b" to "start gateway outside systemd (use 'systemctl --user restart hermes-gateway')",
    "\\b(pkill|killall)\\b.*\\b(hermes|gateway|cli\\.py)\\b" to "kill hermes/gateway process (self-termination)",
    "\\bkill\\b.*\\\$\\(\\s*pgrep\\b" to "kill process via pgrep expansion (self-termination)",
    "\\bkill\\b.*`\\s*pgrep\\b" to "kill process via backtick pgrep expansion (self-termination)",
    "\\b(cp|mv|install)\\b.*\\s/etc/" to "copy/move file into /etc/",
    "\\bsed\\s+-[^\\s]*i.*\\s/etc/" to "in-place edit of system config",
    "\\bsed\\s+--in-place\\b.*\\s/etc/" to "in-place edit of system config (long flag)",
    "\\b(python[23]?|perl|ruby|node)\\s+<<" to "script execution via heredoc",
    "\\bgit\\s+reset\\s+--hard\\b" to "git reset --hard (destroys uncommitted changes)",
    "\\bgit\\s+push\\b.*--force\\b" to "git force push (rewrites remote history)",
    "\\bgit\\s+push\\b.*-f\\b" to "git force push short flag (rewrites remote history)",
    "\\bgit\\s+clean\\s+-[^\\s]*f" to "git clean with force (deletes untracked files)",
    "\\bgit\\s+branch\\s+-D\\b" to "git branch force delete",
    "\\bchmod\\s+\\+x\\b.*[;&|]+\\s*\\./" to "chmod +x followed by immediate execution",
)


fun _legacyPatternKey(pattern: String): String {
    return if ("\\b" in pattern) pattern.split("\\b")[1] else pattern.take(20)
}


val _PATTERN_KEY_ALIASES: MutableMap<String, MutableSet<String>> = mutableMapOf()

private fun _populatePatternKeyAliases() {
    _PATTERN_KEY_ALIASES.clear()
    for ((pattern, description) in DANGEROUS_PATTERNS) {
        val legacyKey = _legacyPatternKey(pattern)
        _PATTERN_KEY_ALIASES.getOrPut(description) { mutableSetOf() }.addAll(setOf(description, legacyKey))
        _PATTERN_KEY_ALIASES.getOrPut(legacyKey) { mutableSetOf() }.addAll(setOf(legacyKey, description))
    }
}


fun _approvalKeyAliases(patternKey: String): Set<String> {
    if (_PATTERN_KEY_ALIASES.isEmpty()) _populatePatternKeyAliases()
    return _PATTERN_KEY_ALIASES[patternKey] ?: setOf(patternKey)
}


// =========================================================================
// Detection
// =========================================================================

fun _normalizeCommandForDetection(command: String): String {
    // Strip ANSI escape sequences (CSI, OSC, DCS, 8-bit C1).
    val ansiStripped = command.replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "")
        .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")
    // Strip null bytes.
    val nullStripped = ansiStripped.replace("\u0000", "")
    // NFKC normalize (fullwidth Latin, halfwidth Katakana, etc.).
    return Normalizer.normalize(nullStripped, Normalizer.Form.NFKC)
}


fun detectDangerousCommand(command: String): Triple<Boolean, String?, String?> {
    val commandLower = _normalizeCommandForDetection(command).lowercase()
    for ((pattern, description) in DANGEROUS_PATTERNS) {
        val re = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        if (re.containsMatchIn(commandLower)) {
            return Triple(true, description, description)
        }
    }
    return Triple(false, null, null)
}


// =========================================================================
// Per-session approval state (thread-safe)
// =========================================================================

// Renamed from Python module-level `_lock` to avoid JVM top-level clash
// with `tools/interrupt.py`'s `_lock` (both live in package …tools).
val _approvalLock: Any = Any()
val _pending: MutableMap<String, Map<String, Any?>> = mutableMapOf()
val _sessionApproved: MutableMap<String, MutableSet<String>> = mutableMapOf()
val _sessionYolo: MutableSet<String> = mutableSetOf()
val _permanentApproved: MutableSet<String> = mutableSetOf()


class _ApprovalEntry(val data: Map<String, Any?>) {
    val event: CountDownLatch = CountDownLatch(1)
    @Volatile var result: String? = null  // "once"|"session"|"always"|"deny"
}


val _gatewayQueues: MutableMap<String, MutableList<_ApprovalEntry>> = mutableMapOf()
val _gatewayNotifyCbs: MutableMap<String, (Map<String, Any?>) -> Unit> = mutableMapOf()


fun registerGatewayNotify(sessionKey: String, cb: (Map<String, Any?>) -> Unit) {
    synchronized(_approvalLock) {
        _gatewayNotifyCbs[sessionKey] = cb
    }
}


fun unregisterGatewayNotify(sessionKey: String) {
    val entries: List<_ApprovalEntry>
    synchronized(_approvalLock) {
        _gatewayNotifyCbs.remove(sessionKey)
        entries = _gatewayQueues.remove(sessionKey) ?: emptyList()
    }
    for (entry in entries) entry.event.countDown()
}


fun resolveGatewayApproval(sessionKey: String, choice: String, resolveAll: Boolean = false): Int {
    val targets: List<_ApprovalEntry>
    synchronized(_approvalLock) {
        val queue = _gatewayQueues[sessionKey] ?: return 0
        targets = if (resolveAll) {
            val all = queue.toList()
            queue.clear()
            all
        } else {
            listOf(queue.removeAt(0))
        }
        if (queue.isEmpty()) _gatewayQueues.remove(sessionKey)
    }
    for (entry in targets) {
        entry.result = choice
        entry.event.countDown()
    }
    return targets.size
}


fun hasBlockingApproval(sessionKey: String): Boolean {
    synchronized(_approvalLock) {
        return _gatewayQueues[sessionKey]?.isNotEmpty() == true
    }
}


fun submitPending(sessionKey: String, approval: Map<String, Any?>) {
    synchronized(_approvalLock) {
        _pending[sessionKey] = approval
    }
}


fun approveSession(sessionKey: String, patternKey: String) {
    synchronized(_approvalLock) {
        _sessionApproved.getOrPut(sessionKey) { mutableSetOf() }.add(patternKey)
    }
}


fun enableSessionYolo(sessionKey: String) {
    if (sessionKey.isEmpty()) return
    synchronized(_approvalLock) { _sessionYolo.add(sessionKey) }
}


fun disableSessionYolo(sessionKey: String) {
    if (sessionKey.isEmpty()) return
    synchronized(_approvalLock) { _sessionYolo.remove(sessionKey) }
}


fun clearSession(sessionKey: String) {
    if (sessionKey.isEmpty()) return
    synchronized(_approvalLock) {
        _sessionApproved.remove(sessionKey)
        _sessionYolo.remove(sessionKey)
        _pending.remove(sessionKey)
        _gatewayQueues.remove(sessionKey)
    }
}


fun isSessionYoloEnabled(sessionKey: String): Boolean {
    if (sessionKey.isEmpty()) return false
    synchronized(_approvalLock) { return sessionKey in _sessionYolo }
}


fun isCurrentSessionYoloEnabled(): Boolean {
    return isSessionYoloEnabled(getCurrentSessionKey(default = ""))
}


fun isApproved(sessionKey: String, patternKey: String): Boolean {
    val aliases = _approvalKeyAliases(patternKey)
    synchronized(_approvalLock) {
        if (aliases.any { it in _permanentApproved }) return true
        val sessionApprovals = _sessionApproved[sessionKey] ?: emptySet()
        return aliases.any { it in sessionApprovals }
    }
}


fun approvePermanent(patternKey: String) {
    synchronized(_approvalLock) { _permanentApproved.add(patternKey) }
}


fun loadPermanent(patterns: Set<String>) {
    synchronized(_approvalLock) { _permanentApproved.addAll(patterns) }
}


// =========================================================================
// Config persistence for permanent allowlist
// =========================================================================

fun loadPermanentAllowlist(): Set<String> {
    return try {
        val configFile = File(getHermesHome(), "config.yaml")
        if (!configFile.exists()) return emptySet()
        val content = configFile.readText()
        val block = Regex("(?m)^command_allowlist:\\s*\\n((?:  -.*\\n?)*)").find(content)
        val patterns = mutableSetOf<String>()
        if (block != null) {
            for (line in block.groupValues[1].lines()) {
                val m = Regex("^  -\\s*(.*)$").matchEntire(line) ?: continue
                var v = m.groupValues[1].trim()
                if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) v = v.substring(1, v.length - 1)
                else if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) v = v.substring(1, v.length - 1)
                if (v.isNotEmpty()) patterns.add(v)
            }
        }
        if (patterns.isNotEmpty()) loadPermanent(patterns)
        patterns
    } catch (_: Exception) {
        emptySet()
    }
}


fun savePermanentAllowlist(patterns: Set<String>) {
    // Android port: minimal YAML write — reads then rewrites command_allowlist block.
    try {
        val configFile = File(getHermesHome(), "config.yaml")
        val original = if (configFile.exists()) configFile.readText() else ""
        val newBlock = buildString {
            append("command_allowlist:\n")
            for (p in patterns) append("  - ${p}\n")
        }
        val updated = if (Regex("(?m)^command_allowlist:").containsMatchIn(original)) {
            original.replace(
                Regex("(?m)^command_allowlist:\\s*\\n(?:  -.*\\n?)*"),
                newBlock,
            )
        } else {
            (if (original.isEmpty()) "" else "$original\n") + newBlock
        }
        configFile.writeText(updated)
    } catch (_: Exception) {
        // Best-effort.
    }
}


// =========================================================================
// Approval prompting + orchestration
// =========================================================================

fun promptDangerousApproval(
    command: String,
    description: String,
    timeoutSeconds: Int? = null,
    allowPermanent: Boolean = true,
    approvalCallback: ((String, String, Boolean) -> String)? = null,
): String {
    if (approvalCallback != null) {
        return try {
            approvalCallback(command, description, allowPermanent)
        } catch (_: Exception) {
            "deny"
        }
    }
    // Android has no interactive stdin prompt; default to deny so unapproved
    // dangerous commands are never silently executed.
    return "deny"
}


fun _normalizeApprovalMode(mode: Any?): String {
    if (mode is Boolean) return if (mode == false) "off" else "manual"
    if (mode is String) {
        val normalized = mode.trim().lowercase()
        return normalized.ifEmpty { "manual" }
    }
    return "manual"
}


fun _getApprovalConfig(): Map<String, Any?> {
    return try {
        val configFile = File(getHermesHome(), "config.yaml")
        if (!configFile.exists()) return emptyMap()
        val content = configFile.readText()
        val block = Regex("(?m)^approvals:\\s*\\n((?:  \\S.*\\n?)*)").find(content) ?: return emptyMap()
        val result = mutableMapOf<String, Any?>()
        for (line in block.groupValues[1].lines()) {
            val m = Regex("^  ([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$").matchEntire(line) ?: continue
            val k = m.groupValues[1]
            var v: Any? = m.groupValues[2].trim()
            val s = v as String
            v = when {
                s == "true" -> true
                s == "false" -> false
                s.toIntOrNull() != null -> s.toInt()
                s.startsWith("\"") && s.endsWith("\"") -> s.substring(1, s.length - 1)
                else -> s
            }
            result[k] = v
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}


fun _getApprovalMode(): String {
    val mode = _getApprovalConfig()["mode"] ?: "manual"
    return _normalizeApprovalMode(mode)
}


fun _getApprovalTimeout(): Int {
    return try {
        (_getApprovalConfig()["timeout"] as? Number)?.toInt() ?: 60
    } catch (_: Exception) {
        60
    }
}


fun _getCronApprovalMode(): String {
    val mode = (_getApprovalConfig()["cron_mode"] ?: "deny").toString().lowercase().trim()
    return if (mode in setOf("approve", "off", "allow", "yes")) "approve" else "deny"
}


fun _smartApprove(command: String, description: String): String {
    // Android port: no auxiliary LLM wired in here — always escalate to manual.
    return "escalate"
}


fun checkDangerousCommand(
    command: String,
    envType: String,
    approvalCallback: ((String, String, Boolean) -> String)? = null,
): Map<String, Any?> {
    if (envType in setOf("docker", "singularity", "modal", "daytona")) {
        return mapOf("approved" to true, "message" to null)
    }

    if (!System.getenv("HERMES_YOLO_MODE").isNullOrEmpty() || isCurrentSessionYoloEnabled()) {
        return mapOf("approved" to true, "message" to null)
    }

    val (isDangerous, patternKey, description) = detectDangerousCommand(command)
    if (!isDangerous) return mapOf("approved" to true, "message" to null)

    val sessionKey = getCurrentSessionKey()
    if (isApproved(sessionKey, patternKey ?: "")) {
        return mapOf("approved" to true, "message" to null)
    }

    val isCli = !System.getenv("HERMES_INTERACTIVE").isNullOrEmpty()
    val isGateway = !System.getenv("HERMES_GATEWAY_SESSION").isNullOrEmpty()

    if (!isCli && !isGateway) {
        if (!System.getenv("HERMES_CRON_SESSION").isNullOrEmpty()) {
            if (_getCronApprovalMode() == "deny") {
                return mapOf(
                    "approved" to false,
                    "message" to "BLOCKED: Command flagged as dangerous ($description) " +
                        "but cron jobs run without a user present to approve it. " +
                        "Find an alternative approach that avoids this command. " +
                        "To allow dangerous commands in cron jobs, set " +
                        "approvals.cron_mode: approve in config.yaml.",
                )
            }
        }
        return mapOf("approved" to true, "message" to null)
    }

    if (isGateway || !System.getenv("HERMES_EXEC_ASK").isNullOrEmpty()) {
        submitPending(sessionKey, mapOf(
            "command" to command,
            "pattern_key" to patternKey,
            "description" to description,
        ))
        return mapOf(
            "approved" to false,
            "pattern_key" to patternKey,
            "status" to "approval_required",
            "command" to command,
            "description" to description,
            "message" to "\u26A0\uFE0F This command is potentially dangerous ($description). " +
                "Asking the user for approval.\n\n**Command:**\n```\n$command\n```",
        )
    }

    val choice = promptDangerousApproval(command, description ?: "", approvalCallback = approvalCallback)

    if (choice == "deny") {
        return mapOf(
            "approved" to false,
            "message" to "BLOCKED: User denied this potentially dangerous command (matched '$description' pattern). Do NOT retry this command - the user has explicitly rejected it.",
            "pattern_key" to patternKey,
            "description" to description,
        )
    }

    if (choice == "session") {
        approveSession(sessionKey, patternKey ?: "")
    } else if (choice == "always") {
        approveSession(sessionKey, patternKey ?: "")
        approvePermanent(patternKey ?: "")
        savePermanentAllowlist(_permanentApproved)
    }

    return mapOf("approved" to true, "message" to null)
}


// =========================================================================
// Combined pre-exec guard (tirith + dangerous command detection)
// =========================================================================

fun _formatTirithDescription(tirithResult: Map<String, Any?>): String {
    @Suppress("UNCHECKED_CAST")
    val findings = (tirithResult["findings"] as? List<Map<String, Any?>>) ?: emptyList()
    if (findings.isEmpty()) {
        val summary = tirithResult["summary"]?.toString() ?: "security issue detected"
        return "Security scan: $summary"
    }
    val parts = mutableListOf<String>()
    for (f in findings) {
        val severity = f["severity"]?.toString() ?: ""
        val title = f["title"]?.toString() ?: ""
        val desc = f["description"]?.toString() ?: ""
        parts.add(when {
            title.isNotEmpty() && desc.isNotEmpty() -> if (severity.isNotEmpty()) "[$severity] $title: $desc" else "$title: $desc"
            title.isNotEmpty() -> if (severity.isNotEmpty()) "[$severity] $title" else title
            else -> ""
        }.takeIf { it.isNotEmpty() } ?: "")
    }
    val filtered = parts.filter { it.isNotEmpty() }
    if (filtered.isEmpty()) {
        val summary = tirithResult["summary"]?.toString() ?: "security issue detected"
        return "Security scan: $summary"
    }
    return "Security scan — " + filtered.joinToString("; ")
}


fun checkAllCommandGuards(
    command: String,
    envType: String,
    approvalCallback: ((String, String, Boolean) -> String)? = null,
): Map<String, Any?> {
    if (envType in setOf("docker", "singularity", "modal", "daytona")) {
        return mapOf("approved" to true, "message" to null)
    }

    val approvalMode = _getApprovalMode()
    if (!System.getenv("HERMES_YOLO_MODE").isNullOrEmpty() || isCurrentSessionYoloEnabled() || approvalMode == "off") {
        return mapOf("approved" to true, "message" to null)
    }

    val isCli = !System.getenv("HERMES_INTERACTIVE").isNullOrEmpty()
    val isGateway = !System.getenv("HERMES_GATEWAY_SESSION").isNullOrEmpty()
    val isAsk = !System.getenv("HERMES_EXEC_ASK").isNullOrEmpty()

    if (!isCli && !isGateway && !isAsk) {
        if (!System.getenv("HERMES_CRON_SESSION").isNullOrEmpty() && _getCronApprovalMode() == "deny") {
            val (isDangerous, _, description) = detectDangerousCommand(command)
            if (isDangerous) {
                return mapOf(
                    "approved" to false,
                    "message" to "BLOCKED: Command flagged as dangerous ($description) " +
                        "but cron jobs run without a user present to approve it. " +
                        "Find an alternative approach that avoids this command. " +
                        "To allow dangerous commands in cron jobs, set " +
                        "approvals.cron_mode: approve in config.yaml.",
                )
            }
        }
        return mapOf("approved" to true, "message" to null)
    }

    // Android port: tirith is not wired in, so skip that branch (tirith_result = allow).
    val (isDangerous, patternKey, description) = detectDangerousCommand(command)

    val warnings = mutableListOf<Triple<String, String, Boolean>>()
    val sessionKey = getCurrentSessionKey()

    if (isDangerous && patternKey != null) {
        if (!isApproved(sessionKey, patternKey)) {
            warnings.add(Triple(patternKey, description ?: "", false))
        }
    }

    if (warnings.isEmpty()) return mapOf("approved" to true, "message" to null)

    if (approvalMode == "smart") {
        val combinedDescForLlm = warnings.joinToString("; ") { it.second }
        when (_smartApprove(command, combinedDescForLlm)) {
            "approve" -> {
                for ((key, _, _) in warnings) approveSession(sessionKey, key)
                return mapOf(
                    "approved" to true,
                    "message" to null,
                    "smart_approved" to true,
                    "description" to combinedDescForLlm,
                )
            }
            "deny" -> {
                return mapOf(
                    "approved" to false,
                    "message" to "BLOCKED by smart approval: $combinedDescForLlm. " +
                        "The command was assessed as genuinely dangerous. Do NOT retry.",
                    "smart_denied" to true,
                )
            }
            else -> {
                // escalate → fall through to manual prompt
            }
        }
    }

    val combinedDesc = warnings.joinToString("; ") { it.second }
    val primaryKey = warnings[0].first
    val allKeys = warnings.map { it.first }
    val hasTirith = warnings.any { it.third }

    if (isGateway || isAsk) {
        val notifyCb: ((Map<String, Any?>) -> Unit)?
        synchronized(_approvalLock) { notifyCb = _gatewayNotifyCbs[sessionKey] }

        if (notifyCb != null) {
            val approvalData = mapOf(
                "command" to command,
                "pattern_key" to primaryKey,
                "pattern_keys" to allKeys,
                "description" to combinedDesc,
            )
            val entry = _ApprovalEntry(approvalData)
            synchronized(_approvalLock) {
                _gatewayQueues.getOrPut(sessionKey) { mutableListOf() }.add(entry)
            }

            try {
                notifyCb(approvalData)
            } catch (_: Exception) {
                synchronized(_approvalLock) {
                    val queue = _gatewayQueues[sessionKey] ?: mutableListOf()
                    queue.remove(entry)
                    if (queue.isEmpty()) _gatewayQueues.remove(sessionKey)
                }
                return mapOf(
                    "approved" to false,
                    "message" to "BLOCKED: Failed to send approval request to user. Do NOT retry.",
                    "pattern_key" to primaryKey,
                    "description" to combinedDesc,
                )
            }

            val timeout = try {
                (_getApprovalConfig()["gateway_timeout"] as? Number)?.toInt() ?: 300
            } catch (_: Exception) { 300 }

            val resolved = entry.event.await(timeout.toLong(), TimeUnit.SECONDS)

            synchronized(_approvalLock) {
                val queue = _gatewayQueues[sessionKey] ?: mutableListOf()
                queue.remove(entry)
                if (queue.isEmpty()) _gatewayQueues.remove(sessionKey)
            }

            val choice = entry.result
            if (!resolved || choice == null || choice == "deny") {
                val reason = if (!resolved) "timed out" else "denied by user"
                return mapOf(
                    "approved" to false,
                    "message" to "BLOCKED: Command $reason. Do NOT retry this command.",
                    "pattern_key" to primaryKey,
                    "description" to combinedDesc,
                )
            }

            for ((key, _, isTirith) in warnings) {
                when {
                    choice == "session" || (choice == "always" && isTirith) -> approveSession(sessionKey, key)
                    choice == "always" -> {
                        approveSession(sessionKey, key)
                        approvePermanent(key)
                        savePermanentAllowlist(_permanentApproved)
                    }
                }
            }

            return mapOf(
                "approved" to true,
                "message" to null,
                "user_approved" to true,
                "description" to combinedDesc,
            )
        }

        submitPending(sessionKey, mapOf(
            "command" to command,
            "pattern_key" to primaryKey,
            "pattern_keys" to allKeys,
            "description" to combinedDesc,
        ))
        return mapOf(
            "approved" to false,
            "pattern_key" to primaryKey,
            "status" to "approval_required",
            "command" to command,
            "description" to combinedDesc,
            "message" to "\u26A0\uFE0F $combinedDesc. Asking the user for approval.\n\n**Command:**\n```\n$command\n```",
        )
    }

    val choice = promptDangerousApproval(
        command, combinedDesc,
        allowPermanent = !hasTirith,
        approvalCallback = approvalCallback,
    )

    if (choice == "deny") {
        return mapOf(
            "approved" to false,
            "message" to "BLOCKED: User denied. Do NOT retry.",
            "pattern_key" to primaryKey,
            "description" to combinedDesc,
        )
    }

    for ((key, _, isTirith) in warnings) {
        when {
            choice == "session" || (choice == "always" && isTirith) -> approveSession(sessionKey, key)
            choice == "always" -> {
                approveSession(sessionKey, key)
                approvePermanent(key)
                savePermanentAllowlist(_permanentApproved)
            }
        }
    }

    return mapOf(
        "approved" to true,
        "message" to null,
        "user_approved" to true,
        "description" to combinedDesc,
    )
}


// Load permanent allowlist on first access. Python loads it at import time;
// Android defers to first access to avoid blocking application startup.
private val _permanentAllowlistLoaded: Unit = run { loadPermanentAllowlist(); Unit }
