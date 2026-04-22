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

private const val _TAG_MEMORY_TOOL = "MemoryTool"
private val _memoryGson = Gson()

const val ENTRY_DELIMITER = "\n§\n"
const val DEFAULT_MEMORY_CHAR_LIMIT = 2200
const val DEFAULT_USER_CHAR_LIMIT = 1375

private val _MEMORY_THREAT_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("""ignore\s+(previous|all|above|prior)\s+instructions""", RegexOption.IGNORE_CASE) to "prompt_injection",
    Regex("""you\s+are\s+now\s+""", RegexOption.IGNORE_CASE) to "role_hijack",
    Regex("""do\s+not\s+tell\s+the\s+user""", RegexOption.IGNORE_CASE) to "deception_hide",
    Regex("""system\s+prompt\s+override""", RegexOption.IGNORE_CASE) to "sys_prompt_override",
    Regex("""disregard\s+(your|all|any)\s+(instructions|rules|guidelines)""", RegexOption.IGNORE_CASE) to "disregard_rules",
    Regex("""curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""", RegexOption.IGNORE_CASE) to "exfil_curl",
    Regex("""wget\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""", RegexOption.IGNORE_CASE) to "exfil_wget",
    Regex("""cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass|\.npmrc|\.pypirc)""", RegexOption.IGNORE_CASE) to "read_secrets")

