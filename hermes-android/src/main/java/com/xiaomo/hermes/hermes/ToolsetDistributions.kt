package com.xiaomo.hermes.hermes

import kotlin.random.Random

/**
 * Toolset Distributions Module
 * 1:1 对齐 hermes-agent/toolset_distributions.py
 *
 * 工具集分布：定义工具集的选择概率。
 */

// ── Distribution 定义 ─────────────────────────────────────────────────────

data class DistributionDefinition(
    val description: String,
    val toolsets: Map<String, Int>, // toolset_name -> probability (%)
)

val DISTRIBUTIONS: Map<String, DistributionDefinition> = mapOf(
    "default" to DistributionDefinition(
        description = "All available tools, all the time",
        toolsets = mapOf(
            "web" to 100,
            "vision" to 100,
            "image_gen" to 100,
            "terminal" to 100,
            "file" to 100,
            "moa" to 100,
            "browser" to 100)),
    "image_gen" to DistributionDefinition(
        description = "Heavy focus on image generation with vision and web support",
        toolsets = mapOf(
            "image_gen" to 90,
            "vision" to 90,
            "web" to 55,
            "terminal" to 45,
            "moa" to 10)),
    "research" to DistributionDefinition(
        description = "Web research with vision analysis and reasoning",
        toolsets = mapOf(
            "web" to 90,
            "browser" to 70,
            "vision" to 50,
            "moa" to 40,
            "terminal" to 10)),
    "science" to DistributionDefinition(
        description = "Scientific research with web, terminal, file, and browser capabilities",
        toolsets = mapOf(
            "web" to 94,
            "terminal" to 94,
            "file" to 94,
            "vision" to 65,
            "browser" to 50,
            "image_gen" to 15,
            "moa" to 10)),
    "development" to DistributionDefinition(
        description = "Terminal, file tools, and reasoning with occasional web lookup",
        toolsets = mapOf(
            "terminal" to 80,
            "file" to 80,
            "moa" to 60,
            "web" to 30,
            "vision" to 10)),
    "safe" to DistributionDefinition(
        description = "All tools except terminal for safety",
        toolsets = mapOf(
            "web" to 80,
            "browser" to 70,
            "vision" to 60,
            "image_gen" to 60,
            "moa" to 50)),
    "balanced" to DistributionDefinition(
        description = "Equal probability of all toolsets",
        toolsets = mapOf(
            "web" to 50,
            "vision" to 50,
            "image_gen" to 50,
            "terminal" to 50,
            "file" to 50,
            "moa" to 50,
            "browser" to 50)),
    "minimal" to DistributionDefinition(
        description = "Only web tools for basic research",
        toolsets = mapOf("web" to 100)),
    "terminal_only" to DistributionDefinition(
        description = "Terminal and file tools for code execution tasks",
        toolsets = mapOf("terminal" to 100, "file" to 100)),
    "terminal_web" to DistributionDefinition(
        description = "Terminal and file tools with web search for documentation lookup",
        toolsets = mapOf("terminal" to 100, "file" to 100, "web" to 100)),
    "creative" to DistributionDefinition(
        description = "Image generation and vision analysis focus",
        toolsets = mapOf("image_gen" to 90, "vision" to 90, "web" to 30)),
    "reasoning" to DistributionDefinition(
        description = "Heavy mixture of agents usage with minimal other tools",
        toolsets = mapOf("moa" to 90, "web" to 30, "terminal" to 20)),
    "browser_use" to DistributionDefinition(
        description = "Full browser-based web interaction with search, vision, and page control",
        toolsets = mapOf("browser" to 100, "web" to 80, "vision" to 70)),
    "browser_only" to DistributionDefinition(
        description = "Only browser automation tools for pure web interaction tasks",
        toolsets = mapOf("browser" to 100)),
    "browser_tasks" to DistributionDefinition(
        description = "Browser-focused distribution",
        toolsets = mapOf("browser" to 97, "vision" to 12, "terminal" to 15)),
    "terminal_tasks" to DistributionDefinition(
        description = "Terminal-focused distribution with high terminal/file availability",
        toolsets = mapOf(
            "terminal" to 97,
            "file" to 97,
            "web" to 97,
            "browser" to 75,
            "vision" to 50,
            "image_gen" to 10)),
    "mixed_tasks" to DistributionDefinition(
        description = "Mixed distribution with high browser, terminal, and file availability",
        toolsets = mapOf(
            "browser" to 92,
            "terminal" to 92,
            "file" to 92,
            "web" to 35,
            "vision" to 15,
            "image_gen" to 15)))

// ── 公开 API ──────────────────────────────────────────────────────────────

/**
 * 获取分布定义
 * Python: get_distribution(name)
 */
fun getDistribution(name: String): DistributionDefinition? {
    return DISTRIBUTIONS[name]
}

/**
 * 列出所有分布
 * Python: list_distributions()
 */
fun listDistributions(): Map<String, DistributionDefinition> {
    return DISTRIBUTIONS.toMap()
}

/**
 * 从分布中采样工具集
 * Python: sample_toolsets_from_distribution(distribution_name)
 */
fun sampleToolsetsFromDistribution(distributionName: String): List<String> {
    val dist = getDistribution(distributionName)
        ?: throw IllegalArgumentException("Unknown distribution: $distributionName")

    val selected = mutableListOf<String>()

    for ((toolsetName, probability) in dist.toolsets) {
        if (!validateToolset(toolsetName)) {
            getLogger("toolset_distributions").warning(
                "Toolset '$toolsetName' in distribution '$distributionName' is not valid"
            )
            continue
        }

        if (Random.nextDouble() * 100 < probability) {
            selected.add(toolsetName)
        }
    }

    // 如果没有选中任何工具集，选概率最高的
    if (selected.isEmpty() && dist.toolsets.isNotEmpty()) {
        val highest = dist.toolsets.maxByOrNull { it.value }?.key
        if (highest != null && validateToolset(highest)) {
            selected.add(highest)
        }
    }

    return selected
}

/**
 * 验证分布
 * Python: validate_distribution(distribution_name)
 */
fun validateDistribution(distributionName: String): Boolean {
    return distributionName in DISTRIBUTIONS
}

/**
 * 打印分布信息（Android 版本返回字符串）
 * Python: print_distribution_info(distribution_name)
 */
fun formatDistributionInfo(distributionName: String): String {
    val dist = getDistribution(distributionName) ?: return "Unknown distribution: $distributionName"

    val sb = StringBuilder()
    sb.appendLine("Distribution: $distributionName")
    sb.appendLine("Description: ${dist.description}")
    sb.appendLine("Toolsets:")
    for ((toolset, prob) in dist.toolsets.toList().sortedByDescending { it.second }) {
        sb.appendLine("  - $toolset: $prob%")
    }
    return sb.toString()


}
