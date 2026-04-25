package com.xiaomo.hermes.hermes.plugins.memory.holographic

import com.xiaomo.hermes.hermes.getLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Holographic 检索引擎
 * 1:1 对齐 hermes-agent/plugins/memory/holographic/retrieval.py
 *
 * 提供基于 TF-IDF 和余弦相似度的语义检索。
 */

private val logger = getLogger("holographic.retrieval")

// ── 检索配置 ──────────────────────────────────────────────────────────────
data class RetrievalConfig(
    val maxResults: Int = 20,
    val minScore: Double = 0.1,
    val useTfIdf: Boolean = true,
    val useBM25: Boolean = false,
    val bm25K1: Double = 1.5,
    val bm25B: Double = 0.75,
    val decayRate: Double = 0.01,
    val importanceBoost: Double = 1.5,
    val recencyBoost: Double = 0.1)

// ── 检索结果 ──────────────────────────────────────────────────────────────
data class RetrievalResult(
    val memoryId: String,
    val content: String,
    val score: Double,
    val rank: Int,
    val metadata: Map<String, Any> = emptyMap(),
    val explanation: String? = null)

// ── 检索引擎 ──────────────────────────────────────────────────────────────
class HolographicRetrievalEngine(
    private val config: RetrievalConfig = RetrievalConfig()) {

    // IDF 缓存
    private val _idfCache = ConcurrentHashMap<String, Double>()
    private var _totalDocs = 0
    private var _idfDirty = true

    /**
     * 检索记忆
     * Python: retrieval.py -> retrieve()
     */
    fun retrieve(
        query: String,
        memories: List<HolographicMemory>,
        limit: Int = config.maxResults,
        threshold: Double = config.minScore): List<RetrievalResult> {
        if (query.isBlank() || memories.isEmpty()) return emptyList()

        // 更新 IDF 缓存
        if (_idfDirty || _totalDocs != memories.size) {
            updateIdfCache(memories)
        }

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scores = memories.mapNotNull { memory ->
            val memoryTokens = tokenize(memory.content)
            val rawScore = if (config.useBM25) {
                bm25Score(queryTokens, memoryTokens, memories.size)
            } else {
                tfidfCosineSimilarity(queryTokens, memoryTokens)
            }

            // 应用时间衰减
            val timeDecay = calculateTimeDecay(memory.createdAt)

            // 应用重要性加权
            val importanceBoost = if (memory.importance > 0.7) {
                config.importanceBoost
            } else 1.0

            // 应用新鲜度加权
            val recencyBoost = if (memory.lastAccessed != null) {
                val age = System.currentTimeMillis() - memory.lastAccessed
                1.0 + config.recencyBoost * (1.0 / (1.0 + age / (24 * 3600 * 1000.0)))
            } else 1.0

            val finalScore = rawScore * timeDecay * importanceBoost * recencyBoost

            if (finalScore >= threshold) {
                RetrievalResult(
                    memoryId = memory.id,
                    content = memory.content,
                    score = finalScore,
                    rank = 0,
                    metadata = memory.metadata)
            } else null
        }

        return scores
            .sortedByDescending { it.score }
            .take(limit)
            .mapIndexed { index, result ->
                result.copy(rank = index + 1)
            }
    }

    /**
     * 批量检索
     */
    fun retrieveBatch(
        queries: List<String>,
        memories: List<HolographicMemory>,
        limit: Int = config.maxResults,
        threshold: Double = config.minScore): Map<String, List<RetrievalResult>> {
        return queries.associateWith { query ->
            retrieve(query, memories, limit, threshold)
        }
    }

    /**
     * 相似记忆查找
     */
    fun findSimilar(
        targetMemory: HolographicMemory,
        memories: List<HolographicMemory>,
        limit: Int = 5,
        threshold: Double = 0.5): List<RetrievalResult> {
        val targetTokens = tokenize(targetMemory.content)
        if (targetTokens.isEmpty()) return emptyList()

        return memories
            .filter { it.id != targetMemory.id }
            .mapNotNull { memory ->
                val memoryTokens = tokenize(memory.content)
                val similarity = cosineSimilarity(targetTokens, memoryTokens)
                if (similarity >= threshold) {
                    RetrievalResult(
                        memoryId = memory.id,
                        content = memory.content,
                        score = similarity,
                        rank = 0,
                        metadata = memory.metadata)
                } else null
            }
            .sortedByDescending { it.score }
            .take(limit)
            .mapIndexed { index, result ->
                result.copy(rank = index + 1)
            }
    }

    // ── 内部方法 ──────────────────────────────────────────────────────────

    /**
     * 更新 IDF 缓存
     */
    private fun updateIdfCache(memories: List<HolographicMemory>) {
        _idfCache.clear()
        _totalDocs = memories.size

        // 统计每个词出现在多少文档中
        val docFreq = ConcurrentHashMap<String, Int>()
        for (memory in memories) {
            val tokens = tokenize(memory.content).keys
            for (token in tokens) {
                docFreq[token] = (docFreq[token] ?: 0) + 1
            }
        }

        // 计算 IDF
        val n = memories.size.toDouble()
        for ((token, freq) in docFreq) {
            _idfCache[token] = Math.log((n + 1) / (freq + 1)) + 1
        }

        _idfDirty = false
    }

    /**
     * TF-IDF 余弦相似度
     */
    private fun tfidfCosineSimilarity(
        queryTokens: Map<String, Int>,
        docTokens: Map<String, Int>): Double {
        val allTokens = (queryTokens.keys + docTokens.keys).toSet()
        if (allTokens.isEmpty()) return 0.0

        var dotProduct = 0.0
        var normQuery = 0.0
        var normDoc = 0.0

        for (token in allTokens) {
            val queryTf = (queryTokens[token] ?: 0).toDouble()
            val docTf = (docTokens[token] ?: 0).toDouble()
            val idf = _idfCache[token] ?: 1.0

            val queryTfidf = queryTf * idf
            val docTfidf = docTf * idf

            dotProduct += queryTfidf * docTfidf
            normQuery += queryTfidf * queryTfidf
            normDoc += docTfidf * docTfidf
        }

        val denominator = Math.sqrt(normQuery) * Math.sqrt(normDoc)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }

    /**
     * BM25 评分
     */
    private fun bm25Score(
        queryTokens: Map<String, Int>,
        docTokens: Map<String, Int>,
        totalDocs: Int): Double {
        val k1 = config.bm25K1
        val b = config.bm25B
        val avgDocLen = docTokens.values.sum().toDouble()

        var score = 0.0
        for ((token, queryTf) in queryTokens) {
            val docTf = docTokens[token] ?: 0
            val idf = _idfCache[token] ?: 1.0
            val docLen = docTokens.values.sum().toDouble()

            val tfNorm = (docTf * (k1 + 1)) / (docTf + k1 * (1 - b + b * docLen / avgDocLen))
            score += idf * tfNorm * queryTf
        }

        return score
    }

    /**
     * 时间衰减计算
     */
    private fun calculateTimeDecay(createdAt: Long): Double {
        val age = System.currentTimeMillis() - createdAt
        val days = age / (24 * 3600 * 1000.0)
        return Math.exp(-config.decayRate * days)
    }

    /**
     * 分词
     */
    private fun tokenize(text: String): Map<String, Int> {
        val tokens = mutableMapOf<String, Int>()
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s\\u4e00-\\u9fff]"), "") // 支持中文
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .forEach { token ->
                tokens[token] = (tokens[token] ?: 0) + 1
            }
        return tokens
    }

    /**
     * 余弦相似度
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

    /**
     * 标记 IDF 缓存为需要更新
     */
    fun markIdfDirty() {
        _idfDirty = true
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        _idfCache.clear()
        _totalDocs = 0
        _idfDirty = true
    }
}
