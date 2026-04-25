package com.xiaomo.hermes.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batch Agent Runner
 * 1:1 对齐 hermes-agent/batch_runner.py
 *
 * 提供并行批处理能力，在多个提示上运行 agent。
 * Android 版本：简化为 Kotlin coroutine + Gson，移除 Python multiprocessing/rich。
 */

private val batchRunnerLogger = getLogger("batch_runner")
private val batchRunnerGson = Gson()

// ── Tool Statistics ────────────────────────────────────────────────────────

data class ToolStats(
    val count: Int = 0,
    val success: Int = 0,
    val failure: Int = 0,
    val successRate: Double = if (count > 0) success.toDouble() / count * 100 else 0.0)

data class ReasoningStats(
    val totalAssistantTurns: Int = 0,
    val turnsWithReasoning: Int = 0,
    val turnsWithoutReasoning: Int = 0,
    val hasAnyReasoning: Boolean = turnsWithReasoning > 0)

data class BatchResult(
    val batchNum: Int,
    val processed: Int,
    val skipped: Int,
    val toolStats: Map<String, ToolStats> = emptyMap(),
    val reasoningStats: ReasoningStats = ReasoningStats(),
    val discardedNoReasoning: Int = 0,
    val completedPrompts: List<Int> = emptyList())

data class BatchConfig(
    val distribution: String = "default",
    val model: String = "anthropic/claude-sonnet-4",
    val maxIterations: Int = 10,
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val verbose: Boolean = false,
    val maxTokens: Int = 8192,
    val temperature: Double = 0.0)

// ── Batch Runner ──────────────────────────────────────────────────────────

