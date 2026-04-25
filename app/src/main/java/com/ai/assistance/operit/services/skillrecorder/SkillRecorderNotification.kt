package com.ai.assistance.operit.services.skillrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.MainActivity

/**
 * Skill Recorder 通知构建器
 */
object SkillRecorderNotification {

    const val CHANNEL_ID = "skill_recorder_channel"
    const val NOTIFICATION_ID = 1338

    const val ACTION_PAUSE = "com.ai.assistance.operit.SKILL_RECORDER_PAUSE"
    const val ACTION_RESUME = "com.ai.assistance.operit.SKILL_RECORDER_RESUME"
    const val ACTION_STOP = "com.ai.assistance.operit.SKILL_RECORDER_STOP"
    const val ACTION_DISCARD = "com.ai.assistance.operit.SKILL_RECORDER_DISCARD"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.skill_recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.skill_recorder_channel_desc)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(
        context: Context,
        frameCount: Int,
        elapsedSeconds: Long,
        isPaused: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                if (isPaused) context.getString(R.string.skill_recorder_paused)
                else context.getString(R.string.skill_recorder_recording)
            )
            .setContentText(
                context.getString(
                    R.string.skill_recorder_status,
                    frameCount,
                    formatDuration(elapsedSeconds)
                )
            )
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setSilent(true)

        // Pause / Resume
        if (isPaused) {
            builder.addAction(
                0,
                context.getString(R.string.skill_recorder_resume),
                serviceIntent(context, ACTION_RESUME)
            )
        } else {
            builder.addAction(
                0,
                context.getString(R.string.skill_recorder_pause),
                serviceIntent(context, ACTION_PAUSE)
            )
        }

        // Stop
        builder.addAction(
            0,
            context.getString(R.string.skill_recorder_stop),
            serviceIntent(context, ACTION_STOP)
        )

        // Discard
        builder.addAction(
            0,
            context.getString(R.string.skill_recorder_discard),
            serviceIntent(context, ACTION_DISCARD)
        )

        return builder.build()
    }

    fun buildSummarizingNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.skill_recorder_summarizing))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, SkillRecorderService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
