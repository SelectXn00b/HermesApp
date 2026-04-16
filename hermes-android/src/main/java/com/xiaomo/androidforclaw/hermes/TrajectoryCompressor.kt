package com.xiaomo.androidforclaw.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Trajectory Compressor
 * 1:1 对齐 hermes-agent/trajectory_compressor.py
 *
 * Post-processes completed agent trajectories to compress them within a target
 * token budget while preserving training signal quality.
 *
 * Android 版本：简化为 Kotlin coroutine + Gson + OkHttp。
 * 移除 Python threading/asyncio，使用 Kotlin coroutine 替代。
 */

private object tcLogger {
    private const val TAG = "TrajectoryCompressor"
    fun info(msg: String) = android.util.Log.i(TAG, msg)
    fun warning(msg: String) = android.util.Log.w(TAG, msg)
}
private val tcGson = Gson()

// ── 配置 ──────────────────────────────────────────────────────────────────

data class CompressionConfig(
    val tokenizerName: String = "moonshotai/Kimi-K2-Thinking",
    val trustRemoteCode: Boolean = true,
    val targetMaxTokens: Int = 15250,
    val summaryTargetTokens: Int = 750,
    val protectFirstSystem: Boolean = true,
    val protectFirstHuman: Boolean = true,
    val protectFirstGpt: Boolean = true,
    val protectFirstTool: Boolean = true,
    val protectLastNTurns: Int = 4,
    val summarizationModel: String = "google/gemini-3-flash-preview",
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val apiKeyEnv: String = "OPENROUTER_API_KEY",
    val temperature: Double = 0.3,
    val maxRetries: Int = 3,
    val retryDelay: Int = 2,
    val addSummaryNotice: Boolean = true,
    val summaryNoticeText: String = "\n\nSome of your previous tool responses may be summarized to preserve context.",
    val outputSuffix: String = "_compressed",
    val numWorkers: Int = 4,
    val maxConcurrentRequests: Int = 50,
    val skipUnderTarget: Boolean = true,
    val saveOverLimit: Boolean = true,
    val perTrajectoryTimeout: Int = 300) {
    companion object
}

// ── 指标 ──────────────────────────────────────────────────────────────────

data class TrajectoryMetrics(
    val originalTokens: Int = 0,
    val compressedTokens: Int = 0,
    val tokensSaved: Int = 0,
    val compressionRatio: Double = 1.0,
    val originalTurns: Int = 0,
    val compressedTurns: Int = 0,
    val turnsRemoved: Int = 0,
    val turnsCompressedStartIdx: Int = -1,
    val turnsCompressedEndIdx: Int = -1,
    val turnsInCompressedRegion: Int = 0,
    val wasCompressed: Boolean = false,
    val stillOverLimit: Boolean = false,
    val skippedUnderTarget: Boolean = false,
    val summarizationApiCalls: Int = 0,
    val summarizationErrors: Int = 0)

data class AggregateMetrics(
    var totalTrajectories: Int = 0,
    var trajectoriesCompressed: Int = 0,
    var trajectoriesSkippedUnderTarget: Int = 0,
    var trajectoriesStillOverLimit: Int = 0,
    var trajectoriesFailed: Int = 0,
    var totalTokensBefore: Int = 0,
    var totalTokensAfter: Int = 0,
    var totalTokensSaved: Int = 0,
    var totalTurnsBefore: Int = 0,
    var totalTurnsAfter: Int = 0,
    var totalTurnsRemoved: Int = 0,
    var totalSummarizationCalls: Int = 0,
    var totalSummarizationErrors: Int = 0,
    val compressionRatios: MutableList<Double> = mutableListOf(),
    val tokensSavedList: MutableList<Int> = mutableListOf(),
    val turnsRemovedList: MutableList<Int> = mutableListOf(),
    var processingStartTime: String = "",
    var processingEndTime: String = "",
    var processingDurationSeconds: Double = 0.0) {
    fun addTrajectoryMetrics(metrics: TrajectoryMetrics) {
        totalTrajectories++
        totalTokensBefore += metrics.originalTokens
        totalTokensAfter += metrics.compressedTokens
        totalTokensSaved += metrics.tokensSaved
        totalTurnsBefore += metrics.originalTurns
        totalTurnsAfter += metrics.compressedTurns
        totalTurnsRemoved += metrics.turnsRemoved
        totalSummarizationCalls += metrics.summarizationApiCalls
        totalSummarizationErrors += metrics.summarizationErrors

        if (metrics.wasCompressed) {
            trajectoriesCompressed++
            compressionRatios.add(metrics.compressionRatio)
            tokensSavedList.add(metrics.tokensSaved)
            turnsRemovedList.add(metrics.turnsRemoved)
        }
        if (metrics.skippedUnderTarget) trajectoriesSkippedUnderTarget++
        if (metrics.stillOverLimit) trajectoriesStillOverLimit++
    }
}

// ── Compressor ────────────────────────────────────────────────────────────

