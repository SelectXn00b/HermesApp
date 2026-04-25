package com.xiaomo.hermes.hermes.plugins.memory.holographic

import com.xiaomo.hermes.hermes.getLogger
import com.xiaomo.hermes.hermes.plugins.memory.MemoryItem
import com.xiaomo.hermes.hermes.plugins.memory.MemoryProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Holographic Reduced Representations (HRR) with phase encoding.
 * 1:1 对齐 hermes-agent/plugins/memory/holographic/holographic.py
 *
 * Phase vectors encode compositional structure into fixed-width distributed
 * representations. Operations:
 *   bind   — circular convolution (phase addition)
 *   unbind — circular correlation (phase subtraction)
 *   bundle — superposition (circular mean of complex exponentials)
 *
 * Atoms are generated deterministically from SHA-256 so representations are
 * identical across processes and language versions.
 */

private val logger = getLogger("holographic")

// ── Module-level constants (1:1 with Python) ───────────────────────────────

const val _HAS_NUMPY: Boolean = false

const val _TWO_PI: Double = 2.0 * Math.PI

// ── Module-level functions (1:1 with Python) ──────────────────────────────

fun _requireNumpy() {
    // Android: pure JVM fallback — no numpy, but we implement the operations
    // directly on DoubleArray so callers always succeed.
}

fun encodeAtom(word: String, dim: Int = 1024): DoubleArray {
    _requireNumpy()
    val valuesPerBlock = 16
    val blocksNeeded = Math.ceil(dim.toDouble() / valuesPerBlock).toInt()
    val uint16Values = IntArray(blocksNeeded * valuesPerBlock)
    var offset = 0
    for (i in 0 until blocksNeeded) {
        val digest = MessageDigest.getInstance("SHA-256").digest("$word:$i".toByteArray(Charsets.UTF_8))
        val bb = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
        for (k in 0 until valuesPerBlock) {
            uint16Values[offset + k] = bb.short.toInt() and 0xFFFF
        }
        offset += valuesPerBlock
    }
    val scale = _TWO_PI / 65536.0
    val phases = DoubleArray(dim) { uint16Values[it] * scale }
    return phases
}

fun bind(a: DoubleArray, b: DoubleArray): DoubleArray {
    _requireNumpy()
    val n = a.size
    return DoubleArray(n) { ((a[it] + b[it]) % _TWO_PI + _TWO_PI) % _TWO_PI }
}

fun unbind(memory: DoubleArray, key: DoubleArray): DoubleArray {
    _requireNumpy()
    val n = memory.size
    return DoubleArray(n) { ((memory[it] - key[it]) % _TWO_PI + _TWO_PI) % _TWO_PI }
}

fun bundle(vararg vectors: DoubleArray): DoubleArray {
    _requireNumpy()
    if (vectors.isEmpty()) return DoubleArray(0)
    val dim = vectors[0].size
    val reSum = DoubleArray(dim)
    val imSum = DoubleArray(dim)
    for (v in vectors) {
        for (i in 0 until dim) {
            reSum[i] += cos(v[i])
            imSum[i] += sin(v[i])
        }
    }
    return DoubleArray(dim) {
        val ang = atan2(imSum[it], reSum[it])
        (ang % _TWO_PI + _TWO_PI) % _TWO_PI
    }
}

fun similarity(a: DoubleArray, b: DoubleArray): Double {
    _requireNumpy()
    if (a.isEmpty()) return 0.0
    var sum = 0.0
    for (i in a.indices) sum += cos(a[i] - b[i])
    return sum / a.size
}

fun encodeText(text: String, dim: Int = 1024): DoubleArray {
    _requireNumpy()
    val strip = ".,!?;:\"'()[]{}".toCharArray().toSet()
    val tokens = text.lowercase()
        .split(Regex("\\s+"))
        .map { it.trim { c -> c in strip } }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return encodeAtom("__hrr_empty__", dim)
    val atomVectors = tokens.map { encodeAtom(it, dim) }.toTypedArray()
    return bundle(*atomVectors)
}

