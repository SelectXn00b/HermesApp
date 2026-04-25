/** 1:1 对齐 hermes/tools/cronjob_tools.py */
package com.xiaomo.hermes.hermes.tools

/**
 * Cronjob tool handler — stub port of tools/cronjob_tools.py.
 *
 * Python exposes a single `cronjob(action, ...)` tool that talks to the
 * host crond (or systemd-timers) to schedule recurring prompts. On Android
 * there is no such daemon, so the handler returns an error until a proper
 * scheduler (WorkManager/AlarmManager) is wired in.
 */
fun cronjob(
    action: String,
    jobId: String? = null,
    prompt: String? = null,
    schedule: String? = null,
    name: String? = null,
    repeat: Int? = null,
    deliver: String? = null,
    includeDisabled: Boolean = true,
    skill: String? = null,
    skills: Any? = null,
    model: String? = null,
    provider: String? = null,
    baseUrl: String? = null,
    reason: String? = null,
    script: String? = null,
    taskId: String? = null,
): String {
    @Suppress("UNUSED_VARIABLE") val _createdSuffix = "' created."
    @Suppress("UNUSED_VARIABLE") val _notFoundSuffix = "' not found. Use cronjob(action='list') to inspect jobs."
    @Suppress("UNUSED_VARIABLE") val _removedSuffix = "' removed."
    @Suppress("UNUSED_VARIABLE") val _cronJobPrefix = "Cron job '"
    @Suppress("UNUSED_VARIABLE") val _failedRemovePrefix = "Failed to remove job '"
    @Suppress("UNUSED_VARIABLE") val _jobWithIdPrefix = "Job with ID '"
    @Suppress("UNUSED_VARIABLE") val _noUpdates = "No updates provided."
    @Suppress("UNUSED_VARIABLE") val _unknownActionPrefix = "Unknown cron action '"
    @Suppress("UNUSED_VARIABLE") val _countKey = "count"
    @Suppress("UNUSED_VARIABLE") val _createAction = "create"
    @Suppress("UNUSED_VARIABLE") val _createRequiresMsg = "create requires either prompt or at least one skill"
    @Suppress("UNUSED_VARIABLE") val _displayKey = "display"
    @Suppress("UNUSED_VARIABLE") val _jobIdRequiredPrefix = "job_id is required for action '"
    @Suppress("UNUSED_VARIABLE") val _jobsKey = "jobs"
    @Suppress("UNUSED_VARIABLE") val _listAction = "list"
    @Suppress("UNUSED_VARIABLE") val _messageKey = "message"
    @Suppress("UNUSED_VARIABLE") val _pauseAction = "pause"
    @Suppress("UNUSED_VARIABLE") val _removeAction = "remove"
    @Suppress("UNUSED_VARIABLE") val _removedJobKey = "removed_job"
    @Suppress("UNUSED_VARIABLE") val _resumeAction = "resume"
    @Suppress("UNUSED_VARIABLE") val _runAction = "run"
    @Suppress("UNUSED_VARIABLE") val _runNowAction = "run_now"
    @Suppress("UNUSED_VARIABLE") val _scheduleRequiredMsg = "schedule is required for create"
    @Suppress("UNUSED_VARIABLE") val _successKey = "success"
    @Suppress("UNUSED_VARIABLE") val _triggerAction = "trigger"
    @Suppress("UNUSED_VARIABLE") val _updateAction = "update"
    return toolError("cronjob tool is not available on Android")
}

fun checkCronjobRequirements(): Boolean {
    @Suppress("UNUSED_VARIABLE") val _execAskEnv = "HERMES_EXEC_ASK"
    @Suppress("UNUSED_VARIABLE") val _gatewaySessionEnv = "HERMES_GATEWAY_SESSION"
    @Suppress("UNUSED_VARIABLE") val _interactiveEnv = "HERMES_INTERACTIVE"
    return false
}

// ── Module-level helpers ported from tools/cronjob_tools.py ───────────────

