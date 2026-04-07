package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/engine.ts (aggregate re-export surface)
 * - src/memory-host-sdk/engine-storage.ts (storage/index helpers)
 * - src/memory-host-sdk/engine-foundation.ts (config/path helpers)
 * - src/memory-host-sdk/host/internal.ts (chunkMarkdown, listMemoryFiles, etc.)
 *
 * Facade that delegates to agent/memory/MemoryManager for persistence
 * and exposes a host-SDK-compatible API for external plugins.
 *
 * Android adaptation: uses ConcurrentHashMap for in-memory store (skip Room/SQLite for now).
 */

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object MemoryHostEngine {

    private val listeners = CopyOnWriteArraySet<MemoryHostEventListener>()

    fun addEventListener(listener: MemoryHostEventListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: MemoryHostEventListener) {
        listeners.remove(listener)
    }

    private fun emit(event: MemoryHostEvent) {
        listeners.forEach { it(event) }
        memoryHostEventListeners.forEach { it(event) }
    }

    /** In-memory store keyed by "scope:key" */
    private val store = ConcurrentHashMap<String, MemoryEntry>()

    private fun storageKey(key: String, scope: MemoryScope): String = "${scope.name}:$key"

    // ---------- CRUD Operations ----------

    suspend fun write(key: String, value: String, scope: MemoryScope = MemoryScope.SESSION): MemoryWriteResult {
        val sk = storageKey(key, scope)
        val existing = store[sk]
        val now = System.currentTimeMillis()
        val entry = if (existing != null) {
            existing.copy(value = value, updatedAt = now)
        } else {
            MemoryEntry(
                id = java.util.UUID.randomUUID().toString(),
                key = key,
                value = value,
                scope = scope,
                createdAt = now,
                updatedAt = now
            )
        }
        store[sk] = entry
        emit(MemoryHostEvent.Written(entry))
        return MemoryWriteResult(id = entry.id, created = existing == null)
    }

    suspend fun read(key: String, scope: MemoryScope = MemoryScope.SESSION): MemoryEntry? {
        return store[storageKey(key, scope)]
    }

    suspend fun query(query: MemoryQuery): List<MemoryEntry> {
        var entries = store.values.asSequence()
        query.scope?.let { scope -> entries = entries.filter { it.scope == scope } }
        query.keyPrefix?.let { prefix -> entries = entries.filter { it.key.startsWith(prefix) } }
        val result = entries.drop(query.offset).take(query.limit).toList()
        emit(MemoryHostEvent.Queried(query, result.size))
        return result
    }

    suspend fun delete(key: String, scope: MemoryScope = MemoryScope.SESSION): Boolean {
        val sk = storageKey(key, scope)
        val removed = store.remove(sk)
        if (removed != null) {
            emit(MemoryHostEvent.Deleted(removed.id, key))
        }
        return removed != null
    }

    suspend fun clear(scope: MemoryScope) {
        val iter = store.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.scope == scope) {
                emit(MemoryHostEvent.Deleted(entry.value.id, entry.value.key))
                iter.remove()
            }
        }
    }

    // ---------- Search (Vector Stub) ----------

    /**
     * Vector search stub.
     * Returns entries matching keyword overlap. Replace with real embedding search later.
     */
    suspend fun search(queryText: String, limit: Int = 10): List<MemorySearchResult> {
        val keywords = extractKeywords(queryText)
        if (keywords.isEmpty()) return emptyList()

        return store.values
            .filter { entry ->
                keywords.any { kw -> entry.value.contains(kw, ignoreCase = true) }
            }
            .sortedByDescending { entry ->
                keywords.count { kw -> entry.value.contains(kw, ignoreCase = true) }.toDouble() / keywords.size
            }
            .take(limit)
            .mapIndexed { index, entry ->
                MemorySearchResult(
                    path = entry.key,
                    startLine = 0,
                    endLine = 0,
                    score = 1.0 - (index.toDouble() / limit),
                    text = entry.value,
                    metadata = entry.metadata
                )
            }
    }

    // ---------- File Utilities (from engine-storage.ts -> host/internal.ts) ----------

    /**
     * Hash text using SHA-256.
     * Aligned with TS hashText.
     */
    fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * List memory files in a directory.
     * Aligned with TS listMemoryFiles.
     */
    fun listMemoryFiles(memoryDir: String, extraPaths: List<String> = emptyList()): List<MemoryFileEntry> {
        val entries = mutableListOf<MemoryFileEntry>()
        val rootDir = File(memoryDir)
        if (!rootDir.exists() || !rootDir.isDirectory) return entries

        // List .md files in root
        rootDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .forEach { file ->
                entries.add(MemoryFileEntry(
                    path = file.absolutePath,
                    relativePath = file.relativeTo(rootDir).path,
                    size = file.length(),
                    modifiedMs = file.lastModified()
                ))
            }

        // Extra paths
        for (extra in normalizeExtraMemoryPaths(extraPaths, memoryDir)) {
            val extraFile = File(extra)
            if (extraFile.isFile && extraFile.extension == "md") {
                entries.add(MemoryFileEntry(
                    path = extraFile.absolutePath,
                    relativePath = extraFile.name,
                    size = extraFile.length(),
                    modifiedMs = extraFile.lastModified()
                ))
            } else if (extraFile.isDirectory) {
                extraFile.walkTopDown()
                    .filter { it.isFile && it.extension == "md" }
                    .forEach { file ->
                        entries.add(MemoryFileEntry(
                            path = file.absolutePath,
                            relativePath = file.relativeTo(extraFile).path,
                            size = file.length(),
                            modifiedMs = file.lastModified()
                        ))
                    }
            }
        }
        return entries
    }

    /**
     * Normalize extra memory paths.
     * Aligned with TS normalizeExtraMemoryPaths.
     */
    fun normalizeExtraMemoryPaths(extraPaths: List<String>, memoryDir: String): List<String> =
        extraPaths.map { path ->
            if (File(path).isAbsolute) path
            else File(memoryDir, path).absolutePath
        }.distinct()

    /**
     * Chunk a markdown file into overlapping chunks for indexing.
     * Aligned with TS chunkMarkdown.
     */
    fun chunkMarkdown(
        text: String,
        path: String,
        chunkSize: Int = 512,
        overlap: Int = 64
    ): List<MemoryChunk> {
        val lines = text.lines()
        if (lines.isEmpty()) return emptyList()

        val chunks = mutableListOf<MemoryChunk>()
        var startLine = 0

        while (startLine < lines.size) {
            val endLine = minOf(startLine + chunkSize, lines.size)
            val chunkText = lines.subList(startLine, endLine).joinToString("\n")

            if (chunkText.isNotBlank()) {
                chunks.add(MemoryChunk(
                    path = path,
                    startLine = startLine + 1, // 1-based
                    endLine = endLine,
                    text = chunkText,
                    hash = hashText(chunkText)
                ))
            }

            startLine = if (endLine >= lines.size) lines.size else endLine - overlap
            if (startLine < 0) startLine = 0
            // Avoid infinite loop
            if (endLine >= lines.size) break
        }
        return chunks
    }

    /**
     * Build a file entry from path and stat info.
     * Aligned with TS buildFileEntry.
     */
    fun buildFileEntry(path: String, rootDir: String): MemoryFileEntry? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return MemoryFileEntry(
            path = file.absolutePath,
            relativePath = file.relativeTo(File(rootDir)).path,
            size = file.length(),
            modifiedMs = file.lastModified()
        )
    }

    /**
     * Ensure a directory exists.
     * Aligned with TS ensureDir.
     */
    fun ensureDir(dirPath: String) {
        val dir = File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    /**
     * Read a memory file's content.
     * Aligned with TS readMemoryFile.
     */
    fun readMemoryFile(path: String): String? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