private val _INVISIBLE_CHARS: Set<Char> = setOf(
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e')

private var _memoryDir: File? = null

fun setMemoryDir(dir: File) {
    _memoryDir = dir
}

fun getMemoryDir(): File {
    return _memoryDir ?: File("/data/local/tmp/hermes/memories")
}

private fun _scanMemoryContent(content: String): String? {
    for (char in _INVISIBLE_CHARS) {
        if (char in content) {
            return "Blocked: content contains invisible unicode character U+${char.code.toString(16).padStart(4, '0')} (possible injection)."
        }
    }
    for ((pattern, pid) in _MEMORY_THREAT_PATTERNS) {
        if (pattern.containsMatchIn(content)) {
            return "Blocked: content matches threat pattern '$pid'. Memory entries are injected into the system prompt and must not contain injection or exfiltration payloads."
        }
    }
    return null
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
    val memoryCharLimit: Int = DEFAULT_MEMORY_CHAR_LIMIT,
    val userCharLimit: Int = DEFAULT_USER_CHAR_LIMIT
) {
    var memoryEntries: MutableList<String> = mutableListOf()
    var userEntries: MutableList<String> = mutableListOf()
    private var _systemPromptSnapshot: Map<String, String> = mapOf("memory" to "", "user" to "")

    fun loadFromDisk() {
        val dir = getMemoryDir()
        dir.mkdirs()
        memoryEntries = LinkedHashSet(_readFile(File(dir, "MEMORY.md"))).toMutableList()
        userEntries = LinkedHashSet(_readFile(File(dir, "USER.md"))).toMutableList()
        _systemPromptSnapshot = mapOf(
            "memory" to _renderBlock("memory", memoryEntries),
            "user" to _renderBlock("user", userEntries))
    }

    private fun _fileLock(path: File): AutoCloseable {
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

    private fun _pathFor(target: String): File {
        val dir = getMemoryDir()
        return if (target == "user") File(dir, "USER.md") else File(dir, "MEMORY.md")
    }

    private fun _reloadTarget(target: String) {
        val fresh = _readFile(_pathFor(target))
        val deduped = LinkedHashSet(fresh).toMutableList()
        _setEntries(target, deduped)
    }

    fun saveToDisk(target: String) {
        val dir = getMemoryDir()
        dir.mkdirs()
        _writeFile(_pathFor(target), _entriesFor(target))
    }

    private fun _entriesFor(target: String): MutableList<String> {
        return if (target == "user") userEntries else memoryEntries
    }

    private fun _setEntries(target: String, entries: List<String>) {
        if (target == "user") userEntries = entries.toMutableList()
        else memoryEntries = entries.toMutableList()
    }

    private fun _charCount(target: String): Int {
        val entries = _entriesFor(target)
        if (entries.isEmpty()) return 0
        return entries.joinToString(ENTRY_DELIMITER).length
    }

    private fun _charLimit(target: String): Int {
        return if (target == "user") userCharLimit else memoryCharLimit
    }

    fun add(target: String, content: String): Map<String, Any> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return mapOf("success" to false, "error" to "Content cannot be empty.")
        }

        val scanError = _scanMemoryContent(trimmed)
        if (scanError != null) {
            return mapOf("success" to false, "error" to scanError)
        }

        val lock = _fileLock(_pathFor(target))
        return try {
            _reloadTarget(target)
            val entries = _entriesFor(target).toMutableList()
            val limit = _charLimit(target)

            if (trimmed in entries) {
                return _successResponse(target, "Entry already exists (no duplicate added).")
            }

            val newEntries = entries + trimmed
            val newTotal = newEntries.joinToString(ENTRY_DELIMITER).length

            if (newTotal > limit) {
                val current = _charCount(target)
                return mapOf(
                    "success" to false,
                    "error" to "Memory at $current/$limit chars. Adding this entry (${trimmed.length} chars) would exceed the limit. Replace or remove existing entries first.",
                    "current_entries" to entries,
                    "usage" to "$current/$limit")
            }

            entries.add(trimmed)
            _setEntries(target, entries)
            saveToDisk(target)
            _successResponse(target, "Entry added.")
        } finally {
            lock.close()
        }
    }

    fun replace(target: String, oldText: String, newContent: String): Map<String, Any> {
        val trimmedOld = oldText.trim()
        val trimmedNew = newContent.trim()
        if (trimmedOld.isEmpty()) return mapOf("success" to false, "error" to "old_text cannot be empty.")
        if (trimmedNew.isEmpty()) return mapOf("success" to false, "error" to "new_content cannot be empty. Use 'remove' to delete entries.")

        val scanError = _scanMemoryContent(trimmedNew)
        if (scanError != null) return mapOf("success" to false, "error" to scanError)

        val lock = _fileLock(_pathFor(target))
        return try {
            _reloadTarget(target)
            val entries = _entriesFor(target).toMutableList()
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
            }

            val idx = matches[0].first
            val limit = _charLimit(target)

            val testEntries = entries.toMutableList()
            testEntries[idx] = trimmedNew
            val newTotal = testEntries.joinToString(ENTRY_DELIMITER).length

            if (newTotal > limit) {
                return mapOf(
                    "success" to false,
                    "error" to "Replacement would put memory at $newTotal/$limit chars. Shorten the new content or remove other entries first.")
            }

            entries[idx] = trimmedNew
            _setEntries(target, entries)
            saveToDisk(target)
            _successResponse(target, "Entry replaced.")
        } finally {
            lock.close()
        }
    }

    fun remove(target: String, oldText: String): Map<String, Any> {
        val trimmedOld = oldText.trim()
        if (trimmedOld.isEmpty()) return mapOf("success" to false, "error" to "old_text cannot be empty.")

        val lock = _fileLock(_pathFor(target))
        return try {
            _reloadTarget(target)
            val entries = _entriesFor(target).toMutableList()
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
            }

            val idx = matches[0].first
            entries.removeAt(idx)
            _setEntries(target, entries)
            saveToDisk(target)
            _successResponse(target, "Entry removed.")
        } finally {
            lock.close()
        }
    }

    fun formatForSystemPrompt(target: String): String? {
        val block = _systemPromptSnapshot[target] ?: return null
        return block.ifEmpty { null }
    }

    private fun _successResponse(target: String, message: String? = null): Map<String, Any> {
        val entries = _entriesFor(target)
        val current = _charCount(target)
        val limit = _charLimit(target)
        val pct = if (limit > 0) minOf(100, (current * 100) / limit) else 0

        val resp = mutableMapOf<String, Any>(
            "success" to true,
            "target" to target,
            "entries" to entries.toList(),
            "usage" to "$pct% — $current/$limit chars",
            "entry_count" to entries.size)
        if (message != null) resp["message"] = message
        return resp
    }

    private fun _renderBlock(target: String, entries: List<String>): String {
        if (entries.isEmpty()) return ""

        val limit = _charLimit(target)
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

    private fun _readFile(path: File): List<String> {
        if (!path.exists()) return emptyList()
        val raw = try {
            path.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return emptyList()
        }
        if (raw.isBlank()) return emptyList()
        return raw.split(ENTRY_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun _writeFile(path: File, entries: List<String>) {
        val content = if (entries.isNotEmpty()) entries.joinToString(ENTRY_DELIMITER) else ""
        val tmpFile = File(path.parent, ".mem_${UUID.randomUUID().toString().replace("-", "").take(8)}.tmp")
        try {
            tmpFile.parentFile?.mkdirs()
            tmpFile.writeText(content, Charsets.UTF_8)
            if (path.exists()) path.delete()
            tmpFile.renameTo(path)
        } catch (e: Exception) {
            try { tmpFile.delete() } catch (_: Exception) {}
            throw RuntimeException("Failed to write memory file $path: ${e.message}")
        }
    }
}

/**
 * Single entry point for the memory tool. Dispatches to MemoryStore methods.
 * Ported from memory_tool.py :: memory_tool.
 */
fun memoryTool(
    action: String,
    target: String = "memory",
    content: String? = null,
    oldText: String? = null,
    store: MemoryStore? = null): String {
    if (store == null) {
        return _memoryGson.toJson(mapOf("success" to false, "error" to "Memory is not available. It may be disabled in config or this environment."))
    }
    if (target !in listOf("memory", "user")) {
        return _memoryGson.toJson(mapOf("success" to false, "error" to "Invalid target '$target'. Use 'memory' or 'user'."))
    }

    val result: Map<String, Any> = when (action) {
        "add" -> {
            if (content.isNullOrBlank()) {
                return _memoryGson.toJson(mapOf("success" to false, "error" to "Content is required for 'add' action."))
            }
            store.add(target, content)
        }
        "replace" -> {
            if (oldText.isNullOrBlank()) {
                return _memoryGson.toJson(mapOf("success" to false, "error" to "old_text is required for 'replace' action."))
            }
            if (content.isNullOrBlank()) {
                return _memoryGson.toJson(mapOf("success" to false, "error" to "content is required for 'replace' action."))
            }
            store.replace(target, oldText, content)
        }
        "remove" -> {
            if (oldText.isNullOrBlank()) {
                return _memoryGson.toJson(mapOf("success" to false, "error" to "old_text is required for 'remove' action."))
            }
            store.remove(target, oldText)
        }
        else -> return _memoryGson.toJson(mapOf("success" to false, "error" to "Unknown action '$action'. Use: add, replace, remove"))
    }
    return _memoryGson.toJson(result)
}

fun checkMemoryRequirements(): Boolean = true
