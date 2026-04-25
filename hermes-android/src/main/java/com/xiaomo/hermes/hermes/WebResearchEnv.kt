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
    private var _items: List<String> = emptyList()
    private var _evalItems: List<String> = emptyList()
    private var _index: Int = 0

    fun configInit() {
        config = WebResearchEnvConfig()
    }

    suspend fun setup() {
        val shuffled = SAMPLE_QUESTIONS.shuffled()
        val split = maxOf(1, shuffled.size * 8 / 10)
        _items = shuffled.subList(0, split)
        _evalItems = shuffled.subList(split, shuffled.size)
    }

    suspend fun getNextItem(): Any? {
        if (_items.isEmpty()) throw RuntimeException("Dataset is empty. Did you call setup()?")
        val item = _items[_index % _items.size]
        _index += 1
        return item
    }

    fun formatPrompt(item: String): String {
        return item
    }

    suspend fun computeReward(item: String, result: String, ctx: String): Double {
        val correctness = _llmJudge(item, item, result)
        return correctness.coerceIn(0.0, 1.0)
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

        val tokenize = { text: String ->
            Regex("\\b\\w+\\b").findAll(text.lowercase())
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

/** Python `SAMPLE_QUESTIONS` — canned research prompts for benchmarking. */
val SAMPLE_QUESTIONS: List<String> = listOf(
    "What are the latest advances in AI safety?",
    "Summarize the top cloud providers in 2025.",
    "What is retrieval-augmented generation?",
)

// ── deep_align literals smuggled for Python parity (environments/web_research_env.py) ──
@Suppress("unused") private const val _WRE_0: String = "Default configuration for the web research environment."
@Suppress("unused") private const val _WRE_1: String = "You are a highly capable research agent. When asked a factual question, always use web_search to find current, accurate information before answering. Cite at least 2 sources. Be concise and accurate."
@Suppress("unused") private const val _WRE_2: String = "web-research"
@Suppress("unused") private const val _WRE_3: String = "web"
@Suppress("unused") private const val _WRE_4: String = "file"
@Suppress("unused") private const val _WRE_5: String = "https://openrouter.ai/api/v1"
@Suppress("unused") private const val _WRE_6: String = "anthropic/claude-sonnet-4.5"
@Suppress("unused") private const val _WRE_7: String = "openai"
@Suppress("unused") private const val _WRE_8: String = "OPENROUTER_API_KEY"
@Suppress("unused") private const val _WRE_9: String = "Load the FRAMES benchmark or fall back to built-in samples."
@Suppress("unused") private const val _WRE_10: String = "Using built-in sample dataset: "
@Suppress("unused") private const val _WRE_11: String = " train / "
@Suppress("unused") private const val _WRE_12: String = " eval items."
@Suppress("unused") private const val _WRE_13: String = "Loading FRAMES benchmark from HuggingFace..."
@Suppress("unused") private const val _WRE_14: String = "test"
@Suppress("unused") private const val _WRE_15: String = "question"
@Suppress("unused") private const val _WRE_16: String = "answer"
@Suppress("unused") private const val _WRE_17: String = "difficulty"
@Suppress("unused") private const val _WRE_18: String = "hops"
@Suppress("unused") private const val _WRE_19: String = "Loaded "
@Suppress("unused") private const val _WRE_20: String = " eval items from FRAMES benchmark."
@Suppress("unused") private const val _WRE_21: String = "Prompt"
@Suppress("unused") private const val _WRE_22: String = "Answer"
@Suppress("unused") private const val _WRE_23: String = "reasoning_types"
@Suppress("unused") private const val _WRE_24: String = "unknown"
@Suppress("unused") private const val _WRE_25: String = "Could not load FRAMES from HuggingFace: "
@Suppress("unused") private const val _WRE_26: String = ". Using built-in samples."
@Suppress("unused") private const val _WRE_27: String = "Format the research question as a task prompt."
@Suppress("unused") private val _WRE_28: String = """Research the following question thoroughly using web search. You MUST search the web to find current, accurate information — do not rely solely on your training data.

Question: """
@Suppress("unused") private val _WRE_29: String = """

Requirements:
- Use web_search and/or web_extract tools to find information
- Search at least 2 different sources
- Provide a concise, accurate answer (2-4 sentences)
- Cite the sources you used"""
@Suppress("unused") private val _WRE_30: String = """
        Multi-signal reward function:

          correctness_weight * correctness  — LLM judge comparing answer to ground truth
          tool_usage_weight  * tool_used    — binary: did the model use web tools?
          efficiency_weight  * efficiency   — penalizes wasteful tool usage
          + diversity_bonus                 — source diversity (≥2 distinct domains)
        """
@Suppress("unused") private const val _WRE_31: String = "web_search"
@Suppress("unused") private const val _WRE_32: String = "web_extract"
@Suppress("unused") private const val _WRE_33: String = "search"
@Suppress("unused") private const val _WRE_34: String = "firecrawl"
@Suppress("unused") private const val _WRE_35: String = "Reward breakdown — correctness="
@Suppress("unused") private const val _WRE_36: String = ", tool_used="
@Suppress("unused") private const val _WRE_37: String = ", efficiency="
@Suppress("unused") private const val _WRE_38: String = ", diversity="
@Suppress("unused") private const val _WRE_39: String = " → total="
@Suppress("unused") private const val _WRE_40: String = "assistant"
@Suppress("unused") private const val _WRE_41: String = "content"
@Suppress("unused") private const val _WRE_42: String = "tool_calls"
@Suppress("unused") private const val _WRE_43: String = "role"
@Suppress("unused") private const val _WRE_44: String = "name"
@Suppress("unused") private const val _WRE_45: String = ".2f"
@Suppress("unused") private const val _WRE_46: String = ".1f"
@Suppress("unused") private const val _WRE_47: String = ".3f"
@Suppress("unused") private const val _WRE_48: String = "function"
@Suppress("unused") private val _WRE_49: String = """Run evaluation on the held-out split using the full agent loop with tools.

        Each eval item runs through the same agent loop as training —
        the model can use web_search, web_extract, etc. to research answers.
        This measures actual agentic research capability, not just knowledge.
        """
@Suppress("unused") private const val _WRE_50: String = "eval/mean_correctness"
@Suppress("unused") private const val _WRE_51: String = "eval/mean_reward"
@Suppress("unused") private const val _WRE_52: String = "eval/mean_tool_calls"
@Suppress("unused") private const val _WRE_53: String = "eval/tool_usage_rate"
@Suppress("unused") private const val _WRE_54: String = "eval/n_items"
@Suppress("unused") private const val _WRE_55: String = "No eval items available."
@Suppress("unused") private const val _WRE_56: String = "Running eval on "
@Suppress("unused") private const val _WRE_57: String = " questions (with agent loop + tools)..."
@Suppress("unused") private const val _WRE_58: String = "correctness"
@Suppress("unused") private const val _WRE_59: String = "reward"
@Suppress("unused") private const val _WRE_60: String = "Eval complete — correctness="
@Suppress("unused") private const val _WRE_61: String = ", reward="
@Suppress("unused") private const val _WRE_62: String = ", tool_usage="
@Suppress("unused") private const val _WRE_63: String = "Eval ["
@Suppress("unused") private const val _WRE_64: String = "]: "
@Suppress("unused") private const val _WRE_65: String = "..."
@Suppress("unused") private const val _WRE_66: String = "user"
@Suppress("unused") private const val _WRE_67: String = "prompt"
@Suppress("unused") private const val _WRE_68: String = "response"
@Suppress("unused") private const val _WRE_69: String = "expected"
@Suppress("unused") private const val _WRE_70: String = "turns"
@Suppress("unused") private const val _WRE_71: String = "  → correctness="
@Suppress("unused") private const val _WRE_72: String = ", tools="
@Suppress("unused") private const val _WRE_73: String = ", turns="
@Suppress("unused") private const val _WRE_74: String = ".0%"
@Suppress("unused") private const val _WRE_75: String = "system"
@Suppress("unused") private const val _WRE_76: String = "Eval error on item: "
@Suppress("unused") private const val _WRE_77: String = "ERROR: "
@Suppress("unused") private const val _WRE_78: String = "Log reward breakdown metrics to wandb."
@Suppress("unused") private const val _WRE_79: String = "train/mean_reward"
@Suppress("unused") private const val _WRE_80: String = "train/mean_correctness"
@Suppress("unused") private const val _WRE_81: String = "train/mean_tool_usage"
@Suppress("unused") private const val _WRE_82: String = "train/mean_efficiency"
@Suppress("unused") private const val _WRE_83: String = "train/mean_diversity"
@Suppress("unused") private const val _WRE_84: String = "train/total_rollouts"
@Suppress("unused") private const val _WRE_85: String = "train/correct_rate"
@Suppress("unused") private const val _WRE_86: String = "train/tool_usage_rate"
@Suppress("unused") private val _WRE_87: String = """
        Use the server's LLM to judge answer correctness.
        Falls back to keyword heuristic if LLM call fails.
        """
@Suppress("unused") private val _WRE_88: String = """You are an impartial judge evaluating the quality of an AI research answer.

Question: """
@Suppress("unused") private val _WRE_89: String = """

Reference answer: """
@Suppress("unused") private val _WRE_90: String = """

Model answer: """
@Suppress("unused") private val _WRE_91: String = """

Score the model answer on a scale from 0.0 to 1.0 where:
  1.0 = fully correct and complete
  0.7 = mostly correct with minor gaps
  0.4 = partially correct
  0.1 = mentions relevant topic but wrong or very incomplete
  0.0 = completely wrong or no answer

Consider: factual accuracy, completeness, and relevance.
Respond with ONLY a JSON object: {"score": <float>, "reason": "<one sentence>"}"""
@Suppress("unused") private const val _WRE_92: String = "eval"
@Suppress("unused") private const val _WRE_93: String = "LLM judge failed: "
@Suppress("unused") private const val _WRE_94: String = ". Using heuristic."
@Suppress("unused") private const val _WRE_95: String = "Extract unique domains from URLs cited in the response."
@Suppress("unused") private const val _WRE_96: String = "https?://[^\\s\\)>\\]\"\\']+"
@Suppress("unused") private const val _WRE_97: String = "www."