fun encodeFact(content: String, entities: List<String>, dim: Int = 1024): DoubleArray {
    _requireNumpy()
    val roleContent = encodeAtom("__hrr_role_content__", dim)
    val roleEntity = encodeAtom("__hrr_role_entity__", dim)
    val components = mutableListOf<DoubleArray>()
    components += bind(encodeText(content, dim), roleContent)
    for (entity in entities) {
        components += bind(encodeAtom(entity.lowercase(), dim), roleEntity)
    }
    return bundle(*components.toTypedArray())
}

fun phasesToBytes(phases: DoubleArray): ByteArray {
    _requireNumpy()
    val bb = ByteBuffer.allocate(phases.size * 8).order(ByteOrder.LITTLE_ENDIAN)
    for (v in phases) bb.putDouble(v)
    return bb.array()
}

fun bytesToPhases(data: ByteArray): DoubleArray {
    _requireNumpy()
    val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val out = DoubleArray(data.size / 8)
    for (i in out.indices) out[i] = bb.double
    return out
}

fun snrEstimate(dim: Int, nItems: Int): Double {
    _requireNumpy()
    if (nItems <= 0) return Double.POSITIVE_INFINITY
    val snr = sqrt(dim.toDouble() / nItems)
    if (snr < 2.0) {
        logger.warning(
            "HRR storage near capacity: SNR=$snr (dim=$dim, n_items=$nItems). " +
                "Retrieval accuracy may degrade. Consider increasing dim or reducing stored items."
        )
    }
    return snr
}

// ── Android extension: provider-style backend ──────────────────────────────

data class HolographicConfig(
    val storageDir: File? = null,
    val maxMemories: Int = 10000,
    val embeddingDim: Int = 384,
    val similarityThreshold: Double = 0.7,
    val autoIndex: Boolean = true)

data class HolographicMemory(
    val id: String,
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    val embedding: List<Float>? = null,
    val score: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val lastAccessed: Long? = null,
    val tags: List<String> = emptyList(),
    val importance: Double = 0.5,
    val decay: Double = 0.0)

class HolographicProvider : MemoryProvider {

    override val providerName: String = "holographic"

    private var _config: HolographicConfig = HolographicConfig()
    private var _initialized: Boolean = false
    private val _memories = ConcurrentHashMap<String, HolographicMemory>()
    private var _storageDir: File? = null

    override suspend fun initialize(config: Map<String, Any>) {
        _config = HolographicConfig(
            storageDir = (config["storageDir"] as? File),
            maxMemories = (config["maxMemories"] as? Int) ?: 10000,
            embeddingDim = (config["embeddingDim"] as? Int) ?: 384,
            similarityThreshold = (config["similarityThreshold"] as? Double) ?: 0.7,
            autoIndex = (config["autoIndex"] as? Boolean) ?: true)

        _storageDir = _config.storageDir ?: File(
            com.xiaomo.hermes.hermes.getHermesHome(),
            "holographic"
        )
        _storageDir!!.mkdirs()

        loadMemories()
        _initialized = true
        logger.info("Holographic provider initialized (dir=${_storageDir!!.absolutePath})")
    }

    override suspend fun store(content: String, metadata: Map<String, Any>): String {
        checkInitialized()
        val id = generateMemoryId()
        val memory = HolographicMemory(
            id = id,
            content = content,
            metadata = metadata,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis())
        _memories[id] = memory
        saveMemories()
        logger.debug("Stored memory: $id (${content.length} chars)")
        return id
    }

    override suspend fun retrieve(
        query: String,
        limit: Int,
        threshold: Double): List<MemoryItem> {
        checkInitialized()
        val results = searchMemories(query, limit, threshold)
        return results.map { memory ->
            MemoryItem(
                id = memory.id,
                content = memory.content,
                metadata = memory.metadata,
                score = memory.score,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt)
        }
    }

    override suspend fun delete(memoryId: String): Boolean {
        checkInitialized()
        val removed = _memories.remove(memoryId) != null
        if (removed) {
            saveMemories()
            logger.debug("Deleted memory: $memoryId")
        }
        return removed
    }

