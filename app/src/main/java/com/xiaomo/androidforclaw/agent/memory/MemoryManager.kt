package com.xiaomo.androidforclaw.agent.memory

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/manager.ts
 *
 * Memory Manager — aligned with OpenClaw memory system.
 *
 * Features:
 * - Long-term memory (MEMORY.md) read/write
 * - Daily log (memory/YYYY-MM-DD.md) append
 * - Memory file path management
 * - MemoryIndex integration (SQLite + FTS5 + vector search)
 */
class MemoryManager(
    private val workspacePath: String,
    private val context: Context? = null,
    embeddingBaseUrl: String = "",
    embeddingApiKey: String = "",
    embeddingModel: String = "text-embedding-3-small"
) {
    companion object {
        private const val TAG = "MemoryManager"
        private const val MEMORY_FILE = "MEMORY.md"
        private const val MEMORY_DIR = "memory"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val workspaceDir = File(workspacePath)
    private val memoryFile = File(workspaceDir, MEMORY_FILE)
    private val memoryDir = File(workspaceDir, MEMORY_DIR)

    private var embeddingProvider: EmbeddingProvider? = null
    private var memoryIndex: MemoryIndex? = null

    init {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
        if (!memoryDir.exists()) memoryDir.mkdirs()

        // Initialize embedding provider and memory index if context available
        if (context != null) {
            embeddingProvider = EmbeddingProvider(
                baseUrl = embeddingBaseUrl,
                apiKey = embeddingApiKey,
                model = embeddingModel
            )
            memoryIndex = MemoryIndex(context, embeddingProvider)
            Log.d(TAG, "MemoryIndex initialized (embedding available: ${embeddingProvider?.isAvailable})")
        }
    }

    /**
     * Initialize MemoryIndex with context (call after construction if context wasn't provided).
     */
    fun initIndex(ctx: Context, baseUrl: String = "", apiKey: String = "", model: String = "text-embedding-3-small") {
        embeddingProvider = EmbeddingProvider(baseUrl = baseUrl, apiKey = apiKey, model = model)
        memoryIndex = MemoryIndex(ctx, embeddingProvider)
        Log.d(TAG, "MemoryIndex initialized (embedding available: ${embeddingProvider?.isAvailable})")
    }

    fun getMemoryIndex(): MemoryIndex? = memoryIndex
    fun getEmbeddingProvider(): EmbeddingProvider? = embeddingProvider

    /**
     * Sync all memory files into the index.
     */
    suspend fun syncIndex() {
        val index = memoryIndex ?: return
        val files = getAllMemoryFiles()
        index.sync(files.map { File(it) })
    }

    /**
     * Get all memory-related files for indexing.
     */
    private fun getAllMemoryFiles(): List<String> {
        val files = mutableListOf<String>()
        if (memoryFile.exists()) files.add(memoryFile.absolutePath)

        // All .md files in memory/ directory
        memoryDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.forEach { files.add(it.absolutePath) }

        // Other workspace .md files (SOUL.md, USER.md, etc.)
        workspaceDir.listFiles { f -> f.isFile && f.name.endsWith(".md") && f.name != MEMORY_FILE }
            ?.forEach { files.add(it.absolutePath) }

        return files
    }

    /**
     * Index a single file (call on file change).
     */
    suspend fun indexFile(file: File, source: String = "memory") {
        memoryIndex?.indexFile(file, source)
    }

    // ---- Existing MemoryManager methods (unchanged) ----

    suspend fun readMemory(): String = withContext(Dispatchers.IO) {
        try {
            if (memoryFile.exists()) memoryFile.readText()
            else {
                Log.d(TAG, "MEMORY.md does not exist, creating template")
                createMemoryTemplate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MEMORY.md", e)
            ""
        }
    }

    suspend fun writeMemory(content: String) = withContext(Dispatchers.IO) {
        try {
            memoryFile.writeText(content, Charsets.UTF_8)
            Log.d(TAG, "MEMORY.md written successfully")
            // Re-index after write
            memoryIndex?.indexFile(memoryFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write MEMORY.md", e)
        }
    }

    suspend fun appendToMemory(section: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val currentContent = readMemory()
            val newContent = if (currentContent.contains(section)) {
                currentContent.replace(section, "$section\n$content")
            } else {
                "$currentContent\n\n$section\n$content"
            }
            writeMemory(newContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append to MEMORY.md", e)
        }
    }

    suspend fun getTodayLog(): String = withContext(Dispatchers.IO) {
        val today = DATE_FORMAT.format(Date())
        val logFile = File(memoryDir, "$today.md")
        try { if (logFile.exists()) logFile.readText() else "" }
        catch (e: Exception) { Log.e(TAG, "Failed to read today's log", e); "" }
    }

    suspend fun getYesterdayLog(): String = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val yesterday = DATE_FORMAT.format(calendar.time)
        val logFile = File(memoryDir, "$yesterday.md")
        try { if (logFile.exists()) logFile.readText() else "" }
        catch (e: Exception) { Log.e(TAG, "Failed to read yesterday's log", e); "" }
    }

    suspend fun appendToToday(content: String) = withContext(Dispatchers.IO) {
        val today = DATE_FORMAT.format(Date())
        val logFile = File(memoryDir, "$today.md")
        try {
            if (!logFile.exists()) {
                logFile.writeText("# Daily Log - $today\n\n", Charsets.UTF_8)
            }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            logFile.appendText("\n## [$timestamp]\n$content\n")
            // Re-index
            memoryIndex?.indexFile(logFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append to today's log", e)
        }
    }

    suspend fun getLogByDate(date: String): String = withContext(Dispatchers.IO) {
        val logFile = File(memoryDir, "$date.md")
        try { if (logFile.exists()) logFile.readText() else "" }
        catch (e: Exception) { Log.e(TAG, "Failed to read log: $date.md", e); "" }
    }

    suspend fun listLogs(): List<String> = withContext(Dispatchers.IO) {
        try {
            memoryDir.listFiles { f -> f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
                ?.map { it.nameWithoutExtension }?.sortedDescending() ?: emptyList()
        } catch (e: Exception) { Log.e(TAG, "Failed to list logs", e); emptyList() }
    }

    suspend fun listMemoryFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val files = mutableListOf<String>()
            if (memoryFile.exists()) files.add(memoryFile.absolutePath)
            memoryDir.listFiles { f ->
                f.isFile && f.name.endsWith(".md") && !f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md"))
            }?.forEach { files.add(it.absolutePath) }
            files
        } catch (e: Exception) { Log.e(TAG, "Failed to list memory files", e); emptyList() }
    }

    private fun createMemoryTemplate(): String {
        val template = """
# Long-term Memory

This file stores long-term, curated memories that persist across sessions.

## User Preferences

## Application Knowledge

## Known Issues and Solutions

## Important Context

---

Last updated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
        """.trimIndent()
        try { memoryFile.writeText(template, Charsets.UTF_8) } catch (e: Exception) { Log.e(TAG, "Failed to create template", e) }
        return template
    }

    suspend fun pruneOldLogs(days: Int) = withContext(Dispatchers.IO) {
        try {
            val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }.time
            memoryDir.listFiles { f -> f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
                ?.forEach { file ->
                    try {
                        val fileDate = DATE_FORMAT.parse(file.nameWithoutExtension)
                        if (fileDate != null && fileDate.before(cutoff)) {
                            file.delete()
                            Log.d(TAG, "Pruned old log: ${file.name}")
                        }
                    } catch (_: Exception) {}
                }
        } catch (e: Exception) { Log.e(TAG, "Failed to prune old logs", e) }
    }
}
