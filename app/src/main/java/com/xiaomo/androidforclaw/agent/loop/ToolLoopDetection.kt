package com.xiaomo.androidforclaw.agent.loop

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-loop-detection.ts
 *
 * AndroidForClaw adaptation: detect repetitive tool loops.
 * Aligned 1:1 with OpenClaw tool-loop-detection.ts.
 */


import com.xiaomo.androidforclaw.logging.Log
import com.google.gson.Gson
import java.security.MessageDigest

/**
 * Configuration for tool loop detection.
 * Aligned with OpenClaw ToolLoopDetectionConfig (config/types.tools.ts).
 */
data class ToolLoopDetectionConfig(
    val enabled: Boolean? = null,
    val historySize: Int? = null,
    val warningThreshold: Int? = null,
    val criticalThreshold: Int? = null,
    val globalCircuitBreakerThreshold: Int? = null,
    val detectors: DetectorToggles? = null,
) {
    data class DetectorToggles(
        val genericRepeat: Boolean? = null,
        val knownPollNoProgress: Boolean? = null,
        val pingPong: Boolean? = null,
    )
}

/**
 * Resolved (validated) loop detection config.
 * Aligned with OpenClaw ResolvedLoopDetectionConfig.
 */
internal data class ResolvedLoopDetectionConfig(
    val enabled: Boolean,
    val historySize: Int,
    val warningThreshold: Int,
    val criticalThreshold: Int,
    val globalCircuitBreakerThreshold: Int,
    val detectors: ResolvedDetectorToggles,
) {
    data class ResolvedDetectorToggles(
        val genericRepeat: Boolean,
        val knownPollNoProgress: Boolean,
        val pingPong: Boolean,
    )
}

/**
 * Loop detection result.
 * Aligned with OpenClaw LoopDetectionResult.
 */
sealed class LoopDetectionResult {
    data object NoLoop : LoopDetectionResult()

    data class LoopDetected(
        val level: Level,
        val detector: LoopDetectorKind,
        val count: Int,
        val message: String,
        val pairedToolName: String? = null,
        val warningKey: String? = null,
    ) : LoopDetectionResult()

    enum class Level { WARNING, CRITICAL }
}

/**
 * Detector kinds. Aligned with OpenClaw LoopDetectorKind.
 */
enum class LoopDetectorKind {
    GENERIC_REPEAT,
    KNOWN_POLL_NO_PROGRESS,
    PING_PONG,
    GLOBAL_CIRCUIT_BREAKER;

    val wireValue: String get() = name.lowercase()
}

/**
 * Tool loop detector.
 * Reference: OpenClaw's tool-loop-detection.ts implementation.
 *
 * Detects the following loop patterns:
 * 1. generic_repeat - Generic repeated calls
 * 2. known_poll_no_progress - Known polling tools with no progress
 * 3. ping_pong - Two tools calling back and forth
 * 4. global_circuit_breaker - Global circuit breaker (critical loop)
 */
object ToolLoopDetection {
    private const val TAG = "ToolLoopDetection"

    // Default configuration (aligned with OpenClaw DEFAULT_LOOP_DETECTION_CONFIG)
    const val TOOL_CALL_HISTORY_SIZE = 30
    const val WARNING_THRESHOLD = 10
    const val CRITICAL_THRESHOLD = 20
    const val GLOBAL_CIRCUIT_BREAKER_THRESHOLD = 30

    private val gson = Gson()

    /**
     * Tool call history record.
     * Aligned with OpenClaw SessionState.toolCallHistory entries.
     */
    data class ToolCallRecord(
        val toolName: String,
        val argsHash: String,
        var resultHash: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val toolCallId: String? = null,
    )

    /**
     * Session state (stores tool call history).
     * Aligned with OpenClaw SessionState (diagnostic-session-state.ts).
     * Note: No reportedWarnings — OpenClaw returns warnings every time condition is met,
     * caller decides whether to act on duplicates.
     */
    class SessionState {
        var toolCallHistory: MutableList<ToolCallRecord> = mutableListOf()
    }

