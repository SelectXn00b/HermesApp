package com.xiaomo.androidforclaw.cron

import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CronRunLog(private val runsDir: String, private val config: CronRunLogConfig) {
    companion object {
        private const val TAG = "CronRunLog"
    }

    private val gson = Gson()
    private val lock = ReentrantLock()

    fun append(entry: CronRunLogEntry) {
        lock.withLock {
            try {
                val dir = File(runsDir)
                dir.mkdirs()

                val logFile = File(dir, "${entry.jobId}.jsonl")
                val json = gson.toJson(entry)

                FileOutputStream(logFile, true).use {
                    it.write("$json\n".toByteArray())
                }

                pruneIfNeeded(logFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append log", e)
            }
        }
    }

    fun query(jobId: String, limit: Int = 100, status: RunStatus? = null): List<CronRunLogEntry> {
        return lock.withLock {
            try {
                val logFile = File(runsDir, "$jobId.jsonl")
                if (!logFile.exists()) return emptyList()

                val lines = logFile.readLines()
                val entries = lines.mapNotNull {
                    try {
                        gson.fromJson(it, CronRunLogEntry::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                val filtered = if (status != null) {
                    entries.filter { it.status == status }
                } else entries

                filtered.reversed().take(limit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query log", e)
                emptyList()
            }
        }
    }

    fun delete(jobId: String) {
        lock.withLock {
            try {
                File(runsDir, "$jobId.jsonl").delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete log", e)
            }
        }
    }

    private fun pruneIfNeeded(logFile: File) {
        try {
            if (logFile.length() <= config.maxBytes) return

            val lines = logFile.readLines()
            val toKeep = lines.takeLast(config.keepLines)

            FileOutputStream(logFile).use { out ->
                toKeep.forEach { out.write("$it\n".toByteArray()) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune log", e)
        }
    }
}
