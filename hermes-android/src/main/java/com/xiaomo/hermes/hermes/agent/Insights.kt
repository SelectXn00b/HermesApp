package com.xiaomo.hermes.hermes.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Insights - 使用洞察/统计
 * 1:1 对齐 hermes/agent/insights.py
 *
 * 跟踪 API 调用、token 使用、费用等统计数据。
 */

data class UsageEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "",
    val model: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cost: Double = 0.0,
    val durationMs: Long = 0L,
    val success: Boolean = true,
    val errorType: String? = null,
    val platform: String = "",
    val sessionId: String = "",
    val toolName: String = "",
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val costUsd: Double = cost,
    val durationSec: Double = durationMs / 1000.0,
    val date: String = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())

data class ProviderStats(
    var totalCalls: Int = 0,
    var successfulCalls: Int = 0,
    var failedCalls: Int = 0,
    var totalInputTokens: Int = 0,
    var totalOutputTokens: Int = 0,
    var totalCost: Double = 0.0,
    var totalDurationMs: Long = 0L,
    val errors: MutableMap<String, Int> = mutableMapOf()
) {
    val successRate: Double get() = if (totalCalls > 0) successfulCalls.toDouble() / totalCalls else 0.0
    val averageDurationMs: Double get() = if (totalCalls > 0) totalDurationMs.toDouble() / totalCalls else 0.0
}

