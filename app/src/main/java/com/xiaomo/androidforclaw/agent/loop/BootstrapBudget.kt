package com.xiaomo.androidforclaw.agent.loop

/**
 * Bootstrap Budget — 对齐 OpenClaw bootstrap-budget.ts
 *
 * 分析系统 prompt（bootstrap files）的 token 预算使用情况：
 * - 检测截断
 * - 检测接近限制
 * - 生成警告信息
 *
 * OpenClaw 源: ../openclaw/src/agents/bootstrap-budget.ts
 *
 * Android 适配：不读文件系统，而是分析系统 prompt 字符串。
 */

// ── Constants ──

/** 接近限制的比例阈值。OpenClaw: DEFAULT_BOOTSTRAP_NEAR_LIMIT_RATIO */
const val BOOTSTRAP_NEAR_LIMIT_RATIO = 0.85

/** 每个 bootstrap 文件的最大字符数（估算）。OpenClaw: per-file limit */
const val BOOTSTRAP_PER_FILE_MAX_CHARS = 64_000

/** 所有 bootstrap 文件总字符数限制。OpenClaw: total limit */
const val BOOTSTRAP_TOTAL_MAX_CHARS = 128_000

/** 警告中最多显示的截断文件数。OpenClaw: DEFAULT_BOOTSTRAP_PROMPT_WARNING_MAX_FILES */
const val BOOTSTRAP_PROMPT_WARNING_MAX_FILES = 3

/** 警告签名历史最大条目数。OpenClaw: DEFAULT_BOOTSTRAP_PROMPT_WARNING_SIGNATURE_HISTORY_MAX */
const val BOOTSTRAP_PROMPT_WARNING_SIGNATURE_HISTORY_MAX = 32

// ── Types ──

/** 截断原因。OpenClaw: BootstrapTruncationCause */
enum class BootstrapTruncationCause {
    /** 单文件超限 */
    PER_FILE_LIMIT,
    /** 总量超限 */
    TOTAL_LIMIT
}

/** 警告模式。OpenClaw: BootstrapPromptWarningMode */
enum class BootstrapWarningMode {
    OFF,
    ONCE,
    ALWAYS
}

/** 单个 bootstrap 段的注入统计。OpenClaw: BootstrapInjectionStat */
data class BootstrapInjectionStat(
    val name: String,
    val rawChars: Int,
    val injectedChars: Int,
    val truncated: Boolean
)

/** 带预算分析的 bootstrap 段。OpenClaw: BootstrapAnalyzedFile */
data class BootstrapAnalyzedSection(
    val name: String,
    val rawChars: Int,
    val injectedChars: Int,
    val truncated: Boolean,
    val nearLimit: Boolean,
    val causes: List<BootstrapTruncationCause>
)

/** 预算分析结果。OpenClaw: BootstrapBudgetAnalysis */
data class BootstrapBudgetAnalysis(
    val sections: List<BootstrapAnalyzedSection>,
    val truncatedSections: List<BootstrapAnalyzedSection>,
    val nearLimitSections: List<BootstrapAnalyzedSection>,
    val totalNearLimit: Boolean,
    val hasTruncation: Boolean,
    val totalRawChars: Int,
    val totalInjectedChars: Int,
    val totalTruncatedChars: Int,
    val bootstrapMaxChars: Int,
    val bootstrapTotalMaxChars: Int,
    val nearLimitRatio: Double
)

/** 截断报告元数据。OpenClaw: BootstrapTruncationReportMeta */
data class BootstrapTruncationReportMeta(
    val warningMode: BootstrapWarningMode,
    val warningShown: Boolean,
    val truncatedFiles: Int,
    val nearLimitFiles: Int,
    val totalNearLimit: Boolean
)

// ── Analysis Functions ──

/**
 * 分析单个 bootstrap 段。
 * OpenClaw: analyzeSingleBootstrapFile
 *
 * @param name 段名称（如 "System Prompt", "Skills", "TOOLS.md"）
 * @param content 段内容
 * @param perFileLimit 单段字符限制
 * @param totalInjectedChars 当前已注入的总字符数
 * @param totalLimit 总字符限制
 * @param nearLimitRatio 接近限制的比例阈值
 */