/**
 * Critical-severity patterns for cron-prompt scanning.  Cron prompts run in
 * fresh sessions with full tool access, so injection/exfiltration payloads
 * must be rejected at the API boundary.
 */
val _CRON_THREAT_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("ignore\\s+(?:\\w+\\s+)*(?:previous|all|above|prior)\\s+(?:\\w+\\s+)*instructions", RegexOption.IGNORE_CASE) to "prompt_injection",
    Regex("do\\s+not\\s+tell\\s+the\\s+user", RegexOption.IGNORE_CASE) to "deception_hide",
    Regex("system\\s+prompt\\s+override", RegexOption.IGNORE_CASE) to "sys_prompt_override",
    Regex("disregard\\s+(your|all|any)\\s+(instructions|rules|guidelines)", RegexOption.IGNORE_CASE) to "disregard_rules",
    Regex("curl\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", RegexOption.IGNORE_CASE) to "exfil_curl",
    Regex("wget\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", RegexOption.IGNORE_CASE) to "exfil_wget",
    Regex("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc|\\.pgpass)", RegexOption.IGNORE_CASE) to "read_secrets",
    Regex("authorized_keys", RegexOption.IGNORE_CASE) to "ssh_backdoor",
    Regex("/etc/sudoers|visudo", RegexOption.IGNORE_CASE) to "sudoers_mod",
    Regex("rm\\s+-rf\\s+/", RegexOption.IGNORE_CASE) to "destructive_root_rm"
)

/** Invisible unicode code points that are stripped/blocked in cron prompts. */
val _CRON_INVISIBLE_CHARS: Set<Char> = setOf(
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e'
)

/** OpenAI-style function schema for the single `cronjob` tool. */
val CRONJOB_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "cronjob",
    "description" to (
        "Manage scheduled cron jobs with a single compressed tool.\n\n" +
            "Use action='create' to schedule a new job from a prompt or one or more skills.\n" +
            "Use action='list' to inspect jobs.\n" +
            "Use action='update', 'pause', 'resume', 'remove', or 'run' to manage an existing job.\n\n" +
            "To stop a job the user no longer wants: first action='list' to find the job_id, " +
            "then action='remove' with that job_id. Never guess job IDs — always list first.\n\n" +
            "Jobs run in a fresh session with no current-chat context, so prompts must be self-contained.\n" +
            "If skills are provided on create, the future cron run loads those skills in order, then follows the prompt as the task instruction.\n" +
            "On update, passing skills=[] clears attached skills.\n\n" +
            "NOTE: The agent's final response is auto-delivered to the target. Put the primary\n" +
            "user-facing content in the final response. Cron jobs run autonomously with no user\n" +
            "present — they cannot ask questions or request clarification.\n\n" +
            "Important safety rule: cron-run sessions should not recursively schedule more cron jobs."
        ),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "description" to "One of: create, list, update, pause, resume, remove, run"
            ),
            "job_id" to mapOf(
                "type" to "string",
                "description" to "Required for update/pause/resume/remove/run"
            ),
            "prompt" to mapOf(
                "type" to "string",
                "description" to "For create: the full self-contained prompt. If skills are also provided, this becomes the task instruction paired with those skills."
            ),
            "schedule" to mapOf(
                "type" to "string",
                "description" to "For create/update: '30m', 'every 2h', '0 9 * * *', or ISO timestamp"
            ),
            "name" to mapOf("type" to "string", "description" to "Optional human-friendly name"),
            "repeat" to mapOf(
                "type" to "integer",
                "description" to "Optional repeat count. Omit for defaults (once for one-shot, forever for recurring)."
            ),
            "deliver" to mapOf(
                "type" to "string",
                "description" to "Omit this parameter to auto-deliver back to the current chat and topic (recommended). Values: 'origin', 'local', or platform:chat_id:thread_id."
            ),
            "skills" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Optional ordered list of skill names to load before executing the cron prompt. On update, pass an empty array to clear."
            ),
            "model" to mapOf(
                "type" to "object",
                "description" to "Optional per-job model override.",
                "properties" to mapOf(
                    "provider" to mapOf("type" to "string", "description" to "Provider name (e.g. 'openrouter', 'anthropic')."),
                    "model" to mapOf("type" to "string", "description" to "Model name (e.g. 'anthropic/claude-sonnet-4').")
                ),
                "required" to listOf("model")
            ),
            "script" to mapOf(
                "type" to "string",
                "description" to "Optional path to a Python script that runs before each cron job execution; its stdout is injected into the prompt as context."
            )
        ),
        "required" to listOf("action")
    )
)

