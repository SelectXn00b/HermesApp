package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: compact prior conversation context.
 */


import android.util.Log
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.systemMessage
import com.xiaomo.androidforclaw.providers.llm.userMessage
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Message Compactor — aligned with OpenClaw compaction-safeguard.ts
 *
 * Features:
 * - Structured summary with 5 required sections (Decisions / Open TODOs / Constraints / Pending asks / Exact identifiers)
 * - Quality guard: audit summary for missing sections, retry if needed
 * - Recent turn preservation (default 3 turns kept verbatim)
 * - Tool failure collection
 * - Opaque identifier extraction from recent messages
 * - Compaction timeout + snapshot rollback (Gap 8)
 * - Fallback to structured skeleton on failure
 */
class MessageCompactor(
    private val llmProvider: UnifiedLLMProvider
) {
    companion object {
        private const val TAG = "MessageCompactor"

        // Aligned with OpenClaw compaction-safeguard.ts
        private const val MIN_MESSAGES_TO_COMPACT = 5
        private const val CHARS_PER_TOKEN = 4

        // Turn preservation (aligned with OpenClaw)
        const val DEFAULT_RECENT_TURNS_PRESERVE = 3
        const val MAX_RECENT_TURNS_PRESERVE = 12

        // Quality guard
        const val DEFAULT_QUALITY_GUARD_MAX_RETRIES = 1
        const val MAX_QUALITY_GUARD_MAX_RETRIES = 3

        // Tool failures
        private const val MAX_TOOL_FAILURES = 8
        private const val MAX_TOOL_FAILURE_CHARS = 240

        // Identifiers
        private const val MAX_EXTRACTED_IDENTIFIERS = 12

        // Timeout (Gap 8)
        const val DEFAULT_COMPACTION_TIMEOUT_MS = 120_000L  // 2 minutes

        // Required sections (aligned with OpenClaw)
        val REQUIRED_SUMMARY_SECTIONS = listOf(
            "## Decisions",
            "## Open TODOs",
            "## Constraints/Rules",
            "## Pending user asks",
            "## Exact identifiers"
        )
    }

    /**
     * Compact message history with full safeguard pipeline.
     *
     * @param messages Original message list
     * @param keepLastN Recent turns to preserve verbatim (default 3)
     * @param qualityGuardEnabled Enable quality audit + retry
     * @param qualityGuardMaxRetries Max retries for quality guard
     * @param timeoutMs Compaction timeout in milliseconds
     * @return Compacted message list
     */
    suspend fun compactMessages(
        messages: List<LegacyMessage>,
        keepLastN: Int = DEFAULT_RECENT_TURNS_PRESERVE,
        qualityGuardEnabled: Boolean = true,
        qualityGuardMaxRetries: Int = DEFAULT_QUALITY_GUARD_MAX_RETRIES,
        timeoutMs: Long = DEFAULT_COMPACTION_TIMEOUT_MS
    ): Result<List<LegacyMessage>> {
        // Pre-compaction snapshot for rollback
        val snapshot = messages.toList()

        return try {
            withTimeout(timeoutMs) {
                compactMessagesInternal(
                    messages = messages,
                    keepLastN = keepLastN.coerceIn(1, MAX_RECENT_TURNS_PRESERVE),
                    qualityGuardEnabled = qualityGuardEnabled,
                    qualityGuardMaxRetries = qualityGuardMaxRetries.coerceIn(0, MAX_QUALITY_GUARD_MAX_RETRIES)
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Compaction timed out after ${timeoutMs}ms, rolling back to snapshot")
            // Rollback: return original messages
            Result.success(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "Compaction failed, rolling back to snapshot", e)
            Result.success(snapshot)
        }
    }

    private suspend fun compactMessagesInternal(
        messages: List<LegacyMessage>,
        keepLastN: Int,
        qualityGuardEnabled: Boolean,
        qualityGuardMaxRetries: Int
    ): Result<List<LegacyMessage>> {
        Log.d(TAG, "Starting compaction: total=${messages.size}, keepLast=$keepLastN")

        if (messages.size < MIN_MESSAGES_TO_COMPACT) {
            Log.d(TAG, "Too few messages, skipping compaction")
            return Result.success(messages)
        }

        // Separate system messages
        val systemMessages = messages.filter { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }

        if (nonSystemMessages.size <= keepLastN) {
            Log.d(TAG, "Non-system messages <= keepLastN, skipping")
            return Result.success(messages)
        }

        // Split: messages to compact vs recent to preserve
        val toCompact = nonSystemMessages.dropLast(keepLastN)
        val recentMessages = nonSystemMessages.takeLast(keepLastN)

        Log.d(TAG, "To compact: ${toCompact.size}, to preserve: ${recentMessages.size}")

        // Collect tool failures from messages to compact
        val toolFailures = collectToolFailures(toCompact)

        // Extract opaque identifiers from recent messages
        val identifiers = extractOpaqueIdentifiers(recentMessages)

        // Find the latest user ask
        val latestAsk = findLatestUserAsk(recentMessages) ?: findLatestUserAsk(toCompact)

        // Build structured compaction instructions
        val instructions = buildCompactionInstructions()

        // Attempt summarization with quality guard
        val totalAttempts = if (qualityGuardEnabled) qualityGuardMaxRetries + 1 else 1
        var bestSummary: String? = null

        for (attempt in 1..totalAttempts) {
            Log.d(TAG, "Summarization attempt $attempt/$totalAttempts")

            val summary = try {
                summarizeMessages(toCompact, instructions)
            } catch (e: Exception) {
                Log.e(TAG, "Summarization attempt $attempt failed", e)
                null
            }

            if (summary == null) continue

            // Append supplemental sections
            val enrichedSummary = enrichSummary(summary, toolFailures, identifiers, recentMessages)

            if (!qualityGuardEnabled || attempt == totalAttempts) {
                bestSummary = enrichedSummary
                break
            }

            // Quality audit
            val quality = auditSummaryQuality(enrichedSummary, identifiers, latestAsk)
            if (quality.ok) {
                Log.d(TAG, "Quality audit PASSED on attempt $attempt")
                bestSummary = enrichedSummary
                break
            }

            Log.w(TAG, "Quality audit FAILED on attempt $attempt: ${quality.reasons.joinToString(", ")}")
            bestSummary = enrichedSummary  // Keep best effort
        }

        // Fallback if all attempts failed
        if (bestSummary == null) {
            Log.w(TAG, "All summarization attempts failed, using structured fallback")
            bestSummary = buildStructuredFallbackSummary(toCompact)
        }

        // Build compacted message list
        val compacted = buildList {
            addAll(systemMessages)

            add(LegacyMessage(
                role = "user",
                content = buildString {
                    appendLine("The conversation history before this point was compacted into the following summary:")
                    appendLine()
                    appendLine("<summary>")
                    appendLine(bestSummary)
                    appendLine("</summary>")
                }
            ))
            add(LegacyMessage(
                role = "assistant",
                content = "I understand. I'll continue from where we left off, using the summary for context."
            ))

            addAll(recentMessages)
        }

        Log.d(TAG, "Compaction complete: ${messages.size} -> ${compacted.size} messages")
        return Result.success(compacted)
    }

    /**
     * Summarize messages using LLM with structured instructions.
     */
    private suspend fun summarizeMessages(
        messages: List<LegacyMessage>,
        instructions: String
    ): String {
        val textToSummarize = buildString {
            messages.forEach { msg ->
                appendLine("[${msg.role}]")
                val content = msg.content?.toString() ?: ""
                // Truncate very long individual messages
                if (content.length > 2000) {
                    appendLine(content.take(1800))
                    appendLine("... [truncated, ${content.length} chars total]")
                } else {
                    appendLine(content)
                }
                msg.toolCalls?.forEach { tc ->
                    appendLine("  -> tool: ${tc.function.name}(${tc.function.arguments.take(200)})")
                }
                appendLine()
            }
        }

        val messagesToSend = listOf(
            systemMessage(instructions),
            userMessage("Summarize this conversation history:\n\n$textToSummarize")
        )

        val response = llmProvider.chatWithTools(
            messages = messagesToSend,
            tools = null,
            reasoningEnabled = false
        )

        return response.content ?: throw IllegalStateException("LLM returned null content")
    }

    /**
     * Build compaction instructions with required sections.
     * Aligned with OpenClaw buildCompactionStructureInstructions.
     */
    private fun buildCompactionInstructions(): String {
        return buildString {
            appendLine("You are summarizing a conversation history to save context space.")
            appendLine()
            appendLine("Produce a compact, factual summary with these exact section headings:")
            REQUIRED_SUMMARY_SECTIONS.forEach { appendLine(it) }
            appendLine()
            appendLine("For ## Exact identifiers, preserve literal values exactly as seen (IDs, URLs, file paths, ports, hashes, dates, times).")
            appendLine("Do not omit unresolved asks from the user.")
            appendLine()
            appendLine("Rules:")
            appendLine("1. Preserve ALL important facts, decisions, and outcomes")
            appendLine("2. Keep identifiers (UUIDs, IPs, file paths, package names, URLs)")
            appendLine("3. Note errors, failures, and important observations")
            appendLine("4. Be concise but complete — capture what's needed to continue work")
            appendLine("5. Include tool names and their results when relevant")
        }
    }

    /**
     * Enrich summary with supplemental sections.
     */
    private fun enrichSummary(
        summary: String,
        toolFailures: List<ToolFailure>,
        identifiers: List<String>,
        recentMessages: List<LegacyMessage>
    ): String {
        return buildString {
            append(summary)

            // Append tool failures if not already in summary
            if (toolFailures.isNotEmpty() && !summary.contains("tool failure", ignoreCase = true)) {
                appendLine()
                appendLine()
                appendLine("### Tool Failures")
                toolFailures.take(MAX_TOOL_FAILURES).forEach { failure ->
                    appendLine("- ${failure.toolName}: ${failure.summary}")
                }
            }

            // Ensure identifiers section has extracted identifiers
            if (identifiers.isNotEmpty() && !hasAllIdentifiers(summary, identifiers)) {
                val missingIds = identifiers.filter { id -> !summary.contains(id) }
                if (missingIds.isNotEmpty()) {
                    appendLine()
                    appendLine()
                    appendLine("### Additional identifiers (auto-extracted)")
                    missingIds.take(MAX_EXTRACTED_IDENTIFIERS).forEach { id ->
                        appendLine("- $id")
                    }
                }
            }

            // Append recent turns context
            appendLine()
            appendLine()
            appendLine("## Recent turns preserved verbatim")
            recentMessages.take(3).forEach { msg ->
                val content = msg.content?.toString()?.take(600) ?: ""
                appendLine("- ${msg.role.replaceFirstChar { it.uppercase() }}: $content")
            }
        }
    }

    /**
     * Audit summary quality.
     * Aligned with OpenClaw auditSummaryQuality.
     */
    data class QualityAuditResult(val ok: Boolean, val reasons: List<String>)

    fun auditSummaryQuality(
        summary: String,
        identifiers: List<String>,
        latestAsk: String?
    ): QualityAuditResult {
        val reasons = mutableListOf<String>()
        val lines = summary.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        // Check required sections
        for (section in REQUIRED_SUMMARY_SECTIONS) {
            if (section !in lines) {
                reasons.add("missing_section:$section")
            }
        }

        // Check identifiers present
        val missingIds = identifiers.filter { id -> !summary.contains(id) }
        if (missingIds.isNotEmpty()) {
            reasons.add("missing_identifiers:${missingIds.take(3).joinToString(",")}")
        }

        // Check latest ask overlap
        if (latestAsk != null && !hasAskOverlap(summary, latestAsk)) {
            reasons.add("latest_user_ask_not_reflected")
        }

        return QualityAuditResult(ok = reasons.isEmpty(), reasons = reasons)
    }

    /**
     * Build structured fallback summary when LLM summarization fails.
     */
    private fun buildStructuredFallbackSummary(messages: List<LegacyMessage>): String {
        val toolNames = messages
            .flatMap { it.toolCalls ?: emptyList() }
            .map { it.function.name }
            .distinct()

        val userMsgCount = messages.count { it.role == "user" }
        val assistantMsgCount = messages.count { it.role == "assistant" }

        return buildString {
            appendLine("## Decisions")
            appendLine("No prior history available (summarization failed).")
            appendLine()
            appendLine("## Open TODOs")
            appendLine("None captured.")
            appendLine()
            appendLine("## Constraints/Rules")
            appendLine("None captured.")
            appendLine()
            appendLine("## Pending user asks")
            appendLine("None captured.")
            appendLine()
            appendLine("## Exact identifiers")
            appendLine("None captured.")
            appendLine()
            appendLine("## Statistics")
            appendLine("- User messages: $userMsgCount")
            appendLine("- Assistant messages: $assistantMsgCount")
            if (toolNames.isNotEmpty()) {
                appendLine("- Tools used: ${toolNames.joinToString(", ")}")
            }
        }
    }

    // --- Helper functions ---

    data class ToolFailure(
        val toolCallId: String,
        val toolName: String,
        val summary: String
    )

    private fun collectToolFailures(messages: List<LegacyMessage>): List<ToolFailure> {
        val failures = mutableListOf<ToolFailure>()
        for (msg in messages) {
            if (msg.role != "tool") continue
            val content = msg.content?.toString() ?: continue
            // Heuristic: tool result containing error/fail/exception
            if (content.contains("error", ignoreCase = true) ||
                content.contains("fail", ignoreCase = true) ||
                content.contains("exception", ignoreCase = true) ||
                content.contains("404") ||
                content.contains("500") ||
                content.contains("401") ||
                content.contains("403")) {
                failures.add(ToolFailure(
                    toolCallId = msg.toolCallId ?: "",
                    toolName = msg.name ?: "unknown",
                    summary = content.take(MAX_TOOL_FAILURE_CHARS).replace(Regex("\\s+"), " ").trim()
                ))
                if (failures.size >= MAX_TOOL_FAILURES) break
            }
        }
        return failures
    }

    /**
     * Extract opaque identifiers (URLs, paths, UUIDs, etc.) from recent messages.
     * Aligned with OpenClaw identifier extraction.
     */
    private fun extractOpaqueIdentifiers(messages: List<LegacyMessage>): List<String> {
        val identifiers = mutableSetOf<String>()

        val patterns = listOf(
            // UUIDs
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE),
            // URLs
            Regex("https?://[^\\s\"'`<>)\\]]+"),
            // File paths (absolute)
            Regex("/(?:[\\w.-]+/)+[\\w.-]+"),
            // Package names
            Regex("com\\.[\\w.]+"),
            // Port numbers (e.g., :8765)
            Regex(":\\d{2,5}\\b"),
            // SHA hashes (short)
            Regex("\\b[0-9a-f]{7,12}\\b"),
            // IP addresses
            Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
        )

        for (msg in messages) {
            val text = msg.content?.toString() ?: continue
            for (pattern in patterns) {
                pattern.findAll(text).forEach { match ->
                    val value = match.value.trim()
                        .trimStart('(', '"', '\'', '`', '[', '{', '<')
                        .trimEnd(')', ']', '"', '\'', '`', ',', ';', ':', '.', '!', '?', '<', '>')
                    if (value.length >= 4) {
                        identifiers.add(value)
                    }
                }
                if (identifiers.size >= MAX_EXTRACTED_IDENTIFIERS * 2) break
            }
        }

        return identifiers.take(MAX_EXTRACTED_IDENTIFIERS).toList()
    }

    private fun findLatestUserAsk(messages: List<LegacyMessage>): String? {
        return messages.lastOrNull { it.role == "user" }?.content?.toString()
    }

    private fun hasAllIdentifiers(summary: String, identifiers: List<String>): Boolean {
        return identifiers.all { summary.contains(it) }
    }

    private fun hasAskOverlap(summary: String, ask: String): Boolean {
        val summaryTokens = summary.lowercase().split(Regex("\\W+")).filter { it.length >= 3 }.toSet()
        val askTokens = ask.lowercase().split(Regex("\\W+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }

        if (askTokens.isEmpty()) return true

        val overlapCount = askTokens.count { it in summaryTokens }
        val requiredMatches = if (askTokens.size >= 3) 2 else 1
        return overlapCount >= requiredMatches
    }

    /**
     * Estimate token count of message list
     */
    fun estimateTokens(messages: List<LegacyMessage>): Int {
        val totalChars = messages.sumOf { msg ->
            (msg.content?.toString()?.length ?: 0) +
            (msg.toolCalls?.sumOf { it.function.arguments.length } ?: 0)
        }
        return totalChars / CHARS_PER_TOKEN
    }

    /**
     * Check if compaction is needed — now reads context window dynamically (Gap 2).
     */
    fun shouldCompact(
        messages: List<LegacyMessage>,
        contextWindowTokens: Int = 128_000
    ): Boolean {
        val estimatedTokens = estimateTokens(messages)
        val threshold = (contextWindowTokens * 0.7).toInt()

        Log.d(TAG, "Token estimate: $estimatedTokens / $contextWindowTokens (threshold: $threshold)")
        return estimatedTokens > threshold
    }

    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "shall",
        "should", "may", "might", "must", "can", "could", "and", "but", "or",
        "nor", "not", "for", "with", "about", "from", "into", "through",
        "during", "before", "after", "above", "below", "between", "under",
        "again", "further", "then", "once", "here", "there", "when", "where",
        "why", "how", "all", "each", "every", "both", "few", "more", "most",
        "other", "some", "such", "only", "own", "same", "than", "too", "very",
        "just", "because", "while", "that", "this", "what", "which", "who"
    )
}
