package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/webhook-memory-guards.ts
 *
 * In-memory rate limiting and anomaly tracking for webhook protection.
 * Android adaptation: uses ConcurrentHashMap-based LinkedHashMap for LRU eviction.
 */

import java.util.concurrent.ConcurrentHashMap

// ---------- Types ----------

/**
 * Fixed window rate limiter.
 * Aligned with TS FixedWindowRateLimiter.
 */
interface FixedWindowRateLimiter {
    fun isRateLimited(key: String, nowMs: Long = System.currentTimeMillis()): Boolean
    fun size(): Int
    fun clear()
}

/**
 * Bounded counter.
 * Aligned with TS BoundedCounter.
 */
interface BoundedCounter {
    fun increment(key: String, nowMs: Long = System.currentTimeMillis()): Int
    fun size(): Int
    fun clear()
}

/**
 * Webhook anomaly tracker.
 * Aligned with TS WebhookAnomalyTracker.
 */
interface WebhookAnomalyTracker {
    fun record(
        key: String,
        statusCode: Int,
        message: (count: Int) -> String,
        log: ((String) -> Unit)? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Int
    fun size(): Int
    fun clear()
}

// ---------- Default Constants ----------

/** Aligned with TS WEBHOOK_RATE_LIMIT_DEFAULTS. */
object WebhookRateLimitDefaults {
    const val WINDOW_MS = 60_000L
    const val MAX_REQUESTS = 120
    const val MAX_TRACKED_KEYS = 4_096
}

/** Aligned with TS WEBHOOK_ANOMALY_COUNTER_DEFAULTS. */
object WebhookAnomalyCounterDefaults {
    const val MAX_TRACKED_KEYS = 4_096
    const val TTL_MS = 6L * 60L * 60_000L // 6 hours
    const val LOG_EVERY = 25
}

/** Aligned with TS WEBHOOK_ANOMALY_STATUS_CODES. */
val WEBHOOK_ANOMALY_STATUS_CODES = setOf(400, 401, 408, 413, 415, 429)

// ---------- Internal state classes ----------

private data class FixedWindowState(
    val count: Int,
    val windowStartMs: Long,
)

private data class CounterState(
    val count: Int,
    val updatedAtMs: Long,
)

// ---------- LRU eviction ----------

/**
 * Prune a LinkedHashMap to a maximum size by removing oldest entries.
 * Aligned with TS pruneMapToMaxSize.
 */
private fun <K, V> pruneMapToMaxSize(map: LinkedHashMap<K, V>, maxSize: Int) {
    while (map.size > maxSize) {
        val firstKey = map.keys.firstOrNull() ?: break
        map.remove(firstKey)
    }
}

// ---------- Fixed Window Rate Limiter ----------

/**
 * Create a simple fixed-window rate limiter for in-memory webhook protection.
 * Aligned with TS createFixedWindowRateLimiter.
 */
fun createFixedWindowRateLimiter(
    windowMs: Long = WebhookRateLimitDefaults.WINDOW_MS,
    maxRequests: Int = WebhookRateLimitDefaults.MAX_REQUESTS,
    maxTrackedKeys: Int = WebhookRateLimitDefaults.MAX_TRACKED_KEYS,
    pruneIntervalMs: Long = windowMs,
): FixedWindowRateLimiter {
    val effectiveWindowMs = maxOf(1L, windowMs)
    val effectiveMaxRequests = maxOf(1, maxRequests)
    val effectiveMaxTracked = maxOf(1, maxTrackedKeys)
    val effectivePruneInterval = maxOf(1L, pruneIntervalMs)
    val state = LinkedHashMap<String, FixedWindowState>(16, 0.75f, true)
    var lastPruneMs = 0L
    val lock = Any()

    fun touch(key: String, value: FixedWindowState) {
        state.remove(key)
        state[key] = value
    }

    fun prune(nowMs: Long) {
        val iter = state.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (nowMs - entry.value.windowStartMs >= effectiveWindowMs) {
                iter.remove()
            }
        }
    }