fun analyzeBootstrapSection(
    name: String,
    content: String,
    perFileLimit: Int = BOOTSTRAP_PER_FILE_MAX_CHARS,
    totalInjectedChars: Int = 0,
    totalLimit: Int = BOOTSTRAP_TOTAL_MAX_CHARS,
    nearLimitRatio: Double = BOOTSTRAP_NEAR_LIMIT_RATIO
): BootstrapAnalyzedSection {
    val rawChars = content.length
    val injectedChars = minOf(rawChars, perFileLimit, totalLimit - totalInjectedChars).coerceAtLeast(0)
    val truncated = rawChars > injectedChars

    val causes = mutableListOf<BootstrapTruncationCause>()
    if (rawChars > perFileLimit) causes.add(BootstrapTruncationCause.PER_FILE_LIMIT)
    if (totalInjectedChars + rawChars > totalLimit) causes.add(BootstrapTruncationCause.TOTAL_LIMIT)

    val nearLimit = injectedChars.toDouble() / perFileLimit >= nearLimitRatio ||
        (totalInjectedChars + injectedChars).toDouble() / totalLimit >= nearLimitRatio

    return BootstrapAnalyzedSection(
        name = name,
        rawChars = rawChars,
        injectedChars = injectedChars,
        truncated = truncated,
        nearLimit = nearLimit,
        causes = causes
    )
}

/**
 * 分析完整 bootstrap 预算。
 * OpenClaw: analyzeBootstrapBudget
 *
 * @param sections 段名到内容的映射
 * @param perFileLimit 单段字符限制
 * @param totalLimit 总字符限制
 * @param nearLimitRatio 接近限制的比例阈值
 */
fun analyzeBootstrapBudget(
    sections: Map<String, String>,
    perFileLimit: Int = BOOTSTRAP_PER_FILE_MAX_CHARS,
    totalLimit: Int = BOOTSTRAP_TOTAL_MAX_CHARS,
    nearLimitRatio: Double = BOOTSTRAP_NEAR_LIMIT_RATIO
): BootstrapBudgetAnalysis {
    val analyzed = mutableListOf<BootstrapAnalyzedSection>()
    var totalRawChars = 0
    var totalInjectedChars = 0

    for ((name, content) in sections) {
        val section = analyzeBootstrapSection(
            name = name,
            content = content,
            perFileLimit = perFileLimit,
            totalInjectedChars = totalInjectedChars,
            totalLimit = totalLimit,
            nearLimitRatio = nearLimitRatio
        )
        analyzed.add(section)
        totalRawChars += section.rawChars
        totalInjectedChars += section.injectedChars
    }

    return BootstrapBudgetAnalysis(
        sections = analyzed,
        truncatedSections = analyzed.filter { it.truncated },
        nearLimitSections = analyzed.filter { it.nearLimit },
        totalNearLimit = totalInjectedChars.toDouble() / totalLimit >= nearLimitRatio,
        hasTruncation = analyzed.any { it.truncated },
        totalRawChars = totalRawChars,
        totalInjectedChars = totalInjectedChars,
        totalTruncatedChars = totalRawChars - totalInjectedChars,
        bootstrapMaxChars = perFileLimit,
        bootstrapTotalMaxChars = totalLimit,
        nearLimitRatio = nearLimitRatio
    )
}

/**
 * 生成预算警告消息。
 * OpenClaw: buildBootstrapBudgetWarning
 *
 * @param analysis 预算分析结果
 * @param maxWarningFiles 最多显示的截断文件数
 * @return 警告消息，如果没有截断则返回 null
 */
fun buildBootstrapBudgetWarning(
    analysis: BootstrapBudgetAnalysis,
    maxWarningFiles: Int = BOOTSTRAP_PROMPT_WARNING_MAX_FILES
): String? {
    if (!analysis.hasTruncation && !analysis.totalNearLimit) return null

    val lines = mutableListOf<String>()

    if (analysis.hasTruncation) {
        lines.add("⚠️ Some system prompt content was truncated due to size limits:")
        for (section in analysis.truncatedSections.take(maxWarningFiles)) {
            val truncatedChars = section.rawChars - section.injectedChars
            lines.add("  - ${section.name}: ${section.rawChars} chars → ${section.injectedChars} chars (-$truncatedChars)")
        }
        if (analysis.truncatedSections.size > maxWarningFiles) {
            lines.add("  ... and ${analysis.truncatedSections.size - maxWarningFiles} more")
        }
    }

    if (analysis.totalNearLimit) {
        val pct = (analysis.totalInjectedChars.toDouble() / analysis.bootstrapTotalMaxChars * 100).toInt()
        lines.add("📊 Total bootstrap usage: ${analysis.totalInjectedChars}/${analysis.bootstrapTotalMaxChars} chars ($pct%)")
    }

    return lines.joinToString("\n")
}

/**
 * 生成警告签名（用于去重）。
 * OpenClaw: generateWarningSignature
 */
fun generateBootstrapWarningSignature(analysis: BootstrapBudgetAnalysis): String {
    val parts = analysis.truncatedSections.map { "${it.name}:${it.rawChars}:${it.injectedChars}" }
    return parts.joinToString("|").hashCode().toString()
}