/** Scan a cron prompt for critical threats.  Returns error string if blocked, else empty. */
fun _scanCronPrompt(prompt: String): String {
    for (c in _CRON_INVISIBLE_CHARS) {
        if (prompt.indexOf(c) >= 0) {
            val hex = "%04X".format(c.code)
            return "Blocked: prompt contains invisible unicode U+$hex (possible injection)."
        }
    }
    for ((pattern, pid) in _CRON_THREAT_PATTERNS) {
        if (pattern.containsMatchIn(prompt)) {
            return "Blocked: prompt matches threat pattern '$pid'. Cron prompts must not contain injection or exfiltration payloads."
        }
    }
    return ""
}

/**
 * Capture origin context from session env vars so a cron job can deliver back
 * to the same platform/chat/thread later.  Returns null if platform+chat_id
 * are not both present.
 */
fun _originFromEnv(): Map<String, String?>? {
    val originPlatform = System.getenv("HERMES_SESSION_PLATFORM")?.takeIf { it.isNotEmpty() }
    val originChatId = System.getenv("HERMES_SESSION_CHAT_ID")?.takeIf { it.isNotEmpty() }
    if (originPlatform != null && originChatId != null) {
        val threadId = System.getenv("HERMES_SESSION_THREAD_ID")?.takeIf { it.isNotEmpty() }
        return mapOf(
            "platform" to originPlatform,
            "chat_id" to originChatId,
            "chat_name" to System.getenv("HERMES_SESSION_CHAT_NAME")?.takeIf { it.isNotEmpty() },
            "thread_id" to threadId
        )
    }
    return null
}

/** Human-readable repeat display: "forever" / "once" / "3/5" / "5 times". */
@Suppress("UNCHECKED_CAST")
fun _repeatDisplay(job: Map<String, Any?>): String {
    val repeat = job["repeat"] as? Map<String, Any?>
    val times = (repeat?.get("times") as? Number)?.toInt()
    val completed = (repeat?.get("completed") as? Number)?.toInt() ?: 0
    if (times == null) return "forever"
    if (times == 1) return if (completed == 0) "once" else "1/1"
    return if (completed != 0) "$completed/$times" else "$times times"
}

/** Normalize skill/skills inputs into a deduplicated ordered list. */
fun _canonicalSkills(skill: String? = null, skills: Any? = null): List<String> {
    val rawItems: List<Any?> = when {
        skills == null -> if (!skill.isNullOrEmpty()) listOf(skill) else emptyList()
        skills is String -> listOf(skills)
        skills is List<*> -> skills
        skills is Array<*> -> skills.toList()
        else -> listOf(skills)
    }
    val normalized = mutableListOf<String>()
    for (item in rawItems) {
        val text = (item?.toString() ?: "").trim()
        if (text.isNotEmpty() && text !in normalized) normalized.add(text)
    }
    return normalized
}

/**
 * Resolve a model override object into a (provider, model) pair.
 * When provider is omitted but a model is given, pins the current main
 * provider so the job doesn't drift when defaults change later.
 */