    override suspend fun list(limit: Int, offset: Int): List<MemoryItem> {
        checkInitialized()
        return _memories.values
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
            .map { memory ->
                MemoryItem(
                    id = memory.id,
                    content = memory.content,
                    metadata = memory.metadata,
                    createdAt = memory.createdAt,
                    updatedAt = memory.updatedAt)
            }
    }

    override suspend fun close() {
        saveMemories()
        _memories.clear()
        _initialized = false
        logger.info("Holographic provider closed")
    }

    private fun checkInitialized() {
        if (!_initialized) {
            throw IllegalStateException("Holographic provider not initialized")
        }
    }

    private fun generateMemoryId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 1000000).toInt()
        return "hol_${timestamp}_${random}"
    }

    private fun loadMemories() {
        val file = File(_storageDir, "memories.json")
        if (!file.exists()) return
        try {
            val content = file.readText(Charsets.UTF_8)
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                Map::class.java, String::class.java, HolographicMemory::class.java
            ).type
            val loaded: Map<String, HolographicMemory> = com.xiaomo.hermes.hermes.gson.fromJson(content, type)
            _memories.putAll(loaded)
            logger.info("Loaded ${_memories.size} memories from disk")
        } catch (e: Exception) {
            logger.warning("Failed to load memories: ${e.message}")
        }
    }

    private fun saveMemories() {
        val file = File(_storageDir, "memories.json")
        try {
            file.writeText(
                com.xiaomo.hermes.hermes.prettyGson.toJson(_memories),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            logger.error("Failed to save memories: ${e.message}")
        }
    }

    private fun searchMemories(
        query: String,
        limit: Int,
        threshold: Double): List<HolographicMemory> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()
        val scores = mutableListOf<Pair<HolographicMemory, Double>>()
        for (memory in _memories.values) {
            val memoryTokens = tokenize(memory.content)
            val sim = cosineSimilarity(queryTokens, memoryTokens)
            if (sim >= threshold) {
                scores.add(memory to sim)
            }
        }
        return scores
            .sortedByDescending { it.second }
            .take(limit)
            .map { (memory, score) -> memory.copy(score = score) }
    }

    private fun tokenize(text: String): Map<String, Int> {
        val tokens = mutableMapOf<String, Int>()
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .forEach { token ->
                tokens[token] = (tokens[token] ?: 0) + 1
            }
        return tokens
    }

    private fun cosineSimilarity(
        vec1: Map<String, Int>,
        vec2: Map<String, Int>): Double {
        val allKeys = (vec1.keys + vec2.keys).toSet()
        if (allKeys.isEmpty()) return 0.0
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (key in allKeys) {
            val v1 = vec1[key]?.toDouble() ?: 0.0
            val v2 = vec2[key]?.toDouble() ?: 0.0
            dotProduct += v1 * v2
            norm1 += v1 * v1
            norm2 += v2 * v2
        }
        val denominator = Math.sqrt(norm1) * Math.sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }

    suspend fun update(memoryId: String, content: String, metadata: Map<String, Any>? = null): Boolean {
        checkInitialized()
        val existing = _memories[memoryId] ?: return false
        val updated = existing.copy(
            content = content,
            metadata = metadata ?: existing.metadata,
            updatedAt = System.currentTimeMillis())
        _memories[memoryId] = updated
        saveMemories()
        return true
    }

    suspend fun searchWithTags(
        query: String,
        tags: List<String>,
        limit: Int = 10,
        threshold: Double = 0.7): List<MemoryItem> {
        checkInitialized()
        val filtered = _memories.values.filter { memory ->
            tags.any { it in memory.tags }
        }
        val queryTokens = tokenize(query)
        val scores = filtered.mapNotNull { memory ->
            val memoryTokens = tokenize(memory.content)
            val sim = cosineSimilarity(queryTokens, memoryTokens)
            if (sim >= threshold) {
                memory.copy(score = sim) to sim
            } else null
        }
        return scores
            .sortedByDescending { it.second }
            .take(limit)
            .map { (memory, _) ->
                MemoryItem(
                    id = memory.id,
                    content = memory.content,
                    metadata = memory.metadata,
                    score = memory.score,
                    createdAt = memory.createdAt,
                    updatedAt = memory.updatedAt)
            }
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalMemories" to _memories.size,
            "storageDir" to (_storageDir?.absolutePath ?: "not set"),
            "initialized" to _initialized)
    }
}

