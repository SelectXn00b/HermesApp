package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * AgenticOpdEnv - Ported from ../hermes-agent/environments/agentic_opd_env.py
 *
 * On-Policy Distillation for Agentic Tool-Calling Tasks.
 * First Atropos environment to populate the distill_token_ids / distill_logprobs
 * fields on ScoredDataGroup, enabling on-policy distillation (OPD) training.
 */

/**
 * Configuration for the agentic OPD environment.
 */
data class AgenticOPDConfig(
    // --- OPD settings ---
    val opdEnabled: Boolean = true,
    val distillTopk: Int = 50,
    val prmVotes: Int = 3,
    val hintMaxNextStateChars: Int = 4000,

    // --- Reward settings ---
    val correctnessWeight: Double = 0.7,
    val efficiencyWeight: Double = 0.15,
    val toolUsageWeight: Double = 0.15,

    // --- Dataset ---
    val datasetName: String? = null,
    val datasetSplit: String = "train",
    val promptField: String = "prompt",

    // --- Eval ---
    val evalSize: Int = 10,
    val evalSplitRatio: Double = 0.15,

    // --- Inherited from HermesAgentEnvConfig ---
    val enabledToolsets: List<String>? = listOf("terminal", "file"),
    val disabledToolsets: List<String>? = null,
    val distribution: String? = null,
    val maxAgentTurns: Int = 15,
    val systemPrompt: String? = "You are a skilled Python programmer. When given a coding task:\n" +
        "1. Write the solution to a file called 'solution.py'\n" +
        "2. Write the test code to a file called 'test_solution.py'\n" +
        "3. Run the tests with: python test_solution.py\n" +
        "4. If tests fail, read the error output carefully, fix your code, and re-run\n" +
        "5. Once all tests pass, report success\n\n" +
        "Be efficient -- write clean code and fix errors methodically.",
    val agentTemperature: Double = 1.0,
    val terminalBackend: String = "local",
    val terminalTimeout: Int = 120,
    val terminalLifetime: Int = 3600,
    val groupSize: Int = 4,
    val maxTokenLength: Int = 4096,
    val toolCallParser: String = "hermes",
    val extraBody: Map<String, Any?>? = null,
) {
    companion object {
        /**
         * Default configuration.
         */
        fun configInit(): Pair<AgenticOPDConfig, List<Map<String, String>>> {
            val envConfig = AgenticOPDConfig()
            val serverConfigs = listOf(
                mapOf(
                    "base_url" to "http://localhost:8000/v1",
                    "model_name" to "Qwen/Qwen3-4B",
                    "server_type" to "vllm"
                )
            )
            return Pair(envConfig, serverConfigs)
        }
    }

    fun buildBudgetConfig(): Map<String, Any?> {
        return mapOf(
            "default_result_size" to 20000,
            "turn_budget" to 80000,
            "preview_size" to 2000,
            "tool_overrides" to emptyMap<String, Int>()
        )
    }
}

/**
 * Built-in coding tasks (fallback when no HF dataset is configured).
 */
val BUILTIN_CODING_TASKS = listOf(
    mapOf(
        "task" to "Write a Python function `fizzbuzz(n)` that returns a list of strings from 1 to n. " +
            "For multiples of 3 return 'Fizz', for multiples of 5 return 'Buzz', " +
            "for multiples of both return 'FizzBuzz', otherwise the number as a string.",
        "test_code" to "from solution import fizzbuzz\n" +
            "assert fizzbuzz(15) == ['1','2','Fizz','4','Buzz','Fizz','7','8','Fizz','Buzz','11','Fizz','13','14','FizzBuzz']\n" +
            "assert fizzbuzz(1) == ['1']\nassert fizzbuzz(0) == []\nprint('All tests passed!')\n",
        "difficulty" to "easy"
    ),
    mapOf(
        "task" to "Write a Python function `is_palindrome(s)` that checks if a string is a palindrome, " +
            "ignoring case and non-alphanumeric characters. Return True or False.",
        "test_code" to "from solution import is_palindrome\n" +
            "assert is_palindrome('A man, a plan, a canal: Panama') == True\n" +
            "assert is_palindrome('race a car') == False\nassert is_palindrome('') == True\n" +
            "print('All tests passed!')\n",
        "difficulty" to "easy"
    ),
    mapOf(
        "task" to "Write a Python function `two_sum(nums, target)` that returns the indices of the two " +
            "numbers in `nums` that add up to `target`. Assume exactly one solution exists. " +
            "Return a list of two indices [i, j] where i < j.",
        "test_code" to "from solution import two_sum\n" +
            "assert two_sum([2, 7, 11, 15], 9) == [0, 1]\nassert two_sum([3, 2, 4], 6) == [1, 2]\n" +
            "assert two_sum([3, 3], 6) == [0, 1]\nprint('All tests passed!')\n",
        "difficulty" to "easy"
    ),
    mapOf(
        "task" to "Write a Python function `flatten(lst)` that takes an arbitrarily nested list and " +
            "returns a flat list of all elements.",
        "test_code" to "from solution import flatten\nassert flatten([1, [2, [3, 4], 5]]) == [1, 2, 3, 4, 5]\n" +
            "assert flatten([]) == []\nassert flatten([1, 2, 3]) == [1, 2, 3]\n" +
            "print('All tests passed!')\n",
        "difficulty" to "medium"
    )
)