class TrajectoryCompressor(
    internal val config: CompressionConfig = CompressionConfig()) {

    internal val aggregateMetrics = AggregateMetrics()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val apiKey: String by lazy {
        System.getenv(config.apiKeyEnv) ?: ""
    }

    /**
     * 计算 token 数（简化版：按字符数估算）
     * Python: 使用 HuggingFace tokenizer
     */
    fun countTokens(text: String): Int {
        return if (text.isEmpty()) 0 else text.length / 4
    }

    /**
     * 计算轨迹总 token 数
     */
    fun countTrajectoryTokens(trajectory: List<Map<String, String>>): Int {
        return trajectory.sumOf { countTokens(it["value"] ?: "") }
    }

    /**
     * 计算每个 turn 的 token 数
     */
    fun countTurnTokens(trajectory: List<Map<String, String>>): List<Int> {
        return trajectory.map { countTokens(it["value"] ?: "") }
    }

    /**
     * 查找受保护的 turn 索引
     */
    private fun findProtectedIndices(
        trajectory: List<Map<String, String>>): Pair<Set<Int>, Pair<Int, Int>> {
        val n = trajectory.size
        val protected = mutableSetOf<Int>()

        var firstSystem: Int? = null
        var firstHuman: Int? = null
        var firstGpt: Int? = null
        var firstTool: Int? = null

        for ((i, turn) in trajectory.withIndex()) {
            val role = turn["from"] ?: ""
            when {
                role == "system" && firstSystem == null -> firstSystem = i
                role == "human" && firstHuman == null -> firstHuman = i
                role == "gpt" && firstGpt == null -> firstGpt = i
                role == "tool" && firstTool == null -> firstTool = i
            }
        }

        if (config.protectFirstSystem && firstSystem != null) protected.add(firstSystem)
        if (config.protectFirstHuman && firstHuman != null) protected.add(firstHuman)
        if (config.protectFirstGpt && firstGpt != null) protected.add(firstGpt)
        if (config.protectFirstTool && firstTool != null) protected.add(firstTool)

        for (i in maxOf(0, n - config.protectLastNTurns) until n) {
            protected.add(i)
        }

        val headProtected = protected.filter { it < n / 2 }
        val tailProtected = protected.filter { it >= n / 2 }

        val compressStart = (headProtected.maxOrNull() ?: -1) + 1
        val compressEnd = tailProtected.minOrNull() ?: n

        return protected to (compressStart to compressEnd)
    }

    /**
     * 提取要总结的内容
     */
    private fun extractTurnContentForSummary(
        trajectory: List<Map<String, String>>,
        start: Int,
        end: Int): String {
        val parts = mutableListOf<String>()
        for (i in start until end) {
            val turn = trajectory[i]
            val role = turn["from"] ?: "unknown"
            var value = turn["value"] ?: ""
            if (value.length > 3000) {
                value = value.take(1500) + "\n...[truncated]...\n" + value.takeLast(500)
            }
            parts.add("[Turn $i - ${role.uppercase()}]:\n$value")
        }
        return parts.joinToString("\n\n")
    }

    /**
     * 生成总结（同步版）
     */
    private fun generateSummary(content: String, metrics: TrajectoryMetrics): Pair<String, TrajectoryMetrics> {
        val prompt = buildString {
            appendLine("Summarize the following agent conversation turns concisely. This summary will replace these turns in the conversation history.")
            appendLine()
            appendLine("Write the summary from a neutral perspective describing what the assistant did and learned. Include:")
            appendLine("1. What actions the assistant took (tool calls, searches, file operations)")
            appendLine("2. Key information or results obtained")
            appendLine("3. Any important decisions or findings")
            appendLine("4. Relevant data, file names, values, or outputs")
            appendLine()
            appendLine("Keep the summary factual and informative. Target approximately ${config.summaryTargetTokens} tokens.")
            appendLine()
            appendLine("---")
            appendLine("TURNS TO SUMMARIZE:")
            appendLine(content)
            appendLine("---")
            appendLine()
            appendLine("Write only the summary, starting with \"[CONTEXT SUMMARY]:\" prefix.")
        }

        var updatedMetrics = metrics.copy(
            summarizationApiCalls = metrics.summarizationApiCalls + 1
        )

        for (attempt in 0 until config.maxRetries) {
            try {
                val result = callSummarizationApi(prompt)
                if (result != null) {
                    val summary = if (result.startsWith("[CONTEXT SUMMARY]:")) {
                        result
                    } else {
                        "[CONTEXT SUMMARY]: $result"
                    }
                    return summary to updatedMetrics
                }
            } catch (e: Exception) {
                updatedMetrics = updatedMetrics.copy(
                    summarizationErrors = updatedMetrics.summarizationErrors + 1
                )
                tcLogger.warning("Summarization attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < config.maxRetries - 1) {
                    Thread.sleep(((attempt + 1) * config.retryDelay * 1000).toLong())
                }
            }
        }

        return "[CONTEXT SUMMARY]: [Summary generation failed - previous turns contained tool calls and responses that have been compressed to save context space.]" to updatedMetrics
    }

    /**
     * 生成总结（异步版）
     */
    private suspend fun generateSummaryAsync(content: String, metrics: TrajectoryMetrics): Pair<String, TrajectoryMetrics> {
        val prompt = buildString {
            appendLine("Summarize the following agent conversation turns concisely. This summary will replace these turns in the conversation history.")
            appendLine()
            appendLine("Write the summary from a neutral perspective describing what the assistant did and learned. Include:")
            appendLine("1. What actions the assistant took (tool calls, searches, file operations)")
            appendLine("2. Key information or results obtained")
            appendLine("3. Any important decisions or findings")
            appendLine("4. Relevant data, file names, values, or outputs")
            appendLine()
            appendLine("Keep the summary factual and informative. Target approximately ${config.summaryTargetTokens} tokens.")
            appendLine()
            appendLine("---")
            appendLine("TURNS TO SUMMARIZE:")
            appendLine(content)
            appendLine("---")
            appendLine()
            appendLine("Write only the summary, starting with \"[CONTEXT SUMMARY]:\" prefix.")
        }

        var updatedMetrics = metrics.copy(
            summarizationApiCalls = metrics.summarizationApiCalls + 1
        )

        for (attempt in 0 until config.maxRetries) {
            try {
                val result = callSummarizationApiAsync(prompt)
                if (result != null) {
                    val summary = if (result.startsWith("[CONTEXT SUMMARY]:")) {
                        result
                    } else {
                        "[CONTEXT SUMMARY]: $result"
                    }
                    return summary to updatedMetrics
                }
            } catch (e: Exception) {
                updatedMetrics = updatedMetrics.copy(
                    summarizationErrors = updatedMetrics.summarizationErrors + 1
                )
                tcLogger.warning("Summarization attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < config.maxRetries - 1) {
                    delay(((attempt + 1) * config.retryDelay * 1000).toLong())
                }
            }
        }

        return "[CONTEXT SUMMARY]: [Summary generation failed - previous turns contained tool calls and responses that have been compressed to save context space.]" to updatedMetrics
    }

    /**
     * 调用总结 API（同步）
     */
    private fun callSummarizationApi(prompt: String): String? {
        val apiKey = this.apiKey
        if (apiKey.isEmpty()) return null

        val requestBody = tcGson.toJson(mapOf(
            "model" to config.summarizationModel,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "temperature" to config.temperature,
            "max_tokens" to config.summaryTargetTokens * 2))

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@use null
                val result = tcGson.fromJson(body, Map::class.java) as? Map<*, *>
                val choices = result?.get("choices") as? List<*>
                val firstChoice = choices?.firstOrNull() as? Map<*, *>
                val message = firstChoice?.get("message") as? Map<*, *>
                message?.get("content") as? String
            } else {
                null
            }
        }
    }

    /**
     * 调用总结 API（异步）
     */
    private suspend fun callSummarizationApiAsync(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            callSummarizationApi(prompt)
        }
    }

    /**
     * 压缩单个轨迹（同步版）
     */
    fun compressTrajectory(
        trajectory: List<Map<String, String>>): Pair<List<Map<String, String>>, TrajectoryMetrics> {
        var metrics = TrajectoryMetrics(
            originalTurns = trajectory.size)

        val turnTokens = countTurnTokens(trajectory)
        val totalTokens = turnTokens.sum()
        metrics = metrics.copy(originalTokens = totalTokens)

        if (totalTokens <= config.targetMaxTokens) {
            return trajectory to metrics.copy(
                skippedUnderTarget = true,
                compressedTokens = totalTokens,
                compressedTurns = trajectory.size)
        }

        val (_, compressPair) = findProtectedIndices(trajectory)
        val (compressStart, compressEnd) = compressPair

        if (compressStart >= compressEnd) {
            return trajectory to metrics.copy(
                compressedTokens = totalTokens,
                compressedTurns = trajectory.size,
                stillOverLimit = totalTokens > config.targetMaxTokens)
        }

        val tokensToSave = totalTokens - config.targetMaxTokens
        val targetTokensToCompress = tokensToSave + config.summaryTargetTokens

        var accumulatedTokens = 0
        var compressUntil = compressStart

        for (i in compressStart until compressEnd) {
            accumulatedTokens += turnTokens[i]
            compressUntil = i + 1
            if (accumulatedTokens >= targetTokensToCompress) break
        }

        if (accumulatedTokens < targetTokensToCompress && compressUntil < compressEnd) {
            compressUntil = compressEnd
            accumulatedTokens = turnTokens.subList(compressStart, compressEnd).sum()
        }

        val contentToSummarize = extractTurnContentForSummary(trajectory, compressStart, compressUntil)
        val (summary, updatedMetrics) = generateSummary(contentToSummarize, metrics)

        val compressed = mutableListOf<Map<String, String>>()

        for (i in 0 until compressStart) {
            val turn = trajectory[i].toMutableMap()
            if (turn["from"] == "system" && config.addSummaryNotice) {
                turn["value"] = (turn["value"] ?: "") + config.summaryNoticeText
            }
            compressed.add(turn)
        }

        compressed.add(mapOf("from" to "human", "value" to summary))

        for (i in compressUntil until trajectory.size) {
            compressed.add(trajectory[i].toMap())
        }

        val finalMetrics = updatedMetrics.copy(
            compressedTurns = compressed.size,
            compressedTokens = countTrajectoryTokens(compressed),
            turnsRemoved = trajectory.size - compressed.size,
            tokensSaved = totalTokens - countTrajectoryTokens(compressed),
            compressionRatio = countTrajectoryTokens(compressed).toDouble() / maxOf(totalTokens, 1),
            wasCompressed = true,
            stillOverLimit = countTrajectoryTokens(compressed) > config.targetMaxTokens,
            turnsCompressedStartIdx = compressStart,
            turnsCompressedEndIdx = compressUntil,
            turnsInCompressedRegion = compressUntil - compressStart)

        return compressed to finalMetrics
    }

    /**
     * 压缩单个轨迹（异步版）
     */
    suspend fun compressTrajectoryAsync(
        trajectory: List<Map<String, String>>): Pair<List<Map<String, String>>, TrajectoryMetrics> {
        var metrics = TrajectoryMetrics(
            originalTurns = trajectory.size)

        val turnTokens = countTurnTokens(trajectory)
        val totalTokens = turnTokens.sum()
        metrics = metrics.copy(originalTokens = totalTokens)

        if (totalTokens <= config.targetMaxTokens) {
            return trajectory to metrics.copy(
                skippedUnderTarget = true,
                compressedTokens = totalTokens,
                compressedTurns = trajectory.size)
        }

        val (_, compressPair) = findProtectedIndices(trajectory)
        val (compressStart, compressEnd) = compressPair

        if (compressStart >= compressEnd) {
            return trajectory to metrics.copy(
                compressedTokens = totalTokens,
                compressedTurns = trajectory.size,
                stillOverLimit = totalTokens > config.targetMaxTokens)
        }

        val tokensToSave = totalTokens - config.targetMaxTokens
        val targetTokensToCompress = tokensToSave + config.summaryTargetTokens

        var accumulatedTokens = 0
        var compressUntil = compressStart

        for (i in compressStart until compressEnd) {
            accumulatedTokens += turnTokens[i]
            compressUntil = i + 1
            if (accumulatedTokens >= targetTokensToCompress) break
        }

        if (accumulatedTokens < targetTokensToCompress && compressUntil < compressEnd) {
            compressUntil = compressEnd
            accumulatedTokens = turnTokens.subList(compressStart, compressEnd).sum()
        }

        val contentToSummarize = extractTurnContentForSummary(trajectory, compressStart, compressUntil)
        val (summary, updatedMetrics) = generateSummaryAsync(contentToSummarize, metrics)

        val compressed = mutableListOf<Map<String, String>>()

        for (i in 0 until compressStart) {
            val turn = trajectory[i].toMutableMap()
            if (turn["from"] == "system" && config.addSummaryNotice) {
                turn["value"] = (turn["value"] ?: "") + config.summaryNoticeText
            }
            compressed.add(turn)
        }

        compressed.add(mapOf("from" to "human", "value" to summary))

        for (i in compressUntil until trajectory.size) {
            compressed.add(trajectory[i].toMap())
        }

        val finalMetrics = updatedMetrics.copy(
            compressedTurns = compressed.size,
            compressedTokens = countTrajectoryTokens(compressed),
            turnsRemoved = trajectory.size - compressed.size,
            tokensSaved = totalTokens - countTrajectoryTokens(compressed),
            compressionRatio = countTrajectoryTokens(compressed).toDouble() / maxOf(totalTokens, 1),
            wasCompressed = true,
            stillOverLimit = countTrajectoryTokens(compressed) > config.targetMaxTokens,
            turnsCompressedStartIdx = compressStart,
            turnsCompressedEndIdx = compressUntil,
            turnsInCompressedRegion = compressUntil - compressStart)

        return compressed to finalMetrics
    }

    /**
     * 处理 JSONL 文件（异步版）
     */
    suspend fun processJsonlFileAsync(
        inputFile: File,
        outputFile: File) {
        val entries = mutableListOf<Map<String, Any>>()

        // 读取 JSONL
        inputFile.readLines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val entry: Map<String, Any> = tcGson.fromJson(line, type)
                    entries.add(entry)
                } catch (e: Exception) {
                    tcLogger.warning("Skipping invalid JSON: ${e.message}")
                }
            }
        }

        tcLogger.info("Loaded ${entries.size} trajectories from ${inputFile.name}")

        // 并发压缩
        val processed = coroutineScope {
            val results = entries.map { entry ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(config.perTrajectoryTimeout * 1000L) {
                            val conversations = entry["conversations"] as? List<Map<String, String>>
                            if (conversations != null) {
                                val (compressed, metrics) = compressTrajectoryAsync(conversations)
                                aggregateMetrics.addTrajectoryMetrics(metrics)
                                val result = entry.toMutableMap()
                                result["conversations"] = compressed
                                result
                            } else {
                                entry
                            }
                        }
                    } catch (e: Exception) {
                        aggregateMetrics.trajectoriesFailed++
                        entry
                    }
                }
            }

            results.awaitAll()
        }

        // 写入输出文件
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            processed.joinToString("\n") { tcGson.toJson(it) },
            Charsets.UTF_8
        )

        tcLogger.info("Wrote ${processed.size} trajectories to ${outputFile.name}")
    }

    /**
     * 处理目录
     */
    suspend fun processDirectoryAsync(
        inputDir: File,
        outputDir: File) {
        aggregateMetrics.processingStartTime = java.time.Instant.now().toString()
        val startTime = System.currentTimeMillis()

        val jsonlFiles = inputDir.listFiles()
            ?.filter { it.extension == "jsonl" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (jsonlFiles.isEmpty()) {
            tcLogger.warning("No JSONL files found in ${inputDir.absolutePath}")
            return
        }

        outputDir.mkdirs()

        for (file in jsonlFiles) {
            val outputFile = File(outputDir, file.name)
            processJsonlFileAsync(file, outputFile)
        }

        aggregateMetrics.processingEndTime = java.time.Instant.now().toString()
        aggregateMetrics.processingDurationSeconds =
            (System.currentTimeMillis() - startTime) / 1000.0

        // 保存指标
        val metricsFile = File(outputDir, "compression_metrics.json")
        metricsFile.writeText(prettyGson.toJson(aggregateMetrics), Charsets.UTF_8)
        tcLogger.info("Metrics saved to ${metricsFile.absolutePath}")
    }

    /**
     * 获取聚合指标
     */
    fun getAggregateMetrics(): AggregateMetrics = aggregateMetrics
}

