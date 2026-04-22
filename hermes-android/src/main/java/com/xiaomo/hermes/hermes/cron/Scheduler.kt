/** 1:1 对齐 hermes/cron/scheduler.py */
package com.xiaomo.hermes.hermes.cron

import com.xiaomo.hermes.hermes.getHermesHome
import com.xiaomo.hermes.hermes.getLogger
import com.xiaomo.hermes.hermes.hermesNow
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*

/**
 * Cron job scheduler - executes due jobs.
 *
 * Provides [tick] which checks for due jobs and runs them.
 * The gateway calls this every 60 seconds from a background thread.
 *
 * Uses a file-based lock (<HERMES_HOME>/cron/.tick.lock) so only one tick
 * runs at a time if multiple processes overlap.
 */

private val logger = getLogger("cron.scheduler")

// Valid delivery platforms
private val _KNOWN_DELIVERY_PLATFORMS = setOf(
    "telegram", "discord", "slack", "whatsapp", "signal",
    "matrix", "mattermost", "homeassistant", "dingtalk", "feishu",
    "wecom", "wecom_callback", "weixin", "sms", "email", "webhook",
    "bluebubbles", "qqbot"
)

// Platforms that support a configured cron/notification home target
private val _HOME_TARGET_ENV_VARS = mapOf(
    "matrix" to "MATRIX_HOME_ROOM",
    "telegram" to "TELEGRAM_HOME_CHANNEL",
    "discord" to "DISCORD_HOME_CHANNEL",
    "slack" to "SLACK_HOME_CHANNEL",
    "signal" to "SIGNAL_HOME_CHANNEL",
    "mattermost" to "MATTERMOST_HOME_CHANNEL",
    "sms" to "SMS_HOME_CHANNEL",
    "email" to "EMAIL_HOME_ADDRESS",
    "dingtalk" to "DINGTALK_HOME_CHANNEL",
    "feishu" to "FEISHU_HOME_CHANNEL",
    "wecom" to "WECOM_HOME_CHANNEL",
    "weixin" to "WEIXIN_HOME_CHANNEL",
    "bluebubbles" to "BLUEBUBBLES_HOME_CHANNEL",
    "qqbot" to "QQBOT_HOME_CHANNEL"
)

// Legacy env var names kept for back-compat
private val _LEGACY_HOME_TARGET_ENV_VARS = mapOf(
    "QQBOT_HOME_CHANNEL" to "QQ_HOME_CHANNEL"
)

/** Sentinel: when a cron agent has nothing new to report. */
const val SILENT_MARKER = "[SILENT]"

// File-based lock directory
private val _LOCK_DIR: File get() = File(getHermesHome(), "cron")
private val _LOCK_FILE: File get() = File(_LOCK_DIR, ".tick.lock")

// Default script timeout
private const val _DEFAULT_SCRIPT_TIMEOUT = 120 // seconds

/**
 * Delivery target resolved from job config.
 */
data class DeliveryTarget(
    val platform: String,
    val chatId: String,
    val threadId: String? = null
)

// =============================================================================
// Origin & Delivery Resolution
// =============================================================================

/**
 * Extract origin info from a job, preserving any extra routing metadata.
 * Python: _resolve_origin(job)
 */
@Suppress("UNCHECKED_CAST")
fun resolveOrigin(job: Map<String, Any?>): Map<String, Any?>? {
    val origin = job["origin"] as? Map<String, Any?> ?: return null
    val platform = origin["platform"] as? String
    val chatId = origin["chat_id"]
    if (platform != null && chatId != null) {
        return origin
    }
    return null
}

/**
 * Return the configured home target chat/room ID for a delivery platform.
 * Python: _get_home_target_chat_id(platform_name)
 */
private fun _getHomeTargetChatId(platformName: String): String {
    val envVar = _HOME_TARGET_ENV_VARS[platformName.lowercase()] ?: return ""
    var value = System.getenv(envVar) ?: ""
    if (value.isEmpty()) {
        val legacy = _LEGACY_HOME_TARGET_ENV_VARS[envVar]
        if (legacy != null) {
            value = System.getenv(legacy) ?: ""
        }
    }
    return value
}

