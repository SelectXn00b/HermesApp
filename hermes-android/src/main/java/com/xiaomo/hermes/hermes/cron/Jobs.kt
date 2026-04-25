/** 1:1 对齐 hermes/cron/jobs.py */
package com.xiaomo.hermes.hermes.cron

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.xiaomo.hermes.hermes.getHermesHome
import com.xiaomo.hermes.hermes.getLogger
import com.xiaomo.hermes.hermes.hermesNow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Cron job storage and management.
 *
 * Jobs are stored in <HERMES_HOME>/cron/jobs.json
 * Output is saved to <HERMES_HOME>/cron/output/{job_id}/{timestamp}.md
 */

private val logger = getLogger("cron.jobs")

private val gson: Gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .serializeNulls()
    .create()

// =============================================================================
// Configuration
// =============================================================================

private val HERMES_DIR: File get() = getHermesHome()
private val CRON_DIR: File get() = File(HERMES_DIR, "cron")
private val JOBS_FILE: File get() = File(CRON_DIR, "jobs.json")
private val OUTPUT_DIR: File get() = File(CRON_DIR, "output")
private const val ONESHOT_GRACE_SECONDS = 120L

// Whether croniter-equivalent is available (Android: we use simple cron parsing)
private const val HAS_CRONITER = false

// =============================================================================
// Skill normalization helpers
// =============================================================================

/**
 * Normalize legacy/single-skill and multi-skill inputs into a unique ordered list.
 * Python: _normalize_skill_list(skill, skills)
 */
fun normalizeSkillList(skill: String? = null, skills: List<String>? = null): List<String> {
    val rawItems = when {
        skills != null -> if (skills is List<*>) skills.map { it.toString() } else listOf(skills.toString())
        skill != null -> listOf(skill)
        else -> emptyList()
    }

    val normalized = mutableListOf<String>()
    for (item in rawItems) {
        val text = item.trim()
        if (text.isNotEmpty() && text !in normalized) {
            normalized.add(text)
        }
    }
    return normalized
}

/**
 * Return a job dict with canonical `skills` and legacy `skill` fields aligned.
 * Python: _apply_skill_fields(job)
 */
fun applySkillFields(job: MutableMap<String, Any?>): MutableMap<String, Any?> {
    val normalized = HashMap(job)
    val skillVal = normalized["skill"] as? String
    @Suppress("UNCHECKED_CAST")
    val skillsVal = normalized["skills"] as? List<String>
    val skills = normalizeSkillList(skillVal, skillsVal)
    normalized["skills"] = skills
    normalized["skill"] = if (skills.isNotEmpty()) skills[0] else null
    return normalized
}

/**
 * Set directory permissions (no-op on Android, kept for API parity).
 * Python: _secure_dir(path)
 */
private fun _secureDir(@Suppress("UNUSED_PARAMETER") path: File) {
    // Android handles file permissions through its sandboxed storage
}

/**
 * Set file permissions (no-op on Android).
 * Python: _secure_file(path)
 */
private fun _secureFile(@Suppress("UNUSED_PARAMETER") path: File) {
    // Android handles file permissions through its sandboxed storage
}

/**
 * Ensure cron directories exist with secure permissions.
 * Python: ensure_dirs()
 */
fun ensureDirs() {
    CRON_DIR.mkdirs()
    OUTPUT_DIR.mkdirs()
    _secureDir(CRON_DIR)
    _secureDir(OUTPUT_DIR)
}

// =============================================================================
// Schedule Parsing
// =============================================================================

/**
 * Parse duration string into minutes.
 *
 * Examples:
 *   "30m" → 30
 *   "2h" → 120
 *   "1d" → 1440
 *
 * Python: parse_duration(s)
 */
fun parseDuration(s: String): Int {
    val trimmed = s.trim().lowercase()
    val regex = Regex("""^(\d+)\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)$""")
    val match = regex.matchEntire(trimmed)
        ?: throw IllegalArgumentException("Invalid duration: '$s'. Use format like '30m', '2h', or '1d'")

    val value = match.groupValues[1].toInt()
    val unit = match.groupValues[2][0] // First char: m, h, or d

    val multipliers = mapOf('m' to 1, 'h' to 60, 'd' to 1440)
    return value * (multipliers[unit] ?: 1)
}

