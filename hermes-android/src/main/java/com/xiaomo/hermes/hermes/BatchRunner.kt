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
fun main(
    datasetFile: String = "",
    batchSize: Int = 1,
    runName: String = "run",
    numWorkers: Int = 1
) {
    batchRunnerLogger.info("batch_runner.main: Android stub — use BatchRunner class directly")
}