/**
 * Resolve one concrete auto-delivery target for a cron job.
 * Python: _resolve_single_delivery_target(job, deliver_value)
 */
@Suppress("UNCHECKED_CAST")
private fun _resolveSingleDeliveryTarget(
    job: Map<String, Any?>,
    deliverValue: String
): DeliveryTarget? {
    val origin = resolveOrigin(job)

    if (deliverValue == "local") return null

    if (deliverValue == "origin") {
        if (origin != null) {
            return DeliveryTarget(
                platform = origin["platform"] as String,
                chatId = origin["chat_id"].toString(),
                threadId = origin["thread_id"] as? String
            )
        }
        // Origin missing — try each platform's home channel as fallback
        for ((platformName, _) in _HOME_TARGET_ENV_VARS) {
            val chatId = _getHomeTargetChatId(platformName)
            if (chatId.isNotEmpty()) {
                logger.info(
                    "Job '${job["name"] ?: job["id"]}' has deliver=origin but no origin; " +
                    "falling back to $platformName home channel"
                )
                return DeliveryTarget(
                    platform = platformName,
                    chatId = chatId,
                    threadId = null
                )
            }
        }
        return null
    }

    if (":" in deliverValue) {
        val colonIndex = deliverValue.indexOf(':')
        val platformName = deliverValue.substring(0, colonIndex)
        val rest = deliverValue.substring(colonIndex + 1)
        // Simplified: use rest as chat_id directly
        return DeliveryTarget(
            platform = platformName,
            chatId = rest,
            threadId = null
        )
    }

    // Bare platform name
    val platformName = deliverValue
    if (origin != null && (origin["platform"] as? String) == platformName) {
        return DeliveryTarget(
            platform = platformName,
            chatId = origin["chat_id"].toString(),
            threadId = origin["thread_id"] as? String
        )
    }

    if (platformName.lowercase() !in _KNOWN_DELIVERY_PLATFORMS) return null
    val chatId = _getHomeTargetChatId(platformName)
    if (chatId.isEmpty()) return null

    return DeliveryTarget(
        platform = platformName,
        chatId = chatId,
        threadId = null
    )
}

/**
 * Resolve all concrete auto-delivery targets for a cron job (supports comma-separated deliver).
 * Python: _resolve_delivery_targets(job)
 */
fun resolveDeliveryTargets(job: Map<String, Any?>): List<DeliveryTarget> {
    val deliver = job["deliver"] as? String ?: "local"
    if (deliver == "local") return emptyList()

    val parts = deliver.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val seen = mutableSetOf<Triple<String, String, String?>>()
    val targets = mutableListOf<DeliveryTarget>()

    for (part in parts) {
        val target = _resolveSingleDeliveryTarget(job, part) ?: continue
        val key = Triple(target.platform.lowercase(), target.chatId, target.threadId)
        if (key !in seen) {
            seen.add(key)
            targets.add(target)
        }
    }
    return targets
}

/**
 * Resolve the concrete auto-delivery target for a cron job, if any.
 * Python: _resolve_delivery_target(job)
 */
fun resolveDeliveryTarget(job: Map<String, Any?>): DeliveryTarget? {
    val targets = resolveDeliveryTargets(job)
    return targets.firstOrNull()
}

// =============================================================================
// Media Extensions
// =============================================================================

// Media extension sets — kept in sync with Python
private val _AUDIO_EXTS = setOf(".ogg", ".opus", ".mp3", ".wav", ".m4a")
private val _VIDEO_EXTS = setOf(".mp4", ".mov", ".avi", ".mkv", ".webm", ".3gp")
private val _IMAGE_EXTS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

// =============================================================================
// Script Execution
// =============================================================================

/**
 * Resolve cron pre-run script timeout.
 * Python: _get_script_timeout()
 */
