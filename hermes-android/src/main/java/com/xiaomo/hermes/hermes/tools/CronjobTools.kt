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
): String = toolError("cronjob tool is not available on Android")

fun checkCronjobRequirements(): Boolean = false
