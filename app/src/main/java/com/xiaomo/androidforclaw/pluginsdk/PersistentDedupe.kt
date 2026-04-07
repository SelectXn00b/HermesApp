package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/persistent-dedupe.ts
 *
 * Dedupe helper that combines in-memory fast checks with a file-backed store.
 * Android adaptation: file lock replaced with synchronized blocks
 * (single-process Android app doesn't need cross-process locking).
 */

import java.util.concurrent.ConcurrentHashMap

// ---------- Types ----------

/**
 * Options for creating a persistent dedupe.
 * Aligned with TS PersistentDedupeOptions.
 */
data class PersistentDedupeOptions(
    val ttlMs: Long,
    val memoryMaxSize: Int,
    val fileMaxEntries: Int,
    val resolveFilePath: (namespace: String) -> String,
    val onDiskError: ((Exception) -> Unit)? = null,
)

/**
 * Check options for a single dedupe entry.
 * Aligned with TS PersistentDedupeCheckOptions.
 */
data class PersistentDedupeCheckOptions(
    val namespace: String? = null,
    val now: Long? = null,
    val onDiskError: ((Exception) -> Unit)? = null,
)

/**
 * Persistent dedupe interface.
 * Aligned with TS PersistentDedupe.
 */
interface PersistentDedupe {
    suspend fun checkAndRecord(key: String, options: PersistentDedupeCheckOptions? = null): Boolean
    suspend fun warmup(namespace: String? = null, onError: ((Exception) -> Unit)? = null): Int
    fun clearMemory()
    fun memorySize(): Int
}

// ---------- In-memory dedupe cache ----------

private class DedupeCache(
    private val ttlMs: Long,
    private val maxSize: Int,
) {
    private val entries = LinkedHashMap<String, Long>(16, 0.75f, true)

    @Synchronized
    fun check(key: String, now: Long): Boolean {
        val existing = entries[key]
        if (existing != null && (ttlMs <= 0 || now - existing < ttlMs)) {
            return true // seen recently
        }
        entries[key] = now
        while (entries.size > maxSize) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        return false // first time
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}

// ---------- Data helpers ----------

@Suppress("UNCHECKED_CAST")
private fun sanitizeData(value: Map<String, Any?>): Map<String, Long> {
    val out = mutableMapOf<String, Long>()
    for ((k, v) in value) {
        val ts = when (v) {
            is Number -> v.toLong()
            else -> continue
        }
        if (ts > 0) out[k] = ts
    }
    return out
}

private fun pruneData(data: MutableMap<String, Long>, now: Long, ttlMs: Long, maxEntries: Int) {
    if (ttlMs > 0) {
        data.entries.removeAll { now - it.value >= ttlMs }
    }
    val keys = data.keys.toList()
    if (keys.size > maxEntries) {
        keys.sortedBy { data[it] ?: 0L }
            .take(keys.size - maxEntries)
            .forEach { data.remove(it) }
    }
}

// ---------- Implementation ----------

/**
 * Create a dedupe helper that combines in-memory fast checks with a file-backed store.
 * Aligned with TS createPersistentDedupe.
 */
fun createPersistentDedupe(options: PersistentDedupeOptions): PersistentDedupe {
    val ttlMs = maxOf(0L, options.ttlMs)
    val memoryMaxSize = maxOf(0, options.memoryMaxSize)
    val fileMaxEntries = maxOf(1, options.fileMaxEntries)
    val resolveFilePath = options.resolveFilePath
    val globalOnDiskError = options.onDiskError
    val memory = DedupeCache(ttlMs, memoryMaxSize)
    val inflight = ConcurrentHashMap<String, Boolean>()

    return object : PersistentDedupe {
        override suspend fun checkAndRecord(
            key: String,
            options: PersistentDedupeCheckOptions?,
        ): Boolean {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) return true
            val namespace = options?.namespace?.trim()?.takeIf { it.isNotEmpty() } ?: "global"
            val scopedKey = "$namespace:$trimmed"

            if (inflight.containsKey(scopedKey)) return false

            val onDiskError = options?.onDiskError ?: globalOnDiskError
            val now = options?.now ?: System.currentTimeMillis()

            // Memory fast path
            if (memory.check(scopedKey, now)) return false

            val filePath = resolveFilePath(namespace)
            inflight[scopedKey] = true
            try {
                val duplicate = try {
                    synchronized(filePath.intern()) {
                        val readResult = readJsonFileWithFallback(filePath, emptyMap())
                        val data = sanitizeData(readResult.value).toMutableMap()
                        val seenAt = data[trimmed]
                        val isRecent = seenAt != null && (ttlMs <= 0 || now - seenAt < ttlMs)
                        if (isRecent) return@synchronized true
                        data[trimmed] = now
                        pruneData(data, now, ttlMs, fileMaxEntries)
                        writeJsonFileAtomically(filePath, data.mapValues { (_, v) -> v as Any? })
                        false
                    }
                } catch (e: Exception) {
                    onDiskError?.invoke(e)
                    // Fall through to memory-only mode
                    false
                }
                return !duplicate
            } finally {
                inflight.remove(scopedKey)
            }
        }

        override suspend fun warmup(namespace: String?, onError: ((Exception) -> Unit)?): Int {
            val ns = namespace?.trim()?.takeIf { it.isNotEmpty() } ?: "global"
            val now = System.currentTimeMillis()
            return try {
                val filePath = resolveFilePath(ns)
                val readResult = readJsonFileWithFallback(filePath, emptyMap())
                val data = sanitizeData(readResult.value)
                var loaded = 0
                for ((k, ts) in data) {
                    if (ttlMs > 0 && now - ts >= ttlMs) continue
                    val scopedKey = "$ns:$k"
                    memory.check(scopedKey, ts)
                    loaded++
                }
                loaded
            } catch (e: Exception) {
                onError?.invoke(e)
                0
            }
        }

        override fun clearMemory() = memory.clear()
        override fun memorySize(): Int = memory.size()
    }
}