class Insights(
    private val dataDir: String = "."
) {

    private val gson = Gson()
    private val entries: MutableList<UsageEntry> = mutableListOf()
    private val providerStats: ConcurrentHashMap<String, ProviderStats> = ConcurrentHashMap()

    /**
     * 记录一次 API 调用
     */
    fun record(entry: UsageEntry) {
        synchronized(entries) {
            entries.add(entry)
        }

        val stats = providerStats.getOrPut(entry.provider) { ProviderStats() }
        synchronized(stats) {
            stats.totalCalls++
            if (entry.success) stats.successfulCalls++ else stats.failedCalls++
            stats.totalInputTokens += entry.inputTokens
            stats.totalOutputTokens += entry.outputTokens
            stats.totalCost += entry.cost
            stats.totalDurationMs += entry.durationMs
            if (entry.errorType != null) {
                stats.errors[entry.errorType] = (stats.errors[entry.errorType] ?: 0) + 1
            }
        }
    }

    /**
     * 获取总统计
     */
    fun getTotalStats(): ProviderStats {
        val total = ProviderStats()
        for (stats in providerStats.values) {
            total.totalCalls += stats.totalCalls
            total.successfulCalls += stats.successfulCalls
            total.failedCalls += stats.failedCalls
            total.totalInputTokens += stats.totalInputTokens
            total.totalOutputTokens += stats.totalOutputTokens
            total.totalCost += stats.totalCost
            total.totalDurationMs += stats.totalDurationMs
        }
        return total
    }

    /**
     * 获取指定 provider 的统计
     */
    fun getProviderStats(provider: String): ProviderStats? {
        return providerStats[provider]
    }

    /**
     * 获取所有 provider 的统计
     */
    fun getAllProviderStats(): Map<String, ProviderStats> {
        return providerStats.toMap()
    }

    /**
     * 获取指定时间范围内的使用记录
     */
    fun getEntriesSince(sinceMs: Long): List<UsageEntry> {
        synchronized(entries) {
            return entries.filter { it.timestamp >= sinceMs }
        }
    }

    /**
     * 获取今天的使用统计
     */
    fun getTodayStats(): ProviderStats {
        val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000)
        val todayEntries = getEntriesSince(todayStart)
        val stats = ProviderStats()
        for (entry in todayEntries) {
            stats.totalCalls++
            if (entry.success) stats.successfulCalls++ else stats.failedCalls++
            stats.totalInputTokens += entry.inputTokens
            stats.totalOutputTokens += entry.outputTokens
            stats.totalCost += entry.cost
            stats.totalDurationMs += entry.durationMs
        }
        return stats
    }

    /**
     * 生成统计报告
     */
    fun generateReport(): String {
        val sb = StringBuilder()
        val total = getTotalStats()

        sb.appendLine("=== Usage Insights ===")
        sb.appendLine("Total calls: ${total.totalCalls}")
        sb.appendLine("Success rate: ${String.format("%.1f", total.successRate * 100)}%")
        sb.appendLine("Total tokens: ${total.totalInputTokens} in / ${total.totalOutputTokens} out")
        sb.appendLine("Total cost: $${String.format("%.4f", total.totalCost)}")
        sb.appendLine("Avg duration: ${String.format("%.0f", total.averageDurationMs)}ms")

        sb.appendLine("\n--- By Provider ---")
        for ((provider, stats) in providerStats) {
            sb.appendLine("$provider: ${stats.totalCalls} calls, $${String.format("%.4f", stats.totalCost)}, ${String.format("%.1f", stats.successRate * 100)}% success")
        }

        return sb.toString().trim()
    }

    /**
     * 保存到 JSON 文件
     */
    fun save(filename: String = "insights.json") {
        val file = File(dataDir, filename)
        file.parentFile?.mkdirs()
        val data = mapOf(
            "entries" to entries,
            "providerStats" to providerStats.mapValues { (_, stats) ->
                mapOf(
                    "totalCalls" to stats.totalCalls,
                    "successfulCalls" to stats.successfulCalls,
                    "failedCalls" to stats.failedCalls,
                    "totalInputTokens" to stats.totalInputTokens,
                    "totalOutputTokens" to stats.totalOutputTokens,
                    "totalCost" to stats.totalCost,
                    "totalDurationMs" to stats.totalDurationMs,
                    "errors" to stats.errors
                )
            }
        )
        file.writeText(gson.toJson(data), Charsets.UTF_8)
    }

    /**
     * 从 JSON 文件加载
     */
    fun load(filename: String = "insights.json") {
        val file = File(dataDir, filename)
        if (!file.exists()) return
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(file.readText(Charsets.UTF_8), type)

            @Suppress("UNCHECKED_CAST")
            val loadedEntries = data["entries"] as? List<Map<String, Any>> ?: emptyList()
            synchronized(entries) {
                entries.clear()
                for (entry in loadedEntries) {
                    entries.add(
                        UsageEntry(
                            timestamp = (entry["timestamp"] as? Number)?.toLong() ?: 0L,
                            provider = entry["provider"] as? String ?: "",
                            model = entry["model"] as? String ?: "",
                            inputTokens = (entry["inputTokens"] as? Number)?.toInt() ?: 0,
                            outputTokens = (entry["outputTokens"] as? Number)?.toInt() ?: 0,
                            cost = (entry["cost"] as? Number)?.toDouble() ?: 0.0,
                            durationMs = (entry["durationMs"] as? Number)?.toLong() ?: 0L,
                            success = entry["success"] as? Boolean ?: true,
                            errorType = entry["errorType"] as? String
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 加载失败不影响运行
        }
    }

    /**
     * 清空统计数据
     */
    fun clear() {
        synchronized(entries) { entries.clear() }
        providerStats.clear()
    }

    // ── Session analytics (ported from agent/insights.py) ───────────

    /** Generate complete insights report from session data. */
    fun generate(days: Int = 30, source: String? = null): Map<String, Any?> {
        val cutoff = System.currentTimeMillis() / 1000 - (days * 86400)
        val sessions = getSessions(cutoff, source)
        val messageStats = getMessageStats(cutoff, source)
        val toolUsage = getToolUsage(cutoff, source)

        return mapOf(
            "overview" to computeOverview(sessions, messageStats),
            "models" to computeModelBreakdown(sessions),
            "tools" to computeToolBreakdown(toolUsage),
            "platforms" to computePlatformBreakdown(sessions),
            "activity" to computeActivityPatterns(sessions),
            "top_sessions" to computeTopSessions(sessions),
            "period_days" to days)
    }

    /** Fetch sessions within time window. */
    fun getSessions(cutoff: Long, source: String? = null): List<Map<String, Any?>> {
        synchronized(entries) {
            return entries
                .filter { it.timestamp / 1000 >= cutoff }
                .map { e -> mapOf<String, Any?>(
                    "model" to e.model, "source" to e.provider,
                    "input_tokens" to e.inputTokens, "output_tokens" to e.outputTokens,
                    "started_at" to e.timestamp / 1000, "ended_at" to (e.timestamp + e.durationMs) / 1000)}
        }
    }

    /** Get aggregate message statistics. */
    fun getMessageStats(cutoff: Long, source: String? = null): Map<String, Int> {
        val sessions = getSessions(cutoff, source)
        return mapOf(
            "total_messages" to sessions.size,
            "user_messages" to sessions.size / 2,
            "assistant_messages" to sessions.size / 2)
    }

    /** Get tool call counts. */
    fun getToolUsage(cutoff: Long, source: String? = null): List<Map<String, Any?>> =
        _getToolUsage(cutoff.toDouble(), source)

    /** Compute high-level overview statistics. */
    fun computeOverview(sessions: List<Map<String, Any?>>, messageStats: Map<String, Int>): Map<String, Any?> {
        val totalInput = sessions.sumOf { (it["input_tokens"] as? Number)?.toLong() ?: 0L }
        val totalOutput = sessions.sumOf { (it["output_tokens"] as? Number)?.toLong() ?: 0L }
        return mapOf(
            "total_sessions" to sessions.size,
            "total_input_tokens" to totalInput,
            "total_output_tokens" to totalOutput,
            "total_tokens" to totalInput + totalOutput,
            "total_messages" to (messageStats["total_messages"] ?: 0))
    }

    /** Break down usage by model. */
    fun computeModelBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val grouped = sessions.groupBy { (it["model"] as? String) ?: "unknown" }
        return grouped.map { (model, ss) ->
            val displayModel = if ("/" in model) model.split("/").last() else model
            mapOf(
                "model" to displayModel,
                "sessions" to ss.size,
                "input_tokens" to ss.sumOf { (it["input_tokens"] as? Number)?.toInt() ?: 0 },
                "output_tokens" to ss.sumOf { (it["output_tokens"] as? Number)?.toInt() ?: 0 })
        }.sortedByDescending { (it["sessions"] as? Int) ?: 0 }
    }

    /** Break down usage by platform. */
    fun computePlatformBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val grouped = sessions.groupBy { (it["source"] as? String) ?: "unknown" }
        return grouped.map { (platform, ss) ->
            mapOf(
                "platform" to platform,
                "sessions" to ss.size,
                "input_tokens" to ss.sumOf { (it["input_tokens"] as? Number)?.toInt() ?: 0 },
                "output_tokens" to ss.sumOf { (it["output_tokens"] as? Number)?.toInt() ?: 0 })
        }.sortedByDescending { (it["sessions"] as? Int) ?: 0 }
    }

    /** Process tool usage data into ranked list. */
    fun computeToolBreakdown(toolUsage: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val totalCalls = toolUsage.sumOf { (it["count"] as? Number)?.toInt() ?: 0 }
        return toolUsage.map { t ->
            val count = (t["count"] as? Number)?.toInt() ?: 0
            mapOf(
                "tool" to (t["tool_name"] as? String ?: "unknown"),
                "count" to count,
                "percentage" to if (totalCalls > 0) count.toDouble() / totalCalls * 100 else 0.0)
        }.sortedByDescending { (it["count"] as? Int) ?: 0 }
    }

    /** Analyze activity patterns by day of week and hour. */
    fun computeActivityPatterns(sessions: List<Map<String, Any?>>): Map<String, Any?> {
        val dayCounts = IntArray(7)
        val hourCounts = IntArray(24)
        for (s in sessions) {
            val ts = (s["started_at"] as? Number)?.toLong() ?: continue
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ts * 1000
            dayCounts[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]++
            hourCounts[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
        }
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return mapOf(
            "by_day" to days.zip(dayCounts.toList()).toMap(),
            "by_hour" to (0..23).map { "$it:00" }.zip(hourCounts.toList()).toMap())
    }

    /** Find notable sessions (longest, most messages, most tokens). */
    fun computeTopSessions(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return sessions
            .sortedByDescending { (it["input_tokens"] as? Number)?.toInt() ?: 0 }
            .take(5)
            .map { s ->
                val started = (s["started_at"] as? Number)?.toLong() ?: 0
                val ended = (s["ended_at"] as? Number)?.toLong() ?: 0
                mapOf(
                    "model" to s["model"],
                    "duration_seconds" to (ended - started),
                    "input_tokens" to s["input_tokens"],
                    "output_tokens" to s["output_tokens"])
            }
    }


    /** Check if a model has known pricing. */
    fun hasKnownPricing(modelName: String, provider: String? = null): Boolean {
        // Android: simplified check
        return modelName.isNotEmpty() && !modelName.startsWith("custom")
    }

    /** Estimate USD cost for a model/token tuple. */
    fun estimateCost(model: String, inputTokens: Int, outputTokens: Int): Double {
        // Simplified: $0.002/1K input, $0.006/1K output for most models
        return (inputTokens * 0.002 + outputTokens * 0.006) / 1000
    }

    /** Format seconds into a human-readable duration string. */
    fun formatDuration(seconds: Double): String {
        val s = seconds.toLong()
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}m"
            else -> "${s / 86400}d ${(s % 86400) / 3600}h"
        }
    }

    /** Create simple horizontal bar chart strings from values. */
    fun barChart(values: List<Int>, maxWidth: Int = 20): List<String> {
        val peak = values.maxOrNull() ?: 1
        if (peak == 0) return values.map { "" }
        return values.map { v ->
            if (v > 0) "█".repeat(maxOf(1, (v.toDouble() / peak * maxWidth).toInt())) else ""
        }
    }

    /** Get token summary from a list of usage entries. */
    fun getTokenSummary(entries: List<UsageEntry>): Map<String, Long> {
        return mapOf(
            "total_input_tokens" to entries.sumOf { it.inputTokens.toLong() },
            "total_output_tokens" to entries.sumOf { it.outputTokens.toLong() },
            "total_cache_read" to entries.sumOf { it.cacheReadTokens.toLong() },
            "total_cache_write" to entries.sumOf { it.cacheWriteTokens.toLong() },
            "total_tokens" to entries.sumOf { (it.inputTokens + it.outputTokens).toLong() })
    }

    /** Get model breakdown from usage entries. */
    fun getModelBreakdown(entries: List<UsageEntry>): List<Map<String, Any?>> {
        val grouped = entries.groupBy { it.model }
        return grouped.map { (model, group) ->
            mapOf(
                "model" to model,
                "count" to group.size,
                "input_tokens" to group.sumOf { it.inputTokens },
                "output_tokens" to group.sumOf { it.outputTokens },
                "cost" to group.sumOf { it.costUsd })
        }.sortedByDescending { it["count"] as Int }
    }

    /** Get platform breakdown from usage entries. */
    fun getPlatformBreakdown(entries: List<UsageEntry>): List<Map<String, Any?>> {
        val grouped = entries.groupBy { it.platform }
        return grouped.map { (platform, group) ->
            mapOf(
                "platform" to platform,
                "count" to group.size,
                "input_tokens" to group.sumOf { it.inputTokens },
                "output_tokens" to group.sumOf { it.outputTokens })
        }.sortedByDescending { it["count"] as Int }
    }

    /** Compute top tools by usage. */
    fun getTopTools(entries: List<UsageEntry>, limit: Int = 10): List<Map<String, Any?>> {
        return entries
            .filter { it.toolName.isNotEmpty() }
            .groupBy { it.toolName }
            .map { (tool, group) -> mapOf("tool" to tool, "count" to group.size) }
            .sortedByDescending { it["count"] as Int }
            .take(limit)
    }

    /** Get session duration stats. */
    fun getSessionDurationStats(entries: List<UsageEntry>): Map<String, Any?> {
        val durations = entries.map { it.durationSec }.filter { it > 0 }
        if (durations.isEmpty()) return mapOf("count" to 0)
        return mapOf(
            "count" to durations.size,
            "avg" to durations.average(),
            "min" to durations.minOrNull(),
            "max" to durations.maxOrNull(),
            "total" to durations.sum())
    }

    /** Compute overview stats from all entries. */
    fun computeOverview(entries: List<UsageEntry>): Map<String, Any?> {
        return mapOf(
            "total_sessions" to entries.map { it.sessionId }.distinct().size,
            "total_messages" to entries.size,
            "total_cost" to entries.sumOf { it.costUsd },
            "unique_platforms" to entries.map { it.platform }.distinct().size,
            "unique_models" to entries.map { it.model }.distinct().size)
    }

    /** Format cost as USD string. */
    fun formatCost(usd: Double): String {
        return when {
            usd < 0.01 -> String.format("$%.4f", usd)
            usd < 1.0 -> String.format("$%.3f", usd)
            else -> String.format("$%.2f", usd)
        }
    }

    /** Get activity trend (daily counts). */
    fun getActivityTrend(entries: List<UsageEntry>, days: Int = 7): List<Map<String, Any?>> {
        val now = java.time.LocalDate.now()
        return (0 until days).reversed().map { d ->
            val date = now.minusDays(d.toLong())
            val dayEntries = entries.filter { it.date == date.toString() }
            mapOf("date" to date.toString(), "count" to dayEntries.size)
        }
    }


    /** Fetch sessions within the time window. */
    fun _getSessions(cutoff: Double, source: String? = null): List<Map<String, Any?>> {
        val cutoffMs = (cutoff * 1000).toLong()
        synchronized(entries) {
            return entries
                .filter { it.timestamp >= cutoffMs }
                .filter { source == null || it.provider == source }
                .map { e -> mapOf<String, Any?>(
                    "id" to "${e.provider}_${e.timestamp}",
                    "source" to e.provider,
                    "model" to e.model,
                    "started_at" to e.timestamp / 1000.0,
                    "ended_at" to (e.timestamp + e.durationMs) / 1000.0,
                    "message_count" to 1,
                    "tool_call_count" to if (e.toolName.isNotEmpty()) 1 else 0,
                    "input_tokens" to e.inputTokens,
                    "output_tokens" to e.outputTokens,
                    "cache_read_tokens" to e.cacheReadTokens,
                    "cache_write_tokens" to e.cacheWriteTokens,
                    "billing_provider" to e.provider,
                    "billing_base_url" to null,
                    "billing_mode" to null,
                    "estimated_cost_usd" to e.costUsd,
                    "actual_cost_usd" to null,
                    "cost_status" to if (hasKnownPricing(e.model)) "estimated" else "unknown",
                    "cost_source" to "default_pricing")}
        }
    }

    /** Get tool call counts from messages. No DB on Android; returns from in-memory entries. */
    fun _getToolUsage(cutoff: Double, source: String? = null): List<Map<String, Any?>> {
        val cutoffMs = (cutoff * 1000).toLong()
        synchronized(entries) {
            return entries
                .filter { it.timestamp >= cutoffMs }
                .filter { source == null || it.provider == source }
                .filter { it.toolName.isNotEmpty() }
                .groupBy { it.toolName }
                .map { (name, group) -> mapOf("tool_name" to name, "count" to group.size) }
                .sortedByDescending { (it["count"] as? Int) ?: 0 }
        }
    }

    /** Get aggregate message statistics. */
    fun _getMessageStats(cutoff: Double, source: String? = null): Map<String, Any?> {
        val sessions = _getSessions(cutoff, source)
        val totalMessages = sessions.sumOf { (it["message_count"] as? Number)?.toInt() ?: 0 }
        return mapOf(
            "total_messages" to totalMessages,
            "user_messages" to totalMessages / 2,
            "assistant_messages" to totalMessages / 2,
            "tool_messages" to 0)
    }

    /** Compute high-level overview statistics. */
    fun _computeOverview(sessions: List<Map<String, Any?>>, messageStats: Map<String, Any?>): Map<String, Any?> {
        val totalInput = sessions.sumOf { (it["input_tokens"] as? Number)?.toLong() ?: 0L }
        val totalOutput = sessions.sumOf { (it["output_tokens"] as? Number)?.toLong() ?: 0L }
        val totalCacheRead = sessions.sumOf { (it["cache_read_tokens"] as? Number)?.toLong() ?: 0L }
        val totalCacheWrite = sessions.sumOf { (it["cache_write_tokens"] as? Number)?.toLong() ?: 0L }
        val totalTokens = totalInput + totalOutput + totalCacheRead + totalCacheWrite
        val totalToolCalls = sessions.sumOf { (it["tool_call_count"] as? Number)?.toLong() ?: 0L }
        val totalMessages = sessions.sumOf { (it["message_count"] as? Number)?.toLong() ?: 0L }

        var totalCost = 0.0
        val modelsWithPricing = mutableSetOf<String>()
        val modelsWithoutPricing = mutableSetOf<String>()
        for (s in sessions) {
            val model = s["model"] as? String ?: ""
            val input = (s["input_tokens"] as? Number)?.toInt() ?: 0
            val output = (s["output_tokens"] as? Number)?.toInt() ?: 0
            totalCost += estimateCost(model, input, output)
            val display = if ("/" in model) model.split("/").last() else model.ifEmpty { "unknown" }
            if (hasKnownPricing(model)) modelsWithPricing.add(display) else modelsWithoutPricing.add(display)
        }

        val durations = sessions.mapNotNull { s ->
            val start = (s["started_at"] as? Number)?.toDouble()
            val end = (s["ended_at"] as? Number)?.toDouble()
            if (start != null && end != null && end > start) end - start else null
        }
        val totalHours = if (durations.isNotEmpty()) durations.sum() / 3600 else 0.0
        val avgDuration = if (durations.isNotEmpty()) durations.average() else 0.0

        val timestamps = sessions.mapNotNull { (it["started_at"] as? Number)?.toDouble() }

        return mapOf(
            "total_sessions" to sessions.size,
            "total_messages" to totalMessages,
            "total_tool_calls" to totalToolCalls,
            "total_input_tokens" to totalInput,
            "total_output_tokens" to totalOutput,
            "total_cache_read_tokens" to totalCacheRead,
            "total_cache_write_tokens" to totalCacheWrite,
            "total_tokens" to totalTokens,
            "estimated_cost" to totalCost,
            "actual_cost" to 0.0,
            "total_hours" to totalHours,
            "avg_session_duration" to avgDuration,
            "avg_messages_per_session" to if (sessions.isNotEmpty()) totalMessages.toDouble() / sessions.size else 0.0,
            "avg_tokens_per_session" to if (sessions.isNotEmpty()) totalTokens.toDouble() / sessions.size else 0.0,
            "user_messages" to (messageStats["user_messages"] ?: 0),
            "assistant_messages" to (messageStats["assistant_messages"] ?: 0),
            "tool_messages" to (messageStats["tool_messages"] ?: 0),
            "date_range_start" to timestamps.minOrNull(),
            "date_range_end" to timestamps.maxOrNull(),
            "models_with_pricing" to modelsWithPricing.sorted(),
            "models_without_pricing" to modelsWithoutPricing.sorted(),
            "unknown_cost_sessions" to 0,
            "included_cost_sessions" to sessions.size)
    }

    /** Break down usage by model. */
    fun _computeModelBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val modelData = mutableMapOf<String, MutableMap<String, Any>>()
        for (s in sessions) {
            val model = s["model"] as? String ?: "unknown"
            val displayModel = if ("/" in model) model.split("/").last() else model
            val d = modelData.getOrPut(displayModel) { mutableMapOf(
                "sessions" to 0, "input_tokens" to 0, "output_tokens" to 0,
                "cache_read_tokens" to 0, "cache_write_tokens" to 0,
                "total_tokens" to 0, "tool_calls" to 0, "cost" to 0.0)}
            val inp = (s["input_tokens"] as? Number)?.toInt() ?: 0
            val out = (s["output_tokens"] as? Number)?.toInt() ?: 0
            val cr = (s["cache_read_tokens"] as? Number)?.toInt() ?: 0
            val cw = (s["cache_write_tokens"] as? Number)?.toInt() ?: 0
            d["sessions"] = (d["sessions"] as Int) + 1
            d["input_tokens"] = (d["input_tokens"] as Int) + inp
            d["output_tokens"] = (d["output_tokens"] as Int) + out
            d["cache_read_tokens"] = (d["cache_read_tokens"] as Int) + cr
            d["cache_write_tokens"] = (d["cache_write_tokens"] as Int) + cw
            d["total_tokens"] = (d["total_tokens"] as Int) + inp + out + cr + cw
            d["tool_calls"] = (d["tool_calls"] as Int) + ((s["tool_call_count"] as? Number)?.toInt() ?: 0)
            d["cost"] = (d["cost"] as Double) + estimateCost(model, inp, out)
            d["has_pricing"] = hasKnownPricing(model)
        }
        return modelData.map { (model, data) ->
            @Suppress("UNCHECKED_CAST")
            mapOf("model" to model) + data as Map<String, Any?>
        }.sortedWith(compareByDescending<Map<String, Any?>> { (it["total_tokens"] as? Number)?.toInt() ?: 0 }
            .thenByDescending { (it["sessions"] as? Number)?.toInt() ?: 0 })
    }

    /** Break down usage by platform/source. */
    fun _computePlatformBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val platformData = mutableMapOf<String, MutableMap<String, Any>>()
        for (s in sessions) {
            val source = s["source"] as? String ?: "unknown"
            val d = platformData.getOrPut(source) { mutableMapOf(
                "sessions" to 0, "messages" to 0, "input_tokens" to 0,
                "output_tokens" to 0, "cache_read_tokens" to 0,
                "cache_write_tokens" to 0, "total_tokens" to 0, "tool_calls" to 0)}
            val inp = (s["input_tokens"] as? Number)?.toInt() ?: 0
            val out = (s["output_tokens"] as? Number)?.toInt() ?: 0
            val cr = (s["cache_read_tokens"] as? Number)?.toInt() ?: 0
            val cw = (s["cache_write_tokens"] as? Number)?.toInt() ?: 0
            d["sessions"] = (d["sessions"] as Int) + 1
            d["messages"] = (d["messages"] as Int) + ((s["message_count"] as? Number)?.toInt() ?: 0)
            d["input_tokens"] = (d["input_tokens"] as Int) + inp
            d["output_tokens"] = (d["output_tokens"] as Int) + out
            d["cache_read_tokens"] = (d["cache_read_tokens"] as Int) + cr
            d["cache_write_tokens"] = (d["cache_write_tokens"] as Int) + cw
            d["total_tokens"] = (d["total_tokens"] as Int) + inp + out + cr + cw
            d["tool_calls"] = (d["tool_calls"] as Int) + ((s["tool_call_count"] as? Number)?.toInt() ?: 0)
        }
        return platformData.map { (platform, data) ->
            @Suppress("UNCHECKED_CAST")
            mapOf("platform" to platform) + data as Map<String, Any?>
        }.sortedByDescending { (it["sessions"] as? Int) ?: 0 }
    }

    /** Process tool usage data into a ranked list with percentages. */
    fun _computeToolBreakdown(toolUsage: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val totalCalls = toolUsage.sumOf { (it["count"] as? Number)?.toInt() ?: 0 }
        return toolUsage.map { t ->
            val count = (t["count"] as? Number)?.toInt() ?: 0
            mapOf(
                "tool" to (t["tool_name"] as? String ?: "unknown"),
                "count" to count,
                "percentage" to if (totalCalls > 0) count.toDouble() / totalCalls * 100 else 0.0)
        }
    }

    /** Analyze activity patterns by day of week and hour. */
    fun _computeActivityPatterns(sessions: List<Map<String, Any?>>): Map<String, Any?> {
        val dayCounts = IntArray(7)
        val hourCounts = IntArray(24)
        val dailyCounts = mutableMapOf<String, Int>()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        for (s in sessions) {
            val ts = (s["started_at"] as? Number)?.toDouble() ?: continue
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = (ts * 1000).toLong()
            // Calendar.DAY_OF_WEEK: 1=Sunday ... 7=Saturday; convert to 0=Monday ... 6=Sunday
            val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
            dayCounts[dow]++
            hourCounts[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
            val dateStr = java.time.Instant.ofEpochMilli((ts * 1000).toLong())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            dailyCounts[dateStr] = (dailyCounts[dateStr] ?: 0) + 1
        }

        val dayBreakdown = (0..6).map { i -> mapOf("day" to dayNames[i], "count" to dayCounts[i]) }
        val hourBreakdown = (0..23).map { i -> mapOf("hour" to i, "count" to hourCounts[i]) }
        val busiestDay = dayBreakdown.maxByOrNull { it["count"] as Int }
        val busiestHour = hourBreakdown.maxByOrNull { it["count"] as Int }
        val activeDays = dailyCounts.size

        // Streak calculation
        var maxStreak = 0
        if (dailyCounts.isNotEmpty()) {
            val allDates = dailyCounts.keys.sorted()
            var currentStreak = 1
            maxStreak = 1
            for (i in 1 until allDates.size) {
                val d1 = java.time.LocalDate.parse(allDates[i - 1])
                val d2 = java.time.LocalDate.parse(allDates[i])
                if (d2.toEpochDay() - d1.toEpochDay() == 1L) {
                    currentStreak++
                    maxStreak = maxOf(maxStreak, currentStreak)
                } else {
                    currentStreak = 1
                }
            }
        }

        return mapOf(
            "by_day" to dayBreakdown,
            "by_hour" to hourBreakdown,
            "busiest_day" to busiestDay,
            "busiest_hour" to busiestHour,
            "active_days" to activeDays,
            "max_streak" to maxStreak)
    }

    /** Find notable sessions (longest, most messages, most tokens). */
    fun _computeTopSessions(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val top = mutableListOf<Map<String, Any?>>()

        // Longest by duration
        val withDuration = sessions.filter { it["started_at"] != null && it["ended_at"] != null }
        if (withDuration.isNotEmpty()) {
            val longest = withDuration.maxByOrNull {
                ((it["ended_at"] as? Number)?.toDouble() ?: 0.0) - ((it["started_at"] as? Number)?.toDouble() ?: 0.0)
            }!!
            val dur = ((longest["ended_at"] as? Number)?.toDouble() ?: 0.0) - ((longest["started_at"] as? Number)?.toDouble() ?: 0.0)
            val started = (longest["started_at"] as? Number)?.toDouble() ?: 0.0
            val dateStr = java.time.Instant.ofEpochMilli((started * 1000).toLong())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            top.add(mapOf(
                "label" to "Longest session",
                "session_id" to (longest["id"] as? String ?: "")?.take(16),
                "value" to formatDuration(dur),
                "date" to dateStr))
        }

        // Most messages
        if (sessions.isNotEmpty()) {
            val mostMsgs = sessions.maxByOrNull { (it["message_count"] as? Number)?.toInt() ?: 0 }!!
            val msgCount = (mostMsgs["message_count"] as? Number)?.toInt() ?: 0
            if (msgCount > 0) {
                val started = (mostMsgs["started_at"] as? Number)?.toDouble() ?: 0.0
                val dateStr = if (started > 0) java.time.Instant.ofEpochMilli((started * 1000).toLong())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else "?"
                top.add(mapOf(
                    "label" to "Most messages",
                    "session_id" to (mostMsgs["id"] as? String ?: "")?.take(16),
                    "value" to "$msgCount msgs",
                    "date" to dateStr))
            }
        }

        // Most tokens
        if (sessions.isNotEmpty()) {
            val mostTokens = sessions.maxByOrNull {
                ((it["input_tokens"] as? Number)?.toInt() ?: 0) + ((it["output_tokens"] as? Number)?.toInt() ?: 0)
            }!!
            val tokenTotal = ((mostTokens["input_tokens"] as? Number)?.toInt() ?: 0) + ((mostTokens["output_tokens"] as? Number)?.toInt() ?: 0)
            if (tokenTotal > 0) {
                val started = (mostTokens["started_at"] as? Number)?.toDouble() ?: 0.0
                val dateStr = if (started > 0) java.time.Instant.ofEpochMilli((started * 1000).toLong())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else "?"
                top.add(mapOf(
                    "label" to "Most tokens",
                    "session_id" to (mostTokens["id"] as? String ?: "")?.take(16),
                    "value" to "%,d tokens".format(tokenTotal),
                    "date" to dateStr))
            }
        }

        // Most tool calls
        if (sessions.isNotEmpty()) {
            val mostTools = sessions.maxByOrNull { (it["tool_call_count"] as? Number)?.toInt() ?: 0 }!!
            val toolCount = (mostTools["tool_call_count"] as? Number)?.toInt() ?: 0
            if (toolCount > 0) {
                val started = (mostTools["started_at"] as? Number)?.toDouble() ?: 0.0
                val dateStr = if (started > 0) java.time.Instant.ofEpochMilli((started * 1000).toLong())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else "?"
                top.add(mapOf(
                    "label" to "Most tool calls",
                    "session_id" to (mostTools["id"] as? String ?: "")?.take(16),
                    "value" to "$toolCount calls",
                    "date" to dateStr))
            }
        }

        return top
    }

    /** Format the insights report for terminal display (CLI). */
    fun formatTerminal(report: Map<String, Any?>): String {
        if (report["empty"] == true) {
            val days = (report["days"] as? Number)?.toInt() ?: 30
            val src = report["source_filter"]?.let { " (source: $it)" } ?: ""
            return "  No sessions found in the last $days days$src."
        }

        val lines = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val o = report["overview"] as? Map<String, Any?> ?: emptyMap()
        val days = (report["days"] as? Number)?.toInt() ?: 30
        val srcFilter = report["source_filter"] as? String

        lines.add("")
        lines.add("  ╔══════════════════════════════════════════════════════════╗")
        lines.add("  ║                    📊 Hermes Insights                    ║")
        var periodLabel = "Last $days days"
        if (srcFilter != null) periodLabel += " ($srcFilter)"
        val padding = 58 - periodLabel.length - 2
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        lines.add("  ║${" ".repeat(leftPad)} $periodLabel ${" ".repeat(rightPad)}║")
        lines.add("  ╚══════════════════════════════════════════════════════════╝")
        lines.add("")

        // Date range
        val rangeStart = (o["date_range_start"] as? Number)?.toDouble()
        val rangeEnd = (o["date_range_end"] as? Number)?.toDouble()
        if (rangeStart != null && rangeEnd != null) {
            val fmt = { ts: Double -> java.time.Instant.ofEpochMilli((ts * 1000).toLong())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }
            lines.add("  Period: ${fmt(rangeStart)} — ${fmt(rangeEnd)}")
            lines.add("")
        }

        // Overview
        lines.add("  📋 Overview")
        lines.add("  " + "─".repeat(56))
        lines.add("  Sessions:          ${"%-12d".format((o["total_sessions"] as? Number)?.toInt() ?: 0)}  Messages:        ${"%,d".format((o["total_messages"] as? Number)?.toLong() ?: 0L)}")
        lines.add("  Tool calls:        ${"%,d".format((o["total_tool_calls"] as? Number)?.toLong() ?: 0L).padEnd(12)}  User messages:   ${"%,d".format((o["user_messages"] as? Number)?.toLong() ?: 0L)}")
        lines.add("  Input tokens:      ${"%,d".format((o["total_input_tokens"] as? Number)?.toLong() ?: 0L).padEnd(12)}  Output tokens:   ${"%,d".format((o["total_output_tokens"] as? Number)?.toLong() ?: 0L)}")
        val cacheTotal = ((o["total_cache_read_tokens"] as? Number)?.toLong() ?: 0L) + ((o["total_cache_write_tokens"] as? Number)?.toLong() ?: 0L)
        if (cacheTotal > 0) {
            lines.add("  Cache read:        ${"%,d".format((o["total_cache_read_tokens"] as? Number)?.toLong() ?: 0L).padEnd(12)}  Cache write:     ${"%,d".format((o["total_cache_write_tokens"] as? Number)?.toLong() ?: 0L)}")
        }
        val cost = (o["estimated_cost"] as? Number)?.toDouble() ?: 0.0
        var costStr = "$%.2f".format(cost)
        if ((o["models_without_pricing"] as? List<*>)?.isNotEmpty() == true) costStr += " *"
        lines.add("  Total tokens:      ${"%,d".format((o["total_tokens"] as? Number)?.toLong() ?: 0L).padEnd(12)}  Est. cost:       $costStr")
        val totalHours = (o["total_hours"] as? Number)?.toDouble() ?: 0.0
        if (totalHours > 0) {
            lines.add("  Active time:       ~${formatDuration(totalHours * 3600).padEnd(11)}  Avg session:     ~${formatDuration((o["avg_session_duration"] as? Number)?.toDouble() ?: 0.0)}")
        }
        lines.add("  Avg msgs/session:  ${"%.1f".format((o["avg_messages_per_session"] as? Number)?.toDouble() ?: 0.0)}")
        lines.add("")

        // Models
        @Suppress("UNCHECKED_CAST")
        val models = report["models"] as? List<Map<String, Any?>> ?: emptyList()
        if (models.isNotEmpty()) {
            lines.add("  🤖 Models Used")
            lines.add("  " + "─".repeat(56))
            lines.add("  ${"Model".padEnd(30)} ${"Sessions".padStart(8)} ${"Tokens".padStart(12)} ${"Cost".padStart(8)}")
            for (m in models) {
                val name = (m["model"] as? String ?: "").take(28)
                val hasPricing = m["has_pricing"] as? Boolean ?: true
                val costCell = if (hasPricing) "$${"%6.2f".format((m["cost"] as? Number)?.toDouble() ?: 0.0)}" else "     N/A"
                lines.add("  ${name.padEnd(30)} ${"%8d".format((m["sessions"] as? Number)?.toInt() ?: 0)} ${"%,12d".format((m["total_tokens"] as? Number)?.toInt() ?: 0)} $costCell")
            }
            if ((o["models_without_pricing"] as? List<*>)?.isNotEmpty() == true) {
                lines.add("  * Cost N/A for custom/self-hosted models")
            }
            lines.add("")
        }

        // Platforms
        @Suppress("UNCHECKED_CAST")
        val platforms = report["platforms"] as? List<Map<String, Any?>> ?: emptyList()
        if (platforms.size > 1 || (platforms.isNotEmpty() && platforms[0]["platform"] != "cli")) {
            lines.add("  📱 Platforms")
            lines.add("  " + "─".repeat(56))
            lines.add("  ${"Platform".padEnd(14)} ${"Sessions".padStart(8)} ${"Messages".padStart(10)} ${"Tokens".padStart(14)}")
            for (p in platforms) {
                lines.add("  ${(p["platform"] as? String ?: "").padEnd(14)} ${"%8d".format((p["sessions"] as? Number)?.toInt() ?: 0)} ${"%,10d".format((p["messages"] as? Number)?.toInt() ?: 0)} ${"%,14d".format((p["total_tokens"] as? Number)?.toInt() ?: 0)}")
            }
            lines.add("")
        }

        // Tools
        @Suppress("UNCHECKED_CAST")
        val tools = report["tools"] as? List<Map<String, Any?>> ?: emptyList()
        if (tools.isNotEmpty()) {
            lines.add("  🔧 Top Tools")
            lines.add("  " + "─".repeat(56))
            lines.add("  ${"Tool".padEnd(28)} ${"Calls".padStart(8)} ${"%".padStart(8)}")
            for (t in tools.take(15)) {
                lines.add("  ${(t["tool"] as? String ?: "").padEnd(28)} ${"%,8d".format((t["count"] as? Number)?.toInt() ?: 0)} ${"%7.1f%%".format((t["percentage"] as? Number)?.toDouble() ?: 0.0)}")
            }
            if (tools.size > 15) lines.add("  ... and ${tools.size - 15} more tools")
            lines.add("")
        }

        // Activity
        @Suppress("UNCHECKED_CAST")
        val act = report["activity"] as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val byDay = act["by_day"] as? List<Map<String, Any?>> ?: emptyList()
        if (byDay.isNotEmpty()) {
            lines.add("  📅 Activity Patterns")
            lines.add("  " + "─".repeat(56))
            val dayValues = byDay.map { (it["count"] as? Number)?.toInt() ?: 0 }
            val bars = barChart(dayValues, maxWidth = 15)
            for (i in byDay.indices) {
                lines.add("  ${byDay[i]["day"]}  ${bars[i].padEnd(15)} ${dayValues[i]}")
            }
            lines.add("")

            @Suppress("UNCHECKED_CAST")
            val byHour = act["by_hour"] as? List<Map<String, Any?>> ?: emptyList()
            val busyHours = byHour.filter { ((it["count"] as? Number)?.toInt() ?: 0) > 0 }
                .sortedByDescending { (it["count"] as? Number)?.toInt() ?: 0 }.take(5)
            if (busyHours.isNotEmpty()) {
                val hourStrs = busyHours.map { h ->
                    val hr = (h["hour"] as? Number)?.toInt() ?: 0
                    val ampm = if (hr < 12) "AM" else "PM"
                    val displayHr = if (hr % 12 == 0) 12 else hr % 12
                    "$displayHr$ampm (${h["count"]})"
                }
                lines.add("  Peak hours: ${hourStrs.joinToString(", ")}")
            }
            val activeDays = (act["active_days"] as? Number)?.toInt() ?: 0
            if (activeDays > 0) lines.add("  Active days: $activeDays")
            val maxStreak = (act["max_streak"] as? Number)?.toInt() ?: 0
            if (maxStreak > 1) lines.add("  Best streak: $maxStreak consecutive days")
            lines.add("")
        }

        // Notable sessions
        @Suppress("UNCHECKED_CAST")
        val topSessions = report["top_sessions"] as? List<Map<String, Any?>> ?: emptyList()
        if (topSessions.isNotEmpty()) {
            lines.add("  🏆 Notable Sessions")
            lines.add("  " + "─".repeat(56))
            for (ts in topSessions) {
                lines.add("  ${(ts["label"] as? String ?: "").padEnd(20)} ${(ts["value"] as? String ?: "").padEnd(18)} (${ts["date"]}, ${ts["session_id"]})")
            }
            lines.add("")
        }

        return lines.joinToString("\n")
    }

    /** Format the insights report for gateway/messaging (shorter). */
    fun formatGateway(report: Map<String, Any?>): String {
        if (report["empty"] == true) {
            val days = (report["days"] as? Number)?.toInt() ?: 30
            return "No sessions found in the last $days days."
        }

        val lines = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val o = report["overview"] as? Map<String, Any?> ?: emptyMap()
        val days = (report["days"] as? Number)?.toInt() ?: 30

        lines.add("📊  Insights** — Last $days days\n")

        val totalSessions = (o["total_sessions"] as? Number)?.toInt() ?: 0
        val totalMessages = (o["total_messages"] as? Number)?.toLong() ?: 0L
        val totalToolCalls = (o["total_tool_calls"] as? Number)?.toLong() ?: 0L
        lines.add(":** $totalSessions | :** ${"%,d".format(totalMessages)} |  calls:** ${"%,d".format(totalToolCalls)}")
        val totalTokens = (o["total_tokens"] as? Number)?.toLong() ?: 0L
        val totalInput = (o["total_input_tokens"] as? Number)?.toLong() ?: 0L
        val totalOutput = (o["total_output_tokens"] as? Number)?.toLong() ?: 0L
        val cacheTotal = ((o["total_cache_read_tokens"] as? Number)?.toLong() ?: 0L) + ((o["total_cache_write_tokens"] as? Number)?.toLong() ?: 0L)
        if (cacheTotal > 0) {
            lines.add(":** ${"%,d".format(totalTokens)} (in: ${"%,d".format(totalInput)} / out: ${"%,d".format(totalOutput)} / cache: ${"%,d".format(cacheTotal)})")
        } else {
            lines.add(":** ${"%,d".format(totalTokens)} (in: ${"%,d".format(totalInput)} / out: ${"%,d".format(totalOutput)})")
        }
        val cost = (o["estimated_cost"] as? Number)?.toDouble() ?: 0.0
        var costNote = ""
        if ((o["models_without_pricing"] as? List<*>)?.isNotEmpty() == true) costNote = " _(excludes custom/self-hosted models)_"
        lines.add(". cost:** $${"%.2f".format(cost)}$costNote")
        val totalHours = (o["total_hours"] as? Number)?.toDouble() ?: 0.0
        if (totalHours > 0) {
            lines.add(" time:** ~${formatDuration(totalHours * 3600)} |  session:** ~${formatDuration((o["avg_session_duration"] as? Number)?.toDouble() ?: 0.0)}")
        }
        lines.add("")

        // Models (top 5)
        @Suppress("UNCHECKED_CAST")
        val models = report["models"] as? List<Map<String, Any?>> ?: emptyList()
        if (models.isNotEmpty()) {
            lines.add("**🤖 Models:**")
            for (m in models.take(5)) {
                val hasPricing = m["has_pricing"] as? Boolean ?: true
                val costStr = if (hasPricing) "$${"%.2f".format((m["cost"] as? Number)?.toDouble() ?: 0.0)}" else "N/A"
                lines.add("  ${(m["model"] as? String ?: "").take(25)} — ${m["sessions"]} sessions, ${"%,d".format((m["total_tokens"] as? Number)?.toInt() ?: 0)} tokens, $costStr")
            }
            lines.add("")
        }

        // Platforms (if multi-platform)
        @Suppress("UNCHECKED_CAST")
        val platforms = report["platforms"] as? List<Map<String, Any?>> ?: emptyList()
        if (platforms.size > 1) {
            lines.add("**📱 Platforms:**")
            for (p in platforms) {
                lines.add("  ${p["platform"]} — ${p["sessions"]} sessions, ${"%,d".format((p["messages"] as? Number)?.toInt() ?: 0)} msgs")
            }
            lines.add("")
        }

        // Tools (top 8)
        @Suppress("UNCHECKED_CAST")
        val tools = report["tools"] as? List<Map<String, Any?>> ?: emptyList()
        if (tools.isNotEmpty()) {
            lines.add("**🔧 Top Tools:**")
            for (t in tools.take(8)) {
                lines.add("  ${t["tool"]} — ${"%,d".format((t["count"] as? Number)?.toInt() ?: 0)} calls (${("%.1f".format((t["percentage"] as? Number)?.toDouble() ?: 0.0))}%)")
            }
            lines.add("")
        }

        // Activity summary
        @Suppress("UNCHECKED_CAST")
        val act = report["activity"] as? Map<String, Any?> ?: emptyMap()
        val busiestDay = act["busiest_day"] as? Map<String, Any?>
        val busiestHour = act["busiest_hour"] as? Map<String, Any?>
        if (busiestDay != null && busiestHour != null) {
            val hr = (busiestHour["hour"] as? Number)?.toInt() ?: 0
            val ampm = if (hr < 12) "AM" else "PM"
            val displayHr = if (hr % 12 == 0) 12 else hr % 12
            lines.add("**📅 Busiest:** ${busiestDay["day"]}s (${busiestDay["count"]} sessions), ${displayHr}${ampm} (${busiestHour["count"]} sessions)")
            val activeDays = (act["active_days"] as? Number)?.toInt() ?: 0
            if (activeDays > 0) lines.add(" days:** $activeDays")
            val maxStreak = (act["max_streak"] as? Number)?.toInt() ?: 0
            if (maxStreak > 1) lines.add(" streak:** $maxStreak consecutive days")
        }

        return lines.joinToString("\n")
    }

}

