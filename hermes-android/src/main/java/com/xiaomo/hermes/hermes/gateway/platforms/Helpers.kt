package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Shared helper classes for gateway platform adapters.
 *
 * Ported from gateway/platforms/helpers.py
 */

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ─── Message Deduplication ────────────────────────────────────────────────────

/**
 * TTL-based message deduplication cache.
 */
class MessageDeduplicator(
    private val maxSize: Int = 2000,
    private val ttlSeconds: Double = 300.0,
) {
    private val _seen: ConcurrentHashMap<String, Double> = ConcurrentHashMap()

    /** Return true if [msgId] was already seen within the TTL window. */
    fun isDuplicate(msgId: String): Boolean {
        if (msgId.isEmpty()) return false
        val now = System.currentTimeMillis() / 1000.0
        val existing = _seen[msgId]
        if (existing != null) {
            if (now - existing < ttlSeconds) return true
            _seen.remove(msgId)
        }
        _seen[msgId] = now
        if (_seen.size > maxSize) {
            val cutoff = now - ttlSeconds
            val expired = _seen.entries.filter { it.value <= cutoff }.map { it.key }
            for (k in expired) _seen.remove(k)
        }
        return false
    }

    fun clear() {
        _seen.clear()
    }
}

// ─── Text Batch Aggregation ──────────────────────────────────────────────────

/**
 * Aggregates rapid-fire text events into single messages.
 *
 * Ported from TextBatchAggregator in helpers.py. Android uses coroutines
 * instead of asyncio tasks.
 */
class TextBatchAggregator(
    private val scope: CoroutineScope,
    private val handler: suspend (MessageEvent) -> Unit,
    private val batchDelay: Double = 0.6,
    private val splitDelay: Double = 2.0,
    private val splitThreshold: Int = 4000,
) {
    private val _pending: ConcurrentHashMap<String, MessageEvent> = ConcurrentHashMap()
    private val _pendingTasks: ConcurrentHashMap<String, Job> = ConcurrentHashMap()
    private val _lastChunkLen: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    fun isEnabled(): Boolean = batchDelay > 0

    fun enqueue(event: MessageEvent, key: String) {
        val chunkLen = event.text.length
        val existing = _pending[key]
        if (existing == null) {
            _pending[key] = event
        } else {
            _pending[key] = existing.copy(text = "${existing.text}\n${event.text}")
        }
        _lastChunkLen[key] = chunkLen

        _pendingTasks[key]?.cancel()
        _pendingTasks[key] = scope.launch { _flush(key) }
    }

    private suspend fun _flush(key: String) {
        val currentTask = _pendingTasks[key]
        val lastLen = _lastChunkLen[key] ?: 0
        val delaySec = if (lastLen >= splitThreshold) splitDelay else batchDelay
        delay((delaySec * 1000).toLong())

        val event = _pending.remove(key)
        _lastChunkLen.remove(key)
        if (event != null) {
            try {
                handler(event)
            } catch (e: Exception) {
                Log.e("TextBatchAggregator", "Error dispatching batched event for $key", e)
            }
        }
        if (_pendingTasks[key] === currentTask) _pendingTasks.remove(key)
    }

    fun cancelAll() {
        for (task in _pendingTasks.values) task.cancel()
        _pendingTasks.clear()
        _pending.clear()
        _lastChunkLen.clear()
    }
}

// ─── Markdown Stripping ──────────────────────────────────────────────────────

private val _RE_BOLD = Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)
private val _RE_ITALIC_STAR = Regex("\\*(.+?)\\*", RegexOption.DOT_MATCHES_ALL)
private val _RE_BOLD_UNDER = Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL)
private val _RE_ITALIC_UNDER = Regex("_(.+?)_", RegexOption.DOT_MATCHES_ALL)
private val _RE_CODE_BLOCK = Regex("```[a-zA-Z0-9_+-]*\n?")
private val _RE_INLINE_CODE = Regex("`(.+?)`")
private val _RE_HEADING = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
private val _RE_LINK = Regex("\\[([^\\]]+)\\]\\([^)]+\\)")
private val _RE_MULTI_NEWLINE = Regex("\n{3,}")

/**
 * Strip markdown formatting for plain-text platforms (SMS, iMessage, etc.).
 */
fun stripMarkdown(text: String): String {
    var result = text
    result = _RE_BOLD.replace(result, "$1")
    result = _RE_ITALIC_STAR.replace(result, "$1")
    result = _RE_BOLD_UNDER.replace(result, "$1")
    result = _RE_ITALIC_UNDER.replace(result, "$1")
    result = _RE_CODE_BLOCK.replace(result, "")
    result = _RE_INLINE_CODE.replace(result, "$1")
    result = _RE_HEADING.replace(result, "")
    result = _RE_LINK.replace(result, "$1")
    result = _RE_MULTI_NEWLINE.replace(result, "\n\n")
    return result.trim()
}

// ─── Thread Participation Tracking ───────────────────────────────────────────

/**
 * Persistent tracking of threads the bot has participated in.
 */
class ThreadParticipationTracker(
    private val platformName: String,
    private val maxTracked: Int = 500,
) {
    private val _threads: MutableSet<String> = _load().toMutableSet()

    private fun _statePath(): File {
        val home = File(System.getProperty("user.home") ?: "/tmp", ".hermes")
        return File(home, "${platformName}_threads.json")
    }

    private fun _load(): Set<String> {
        val path = _statePath()
        if (!path.exists()) return emptySet()
        return try {
            val text = path.readText(Charsets.UTF_8).trim()
            if (text.startsWith("[") && text.endsWith("]")) {
                text.substring(1, text.length - 1)
                    .split(",")
                    .map { it.trim().trim('"') }
                    .filter { it.isNotEmpty() }
                    .toSet()
            } else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun _save() {
        val path = _statePath()
        path.parentFile?.mkdirs()
        val list = _threads.toMutableList()
        if (list.size > maxTracked) {
            val trimmed = list.subList(list.size - maxTracked, list.size)
            _threads.clear()
            _threads.addAll(trimmed)
        }
        val json = _threads.joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
        path.writeText(json, Charsets.UTF_8)
    }

    /** Mark [threadId] as participated and persist. */
    fun mark(threadId: String) {
        if (threadId !in _threads) {
            _threads.add(threadId)
            _save()
        }
    }

    operator fun contains(threadId: String): Boolean = threadId in _threads

    fun clear() {
        _threads.clear()
    }
}

// ─── Phone Number Redaction ──────────────────────────────────────────────────

/**
 * Redact a phone number for logging, preserving country code and last 4.
 */
fun redactPhone(phone: String): String {
    if (phone.isEmpty()) return "<none>"
    if (phone.length <= 8) {
        return if (phone.length > 4) phone.substring(0, 2) + "****" + phone.substring(phone.length - 2) else "****"
    }
    return phone.substring(0, 4) + "****" + phone.substring(phone.length - 4)
}
