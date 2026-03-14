package com.xiaomo.androidforclaw.agent.loop

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: detect repetitive tool loops.
 */


import android.util.Log
import com.google.gson.Gson
import java.security.MessageDigest

/**
 * Tool loop detector
 * Reference: OpenClaw's tool-loop-detection.ts implementation
 *
 * Detects the following loop patterns:
 * 1. generic_repeat - Generic repeated calls
 * 2. known_poll_no_progress - Known polling tools with no progress
 * 3. ping_pong - Two tools calling back and forth
 * 4. global_circuit_breaker - Global circuit breaker (critical loop)
 */
object ToolLoopDetection {
    private const val TAG = "ToolLoopDetection"

    // Default configuration (aligned with OpenClaw)
    private const val TOOL_CALL_HISTORY_SIZE = 30
    private const val WARNING_THRESHOLD = 10
    private const val CRITICAL_THRESHOLD = 20
    private const val GLOBAL_CIRCUIT_BREAKER_THRESHOLD = 30

    private val gson = Gson()

    /**
     * Tool call history record
     */
    data class ToolCallRecord(
        val toolName: String,
        val argsHash: String,
        val resultHash: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val toolCallId: String? = null
    )

    /**
     * Loop detection result
     */
    sealed class LoopDetectionResult {
        object NoLoop : LoopDetectionResult()

        data class LoopDetected(
            val level: Level,
            val detector: DetectorKind,
            val count: Int,
            val message: String,
            val warningKey: String? = null
        ) : LoopDetectionResult()

        enum class Level {
            WARNING,
            CRITICAL
        }

        enum class DetectorKind {
            GENERIC_REPEAT,
            KNOWN_POLL_NO_PROGRESS,
            PING_PONG,
            GLOBAL_CIRCUIT_BREAKER
        }
    }

    /**
     * Session state (stores tool call history)
     */
    class SessionState {
        val toolCallHistory = ArrayDeque<ToolCallRecord>(TOOL_CALL_HISTORY_SIZE)
        val reportedWarnings = mutableSetOf<String>()
    }

    /**
     * Calculate hash for tool call (toolName + params)
     */
    fun hashToolCall(toolName: String, params: Map<String, Any?>): String {
        val paramsHash = digestStable(params)
        return "$toolName:$paramsHash"
    }

    /**
     * Calculate hash for tool result
     */
    fun hashToolOutcome(toolName: String, params: Map<String, Any?>, result: String, error: Throwable?): String? {
        if (error != null) {
            return "error:${digestStable(error.message ?: "unknown")}"
        }

        // For known polling tools, hash only key state fields
        if (isKnownPollTool(toolName, params)) {
            // Simplified: directly hash result content
            return digestStable(result.take(500))
        }

        return digestStable(result.take(500))
    }

    /**
     * Detect tool call loop
     */
    fun detectToolCallLoop(
        state: SessionState,
        toolName: String,
        params: Map<String, Any?>
    ): LoopDetectionResult {
        val history = state.toolCallHistory
        val currentHash = hashToolCall(toolName, params)

        // 1. Global circuit breaker (highest priority)
        val noProgress = getNoProgressStreak(history, toolName, currentHash)
        if (noProgress.count >= GLOBAL_CIRCUIT_BREAKER_THRESHOLD) {
            val warningKey = "global:$toolName:$currentHash:${noProgress.latestResultHash}"
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectionResult.DetectorKind.GLOBAL_CIRCUIT_BREAKER,
                count = noProgress.count,
                message = "CRITICAL: $toolName has repeated identical no-progress outcomes ${noProgress.count} times. " +
                        "Session execution blocked by global circuit breaker to prevent runaway loops.",
                warningKey = warningKey
            )
        }

