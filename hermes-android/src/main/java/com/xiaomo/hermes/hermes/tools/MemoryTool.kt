package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.UUID

/**
 * Memory Tool — provides persistent memory storage for the agent.
 * Ported from memory_tool.py
 *
 * Two stores:
 * - MEMORY.md: agent's personal notes and observations
 * - USER.md: what the agent knows about the user
 *
 * Entries are delimited by § (section sign).
 * Character limits enforced per store.
 */
object MemoryTool {

    private const val _TAG = "MemoryTool"
    private val gson = Gson()

    private const val ENTRY_DELIMITER = "\n§\n"
    private const val DEFAULT_MEMORY_CHAR_LIMIT = 2200
    private const val DEFAULT_USER_CHAR_LIMIT = 1375

    // Threat patterns for content scanning
    private val MEMORY_THREAT_PATTERNS = listOf(
        Regex("""ignore\s+(previous|all|above|prior)\s+instructions""", RegexOption.IGNORE_CASE) to "prompt_injection",
        Regex("""you\s+are\s+now\s+""", RegexOption.IGNORE_CASE) to "role_hijack",
        Regex("""do\s+not\s+tell\s+the\s+user""", RegexOption.IGNORE_CASE) to "deception_hide",
        Regex("""system\s+prompt\s+override""", RegexOption.IGNORE_CASE) to "sys_prompt_override",
        Regex("""disregard\s+(your|all|any)\s+(instructions|rules|guidelines)""", RegexOption.IGNORE_CASE) to "disregard_rules",
        Regex("""curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""", RegexOption.IGNORE_CASE) to "exfil_curl",
        Regex("""wget\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""", RegexOption.IGNORE_CASE) to "exfil_wget",
        Regex("""cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass|\.npmrc|\.pypirc)""", RegexOption.IGNORE_CASE) to "read_secrets")

    private val INVISIBLE_CHARS = setOf(
        '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
        '\u202a', '\u202b', '\u202c', '\u202d', '\u202e')

    data class MemoryEntry(
        val id: String,
        val content: String,
        val tags: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis())

    private val _memories = mutableListOf<MemoryEntry>()

    // --- In-memory entry lists (mirrors Python MemoryStore) ---
    private var memoryEntries: MutableList<String> = mutableListOf()
    private var userEntries: MutableList<String> = mutableListOf()
    private var memoryCharLimit: Int = DEFAULT_MEMORY_CHAR_LIMIT
    private var userCharLimit: Int = DEFAULT_USER_CHAR_LIMIT
    private var systemPromptSnapshot: Map<String, String> = mapOf("memory" to "", "user" to "")

    // Memory directory
    private var memoryDir: File? = null

    fun setMemoryDir(dir: File) {
        memoryDir = dir
    }

    // === Simple memory API (backward compat) ===

