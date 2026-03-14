package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: serialized keyed async work queue.
 */


import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Queue for executing async tasks serially by key
 *
 * Aligned with OpenClaw's KeyedAsyncQueue implementation:
 * - Tasks with the same key execute serially
 * - Tasks with different keys can execute concurrently
 * - Guarantees message order (messages from same session processed in received order)
 *
 * Reference: openclaw/src/plugin-sdk/keyed-async-queue.ts
 */
class KeyedAsyncQueue {

    // Store the last task (tail) for each key
    private val tails = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /**
     * Enqueue task
     *
     * @param key Queue key (usually sessionId or channelId)
     * @param task Task to execute
     * @return Task execution result
     */
    suspend fun <T> enqueue(key: String, task: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            // Get current queue's tail task
            val previousTail = tails[key]

            // Create new tail
            val newTail = CompletableDeferred<Unit>()

            // Update tail (update before execution to avoid race condition)
            tails[key] = newTail

            try {
                // Wait for previous task to complete (ignore errors)
                try {
                    previousTail?.await()
                } catch (e: Exception) {
                    // Ignore previous task's error
                }

                // Execute current task
                val result = task()

                // Mark current task as complete
                newTail.complete(Unit)

                // Clean up completed tail
                if (tails[key] == newTail) {
                    tails.remove(key)
                }

                result
            } catch (e: Exception) {
                // Mark current task as complete (even if failed)
                newTail.complete(Unit)

                // Clean up completed tail
                if (tails[key] == newTail) {
                    tails.remove(key)
                }

                throw e
            }
        }
    }

    /**
     * Get count of pending tasks in queue
     */
    fun getPendingCount(): Int = tails.size

    /**
     * Check if there's a task being processed for given key
     */
    fun isProcessing(key: String): Boolean = tails.containsKey(key)

    /**
     * Clear all queues (for testing or reset)
     */
    fun clear() {
        tails.clear()
    }
}