        // 2. Known poll no progress
        val knownPollTool = isKnownPollTool(toolName, params)
        if (knownPollTool && noProgress.count >= CRITICAL_THRESHOLD) {
            val warningKey = "poll:$toolName:$currentHash:${noProgress.latestResultHash}"
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectionResult.DetectorKind.KNOWN_POLL_NO_PROGRESS,
                count = noProgress.count,
                message = "CRITICAL: Called $toolName with identical arguments and no progress ${noProgress.count} times. " +
                        "This appears to be a stuck polling loop. Session execution blocked to prevent resource waste.",
                warningKey = warningKey
            )
        }

        if (knownPollTool && noProgress.count >= WARNING_THRESHOLD) {
            val warningKey = "poll:$toolName:$currentHash:${noProgress.latestResultHash}"
            // Report warning only once
            if (warningKey !in state.reportedWarnings) {
                state.reportedWarnings.add(warningKey)
                return LoopDetectionResult.LoopDetected(
                    level = LoopDetectionResult.Level.WARNING,
                    detector = LoopDetectionResult.DetectorKind.KNOWN_POLL_NO_PROGRESS,
                    count = noProgress.count,
                    message = "WARNING: You have called $toolName ${noProgress.count} times with identical arguments and no progress. " +
                            "Stop polling and either (1) increase wait time between checks, or (2) report the task as failed if the process is stuck.",
                    warningKey = warningKey
                )
            }
        }

        // 3. Ping-pong detection
        val pingPong = getPingPongStreak(history, currentHash)
        if (pingPong.count >= CRITICAL_THRESHOLD && pingPong.noProgressEvidence) {
            val warningKey = "pingpong:${pingPong.pairedSignature}:$currentHash"
            return LoopDetectionResult.LoopDetected(
                level = LoopDetectionResult.Level.CRITICAL,
                detector = LoopDetectionResult.DetectorKind.PING_PONG,
                count = pingPong.count,
                message = "CRITICAL: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls) with no progress. " +
                        "This appears to be a stuck ping-pong loop. Session execution blocked to prevent resource waste.",
                warningKey = warningKey
            )
        }

        if (pingPong.count >= WARNING_THRESHOLD) {
            val warningKey = "pingpong:${pingPong.pairedSignature}:$currentHash"
            if (warningKey !in state.reportedWarnings) {
                state.reportedWarnings.add(warningKey)
                return LoopDetectionResult.LoopDetected(
                    level = LoopDetectionResult.Level.WARNING,
                    detector = LoopDetectionResult.DetectorKind.PING_PONG,
                    count = pingPong.count,
                    message = "WARNING: You are alternating between repeated tool-call patterns (${pingPong.count} consecutive calls). " +
                            "This looks like a ping-pong loop; stop retrying and report the task as failed.",
                    warningKey = warningKey
                )
            }
        }

        // 4. Generic repeat (last check)
        if (!knownPollTool) {
            val recentCount = history.count { it.toolName == toolName && it.argsHash == currentHash }
            if (recentCount >= WARNING_THRESHOLD) {
                val warningKey = "generic:$toolName:$currentHash"
                if (warningKey !in state.reportedWarnings) {
                    state.reportedWarnings.add(warningKey)
                    return LoopDetectionResult.LoopDetected(
                        level = LoopDetectionResult.Level.WARNING,
                        detector = LoopDetectionResult.DetectorKind.GENERIC_REPEAT,
                        count = recentCount,
                        message = "WARNING: You have called $toolName $recentCount times with identical arguments. " +
                                "If this is not making progress, stop retrying and report the task as failed.",
                        warningKey = warningKey
                    )
                }
            }
        }

        return LoopDetectionResult.NoLoop
    }

    /**
     * Record tool call
     */
    fun recordToolCall(
        state: SessionState,
        toolName: String,
        params: Map<String, Any?>,
        toolCallId: String? = null
    ) {
        val record = ToolCallRecord(
            toolName = toolName,
            argsHash = hashToolCall(toolName, params),
            toolCallId = toolCallId
        )

        state.toolCallHistory.addLast(record)

        // Maintain history size limit
        while (state.toolCallHistory.size > TOOL_CALL_HISTORY_SIZE) {
            state.toolCallHistory.removeFirst()
        }
    }

    /**
     * Record tool call outcome
     */
    fun recordToolCallOutcome(
        state: SessionState,
        toolName: String,
        toolParams: Map<String, Any?>,
        result: String,
        error: Throwable? = null,
        toolCallId: String? = null
    ) {
        val resultHash = hashToolOutcome(toolName, toolParams, result, error) ?: return
        val argsHash = hashToolCall(toolName, toolParams)

        // Find and update most recent matching record
        var matched = false
        for (i in state.toolCallHistory.size - 1 downTo 0) {
            val call = state.toolCallHistory[i]
            if (toolCallId != null && call.toolCallId != toolCallId) continue
            if (call.toolName != toolName || call.argsHash != argsHash) continue
            if (call.resultHash != null) continue

            // Update resultHash (need to replace entire object)
            state.toolCallHistory[i] = call.copy(resultHash = resultHash)
            matched = true
            break
        }

        // If no match, add new record
        if (!matched) {
            val record = ToolCallRecord(
                toolName = toolName,
                argsHash = argsHash,
                resultHash = resultHash,
                toolCallId = toolCallId
            )
            state.toolCallHistory.addLast(record)

            // Maintain history size limit
            while (state.toolCallHistory.size > TOOL_CALL_HISTORY_SIZE) {
                state.toolCallHistory.removeFirst()
            }
        }
    }

    /**
     * Get no-progress streak count
     */
    private fun getNoProgressStreak(
        history: ArrayDeque<ToolCallRecord>,
        toolName: String,
        argsHash: String
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
     * Get ping-pong streak count
     */
    private fun getPingPongStreak(
        history: ArrayDeque<ToolCallRecord>,
        currentSignature: String
    ): PingPongStreak {
        if (history.isEmpty()) {
            return PingPongStreak(0, null, false)
        }

        val last = history.last()

        // Find most recent different signature
        var otherSignature: String? = null
        for (i in history.size - 2 downTo 0) {
            val call = history[i]
            if (call.argsHash != last.argsHash) {
                otherSignature = call.argsHash
                break
            }
        }

        if (otherSignature == null) {
            return PingPongStreak(0, null, false)
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
            return PingPongStreak(0, null, false)
        }

        // Check if current signature matches expected
        val expectedCurrentSignature = otherSignature
        if (currentSignature != expectedCurrentSignature) {
            return PingPongStreak(0, null, false)
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

        if (firstHashA == null || firstHashB == null) {
            noProgressEvidence = false
        }

        return PingPongStreak(alternatingTailCount + 1, otherSignature, noProgressEvidence)
    }

    /**
     * Check if it's a known polling tool
     */
    private fun isKnownPollTool(toolName: String, params: Map<String, Any?>): Boolean {
        // Android platform specific polling tools
        return when (toolName) {
            "wait" -> true
            "wait_for_element" -> true
            "command_status" -> true
            else -> false
        }
    }

    /**
     * Stable hash (ensure same input produces same hash)
     */
    private fun digestStable(value: Any?): String {
        val serialized = stableStringify(value)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(serialized.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Stable serialization (sorted keys)
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

    private data class NoProgressStreak(
        val count: Int,
        val latestResultHash: String?
    )

    private data class PingPongStreak(
        val count: Int,
        val pairedSignature: String?,
        val noProgressEvidence: Boolean
    )
}
