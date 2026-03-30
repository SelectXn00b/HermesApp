package com.xiaomo.androidforclaw.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/mmr.ts (MMRConfig, DEFAULT_MMR_CONFIG, mmrRerank, tokenize, jaccardSimilarity)
 *
 * AndroidForClaw adaptation: Maximal Marginal Relevance re-ranking.
 * Improves diversity in search results by penalizing redundancy.
 */

/**
 * MMR configuration.
 * Aligned with OpenClaw MMRConfig.
 */
data class MMRConfig(
    /** Lambda: 1.0 = pure relevance, 0.0 = pure diversity */
    val lambda: Float = 0.7f,
    /** Whether MMR re-ranking is enabled */
    val enabled: Boolean = false
)

/** Default MMR configuration. Aligned with OpenClaw DEFAULT_MMR_CONFIG (enabled=false). */
val DEFAULT_MMR_CONFIG = MMRConfig(lambda = 0.7f, enabled = false)

/**
 * Temporal decay configuration for memory search results.
 */
data class TemporalDecayConfig(
    val enabled: Boolean = false,
    val halfLifeDays: Float = 7f
)

val DEFAULT_TEMPORAL_DECAY_CONFIG = TemporalDecayConfig()

/**
 * MMRReranker — Maximal Marginal Relevance re-ranking for search results.
 * Aligned with OpenClaw mmr.ts.
 *
 * MMR balances relevance and diversity:
 * MMR = λ * sim(d, q) - (1-λ) * max(sim(d, d_j)) for d_j in selected
 */
object MMRReranker {

    /**
     * Tokenize text into a set of lowercase word tokens.
     * Aligned with OpenClaw tokenize (word-level, not trigrams).
     */
    fun tokenize(text: String): Set<String> {
        val matches = Regex("[a-z0-9_]+").findAll(text.lowercase())
        return matches.map { it.value }.toSet()
    }

    /**
     * Jaccard similarity between two token sets.
     * Aligned with OpenClaw jaccardSimilarity.
     */
    fun jaccardSimilarity(setA: Set<String>, setB: Set<String>): Float {
        if (setA.isEmpty() && setB.isEmpty()) return 1f
        if (setA.isEmpty() || setB.isEmpty()) return 0f

        // Iterate the smaller set for efficiency
        val (smaller, larger) = if (setA.size <= setB.size) setA to setB else setB to setA
        val intersectionSize = smaller.count { it in larger }
        val unionSize = setA.size + setB.size - intersectionSize

        return if (unionSize > 0) intersectionSize.toFloat() / unionSize else 0f
    }

    /**
     * Text similarity via Jaccard on word tokens.
     * Aligned with OpenClaw textSimilarity.
     */
    fun textSimilarity(contentA: String, contentB: String): Float {
        return jaccardSimilarity(tokenize(contentA), tokenize(contentB))
    }

    /**
     * Compute MMR score.
     * Aligned with OpenClaw computeMMRScore.
     */
    fun computeMMRScore(relevance: Float, maxSimilarity: Float, lambda: Float): Float {
        return lambda * relevance - (1f - lambda) * maxSimilarity
    }

    /**
     * Apply MMR re-ranking to search results.
     * Aligned with OpenClaw mmrRerank.
     *
     * Key differences from previous version:
     * - Uses word-level Jaccard (not trigrams)
     * - Normalizes scores to [0,1] range
     * - lambda=1 short-circuits to pure relevance sort
     */
    fun <T> apply(
        results: List<T>,
        config: MMRConfig = DEFAULT_MMR_CONFIG,
        maxResults: Int = results.size,
        scoreSelector: (T) -> Float,
        snippetSelector: (T) -> String,
        copyWithScore: (T, Float) -> T
    ): List<T> {
        if (!config.enabled || results.size <= 1) return results.take(maxResults)

        val lambda = config.lambda.coerceIn(0f, 1f)

        // lambda=1: pure relevance, no diversity needed
        if (lambda == 1f) {
            return results.sortedByDescending { scoreSelector(it) }.take(maxResults)
        }

        // Pre-tokenize all items
        val tokenCache = HashMap<Int, Set<String>>(results.size)
        for (i in results.indices) {
            tokenCache[i] = tokenize(snippetSelector(results[i]))
        }

        // Normalize scores to [0,1]
        val scores = results.map { scoreSelector(it) }
        val minScore = scores.min()
        val maxScore = scores.max()
        val scoreRange = maxScore - minScore
        val normalizedScores = if (scoreRange > 0f) {
            scores.map { (it - minScore) / scoreRange }
        } else {
            scores.map { 1f }
        }

        val selected = mutableListOf<Int>()  // indices
        val remaining = results.indices.toMutableList()

        while (selected.size < maxResults && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestMmrScore = Float.NEGATIVE_INFINITY
            var bestOriginalScore = Float.NEGATIVE_INFINITY

            for (i in remaining) {
                val relevance = normalizedScores[i]

                // Max similarity to already-selected items
                val maxSim = if (selected.isEmpty()) {
                    0f
                } else {
                    selected.maxOf { selIdx ->
                        jaccardSimilarity(tokenCache[i]!!, tokenCache[selIdx]!!)
                    }
                }

                val mmrScore = computeMMRScore(relevance, maxSim, lambda)

                // Tiebreaker: original score
                if (mmrScore > bestMmrScore ||
                    (mmrScore == bestMmrScore && scores[i] > bestOriginalScore)) {
                    bestMmrScore = mmrScore
                    bestOriginalScore = scores[i]
                    bestIdx = i
                }
            }

            if (bestIdx >= 0) {
                selected.add(bestIdx)
                remaining.remove(bestIdx)
            } else {
                break
            }
        }

        return selected.map { idx -> copyWithScore(results[idx], scores[idx]) }
    }

    /**
     * Apply temporal decay to a score.
     */
    fun applyTemporalDecay(
        score: Float,
        ageMs: Long,
        config: TemporalDecayConfig = DEFAULT_TEMPORAL_DECAY_CONFIG
    ): Float {
        if (!config.enabled || ageMs <= 0) return score
        val ageDays = ageMs / (24 * 3600 * 1000f)
        val decayFactor = Math.pow(0.5, (ageDays / config.halfLifeDays).toDouble()).toFloat()
        return score * decayFactor
    }
}