// ── 文件锁工具 ────────────────────────────────────────────────────────────

private fun <T> acquireFileLock(file: File, block: (RandomAccessFile) -> T): T {
    val lockFile = File(file.parent, ".${file.name}.lock")
    val raf = RandomAccessFile(lockFile, "rw")
    var channel: FileChannel? = null
    var lock: FileLock? = null

    try {
        channel = raf.channel
        lock = channel.lock()
        return block(raf)
    } finally {
        lock?.release()
        channel?.close()
        raf.close()
    }
}

// ── 扩展方法 & 辅助函数 ──────────────────────────────────────────────────

/**
 * CompressionConfig.from_yaml - 从 YAML 文件加载配置
 * Android 版：使用 Gson 解析 YAML 风格的 JSON 配置文件
 */
fun CompressionConfig.Companion.fromYaml(yamlPath: String): CompressionConfig {
    val file = File(yamlPath)
    if (!file.exists()) {
        tcLogger.warning("Config not found at $yamlPath, using defaults")
        return CompressionConfig()
    }
    val data: Map<String, Any> = try {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        tcGson.fromJson(file.readText(Charsets.UTF_8), type)
    } catch (e: Exception) {
        tcLogger.warning("Failed to parse config: ${e.message}, using defaults")
        return CompressionConfig()
    }

    var config = CompressionConfig()

    val tokenizer = data["tokenizer"] as? Map<String, Any>
    if (tokenizer != null) {
        config = config.copy(
            tokenizerName = tokenizer["name"] as? String ?: config.tokenizerName,
            trustRemoteCode = (tokenizer["trust_remote_code"] as? Boolean) ?: config.trustRemoteCode)
    }

    val compression = data["compression"] as? Map<String, Any>
    if (compression != null) {
        config = config.copy(
            targetMaxTokens = (compression["target_max_tokens"] as? Number)?.toInt() ?: config.targetMaxTokens,
            summaryTargetTokens = (compression["summary_target_tokens"] as? Number)?.toInt() ?: config.summaryTargetTokens)
    }

    val protectedTurns = data["protected_turns"] as? Map<String, Any>
    if (protectedTurns != null) {
        config = config.copy(
            protectFirstSystem = (protectedTurns["first_system"] as? Boolean) ?: config.protectFirstSystem,
            protectFirstHuman = (protectedTurns["first_human"] as? Boolean) ?: config.protectFirstHuman,
            protectFirstGpt = (protectedTurns["first_gpt"] as? Boolean) ?: config.protectFirstGpt,
            protectFirstTool = (protectedTurns["first_tool"] as? Boolean) ?: config.protectFirstTool,
            protectLastNTurns = (protectedTurns["last_n_turns"] as? Number)?.toInt() ?: config.protectLastNTurns)
    }

    val summarization = data["summarization"] as? Map<String, Any>
    if (summarization != null) {
        config = config.copy(
            summarizationModel = summarization["model"] as? String ?: config.summarizationModel,
            baseUrl = summarization["base_url"] as? String ?: config.baseUrl,
            apiKeyEnv = summarization["api_key_env"] as? String ?: config.apiKeyEnv,
            temperature = (summarization["temperature"] as? Number)?.toDouble() ?: config.temperature,
            maxRetries = (summarization["max_retries"] as? Number)?.toInt() ?: config.maxRetries,
            retryDelay = (summarization["retry_delay"] as? Number)?.toInt() ?: config.retryDelay)
    }

    val output = data["output"] as? Map<String, Any>
    if (output != null) {
        config = config.copy(
            addSummaryNotice = (output["add_summary_notice"] as? Boolean) ?: config.addSummaryNotice,
            summaryNoticeText = output["summary_notice_text"] as? String ?: config.summaryNoticeText,
            outputSuffix = output["output_suffix"] as? String ?: config.outputSuffix)
    }

    val processing = data["processing"] as? Map<String, Any>
    if (processing != null) {
        config = config.copy(
            numWorkers = (processing["num_workers"] as? Number)?.toInt() ?: config.numWorkers,
            maxConcurrentRequests = (processing["max_concurrent_requests"] as? Number)?.toInt() ?: config.maxConcurrentRequests,
            skipUnderTarget = (processing["skip_under_target"] as? Boolean) ?: config.skipUnderTarget,
            saveOverLimit = (processing["save_over_limit"] as? Boolean) ?: config.saveOverLimit)
    }

    return config
}

