package com.xiaomo.androidforclaw.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: manage local memory lifecycle and summarization.
 */


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Memory Manager
 * Aligned with OpenClaw memory system
 *
 * Features:
 * - Long-term memory (MEMORY.md) read/write
 * - Daily log (memory/YYYY-MM-DD.md) append
 * - Memory file path management
 */
class MemoryManager(private val workspacePath: String) {
    companion object {
        private const val TAG = "MemoryManager"
        private const val MEMORY_FILE = "MEMORY.md"
        private const val MEMORY_DIR = "memory"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val workspaceDir = File(workspacePath)
    private val memoryFile = File(workspaceDir, MEMORY_FILE)
    private val memoryDir = File(workspaceDir, MEMORY_DIR)

    init {
        // Ensure directories exist
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        if (!memoryDir.exists()) {
            memoryDir.mkdirs()
        }
    }

    /**
     * Read long-term memory (MEMORY.md)
     *
     * @return Memory content, or empty string if file doesn't exist
     */
    suspend fun readMemory(): String = withContext(Dispatchers.IO) {
        try {
            if (memoryFile.exists()) {
                memoryFile.readText()
            } else {
                Log.d(TAG, "MEMORY.md does not exist, creating template")
                createMemoryTemplate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MEMORY.md", e)
            ""
        }
    }

    /**
     * Write long-term memory (MEMORY.md)
     *
     * @param content Content to write
     */
    suspend fun writeMemory(content: String) = withContext(Dispatchers.IO) {
        try {
            memoryFile.writeText(content)
            Log.d(TAG, "MEMORY.md written successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write MEMORY.md", e)
        }
    }

    /**
     * Append content to long-term memory
     *
     * @param section Section name (e.g., "## User Preferences")
     * @param content Content to append
     */
    suspend fun appendToMemory(section: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val currentContent = readMemory()
            val newContent = if (currentContent.contains(section)) {
                // 在指定章节后追加
                currentContent.replace(section, "$section\n$content")
            } else {
                // 章节不存在，追加到末尾
                "$currentContent\n\n$section\n$content"
            }
            writeMemory(newContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append to MEMORY.md", e)
        }
    }

    /**
     * Read today's log
     *
     * @return Today's log content
     */
    suspend fun getTodayLog(): String = withContext(Dispatchers.IO) {
        val today = DATE_FORMAT.format(Date())
        val logFile = File(memoryDir, "$today.md")
        try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                Log.d(TAG, "Today's log does not exist: $today.md")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read today's log", e)
            ""
        }
    }

    /**
     * Read yesterday's log
     *
     * @return Yesterday's log content
     */
    suspend fun getYesterdayLog(): String = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = DATE_FORMAT.format(calendar.time)
        val logFile = File(memoryDir, "$yesterday.md")
        try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                Log.d(TAG, "Yesterday's log does not exist: $yesterday.md")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read yesterday's log", e)
            ""
        }
    }

    /**
     * Append content to today's log
     *
     * @param content Content to append
     */
    suspend fun appendToToday(content: String) = withContext(Dispatchers.IO) {
        val today = DATE_FORMAT.format(Date())
        val logFile = File(memoryDir, "$today.md")
        try {
            if (!logFile.exists()) {
                // Create new log file
                val header = "# Daily Log - $today\n\n"
                logFile.writeText(header)
                Log.d(TAG, "Created new daily log: $today.md")
            }

            // Append content (with timestamp)
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val entry = "\n## [$timestamp]\n$content\n"
            logFile.appendText(entry)
            Log.d(TAG, "Appended to today's log: $today.md")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append to today's log", e)
        }
    }

    /**
     * Read log by specified date
     *
     * @param date Date string (yyyy-MM-dd)
     * @return Log content
     */
    suspend fun getLogByDate(date: String): String = withContext(Dispatchers.IO) {
        val logFile = File(memoryDir, "$date.md")
        try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                Log.d(TAG, "Log does not exist: $date.md")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log: $date.md", e)
            ""
        }
    }

    /**
     * List all log files
     *
     * @return Log file list (sorted by date descending)
     */
    suspend fun listLogs(): List<String> = withContext(Dispatchers.IO) {
        try {
            memoryDir.listFiles { file ->
                file.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md"))
            }?.map { it.nameWithoutExtension }?.sortedDescending() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list logs", e)
            emptyList()
        }
    }

    /**
     * List all memory files (non-date files)
     *
     * @return Memory file path list
     */
    suspend fun listMemoryFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val files = mutableListOf<String>()

            // Add MEMORY.md from root directory
            if (memoryFile.exists()) {
                files.add(memoryFile.absolutePath)
            }

            // Add non-date files from memory/ directory
            memoryDir.listFiles { file ->
                file.isFile &&
                file.name.endsWith(".md") &&
                !file.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md"))
            }?.forEach { file ->
                files.add(file.absolutePath)
            }

            files
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list memory files", e)
            emptyList()
        }
    }

    /**
     * Create MEMORY.md template
     */
    private fun createMemoryTemplate(): String {
        val template = """
# Long-term Memory

This file stores long-term, curated memories that persist across sessions.

## User Preferences

<!-- User preferences, communication style, language preferences -->

## Application Knowledge

<!-- Common app package names, successful operation patterns -->

## Known Issues and Solutions

<!-- Problems encountered and their solutions -->

## Stable Coordinates

<!-- UI element coordinates if they are fixed -->

## Important Context

<!-- Other important context that should be remembered -->

---

Last updated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
        """.trimIndent()

        try {
            memoryFile.writeText(template)
            Log.d(TAG, "Created MEMORY.md template")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MEMORY.md template", e)
        }

        return template
    }

    /**
     * Clear logs older than specified days
     *
     * @param days Days to keep
     */
    suspend fun pruneOldLogs(days: Int) = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -days)
            }.time

            memoryDir.listFiles { file ->
                file.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md"))
            }?.forEach { file ->
                try {
                    val fileDate = DATE_FORMAT.parse(file.nameWithoutExtension)
                    if (fileDate != null && fileDate.before(cutoffDate)) {
                        file.delete()
                        Log.d(TAG, "Pruned old log: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse date for: ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune old logs", e)
        }
    }
}