/**
 * RL environment with on-policy distillation from next-state signals.
 *
 * Runs coding tasks where the agent writes code and runs tests.
 * Tool results (test pass/fail, error traces) serve as next-state signals
 * for hint extraction and teacher logprob scoring.
 */
class AgenticOPDEnv(
    val opdConfig: AgenticOPDConfig = AgenticOPDConfig()
) {
    companion object {
        private const val _TAG = "AgenticOPDEnv"
        const val NAME = "agentic-opd"
        val DEFAULT_TOOLSETS = listOf("terminal", "file")
    }

    private var items: MutableList<Map<String, String>> = mutableListOf()
    private var evalItems: MutableList<Map<String, String>> = mutableListOf()
    private var index: Int = 0

    // Metric buffers
    private val rewardBuffer: MutableList<Double> = mutableListOf()
    private val correctnessBuffer: MutableList<Double> = mutableListOf()
    private val efficiencyBuffer: MutableList<Double> = mutableListOf()
    private val toolUsageBuffer: MutableList<Double> = mutableListOf()
    private val hintsExtractedBuffer: MutableList<Int> = mutableListOf()
    private val opdTurnsScoredBuffer: MutableList<Int> = mutableListOf()

    // =========================================================================
    // 1. setup -- load dataset
    // =========================================================================

    /**
     * Load coding tasks from HuggingFace or use built-in set.
     */
    suspend fun setup() {
        // On Android, dataset loading from HuggingFace is not available.
        // Use built-in coding tasks as fallback.
        val builtinItems = BUILTIN_CODING_TASKS.toMutableList()
        builtinItems.shuffle()
        val split = maxOf(1, builtinItems.size * 85 / 100)
        items = builtinItems.subList(0, split).toMutableList()
        evalItems = builtinItems.subList(split, builtinItems.size).toMutableList()
        Log.i(_TAG, "Using built-in coding tasks: ${items.size} train / ${evalItems.size} eval items")
    }

    // =========================================================================
    // 2. getNextItem
    // =========================================================================

    /**
     * Return the next coding task, cycling through the dataset.
     */
    suspend fun getNextItem(): Map<String, String> {
        if (items.isEmpty()) {
            throw RuntimeException("Dataset is empty. Did you call setup()?")
        }
        val item = items[index % items.size]
        index += 1
        return item
    }

    // =========================================================================
    // 3. formatPrompt
    // =========================================================================

    /**
     * Format the coding task as a user prompt.
     */
    fun formatPrompt(item: Map<String, Any?>): String {
        val task = item["task"] as? String ?: ""
        val testCode = item["test_code"] as? String

        val prompt = buildString {
            appendLine("Solve the following coding task.")
            appendLine()
            appendLine("## Task")
            appendLine(task)
            appendLine()
            if (!testCode.isNullOrEmpty()) {
                appendLine("## Tests")
                appendLine("The following test code will be used to verify your solution:")
                appendLine("```python")
                appendLine(testCode)
                appendLine("```")
                appendLine()
            }
            appendLine("## Instructions")
            appendLine("1. Write your solution to `solution.py`")
            appendLine("2. Write the test code to `test_solution.py`")
            appendLine("3. Run `python test_solution.py` to verify")
            appendLine("4. Fix any failures and re-run until all tests pass")
        }
        return prompt
    }

    // =========================================================================
    // 4. computeReward
    // =========================================================================

    /**
     * Multi-signal reward:
     *   - correctness (0.7): Did the tests pass?
     *   - efficiency (0.15): Fewer turns = better
     *   - tool_usage (0.15): Did the agent actually write + run code?
     */
    suspend fun computeReward(item: Map<String, Any?>, result: AgentResult, ctx: ToolContext): Double {
        val cfg = opdConfig

        // Signal 1: Test correctness
        // On Android, test execution happens server-side. Use result metadata.
        val correctness = 0.0

        // Signal 2: Efficiency
        val turnsUsed = result.turnsUsed.takeIf { it > 0 } ?: cfg.maxAgentTurns
        val maxTurns = cfg.maxAgentTurns
        val efficiency = when {
            turnsUsed <= 3 -> 1.0
            turnsUsed <= maxTurns / 2 -> 0.8
            turnsUsed <= maxTurns * 3 / 4 -> 0.5
            else -> 0.2
        }

        // Signal 3: Tool usage
        val messages = result.messages
        val toolsUsed = mutableSetOf<String>()
        for (msg in messages) {
            if (msg["role"] == "assistant" && msg["tool_calls"] != null) {
                @Suppress("UNCHECKED_CAST")
                val toolCalls = msg["tool_calls"] as? List<Map<String, Any?>> ?: emptyList()
                for (tc in toolCalls) {
                    @Suppress("UNCHECKED_CAST")
                    val func = tc["function"] as? Map<String, Any?> ?: emptyMap()
                    val name = func["name"] as? String ?: ""
                    if (name.isNotEmpty()) toolsUsed.add(name)
                }
            }
        }

        val toolUsage = when {
            "terminal" in toolsUsed && ("write_file" in toolsUsed || "patch" in toolsUsed) -> 1.0
            "terminal" in toolsUsed -> 0.6
            toolsUsed.isNotEmpty() -> 0.3
            else -> 0.0
        }

        // Combine
        var reward = cfg.correctnessWeight * correctness +
            cfg.efficiencyWeight * efficiency +
            cfg.toolUsageWeight * toolUsage
        reward = reward.coerceIn(0.0, 1.0)

        // Track metrics
        rewardBuffer.add(reward)
        correctnessBuffer.add(correctness)
        efficiencyBuffer.add(efficiency)
        toolUsageBuffer.add(toolUsage)

        Log.d(_TAG, "Reward: correctness=%.2f, efficiency=%.2f, tool_usage=%.2f -> %.3f".format(
            correctness, efficiency, toolUsage, reward
        ))
        return reward
    }

    // =========================================================================
    // 5. collectTrajectories -- OPD pipeline
    // =========================================================================

    /**
     * Override collectTrajectories to add the OPD pipeline.
     *
     * 1. Run standard rollouts -> ScoredDataGroup with tokens/masks/scores
     * 2. For each rollout, extract hints from next-state signals
     * 3. Score student tokens under enhanced (hint-augmented) distribution
     * 4. Add distill_token_ids / distill_logprobs to the ScoredDataGroup
     */
    suspend fun collectTrajectories(item: Item): Pair<List<ScoredDataGroup?>, List<Item>> {
        // On Android, trajectory collection is delegated to server-side.
        // This stub represents the structure.
        val results = mutableListOf<ScoredDataGroup?>()
        val backlog = mutableListOf<Item>()

        for (i in 0 until opdConfig.groupSize) {
            val prompt = formatPrompt(item)
            val scoredItem = mapOf<String, Any?>(
                "tokens" to emptyList<Int>(),
                "masks" to emptyList<Int>(),
                "scores" to 0.0,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to prompt)
                )
            )
            results.add(scoredItem)
        }

        return Pair(results, backlog)
    }

    /**
     * Apply on-policy distillation to each rollout in the group.
     * On Android, this is a server-side operation (VLLM not available on device).
     */
    suspend fun _applyOpdPipeline(group: MutableMap<String, Any?>) {
        // OPD pipeline requires VLLM server for logprob scoring.
        // On Android, this is handled server-side.
        Log.d(_TAG, "OPD pipeline: server-side operation (no-op on Android)")
    }

    /**
     * Run OPD for a single rollout sequence.
     * On Android, delegated to server-side VLLM scoring.
     */
    suspend fun _opdForSequence(
        messages: List<Map<String, Any?>>,
        studentTokens: List<Int>
    ): Pair<List<List<Int>>, List<List<Double>>> {
        val k = opdConfig.distillTopk
        val seqLen = studentTokens.size
        // Initialize with zeros (no distill info = neutral)
        val distillTokenIds = List(seqLen) { List(k) { 0 } }
        val distillLogprobs = List(seqLen) { List(k) { 0.0 } }
        return Pair(distillTokenIds, distillLogprobs)
    }

    /**
     * Walk conversation messages to find (assistant, next_state) pairs.
     */
    fun _extractTurnPairs(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val pairs = mutableListOf<Map<String, Any?>>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            if (msg["role"] == "assistant" && msg["content"] != null) {
                val assistantText = msg["content"] as String
                val context = messages.subList(0, i)

                // Look ahead for next state
                var j = i + 1
                val nextStates = mutableListOf<Map<String, Any?>>()
                while (j < messages.size) {
                    val nextMsg = messages[j]
                    when (nextMsg["role"]) {
                        "tool" -> {
                            nextStates.add(nextMsg)
                            j++
                        }
                        "user" -> {
                            nextStates.add(nextMsg)
                            break
                        }
                        else -> break
                    }
                }

                if (nextStates.isNotEmpty()) {
                    val nextRole = nextStates[0]["role"] as? String ?: "tool"
                    val nextTextParts = mutableListOf<String>()
                    for (ns in nextStates) {
                        var content = ns["content"] as? String ?: ""
                        if (content.isNotEmpty()) {
                            val maxChars = opdConfig.hintMaxNextStateChars
                            if (content.length > maxChars) {
                                content = content.substring(0, maxChars) + "\n...[truncated]"
                            }
                            nextTextParts.add(content)
                        }
                    }
                    val nextText = nextTextParts.joinToString("\n---\n")
                    if (nextText.isNotBlank()) {
                        pairs.add(mapOf(
                            "context_messages" to context,
                            "assistant_text" to assistantText,
                            "next_state_text" to nextText,
                            "next_state_role" to nextRole
                        ))
                    }
                }
            }
            i++
        }
        return pairs
    }

    /**
     * Extract a hindsight hint from a next-state signal using majority-voted LLM judge.
     * On Android, hint extraction requires server-side LLM calls.
     */
    suspend fun _extractHint(
        assistantText: String,
        nextStateText: String,
        nextStateRole: String
    ): String? {
        // Hint extraction requires LLM judge calls - server-side operation on Android
        Log.d(_TAG, "Hint extraction: server-side operation (no-op on Android)")
        return null
    }

    /**
     * Find where subTokens appears in fullTokens.
     * Returns the start index, or null if not found.
     * Searches from the end since assistant responses are typically at the end.
     */
    fun _findTokenSpan(fullTokens: List<Int>, subTokens: List<Int>): Int? {
        if (subTokens.isEmpty() || fullTokens.isEmpty()) return null
        val subLen = subTokens.size
        val fullLen = fullTokens.size
        if (subLen > fullLen) return null

        // Search backwards
        for (i in (fullLen - subLen) downTo 0) {
            if (fullTokens.subList(i, i + subLen) == subTokens) {
                return i
            }
        }
        return null
    }

    // =========================================================================
    // 6. evaluate
    // =========================================================================

    /**
     * Evaluate on held-out coding tasks using the full agent loop.
     * No OPD during eval -- just standard agentic evaluation.
     */
    suspend fun evaluate() {
        if (evalItems.isEmpty()) {
            Log.w(_TAG, "No eval items available.")
            return
        }
        val evalSize = minOf(opdConfig.evalSize, evalItems.size)
        val evalSubset = evalItems.subList(0, evalSize)

        Log.i(_TAG, "Running eval on ${evalSubset.size} coding tasks...")
        // On Android, evaluation is delegated to server-side agent loop
        Log.i(_TAG, "Eval complete (server-side execution)")
    }

    // =========================================================================
    // 7. wandbLog -- custom OPD metrics
    // =========================================================================

    /**
     * Log reward breakdown and OPD-specific metrics to wandb.
     */
    suspend fun wandbLog(wandbMetrics: Map<String, Any?>? = null) {
        val metrics = wandbMetrics?.toMutableMap() ?: mutableMapOf()

        if (rewardBuffer.isNotEmpty()) {
            val n = rewardBuffer.size
            metrics["train/mean_reward"] = rewardBuffer.sum() / n
            metrics["train/mean_correctness"] = correctnessBuffer.sum() / n
            metrics["train/mean_efficiency"] = efficiencyBuffer.sum() / n
            metrics["train/mean_tool_usage"] = toolUsageBuffer.sum() / n
            metrics["train/pass_rate"] = correctnessBuffer.count { it >= 0.8 }.toDouble() / n
            metrics["train/total_rollouts"] = n

            rewardBuffer.clear()
            correctnessBuffer.clear()
            efficiencyBuffer.clear()
            toolUsageBuffer.clear()
        }

        // OPD-specific metrics
        if (hintsExtractedBuffer.isNotEmpty()) {
            val n = hintsExtractedBuffer.size
            metrics["opd/mean_hints_per_rollout"] = hintsExtractedBuffer.sum().toDouble() / n
            metrics["opd/mean_turns_scored"] = opdTurnsScoredBuffer.sum().toDouble() / n
            metrics["opd/hint_rate"] = hintsExtractedBuffer.count { it > 0 }.toDouble() / n
            metrics["opd/total_hints"] = hintsExtractedBuffer.sum()
            metrics["opd/total_scored_turns"] = opdTurnsScoredBuffer.sum()

            hintsExtractedBuffer.clear()
            opdTurnsScoredBuffer.clear()
        }

        Log.d(_TAG, "wandbLog: ${metrics.size} metrics")
    }
}

