package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/engine-storage.ts (MemoryChunk, MemoryFileEntry, MemorySearchResult, etc.)
 * - src/memory-host-sdk/host/types.ts (MemoryProviderStatus, MemorySearchManager, etc.)
 *
 * Core data types for the memory engine.
 */

// ---------- Core Memory Types ----------

data class MemoryEntry(
    val id: String,
    val key: String,
    val value: String,
    val scope: MemoryScope = MemoryScope.SESSION,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val metadata: Map<String, String> = emptyMap()
)

enum class MemoryScope { SESSION, AGENT, ACCOUNT, GLOBAL }

data class MemoryQuery(
    val scope: MemoryScope? = null,
    val keyPrefix: String? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

data class MemoryWriteResult(
    val id: String,
    val created: Boolean
)

// ---------- Memory Chunk Types (from engine-storage.ts -> host/internal.ts) ----------

/**
 * A chunk of a memory file for indexing/search.
 * Aligned with TS MemoryChunk.
 */
data class MemoryChunk(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val hash: String = "",
    val embedding: FloatArray? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryChunk) return false
        return path == other.path && startLine == other.startLine &&
                endLine == other.endLine && text == other.text && hash == other.hash
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + startLine
        result = 31 * result + endLine
        result = 31 * result + text.hashCode()
        return result
    }
}

/**
 * A memory file entry.
 * Aligned with TS MemoryFileEntry.
 */
data class MemoryFileEntry(
    val path: String,
    val relativePath: String,
    val size: Long,
    val modifiedMs: Long
)

/**
 * A single memory search result.
 * Aligned with TS MemorySearchResult.
 */
data class MemorySearchResult(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val score: Double,
    val text: String = "",
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Memory source identifier.
 * Aligned with TS MemorySource.
 */
enum class MemorySource(val value: String) {
    LOCAL("local"),
    QMD("qmd");

    companion object {
        fun fromString(s: String): MemorySource? = entries.find { it.value == s }
    }
}

/**
 * Memory provider status.
 * Aligned with TS MemoryProviderStatus.
 */
data class MemoryProviderStatus(
    val available: Boolean,
    val backend: String? = null,
    val error: String? = null,
    val fileCount: Int = 0,
    val chunkCount: Int = 0,
    val lastSyncMs: Long? = null
)

/**
 * Memory embedding probe result.
 * Aligned with TS MemoryEmbeddingProbeResult.
 */
data class MemoryEmbeddingProbeResult(
    val available: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val dimensions: Int? = null,
    val error: String? = null
)

/**
 * Memory sync progress update.
 * Aligned with TS MemorySyncProgressUpdate.
 */
data class MemorySyncProgressUpdate(
    val phase: String,
    val current: Int,
    val total: Int,
    val message: String? = null
)

// ---------- Backend Config Types ----------

/**
 * Memory backend types.
 * Aligned with TS MemoryBackend.
 */
enum class MemoryBackend(val value: String) {
    SQLITE("sqlite"),
    LANCEDB("lancedb");

    companion object {
        fun fromString(s: String): MemoryBackend =
            entries.find { it.value == s } ?: SQLITE
    }
}

/**
 * Resolved memory backend configuration.
 * Aligned with TS ResolvedMemoryBackendConfig.
 */
data class ResolvedMemoryBackendConfig(
    val backend: MemoryBackend = MemoryBackend.SQLITE,
    val memoryDir: String,
    val extraPaths: List<String> = emptyList(),
    val embeddingProvider: String? = null,
    val embeddingModel: String? = null,
    val embeddingDimensions: Int? = null,
    val citationsMode: String = "inline" // "inline" | "footnotes" | "none"
)

/**
 * Resolved QMD configuration.
 * Aligned with TS ResolvedQmdConfig.
 */
data class ResolvedQmdConfig(
    val enabled: Boolean = false,
    val searchMode: String = "hybrid", // "hybrid" | "vector" | "fts"
    val indexPaths: List<String> = emptyList()
)

// ---------- Memory Search Manager Interface ----------

/**
 * Interface for memory search operations.
 * Aligned with TS MemorySearchManager.
 */
interface MemorySearchManager {
    suspend fun search(query: String, limit: Int = 10): List<MemorySearchResult>
    suspend fun getStatus(): MemoryProviderStatus
    suspend fun sync(onProgress: ((MemorySyncProgressUpdate) -> Unit)? = null)
    suspend fun close()
}