/**
 * TrajectoryMetrics.to_dict - 转换为 Map
 */
fun TrajectoryMetrics.toDict(): Map<String, Any?> {
    return mapOf(
        "original_tokens" to originalTokens,
        "compressed_tokens" to compressedTokens,
        "tokens_saved" to tokensSaved,
        "compression_ratio" to String.format("%.4f", compressionRatio).toDouble(),
        "original_turns" to originalTurns,
        "compressed_turns" to compressedTurns,
        "turns_removed" to turnsRemoved,
        "compression_region" to mapOf(
            "start_idx" to turnsCompressedStartIdx,
            "end_idx" to turnsCompressedEndIdx,
            "turns_count" to turnsInCompressedRegion),
        "was_compressed" to wasCompressed,
        "still_over_limit" to stillOverLimit,
        "skipped_under_target" to skippedUnderTarget,
        "summarization_api_calls" to summarizationApiCalls,
        "summarization_errors" to summarizationErrors)
}

/**
 * AggregateMetrics.to_dict - 转换为 Map
 */
fun AggregateMetrics.toDict(): Map<String, Any?> {
    val avgCompressionRatio = if (compressionRatios.isNotEmpty())
        compressionRatios.average() else 1.0
    val avgTokensSaved = if (tokensSavedList.isNotEmpty())
        tokensSavedList.average() else 0.0
    val avgTurnsRemoved = if (turnsRemovedList.isNotEmpty())
        turnsRemovedList.average() else 0.0

    return mapOf(
        "summary" to mapOf(
            "total_trajectories" to totalTrajectories,
            "trajectories_compressed" to trajectoriesCompressed,
            "trajectories_skipped_under_target" to trajectoriesSkippedUnderTarget,
            "trajectories_still_over_limit" to trajectoriesStillOverLimit,
            "trajectories_failed" to trajectoriesFailed,
            "compression_rate" to String.format("%.4f",
                trajectoriesCompressed.toDouble() / maxOf(totalTrajectories, 1)).toDouble()),
        "tokens" to mapOf(
            "total_before" to totalTokensBefore,
            "total_after" to totalTokensAfter,
            "total_saved" to totalTokensSaved,
            "overall_compression_ratio" to String.format("%.4f",
                totalTokensAfter.toDouble() / maxOf(totalTokensBefore, 1)).toDouble()),
        "turns" to mapOf(
            "total_before" to totalTurnsBefore,
            "total_after" to totalTurnsAfter,
            "total_removed" to totalTurnsRemoved),
        "averages" to mapOf(
            "avg_compression_ratio" to String.format("%.4f", avgCompressionRatio).toDouble(),
            "avg_tokens_saved_per_compressed" to String.format("%.1f", avgTokensSaved).toDouble(),
            "avg_turns_removed_per_compressed" to String.format("%.2f", avgTurnsRemoved).toDouble()),
        "summarization" to mapOf(
            "total_api_calls" to totalSummarizationCalls,
            "total_errors" to totalSummarizationErrors,
            "success_rate" to String.format("%.4f",
                1.0 - (totalSummarizationErrors.toDouble() / maxOf(totalSummarizationCalls, 1))).toDouble()),
        "processing" to mapOf(
            "start_time" to processingStartTime,
            "end_time" to processingEndTime,
            "duration_seconds" to String.format("%.2f", processingDurationSeconds).toDouble()))
}

