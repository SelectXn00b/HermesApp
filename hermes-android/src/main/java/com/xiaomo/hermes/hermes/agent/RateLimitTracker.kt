package com.xiaomo.hermes.hermes.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * Rate Limit Tracker - 速率限制跟踪
 * 1:1 对齐 hermes/agent/rate_limit_tracker.py
 *
 * 跟踪每个 provider 的请求速率，预测是否即将触发限制。
 */

data class RateLimitInfo(
    val limitRequests: Int = 0,
    val remainingRequests: Int = 0,
    val resetAtMs: Long = 0L,
    val limitTokens: Int = 0,
    val remainingTokens: Int = 0,
    val tokenResetAtMs: Long = 0L
)

class RateLimitTracker {

    private val limits: ConcurrentHashMap<String, RateLimitInfo> = ConcurrentHashMap()
    private val requestCounts: ConcurrentHashMap<String, MutableList<Long>> = ConcurrentHashMap()

    companion object {
        // 窗口大小（毫秒）
        private const val WINDOW_MS = 60_000L
    }

    /**
     * 更新 provider 的速率限制信息（从响应头解析）
     *
     * @param provider provider 名称
     * @param info 限制信息
     */
    fun update(provider: String, info: RateLimitInfo) {
        limits[provider] = info
    }

    /**
     * 记录一次请求
     *
     * @param provider provider 名称
     */
    fun recordRequest(provider: String) {
        val now = System.currentTimeMillis()
        val timestamps = requestCounts.getOrPut(provider) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.add(now)
            // 清理窗口外的记录
            val cutoff = now - WINDOW_MS
            timestamps.removeAll { it < cutoff }
        }
    }

    /**
     * 获取当前窗口内的请求速率（每分钟）
     *
     * @param provider provider 名称
     * @return 每分钟请求数
     */
    fun getRatePerMinute(provider: String): Double {
        val now = System.currentTimeMillis()
        val timestamps = requestCounts[provider] ?: return 0.0
        synchronized(timestamps) {
            val cutoff = now - WINDOW_MS
            timestamps.removeAll { it < cutoff }
            if (timestamps.isEmpty()) return 0.0
            val elapsedMs = now - (timestamps.firstOrNull() ?: now)
            if (elapsedMs <= 0) return timestamps.size.toDouble()
            return timestamps.size * 60_000.0 / elapsedMs
        }
    }

    /**
     * 检查是否接近速率限制
     *
     * @param provider provider 名称
     * @param threshold 接近阈值（0.0 - 1.0），默认 0.8
     * @return 是否接近限制
     */
    fun isNearLimit(provider: String, threshold: Double = 0.8): Boolean {
        val info = limits[provider] ?: return false
        if (info.limitRequests <= 0) return false
        val usageRatio = 1.0 - (info.remainingRequests.toDouble() / info.limitRequests)
        return usageRatio >= threshold
    }

    /**
     * 获取建议的等待时间（毫秒）
     *
     * @param provider provider 名称
     * @return 建议等待毫秒数，0 表示不需要等待
     */
    fun getSuggestedWaitMs(provider: String): Long {
        val info = limits[provider] ?: return 0L
        if (info.remainingRequests > 0) return 0L
        val now = System.currentTimeMillis()
        val waitMs = info.resetAtMs - now
        return if (waitMs > 0) waitMs else 0L
    }

    /**
     * 从 HTTP 响应头解析速率限制信息
     *
     * @param headers 响应头
     * @return 限制信息
     */
    fun parseFromHeaders(headers: Map<String, String>): RateLimitInfo {
        return RateLimitInfo(
            limitRequests = headers["x-ratelimit-limit-requests"]?.toIntOrNull() ?: 0,
            remainingRequests = headers["x-ratelimit-remaining-requests"]?.toIntOrNull() ?: 0,
            resetAtMs = parseResetTime(headers["x-ratelimit-reset-requests"]),
            limitTokens = headers["x-ratelimit-limit-tokens"]?.toIntOrNull() ?: 0,
            remainingTokens = headers["x-ratelimit-remaining-tokens"]?.toIntOrNull() ?: 0,
            tokenResetAtMs = parseResetTime(headers["x-ratelimit-reset-tokens"])
        )
    }

    private fun parseResetTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        // 尝试解析为 Unix 时间戳
        val numeric = value.toDoubleOrNull()
        if (numeric != null) {
            return if (numeric > 1_000_000_000_000) numeric.toLong() else (numeric * 1000).toLong()
        }
        // 尝试解析为 ISO 时间
        return try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    
    /** Check if any rate limit data exists for a provider. */
    fun hasData(provider: String): Boolean = limits.containsKey(provider)

    /** Get usage percentage for a provider. */
    fun usagePct(provider: String): Double {
        val info = limits[provider] ?: return 0.0
        if (info.limitTokens <= 0) return 0.0
        return (info.limitTokens - info.remainingTokens).toDouble() / info.limitTokens * 100.0
    }

    /** Get remaining seconds until reset. */
    fun remainingSecondsNow(provider: String): Long {
        val info = limits[provider] ?: return 0
        return maxOf(0, (info.resetAtMs - System.currentTimeMillis()) / 1000)
    }
}


    fun used(): Int {
        return 0
    }
    fun ageSeconds(): Double {
        return 0.0
    }

}

data class RateLimitBucket(
    val limit: Int = 0,
    val remaining: Int = 0,
    val resetSeconds: Double = 0.0,
    val capturedAt: Double = 0.0
) {
    val used: Int get() = maxOf(0, limit - remaining)
    val usagePct: Double get() = if (limit <= 0) 0.0 else used.toDouble() / limit * 100.0
    val remainingSecondsNow: Double get() {
        val elapsed = System.currentTimeMillis() / 1000.0 - capturedAt
        return maxOf(0.0, resetSeconds - elapsed)
    }
}

data class RateLimitState(
    val requestsMin: RateLimitBucket = RateLimitBucket(),
    val requestsHour: RateLimitBucket = RateLimitBucket(),
    val tokensMin: RateLimitBucket = RateLimitBucket(),
    val tokensHour: RateLimitBucket = RateLimitBucket(),
    val capturedAt: Double = 0.0,
    val provider: String = ""
) {
    val hasData: Boolean get() = capturedAt > 0
    val ageSeconds: Double get() = if (!hasData) Double.MAX_VALUE else System.currentTimeMillis() / 1000.0 - capturedAt
}
