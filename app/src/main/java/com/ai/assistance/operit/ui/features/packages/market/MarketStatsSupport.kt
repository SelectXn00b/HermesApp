package com.ai.assistance.operit.ui.features.packages.market

import androidx.annotation.StringRes
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.MarketRankIssueEntryResponse
import com.ai.assistance.operit.data.api.MarketStatsEntryResponse
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import com.ai.assistance.operit.ui.features.packages.utils.SkillIssueParser
import java.net.URI

enum class MarketStatsType(val wireValue: String) {
    SCRIPT("script"),
    PACKAGE("package"),
    SKILL("skill"),
    MCP("mcp")
}

enum class MarketSortOption(
    @StringRes val labelRes: Int
) {
    UPDATED(R.string.market_sort_updated),
    DOWNLOADS(R.string.market_sort_downloads),
    LIKES(R.string.market_sort_likes)
}

data class MarketEntryStats(
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

fun MarketStatsEntryResponse.toMarketEntryStats(): MarketEntryStats {
    return MarketEntryStats(
        downloads = downloads,
        lastDownloadAt = lastDownloadAt,
        updatedAt = updatedAt
    )
}

fun MarketRankIssueEntryResponse.toMarketEntryStats(): MarketEntryStats {
    return MarketEntryStats(
        downloads = downloads,
        lastDownloadAt = lastDownloadAt,
        updatedAt = statsUpdatedAt ?: updatedAt
    )
}

fun MarketSortOption.toRankMetric(): String {
    return when (this) {
        MarketSortOption.UPDATED -> "updated"
        MarketSortOption.DOWNLOADS -> "downloads"
        MarketSortOption.LIKES -> "likes"
    }
}

fun PublishArtifactType.toMarketStatsType(): MarketStatsType {
    return when (this) {
        PublishArtifactType.SCRIPT -> MarketStatsType.SCRIPT
        PublishArtifactType.PACKAGE -> MarketStatsType.PACKAGE
    }
}

fun resolveArtifactMarketStatsType(metadata: ArtifactMarketMetadata): MarketStatsType? {
    return PublishArtifactType.fromWireValue(metadata.type)?.toMarketStatsType()
}

fun resolveArtifactMarketEntryId(metadata: ArtifactMarketMetadata): String {
    return metadata.normalizedId.ifBlank {
        normalizeMarketArtifactId(metadata.displayName.ifBlank { metadata.assetName })
    }
}

fun resolveSkillMarketEntryId(issue: GitHubIssue): String {
    val skillInfo = SkillIssueParser.parseSkillInfo(issue)
    return resolveMarketEntryId(
        preferredSource = skillInfo.repositoryUrl,
        fallback = issue.title
    )
}

fun resolveMcpMarketEntryId(issue: GitHubIssue): String {
    val pluginInfo = MCPPluginParser.parsePluginInfo(issue)
    return resolveMarketEntryId(
        preferredSource = pluginInfo.repositoryUrl,
        fallback = issue.title
    )
}

fun resolveMarketDownloadTarget(
    preferredUrl: String?,
    fallbackUrl: String
): String {
    return preferredUrl?.trim()?.takeIf { it.isNotBlank() } ?: fallbackUrl
}

fun <T> sortMarketEntries(
    items: List<T>,
    sortOption: MarketSortOption,
    statsById: Map<String, MarketEntryStats>,
    idSelector: (T) -> String,
    updatedAtSelector: (T) -> String,
    titleSelector: (T) -> String,
    likesSelector: (T) -> Int
): List<T> {
    return when (sortOption) {
        MarketSortOption.UPDATED ->
            items.sortedWith(
                compareByDescending<T> { updatedAtSelector(it) }
                    .thenBy { titleSelector(it).lowercase() }
            )

        MarketSortOption.DOWNLOADS ->
            items.sortedWith(
                compareByDescending<T> { statsById[idSelector(it)]?.downloads ?: 0 }
                    .thenByDescending { likesSelector(it) }
                    .thenByDescending { updatedAtSelector(it) }
                    .thenBy { titleSelector(it).lowercase() }
            )

        MarketSortOption.LIKES ->
            items.sortedWith(
                compareByDescending<T> { likesSelector(it) }
                    .thenByDescending { updatedAtSelector(it) }
                    .thenBy { titleSelector(it).lowercase() }
            )
    }
}

private fun resolveMarketEntryId(
    preferredSource: String?,
    fallback: String
): String {
    val source =
        preferredSource
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::canonicalizeMarketSource)
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    return normalizeMarketArtifactId(source)
}

private fun canonicalizeMarketSource(raw: String): String {
    return runCatching {
        val uri = URI(raw.trim())
        val host = uri.host?.removePrefix("www.").orEmpty()
        val path =
            uri.path
                ?.removeSuffix(".git")
                ?.trim('/')
                .orEmpty()
        listOf(host, path).filter { it.isNotBlank() }.joinToString("/")
    }.getOrElse {
        raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix(".git")
            .trim('/')
    }
}