/**
 * Parse schedule string into structured format.
 *
 * Returns map with:
 *   - kind: "once" | "interval" | "cron"
 *   - For "once": "run_at" (ISO timestamp)
 *   - For "interval": "minutes" (int)
 *   - For "cron": "expr" (cron expression)
 *
 * Python: parse_schedule(schedule)
 */
fun parseSchedule(schedule: String): MutableMap<String, Any?> {
    val trimmed = schedule.trim()
    val original = trimmed
    val scheduleLower = trimmed.lowercase()

    // "every X" pattern → recurring interval
    if (scheduleLower.startsWith("every ")) {
        val durationStr = trimmed.substring(6).trim()
        val minutes = parseDuration(durationStr)
        return mutableMapOf(
            "kind" to "interval",
            "minutes" to minutes,
            "display" to "every ${minutes}m"
        )
    }

    // Check for cron expression (5 or 6 space-separated fields)
    val parts = trimmed.split(" ")
    if (parts.size >= 5 && parts.take(5).all { it.matches(Regex("""^[\d*\-,/]+$""")) }) {
        if (!HAS_CRONITER) {
            throw IllegalArgumentException(
                "Cron expressions are not supported on Android. Use interval or one-shot schedules."
            )
        }
        return mutableMapOf(
            "kind" to "cron",
            "expr" to trimmed,
            "display" to trimmed
        )
    }

    // ISO timestamp (contains T or looks like date)
    if ('T' in trimmed || trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}.*"""))) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val cleanSchedule = trimmed.replace("Z", "+00:00")
            val dt = sdf.parse(cleanSchedule)
                ?: throw IllegalArgumentException("Invalid timestamp '$trimmed'")
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            return mutableMapOf(
                "kind" to "once",
                "run_at" to isoFormat.format(dt),
                "display" to "once at ${displayFormat.format(dt)}"
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid timestamp '$trimmed': ${e.message}")
        }
    }

    // Duration like "30m", "2h", "1d" → one-shot from now
    try {
        val minutes = parseDuration(trimmed)
        val cal = Calendar.getInstance()
        cal.time = hermesNow()
        cal.add(Calendar.MINUTE, minutes)
        val runAt = cal.time
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        return mutableMapOf(
            "kind" to "once",
            "run_at" to isoFormat.format(runAt),
            "display" to "once in $original"
        )
    } catch (_: IllegalArgumentException) {
        // not a duration, fall through
    }

    throw IllegalArgumentException(
        "Invalid schedule '$original'. Use:\n" +
        "  - Duration: '30m', '2h', '1d' (one-shot)\n" +
        "  - Interval: 'every 30m', 'every 2h' (recurring)\n" +
        "  - Timestamp: '2026-02-03T14:00:00' (one-shot at time)"
    )
}

/**
 * Return a timezone-aware Date in Hermes configured timezone.
 * Python: _ensure_aware(dt)
 */
private fun _ensureAware(dt: Date): Date {
    // On Android all Dates are inherently timezone-unaware (UTC millis).
    // This is kept for API parity.
    return dt
}

/**
 * Parse an ISO timestamp string to Date.
 */
private fun parseIsoDate(iso: String): Date {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            return sdf.parse(iso) ?: continue
        } catch (_: Exception) {
            // try next
        }
    }
    throw IllegalArgumentException("Cannot parse ISO date: $iso")
}

/**
 * Format a Date as ISO timestamp string.
 */
private fun formatIsoDate(dt: Date): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    return sdf.format(dt)
}

/**
 * Return a one-shot run time if it is still eligible to fire.
 * Python: _recoverable_oneshot_run_at(schedule, now, last_run_at=None)
 */
private fun _recoverableOneshotRunAt(
    schedule: Map<String, Any?>,
    now: Date,
    lastRunAt: String? = null
): String? {
    if (schedule["kind"] != "once") return null
    if (!lastRunAt.isNullOrEmpty()) return null

    val runAt = schedule["run_at"] as? String ?: return null
    val runAtDt = _ensureAware(parseIsoDate(runAt))

    val graceMs = ONESHOT_GRACE_SECONDS * 1000
    if (runAtDt.time >= now.time - graceMs) {
        return runAt
    }
    return null
}

/**
 * Compute how late a job can be and still catch up instead of fast-forwarding.
 * Python: _compute_grace_seconds(schedule)
 */
private fun _computeGraceSeconds(schedule: Map<String, Any?>): Long {
    val minGrace = 120L
    val maxGrace = 7200L // 2 hours

    val kind = schedule["kind"] as? String

    if (kind == "interval") {
        val minutes = (schedule["minutes"] as? Number)?.toInt() ?: 1
        val periodSeconds = minutes * 60L
        val grace = periodSeconds / 2
        return maxOf(minGrace, minOf(grace, maxGrace))
    }

    // cron not supported on Android; return min grace
    return minGrace
}

/**
 * Compute the next run time for a schedule.
 * Returns ISO timestamp string, or null if no more runs.
 *
 * Python: compute_next_run(schedule, last_run_at=None)
 */
fun computeNextRun(schedule: Map<String, Any?>, lastRunAt: String? = null): String? {
    val now = hermesNow()

    when (schedule["kind"]) {
        "once" -> {
            return _recoverableOneshotRunAt(schedule, now, lastRunAt = lastRunAt)
        }
        "interval" -> {
            val minutes = (schedule["minutes"] as? Number)?.toInt() ?: return null
            val nextRun: Date
            if (!lastRunAt.isNullOrEmpty()) {
                val last = _ensureAware(parseIsoDate(lastRunAt))
                val cal = Calendar.getInstance()
                cal.time = last
                cal.add(Calendar.MINUTE, minutes)
                nextRun = cal.time
            } else {
                val cal = Calendar.getInstance()
                cal.time = now
                cal.add(Calendar.MINUTE, minutes)
                nextRun = cal.time
            }
            return formatIsoDate(nextRun)
        }
        "cron" -> {
            // Cron not supported on Android
            logger.warning("Cron expressions not supported on Android")
            return null
        }
    }
    return null
}

// =============================================================================
// Job CRUD Operations
// =============================================================================

/**
 * Internal data wrapper for jobs.json.
 */
private data class JobsData(
    val jobs: List<MutableMap<String, Any?>> = emptyList(),
    val updated_at: String? = null
)

/**
 * Load all jobs from storage.
 * Python: load_jobs()
 */
fun loadJobs(): MutableList<MutableMap<String, Any?>> {
    ensureDirs()
    if (!JOBS_FILE.exists()) return mutableListOf()

    return try {
        val text = JOBS_FILE.readText(Charsets.UTF_8)
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val data: Map<String, Any?> = gson.fromJson(text, type)
        @Suppress("UNCHECKED_CAST")
        val jobsList = data["jobs"] as? List<Map<String, Any?>> ?: emptyList()
        jobsList.map { HashMap(it) as MutableMap<String, Any?> }.toMutableList()
    } catch (e: Exception) {
        logger.error("Failed to load jobs.json: ${e.message}")
        mutableListOf()
    }
}

/**
 * Save all jobs to storage.
 * Python: save_jobs(jobs)
 */
fun saveJobs(jobs: List<MutableMap<String, Any?>>) {
    ensureDirs()
    val data = mapOf(
        "jobs" to jobs,
        "updated_at" to formatIsoDate(hermesNow())
    )
    val tmpFile = File(JOBS_FILE.parent, ".jobs_${System.currentTimeMillis()}.tmp")
    try {
        tmpFile.writeText(gson.toJson(data), Charsets.UTF_8)
        tmpFile.renameTo(JOBS_FILE)
        _secureFile(JOBS_FILE)
    } catch (e: Exception) {
        tmpFile.delete()
        throw e
    }
}

/**
 * Create a new cron job.
 * Python: create_job(prompt, schedule, name=None, repeat=None, deliver=None, ...)
 */
fun createJob(
    prompt: String,
    schedule: String,
    name: String? = null,
    repeat: Int? = null,
    deliver: String? = null,
    origin: Map<String, Any?>? = null,
    skill: String? = null,
    skills: List<String>? = null,
    model: String? = null,
    provider: String? = null,
    baseUrl: String? = null,
    script: String? = null
): MutableMap<String, Any?> {
    val parsedSchedule = parseSchedule(schedule)

    // Normalize repeat: treat 0 or negative values as null (infinite)
    var effectiveRepeat = repeat
    if (effectiveRepeat != null && effectiveRepeat <= 0) {
        effectiveRepeat = null
    }

    // Auto-set repeat=1 for one-shot schedules if not specified
    if (parsedSchedule["kind"] == "once" && effectiveRepeat == null) {
        effectiveRepeat = 1
    }

    // Default delivery to origin if available, otherwise local
    val effectiveDeliver = deliver ?: if (origin != null) "origin" else "local"

    val jobId = UUID.randomUUID().toString().replace("-", "").take(12)
    val now = formatIsoDate(hermesNow())

    val normalizedSkills = normalizeSkillList(skill, skills)
    val normalizedModel = model?.trim()?.ifEmpty { null }
    val normalizedProvider = provider?.trim()?.ifEmpty { null }
    val normalizedBaseUrl = baseUrl?.trim()?.trimEnd('/')?.ifEmpty { null }
    val normalizedScript = script?.trim()?.ifEmpty { null }

    val labelSource = prompt.ifEmpty { normalizedSkills.firstOrNull() ?: "cron job" }

    val job: MutableMap<String, Any?> = mutableMapOf(
        "id" to jobId,
        "name" to (name ?: labelSource.take(50).trim()),
        "prompt" to prompt,
        "skills" to normalizedSkills,
        "skill" to normalizedSkills.firstOrNull(),
        "model" to normalizedModel,
        "provider" to normalizedProvider,
        "base_url" to normalizedBaseUrl,
        "script" to normalizedScript,
        "schedule" to parsedSchedule,
        "schedule_display" to (parsedSchedule["display"] as? String ?: schedule),
        "repeat" to mutableMapOf(
            "times" to effectiveRepeat,
            "completed" to 0
        ),
        "enabled" to true,
        "state" to "scheduled",
        "paused_at" to null,
        "paused_reason" to null,
        "created_at" to now,
        "next_run_at" to computeNextRun(parsedSchedule),
        "last_run_at" to null,
        "last_status" to null,
        "last_error" to null,
        "last_delivery_error" to null,
        "deliver" to effectiveDeliver,
        "origin" to origin
    )

    val jobs = loadJobs()
    jobs.add(job)
    saveJobs(jobs)

    return job
}

/**
 * Get a job by ID.
 * Python: get_job(job_id)
 */
fun getJob(jobId: String): MutableMap<String, Any?>? {
    val jobs = loadJobs()
    for (job in jobs) {
        if (job["id"] == jobId) {
            return applySkillFields(job)
        }
    }
    return null
}

/**
 * List all jobs, optionally including disabled ones.
 * Python: list_jobs(include_disabled=False)
 */
fun listJobs(includeDisabled: Boolean = false): List<MutableMap<String, Any?>> {
    var jobs = loadJobs().map { applySkillFields(it) }
    if (!includeDisabled) {
        jobs = jobs.filter { it["enabled"] as? Boolean != false }
    }
    return jobs
}

/**
 * Update a job by ID, refreshing derived schedule fields when needed.
 * Python: update_job(job_id, updates)
 */
fun updateJob(jobId: String, updates: Map<String, Any?>): MutableMap<String, Any?>? {
    val jobs = loadJobs()
    for (i in jobs.indices) {
        val job = jobs[i]
        if (job["id"] != jobId) continue

        val updated = applySkillFields(HashMap(job).apply { putAll(updates) })
        val scheduleChanged = "schedule" in updates

        if ("skills" in updates || "skill" in updates) {
            val skillVal = updated["skill"] as? String
            @Suppress("UNCHECKED_CAST")
            val skillsVal = updated["skills"] as? List<String>
            val normalizedSkills = normalizeSkillList(skillVal, skillsVal)
            updated["skills"] = normalizedSkills
            updated["skill"] = normalizedSkills.firstOrNull()
        }

        if (scheduleChanged) {
            var updatedSchedule = updated["schedule"]
            // The API may pass schedule as a raw string
            if (updatedSchedule is String) {
                updatedSchedule = parseSchedule(updatedSchedule)
                updated["schedule"] = updatedSchedule
            }
            @Suppress("UNCHECKED_CAST")
            val schedMap = updatedSchedule as? Map<String, Any?> ?: emptyMap()
            updated["schedule_display"] = updates["schedule_display"]
                ?: schedMap["display"]
                ?: updated["schedule_display"]
            if (updated["state"] != "paused") {
                updated["next_run_at"] = computeNextRun(schedMap)
            }
        }

        if (updated["enabled"] as? Boolean != false
            && updated["state"] != "paused"
            && updated["next_run_at"] == null
        ) {
            @Suppress("UNCHECKED_CAST")
            val sched = updated["schedule"] as? Map<String, Any?> ?: emptyMap()
            updated["next_run_at"] = computeNextRun(sched)
        }

        jobs[i] = updated
        saveJobs(jobs)
        return applySkillFields(jobs[i])
    }
    return null
}

/**
 * Pause a job without deleting it.
 * Python: pause_job(job_id, reason=None)
 */
fun pauseJob(jobId: String, reason: String? = null): MutableMap<String, Any?>? {
    return updateJob(
        jobId,
        mapOf(
            "enabled" to false,
            "state" to "paused",
            "paused_at" to formatIsoDate(hermesNow()),
            "paused_reason" to reason
        )
    )
}

/**
 * Resume a paused job and compute the next future run from now.
 * Python: resume_job(job_id)
 */
fun resumeJob(jobId: String): MutableMap<String, Any?>? {
    val job = getJob(jobId) ?: return null
    @Suppress("UNCHECKED_CAST")
    val schedule = job["schedule"] as? Map<String, Any?> ?: return null
    val nextRunAt = computeNextRun(schedule)
    return updateJob(
        jobId,
        mapOf(
            "enabled" to true,
            "state" to "scheduled",
            "paused_at" to null,
            "paused_reason" to null,
            "next_run_at" to nextRunAt
        )
    )
}

/**
 * Schedule a job to run on the next scheduler tick.
 * Python: trigger_job(job_id)
 */
fun triggerJob(jobId: String): MutableMap<String, Any?>? {
    val job = getJob(jobId) ?: return null
    return updateJob(
        jobId,
        mapOf(
            "enabled" to true,
            "state" to "scheduled",
            "paused_at" to null,
            "paused_reason" to null,
            "next_run_at" to formatIsoDate(hermesNow())
        )
    )
}

/**
 * Remove a job by ID.
 * Python: remove_job(job_id)
 */
fun removeJob(jobId: String): Boolean {
    val jobs = loadJobs()
    val originalLen = jobs.size
    val filtered = jobs.filter { it["id"] != jobId }.toMutableList()
    if (filtered.size < originalLen) {
        saveJobs(filtered)
        return true
    }
    return false
}

/**
 * Mark a job as having been run.
 * Python: mark_job_run(job_id, success, error=None, delivery_error=None)
 */
fun markJobRun(
    jobId: String,
    success: Boolean,
    error: String? = null,
    deliveryError: String? = null
) {
    val jobs = loadJobs()
    for (i in jobs.indices) {
        val job = jobs[i]
        if (job["id"] == jobId) {
            val now = formatIsoDate(hermesNow())
            job["last_run_at"] = now
            job["last_status"] = if (success) "ok" else "error"
            job["last_error"] = if (!success) error else null
            job["last_delivery_error"] = deliveryError

            // Increment completed count
            @Suppress("UNCHECKED_CAST")
            val repeatMap = job["repeat"] as? MutableMap<String, Any?>
            if (repeatMap != null) {
                val completed = ((repeatMap["completed"] as? Number)?.toInt() ?: 0) + 1
                repeatMap["completed"] = completed

                val times = (repeatMap["times"] as? Number)?.toInt()
                if (times != null && times > 0 && completed >= times) {
                    // Remove the job (limit reached)
                    jobs.removeAt(i)
                    saveJobs(jobs)
                    return
                }
            }

            // Compute next run
            @Suppress("UNCHECKED_CAST")
            val schedule = job["schedule"] as? Map<String, Any?> ?: emptyMap()
            job["next_run_at"] = computeNextRun(schedule, now)

            // If no next run (one-shot completed), disable
            if (job["next_run_at"] == null) {
                job["enabled"] = false
                job["state"] = "completed"
            } else if (job["state"] != "paused") {
                job["state"] = "scheduled"
            }

            saveJobs(jobs)
            return
        }
    }
    logger.warning("mark_job_run: job_id $jobId not found, skipping save")
}

/**
 * Preemptively advance next_run_at for a recurring job before execution.
 * Python: advance_next_run(job_id)
 */
fun advanceNextRun(jobId: String): Boolean {
    val jobs = loadJobs()
    for (job in jobs) {
        if (job["id"] == jobId) {
            @Suppress("UNCHECKED_CAST")
            val schedule = job["schedule"] as? Map<String, Any?> ?: return false
            val kind = schedule["kind"] as? String
            if (kind != "cron" && kind != "interval") return false

            val now = formatIsoDate(hermesNow())
            val newNext = computeNextRun(schedule, now)
            if (newNext != null && newNext != job["next_run_at"]) {
                job["next_run_at"] = newNext
                saveJobs(jobs)
                return true
            }
            return false
        }
    }
    return false
}

/**
 * Get all jobs that are due to run now.
 * Python: get_due_jobs()
 */
fun getDueJobs(): List<MutableMap<String, Any?>> {
    val now = hermesNow()
    val rawJobs = loadJobs()
    val jobs = rawJobs.map { applySkillFields(HashMap(it)) }
    val due = mutableListOf<MutableMap<String, Any?>>()
    var needsSave = false

    for (job in jobs) {
        if (job["enabled"] as? Boolean == false) continue

        var nextRun = job["next_run_at"] as? String
        if (nextRun == null) {
            @Suppress("UNCHECKED_CAST")
            val schedule = job["schedule"] as? Map<String, Any?> ?: continue
            val recoveredNext = _recoverableOneshotRunAt(
                schedule, now,
                lastRunAt = job["last_run_at"] as? String
            )
            if (recoveredNext == null) continue

            job["next_run_at"] = recoveredNext
            nextRun = recoveredNext
            logger.info("Job '${job["name"] ?: job["id"]}' had no next_run_at; recovering one-shot run at $recoveredNext")

            for (rj in rawJobs) {
                if (rj["id"] == job["id"]) {
                    rj["next_run_at"] = recoveredNext
                    needsSave = true
                    break
                }
            }
        }

        val nextRunDt = _ensureAware(parseIsoDate(nextRun))
        if (nextRunDt.time <= now.time) {
            @Suppress("UNCHECKED_CAST")
            val schedule = job["schedule"] as? Map<String, Any?> ?: continue
            val kind = schedule["kind"] as? String

            // For recurring jobs, check if the scheduled time is stale
            val grace = _computeGraceSeconds(schedule)
            if (kind in listOf("cron", "interval") &&
                (now.time - nextRunDt.time) > grace * 1000
            ) {
                val newNext = computeNextRun(schedule, formatIsoDate(now))
                if (newNext != null) {
                    logger.info(
                        "Job '${job["name"] ?: job["id"]}' missed its scheduled time " +
                        "($nextRun, grace=${grace}s). Fast-forwarding to next run: $newNext"
                    )
                    for (rj in rawJobs) {
                        if (rj["id"] == job["id"]) {
                            rj["next_run_at"] = newNext
                            needsSave = true
                            break
                        }
                    }
                    continue // Skip this run
                }
            }

            due.add(job)
        }
    }

    if (needsSave) {
        saveJobs(rawJobs)
    }

    return due
}

/**
 * Save job output to file.
 * Python: save_job_output(job_id, output)
 */
fun saveJobOutput(jobId: String, output: String): File {
    ensureDirs()
    val jobOutputDir = File(OUTPUT_DIR, jobId)
    jobOutputDir.mkdirs()
    _secureDir(jobOutputDir)

    val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    val timestamp = sdf.format(hermesNow())
    val outputFile = File(jobOutputDir, "$timestamp.md")

    val tmpFile = File(jobOutputDir, ".output_${System.currentTimeMillis()}.tmp")
    try {
        tmpFile.writeText(output, Charsets.UTF_8)
        tmpFile.renameTo(outputFile)
        _secureFile(outputFile)
    } catch (e: Exception) {
        tmpFile.delete()
        throw e
    }

    return outputFile
}