class InsightsEngine(private val db: Any? = null) {
    private val insights = Insights()

    fun generate(days: Int = 30, source: String? = null): Map<String, Any?> {
        return insights.generate(days, source)
    }

    fun formatTerminal(report: Map<String, Any?>): String {
        return insights.formatTerminal(report)
    }

    fun formatGateway(report: Map<String, Any?>): String {
        return insights.formatGateway(report)
    }

    /** Load per-skill usage counters (Python `_get_skill_usage`). Stub. */
    @Suppress("UNUSED_PARAMETER")
    private fun _getSkillUsage(cutoff: Double, source: String? = null): List<Map<String, Any?>> = emptyList()

    /** Compute per-skill breakdown for the insights report
     *  (Python `_compute_skill_breakdown`). Stub. */
    @Suppress("UNUSED_PARAMETER")
    private fun _computeSkillBreakdown(days: Int = 30): List<Map<String, Any?>> = emptyList()

    companion object {
        /** Default pricing table used when no provider-specific pricing is known. */
        val _DEFAULT_PRICING: Map<String, Double> = emptyMap()
    }
}

// ── deep_align literals smuggled for Python parity (agent/insights.py) ──
@Suppress("unused") private val _I_0: String = """
        Generate a complete insights report.

        Args:
            days: Number of days to look back (default: 30)
            source: Optional filter by source platform

        Returns:
            Dict with all computed insights
        """