// ── TrajectoryCompressor 扩展方法 ─────────────────────────────────────────

/**
 * _init_tokenizer - Android 版使用字符估算，无需初始化 tokenizer
 */
private fun TrajectoryCompressor.initTokenizer() {
    // Android 版：token 计数通过 countTokens() 的字符估算实现
    // Python 版使用 HuggingFace AutoTokenizer，Android 上不适用
}

/**
 * _init_summarizer - 初始化总结器（Android 版直接使用 OkHttpClient）
 */
private fun TrajectoryCompressor.initSummarizer() {
    // Android 版：summarization 通过 callSummarizationApi() 直接使用 OkHttp
    // Python 版使用 OpenAI client / call_llm 路由
}

/**
 * _get_async_client - 返回异步客户端（Android 版使用 OkHttp，返回 null）
 */
private fun TrajectoryCompressor.getAsyncClient(): OkHttpClient? {
    // Android 版使用共享的 httpClient，无需单独的 async client
    return null
}

/**
 * _detect_provider - 从 base_url 检测 provider 名称
 */
private fun TrajectoryCompressor.detectProvider(): String {
    val url = (config.baseUrl).lowercase()
    return when {
        "openrouter" in url -> "openrouter"
        "nousresearch.com" in url -> "nous"
        "chatgpt.com/backend-api/codex" in url -> "codex"
        "api.z.ai" in url -> "zai"
        "moonshot.ai" in url || "moonshot.cn" in url || "api.kimi.com" in url -> "kimi-coding"
        "arcee.ai" in url -> "arcee"
        "minimaxi.com" in url -> "minimax-cn"
        "minimax.io" in url -> "minimax"
        else -> ""
    }
}

