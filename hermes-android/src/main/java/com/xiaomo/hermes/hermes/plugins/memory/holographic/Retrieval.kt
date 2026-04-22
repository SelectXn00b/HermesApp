/** 1:1 对齐 hermes/plugins/memory/holographic/retrieval.py */
package com.xiaomo.hermes.hermes.plugins.memory.holographic

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Hybrid keyword/BM25 retrieval for the memory store.
 *
 * Ported from KIK memory_agent.py — combines FTS5 full-text search with
 * Jaccard similarity reranking and trust-weighted scoring.
 */

class FactRetriever(
    val store: MemoryStore,
    val halfLife: Int = 0, // days, 0 = disabled
    ftsWeight: Float = 0.4f,
    jaccardWeight: Float = 0.3f,
    hrrWeight: Float = 0.3f,
    val hrrDim: Int = 1024
) {
    var ftsWeight: Float = ftsWeight
        private set
    var jaccardWeight: Float = jaccardWeight
        private set
    var hrrWeight: Float = hrrWeight
        private set

    init {
        // Auto-redistribute weights if numpy (HRR) unavailable
        if (this.hrrWeight > 0 && !Holographic.hasNumpy) {
            this.ftsWeight = 0.6f
            this.jaccardWeight = 0.4f
            this.hrrWeight = 0.0f
        }
    }

    /**
     * Hybrid search: FTS5 candidates → Jaccard rerank → trust weighting.
     *
     * Pipeline:
     * 1. FTS5 search: Get limit*3 candidates from SQLite full-text search
     * 2. Jaccard boost: Token overlap between query and fact content
     * 3. Trust weighting: final_score = relevance * trust_score
     * 4. Temporal decay (optional): decay = 0.5^(age_days / half_life)
     *
     * Returns list of dicts with fact data + 'score' field, sorted by score desc.
     */
    fun search(
        query: String,
        category: String? = null,
        minTrust: Float = 0.3f,
        limit: Int = 10
    ): List<MutableMap<String, Any?>> {
        // Stage 1: Get FTS5 candidates (more than limit for reranking headroom)
        val candidates = _ftsCandidates(query, category, minTrust, limit * 3)

        if (candidates.isEmpty()) return emptyList()

        // Stage 2: Rerank with Jaccard + trust + optional decay
        val queryTokens = _tokenize(query)
        val scored = mutableListOf<MutableMap<String, Any?>>()

        for (fact in candidates) {
            val contentTokens = _tokenize(fact["content"] as? String ?: "")
            val tagTokens = _tokenize(fact["tags"] as? String ?: "")
            val allTokens = contentTokens + tagTokens

            val jaccard = _jaccardSimilarity(queryTokens, allTokens)
            val ftsScore = (fact["fts_rank"] as? Number)?.toFloat() ?: 0.0f

            // HRR similarity
            val hrrSim: Float
            if (hrrWeight > 0 && fact["hrr_vector"] != null) {
                val factVec = Holographic.bytesToPhases(fact["hrr_vector"] as ByteArray)
                val queryVec = Holographic.encodeText(query, hrrDim)
                hrrSim = (Holographic.similarity(queryVec, factVec) + 1.0f) / 2.0f
            } else {
                hrrSim = 0.5f // neutral
            }

            // Combine FTS5 + Jaccard + HRR
            val relevance = ftsWeight * ftsScore + jaccardWeight * jaccard + hrrWeight * hrrSim

            // Trust weighting
            val trustScore = (fact["trust_score"] as? Number)?.toFloat() ?: 1.0f
            var score = relevance * trustScore

            // Optional temporal decay
            if (halfLife > 0) {
                score *= _temporalDecay(
                    (fact["updated_at"] ?: fact["created_at"]) as? String
                )
            }

            fact["score"] = score
            scored.add(fact)
        }

        // Sort by score descending, return top limit
        scored.sortByDescending { (it["score"] as? Number)?.toFloat() ?: 0.0f }
        val results = scored.take(limit)
        // Strip raw HRR bytes — callers expect JSON-serializable dicts
        for (fact in results) {
            fact.remove("hrr_vector")
        }
        return results
    }

    /**
     * Compositional entity query using HRR algebra.
     *
     * Unbinds entity from memory bank to extract associated content.
     * Falls back to FTS5 search if numpy unavailable.
     */
    fun probe(
        entity: String,
        category: String? = null,
        limit: Int = 10
    ): List<MutableMap<String, Any?>> {
        if (!Holographic.hasNumpy) {
            return search(entity, category = category, limit = limit)
        }

        val conn = store._conn

        // Encode entity as role-bound vector
        val roleEntity = Holographic.encodeAtom("__hrr_role_entity__", hrrDim)
        val entityVec = Holographic.encodeAtom(entity.lowercase(), hrrDim)
        val probeKey = Holographic.bind(entityVec, roleEntity)

        // Try category-specific bank first, then all facts
        if (category != null) {
            val bankName = "cat:$category"
            val bankRow = conn.query(
                "SELECT vector FROM memory_banks WHERE bank_name = ?",
                arrayOf(bankName)
            )
            if (bankRow != null) {
                val bankVec = Holographic.bytesToPhases(bankRow)
                val extracted = Holographic.unbind(bankVec, probeKey)
                return _scoreFactsByVector(extracted, category = category, limit = limit)
            }
        }

        // Score against individual fact vectors directly
        val rows = _queryFactsWithVectors(category)

        if (rows.isEmpty()) {
            return search(entity, category = category, limit = limit)
        }

        val scored = mutableListOf<MutableMap<String, Any?>>()
        val roleContent = Holographic.encodeAtom("__hrr_role_content__", hrrDim)

        for (fact in rows) {
            val hrrVector = fact.remove("hrr_vector") as? ByteArray ?: continue
            val factVec = Holographic.bytesToPhases(hrrVector)
            val residual = Holographic.unbind(factVec, probeKey)
            val contentVec = Holographic.bind(
                Holographic.encodeText(fact["content"] as? String ?: "", hrrDim),
                roleContent
            )
            val sim = Holographic.similarity(residual, contentVec)
            val trustScore = (fact["trust_score"] as? Number)?.toFloat() ?: 1.0f
            fact["score"] = (sim + 1.0f) / 2.0f * trustScore
            scored.add(fact)
        }

        scored.sortByDescending { (it["score"] as? Number)?.toFloat() ?: 0.0f }
        return scored.take(limit)
    }

    /**
     * Discover facts that share structural connections with an entity.
     */
    fun related(
        entity: String,
        category: String? = null,
        limit: Int = 10
    ): List<MutableMap<String, Any?>> {
        if (!Holographic.hasNumpy) {
            return search(entity, category = category, limit = limit)
        }

        val entityVec = Holographic.encodeAtom(entity.lowercase(), hrrDim)
        val rows = _queryFactsWithVectors(category)

        if (rows.isEmpty()) {
            return search(entity, category = category, limit = limit)
        }

        val roleEntity = Holographic.encodeAtom("__hrr_role_entity__", hrrDim)
        val roleContent = Holographic.encodeAtom("__hrr_role_content__", hrrDim)

        val scored = mutableListOf<MutableMap<String, Any?>>()
        for (fact in rows) {
            val hrrVector = fact.remove("hrr_vector") as? ByteArray ?: continue
            val factVec = Holographic.bytesToPhases(hrrVector)
            val residual = Holographic.unbind(factVec, entityVec)
            val entityRoleSim = Holographic.similarity(residual, roleEntity)
            val contentRoleSim = Holographic.similarity(residual, roleContent)
            val bestSim = maxOf(entityRoleSim, contentRoleSim)
            val trustScore = (fact["trust_score"] as? Number)?.toFloat() ?: 1.0f
            fact["score"] = (bestSim + 1.0f) / 2.0f * trustScore
            scored.add(fact)
        }

        scored.sortByDescending { (it["score"] as? Number)?.toFloat() ?: 0.0f }
        return scored.take(limit)
    }

    /**
     * Multi-entity compositional query — vector-space JOIN.
     */
    fun reason(
        entities: List<String>,
        category: String? = null,
        limit: Int = 10
    ): List<MutableMap<String, Any?>> {
        if (!Holographic.hasNumpy || entities.isEmpty()) {
            val query = entities.joinToString(" ")
            return search(query, category = category, limit = limit)
        }

        val roleEntity = Holographic.encodeAtom("__hrr_role_entity__", hrrDim)
        val roleContent = Holographic.encodeAtom("__hrr_role_content__", hrrDim)

        val entityResiduals = entities.map { entity ->
            val entityVec = Holographic.encodeAtom(entity.lowercase(), hrrDim)
            Holographic.bind(entityVec, roleEntity)
        }

        val rows = _queryFactsWithVectors(category)

        if (rows.isEmpty()) {
            val query = entities.joinToString(" ")
            return search(query, category = category, limit = limit)
        }

        val scored = mutableListOf<MutableMap<String, Any?>>()
        for (fact in rows) {
            val hrrVector = fact.remove("hrr_vector") as? ByteArray ?: continue
            val factVec = Holographic.bytesToPhases(hrrVector)

            val entityScores = entityResiduals.map { probeKey ->
                val residual = Holographic.unbind(factVec, probeKey)
                Holographic.similarity(residual, roleContent)
            }

            val minSim = entityScores.minOrNull() ?: 0.0f
            val trustScore = (fact["trust_score"] as? Number)?.toFloat() ?: 1.0f
            fact["score"] = (minSim + 1.0f) / 2.0f * trustScore
            scored.add(fact)
        }

        scored.sortByDescending { (it["score"] as? Number)?.toFloat() ?: 0.0f }
        return scored.take(limit)
    }

    /**
     * Find potentially contradictory facts via entity overlap + content divergence.
     */
    fun contradict(
        category: String? = null,
        threshold: Float = 0.3f,
        limit: Int = 10
    ): List<Map<String, Any?>> {
        if (!Holographic.hasNumpy) return emptyList()

        val conn = store._conn
        var rows = _queryFactsWithVectors(category)

        if (rows.size < 2) return emptyList()

        val maxContradictFacts = 500
        if (rows.size > maxContradictFacts) {
            rows = rows.sortedByDescending {
                (it["updated_at"] ?: it["created_at"]) as? String ?: ""
            }.take(maxContradictFacts).toMutableList()
        }

        // Build entity sets per fact
        val factEntities = mutableMapOf<Any?, Set<String>>()
        for (row in rows) {
            val fid = row["fact_id"]
            val entityRows = conn.queryEntitiesForFact(fid)
            factEntities[fid] = entityRows.map { it.lowercase() }.toSet()
        }

        val facts = rows.map { it.toMutableMap() }
        val contradictions = mutableListOf<Map<String, Any?>>()

        for (i in facts.indices) {
            for (j in i + 1 until facts.size) {
                val f1 = facts[i]
                val f2 = facts[j]
                val ents1 = factEntities[f1["fact_id"]] ?: emptySet()
                val ents2 = factEntities[f2["fact_id"]] ?: emptySet()

                if (ents1.isEmpty() || ents2.isEmpty()) continue

                val intersection = ents1.intersect(ents2)
                val union = ents1.union(ents2)
                val entityOverlap = if (union.isNotEmpty()) intersection.size.toFloat() / union.size else 0.0f

                if (entityOverlap < 0.3f) continue

                val v1 = Holographic.bytesToPhases(f1["hrr_vector"] as ByteArray)
                val v2 = Holographic.bytesToPhases(f2["hrr_vector"] as ByteArray)
                val contentSim = Holographic.similarity(v1, v2)

                val contradictionScore = entityOverlap * (1.0f - (contentSim + 1.0f) / 2.0f)

                if (contradictionScore >= threshold) {
                    val f1Clean = f1.filterKeys { it != "hrr_vector" }
                    val f2Clean = f2.filterKeys { it != "hrr_vector" }
                    contradictions.add(
                        mapOf(
                            "fact_a" to f1Clean,
                            "fact_b" to f2Clean,
                            "entity_overlap" to "%.3f".format(entityOverlap).toFloat(),
                            "content_similarity" to "%.3f".format(contentSim).toFloat(),
                            "contradiction_score" to "%.3f".format(contradictionScore).toFloat(),
                            "shared_entities" to intersection.sorted()
                        )
                    )
                }
            }
        }

        return contradictions.sortedByDescending {
            (it["contradiction_score"] as? Number)?.toFloat() ?: 0.0f
        }.take(limit)
    }

    private fun _scoreFactsByVector(
        targetVec: FloatArray,
        category: String? = null,
        limit: Int = 10
    ): List<MutableMap<String, Any?>> {
        val rows = _queryFactsWithVectors(category)

        val scored = mutableListOf<MutableMap<String, Any?>>()
        for (fact in rows) {
            val hrrVector = fact.remove("hrr_vector") as? ByteArray ?: continue
            val factVec = Holographic.bytesToPhases(hrrVector)
            val sim = Holographic.similarity(targetVec, factVec)
            val trustScore = (fact["trust_score"] as? Number)?.toFloat() ?: 1.0f
            fact["score"] = (sim + 1.0f) / 2.0f * trustScore
            scored.add(fact)
        }

        scored.sortByDescending { (it["score"] as? Number)?.toFloat() ?: 0.0f }
        return scored.take(limit)
    }

    private fun _queryFactsWithVectors(category: String?): MutableList<MutableMap<String, Any?>> {
        // Placeholder for SQLite query — returns facts with hrr_vector
        return mutableListOf()
    }

    private fun _ftsCandidates(
        query: String,
        category: String?,
        minTrust: Float,
        limit: Int
    ): List<MutableMap<String, Any?>> {
        val conn = store._conn

        // Build query - FTS5 rank is negative (lower = better match)
        val params = mutableListOf<Any>()
        val whereClauses = mutableListOf("facts_fts MATCH ?")
        params.add(query)

        if (category != null) {
            whereClauses.add("f.category = ?")
            params.add(category)
        }

        whereClauses.add("f.trust_score >= ?")
        params.add(minTrust)

        val whereSql = whereClauses.joinToString(" AND ")

        val sql = """
            SELECT f.*, facts_fts.rank as fts_rank_raw
            FROM facts_fts
            JOIN facts f ON f.fact_id = facts_fts.rowid
            WHERE $whereSql
            ORDER BY facts_fts.rank
            LIMIT ?
        """.trimIndent()
        params.add(limit)

        val rows: List<Map<String, Any?>>
        try {
            rows = conn.executeQuery(sql, params)
        } catch (e: Exception) {
            return emptyList()
        }

        if (rows.isEmpty()) return emptyList()

        val rawRanks = rows.map { abs((it["fts_rank_raw"] as? Number)?.toFloat() ?: 0.0f) }
        val maxRank = max(rawRanks.maxOrNull() ?: 1.0f, 1e-6f)

        return rows.zip(rawRanks).map { (row, rawRank) ->
            val fact = row.toMutableMap()
            fact.remove("fts_rank_raw")
            fact["fts_rank"] = rawRank / maxRank
            fact
        }
    }

    companion object {
        fun _tokenize(text: String): Set<String> {
            if (text.isEmpty()) return emptySet()
            return text.lowercase().split(Regex("\\s+"))
                .map { it.trim(*".,;:!?\"'()[]{}#@<>".toCharArray()) }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        fun _jaccardSimilarity(setA: Set<String>, setB: Set<String>): Float {
            if (setA.isEmpty() || setB.isEmpty()) return 0.0f
            val intersection = setA.intersect(setB).size
            val union = setA.union(setB).size
            return if (union > 0) intersection.toFloat() / union else 0.0f
        }
    }

    private fun _temporalDecay(timestampStr: String?): Float {
        if (halfLife <= 0 || timestampStr == null) return 1.0f

        return try {
            val ts = Instant.parse(timestampStr.replace("Z", "+00:00").let {
                if (!it.contains("T")) "${it}T00:00:00Z" else it
            })
            val ageDays = (Instant.now().epochSecond - ts.epochSecond).toFloat() / 86400f
            if (ageDays < 0) 1.0f
            else 0.5f.pow(ageDays / halfLife)
        } catch (e: Exception) {
            1.0f
        }
    }

    private fun Float.pow(exp: Float): Float = Math.pow(this.toDouble(), exp.toDouble()).toFloat()
}
