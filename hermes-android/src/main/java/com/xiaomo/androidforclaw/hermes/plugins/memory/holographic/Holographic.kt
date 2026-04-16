package com.xiaomo.androidforclaw.hermes.plugins.memory.holographic

import com.xiaomo.androidforclaw.hermes.getLogger
import com.xiaomo.androidforclaw.hermes.plugins.memory.MemoryItem
import com.xiaomo.androidforclaw.hermes.plugins.memory.MemoryProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Holographic 记忆后端
 * 1:1 对齐 hermes-agent/plugins/memory/holographic/holographic.py
 *
 * 基于本地文件的记忆存储和检索系统。
 * 使用向量相似度进行语义搜索。
 */

private val logger = getLogger("holographic")

// ── 配置 ──────────────────────────────────────────────────────────────────
data class HolographicConfig(
    val storageDir: File? = null,
    val maxMemories: Int = 10000,
    val embeddingDim: Int = 384,
    val similarityThreshold: Double = 0.7,
    val autoIndex: Boolean = true)

// ── 记忆条目（扩展 MemoryItem）────────────────────────────────────────────
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

/**
 * Holographic 记忆后端实现
 */
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
            com.xiaomo.androidforclaw.hermes.getHermesHome(),
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

        // 使用 TF-IDF + 余弦相似度进行检索
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

    // ── 内部方法 ──────────────────────────────────────────────────────────

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
            val loaded: Map<String, HolographicMemory> = com.xiaomo.androidforclaw.hermes.gson.fromJson(content, type)
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
                com.xiaomo.androidforclaw.hermes.prettyGson.toJson(_memories),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            logger.error("Failed to save memories: ${e.message}")
        }
    }

    /**
     * 基于 TF-IDF 的相似度搜索
     * Python: holographic.py -> search_memories()
     */
    private fun searchMemories(
        query: String,
        limit: Int,
        threshold: Double): List<HolographicMemory> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scores = mutableListOf<Pair<HolographicMemory, Double>>()

        for (memory in _memories.values) {
            val memoryTokens = tokenize(memory.content)
            val similarity = cosineSimilarity(queryTokens, memoryTokens)
            if (similarity >= threshold) {
                scores.add(memory to similarity)
            }
        }

        return scores
            .sortedByDescending { it.second }
            .take(limit)
            .map { (memory, score) ->
                memory.copy(score = score)
            }
    }

    /**
     * 简单分词
     */
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

    /**
     * 余弦相似度（基于词频向量）
     */
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

    // ── 公开方法（扩展功能）────────────────────────────────────────────────

    /**
     * 更新记忆
     */
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

    /**
     * 搜索记忆（带标签过滤）
     */
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
            val similarity = cosineSimilarity(queryTokens, memoryTokens)
            if (similarity >= threshold) {
                memory.copy(score = similarity) to similarity
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

    /**
     * 获取记忆统计
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalMemories" to _memories.size,
            "storageDir" to (_storageDir?.absolutePath ?: "not set"),
            "initialized" to _initialized)
    }
}