    return object : FixedWindowRateLimiter {
        override fun isRateLimited(key: String, nowMs: Long): Boolean = synchronized(lock) {
            if (key.isEmpty()) return false
            if (nowMs - lastPruneMs >= effectivePruneInterval) {
                prune(nowMs)
                lastPruneMs = nowMs
            }
            val existing = state[key]
            if (existing == null || nowMs - existing.windowStartMs >= effectiveWindowMs) {
                touch(key, FixedWindowState(count = 1, windowStartMs = nowMs))
                pruneMapToMaxSize(state, effectiveMaxTracked)
                return false
            }
            val nextCount = existing.count + 1
            touch(key, FixedWindowState(count = nextCount, windowStartMs = existing.windowStartMs))
            pruneMapToMaxSize(state, effectiveMaxTracked)
            return nextCount > effectiveMaxRequests
        }

        override fun size(): Int = synchronized(lock) { state.size }

        override fun clear() = synchronized(lock) {
            state.clear()
            lastPruneMs = 0L
        }
    }
}

// ---------- Bounded Counter ----------

/**
 * Count keyed events in memory with optional TTL pruning and bounded cardinality.
 * Aligned with TS createBoundedCounter.
 */
fun createBoundedCounter(
    maxTrackedKeys: Int = WebhookAnomalyCounterDefaults.MAX_TRACKED_KEYS,
    ttlMs: Long = 0L,
    pruneIntervalMs: Long = if (ttlMs > 0) ttlMs else 60_000L,
): BoundedCounter {
    val effectiveMaxTracked = maxOf(1, maxTrackedKeys)
    val effectiveTtl = maxOf(0L, ttlMs)
    val effectivePruneInterval = maxOf(1L, pruneIntervalMs)
    val counters = LinkedHashMap<String, CounterState>(16, 0.75f, true)
    var lastPruneMs = 0L
    val lock = Any()

    fun touch(key: String, value: CounterState) {
        counters.remove(key)
        counters[key] = value
    }

    fun isExpired(entry: CounterState, nowMs: Long): Boolean =
        effectiveTtl > 0 && nowMs - entry.updatedAtMs >= effectiveTtl

    fun prune(nowMs: Long) {
        if (effectiveTtl > 0) {
            val iter = counters.entries.iterator()
            while (iter.hasNext()) {
                if (isExpired(iter.next().value, nowMs)) iter.remove()
            }
        }
    }

    return object : BoundedCounter {
        override fun increment(key: String, nowMs: Long): Int = synchronized(lock) {
            if (key.isEmpty()) return 0
            if (nowMs - lastPruneMs >= effectivePruneInterval) {
                prune(nowMs)
                lastPruneMs = nowMs
            }
            val existing = counters[key]
            val baseCount = if (existing != null && !isExpired(existing, nowMs)) existing.count else 0
            val nextCount = baseCount + 1
            touch(key, CounterState(count = nextCount, updatedAtMs = nowMs))
            pruneMapToMaxSize(counters, effectiveMaxTracked)
            return nextCount
        }

        override fun size(): Int = synchronized(lock) { counters.size }

        override fun clear() = synchronized(lock) {
            counters.clear()
            lastPruneMs = 0L
        }
    }
}

// ---------- Webhook Anomaly Tracker ----------

/**
 * Track repeated webhook failures and emit sampled logs for suspicious request patterns.
 * Aligned with TS createWebhookAnomalyTracker.
 */
fun createWebhookAnomalyTracker(
    maxTrackedKeys: Int = WebhookAnomalyCounterDefaults.MAX_TRACKED_KEYS,
    ttlMs: Long = WebhookAnomalyCounterDefaults.TTL_MS,
    logEvery: Int = WebhookAnomalyCounterDefaults.LOG_EVERY,
    trackedStatusCodes: Set<Int> = WEBHOOK_ANOMALY_STATUS_CODES,
): WebhookAnomalyTracker {
    val effectiveLogEvery = maxOf(1, logEvery)
    val counter = createBoundedCounter(maxTrackedKeys = maxTrackedKeys, ttlMs = ttlMs)

    return object : WebhookAnomalyTracker {
        override fun record(
            key: String,
            statusCode: Int,
            message: (count: Int) -> String,
            log: ((String) -> Unit)?,
            nowMs: Long,
        ): Int {
            if (statusCode !in trackedStatusCodes) return 0
            val next = counter.increment(key, nowMs)
            if (log != null && (next == 1 || next % effectiveLogEvery == 0)) {
                log(message(next))
            }
            return next
        }

        override fun size(): Int = counter.size()

        override fun clear() = counter.clear()
    }
}