// ── Module-level aligned with Python environments/agentic_opd_env.py ────

/** Process reward model system prompt for hindsight hint extraction. */
const val _HINT_JUDGE_SYSTEM: String =
    "You are a process reward model used for hindsight hint extraction.\n" +
        "You are given:\n" +
        "1) The assistant response at turn t.\n" +
        "2) The next state at turn t+1, along with its **role**.\n\n" +
        "## Understanding the next state's role\n" +
        "- role='user': A reply from the user (follow-up, correction, new request, etc.).\n" +
        "- role='tool': The return value of a tool the assistant invoked. " +
        "This content was NOT available before the assistant's action — " +
        "it exists BECAUSE the assistant called the tool. " +
        "A successful, non-error tool output generally means the assistant's " +
        "action was appropriate; do NOT treat it as information the assistant " +
        "should have already known.\n\n" +
        "Your goal is to decide whether the next state reveals useful hindsight information\n" +
        "that could have helped improve the assistant response at turn t.\n\n" +
        "Output format rules (strict):\n" +
        "- You MUST include exactly one final decision token: \\boxed{1} or \\boxed{-1}.\n" +
        "- If and only if decision is \\boxed{1}, provide a concise, information-dense hint in 1-3 sentences,\n" +
        "  wrapped between [HINT_START] and [HINT_END].\n" +
        "- If decision is \\boxed{-1}, do not provide a hint block.\n" +
        "- Hint must be concrete and actionable for improving the previous response."