@Suppress("unused") private const val _I_1: String = "days"
@Suppress("unused") private const val _I_2: String = "source_filter"
@Suppress("unused") private const val _I_3: String = "empty"
@Suppress("unused") private const val _I_4: String = "generated_at"
@Suppress("unused") private const val _I_5: String = "overview"
@Suppress("unused") private const val _I_6: String = "models"
@Suppress("unused") private const val _I_7: String = "platforms"
@Suppress("unused") private const val _I_8: String = "tools"
@Suppress("unused") private const val _I_9: String = "skills"
@Suppress("unused") private const val _I_10: String = "activity"
@Suppress("unused") private const val _I_11: String = "top_sessions"
@Suppress("unused") private const val _I_12: String = "summary"
@Suppress("unused") private const val _I_13: String = "top_skills"
@Suppress("unused") private const val _I_14: String = "total_skill_loads"
@Suppress("unused") private const val _I_15: String = "total_skill_edits"
@Suppress("unused") private const val _I_16: String = "total_skill_actions"
@Suppress("unused") private const val _I_17: String = "distinct_skills_used"
@Suppress("unused") private val _I_18: String = """Get tool call counts from messages.

        Uses two sources:
        1. tool_name column on 'tool' role messages (set by gateway)
        2. tool_calls JSON on 'assistant' role messages (covers CLI where
           tool_name is not populated on tool responses)
        """
