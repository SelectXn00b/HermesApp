package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubLabel

const val MCP_MARKET_VISIBILITY_LABEL = "mcp-plugin"
const val SKILL_MARKET_VISIBILITY_LABEL = "skill-plugin"

val ARTIFACT_MARKET_VISIBILITY_LABELS: Set<String> =
    PublishArtifactType.entries.map { it.marketLabel }.toSet()

fun GitHubIssue.hasAnyLabelName(labelNames: Set<String>): Boolean {
    return labels.any { label ->
        labelNames.any { expected -> expected.equals(label.name, ignoreCase = true) }
    }
}

fun List<GitHubLabel>.withoutLabelNames(labelNames: Set<String>): List<GitHubLabel> {
    return filterNot { label ->
        labelNames.any { excluded -> excluded.equals(label.name, ignoreCase = true) }
    }
}