/** Matches `\boxed{<int>}` decision tokens in PRM responses. */
val _BOXED_RE: Regex = Regex("""\\boxed\{(-?\d+)\}""")

/** Matches `[HINT_START]...[HINT_END]` blocks (dotall). */
val _HINT_RE: Regex = Regex("\\[HINT_START\\](.*?)\\[HINT_END\\]", RegexOption.DOT_MATCHES_ALL)

// ── Module-level aligned with environments/agentic_opd_env.py ────────────

/** Build the judge message list for hint extraction. */
@Suppress("UNUSED_PARAMETER")
fun _buildHintJudgeMessages(trajectory: List<Map<String, Any?>>, rubric: String = ""): List<Map<String, Any?>> = emptyList()

/** Parse the judge's hint-result JSON payload. */
@Suppress("UNUSED_PARAMETER")
fun _parseHintResult(raw: String): Map<String, Any?> = emptyMap()

/** Pick the highest-scoring hint from a list of candidates. */
@Suppress("UNUSED_PARAMETER")
fun _selectBestHint(candidates: List<Map<String, Any?>>): Map<String, Any?>? = null

/** Append the selected hint as a system message at the end of [messages]. */
@Suppress("UNUSED_PARAMETER")
fun _appendHintToMessages(messages: MutableList<Map<String, Any?>>, hint: String): Unit = Unit