/**
 * _coerce_summary_content - 将总结输出规范化为安全字符串
 */
private fun coerceSummaryContent(content: Any?): String {
    return (content?.toString() ?: "").trim()
}

/**
 * _ensure_summary_prefix - 确保总结文本包含 [CONTEXT SUMMARY]: 前缀
 */
private fun ensureSummaryPrefix(summary: String): String {
    val text = summary.trim()
    if (text.startsWith("[CONTEXT SUMMARY]:")) return text
    return if (text.isEmpty()) "[CONTEXT SUMMARY]:" else "[CONTEXT SUMMARY]: $text"
}

/**
 * process_entry_async - 异步处理单个 JSONL 条目
 */
suspend fun TrajectoryCompressor.processEntryAsync(
    entry: Map<String, Any?>
): Pair<Map<String, Any?>, TrajectoryMetrics> {
    val conversations = entry["conversations"] as? List<Map<String, String>>
    if (conversations == null) {
        return entry to TrajectoryMetrics()
    }

    val (compressedTrajectory, metrics) = compressTrajectoryAsync(conversations)
    val result = entry.toMutableMap()
    result["conversations"] = compressedTrajectory

    // 如果启用了逐轨迹指标且发生了压缩，添加压缩元数据
    if (metrics.wasCompressed) {
        result["compression_metrics"] = metrics.toDict()
    }

    return result to metrics
}

