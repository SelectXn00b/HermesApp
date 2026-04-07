package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/keyed-async-queue.ts
 *
 * Serialize async work per key while allowing unrelated keys to run concurrently.
 * Android adaptation: uses Kotlin coroutines Mutex + Deferred instead of Promise chains.
 */

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ---------- Hooks ----------

/**
 * Hooks for enqueue/settle lifecycle.
 * Aligned with TS KeyedAsyncQueueHooks.
 */
data class KeyedAsyncQueueHooks(
    val onEnqueue: (() -> Unit)? = null,
    val onSettle: (() -> Unit)? = null,
)

// ---------- Free function ----------

/**
 * Serialize async work per key while allowing unrelated keys to run concurrently.
 * Aligned with TS enqueueKeyedTask.
 */
suspend fun <T> enqueueKeyedTask(
    tails: ConcurrentHashMap<String, CompletableDeferred<Unit>>,
    key: String,
    task: suspend () -> T,
    hooks: KeyedAsyncQueueHooks? = null,
): T {
    hooks?.onEnqueue?.invoke()

    val previous = tails[key]
    // Wait for previous task on same key to settle (ignore errors)
    try {
        previous?.await()
    } catch (_: Exception) {
        // ignore
    }

    val tail = CompletableDeferred<Unit>()
    tails[key] = tail

    return try {
        val result = task()
        tail.complete(Unit)
        result
    } catch (e: Exception) {
        tail.completeExceptionally(e)
        throw e
    } finally {
        hooks?.onSettle?.invoke()
        // Cleanup if our tail is still the current one
        if (tails[key] === tail) {
            tails.remove(key, tail)
        }
    }
}

// ---------- Class wrapper ----------

/**
 * Keyed async queue: serializes async work per key.
 * Aligned with TS KeyedAsyncQueue class.
 */
class KeyedAsyncQueue {
    private val tails = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /**
     * Enqueue a task for the given key.
     * Tasks with the same key run sequentially; different keys run concurrently.
     */
    suspend fun <T> enqueue(
        key: String,
        task: suspend () -> T,
        hooks: KeyedAsyncQueueHooks? = null,
    ): T = enqueueKeyedTask(tails, key, task, hooks)
}