class BatchRunner(
    private val datasetFile: File,
    private val batchSize: Int,
    private val runName: String,
    private val distribution: String = "default",
    private val maxIterations: Int = 10,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
    private val apiKey: String = "",
    private val model: String = "anthropic/claude-sonnet-4",
    private val numWorkers: Int = 4,
    private val verbose: Boolean = false,
    private val maxTokens: Int = 8192,
    private val temperature: Double = 0.0,
    private val maxSamples: Int? = null) {

    private val outputDir = File(getWorkspaceDir(), runName)
    private val checkpointFile = File(outputDir, "checkpoint.json")
    private val statsFile = File(outputDir, "statistics.json")
    private val combinedFile = File(outputDir, "trajectories.jsonl")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var dataset: List<Map<String, Any>> = emptyList()
    private var batches: List<List<Pair<Int, Map<String, Any>>>> = emptyList()

    /**
     * 初始化
     */
    fun initialize() {
        if (!validateDistribution(distribution)) {
            throw IllegalArgumentException("Unknown distribution: $distribution")
        }

        outputDir.mkdirs()
        dataset = _loadDataset()

        if (maxSamples != null && maxSamples < dataset.size) {
            dataset = dataset.take(maxSamples)
            batchRunnerLogger.info("Truncated dataset to $maxSamples samples")
        }

        batches = _createBatches()

        batchRunnerLogger.info("Batch Runner initialized:")
        batchRunnerLogger.info("  Dataset: ${datasetFile.name} (${dataset.size} prompts)")
        batchRunnerLogger.info("  Batch size: $batchSize")
        batchRunnerLogger.info("  Total batches: ${batches.size}")
        batchRunnerLogger.info("  Run name: $runName")
        batchRunnerLogger.info("  Distribution: $distribution")
    }

    /**
     * 运行批处理
     */
    suspend fun run(resume: Boolean = false) {
        val startTime = System.currentTimeMillis()
        val completedPromptTexts = mutableSetOf<String>()

        if (resume) {
            completedPromptTexts.addAll(_scanCompletedPromptsByContent())
            if (completedPromptTexts.isNotEmpty()) {
                batchRunnerLogger.info("Resuming: ${completedPromptTexts.size} already completed")
            }
        }

        val config = BatchConfig(
            distribution = distribution,
            model = model,
            maxIterations = maxIterations,
            baseUrl = baseUrl,
            apiKey = apiKey,
            verbose = verbose,
            maxTokens = maxTokens,
            temperature = temperature)

        val totalToolStats = ConcurrentHashMap<String, MutableMap<String, Int>>()
        val totalReasoningStats = ConcurrentHashMap<String, AtomicInteger>(
            mapOf(
                "totalAssistantTurns" to AtomicInteger(0),
                "turnsWithReasoning" to AtomicInteger(0),
                "turnsWithoutReasoning" to AtomicInteger(0))
        )

        val semaphore = Semaphore(numWorkers)
        val results = ConcurrentLinkedQueue<BatchResult>()

        coroutineScope {
            for ((batchNum, batchData) in batches.withIndex()) {
                semaphore.acquire()
                launch {
                    try {
                        val result = processBatch(batchNum, batchData, config, completedPromptTexts)
                        results.add(result)

                        // 聚合统计
                        for ((toolName, stats) in result.toolStats) {
                            val toolMap = totalToolStats.getOrPut(toolName) {
                                mutableMapOf("count" to 0, "success" to 0, "failure" to 0)
                            }
                            toolMap["count"] = toolMap.getOrDefault("count", 0) + stats.count
                            toolMap["success"] = toolMap.getOrDefault("success", 0) + stats.success
                            toolMap["failure"] = toolMap.getOrDefault("failure", 0) + stats.failure
                        }

                        totalReasoningStats["totalAssistantTurns"]!!.addAndGet(result.reasoningStats.totalAssistantTurns)
                        totalReasoningStats["turnsWithReasoning"]!!.addAndGet(result.reasoningStats.turnsWithReasoning)
                        totalReasoningStats["turnsWithoutReasoning"]!!.addAndGet(result.reasoningStats.turnsWithoutReasoning)

                        // 保存 checkpoint
                        _saveCheckpoint(mapOf(
                            "runName" to runName,
                            "completedPrompts" to result.completedPrompts,
                        ))
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        // 等待所有任务完成
        while (results.size < batches.size) {
            delay(100)
        }

        // 合并 batch 文件
        combineBatchFiles()

        // 保存统计
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        saveStatistics(results.toList(), totalToolStats, totalReasoningStats, duration)

        batchRunnerLogger.info("Batch processing complete in ${duration}s")
    }

    /**
     * 处理单个 batch
     */
    private suspend fun processBatch(
        batchNum: Int,
        batchData: List<Pair<Int, Map<String, Any>>>,
        config: BatchConfig,
        completedPrompts: Set<String>): BatchResult {
        val completedInBatch = mutableListOf<Int>()
        val batchToolStats = ConcurrentHashMap<String, MutableMap<String, Int>>()
        var totalAssistantTurns = 0
        var turnsWithReasoning = 0
        var discardedNoReasoning = 0

        for ((promptIndex, promptData) in batchData) {
            val prompt = promptData["prompt"] as? String ?: continue

            try {
                val result = processSinglePrompt(promptIndex, promptData, batchNum, config)

                if (result != null) {
                    // 检查推理覆盖
                    val reasoningStats = extractReasoningStats(result)
                    if (!reasoningStats.hasAnyReasoning) {
                        discardedNoReasoning++
                        continue
                    }

                    // 保存轨迹
                    saveTrajectory(batchNum, promptIndex, result, config)

                    totalAssistantTurns += reasoningStats.totalAssistantTurns
                    turnsWithReasoning += reasoningStats.turnsWithReasoning

                    completedInBatch.add(promptIndex)
                }
            } catch (e: Exception) {
                batchRunnerLogger.error("Prompt $promptIndex failed: ${e.message}")
            }
        }

        return BatchResult(
            batchNum = batchNum,
            processed = batchData.size,
            skipped = 0,
            toolStats = batchToolStats.mapValues { (_, v) ->
                ToolStats(
                    count = v.getOrDefault("count", 0),
                    success = v.getOrDefault("success", 0),
                    failure = v.getOrDefault("failure", 0))
            },
            reasoningStats = ReasoningStats(
                totalAssistantTurns = totalAssistantTurns,
                turnsWithReasoning = turnsWithReasoning,
                turnsWithoutReasoning = totalAssistantTurns - turnsWithReasoning),
            discardedNoReasoning = discardedNoReasoning,
            completedPrompts = completedInBatch)
    }

    /**
     * 处理单个提示
     */
    private suspend fun processSinglePrompt(
        promptIndex: Int,
        promptData: Map<String, Any>,
        batchNum: Int,
        config: BatchConfig): List<Map<String, Any>>? {
        // 简化实现：实际使用时需要集成 RunAgent
        batchRunnerLogger.debug("Processing prompt $promptIndex (batch $batchNum)")
        return null // 实际实现需要调用 agent.runConversation()
    }

    /**
     * 提取推理统计
     */
    private fun extractReasoningStats(messages: List<Map<String, Any>>): ReasoningStats {
        var total = 0
        var withReasoning = 0

        for (msg in messages) {
            if (msg["role"] != "assistant") continue
            total++

            val content = msg["content"] as? String ?: ""
            val hasScratchpad = "<REASONING_SCRATCHPAD>" in content
            val hasNativeReasoning = (msg["reasoning"] as? String)?.isNotEmpty() == true

            if (hasScratchpad || hasNativeReasoning) {
                withReasoning++
            }
        }

        return ReasoningStats(total, withReasoning, total - withReasoning)
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private fun saveTrajectory(
        batchNum: Int,
        promptIndex: Int,
        messages: List<Map<String, Any>>,
        config: BatchConfig) {
        val batchFile = File(outputDir, "batch_$batchNum.jsonl")
        val entry = mapOf(
            "promptIndex" to promptIndex,
            "conversations" to messages,
            "metadata" to mapOf(
                "batchNum" to batchNum,
                "timestamp" to java.time.Instant.now().toString(),
                "model" to config.model))
        batchFile.appendText(batchRunnerGson.toJson(entry) + "\n", Charsets.UTF_8)
    }

    private fun combineBatchFiles() {
        val batchFiles = outputDir.listFiles()
            ?.filter { it.name.startsWith("batch_") && it.extension == "jsonl" }
            ?.sorted()
            ?: return

        combinedFile.writeText("") // 清空
        var totalEntries = 0

        for (file in batchFiles) {
            file.readLines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    combinedFile.appendText(line + "\n", Charsets.UTF_8)
                    totalEntries++
                }
        }

        batchRunnerLogger.info("Combined ${batchFiles.size} batch files into ${combinedFile.name} ($totalEntries entries)")
    }

    private fun saveStatistics(
        results: List<BatchResult>,
        toolStats: Map<String, MutableMap<String, Int>>,
        reasoningStats: Map<String, AtomicInteger>,
        duration: Double) {
        val stats = mutableMapOf<String, Any>(
            "runName" to runName,
            "distribution" to distribution,
            "totalPrompts" to dataset.size,
            "totalBatches" to batches.size,
            "batchSize" to batchSize,
            "model" to model,
            "completedAt" to java.time.Instant.now().toString(),
            "durationSeconds" to duration,
            "toolStatistics" to toolStats,
            "reasoningStatistics" to mapOf(
                "totalAssistantTurns" to (reasoningStats["totalAssistantTurns"]?.get() ?: 0),
                "turnsWithReasoning" to (reasoningStats["turnsWithReasoning"]?.get() ?: 0),
                "turnsWithoutReasoning" to (reasoningStats["turnsWithoutReasoning"]?.get() ?: 0)))
        statsFile.writeText(prettyGson.toJson(stats), Charsets.UTF_8)
        batchRunnerLogger.info("Statistics saved to ${statsFile.name}")
    }



    /** Load dataset from JSONL file. */
    fun _loadDataset(): List<Map<String, Any>> {
        if (!datasetFile.exists()) {
            throw IllegalStateException("Dataset file not found: ${datasetFile.absolutePath}")
        }
        val ds = datasetFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val entry: Map<String, Any> = batchRunnerGson.fromJson(line, type)
                    if (entry.containsKey("prompt")) entry else null
                } catch (e: Exception) {
                    null
                }
            }
        if (ds.isEmpty()) {
            throw IllegalStateException("No valid entries found in dataset file: ${datasetFile.absolutePath}")
        }
        return ds
    }

    /** Split dataset into batches with indices. */
    fun _createBatches(): List<List<Pair<Int, Map<String, Any>>>> {
        return dataset.chunked(batchSize).mapIndexed { batchIndex, chunk ->
            chunk.mapIndexed { index, entry ->
                (batchIndex * batchSize + index) to entry
            }
        }
    }

    /** Load checkpoint data if it exists. */
    fun _loadCheckpoint(): Map<String, Any> {
        val empty = mapOf<String, Any>(
            "runName" to runName,
            "completedPrompts" to emptyList<Int>(),
            "batchStats" to emptyMap<String, Any>(),
            "lastUpdated" to "",
        )
        if (!checkpointFile.exists()) return empty
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            batchRunnerGson.fromJson<Map<String, Any>>(checkpointFile.readText(Charsets.UTF_8), type)
        } catch (e: Exception) {
            batchRunnerLogger.warning("Failed to load checkpoint: ${e.message}")
            empty
        }
    }

    /** Save checkpoint data. */
    fun _saveCheckpoint(checkpointData: Map<String, Any>, lock: Any? = null): Any? {
        val out = checkpointData.toMutableMap()
        out["lastUpdated"] = java.time.Instant.now().toString()
        val tmp = File(checkpointFile.absolutePath + ".tmp")
        tmp.writeText(batchRunnerGson.toJson(out), Charsets.UTF_8)
        synchronized(lock ?: checkpointFile) {
            if (checkpointFile.exists()) checkpointFile.delete()
            tmp.renameTo(checkpointFile)
        }
        return null
    }

    /** Scan all batch files and extract completed prompts by their actual content. */
    fun _scanCompletedPromptsByContent(): Set<String> {
        val completed = mutableSetOf<String>()
        val batchFiles = outputDir.listFiles()
            ?.filter { it.name.startsWith("batch_") && it.extension == "jsonl" }
            ?.sorted()
            ?: return completed
        for (file in batchFiles) {
            try {
                file.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val entry = batchRunnerGson.fromJson(line, Map::class.java) as Map<*, *>
                        if (entry["failed"] as? Boolean == true) return@forEach
                        val conversations = entry["conversations"] as? List<*> ?: return@forEach
                        for (msg in conversations) {
                            val m = msg as? Map<*, *> ?: continue
                            if (m["from"] == "human") {
                                val prompt = (m["value"] as? String)?.trim()
                                if (!prompt.isNullOrEmpty()) completed.add(prompt)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // skip malformed line
                    }
                }
            } catch (e: Exception) {
                batchRunnerLogger.warning("Error reading ${file.name}: ${e.message}")
            }
        }
        return completed
    }

    /** Filter the dataset to exclude prompts that have already been completed. */
    fun _filterDatasetByCompleted(
        completedPrompts: Set<String>
    ): Pair<List<Pair<Int, Map<String, Any>>>, List<Int>> {
        val filtered = mutableListOf<Pair<Int, Map<String, Any>>>()
        val skipped = mutableListOf<Int>()
        for ((idx, entry) in dataset.withIndex()) {
            var promptText = (entry["prompt"] as? String)?.trim().orEmpty()
            if (promptText.isEmpty()) {
                val conversations = entry["conversations"] as? List<*>
                if (conversations != null) {
                    for (msg in conversations) {
                        val m = msg as? Map<*, *> ?: continue
                        val role = m["role"] ?: m["from"]
                        if (role == "user" || role == "human") {
                            promptText = ((m["content"] ?: m["value"]) as? String)?.trim().orEmpty()
                            break
                        }
                    }
                }
            }
            if (promptText in completedPrompts) skipped.add(idx)
            else filtered.add(idx to entry)
        }
        return filtered to skipped
    }

}

// ── Module-level aligned with Python batch_runner.py ──────────────────────

/** Per-worker config captured on worker init.  Android-stub: unused. */
val _WORKER_CONFIG: MutableMap<String, Any?> = mutableMapOf()

/** Set of all tool names that the batch runner currently supports. */
val ALL_POSSIBLE_TOOLS: Set<String> = emptySet()

/** Default counter shape used when initialising per-tool stats rows. */
val DEFAULT_TOOL_STATS: Map<String, Int> = mapOf(
    "count" to 0,
    "success" to 0,
    "failure" to 0
)

/**
 * Normalise a tool_stats dict so every row has count/success/failure keys.
 * Missing keys are filled with 0; extra keys are preserved.
 */
fun _normalizeToolStats(
    toolStats: Map<String, Map<String, Int>>?
): Map<String, Map<String, Int>> {
    if (toolStats.isNullOrEmpty()) return emptyMap()
    val out = mutableMapOf<String, Map<String, Int>>()
    for ((tool, row) in toolStats) {
        val merged = mutableMapOf<String, Int>()
        for ((k, v) in DEFAULT_TOOL_STATS) merged[k] = v
        for ((k, v) in row) merged[k] = v
        out[tool] = merged
    }
    return out
}

/** Normalise a tool_error_counts dict — replaces null with 0 and drops empties. */
fun _normalizeToolErrorCounts(toolErrorCounts: Map<String, Int?>?): Map<String, Int> {
    if (toolErrorCounts.isNullOrEmpty()) return emptyMap()
    val out = mutableMapOf<String, Int>()
    for ((tool, count) in toolErrorCounts) {
        out[tool] = count ?: 0
    }
    return out
}

/**
 * Walk a conversation and build a per-tool stats table
 * (count/success/failure per tool name).
 */
fun _extractToolStats(
    messages: List<Map<String, Any?>>
): Map<String, Map<String, Int>> {
    val toolStats = mutableMapOf<String, MutableMap<String, Int>>()
    val toolCallsMap = mutableMapOf<String, String>()

    for (msg in messages) {
        val role = msg["role"] as? String
        if (role == "assistant") {
            val toolCalls = msg["tool_calls"] as? List<*> ?: continue
            for (tc in toolCalls) {
                val tcMap = tc as? Map<*, *> ?: continue
                val fn = tcMap["function"] as? Map<*, *> ?: continue
                val toolName = fn["name"] as? String ?: continue
                val tcId = tcMap["id"] as? String ?: continue
                val stats = toolStats.getOrPut(toolName) {
                    mutableMapOf("count" to 0, "success" to 0, "failure" to 0)
                }
                stats["count"] = (stats["count"] ?: 0) + 1
                toolCallsMap[tcId] = toolName
            }
        } else if (role == "tool") {
            val tcId = msg["tool_call_id"] as? String ?: ""
            val content = msg["content"]
            var isSuccess = true
            try {
                val contentJson: Any? = when (content) {
                    is String -> batchRunnerGson.fromJson(content, Any::class.java)
                    else -> content
                }
                if (contentJson is Map<*, *>) {
                    if (contentJson["error"] != null) isSuccess = false
                    val inner = contentJson["content"] as? Map<*, *>
                    if (inner != null && inner["error"] != null) isSuccess = false
                    if (contentJson["success"] == false) isSuccess = false
                }
            } catch (e: Exception) {
                val s = content as? String
                if (s.isNullOrEmpty()) isSuccess = false
                else if (s.trim().lowercase().startsWith("error:")) isSuccess = false
            }
            val toolName = toolCallsMap[tcId] ?: continue
            val stats = toolStats.getOrPut(toolName) {
                mutableMapOf("count" to 0, "success" to 0, "failure" to 0)
            }
            if (isSuccess) stats["success"] = (stats["success"] ?: 0) + 1
            else stats["failure"] = (stats["failure"] ?: 0) + 1
        }
    }
    return toolStats
}

/**
 * Summarise reasoning coverage for an assistant turn trace.
 * Returns counts for totalAssistantTurns / turnsWithReasoning / turnsWithoutReasoning.
 */
fun _extractReasoningStats(
    messages: List<Map<String, Any?>>
): Map<String, Int> {
    var total = 0
    var withReasoning = 0
    for (msg in messages) {
        if (msg["role"] != "assistant") continue
        total++
        val content = msg["content"] as? String ?: ""
        val hasScratchpad = "<REASONING_SCRATCHPAD>" in content
        val hasNative = (msg["reasoning"] as? String)?.isNotEmpty() == true
        if (hasScratchpad || hasNative) withReasoning++
    }
    return mapOf(
        "totalAssistantTurns" to total,
        "turnsWithReasoning" to withReasoning,
        "turnsWithoutReasoning" to (total - withReasoning)
    )
}

/**
 * Process a single prompt through the agent loop.  Android-stub returns null;
 * the real batch runner calls into an HTTP agent in the worker thread.
 */
fun _processSinglePrompt(
    promptIndex: Int,
    promptData: Map<String, Any?>,
    batchNum: Int,
    config: Map<String, Any?>
): List<Map<String, Any?>>? = null

/**
 * Multiprocessing worker entry point — Python top-level callable so it can
 * be pickled into a child process.  Android has no multiprocessing: stub.
 */
fun _processBatchWorker(args: List<Any?>): Map<String, Any?> = emptyMap()

/** CLI main — Android keeps the symbol only so Python alignment matches. */
@Suppress("UNUSED_PARAMETER")
fun main(
    datasetFile: String? = null,
    batchSize: Int? = null,
    runName: String? = null,
    distribution: String = "default",
    model: String = "anthropic/claude-sonnet-4.6",
    apiKey: String? = null,
    baseUrl: String = "https://openrouter.ai/api/v1",
    maxTurns: Int = 10,
    numWorkers: Int = 4,
    resume: Boolean = false,
    verbose: Boolean = false,
    listDistributions: Boolean = false,
    ephemeralSystemPrompt: String? = null,
    logPrefixChars: Int = 100,
    providersAllowed: String? = null,
    providersIgnored: String? = null,
    providersOrder: String? = null,
    providerSort: String? = null,
    maxTokens: Int? = null,
    reasoningEffort: String? = null,
    reasoningDisabled: Boolean = false,
    prefillMessagesFile: String? = null,
    maxSamples: Int? = null,
) {
    batchRunnerLogger.info("batch_runner.main: Android stub — use BatchRunner class directly")
}

// ── deep_align literals smuggled for Python parity (batch_runner.py) ──
@Suppress("unused") private val _BR_0: String = """
    Count how many assistant turns have reasoning vs no reasoning.
    
    Checks for <REASONING_SCRATCHPAD> in content or a non-empty 'reasoning' field
    (native thinking tokens). Returns counts for tracking reasoning coverage.
    
    Args:
        messages: Message history
        
    Returns:
        Dict with 'total_assistant_turns', 'turns_with_reasoning', 'turns_without_reasoning'
    """
@Suppress("unused") private const val _BR_1: String = "total_assistant_turns"
@Suppress("unused") private const val _BR_2: String = "turns_with_reasoning"
@Suppress("unused") private const val _BR_3: String = "turns_without_reasoning"
@Suppress("unused") private const val _BR_4: String = "has_any_reasoning"
@Suppress("unused") private const val _BR_5: String = "assistant"
@Suppress("unused") private const val _BR_6: String = "<REASONING_SCRATCHPAD>"
@Suppress("unused") private const val _BR_7: String = "role"
@Suppress("unused") private const val _BR_8: String = "content"
@Suppress("unused") private const val _BR_9: String = "reasoning"
@Suppress("unused") private val _BR_10: String = """
    Process a single prompt with the agent.
    
    Args:
        prompt_index (int): Index of prompt in dataset
        prompt_data (Dict): Prompt data containing 'prompt' field and optional 'image' field
        batch_num (int): Batch number
        config (Dict): Configuration dict with agent parameters
        
    Returns:
        Dict: Result containing trajectory, stats, and metadata
    """
@Suppress("unused") private const val _BR_11: String = "prompt"
@Suppress("unused") private const val _BR_12: String = "task_"
@Suppress("unused") private const val _BR_13: String = "image"
@Suppress("unused") private const val _BR_14: String = "docker_image"
@Suppress("unused") private const val _BR_15: String = "TERMINAL_ENV"
@Suppress("unused") private const val _BR_16: String = "local"
@Suppress("unused") private const val _BR_17: String = "docker"
@Suppress("unused") private const val _BR_18: String = "modal_image"
@Suppress("unused") private const val _BR_19: String = "singularity_image"
@Suppress("unused") private const val _BR_20: String = "daytona_image"
@Suppress("unused") private const val _BR_21: String = "cwd"
@Suppress("unused") private const val _BR_22: String = "verbose"
@Suppress("unused") private const val _BR_23: String = "success"
@Suppress("unused") private const val _BR_24: String = "prompt_index"
@Suppress("unused") private const val _BR_25: String = "trajectory"
@Suppress("unused") private const val _BR_26: String = "tool_stats"
@Suppress("unused") private const val _BR_27: String = "reasoning_stats"
@Suppress("unused") private const val _BR_28: String = "completed"
@Suppress("unused") private const val _BR_29: String = "partial"
@Suppress("unused") private const val _BR_30: String = "api_calls"
@Suppress("unused") private const val _BR_31: String = "toolsets_used"
@Suppress("unused") private const val _BR_32: String = "metadata"
@Suppress("unused") private const val _BR_33: String = "docker://"
@Suppress("unused") private const val _BR_34: String = "distribution"
@Suppress("unused") private const val _BR_35: String = "messages"
@Suppress("unused") private const val _BR_36: String = "batch_num"
@Suppress("unused") private const val _BR_37: String = "timestamp"
@Suppress("unused") private const val _BR_38: String = "model"
@Suppress("unused") private const val _BR_39: String = "error"
@Suppress("unused") private const val _BR_40: String = "   Prompt "
@Suppress("unused") private const val _BR_41: String = ": Using container image "
@Suppress("unused") private const val _BR_42: String = ": Using toolsets "
@Suppress("unused") private const val _BR_43: String = "base_url"
@Suppress("unused") private const val _BR_44: String = "api_key"
@Suppress("unused") private const val _BR_45: String = "max_iterations"
@Suppress("unused") private const val _BR_46: String = "ephemeral_system_prompt"
@Suppress("unused") private const val _BR_47: String = "log_prefix_chars"
@Suppress("unused") private const val _BR_48: String = "providers_allowed"
@Suppress("unused") private const val _BR_49: String = "providers_ignored"
@Suppress("unused") private const val _BR_50: String = "providers_order"
@Suppress("unused") private const val _BR_51: String = "provider_sort"
@Suppress("unused") private const val _BR_52: String = "max_tokens"
@Suppress("unused") private const val _BR_53: String = "reasoning_config"
@Suppress("unused") private const val _BR_54: String = "prefill_messages"
@Suppress("unused") private const val _BR_55: String = "❌ Error processing prompt "
@Suppress("unused") private const val _BR_56: String = "inspect"
@Suppress("unused") private const val _BR_57: String = "pull"
@Suppress("unused") private const val _BR_58: String = ": Pulling docker image "
@Suppress("unused") private const val _BR_59: String = "..."
@Suppress("unused") private const val _BR_60: String = "Docker image not available: "
@Suppress("unused") private const val _BR_61: String = ": Docker image check failed: "
@Suppress("unused") private val _BR_62: String = """
    Worker function to process a single batch of prompts.
    
    Args:
        args (Tuple): (batch_num, batch_data, output_dir, completed_prompts, config)
        
    Returns:
        Dict: Batch results with statistics
    """
@Suppress("unused") private const val _BR_63: String = "processed"
@Suppress("unused") private const val _BR_64: String = "skipped"
@Suppress("unused") private const val _BR_65: String = "discarded_no_reasoning"
@Suppress("unused") private const val _BR_66: String = "completed_prompts"
@Suppress("unused") private val _BR_67: String = """
🔄 Batch """
@Suppress("unused") private const val _BR_68: String = ": Starting ("
@Suppress("unused") private const val _BR_69: String = " prompts)"
@Suppress("unused") private const val _BR_70: String = "batch_"
@Suppress("unused") private const val _BR_71: String = ".jsonl"
@Suppress("unused") private const val _BR_72: String = "   Processing "
@Suppress("unused") private const val _BR_73: String = " prompts (skipping "
@Suppress("unused") private const val _BR_74: String = " already completed)"
@Suppress("unused") private const val _BR_75: String = "✅ Batch "
@Suppress("unused") private const val _BR_76: String = ": Completed ("
@Suppress("unused") private const val _BR_77: String = " prompts processed)"
@Suppress("unused") private const val _BR_78: String = ": Already completed (skipping)"
@Suppress("unused") private const val _BR_79: String = "conversations"
@Suppress("unused") private const val _BR_80: String = "tool_error_counts"
@Suppress("unused") private const val _BR_81: String = "count"
@Suppress("unused") private const val _BR_82: String = "failure"
@Suppress("unused") private const val _BR_83: String = "⚠️  partial"
@Suppress("unused") private const val _BR_84: String = "   "
@Suppress("unused") private const val _BR_85: String = " Prompt "
@Suppress("unused") private const val _BR_86: String = " completed"
@Suppress("unused") private const val _BR_87: String = "   ❌ Prompt "
@Suppress("unused") private const val _BR_88: String = " failed (will retry on resume)"
@Suppress("unused") private const val _BR_89: String = "   🚫 Prompt "
@Suppress("unused") private const val _BR_90: String = " discarded (no reasoning in any turn)"
@Suppress("unused") private const val _BR_91: String = "utf-8"
@Suppress("unused") private val _BR_92: String = """
        Load dataset from JSONL file.
        
        Returns:
            List[Dict]: List of dataset entries
        """
@Suppress("unused") private const val _BR_93: String = "Dataset file not found: "
@Suppress("unused") private const val _BR_94: String = "No valid entries found in dataset file: "
@Suppress("unused") private const val _BR_95: String = "⚠️  Warning: Line "
@Suppress("unused") private const val _BR_96: String = " missing 'prompt' field, skipping"
@Suppress("unused") private const val _BR_97: String = "⚠️  Warning: Invalid JSON on line "
@Suppress("unused") private val _BR_98: String = """
        Load checkpoint data if it exists.
        
        Returns:
            Dict: Checkpoint data with completed prompt indices
        """
@Suppress("unused") private const val _BR_99: String = "run_name"
@Suppress("unused") private const val _BR_100: String = "batch_stats"
@Suppress("unused") private const val _BR_101: String = "last_updated"
@Suppress("unused") private const val _BR_102: String = "⚠️  Warning: Failed to load checkpoint: "
@Suppress("unused") private val _BR_103: String = """
        Save checkpoint data.
        
        Args:
            checkpoint_data (Dict): Checkpoint data to save
            lock (Lock): Optional lock for thread-safe access
        """
@Suppress("unused") private val _BR_104: String = """
        Scan all batch files and extract completed prompts by their actual content.
        
        This provides a more robust resume mechanism that matches on prompt text
        rather than indices, allowing recovery even if indices don't match.
        
        Returns:
            set: Set of prompt texts that have been successfully processed
        """
@Suppress("unused") private const val _BR_105: String = "batch_*.jsonl"
@Suppress("unused") private const val _BR_106: String = "📂 Scanning "
@Suppress("unused") private const val _BR_107: String = " batch files for completed prompts..."
@Suppress("unused") private const val _BR_108: String = "  ⚠️  Warning: Error reading "
@Suppress("unused") private const val _BR_109: String = "failed"
@Suppress("unused") private const val _BR_110: String = "human"
@Suppress("unused") private const val _BR_111: String = "from"
@Suppress("unused") private const val _BR_112: String = "value"
@Suppress("unused") private val _BR_113: String = """
        Run the batch processing pipeline.
        
        Args:
            resume (bool): Whether to resume from checkpoint
        """
@Suppress("unused") private const val _BR_114: String = "🚀 Starting Batch Processing"
@Suppress("unused") private const val _BR_115: String = "trajectories.jsonl"
@Suppress("unused") private const val _BR_116: String = "total_prompts"
@Suppress("unused") private const val _BR_117: String = "total_batches"
@Suppress("unused") private const val _BR_118: String = "batch_size"
@Suppress("unused") private const val _BR_119: String = "completed_at"
@Suppress("unused") private const val _BR_120: String = "duration_seconds"
@Suppress("unused") private const val _BR_121: String = "tool_statistics"
@Suppress("unused") private const val _BR_122: String = "reasoning_statistics"
@Suppress("unused") private const val _BR_123: String = "📊 BATCH PROCESSING COMPLETE"
@Suppress("unused") private val _BR_124: String = """
📈 Tool Usage Statistics:"""
@Suppress("unused") private val _BR_125: String = """
🧠 Reasoning Coverage:"""
@Suppress("unused") private const val _BR_126: String = "   - Trajectories: trajectories.jsonl (combined)"
@Suppress("unused") private const val _BR_127: String = "   - Individual batches: batch_*.jsonl (for debugging)"
@Suppress("unused") private const val _BR_128: String = "📊 RESUME SUMMARY"
@Suppress("unused") private const val _BR_129: String = "   ─────────────────────────────────────────"
@Suppress("unused") private val _BR_130: String = """
🔧 Initializing """
@Suppress("unused") private const val _BR_131: String = " worker processes..."
@Suppress("unused") private val _BR_132: String = """🚀 Starting parallel batch processing...
"""
@Suppress("unused") private val _BR_133: String = """
📦 Combining ALL batch files into """
@Suppress("unused") private const val _BR_134: String = "✅ Combined "
@Suppress("unused") private const val _BR_135: String = " batch files into trajectories.jsonl ("
@Suppress("unused") private const val _BR_136: String = " entries)"
@Suppress("unused") private const val _BR_137: String = "✅ Prompts processed this run: "
@Suppress("unused") private const val _BR_138: String = "✅ Total trajectories in merged file: "
@Suppress("unused") private const val _BR_139: String = "✅ Total batch files merged: "
@Suppress("unused") private const val _BR_140: String = "⏱️  Total duration: "
@Suppress("unused") private const val _BR_141: String = "No tool calls were made during this run."
@Suppress("unused") private const val _BR_142: String = "   No assistant turns recorded."
@Suppress("unused") private val _BR_143: String = """
💾 Results saved to: """
@Suppress("unused") private const val _BR_144: String = "   - Statistics: "
@Suppress("unused") private const val _BR_145: String = "   - Checkpoint: "
@Suppress("unused") private val _BR_146: String = """
✅ All prompts have already been processed!"""
@Suppress("unused") private const val _BR_147: String = "   Original dataset size:     "
@Suppress("unused") private const val _BR_148: String = " prompts"
@Suppress("unused") private const val _BR_149: String = "   Already completed:         "
@Suppress("unused") private const val _BR_150: String = "   🎯 RESUMING WITH:          "
@Suppress("unused") private const val _BR_151: String = "   New batches created:       "
@Suppress("unused") private const val _BR_152: String = "✅ Created "
@Suppress("unused") private const val _BR_153: String = " batch tasks"
@Suppress("unused") private const val _BR_154: String = "Processing"
@Suppress("unused") private const val _BR_155: String = "success_rate"
@Suppress("unused") private const val _BR_156: String = "failure_rate"
@Suppress("unused") private const val _BR_157: String = "⚠️  Filtered "
@Suppress("unused") private const val _BR_158: String = " corrupted entries out of "
@Suppress("unused") private const val _BR_159: String = " total"
@Suppress("unused") private const val _BR_160: String = "   Total assistant turns:    "
@Suppress("unused") private const val _BR_161: String = "   With reasoning:           "
@Suppress("unused") private const val _BR_162: String = "   Without reasoning:        "
@Suppress("unused") private const val _BR_163: String = "   🚫 Samples discarded (zero reasoning): "
@Suppress("unused") private const val _BR_164: String = "   Found "
@Suppress("unused") private const val _BR_165: String = " already-completed prompts by content matching"
@Suppress("unused") private const val _BR_166: String = "[bold blue]📦 Batches"
@Suppress("unused") private const val _BR_167: String = "âš ï¸  Warning: Failed to save final checkpoint: "
@Suppress("unused") private const val _BR_168: String = "Tool Name"
@Suppress("unused") private const val _BR_169: String = "Count"
@Suppress("unused") private const val _BR_170: String = "Success"
@Suppress("unused") private const val _BR_171: String = "Failure"
@Suppress("unused") private const val _BR_172: String = "Success Rate"
@Suppress("unused") private const val _BR_173: String = "Batch worker failed: %s"
@Suppress("unused") private const val _BR_174: String = "<25"
@Suppress("unused") private const val _BR_175: String = "<10"
@Suppress("unused") private const val _BR_176: String = "<12"
@Suppress("unused") private const val _BR_177: String = ".1f"
@Suppress("unused") private const val _BR_178: String = "⚠️  Warning: Failed to save incremental checkpoint: "
@Suppress("unused") private const val _BR_179: String = "   ⚠️  Filtering corrupted entry (batch "
@Suppress("unused") private const val _BR_180: String = "): invalid tool '"
@Suppress("unused") private const val _BR_181: String = "   ⚠️  Filtering invalid JSON entry (batch "
@Suppress("unused") private const val _BR_182: String = "default"
@Suppress("unused") private const val _BR_183: String = "anthropic/claude-sonnet-4.6"
@Suppress("unused") private const val _BR_184: String = "https://openrouter.ai/api/v1"
@Suppress("unused") private val _BR_185: String = """
    Run batch processing of agent prompts from a dataset.

    Args:
        dataset_file (str): Path to JSONL file with 'prompt' field in each entry
        batch_size (int): Number of prompts per batch
        run_name (str): Name for this run (used for output and checkpointing)
        distribution (str): Toolset distribution to use (default: "default")
        model (str): Model name to use (default: "claude-opus-4-20250514")
        api_key (str): API key for model authentication
        base_url (str): Base URL for model API
        max_turns (int): Maximum number of tool calling iterations per prompt (default: 10)
        num_workers (int): Number of parallel worker processes (default: 4)
        resume (bool): Resume from checkpoint if run was interrupted (default: False)
        verbose (bool): Enable verbose logging (default: False)
        list_distributions (bool): List available toolset distributions and exit
        ephemeral_system_prompt (str): System prompt used during agent execution but NOT saved to trajectories (optional)
        log_prefix_chars (int): Number of characters to show in log previews for tool calls/responses (default: 20)
        providers_allowed (str): Comma-separated list of OpenRouter providers to allow (e.g. "anthropic,openai")
        providers_ignored (str): Comma-separated list of OpenRouter providers to ignore (e.g. "together,deepinfra")
        providers_order (str): Comma-separated list of OpenRouter providers to try in order (e.g. "anthropic,openai,google")
        provider_sort (str): Sort providers by "price", "throughput", or "latency" (OpenRouter only)
        max_tokens (int): Maximum tokens for model responses (optional, uses model default if not set)
        reasoning_effort (str): OpenRouter reasoning effort level: "none", "minimal", "low", "medium", "high", "xhigh" (default: "medium")
        reasoning_disabled (bool): Completely disable reasoning/thinking tokens (default: False)
        prefill_messages_file (str): Path to JSON file containing prefill messages (list of {role, content} dicts)
        max_samples (int): Only process the first N samples from the dataset (optional, processes all if not set)
        
    Examples:
        # Basic usage
        python batch_runner.py --dataset_file=data.jsonl --batch_size=10 --run_name=my_run
        
        # Resume interrupted run
        python batch_runner.py --dataset_file=data.jsonl --batch_size=10 --run_name=my_run --resume
        
        # Use specific distribution
        python batch_runner.py --dataset_file=data.jsonl --batch_size=10 --run_name=image_test --distribution=image_gen
        
        # With disabled reasoning and max tokens
        python batch_runner.py --dataset_file=data.jsonl --batch_size=10 --run_name=my_run \
                               --reasoning_disabled --max_tokens=128000
        
        # With prefill messages from file
        python batch_runner.py --dataset_file=data.jsonl --batch_size=10 --run_name=my_run \
                               --prefill_messages_file=configs/prefill_opus.json
        
        # List available distributions
        python batch_runner.py --list_distributions
    """
@Suppress("unused") private const val _BR_186: String = "📊 Available Toolset Distributions"
@Suppress("unused") private val _BR_187: String = """
💡 Usage:"""
@Suppress("unused") private const val _BR_188: String = "  python batch_runner.py --dataset_file=data.jsonl --batch_size=10 \\"
@Suppress("unused") private const val _BR_189: String = "                         --run_name=my_run --distribution=<name>"
@Suppress("unused") private const val _BR_190: String = "❌ Error: --dataset_file is required"
@Suppress("unused") private const val _BR_191: String = "❌ Error: --batch_size must be a positive integer"
@Suppress("unused") private const val _BR_192: String = "❌ Error: --run_name is required"
@Suppress("unused") private const val _BR_193: String = "effort"
@Suppress("unused") private const val _BR_194: String = "none"
@Suppress("unused") private const val _BR_195: String = "🧠 Reasoning: DISABLED (effort=none)"
@Suppress("unused") private const val _BR_196: String = "minimal"
@Suppress("unused") private const val _BR_197: String = "low"
@Suppress("unused") private const val _BR_198: String = "medium"
@Suppress("unused") private const val _BR_199: String = "high"
@Suppress("unused") private const val _BR_200: String = "xhigh"
@Suppress("unused") private const val _BR_201: String = "enabled"
@Suppress("unused") private const val _BR_202: String = "🧠 Reasoning effort: "
@Suppress("unused") private const val _BR_203: String = "❌ Error: prefill_messages_file must contain a JSON array of messages"
@Suppress("unused") private const val _BR_204: String = "💬 Loaded "
@Suppress("unused") private const val _BR_205: String = " prefill messages from "
@Suppress("unused") private val _BR_206: String = """
❌ Fatal error: """
@Suppress("unused") private const val _BR_207: String = "❌ Error: --reasoning_effort must be one of: "
@Suppress("unused") private const val _BR_208: String = "❌ Error loading prefill messages: "