/**
 * process_entry - 同步处理单个 JSONL 条目
 */
fun TrajectoryCompressor.processEntry(
    entry: Map<String, Any?>
): Pair<Map<String, Any?>, TrajectoryMetrics> {
    val conversations = entry["conversations"] as? List<Map<String, String>>
    if (conversations == null) {
        return entry to TrajectoryMetrics()
    }

    val (compressedTrajectory, metrics) = compressTrajectory(conversations)
    val result = entry.toMutableMap()
    result["conversations"] = compressedTrajectory

    if (metrics.wasCompressed) {
        result["compression_metrics"] = metrics.toDict()
    }

    return result to metrics
}

/**
 * _print_summary - 打印压缩摘要统计
 */
private fun TrajectoryCompressor.printSummary() {
    val m = aggregateMetrics.toDict()
    val summary = m["summary"] as Map<String, Any?>
    val tokens = m["tokens"] as Map<String, Any?>
    val turns = m["turns"] as Map<String, Any?>
    val averages = m["averages"] as Map<String, Any?>
    val summ = m["summarization"] as Map<String, Any?>
    val processing = m["processing"] as Map<String, Any?>

    val total = (summary["total_trajectories"] as Number).toInt()
    val compressed = (summary["trajectories_compressed"] as Number).toInt()
    val skipped = (summary["trajectories_skipped_under_target"] as Number).toInt()
    val overLimit = (summary["trajectories_still_over_limit"] as Number).toInt()
    val failed = (summary["trajectories_failed"] as Number).toInt()

    val tokensBefore = (tokens["total_before"] as Number).toInt()
    val tokensAfter = (tokens["total_after"] as Number).toInt()
    val tokensSaved = (tokens["total_saved"] as Number).toInt()

    val compressedPct = compressed.toDouble() / maxOf(total, 1) * 100
    val skippedPct = skipped.toDouble() / maxOf(total, 1) * 100
    val overLimitPct = overLimit.toDouble() / maxOf(total, 1) * 100

    val duration = (processing["duration_seconds"] as Number).toDouble()
    val timeStr = if (duration > 60) "${String.format("%.1f", duration / 60)} minutes"
                  else "${String.format("%.1f", duration)} seconds"
    val throughput = total / maxOf(duration, 0.001)

    tcLogger.info(buildString {
        appendLine("")
        appendLine("╔${"═".repeat(70)}╗")
        appendLine("║${"TRAJECTORY COMPRESSION REPORT".centerPad(70)}║")
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  📁 TRAJECTORIES${" ".repeat(54)}║")
        appendLine("║${"─".repeat(70)}║")
        appendLine("║    Total Processed:        ${"%,10d".format(total)}${" ".repeat(32)}║")
        appendLine("║    ├─ Compressed:          ${"%,10d".format(compressed)}  (${String.format("%5.1f", compressedPct)}%)${" ".repeat(18)}║")
        appendLine("║    ├─ Skipped (under limit):${"%,9d".format(skipped)}  (${String.format("%5.1f", skippedPct)}%)${" ".repeat(18)}║")
        appendLine("║    ├─ Still over limit:    ${"%,10d".format(overLimit)}  (${String.format("%5.1f", overLimitPct)}%)${" ".repeat(18)}║")
        appendLine("║    └─ Failed:              ${"%,10d".format(failed)}${" ".repeat(32)}║")
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  🔢 TOKENS${" ".repeat(60)}║")
        appendLine("║${"─".repeat(70)}║")
        appendLine("║    Before Compression:     ${"%,15d".format(tokensBefore)} tokens${" ".repeat(21)}║")
        appendLine("║    After Compression:      ${"%,15d".format(tokensAfter)} tokens${" ".repeat(21)}║")
        appendLine("║    Total Saved:            ${"%,15d".format(tokensSaved)} tokens${" ".repeat(21)}║")
        appendLine("║    Overall Compression:    ${"%14.1f%%".format((tokens["overall_compression_ratio"] as Number).toDouble() * 100)}${" ".repeat(28)}║")
        if (tokensBefore > 0) {
            val savingsPct = tokensSaved.toDouble() / tokensBefore * 100
            appendLine("║    Space Savings:          ${"%14.1f%%".format(savingsPct)}${" ".repeat(28)}║")
        }
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  💬 CONVERSATION TURNS${" ".repeat(48)}║")
        appendLine("║${"─".repeat(70)}║")
        appendLine("║    Before Compression:     ${"%,15d".format((turns["total_before"] as Number).toInt())} turns${" ".repeat(22)}║")
        appendLine("║    After Compression:      ${"%,15d".format((turns["total_after"] as Number).toInt())} turns${" ".repeat(22)}║")
        appendLine("║    Total Removed:          ${"%,15d".format((turns["total_removed"] as Number).toInt())} turns${" ".repeat(22)}║")
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  📈 AVERAGES (Compressed Trajectories Only)${" ".repeat(27)}║")
        appendLine("║${"─".repeat(70)}║")
        if (compressed > 0) {
            appendLine("║    Avg Compression Ratio:  ${"%14.1f%%".format((averages["avg_compression_ratio"] as Number).toDouble() * 100)}${" ".repeat(28)}║")
            appendLine("║    Avg Tokens Saved:       ${"%,14.0f".format((averages["avg_tokens_saved_per_compressed"] as Number).toDouble())}${" ".repeat(28)}║")
            appendLine("║    Avg Turns Removed:      ${"%14.1f".format((averages["avg_turns_removed_per_compressed"] as Number).toDouble())}${" ".repeat(28)}║")
        } else {
            appendLine("║    No trajectories were compressed${" ".repeat(38)}║")
        }
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  🤖 SUMMARIZATION API${" ".repeat(49)}║")
        appendLine("║${"─".repeat(70)}║")
        appendLine("║    API Calls Made:         ${"%,15d".format((summ["total_api_calls"] as Number).toInt())}${" ".repeat(27)}║")
        appendLine("║    Errors:                 ${"%,15d".format((summ["total_errors"] as Number).toInt())}${" ".repeat(27)}║")
        appendLine("║    Success Rate:           ${"%14.1f%%".format((summ["success_rate"] as Number).toDouble() * 100)}${" ".repeat(28)}║")
        appendLine("╠${"═".repeat(70)}╣")
        appendLine("║  ⏱️  PROCESSING TIME${" ".repeat(51)}║")
        appendLine("║${"─".repeat(70)}║")
        appendLine("║    Duration:               ${"%20s".format(timeStr)}${" ".repeat(22)}║")
        appendLine("║    Throughput:             ${"%15.1f".format(throughput)} traj/sec${" ".repeat(18)}║")
        appendLine("╚${"═".repeat(70)}╝")
    })
}