private fun _getScriptTimeout(): Int {
    val envValue = System.getenv("HERMES_CRON_SCRIPT_TIMEOUT")?.trim()
    if (!envValue.isNullOrEmpty()) {
        try {
            val timeout = envValue.toDouble().toInt()
            if (timeout > 0) return timeout
        } catch (_: NumberFormatException) {
            logger.warning("Invalid HERMES_CRON_SCRIPT_TIMEOUT=$envValue; using default")
        }
    }
    return _DEFAULT_SCRIPT_TIMEOUT
}

/**
 * Execute a cron job's data-collection script and capture its output.
 * Python: _run_job_script(script_path)
 *
 * On Android, scripts are not supported directly. Returns failure with explanation.
 */
private fun _runJobScript(scriptPath: String): Pair<Boolean, String> {
    // Android doesn't support running arbitrary Python scripts
    return Pair(
        false,
        "Script execution is not supported on Android. " +
        "Script path: $scriptPath"
    )
}

// =============================================================================
// Prompt Building
// =============================================================================

/**
 * Build the effective prompt for a cron job, optionally loading skills first.
 * Python: _build_job_prompt(job)
 */
@Suppress("UNCHECKED_CAST")
fun buildJobPrompt(job: Map<String, Any?>): String {
    var prompt = job["prompt"] as? String ?: ""

    // Run data-collection script if configured
    val scriptPath = job["script"] as? String
    if (!scriptPath.isNullOrEmpty()) {
        val (success, scriptOutput) = _runJobScript(scriptPath)
        prompt = if (success) {
            if (scriptOutput.isNotEmpty()) {
                "## Script Output\n" +
                "The following data was collected by a pre-run script. " +
                "Use it as context for your analysis.\n\n" +
                "```\n$scriptOutput\n```\n\n" +
                prompt
            } else {
                "[Script ran successfully but produced no output.]\n\n$prompt"
            }
        } else {
            "## Script Error\n" +
            "The data-collection script failed. Report this to the user.\n\n" +
            "```\n$scriptOutput\n```\n\n" +
            prompt
        }
    }

    // Always prepend cron execution guidance
    val cronHint = (
        "[SYSTEM: You are running as a scheduled cron job. " +
        "DELIVERY: Your final response will be automatically delivered " +
        "to the user — do NOT use send_message or try to deliver " +
        "the output yourself. Just produce your report/output as your " +
        "final response and the system handles the rest. " +
        "SILENT: If there is genuinely nothing new to report, respond " +
        "with exactly \"[SILENT]\" (nothing else) to suppress delivery. " +
        "Never combine [SILENT] with content — either report your " +
        "findings normally, or say [SILENT] and nothing more.]\n\n"
    )
    prompt = cronHint + prompt

    var skills = job["skills"] as? List<String>
    if (skills == null) {
        val legacy = job["skill"] as? String
        skills = if (legacy != null) listOf(legacy) else emptyList()
    }

    val skillNames = skills.map { it.trim() }.filter { it.isNotEmpty() }
    if (skillNames.isEmpty()) return prompt

    // On Android, skill loading is simplified — we note the skill names
    // but can't load full skill content like the Python version does
    val parts = mutableListOf<String>()
    for (skillName in skillNames) {
        if (parts.isNotEmpty()) parts.add("")
        parts.add(
            "[SYSTEM: The user has invoked the \"$skillName\" skill, " +
            "indicating they want you to follow its instructions. " +
            "Note: Full skill content loading is not available on Android.]"
        )
    }

    if (prompt.isNotEmpty()) {
        parts.add("")
        parts.add("The user has provided the following instruction alongside the skill invocation: $prompt")
    }
    return parts.joinToString("\n")
}

// =============================================================================
// Job Execution
// =============================================================================

/**
 * Result of running a single cron job.
 */
data class JobRunResult(
    val success: Boolean,
    val fullOutputDoc: String,
    val finalResponse: String,
    val errorMessage: String?
)