    fun store(content: String, tags: List<String> = emptyList()): MemoryEntry {
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            content = content,
            tags = tags)
        _memories.add(entry)
        return entry
    }

    fun search(query: String, limit: Int = 10): List<MemoryEntry> {
        return _memories
            .filter { entry ->
                entry.content.contains(query, ignoreCase = true) ||
                entry.tags.any { it.contains(query, ignoreCase = true) }
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    fun getAll(): List<MemoryEntry> = _memories.toList()

    fun getById(id: String): MemoryEntry? = _memories.find { it.id == id }

    fun delete(id: String): Boolean {
        val sizeBefore = _memories.size
        _memories.removeAll { it.id == id }
        return _memories.size < sizeBefore
    }

    fun clear() = _memories.clear()

    fun save(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(_memories), Charsets.UTF_8)
    }

    fun load(file: File) {
        if (!file.exists()) return
        try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, MemoryEntry::class.java
            ).type
            val loaded = gson.fromJson<List<MemoryEntry>>(file.readText(Charsets.UTF_8), type)
            _memories.clear()
            _memories.addAll(loaded)
        } catch (_: Exception) {}
    }

    // === Full MemoryStore API (ported from memory_tool.py) ===

    fun loadFromDisk(): Unit? {
        val dir = memoryDir ?: return null
        dir.mkdirs()

        memoryEntries = readFile(File(dir, "MEMORY.md")).toMutableList()
        userEntries = readFile(File(dir, "USER.md")).toMutableList()

        // Deduplicate
        memoryEntries = LinkedHashSet(memoryEntries).toMutableList()
        userEntries = LinkedHashSet(userEntries).toMutableList()

        // Capture frozen snapshot for system prompt
        systemPromptSnapshot = mapOf(
            "memory" to renderBlock("memory", memoryEntries),
            "user" to renderBlock("user", userEntries))
        return null
    }

    fun fileLock(path: File): AutoCloseable {
        // Uses a separate .lock file for read-modify-write safety
        val lockFile = File(path.absolutePath + ".lock")
        lockFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(lockFile, "rw")
        val channel: FileChannel = raf.channel
        val lock: FileLock = channel.lock()
        return AutoCloseable {
            lock.release()
            channel.close()
            raf.close()
        }
    }

    fun pathFor(target: String): File {
        val dir = memoryDir ?: File("/data/local/tmp/hermes/memories")
        return if (target == "user") File(dir, "USER.md") else File(dir, "MEMORY.md")
    }

    fun reloadTarget(target: String): Unit? {
        val fresh = readFile(pathFor(target)).toMutableList()
        val deduped = LinkedHashSet(fresh).toMutableList()
        setEntries(target, deduped)
        return null
    }

    fun saveToDisk(target: String): Unit? {
        val dir = memoryDir ?: return null
        dir.mkdirs()
        writeFile(pathFor(target), entriesFor(target))
        return null
    }

    fun entriesFor(target: String): List<String> {
        return if (target == "user") userEntries else memoryEntries
    }

    fun setEntries(target: String, entries: List<String>) {
        if (target == "user") userEntries = entries.toMutableList()
        else memoryEntries = entries.toMutableList()
    }

    fun charCount(target: String): Int {
        val entries = entriesFor(target)
        if (entries.isEmpty()) return 0
        return entries.joinToString(ENTRY_DELIMITER).length
    }

    fun charLimit(target: String): Int {
        return if (target == "user") userCharLimit else memoryCharLimit
    }

    fun add(target: String, content: String): Map<String, Any> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return mapOf("success" to false, "error" to "Content cannot be empty.")
        }

        // Scan for injection/exfiltration
        val scanError = scanMemoryContent(trimmed)
        if (scanError != null) {
            return mapOf("success" to false, "error" to scanError)
        }

        val lock = fileLock(pathFor(target))
        return try {
            reloadTarget(target)
            val entries = entriesFor(target).toMutableList()
            val limit = charLimit(target)

            // Reject exact duplicates
            if (trimmed in entries) {
                return successResponse(target, "Entry already exists (no duplicate added).")
            }

            // Check char limit
            val newEntries = entries + trimmed
            val newTotal = newEntries.joinToString(ENTRY_DELIMITER).length

            if (newTotal > limit) {
                val current = charCount(target)
                return mapOf(
                    "success" to false,
                    "error" to "Memory at $current/$limit chars. Adding this entry (${trimmed.length} chars) would exceed the limit. Replace or remove existing entries first.",
                    "current_entries" to entries,
                    "usage" to "$current/$limit")
            }

            entries.add(trimmed)
            setEntries(target, entries)
            saveToDisk(target)
            successResponse(target, "Entry added.")
        } finally {
            lock.close()
        }
    }

    fun replace(target: String, oldText: String, newContent: String): Map<String, Any> {
        val trimmedOld = oldText.trim()
        val trimmedNew = newContent.trim()
        if (trimmedOld.isEmpty()) return mapOf("success" to false, "error" to "old_text cannot be empty.")
        if (trimmedNew.isEmpty()) return mapOf("success" to false, "error" to "new_content cannot be empty. Use 'remove' to delete entries.")

        // Scan replacement content
        val scanError = scanMemoryContent(trimmedNew)
        if (scanError != null) return mapOf("success" to false, "error" to scanError)

        val lock = fileLock(pathFor(target))
        return try {
            reloadTarget(target)
            val entries = entriesFor(target).toMutableList()
            val matches = entries.mapIndexedNotNull { i, e -> if (trimmedOld in e) Pair(i, e) else null }

            if (matches.isEmpty()) {
                return mapOf("success" to false, "error" to "No entry matched '$trimmedOld'.")
            }

            if (matches.size > 1) {
                val uniqueTexts = matches.map { it.second }.toSet()
                if (uniqueTexts.size > 1) {
                    val previews = matches.map { (_, e) ->
                        if (e.length > 80) e.take(80) + "..." else e
                    }
                    return mapOf(
                        "success" to false,
                        "error" to "Multiple entries matched '$trimmedOld'. Be more specific.",
                        "matches" to previews)
                }
                // All identical — safe to replace just the first
            }

            val idx = matches[0].first
            val limit = charLimit(target)

            // Check replacement doesn't exceed limit
            val testEntries = entries.toMutableList()
            testEntries[idx] = trimmedNew
            val newTotal = testEntries.joinToString(ENTRY_DELIMITER).length

            if (newTotal > limit) {
                return mapOf(
                    "success" to false,
                    "error" to "Replacement would put memory at $newTotal/$limit chars. Shorten the new content or remove other entries first.")
            }

            entries[idx] = trimmedNew
            setEntries(target, entries)
            saveToDisk(target)
            successResponse(target, "Entry replaced.")
        } finally {
            lock.close()
        }
    }

    fun remove(target: String, oldText: String): Map<String, Any> {
        val trimmedOld = oldText.trim()
        if (trimmedOld.isEmpty()) return mapOf("success" to false, "error" to "old_text cannot be empty.")

        val lock = fileLock(pathFor(target))
        return try {
            reloadTarget(target)
            val entries = entriesFor(target).toMutableList()
            val matches = entries.mapIndexedNotNull { i, e -> if (trimmedOld in e) Pair(i, e) else null }

            if (matches.isEmpty()) {
                return mapOf("success" to false, "error" to "No entry matched '$trimmedOld'.")
            }

            if (matches.size > 1) {
                val uniqueTexts = matches.map { it.second }.toSet()
                if (uniqueTexts.size > 1) {
                    val previews = matches.map { (_, e) ->
                        if (e.length > 80) e.take(80) + "..." else e
                    }
                    return mapOf(
                        "success" to false,
                        "error" to "Multiple entries matched '$trimmedOld'. Be more specific.",
                        "matches" to previews)
                }
                // All identical — safe to remove just the first
            }

            val idx = matches[0].first
            entries.removeAt(idx)
            setEntries(target, entries)
            saveToDisk(target)
            successResponse(target, "Entry removed.")
        } finally {
            lock.close()
        }
    }

    fun formatForSystemPrompt(target: String): String? {
        val block = systemPromptSnapshot[target] ?: return null
        return block.ifEmpty { null }
    }

    fun successResponse(target: String, message: String?): Map<String, Any> {
        val entries = entriesFor(target)
        val current = charCount(target)
        val limit = charLimit(target)
        val pct = if (limit > 0) minOf(100, (current * 100) / limit) else 0

        val resp = mutableMapOf<String, Any>(
            "success" to true,
            "target" to target,
            "entries" to entries,
            "usage" to "$pct% — $current/$limit chars",
            "entry_count" to entries.size)
        if (message != null) resp["message"] = message
        return resp
    }

    fun renderBlock(target: String, entries: List<String>): String {
        if (entries.isEmpty()) return ""

        val limit = charLimit(target)
        val content = entries.joinToString(ENTRY_DELIMITER)
        val current = content.length
        val pct = if (limit > 0) minOf(100, (current * 100) / limit) else 0

        val header = if (target == "user") {
            "USER PROFILE (who the user is) [$pct% — $current/$limit chars]"
        } else {
            "MEMORY (your personal notes) [$pct% — $current/$limit chars]"
        }

        val separator = "═".repeat(46)
        return "$separator\n$header\n$separator\n$content"
    }

    fun readFile(path: File): List<String> {
        if (!path.exists()) return emptyList()
        val raw = try {
            path.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return emptyList()
        }
        if (raw.isBlank()) return emptyList()

        return raw.split(ENTRY_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun writeFile(path: File, entries: List<String>): Unit? {
        val content = if (entries.isNotEmpty()) entries.joinToString(ENTRY_DELIMITER) else ""
        val tmpFile = File(path.parent, ".mem_${UUID.randomUUID().toString().replace("-", "").take(8)}.tmp")
        try {
            tmpFile.parentFile?.mkdirs()
            tmpFile.writeText(content, Charsets.UTF_8)
            // Atomic rename on same filesystem
            if (path.exists()) path.delete()
            tmpFile.renameTo(path)
        } catch (e: Exception) {
            // Clean up temp file on failure
            try { tmpFile.delete() } catch (_: Exception) {}
            throw RuntimeException("Failed to write memory file $path: ${e.message}")
        }
        return null
    }

    // --- Content scanning ---

    private fun scanMemoryContent(content: String): String? {
        // Check invisible unicode
        for (char in INVISIBLE_CHARS) {
            if (char in content) {
                return "Blocked: content contains invisible unicode character U+${char.code.toString(16).padStart(4, '0')} (possible injection)."
            }
        }
        // Check threat patterns
        for ((pattern, pid) in MEMORY_THREAT_PATTERNS) {
            if (pattern.containsMatchIn(content)) {
                return "Blocked: content matches threat pattern '$pid'. Memory entries are injected into the system prompt and must not contain injection or exfiltration payloads."
            }
        }
        return null
    }
}

/**
 * Bounded curated memory with file persistence. One instance per AIAgent.
 * Ported from MemoryStore in memory_tool.py.
 *
 * Maintains two parallel states:
 *   - _systemPromptSnapshot: frozen at load time, used for system prompt injection.
 *     Never mutated mid-session. Keeps prefix cache stable.
 *   - memoryEntries / userEntries: live state, mutated by tool calls, persisted to disk.
 *     Tool responses always reflect this live state.
 */
class MemoryStore(
    val memoryCharLimit: Int = 2200,
    val userCharLimit: Int = 1375
) {
    var memoryEntries: MutableList<String> = mutableListOf()
    var userEntries: MutableList<String> = mutableListOf()
    private var _systemPromptSnapshot: Map<String, String> = mapOf("memory" to "", "user" to "")

    fun loadFromDisk() {
        MemoryTool.loadFromDisk()
    }

    fun formatForSystemPrompt(target: String): String? {
        return MemoryTool.formatForSystemPrompt(target)
    }

    fun add(target: String, content: String): Map<String, Any> {
        return MemoryTool.add(target, content)
    }

    fun replace(target: String, oldText: String, newContent: String): Map<String, Any> {
        return MemoryTool.replace(target, oldText, newContent)
    }

    fun remove(target: String, oldText: String): Map<String, Any> {
        return MemoryTool.remove(target, oldText)
    }
}
