package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/bootstrap-budget.ts
 *
 * AndroidForClaw adaptation: bootstrap file truncation analysis and warnings.
 */

/**
 * Per-file budget status.
 */
data class BootstrapFileStatus(
    val fileName: String,
    val originalChars: Int,
    val injectedChars: Int,
    val truncated: Boolean,
    val truncationRatio: Float
)

/**
 * Overall bootstrap budget analysis.
 * Aligned with OpenClaw BootstrapBudgetAnalysis.
 */
data class BootstrapBudgetAnalysis(
    val files: List<BootstrapFileStatus>,
    val totalOriginalChars: Int,
    val totalInjectedChars: Int,
    val totalMaxChars: Int,
    val anyTruncated: Boolean,
    val nearLimit: Boolean
)

/**
 * Bootstrap budget analysis — tracks whether context files were truncated.
 * Aligned with OpenClaw bootstrap-budget.ts.
 */
object BootstrapBudget {

    /** Near-limit ratio threshold (85%). */
    const val DEFAULT_NEAR_LIMIT_RATIO = 0.85f

    /** Max files to include in warning message. */
    const val DEFAULT_MAX_WARNING_FILES = 3

    /**
     * Analyze bootstrap budget usage.
     * Aligned with OpenClaw analyzeBootstrapBudget.
     */
    fun analyzeBootstrapBudget(
        files: List<Pair<String, Pair<Int, Int>>>,  // (fileName, (originalChars, injectedChars))
        totalMaxChars: Int
    ): BootstrapBudgetAnalysis {
        val statuses = files.map { (fileName, chars) ->
            val (original, injected) = chars
            val truncated = injected < original
            val ratio = if (original > 0) injected.toFloat() / original else 1f
            BootstrapFileStatus(
                fileName = fileName,
                originalChars = original,
                injectedChars = injected,
                truncated = truncated,
                truncationRatio = ratio
            )
        }

        val totalOriginal = statuses.sumOf { it.originalChars }
        val totalInjected = statuses.sumOf { it.injectedChars }
        val anyTruncated = statuses.any { it.truncated }
        val nearLimit = totalMaxChars > 0 &&
            totalInjected.toFloat() / totalMaxChars >= DEFAULT_NEAR_LIMIT_RATIO

        return BootstrapBudgetAnalysis(
            files = statuses,
            totalOriginalChars = totalOriginal,
            totalInjectedChars = totalInjected,
            totalMaxChars = totalMaxChars,
            anyTruncated = anyTruncated,
            nearLimit = nearLimit
        )
    }

    /**
     * Build a prompt warning if bootstrap files were truncated.
     * Aligned with OpenClaw buildBootstrapPromptWarning.
     */
    fun buildBootstrapPromptWarning(analysis: BootstrapBudgetAnalysis): String? {
        if (!analysis.anyTruncated) return null

        val truncatedFiles = analysis.files
            .filter { it.truncated }
            .sortedBy { it.truncationRatio }
            .take(DEFAULT_MAX_WARNING_FILES)

        return buildString {
            appendLine("[Bootstrap context was truncated to fit within budget]")
            for (file in truncatedFiles) {
                val pct = ((1 - file.truncationRatio) * 100).toInt()
                appendLine("- ${file.fileName}: ${pct}% truncated (${file.injectedChars}/${file.originalChars} chars)")
            }
            if (analysis.files.count { it.truncated } > DEFAULT_MAX_WARNING_FILES) {
                val remaining = analysis.files.count { it.truncated } - DEFAULT_MAX_WARNING_FILES
                appendLine("- ...and $remaining more files")
            }
            append("If context seems incomplete, read the full files directly.")
        }
    }

    /**
     * Build a truncation signature for deduplication.
     * Aligned with OpenClaw buildBootstrapTruncationSignature.
     */
    fun buildBootstrapTruncationSignature(analysis: BootstrapBudgetAnalysis): String {
        val truncated = analysis.files
            .filter { it.truncated }
            .sortedBy { it.fileName }
            .joinToString(",") { "${it.fileName}:${it.injectedChars}" }
        return "truncation:$truncated"
    }
}