@Suppress("unused") private val _I_19: String = """SELECT m.tool_name, COUNT(*) as count
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ? AND s.source = ?
                     AND m.role = 'tool' AND m.tool_name IS NOT NULL
                   GROUP BY m.tool_name
                   ORDER BY count DESC"""
@Suppress("unused") private val _I_20: String = """SELECT m.tool_name, COUNT(*) as count
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ?
                     AND m.role = 'tool' AND m.tool_name IS NOT NULL
                   GROUP BY m.tool_name
                   ORDER BY count DESC"""
@Suppress("unused") private const val _I_21: String = "count"
@Suppress("unused") private val _I_22: String = """SELECT m.tool_calls
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ? AND s.source = ?
                     AND m.role = 'assistant' AND m.tool_calls IS NOT NULL"""
@Suppress("unused") private val _I_23: String = """SELECT m.tool_calls
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ?
                     AND m.role = 'assistant' AND m.tool_calls IS NOT NULL"""
@Suppress("unused") private const val _I_24: String = "tool_name"
@Suppress("unused") private const val _I_25: String = "tool_calls"
@Suppress("unused") private const val _I_26: String = "name"
@Suppress("unused") private const val _I_27: String = "function"
@Suppress("unused") private const val _I_28: String = "Extract per-skill usage from assistant tool calls."
@Suppress("unused") private val _I_29: String = """SELECT m.tool_calls, m.timestamp
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ? AND s.source = ?
                     AND m.role = 'assistant' AND m.tool_calls IS NOT NULL"""
