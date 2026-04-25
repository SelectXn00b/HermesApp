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
    val ageSeconds: Double get() = if (!hasData) "inf".let { Double.POSITIVE_INFINITY } else System.currentTimeMillis() / 1000.0 - capturedAt
}

// ── Module-level aligned with Python agent/rate_limit_tracker.py ─────────

/** Safe integer coercion with default fallback. */
fun _safeInt(value: Any?, default: Int = 0): Int {
    if (value == null) return default
    return try {
        when (value) {
            is Number -> value.toInt()
            is String -> value.toDouble().toInt()
            else -> default
        }
    } catch (_: Exception) {
        default
    }
}

/** Safe float coercion with default fallback. */
fun _safeFloat(value: Any?, default: Double = 0.0): Double {
    if (value == null) return default
    return try {
        when (value) {
            is Number -> value.toDouble()
            is String -> value.toDouble()
            else -> default
        }
    } catch (_: Exception) {
        default
    }
}

/**
 * Parse `x-ratelimit-*` headers into a RateLimitState. Returns null if the
 * headers contain no `x-ratelimit-*` keys at all.
 */
fun parseRateLimitHeaders(
    headers: Map<String, String>,
    provider: String = ""
): RateLimitState? {
    val lowered = headers.mapKeys { it.key.lowercase() }
    val hasAny = lowered.keys.any { it.startsWith("x-ratelimit-") }
    if (!hasAny) return null

    val now = System.currentTimeMillis() / 1000.0

    fun bucket(resource: String, suffix: String = ""): RateLimitBucket {
        val tag = resource + suffix
        return RateLimitBucket(
            limit = _safeInt(lowered["x-ratelimit-limit-$tag"]),
            remaining = _safeInt(lowered["x-ratelimit-remaining-$tag"]),
            resetSeconds = _safeFloat(lowered["x-ratelimit-reset-$tag"]),
            capturedAt = now
        )
    }

    return RateLimitState(
        requestsMin = bucket("requests"),
        requestsHour = bucket("requests", "-1h"),
        tokensMin = bucket("tokens"),
        tokensHour = bucket("tokens", "-1h"),
        capturedAt = now,
        provider = provider
    )
}

/** Human-friendly count: 7999856 → "8.0M", 33599 → "33.6K", 799 → "799". */
fun _fmtCount(n: Int): String {
    if (n >= 1_000_000) return "%.1fM".format(n / 1_000_000.0)
    if (n >= 10_000) return "%.1fK".format(n / 1_000.0)
    if (n >= 1_000) return "%.1fK".format(n / 1_000.0)
    return n.toString()
}

/** Seconds → human duration: "58s", "2m 14s", "1h 2m". */
fun _fmtSeconds(seconds: Double): String {
    val s = maxOf(0, seconds.toInt())
    if (s < 60) return "${s}s"
    if (s < 3600) {
        val m = s / 60
        val sec = s % 60
        return if (sec != 0) "${m}m ${sec}s" else "${m}m"
    }
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (m != 0) "${h}h ${m}m" else "${h}h"
}

/** ASCII progress bar: `[████████░░░░░░░░░░░░]`. */
fun _bar(pct: Double, width: Int = 20): String {
    val filled = (pct / 100.0 * width).toInt().coerceIn(0, width)
    val empty = width - filled
    val b = StringBuilder("[")
    repeat(filled) { b.append('█') }
    repeat(empty) { b.append('░') }
    b.append(']')
    return b.toString()
}

/** Format one bucket as a single line (label + bar + counts + reset ETA). */
fun _bucketLine(label: String, bucket: RateLimitBucket, labelWidth: Int = 14): String {
    if (bucket.limit <= 0) {
        return "  ${label.padEnd(labelWidth)}  (no data)"
    }
    val pct = bucket.usagePct
    val used = _fmtCount(bucket.used)
    val limit = _fmtCount(bucket.limit)
    val remaining = _fmtCount(bucket.remaining)
    val reset = _fmtSeconds(bucket.remainingSecondsNow)
    val bar = _bar(pct)
    return "  ${label.padEnd(labelWidth)} $bar %5.1f%%  $used/$limit used  ($remaining left, resets in $reset)".format(pct)
}

/** Format rate-limit state for terminal/chat display. */
fun formatRateLimitDisplay(state: RateLimitState): String {
    if (!state.hasData) {
        return "No rate limit data yet — make an API request first."
    }

    val age = state.ageSeconds
    val freshness = when {
        age < 5 -> "just now"
        age < 60 -> "${age.toInt()}s ago"
        else -> "${_fmtSeconds(age)} ago"
    }

    val providerLabel = if (state.provider.isNotEmpty())
        state.provider.replaceFirstChar { it.uppercase() }
    else "Provider"

    val lines = mutableListOf(
        "$providerLabel Rate Limits (captured $freshness):",
        "",
        _bucketLine("Requests/min", state.requestsMin),
        _bucketLine("Requests/hr", state.requestsHour),
        "",
        _bucketLine("Tokens/min", state.tokensMin),
        _bucketLine("Tokens/hr", state.tokensHour)
    )

    val warnings = mutableListOf<String>()
    for ((label, bucket) in listOf(
        "requests/min" to state.requestsMin,
        "requests/hr" to state.requestsHour,
        "tokens/min" to state.tokensMin,
        "tokens/hr" to state.tokensHour
    )) {
        if (bucket.limit > 0 && bucket.usagePct >= 80) {
            val reset = _fmtSeconds(bucket.remainingSecondsNow)
            warnings.add("  ⚠ $label at %.0f%% — resets in $reset".format(bucket.usagePct))
        }
    }
    if (warnings.isNotEmpty()) {
        lines.add("")
        lines.addAll(warnings)
    }
    return lines.joinToString("\n")
}

/** One-line compact summary for status bars / gateway messages. */
fun formatRateLimitCompact(state: RateLimitState): String {
    if (!state.hasData) return "No rate limit data."

    val rm = state.requestsMin
    val tm = state.tokensMin
    val rh = state.requestsHour
    val th = state.tokensHour

    val parts = mutableListOf<String>()
    if (rm.limit > 0) parts.add("RPM: ${rm.remaining}/${rm.limit}")
    if (rh.limit > 0) parts.add("RPH: ${_fmtCount(rh.remaining)}/${_fmtCount(rh.limit)} (resets ${_fmtSeconds(rh.remainingSecondsNow)})")
    if (tm.limit > 0) parts.add("TPM: ${_fmtCount(tm.remaining)}/${_fmtCount(tm.limit)}")
    if (th.limit > 0) parts.add("TPH: ${_fmtCount(th.remaining)}/${_fmtCount(th.limit)} (resets ${_fmtSeconds(th.remainingSecondsNow)})")
    return parts.joinToString(" | ")
}
