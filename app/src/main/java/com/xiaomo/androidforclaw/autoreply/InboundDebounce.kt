package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/inbound-debounce.ts
 *
 * Inbound message debouncing: resolve delay from config, keyed buffering
 * with chained flush guarantees.
 *
 * Android adaptation: uses Kotlin coroutines (delay, CoroutineScope) instead of
 * setTimeout / Promise chains.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

// ============================================================================
// Config resolution (aligned with OpenClaw resolveInboundDebounceMs)
// ============================================================================

/**
 * Resolve the inbound debounce delay from config, per-channel overrides, and explicit override.
 * Aligned with OpenClaw resolveInboundDebounceMs.
 */
fun resolveInboundDebounceMs(
    cfg: OpenClawConfig,
    channel: String,
    overrideMs: Int? = null
): Int {
    val resolveMs = fun(value: Any?): Int? {
        if (value !is Number) return null
        val n = value.toInt()
        return if (n >= 0) n else null
    }

    val override = resolveMs(overrideMs)
    // MessagesConfig does not yet have inbound.byChannel or inbound.debounceMs on Android;
    // return override or 0 (no debounce) as default.
    return override ?: 0
}

// ============================================================================
// Debouncer types
// ============================================================================

/**
 * Parameters for creating an inbound debouncer.
 * Aligned with OpenClaw InboundDebounceCreateParams.
 */
data class InboundDebounceCreateParams<T>(
    val debounceMs: Int,
    val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
    val buildKey: (T) -> String?,
    val shouldDebounce: ((T) -> Boolean)? = null,
    val resolveDebounceMs: ((T) -> Int?)? = null,
    val onFlush: suspend (List<T>) -> Unit,
    val onError: ((Throwable, List<T>) -> Unit)? = null
)

private const val DEFAULT_MAX_TRACKED_KEYS = 2048

// ============================================================================
// Inbound debouncer (aligned with OpenClaw createInboundDebouncer)
// ============================================================================

/**
 * Create a keyed inbound debouncer that buffers items per key and flushes
 * after the debounce window, preserving per-key ordering.
 * Aligned with OpenClaw createInboundDebouncer.
 */
fun <T> createInboundDebouncer(
    params: InboundDebounceCreateParams<T>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
): InboundDebouncer<T> {
    return InboundDebouncer(params, scope)
}

class InboundDebouncer<T>(
    private val params: InboundDebounceCreateParams<T>,
    private val scope: CoroutineScope
) {
    private val defaultDebounceMs = maxOf(0, params.debounceMs)
    private val maxTrackedKeys = maxOf(1, params.maxTrackedKeys)

    private data class DebounceBuffer<T>(
        val items: MutableList<T>,
        var job: Job?,
        var debounceMs: Int
    )

    private val buffers = ConcurrentHashMap<String, DebounceBuffer<T>>()
    private val keyChains = ConcurrentHashMap<String, Job>()

    private fun resolveDebounceMs(item: T): Int {
        val resolved = params.resolveDebounceMs?.invoke(item)
        return if (resolved != null && resolved >= 0) resolved else defaultDebounceMs
    }

    private suspend fun runFlush(items: List<T>) {
        try {
            params.onFlush(items)
        } catch (e: Throwable) {
            try {
                params.onError?.invoke(e, items)
            } catch (_: Throwable) {
                // Flush failures are reported via onError
            }
        }
    }

    private fun enqueueKeyTask(key: String, task: suspend () -> Unit): Job {
        val previous = keyChains[key]
        val job = scope.launch {
            previous?.join()
            task()
        }
        keyChains[key] = job
        job.invokeOnCompletion {
            if (keyChains[key] === job) {
                keyChains.remove(key)
            }
        }
        return job
    }

    private fun flushBuffer(key: String, buffer: DebounceBuffer<T>) {
        buffers.remove(key, buffer)
        buffer.job?.cancel()
        buffer.job = null
        val items = buffer.items.toList()
        if (items.isNotEmpty()) {
            enqueueKeyTask(key) {
                runFlush(items)
            }
        }
    }

    private fun scheduleFlush(key: String, buffer: DebounceBuffer<T>) {
        buffer.job?.cancel()
        buffer.job = scope.launch {
            delay(buffer.debounceMs.toLong())
            flushBuffer(key, buffer)
        }
    }

    private fun canTrackKey(key: String): Boolean {
        if (buffers.containsKey(key) || keyChains.containsKey(key)) return true
        val combinedSize = (buffers.keys + keyChains.keys).toSet().size
        return combinedSize < maxTrackedKeys
    }

    /**
     * Enqueue an item for debounced processing.
     * Aligned with OpenClaw createInboundDebouncer.enqueue.
     */
    suspend fun enqueue(item: T) {
        val key = params.buildKey(item)
        val debounceMs = resolveDebounceMs(item)
        val canDebounce = debounceMs > 0 && (params.shouldDebounce?.invoke(item) ?: true)

        if (!canDebounce || key == null) {
            if (key != null) {
                // Flush any existing buffer for this key first
                val existing = buffers[key]
                if (existing != null) {
                    flushBuffer(key, existing)
                }
                if (keyChains.containsKey(key)) {
                    enqueueKeyTask(key) { runFlush(listOf(item)) }.join()
                    return
                }
            }
            runFlush(listOf(item))
            return
        }

        val existing = buffers[key]
        if (existing != null) {
            existing.items.add(item)
            existing.debounceMs = debounceMs
            scheduleFlush(key, existing)
            return
        }

        if (!canTrackKey(key)) {
            // Saturated: fall back to immediate keyed work
            enqueueKeyTask(key) { runFlush(listOf(item)) }.join()
            return
        }

        val buffer = DebounceBuffer(
            items = mutableListOf(item),
            job = null,
            debounceMs = debounceMs
        )
        buffers[key] = buffer
        scheduleFlush(key, buffer)
    }

    /**
     * Force-flush all pending items for a specific key.
     * Aligned with OpenClaw createInboundDebouncer.flushKey.
     */
    fun flushKey(key: String) {
        val buffer = buffers[key] ?: return
        flushBuffer(key, buffer)
    }
}

// ============================================================================
// Legacy controller (kept for backward compatibility)
// ============================================================================

/**
 * Simple single-key debounce controller.
 * Kept for backward compatibility; prefer createInboundDebouncer for new code.
 */
class InboundDebounceController(
    private val debounceMs: Long = 500,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var pendingJob: Job? = null
    private var pendingMessage: String? = null

    fun debounce(message: String, onReady: (String) -> Unit) {
        pendingJob?.cancel()
        pendingMessage = message
        pendingJob = scope.launch {
            delay(debounceMs)
            pendingMessage?.let { onReady(it) }
            pendingMessage = null
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingMessage = null
    }
}