    // ==================== Config Resolution ====================

    /**
     * Validate positive integer, fallback if invalid.
     * Aligned with OpenClaw asPositiveInt.
     */
    private fun asPositiveInt(value: Int?, fallback: Int): Int {
        if (value == null || value <= 0) return fallback
        return value
    }

    /**
     * Resolve and validate loop detection config.
     * Aligned with OpenClaw resolveLoopDetectionConfig.
     */
    internal fun resolveLoopDetectionConfig(config: ToolLoopDetectionConfig?): ResolvedLoopDetectionConfig {
        var warningThreshold = asPositiveInt(config?.warningThreshold, WARNING_THRESHOLD)
        var criticalThreshold = asPositiveInt(config?.criticalThreshold, CRITICAL_THRESHOLD)
        var globalCircuitBreakerThreshold = asPositiveInt(
            config?.globalCircuitBreakerThreshold, GLOBAL_CIRCUIT_BREAKER_THRESHOLD
        )

        // Threshold validation (aligned with OpenClaw)
        if (criticalThreshold <= warningThreshold) {
            criticalThreshold = warningThreshold + 1
        }
        if (globalCircuitBreakerThreshold <= criticalThreshold) {
            globalCircuitBreakerThreshold = criticalThreshold + 1
        }

        return ResolvedLoopDetectionConfig(
            enabled = config?.enabled ?: false, // OpenClaw: default false
            historySize = asPositiveInt(config?.historySize, TOOL_CALL_HISTORY_SIZE),
            warningThreshold = warningThreshold,
            criticalThreshold = criticalThreshold,
            globalCircuitBreakerThreshold = globalCircuitBreakerThreshold,
            detectors = ResolvedLoopDetectionConfig.ResolvedDetectorToggles(
                genericRepeat = config?.detectors?.genericRepeat ?: true,
                knownPollNoProgress = config?.detectors?.knownPollNoProgress ?: true,
                pingPong = config?.detectors?.pingPong ?: true,
            ),
        )
    }

    // ==================== Hashing ====================

    /**
     * Hash a tool call for pattern matching (toolName + params).
     * Aligned with OpenClaw hashToolCall.
     */
    fun hashToolCall(toolName: String, params: Any?): String {
        val paramsHash = digestStable(params)
        return "$toolName:$paramsHash"
    }

