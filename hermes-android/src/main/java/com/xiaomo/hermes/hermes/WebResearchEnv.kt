package com.xiaomo.hermes.hermes

import java.net.URL
import java.util.regex.Pattern

/**
 * WebResearchEnv — Environment for multi-step web research training.
 *
 * Trains models to do accurate, efficient, multi-source web research.
 * Ported from hermes-agent/environments/web_research_env.py
 */

class WebResearchEnvConfig

class WebResearchEnv {

    private var config = WebResearchEnvConfig()

    fun configInit() {
        config = WebResearchEnvConfig()
    }

    suspend fun setup() {
        // Initialize research environment resources
    }

    suspend fun getNextItem(): Any? {
        return null
    }

    fun formatPrompt(item: String): String {
        return item
    }

    suspend fun computeReward(item: String, result: String, ctx: String): Double {
        return 0.0
    }

    suspend fun evaluate() {
        // Evaluate model on research tasks
    }

    suspend fun wandbLog(wandbMetrics: String) {
        // Log metrics to W&B (no-op on Android)
    }

    /** Use the server's LLM to judge answer correctness. */
    suspend fun _llmJudge(question: String, expected: String, modelAnswer: String): Double {
        if (modelAnswer.isBlank()) return 0.0

        val judgePrompt = buildString {
            appendLine("You are an impartial judge evaluating the quality of an AI research answer.")
            appendLine()
            appendLine("Question: $question")
            appendLine()
            appendLine("Reference answer: $expected")
            appendLine()
            appendLine("Model answer: $modelAnswer")
            appendLine()
            appendLine("Score the model answer on a scale from 0.0 to 1.0 where:")
            appendLine("  1.0 = fully correct and complete")
            appendLine("  0.7 = mostly correct with minor gaps")
            appendLine("  0.4 = partially correct")
            appendLine("  0.1 = mentions relevant topic but wrong or very incomplete")
            appendLine("  0.0 = completely wrong or no answer")
            appendLine()
            appendLine("Consider: factual accuracy, completeness, and relevance.")
            append("Respond with ONLY a JSON object: {\"score\": <float>, \"reason\": \"<one sentence>\"}")
        }

        // LLM call would go here via server.chat_completion
        // Fallback to heuristic if unavailable
        return _heuristicScore(expected, modelAnswer)
    }

    /** Extract the score float from LLM judge JSON response. */
    fun _parseJudgeJson(text: String): Double? {
        // Clean markdown code fences
        val clean = text.replace(Regex("```(?:json)?|```"), "").trim()

        // Try JSON parse first
        try {
            val data = org.json.JSONObject(clean)
            val score = data.optDouble("score", -1.0)
            if (score in 0.0..1.0) return score
        } catch (_: Exception) {
            // Try regex fallback
            val match = Regex("\"score\"\\s*:\\s*([0-9.]+)").find(text)
            if (match != null) {
                val score = match.groupValues[1].toDoubleOrNull()
                if (score != null && score in 0.0..1.0) return score
            }
        }
        return null
    }

    /** Lightweight keyword overlap score as fallback. */
    fun _heuristicScore(expected: String, modelAnswer: String): Double {
        val stopwords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "of", "in", "on",
            "at", "to", "for", "with", "and", "or", "but", "it", "its",
            "this", "that", "as", "by", "from", "be", "has", "have", "had")

        fun tokenize(text: String): Set<String> {
            return Regex("\\b\\w+\\b").findAll(text.lowercase())
                .map { it.value }
                .filter { it !in stopwords && it.length > 2 }
                .toSet()
        }

        val expectedTokens = tokenize(expected)
        val answerTokens = tokenize(modelAnswer)

        if (expectedTokens.isEmpty()) return 0.5

        val overlap = (expectedTokens intersect answerTokens).size
        val union = (expectedTokens union answerTokens).size

        val jaccard = if (union > 0) overlap.toDouble() / union else 0.0
        val recall = if (expectedTokens.isNotEmpty()) overlap.toDouble() / expectedTokens.size else 0.0

        return minOf(1.0, 0.4 * jaccard + 0.6 * recall)
    }

    /** Extract unique domains from URLs cited in the response. */
    fun _extractDomains(text: String): Set<String> {
        val urls = Regex("""https?://[^\s\)>\]"']+""").findAll(text)
        val domains = mutableSetOf<String>()
        for (match in urls) {
            try {
                val parsed = URL(match.value)
                val domain = parsed.host.lowercase().removePrefix("www.")
                if (domain.isNotEmpty()) domains.add(domain)
            } catch (_: Exception) {}
        }
        return domains
    }
}