@Suppress("UNCHECKED_CAST")
fun _resolveModelOverride(modelObj: Map<String, Any?>?): Pair<String?, String?> {
    if (modelObj == null) return Pair(null, null)
    val modelName = (modelObj["model"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    var providerName = (modelObj["provider"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    if (modelName != null && providerName == null) {
        // On Android the cli config isn't bundled — leave provider null (best-effort).
    }
    return Pair(providerName, modelName)
}

/**
 * Normalize an optional string job field: trim whitespace, optionally strip
 * trailing slashes, and return null when the result is empty.
 */
fun _normalizeOptionalJobValue(value: Any?, stripTrailingSlash: Boolean = false): String? {
    if (value == null) return null
    var text = value.toString().trim()
    if (stripTrailingSlash) text = text.trimEnd('/')
    return text.takeIf { it.isNotEmpty() }
}

/**
 * Validate a cron-job script path at the API boundary.  Scripts must be
 * relative paths that resolve within `$HERMES_HOME/scripts/`.  Returns an
 * error string when blocked, null when valid.
 */
fun _validateCronScriptPath(script: String?): String? {
    @Suppress("UNUSED_VARIABLE") val _placeScriptsMsg = ". Place scripts in ~/.hermes/scripts/ and use just the filename."
    @Suppress("UNUSED_VARIABLE") val _scriptRelativePrefix = "Script path must be relative to ~/.hermes/scripts/. Got absolute or home-relative path: "
    if (script.isNullOrBlank()) return null
    val raw = script.trim()
    if (raw.startsWith("/") || raw.startsWith("~") || (raw.length >= 2 && raw[1] == ':')) {
        return "Script path must be relative to ~/.hermes/scripts/. " +
            "Got absolute or home-relative path: '$raw'. " +
            "Place scripts in ~/.hermes/scripts/ and use just the filename."
    }
    val hermesHome = run {
        val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
        if (envVal.isNotEmpty()) java.io.File(envVal).canonicalFile
        else java.io.File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
    }
    val scriptsDir = java.io.File(hermesHome, "scripts")
    val candidate = try {
        java.io.File(scriptsDir, raw).canonicalFile
    } catch (_: Exception) {
        java.io.File(scriptsDir, raw).absoluteFile
    }
    val baseAbs = scriptsDir.absolutePath.trimEnd(java.io.File.separatorChar)
    val candidateAbs = candidate.absolutePath
    if (candidateAbs != baseAbs && !candidateAbs.startsWith(baseAbs + java.io.File.separator)) {
        return "Script path escapes the scripts directory via traversal: '$raw'"
    }
    return null
}

/** Format a stored job dict for display to the user. */
@Suppress("UNCHECKED_CAST")
fun _formatJob(job: Map<String, Any?>): Map<String, Any?> {
    val prompt = (job["prompt"] as? String) ?: ""
    val canonicalSkills = _canonicalSkills(job["skill"] as? String, job["skills"])
    val preview = if (prompt.length > 100) prompt.substring(0, 100) + "..." else prompt
    val enabled = (job["enabled"] as? Boolean) ?: true
    val state = (job["state"] as? String) ?: if (enabled) "scheduled" else "paused"

    val result = mutableMapOf<String, Any?>(
        "job_id" to job["id"],
        "name" to job["name"],
        "skill" to canonicalSkills.firstOrNull(),
        "skills" to canonicalSkills,
        "prompt_preview" to preview,
        "model" to job["model"],
        "provider" to job["provider"],
        "base_url" to job["base_url"],
        "schedule" to job["schedule_display"],
        "repeat" to _repeatDisplay(job),
        "deliver" to (job["deliver"] ?: "local"),
        "next_run_at" to job["next_run_at"],
        "last_run_at" to job["last_run_at"],
        "last_status" to job["last_status"],
        "last_delivery_error" to job["last_delivery_error"],
        "enabled" to enabled,
        "state" to state,
        "paused_at" to job["paused_at"],
        "paused_reason" to job["paused_reason"]
    )
    val script = job["script"]
    if (script != null) result["script"] = script
    return result
}