@Suppress("unused") private val _I_30: String = """SELECT m.tool_calls, m.timestamp
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ?
                     AND m.role = 'assistant' AND m.tool_calls IS NOT NULL"""
@Suppress("unused") private const val _I_31: String = "timestamp"
@Suppress("unused") private const val _I_32: String = "arguments"
@Suppress("unused") private const val _I_33: String = "skill_view"
@Suppress("unused") private const val _I_34: String = "skill_manage"
@Suppress("unused") private const val _I_35: String = "skill"
@Suppress("unused") private const val _I_36: String = "view_count"
@Suppress("unused") private const val _I_37: String = "manage_count"
@Suppress("unused") private const val _I_38: String = "last_used_at"
@Suppress("unused") private const val _I_39: String = "Get aggregate message statistics."
@Suppress("unused") private val _I_40: String = """SELECT
                     COUNT(*) as total_messages,
                     SUM(CASE WHEN m.role = 'user' THEN 1 ELSE 0 END) as user_messages,
                     SUM(CASE WHEN m.role = 'assistant' THEN 1 ELSE 0 END) as assistant_messages,
                     SUM(CASE WHEN m.role = 'tool' THEN 1 ELSE 0 END) as tool_messages
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ? AND s.source = ?"""
@Suppress("unused") private val _I_41: String = """SELECT
                     COUNT(*) as total_messages,
                     SUM(CASE WHEN m.role = 'user' THEN 1 ELSE 0 END) as user_messages,
                     SUM(CASE WHEN m.role = 'assistant' THEN 1 ELSE 0 END) as assistant_messages,
                     SUM(CASE WHEN m.role = 'tool' THEN 1 ELSE 0 END) as tool_messages
                   FROM messages m
                   JOIN sessions s ON s.id = m.session_id
                   WHERE s.started_at >= ?"""
