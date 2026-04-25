package com.ai.assistance.operit.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object RoomDatabaseBackupScheduler {

    private const val UNIQUE_PERIODIC_WORK_NAME = "room_db_daily_backup"
    private const val UNIQUE_ONE_TIME_WORK_NAME = "room_db_manual_backup"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val initialDelayMs = calculateInitialDelayToHour(3)

        val request = PeriodicWorkRequestBuilder<RoomDatabaseBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancelScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    fun enqueueManualBackup(context: Context, force: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<RoomDatabaseBackupWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(RoomDatabaseBackupWorker.KEY_FORCE to force))
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    private fun calculateInitialDelayToHour(targetHour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }
}