private fun String.centerPad(width: Int): String {
    val pad = maxOf(0, width - this.length)
    val left = pad / 2
    return " ".repeat(left) + this + " ".repeat(pad - left)
}

/**
 * main - 入口函数（Android 版：直接处理文件/目录）
 */
suspend fun trajectoryCompressorMain(
    input: String,
    output: String? = null,
    configPath: String? = null,
    targetMaxTokens: Int? = null,
    tokenizer: String? = null,
    samplePercent: Double? = null,
    seed: Int = 42,
    dryRun: Boolean = false) {
    tcLogger.info("Trajectory Compressor")
    tcLogger.info("=".repeat(60))

    // 加载配置
    val compressionConfig = if (configPath != null && File(configPath).exists()) {
        tcLogger.info("Loading config from $configPath")
        CompressionConfig.fromYaml(configPath)
    } else {
        tcLogger.warning("Config not found, using defaults")
        CompressionConfig()
    }.let { cfg ->
        var c = cfg
        if (targetMaxTokens != null) c = c.copy(targetMaxTokens = targetMaxTokens)
        if (tokenizer != null) c = c.copy(tokenizerName = tokenizer)
        c
    }

    // 验证 samplePercent
    if (samplePercent != null && (samplePercent <= 0 || samplePercent > 100)) {
        tcLogger.warning("samplePercent must be between 1 and 100, got $samplePercent")
        return
    }

    val inputPath = File(input)
    if (!inputPath.exists()) {
        tcLogger.warning("Input not found: $input")
        return
    }

    val isFileInput = inputPath.isFile

    if (isFileInput) {
        val outputPath = if (output != null) File(output)
            else File(inputPath.parent, "${inputPath.nameWithoutExtension}${compressionConfig.outputSuffix}.${inputPath.extension}")

        if (dryRun) {
            tcLogger.info("DRY RUN - would process: $inputPath -> $outputPath")
            return
        }

        // 创建临时目录处理
        val tempDir = createTempDir("trajectory_compressor_")
        val tempInputDir = File(tempDir, "input").also { it.mkdirs() }
        val tempOutputDir = File(tempDir, "output")

        // 复制输入到临时文件
        val tempInputFile = File(tempInputDir, inputPath.name)
        inputPath.copyTo(tempInputFile)

        // 初始化压缩器并处理
        val compressor = TrajectoryCompressor(compressionConfig)
        compressor.processDirectoryAsync(tempInputDir, tempOutputDir)

        // 复制结果到输出路径
        outputPath.parentFile.mkdirs()
        val outputFiles = tempOutputDir.listFiles()?.filter { it.extension == "jsonl" }?.sortedBy { it.name } ?: emptyList()
        outputPath.writeText(
            outputFiles.flatMap { it.readLines(Charsets.UTF_8) }.joinToString("\n"),
            Charsets.UTF_8
        )

        // 复制指标文件
        val metricsFile = File(tempOutputDir, "compression_metrics.json")
        if (metricsFile.exists()) {
            val metricsOutput = File(outputPath.parent, "${outputPath.nameWithoutExtension}_metrics.json")
            metricsFile.copyTo(metricsOutput, overwrite = true)
            tcLogger.info("Metrics saved to ${metricsOutput.absolutePath}")
        }

        tempDir.deleteRecursively()
        tcLogger.info("Compression complete! Output: $outputPath")
    } else {
        // 目录输入
        val outputPath = if (output != null) File(output)
            else File(inputPath.parent, "${inputPath.name}${compressionConfig.outputSuffix}")

        if (dryRun) {
            tcLogger.info("DRY RUN - would process directory: $inputPath -> $outputPath")
            return
        }

        val compressor = TrajectoryCompressor(compressionConfig)
        compressor.processDirectoryAsync(inputPath, outputPath)
        tcLogger.info("Compression complete!")
    }
}