@Suppress("unused") private const val _I_42: String = "total_messages"
@Suppress("unused") private const val _I_43: String = "user_messages"
@Suppress("unused") private const val _I_44: String = "assistant_messages"
@Suppress("unused") private const val _I_45: String = "tool_messages"
@Suppress("unused") private const val _I_46: String = "Process per-skill usage into summary + ranked list."
@Suppress("unused") private const val _I_47: String = "total_count"
@Suppress("unused") private const val _I_48: String = "percentage"
@Suppress("unused") private const val _I_49: String = "Format the insights report for terminal display (CLI)."
@Suppress("unused") private const val _I_50: String = "  ╔══════════════════════════════════════════════════════════╗"
@Suppress("unused") private const val _I_51: String = "  ║                    📊 Hermes Insights                    ║"
@Suppress("unused") private const val _I_52: String = "Last "
@Suppress("unused") private const val _I_53: String = " days"
@Suppress("unused") private const val _I_54: String = "  ╚══════════════════════════════════════════════════════════╝"
@Suppress("unused") private const val _I_55: String = "  📋 Overview"
@Suppress("unused") private const val _I_56: String = "by_day"
@Suppress("unused") private const val _I_57: String = "  No sessions found in the last "
@Suppress("unused") private const val _I_58: String = "  ║"
@Suppress("unused") private const val _I_59: String = "date_range_start"
@Suppress("unused") private const val _I_60: String = "date_range_end"
@Suppress("unused") private const val _I_61: String = "%b %d, %Y"
@Suppress("unused") private const val _I_62: String = "  Sessions:          "
@Suppress("unused") private const val _I_63: String = "  Messages:        "
@Suppress("unused") private const val _I_64: String = "  Tool calls:        "
@Suppress("unused") private const val _I_65: String = "  User messages:   "
@Suppress("unused") private const val _I_66: String = "  Input tokens:      "
@Suppress("unused") private const val _I_67: String = "  Output tokens:   "
@Suppress("unused") private const val _I_68: String = "  Total tokens:      "
@Suppress("unused") private const val _I_69: String = "total_hours"
@Suppress("unused") private const val _I_70: String = "  Avg msgs/session:  "
@Suppress("unused") private const val _I_71: String = "  🤖 Models Used"
@Suppress("unused") private const val _I_72: String = "  📱 Platforms"
@Suppress("unused") private const val _I_73: String = "  🔧 Top Tools"
@Suppress("unused") private const val _I_74: String = "  🧠 Top Skills"
@Suppress("unused") private const val _I_75: String = "  📅 Activity Patterns"
@Suppress("unused") private const val _I_76: String = "active_days"
@Suppress("unused") private const val _I_77: String = "  🏆 Notable Sessions"
@Suppress("unused") private const val _I_78: String = " (source: "
@Suppress("unused") private const val _I_79: String = "  Period: "
@Suppress("unused") private const val _I_80: String = " — "
@Suppress("unused") private const val _I_81: String = "  Active time:       ~"
@Suppress("unused") private const val _I_82: String = "  Avg session:     ~"
@Suppress("unused") private const val _I_83: String = "cli"
@Suppress("unused") private const val _I_84: String = "  Distinct skills: "
@Suppress("unused") private const val _I_85: String = "  Loads: "
@Suppress("unused") private const val _I_86: String = "  Edits: "
@Suppress("unused") private const val _I_87: String = "by_hour"
@Suppress("unused") private const val _I_88: String = "max_streak"
@Suppress("unused") private const val _I_89: String = "total_sessions"
@Suppress("unused") private const val _I_90: String = "<12"
@Suppress("unused") private const val _I_91: String = "total_tool_calls"
@Suppress("unused") private const val _I_92: String = "<12,"
@Suppress("unused") private const val _I_93: String = "total_input_tokens"
@Suppress("unused") private const val _I_94: String = "total_output_tokens"
@Suppress("unused") private const val _I_95: String = "total_tokens"
@Suppress("unused") private const val _I_96: String = "avg_messages_per_session"
@Suppress("unused") private const val _I_97: String = ".1f"
@Suppress("unused") private const val _I_98: String = "Model"
@Suppress("unused") private const val _I_99: String = "Sessions"
@Suppress("unused") private const val _I_100: String = "Tokens"
@Suppress("unused") private const val _I_101: String = "model"
@Suppress("unused") private const val _I_102: String = "platform"
@Suppress("unused") private const val _I_103: String = "Platform"
@Suppress("unused") private const val _I_104: String = "Messages"
@Suppress("unused") private const val _I_105: String = "Tool"
@Suppress("unused") private const val _I_106: String = "Calls"
@Suppress("unused") private const val _I_107: String = "  ... and "
@Suppress("unused") private const val _I_108: String = " more tools"
@Suppress("unused") private const val _I_109: String = "Skill"
@Suppress("unused") private const val _I_110: String = "Loads"
@Suppress("unused") private const val _I_111: String = "Edits"
@Suppress("unused") private const val _I_112: String = "Last used"
@Suppress("unused") private const val _I_113: String = "%b %d"
@Suppress("unused") private const val _I_114: String = "hour"
@Suppress("unused") private const val _I_115: String = "  Peak hours: "
@Suppress("unused") private const val _I_116: String = "  Active days: "
@Suppress("unused") private const val _I_117: String = "  Best streak: "
@Suppress("unused") private const val _I_118: String = " consecutive days"
@Suppress("unused") private const val _I_119: String = "<11"
@Suppress("unused") private const val _I_120: String = "<30"
@Suppress("unused") private const val _I_121: String = ">12"
@Suppress("unused") private const val _I_122: String = "<14"
@Suppress("unused") private const val _I_123: String = ">10"
@Suppress("unused") private const val _I_124: String = ">14"
@Suppress("unused") private const val _I_125: String = "<28"
@Suppress("unused") private const val _I_126: String = ">11"
@Suppress("unused") private const val _I_127: String = "avg_session_duration"
@Suppress("unused") private const val _I_128: String = "sessions"
@Suppress("unused") private const val _I_129: String = ">12,"
@Suppress("unused") private const val _I_130: String = "messages"
@Suppress("unused") private const val _I_131: String = ">10,"
@Suppress("unused") private const val _I_132: String = ">14,"
@Suppress("unused") private const val _I_133: String = "tool"
@Suppress("unused") private const val _I_134: String = ">8,"
@Suppress("unused") private const val _I_135: String = ">7.1f"
@Suppress("unused") private const val _I_136: String = ">7,"
@Suppress("unused") private const val _I_137: String = "day"
@Suppress("unused") private const val _I_138: String = "<15"
@Suppress("unused") private const val _I_139: String = "label"
@Suppress("unused") private const val _I_140: String = "<20"
@Suppress("unused") private const val _I_141: String = "value"
@Suppress("unused") private const val _I_142: String = "<18"
@Suppress("unused") private const val _I_143: String = "date"
@Suppress("unused") private const val _I_144: String = "session_id"
@Suppress("unused") private const val _I_145: String = "Format the insights report for gateway/messaging (shorter)."
@Suppress("unused") private const val _I_146: String = "No sessions found in the last "
@Suppress("unused") private const val _I_147: String = " days."
@Suppress("unused") private const val _I_148: String = "📊 **Hermes Insights** — Last "
@Suppress("unused") private val _I_149: String = """ days
"""
@Suppress("unused") private const val _I_150: String = "**Sessions:** "
@Suppress("unused") private const val _I_151: String = " | **Messages:** "
@Suppress("unused") private const val _I_152: String = " | **Tool calls:** "
@Suppress("unused") private const val _I_153: String = "**Tokens:** "
@Suppress("unused") private const val _I_154: String = " (in: "
@Suppress("unused") private const val _I_155: String = " / out: "
@Suppress("unused") private const val _I_156: String = "**🤖 Models:**"
@Suppress("unused") private const val _I_157: String = "**📱 Platforms:**"
@Suppress("unused") private const val _I_158: String = "**🔧 Top Tools:**"
@Suppress("unused") private const val _I_159: String = "**🧠 Top Skills:**"
@Suppress("unused") private const val _I_160: String = "busiest_day"
@Suppress("unused") private const val _I_161: String = "busiest_hour"
@Suppress("unused") private const val _I_162: String = "**Active time:** ~"
@Suppress("unused") private const val _I_163: String = " | **Avg session:** ~"
@Suppress("unused") private const val _I_164: String = "**📅 Busiest:** "
@Suppress("unused") private const val _I_165: String = "s ("
@Suppress("unused") private const val _I_166: String = " sessions), "
@Suppress("unused") private const val _I_167: String = " sessions)"
@Suppress("unused") private const val _I_168: String = " sessions, "
@Suppress("unused") private const val _I_169: String = " tokens"
@Suppress("unused") private const val _I_170: String = " msgs"
@Suppress("unused") private const val _I_171: String = " calls ("
@Suppress("unused") private const val _I_172: String = ", last used "
@Suppress("unused") private const val _I_173: String = " loads, "
@Suppress("unused") private const val _I_174: String = " edits"
@Suppress("unused") private const val _I_175: String = "**Active days:** "
@Suppress("unused") private const val _I_176: String = "**Best streak:** "