/**
 * Execute a single cron job.
 * Python: run_job(job)
 *
 * On Android, this is a simplified version that builds the prompt
 * and delegates to the agent runtime.
 */
fun runJob(job: Map<String, Any?>): JobRunResult {
    val jobId = job["id"] as? String ?: "unknown"
    val jobName = job["name"] as? String ?: jobId
    val prompt = buildJobPrompt(job)

    logger.info("Running job '$jobName' (ID: $jobId)")
    logger.info("Prompt: ${prompt.take(100)}")

    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val nowStr = sdf.format(hermesNow())

    return try {
        // TODO: Integrate with Android agent runtime (AIAgent equivalent)
        // For now, return a placeholder indicating the job was processed
        val finalResponse = ""
        val loggedResponse = finalResponse.ifEmpty { "(No response generated)" }

        val output = buildString {
            appendLine("# Cron Job: $jobName")
            appendLine()
            appendLine("**Job ID:** $jobId")
            appendLine("**Run Time:** $nowStr")
            appendLine("**Schedule:** ${job["schedule_display"] ?: "N/A"}")
            appendLine()
            appendLine("## Prompt")
            appendLine()
            appendLine(prompt)
            appendLine()
            appendLine("## Response")
            appendLine()
            appendLine(loggedResponse)
        }

        logger.info("Job '$jobName' completed successfully")
        JobRunResult(
            success = true,
            fullOutputDoc = output,
            finalResponse = finalResponse,
            errorMessage = null
        )
    } catch (e: Exception) {
        val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
        logger.error("Job '$jobName' failed: $errorMsg")

        val output = buildString {
            appendLine("# Cron Job: $jobName (FAILED)")
            appendLine()
            appendLine("**Job ID:** $jobId")
            appendLine("**Run Time:** $nowStr")
            appendLine("**Schedule:** ${job["schedule_display"] ?: "N/A"}")
            appendLine()
            appendLine("## Prompt")
            appendLine()
            appendLine(prompt)
            appendLine()
            appendLine("## Error")
            appendLine()
            appendLine("```")
            appendLine(errorMsg)
            appendLine("```")
        }

        JobRunResult(
            success = false,
            fullOutputDoc = output,
            finalResponse = "",
            errorMessage = errorMsg
        )
    }
}

// =============================================================================
// Result Delivery
// =============================================================================

/**
 * Deliver job output to the configured target(s).
 * Python: _deliver_result(job, content, adapters=None, loop=None)
 *
 * On Android, delivery is simplified — platform adapters are handled
 * by the Android gateway layer.
 */
fun deliverResult(
    job: Map<String, Any?>,
    content: String
): String? {
    val targets = resolveDeliveryTargets(job)
    if (targets.isEmpty()) {
        val deliver = job["deliver"] as? String ?: "local"
        if (deliver != "local") {
            val msg = "no delivery target resolved for deliver=$deliver"
            logger.warning("Job '${job["id"]}': $msg")
            return msg
        }
        return null // local-only jobs don't deliver — not a failure
    }

    // Optionally wrap the content with a header/footer
    val taskName = job["name"] as? String ?: (job["id"] as? String ?: "")
    val jobId = job["id"] as? String ?: ""
    val deliveryContent = buildString {
        appendLine("Cronjob Response: $taskName")
        appendLine("(job_id: $jobId)")
        appendLine("-------------")
        appendLine()
        appendLine(content)
        appendLine()
        appendLine("To stop or manage this job, send me a new message (e.g. \"stop reminder $taskName\").")
    }

    val deliveryErrors = mutableListOf<String>()

    for (target in targets) {
        try {
            // TODO: Route through Android platform adapters
            // For now, log the delivery attempt
            logger.info(
                "Job '$jobId': would deliver to ${target.platform}:${target.chatId}" +
                (if (target.threadId != null) " thread=${target.threadId}" else "")
            )
        } catch (e: Exception) {
            val msg = "delivery to ${target.platform}:${target.chatId} failed: ${e.message}"
            logger.error("Job '$jobId': $msg")
            deliveryErrors.add(msg)
        }
    }

    return if (deliveryErrors.isNotEmpty()) {
        deliveryErrors.joinToString("; ")
    } else {
        null
    }
}

