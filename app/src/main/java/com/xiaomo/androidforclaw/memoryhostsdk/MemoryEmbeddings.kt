package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/engine-embeddings.ts
 * - src/memory-host-sdk/host/embedding-inputs.ts
 * - src/memory-host-sdk/host/embedding-chunk-limits.ts
 *
 * Embedding provider abstraction and adapter for memory search.
 * Android adaptation: uses direct provider registry instead of plugin runtime.
 */

import java.util.concurrent.ConcurrentHashMap

// ---------- Embedding Provider Types ----------

/**
 * Input for embedding: either text or structured content.
 * Aligned with TS EmbeddingInput.
 */
sealed class EmbeddingInput {
    data class Text(val text: String) : EmbeddingInput()
    data class Structured(val parts: List<EmbeddingPart>) : EmbeddingInput()
}

sealed class EmbeddingPart {
    data class TextPart(val text: String) : EmbeddingPart()
    data class ImagePart(val path: String, val mimeType: String) : EmbeddingPart()
}

/**
 * Check if an embedding input contains non-text parts.
 * Aligned with TS hasNonTextEmbeddingParts.
 */
fun hasNonTextEmbeddingParts(input: EmbeddingInput): Boolean = when (input) {
    is EmbeddingInput.Text -> false
    is EmbeddingInput.Structured -> input.parts.any { it is EmbeddingPart.ImagePart }
}

/**
 * Options for creating an embedding provider.
 * Aligned with TS MemoryEmbeddingProviderCreateOptions.
 */
data class MemoryEmbeddingProviderCreateOptions(
    val model: String? = null,
    val dimensions: Int? = null,
    val apiKey: String? = null,
    val baseUrl: String? = null
)

/**
 * Result of creating an embedding provider.
 * Aligned with TS MemoryEmbeddingProviderCreateResult.
 */
data class MemoryEmbeddingProviderCreateResult(
    val provider: MemoryEmbeddingProvider?,
    val error: String? = null
)

/**
 * A memory embedding provider: produces vector embeddings from text.
 * Aligned with TS MemoryEmbeddingProvider.
 */
interface MemoryEmbeddingProvider {
    val id: String
    val model: String
    val dimensions: Int

    /** Embed a single text. */
    suspend fun embed(text: String): FloatArray

    /** Embed a batch of inputs. */
    suspend fun embedBatch(inputs: List<EmbeddingInput>): List<FloatArray>
}

/**
 * Adapter for registering embedding provider factories.
 * Aligned with TS MemoryEmbeddingProviderAdapter.
 */
interface MemoryEmbeddingProviderAdapter {
    val id: String
    val label: String
    val defaultModel: String

    suspend fun create(options: MemoryEmbeddingProviderCreateOptions): MemoryEmbeddingProviderCreateResult
}

// ---------- Embedding Provider Registry ----------

private val embeddingProviderAdapters = ConcurrentHashMap<String, MemoryEmbeddingProviderAdapter>()

/**
 * Register an embedding provider adapter.
 */
fun registerMemoryEmbeddingProviderAdapter(adapter: MemoryEmbeddingProviderAdapter) {
    embeddingProviderAdapters[adapter.id] = adapter
}

/**
 * List registered embedding provider adapters.
 * Aligned with TS listRegisteredMemoryEmbeddingProviderAdapters.
 */
fun listRegisteredMemoryEmbeddingProviderAdapters(): List<MemoryEmbeddingProviderAdapter> =
    embeddingProviderAdapters.values.toList()

/**
 * Get an embedding provider adapter by ID.
 * Aligned with TS getMemoryEmbeddingProvider.
 */
fun getMemoryEmbeddingProvider(id: String): MemoryEmbeddingProviderAdapter? =
    embeddingProviderAdapters[id]

/**
 * List registered embedding provider IDs.
 * Aligned with TS listRegisteredMemoryEmbeddingProviders.
 */
fun listRegisteredMemoryEmbeddingProviders(): List<String> =
    embeddingProviderAdapters.keys().toList()

// ---------- Vector Utilities ----------

/**
 * Compute cosine similarity between two vectors.
 * Aligned with TS cosineSimilarity from host/internal.ts.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "Vectors must have the same dimension" }
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = Math.sqrt(normA) * Math.sqrt(normB)
    return if (denom == 0.0) 0.0 else dot / denom
}

/**
 * Parse a comma-separated embedding string into a FloatArray.
 * Aligned with TS parseEmbedding from host/internal.ts.
 */
fun parseEmbedding(raw: String): FloatArray? {
    return try {
        raw.split(",").map { it.trim().toFloat() }.toFloatArray()
    } catch (_: Exception) {
        null
    }
}

// ---------- Chunk Limits ----------

/**
 * Estimate UTF-8 byte length of a string.
 * Aligned with TS estimateUtf8Bytes.
 */
fun estimateUtf8Bytes(text: String): Int =
    text.toByteArray(Charsets.UTF_8).size

/**
 * Enforce embedding max input tokens by truncating.
 * Simplified from TS enforceEmbeddingMaxInputTokens.
 */
fun enforceEmbeddingMaxInputTokens(text: String, maxTokens: Int): String {
    // Rough estimate: ~4 chars per token
    val maxChars = maxTokens * 4
    return if (text.length > maxChars) text.substring(0, maxChars) else text
}
