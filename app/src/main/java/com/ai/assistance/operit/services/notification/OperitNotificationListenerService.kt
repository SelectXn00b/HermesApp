package com.ai.assistance.operit.services.notification

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ai.assistance.operit.core.tools.NotificationData

object OperitNotificationStore {
    private data class Entry(
        val key: String,
        val packageName: String,
        val text: String,
        val timestamp: Long,
        val isOngoing: Boolean
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    fun upsert(sbn: StatusBarNotification) {
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sbn.key
        } else {
            "${sbn.packageName}:${sbn.id}:${sbn.tag ?: ""}"
        }

        val notification = sbn.notification
        val text = extractText(notification)
        val isOngoing = sbn.isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

        val entry = Entry(
            key = key,
            packageName = sbn.packageName ?: "",
            text = text,
            timestamp = sbn.postTime,
            isOngoing = isOngoing
        )

        synchronized(lock) {
            entries[key] = entry
            if (entries.size > 200) {
                val toRemove = entries.entries.iterator().next().key
                entries.remove(toRemove)
            }
        }
    }

    fun remove(sbn: StatusBarNotification) {
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sbn.key
        } else {
            "${sbn.packageName}:${sbn.id}:${sbn.tag ?: ""}"
        }

        synchronized(lock) {
            entries.remove(key)
        }
    }

    fun snapshot(limit: Int, includeOngoing: Boolean): List<NotificationData.Notification> {
        val safeLimit = limit.coerceAtLeast(0)

        synchronized(lock) {
            return entries.values
                .asSequence()
                .filter { includeOngoing || !it.isOngoing }
                .sortedByDescending { it.timestamp }
                .take(safeLimit)
                .map {
                    NotificationData.Notification(
                        packageName = it.packageName,
                        text = it.text,
                        timestamp = it.timestamp
                    )
                }
                .toList()
        }
    }

    private fun extractText(notification: Notification): String {
        val parts = ArrayList<String>(4)
        val extras = notification.extras

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isNotBlank()) parts.add(title)

        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) parts.add(text)

        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (bigText.isNotBlank()) parts.add(bigText)

        val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        lines?.forEach { cs ->
            val line = cs?.toString()?.trim().orEmpty()
            if (line.isNotBlank()) parts.add(line)
        }

        val merged = parts.distinct().joinToString("\n").trim()
        if (merged.isNotBlank()) return merged

        val ticker = notification.tickerText?.toString()?.trim().orEmpty()
        return ticker
    }
}

class OperitNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activeNotifications?.forEach { OperitNotificationStore.upsert(it) }
            } catch (_: Exception) {
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        OperitNotificationStore.upsert(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        OperitNotificationStore.remove(sbn)
    }
}