// =============================================================================
// Tick — Main Scheduler Entry Point
// =============================================================================

/**
 * Check and run all due jobs.
 *
 * Uses a file lock so only one tick runs at a time, even if the gateway's
 * in-process ticker and a standalone daemon or manual tick overlap.
 *
 * Python: tick(verbose=True, adapters=None, loop=None)
 *
 * @return Number of jobs executed (0 if another tick is already running)
 */
fun tick(verbose: Boolean = true): Int {
    _LOCK_DIR.mkdirs()

    var lockFile: RandomAccessFile? = null
    var fileLock: FileLock? = null

    try {
        lockFile = RandomAccessFile(_LOCK_FILE, "rw")
        fileLock = lockFile.channel.tryLock()
        if (fileLock == null) {
            logger.debug("Tick skipped — another instance holds the lock")
            lockFile.close()
            return 0
        }
    } catch (e: Exception) {
        logger.debug("Tick skipped — lock acquisition failed: ${e.message}")
        lockFile?.close()
        return 0
    }

    try {
        val dueJobs = getDueJobs()

        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        val timeStr = sdf.format(hermesNow())

        if (verbose && dueJobs.isEmpty()) {
            logger.info("$timeStr - No jobs due")
            return 0
        }

        if (verbose) {
            logger.info("$timeStr - ${dueJobs.size} job(s) due")
        }

        var executed = 0
        for (job in dueJobs) {
            try {
                // For recurring jobs, advance next_run_at before execution
                val jobId = job["id"] as? String ?: continue
                advanceNextRun(jobId)

                val result = runJob(job)

                val outputFile = saveJobOutput(jobId, result.fullOutputDoc)
                if (verbose) {
                    logger.info("Output saved to: $outputFile")
                }

                // Deliver the final response
                val deliverContent = if (result.success) {
                    result.finalResponse
                } else {
                    "⚠️ Cron job '${job["name"] ?: jobId}' failed:\n${result.errorMessage}"
                }

                var shouldDeliver = deliverContent.isNotEmpty()
                if (shouldDeliver && result.success &&
                    SILENT_MARKER in deliverContent.trim().uppercase()
                ) {
                    logger.info("Job '$jobId': agent returned $SILENT_MARKER — skipping delivery")
                    shouldDeliver = false
                }

                var deliveryError: String? = null
                if (shouldDeliver) {
                    try {
                        deliveryError = deliverResult(job, deliverContent)
                    } catch (de: Exception) {
                        deliveryError = de.message
                        logger.error("Delivery failed for job $jobId: ${de.message}")
                    }
                }

                // Treat empty final_response as a soft failure
                var success = result.success
                var error = result.errorMessage
                if (success && result.finalResponse.isEmpty()) {
                    success = false
                    error = "Agent completed but produced empty response " +
                            "(model error, timeout, or misconfiguration)"
                }

                markJobRun(jobId, success, error, deliveryError = deliveryError)
                executed++

            } catch (e: Exception) {
                val jobId = job["id"] as? String ?: "?"
                logger.error("Error processing job $jobId: ${e.message}")
                markJobRun(jobId, false, e.message)
            }
        }

        return executed
    } finally {
        try {
            fileLock?.release()
        } catch (_: Exception) {
        }
        try {
            lockFile?.close()
        } catch (_: Exception) {
        }
    }
}

/** Python `_send_media_via_adapter` — stub. */
private suspend fun _sendMediaViaAdapter(platform: String, target: String, path: String): Boolean = false

/** Python `_parse_wake_gate` — stub. */
private fun _parseWakeGate(expr: String): Any? = null

/** Python `_SCRIPT_TIMEOUT` — default cron script timeout (seconds). */
private val _SCRIPT_TIMEOUT: Int = 120