    /**
     * Stable serialization (sorted keys).
     * Aligned with OpenClaw stableStringify.
     */
    private fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> gson.toJson(value)
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> {
                val sorted = value.toSortedMap(compareBy { it.toString() })
                val entries = sorted.map { (k, v) ->
                    "${gson.toJson(k.toString())}:${stableStringify(v)}"
                }
                "{${entries.joinToString(",")}}"
            }
            is List<*> -> {
                val items = value.map { stableStringify(it) }
                "[${items.joinToString(",")}]"
            }
            else -> gson.toJson(value)
        }
    }

    /**
     * Stable serialization with fallback for errors.
     * Aligned with OpenClaw stableStringifyFallback.
     */
    private fun stableStringifyFallback(value: Any?): String {
        return try {
            stableStringify(value)
        } catch (_: Exception) {
            when {
                value == null -> "null"
                value is String -> value
                value is Number || value is Boolean -> value.toString()
                value is Throwable -> "${value.javaClass.simpleName}:${value.message}"
                else -> value.toString()
            }
        }
    }

    /**
     * Stable hash (SHA-256).
     * Aligned with OpenClaw digestStable.
     */
    private fun digestStable(value: Any?): String {
        val serialized = stableStringifyFallback(value)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(serialized.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ==================== Tool Result Parsing ====================

    /**
     * Extract text content from structured tool result.
     * Aligned with OpenClaw extractTextContent.
     */
    private fun extractTextContent(result: Any?): String {
        if (result !is Map<*, *>) return ""
        val content = result["content"]
        if (content !is List<*>) return ""
        return content
            .filterIsInstance<Map<*, *>>()
            .filter { it["type"] is String && it["text"] is String }
            .joinToString("\n") { it["text"] as String }
            .trim()
    }

    /**
     * Format error for hashing.
     * Aligned with OpenClaw formatErrorForHash.
     */
    private fun formatErrorForHash(error: Any?): String {
        return when (error) {
            is Throwable -> error.message ?: error.javaClass.simpleName
            is String -> error
            is Number, is Boolean -> error.toString()
            else -> stableStringify(error)
        }
    }

    /**
     * Hash a tool call outcome (result or error).
     * Aligned with OpenClaw hashToolOutcome — handles structured results with
     * extractTextContent and details extraction for known poll tools.
     */
    fun hashToolOutcome(
        toolName: String,
        params: Any?,
        result: Any?,
        error: Any?,
    ): String? {
        if (error != null) {
            return "error:${digestStable(formatErrorForHash(error))}"
        }

        if (result !is Map<*, *>) {
            return if (result == null) null else digestStable(result)
        }

        val details = (result["details"] as? Map<*, *>) ?: emptyMap<Any, Any>()
        val text = extractTextContent(result)

        // Known poll tool specific hashing (aligned with OpenClaw)
        if (isKnownPollToolCall(toolName, params) && toolName == "process" && params is Map<*, *>) {
            val action = params["action"]
            if (action == "poll") {
                return digestStable(mapOf(
                    "action" to action,
                    "status" to details["status"],
                    "exitCode" to (details["exitCode"]),
                    "exitSignal" to (details["exitSignal"]),
                    "aggregated" to (details["aggregated"]),
                    "text" to text,
                ))
            }
            if (action == "log") {
                return digestStable(mapOf(
                    "action" to action,
                    "status" to details["status"],
                    "totalLines" to (details["totalLines"]),
                    "totalChars" to (details["totalChars"]),
                    "truncated" to (details["truncated"]),
                    "exitCode" to (details["exitCode"]),
                    "exitSignal" to (details["exitSignal"]),
                    "text" to text,
                ))
            }
        }

        return digestStable(mapOf(
            "details" to details,
            "text" to text,
        ))
    }

    // ==================== Known Poll Tools ====================

    /**
     * Check if it's a known polling tool.
     * Aligned with OpenClaw isKnownPollToolCall.
     * Android adaptation: includes Android-specific polling tools.
     */
    private fun isKnownPollToolCall(toolName: String, params: Any?): Boolean {
        // OpenClaw: command_status + process(action=poll/log)
        if (toolName == "command_status") return true
        if (toolName == "process" && params is Map<*, *>) {
            val action = params["action"]
            return action == "poll" || action == "log"
        }
        // Android platform specific polling tools
        if (toolName == "wait" || toolName == "wait_for_element") return true
        return false
    }

    // ==================== Streak Detection ====================

    private data class NoProgressStreak(
        val count: Int,
        val latestResultHash: String?,
    )

    private data class PingPongStreak(
        val count: Int,
        val pairedToolName: String?,
        val pairedSignature: String?,
        val noProgressEvidence: Boolean,
    )

    /**
     * Get no-progress streak count.
     * Aligned with OpenClaw getNoProgressStreak.
     */
    private fun getNoProgressStreak(
        history: List<ToolCallRecord>,
        toolName: String,
        argsHash: String,
    ): NoProgressStreak {
        var streak = 0
        var latestResultHash: String? = null

        for (i in history.size - 1 downTo 0) {
            val record = history[i]
            if (record.toolName != toolName || record.argsHash != argsHash) continue
            if (record.resultHash == null) continue

            if (latestResultHash == null) {
                latestResultHash = record.resultHash
                streak = 1
                continue
            }

            if (record.resultHash != latestResultHash) {
                break
            }

            streak++
        }

        return NoProgressStreak(streak, latestResultHash)
    }

    /**
     * Get ping-pong streak count.
     * Aligned with OpenClaw getPingPongStreak — includes pairedToolName.
     */
    private fun getPingPongStreak(
        history: List<ToolCallRecord>,
        currentSignature: String,
    ): PingPongStreak {
        if (history.isEmpty()) {
            return PingPongStreak(0, null, null, false)
        }

        val last = history.last()

        // Find most recent different signature
        var otherSignature: String? = null
        var otherToolName: String? = null
        for (i in history.size - 2 downTo 0) {
            val call = history[i]
            if (call.argsHash != last.argsHash) {
                otherSignature = call.argsHash
                otherToolName = call.toolName
                break
            }
        }

        if (otherSignature == null || otherToolName == null) {
            return PingPongStreak(0, null, null, false)
        }

        // Calculate alternating tail length
        var alternatingTailCount = 0
        for (i in history.size - 1 downTo 0) {
            val call = history[i]
            val expected = if (alternatingTailCount % 2 == 0) last.argsHash else otherSignature
            if (call.argsHash != expected) break
            alternatingTailCount++
        }

        if (alternatingTailCount < 2) {
            return PingPongStreak(0, null, null, false)
        }

        // Check if current signature matches expected
        val expectedCurrentSignature = otherSignature
        if (currentSignature != expectedCurrentSignature) {
            return PingPongStreak(0, null, null, false)
        }

        // Check for no-progress evidence
        val tailStart = maxOf(0, history.size - alternatingTailCount)
        var firstHashA: String? = null
        var firstHashB: String? = null
        var noProgressEvidence = true

        for (i in tailStart until history.size) {
            val call = history[i]
            if (call.resultHash == null) {
                noProgressEvidence = false
                break
            }

            if (call.argsHash == last.argsHash) {
                if (firstHashA == null) {
                    firstHashA = call.resultHash
                } else if (firstHashA != call.resultHash) {
                    noProgressEvidence = false
                    break
                }
            } else if (call.argsHash == otherSignature) {
                if (firstHashB == null) {
                    firstHashB = call.resultHash
                } else if (firstHashB != call.resultHash) {
                    noProgressEvidence = false
                    break
                }
            } else {
                noProgressEvidence = false
                break
            }
        }

        // Need repeated stable outcomes on both sides (aligned with OpenClaw)
        if (firstHashA == null || firstHashB == null) {
            noProgressEvidence = false
        }

        return PingPongStreak(
            count = alternatingTailCount + 1,
            pairedToolName = last.toolName,
            pairedSignature = last.argsHash,
            noProgressEvidence = noProgressEvidence,
        )
    }

    /**
     * Canonical pair key for ping-pong warning key (sorted).
     * Aligned with OpenClaw canonicalPairKey.
     */
    private fun canonicalPairKey(signatureA: String, signatureB: String): String {
        return listOf(signatureA, signatureB).sorted().joinToString("|")
    }

    // ==================== Detection ====================

    /**
     * Detect if an agent is stuck in a repetitive tool call loop.
     * Aligned with OpenClaw detectToolCallLoop.
     *
     * Returns NoLoop when detection is disabled (config.enabled=false, the default).
     */
    fun detectToolCallLoop(
        state: SessionState,
        toolName: String,
        params: Any?,
        config: ToolLoopDetectionConfig? = null,
    ): LoopDetectionResult {
        val resolvedConfig = resolveLoopDetectionConfig(config)
        if (!resolvedConfig.enabled) {
            return LoopDetectionResult.NoLoop
        }

        val history = state.toolCallHistory
        val currentHash = hashToolCall(toolName, params)
        val noProgress = getNoProgressStreak(history, toolName, currentHash)
        val noProgressStreak = noProgress.count
        val knownPollTool = isKnownPollToolCall(toolName, params)
        val pingPong = getPingPongStreak(history, currentHash)

        // 1. Global circuit breaker (highest priority)
        if (noProgressStreak >= resolvedConfig.globalCircuitBreakerThreshold) {
            Log.e(TAG, "Global circuit breaker triggered: $toolName repeated $noProgressStreak times with no progress")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectorKind.GLOBAL_CIRCUIT_BREAKER,
                count = noProgressStreak,
                message = "CRITICAL: $toolName has repeated identical no-progress outcomes $noProgressStreak times. " +
                    "Session execution blocked by global circuit breaker to prevent runaway loops.",
                warningKey = "global:$toolName:$currentHash:${noProgress.latestResultHash ?: "none"}",
            )
        }

        // 2. Known poll no progress (critical)
        if (knownPollTool &&
            resolvedConfig.detectors.knownPollNoProgress &&
            noProgressStreak >= resolvedConfig.criticalThreshold
        ) {
            Log.e(TAG, "Critical polling loop detected: $toolName repeated $noProgressStreak times")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectorKind.KNOWN_POLL_NO_PROGRESS,
                count = noProgressStreak,
                message = "CRITICAL: Called $toolName with identical arguments and no progress $noProgressStreak times. " +
                    "This appears to be a stuck polling loop. Session execution blocked to prevent resource waste.",
                warningKey = "poll:$toolName:$currentHash:${noProgress.latestResultHash ?: "none"}",
            )
        }

        // 3. Known poll no progress (warning)
        if (knownPollTool &&
            resolvedConfig.detectors.knownPollNoProgress &&
            noProgressStreak >= resolvedConfig.warningThreshold
        ) {
            Log.w(TAG, "Polling loop warning: $toolName repeated $noProgressStreak times")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.WARNING,
                detector = LoopDetectorKind.KNOWN_POLL_NO_PROGRESS,
                count = noProgressStreak,
                message = "WARNING: You have called $toolName $noProgressStreak times with identical arguments and no progress. " +
                    "Stop polling and either (1) increase wait time between checks, or (2) report the task as failed if the process is stuck.",
                warningKey = "poll:$toolName:$currentHash:${noProgress.latestResultHash ?: "none"}",
            )
        }

        // 4. Ping-pong detection
        val pingPongWarningKey = if (pingPong.pairedSignature != null) {
            "pingpong:${canonicalPairKey(currentHash, pingPong.pairedSignature)}"
        } else {
            "pingpong:$toolName:$currentHash"
        }

        if (resolvedConfig.detectors.pingPong &&
            pingPong.count >= resolvedConfig.criticalThreshold &&
            pingPong.noProgressEvidence
        ) {
            Log.e(TAG, "Critical ping-pong loop detected: alternating calls count=${pingPong.count} currentTool=$toolName")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectorKind.PING_PONG,
                count = pingPong.count,
                message = "CRITICAL: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls) with no progress. " +
                    "This appears to be a stuck ping-pong loop. Session execution blocked to prevent resource waste.",
                pairedToolName = pingPong.pairedToolName,
                warningKey = pingPongWarningKey,
            )
        }

        if (resolvedConfig.detectors.pingPong &&
            pingPong.count >= resolvedConfig.warningThreshold
        ) {
            Log.w(TAG, "Ping-pong loop warning: alternating calls count=${pingPong.count} currentTool=$toolName")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.WARNING,
                detector = LoopDetectorKind.PING_PONG,
                count = pingPong.count,
                message = "WARNING: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls). " +
                    "This looks like a ping-pong loop; stop retrying and report the task as failed.",
                pairedToolName = pingPong.pairedToolName,
                warningKey = pingPongWarningKey,
            )
        }

        // 5. Generic repeat (last check, warn-only)
        val recentCount = history.count { it.toolName == toolName && it.argsHash == currentHash }

        if (!knownPollTool &&
            resolvedConfig.detectors.genericRepeat &&
            recentCount >= resolvedConfig.warningThreshold
        ) {
            Log.w(TAG, "Loop warning: $toolName called $recentCount times with identical arguments")
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.WARNING,
                detector = LoopDetectorKind.GENERIC_REPEAT,
                count = recentCount,
                message = "WARNING: You have called $toolName $recentCount times with identical arguments. " +
                    "If this is not making progress, stop retrying and report the task as failed.",
                warningKey = "generic:$toolName:$currentHash",
            )
        }

        return LoopDetectionResult.NoLoop
    }

    // ==================== Recording ====================

    /**
     * Record a tool call in the session's history for loop detection.
     * Maintains sliding window of last N calls.
     * Aligned with OpenClaw recordToolCall.
     */
    fun recordToolCall(
        state: SessionState,
        toolName: String,
        params: Any?,
        toolCallId: String? = null,
        config: ToolLoopDetectionConfig? = null,
    ) {
        val resolvedConfig = resolveLoopDetectionConfig(config)

        state.toolCallHistory.add(ToolCallRecord(
            toolName = toolName,
            argsHash = hashToolCall(toolName, params),
            toolCallId = toolCallId,
        ))

        if (state.toolCallHistory.size > resolvedConfig.historySize) {
            state.toolCallHistory.removeAt(0)
        }
    }

    /**
     * Record a completed tool call outcome so loop detection can identify no-progress repeats.
     * Aligned with OpenClaw recordToolCallOutcome.
     */
    fun recordToolCallOutcome(
        state: SessionState,
        toolName: String,
        toolParams: Any?,
        result: Any? = null,
        error: Any? = null,
        toolCallId: String? = null,
        config: ToolLoopDetectionConfig? = null,
    ) {
        val resolvedConfig = resolveLoopDetectionConfig(config)
        val resultHash = hashToolOutcome(toolName, toolParams, result, error) ?: return

        val argsHash = hashToolCall(toolName, toolParams)
        var matched = false

        for (i in state.toolCallHistory.size - 1 downTo 0) {
            val call = state.toolCallHistory[i]
            if (toolCallId != null && call.toolCallId != toolCallId) continue
            if (call.toolName != toolName || call.argsHash != argsHash) continue
            if (call.resultHash != null) continue

            call.resultHash = resultHash
            matched = true
            break
        }

        if (!matched) {
            state.toolCallHistory.add(ToolCallRecord(
                toolName = toolName,
                argsHash = argsHash,
                resultHash = resultHash,
                toolCallId = toolCallId,
            ))
        }

        // Trim to history size (aligned with OpenClaw splice approach)
        if (state.toolCallHistory.size > resolvedConfig.historySize) {
            val excess = state.toolCallHistory.size - resolvedConfig.historySize
            repeat(excess) { state.toolCallHistory.removeAt(0) }
        }
    }

    // ==================== Stats ====================

    /**
     * Get current tool call statistics for a session (for debugging/monitoring).
     * Aligned with OpenClaw getToolCallStats.
     */
    fun getToolCallStats(state: SessionState): ToolCallStats {
        val history = state.toolCallHistory
        val patterns = mutableMapOf<String, ToolCallStatEntry>()

        for (call in history) {
            val key = call.argsHash
            val existing = patterns[key]
            if (existing != null) {
                patterns[key] = existing.copy(count = existing.count + 1)
            } else {
                patterns[key] = ToolCallStatEntry(toolName = call.toolName, count = 1)
            }
        }

        var mostFrequent: ToolCallStatEntry? = null
        for (pattern in patterns.values) {
            if (mostFrequent == null || pattern.count > mostFrequent.count) {
                mostFrequent = pattern
            }
        }

        return ToolCallStats(
            totalCalls = history.size,
            uniquePatterns = patterns.size,
            mostFrequent = mostFrequent,
        )
    }

    data class ToolCallStatEntry(
        val toolName: String,
        val count: Int,
    )

    data class ToolCallStats(
        val totalCalls: Int,
        val uniquePatterns: Int,
        val mostFrequent: ToolCallStatEntry?,
    )
}
