/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/cron/(all)
 *
 * AndroidForClaw adaptation: cron scheduling.
 */
package com.xiaomo.androidforclaw.cron

import android.util.Log
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

object CronScheduleParser {
    private const val TAG = "CronScheduleParser"

    fun computeNextRunAtMs(schedule: CronSchedule, nowMs: Long): Long? {
        return when (schedule) {
            is CronSchedule.At -> {
                val atMs = parseAbsoluteTimeMs(schedule.at)
                if (atMs > nowMs) atMs else null
            }
            is CronSchedule.Every -> {
                val everyMs = maxOf(1, schedule.everyMs)
                val anchor = maxOf(0, schedule.anchorMs ?: nowMs)
                if (nowMs < anchor) anchor
                else {
                    val elapsed = nowMs - anchor
                    val steps = maxOf(1, (elapsed + everyMs - 1) / everyMs)
                    anchor + steps * everyMs
                }
            }
            is CronSchedule.Cron -> computeSimpleCronNextRun(schedule.expr, nowMs)
        }
    }

    private fun parseAbsoluteTimeMs(at: String): Long {
        return try {
            if (at.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(at)?.time ?: 0
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(at)?.time ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse time: $at", e)
            0
        }
    }

    private fun computeSimpleCronNextRun(expr: String, nowMs: Long): Long? {
        try {
            val parts = expr.trim().split(Regex("\\s+"))
            if (parts.size != 5) return null

            val (minute, hour, _, _, _) = parts
            
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            repeat(24 * 60) {
                calendar.add(Calendar.MINUTE, 1)
                val matchesMinute = minute == "*" || calendar.get(Calendar.MINUTE) == minute.toIntOrNull()
                val matchesHour = hour == "*" || calendar.get(Calendar.HOUR_OF_DAY) == hour.toIntOrNull()
                
                if (matchesMinute && matchesHour) {
                    val nextMs = calendar.timeInMillis
                    return if (nextMs > nowMs) nextMs else null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute cron: $expr", e)
            return null
        }
    }

    fun errorBackoffMs(consecutiveErrors: Int, backoffSchedule: List<Long>): Long {
        val idx = minOf(consecutiveErrors - 1, backoffSchedule.size - 1)
        return backoffSchedule[maxOf(0, idx)]
    }
}