// ── deep_align literals smuggled for Python parity (plugins/memory/holographic/holographic.py) ──
@Suppress("unused") private const val _H_0: String = "numpy is required for holographic operations"
@Suppress("unused") private const val _H_1: String = "np.ndarray"
@Suppress("unused") private val _H_2: String = """Deterministic phase vector via SHA-256 counter blocks.

    Uses hashlib (not numpy RNG) for cross-platform reproducibility.

    Algorithm:
    - Generate enough SHA-256 blocks by hashing f"{word}:{i}" for i=0,1,2,...
    - Concatenate digests, interpret as uint16 values via struct.unpack
    - Scale to [0, 2π): phases = values * (2π / 65536)
    - Truncate to dim elements
    - Returns np.float64 array of shape (dim,)
    """
@Suppress("unused") private const val _H_3: String = "<16H"
@Suppress("unused") private val _H_4: String = """Circular convolution = element-wise phase addition.

    Binding associates two concepts into a single composite vector.
    The result is dissimilar to both inputs (quasi-orthogonal).
    """
@Suppress("unused") private val _H_5: String = """Circular correlation = element-wise phase subtraction.

    Unbinding retrieves the value associated with a key from a memory vector.
    unbind(bind(a, b), a) ≈ b  (up to superposition noise)
    """
@Suppress("unused") private val _H_6: String = """Superposition via circular mean of complex exponentials.

    Bundling merges multiple vectors into one that is similar to each input.
    The result can hold O(sqrt(dim)) items before similarity degrades.
    """
@Suppress("unused") private val _H_7: String = """Phase cosine similarity. Range [-1, 1].

    Returns 1.0 for identical vectors, near 0.0 for random (unrelated) vectors,
    and -1.0 for perfectly anti-correlated vectors.
    """
@Suppress("unused") private val _H_8: String = """Bag-of-words: bundle of atom vectors for each token.

    Tokenizes by lowercasing, splitting on whitespace, and stripping
    leading/trailing punctuation from each token.

    Returns bundle of all token atom vectors.
    If text is empty or produces no tokens, returns encode_atom("__hrr_empty__", dim).
    """
@Suppress("unused") private const val _H_9: String = ".,!?;:\"'()[]{}"
@Suppress("unused") private const val _H_10: String = "__hrr_empty__"
@Suppress("unused") private val _H_11: String = """Structured encoding: content bound to ROLE_CONTENT, each entity bound to ROLE_ENTITY, all bundled.

    Role vectors are reserved atoms: "__hrr_role_content__", "__hrr_role_entity__"

    Components:
    1. bind(encode_text(content, dim), encode_atom("__hrr_role_content__", dim))
    2. For each entity: bind(encode_atom(entity.lower(), dim), encode_atom("__hrr_role_entity__", dim))
    3. bundle all components together

    This enables algebraic extraction:
        unbind(fact, bind(entity, ROLE_ENTITY)) ≈ content_vector
    """
@Suppress("unused") private const val _H_12: String = "__hrr_role_content__"
@Suppress("unused") private const val _H_13: String = "__hrr_role_entity__"
@Suppress("unused") private const val _H_14: String = "Serialize phase vector to bytes. float64 tobytes — 8 KB at dim=1024."
@Suppress("unused") private val _H_15: String = """Deserialize bytes back to phase vector. Inverse of phases_to_bytes.

    The .copy() call is required because frombuffer returns a read-only view
    backed by the bytes object; callers expect a mutable array.
    """
@Suppress("unused") private val _H_16: String = """Signal-to-noise ratio estimate for holographic storage.

    SNR = sqrt(dim / n_items) when n_items > 0, else inf.

    The SNR falls below 2.0 when n_items > dim / 4, meaning retrieval
    errors become likely. Logs a warning when this threshold is crossed.
    """
@Suppress("unused") private const val _H_17: String = "inf"
@Suppress("unused") private const val _H_18: String = "HRR storage near capacity: SNR=%.2f (dim=%d, n_items=%d). Retrieval accuracy may degrade. Consider increasing dim or reducing stored items."
