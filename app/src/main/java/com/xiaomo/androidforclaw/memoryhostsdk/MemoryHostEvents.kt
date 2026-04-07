package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/events.ts
 *
 * Event types emitted by the memory engine for observability.
 * Supports JSONL event log append/read.
 */

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

// ---------- Event Log Path ----------

const val MEMORY_HOST_EVENT_LOG_RELATIVE_PATH = "memory/.dreams/events.jsonl"

// ---------- Event Types ----------

/**
 * Memory dreaming phase name.
 * Aligned with TS MemoryDreamingPhaseName.
 */
enum class MemoryDreamingPhaseName(val value: String) {
    LIGHT("light"),
    DEEP("deep"),
    REM("rem");

    companion object {
        fun fromString(s: String): MemoryDreamingPhaseName? = entries.find { it.value == s }
    }
}

/**
 * Memory host events — sealed class hierarchy.
 * Aligned with TS MemoryHostEvent union.
 */
sealed class MemoryHostEvent {
    abstract val type: String
    abstract val timestamp: String

    /**
     * Recall was recorded.
     * Aligned with TS MemoryHostRecallRecordedEvent.
     */
    data class RecallRecorded(
        override val timestamp: String = currentIsoTimestamp(),
        val query: String,
        val resultCount: Int,
        val results: List<RecallResult> = emptyList()
    ) : MemoryHostEvent() {
        override val type = "memory.recall.recorded"
    }

    data class RecallResult(
        val path: String,
        val startLine: Int,
        val endLine: Int,
        val score: Double
    )

    /**
     * Promotion was applied.
     * Aligned with TS MemoryHostPromotionAppliedEvent.
     */
    data class PromotionApplied(
        override val timestamp: String = currentIsoTimestamp(),
        val memoryPath: String,
        val applied: Int,
        val candidates: List<PromotionCandidate> = emptyList()
    ) : MemoryHostEvent() {
        override val type = "memory.promotion.applied"
    }

    data class PromotionCandidate(
        val key: String,
        val path: String,
        val startLine: Int,
        val endLine: Int,
        val score: Double,
        val recallCount: Int
    )

    /**
     * Dream phase completed.
     * Aligned with TS MemoryHostDreamCompletedEvent.
     */
    data class DreamCompleted(
        override val timestamp: String = currentIsoTimestamp(),
        val phase: MemoryDreamingPhaseName,
        val inlinePath: String? = null,
        val reportPath: String? = null,
        val lineCount: Int,
        val storageMode: String = "inline" // "inline" | "separate" | "both"
    ) : MemoryHostEvent() {
        override val type = "memory.dream.completed"
    }

    // Legacy events for backward compat
    data class Written(val entry: MemoryEntry) : MemoryHostEvent() {
        override val type = "memory.written"
        override val timestamp = currentIsoTimestamp()
    }

    data class Deleted(val id: String, val key: String) : MemoryHostEvent() {
        override val type = "memory.deleted"
        override val timestamp = currentIsoTimestamp()
    }

    data class Queried(val query: MemoryQuery, val resultCount: Int) : MemoryHostEvent() {
        override val type = "memory.queried"
        override val timestamp = currentIsoTimestamp()
    }

    data class Error(val operation: String, val message: String) : MemoryHostEvent() {
        override val type = "memory.error"
        override val timestamp = currentIsoTimestamp()
    }
}

typealias MemoryHostEventListener = (MemoryHostEvent) -> Unit

/**
 * Thread-safe event listener set.
 * Uses CopyOnWriteArraySet per project patterns.
 */
val memoryHostEventListeners = CopyOnWriteArraySet<MemoryHostEventListener>()

// ---------- Event Log I/O ----------

/**
 * Resolve the event log file path for a workspace.
 * Aligned with TS resolveMemoryHostEventLogPath.
 */
fun resolveMemoryHostEventLogPath(workspaceDir: String): String =
    File(workspaceDir, MEMORY_HOST_EVENT_LOG_RELATIVE_PATH).absolutePath

/**
 * Append a memory host event to the JSONL log.
 * Aligned with TS appendMemoryHostEvent.
 */
