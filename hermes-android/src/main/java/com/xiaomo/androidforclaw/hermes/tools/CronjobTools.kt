package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import java.util.UUID

/**
 * Cronjob tools for scheduling and managing timed tasks.
 * Ported from cronjob_tools.py
 */
object CronjobTools {

    private const val TAG = "CronjobTools"

    data class Cronjob(
        val id: String,
        val name: String,
        val schedule: String,  // Cron expression
        val handler: String,   // Handler name or action
        val enabled: Boolean = true)

    private val _cronjobs = mutableMapOf<String, Cronjob>()

    fun create(name: String, schedule: String, handler: String): Cronjob {
        val id = UUID.randomUUID().toString()
        val job = Cronjob(id, name, schedule, handler)
        _cronjobs[id] = job
        return job
    }

    fun get(id: String): Cronjob? = _cronjobs[id]

    fun getAll(): List<Cronjob> = _cronjobs.values.toList()

    fun delete(id: String): Boolean = _cronjobs.remove(id) != null

    fun setEnabled(id: String, enabled: Boolean): Cronjob? {
        val job = _cronjobs[id] ?: return null
        val updated = job.copy(enabled = enabled)
        _cronjobs[id] = updated
        return updated
    }

    /**
     * Parse a cron expression to determine next run time.
     * Simplified implementation — returns basic info.
     */
    fun parseSchedule(cronExpr: String): Map<String, String> {
        val parts = cronExpr.trim().split("\\s+".toRegex())
        if (parts.size != 5) return mapOf("error" to "Invalid cron expression: expected 5 fields")
        return mapOf(
            "minute" to parts[0],
            "hour" to parts[1],
            "day_of_month" to parts[2],
            "month" to parts[3],
            "day_of_week" to parts[4])
    }


}