suspend fun appendMemoryHostEvent(workspaceDir: String, event: MemoryHostEvent) {
    val eventLogPath = resolveMemoryHostEventLogPath(workspaceDir)
    val file = File(eventLogPath)
    file.parentFile?.mkdirs()

    val json = JSONObject().apply {
        put("type", event.type)
        put("timestamp", event.timestamp)
        when (event) {
            is MemoryHostEvent.RecallRecorded -> {
                put("query", event.query)
                put("resultCount", event.resultCount)
                put("results", JSONArray().apply {
                    event.results.forEach { r ->
                        put(JSONObject().apply {
                            put("path", r.path)
                            put("startLine", r.startLine)
                            put("endLine", r.endLine)
                            put("score", r.score)
                        })
                    }
                })
            }
            is MemoryHostEvent.PromotionApplied -> {
                put("memoryPath", event.memoryPath)
                put("applied", event.applied)
                put("candidates", JSONArray().apply {
                    event.candidates.forEach { c ->
                        put(JSONObject().apply {
                            put("key", c.key)
                            put("path", c.path)
                            put("startLine", c.startLine)
                            put("endLine", c.endLine)
                            put("score", c.score)
                            put("recallCount", c.recallCount)
                        })
                    }
                })
            }
            is MemoryHostEvent.DreamCompleted -> {
                put("phase", event.phase.value)
                event.inlinePath?.let { put("inlinePath", it) }
                event.reportPath?.let { put("reportPath", it) }
                put("lineCount", event.lineCount)
                put("storageMode", event.storageMode)
            }
            else -> { /* Legacy events not serialized to JSONL */ }
        }
    }
    file.appendText(json.toString() + "\n")
}

/**
 * Read memory host events from the JSONL log.
 * Aligned with TS readMemoryHostEvents.
 */
suspend fun readMemoryHostEvents(workspaceDir: String, limit: Int? = null): List<MemoryHostEvent> {
    val eventLogPath = resolveMemoryHostEventLogPath(workspaceDir)
    val file = File(eventLogPath)
    if (!file.exists()) return emptyList()

    val raw = file.readText()
    if (raw.isBlank()) return emptyList()

    val events = raw.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            try {
                parseMemoryHostEventFromJson(JSONObject(line))
            } catch (_: Exception) {
                null
            }
        }

    if (limit == null || limit <= 0) return events
    return events.takeLast(limit)
}

private fun parseMemoryHostEventFromJson(json: JSONObject): MemoryHostEvent? {
    val type = json.optString("type", "")
    val timestamp = json.optString("timestamp", currentIsoTimestamp())

    return when (type) {
        "memory.recall.recorded" -> {
            val resultsArray = json.optJSONArray("results")
            val results = mutableListOf<MemoryHostEvent.RecallResult>()
            if (resultsArray != null) {
                for (i in 0 until resultsArray.length()) {
                    val r = resultsArray.getJSONObject(i)
                    results.add(MemoryHostEvent.RecallResult(
                        path = r.optString("path"),
                        startLine = r.optInt("startLine"),
                        endLine = r.optInt("endLine"),
                        score = r.optDouble("score")
                    ))
                }
            }
            MemoryHostEvent.RecallRecorded(
                timestamp = timestamp,
                query = json.optString("query"),
                resultCount = json.optInt("resultCount"),
                results = results
            )
        }
        "memory.promotion.applied" -> {
            val candidatesArray = json.optJSONArray("candidates")
            val candidates = mutableListOf<MemoryHostEvent.PromotionCandidate>()
            if (candidatesArray != null) {
                for (i in 0 until candidatesArray.length()) {
                    val c = candidatesArray.getJSONObject(i)
                    candidates.add(MemoryHostEvent.PromotionCandidate(
                        key = c.optString("key"),
                        path = c.optString("path"),
                        startLine = c.optInt("startLine"),
                        endLine = c.optInt("endLine"),
                        score = c.optDouble("score"),
                        recallCount = c.optInt("recallCount")
                    ))
                }
            }
            MemoryHostEvent.PromotionApplied(
                timestamp = timestamp,
                memoryPath = json.optString("memoryPath"),
                applied = json.optInt("applied"),
                candidates = candidates
            )
        }
        "memory.dream.completed" -> {
            MemoryHostEvent.DreamCompleted(
                timestamp = timestamp,
                phase = MemoryDreamingPhaseName.fromString(json.optString("phase")) ?: MemoryDreamingPhaseName.LIGHT,
                inlinePath = json.optString("inlinePath").takeIf { it.isNotEmpty() },
                reportPath = json.optString("reportPath").takeIf { it.isNotEmpty() },
                lineCount = json.optInt("lineCount"),
                storageMode = json.optString("storageMode", "inline")
            )
        }
        else -> null
    }
}

// ---------- Utility ----------

internal fun currentIsoTimestamp(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date())
}
