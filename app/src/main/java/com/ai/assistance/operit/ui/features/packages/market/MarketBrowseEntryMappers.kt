package com.ai.assistance.operit.ui.features.packages.market

import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.mcp.InstallProgress

val ArtifactMarketBrowseConfig =
    MarketBrowseSectionConfig(
        searchPlaceholderRes = R.string.artifact_market_search_placeholder,
        headerTitleRes = R.string.available_artifacts_market,
        emptySearchTitleRes = R.string.no_matching_artifacts_found,
        emptyDefaultTitleRes = R.string.no_artifacts_available
    )

val SkillMarketBrowseConfig =
    MarketBrowseSectionConfig(
        searchPlaceholderRes = R.string.skill_market_search_placeholder,
        headerTitleRes = R.string.available_skills_market,
        emptySearchTitleRes = R.string.no_matching_skills_found,
        emptyDefaultTitleRes = R.string.no_skills_available
    )

val McpMarketBrowseConfig =
    MarketBrowseSectionConfig(
        searchPlaceholderRes = R.string.mcp_market_search_hint,
        headerTitleRes = R.string.available_mcp_plugins,
        emptySearchTitleRes = R.string.no_matching_plugins_found,
        emptyDefaultTitleRes = R.string.no_mcp_plugins_available
    )

@Composable
fun rememberArtifactMarketBrowseEntry(
    item: ArtifactMarketItem,
    marketStats: Map<String, MarketEntryStats>,
    installingIds: Set<String>,
    installedArtifactIds: Set<String>,
    isCompatible: Boolean,
    compatibilityLabel: String,
    onViewDetails: (GitHubIssue) -> Unit,
    onInstallRequest: (ArtifactMarketItem) -> Unit
): MarketBrowseEntry {
    val metadata = item.metadata
    val entryId = remember(metadata) { resolveArtifactMarketEntryId(metadata) }
    val artifactId = metadata.normalizedId.ifBlank { entryId }
    val isInstalling = artifactId in installingIds
    val isInstalled = artifactId in installedArtifactIds
    val artifactTypeLabel =
        when (PublishArtifactType.fromWireValue(metadata.type)) {
            PublishArtifactType.SCRIPT -> stringResource(R.string.artifact_type_script)
            PublishArtifactType.PACKAGE -> stringResource(R.string.artifact_type_package)
            null -> metadata.type
        }

    return MarketBrowseEntry(
        model =
            MarketBrowseCardModel(
                title = metadata.displayName.ifBlank { item.issue.title },
                description = metadata.description,
                ownerUsername = metadata.publisherLogin,
                publisherAvatarUrl = item.issue.user.avatarUrl,
                thumbsUpCount = item.issue.reactions?.thumbs_up ?: 0,
                heartCount = item.issue.reactions?.heart ?: 0,
                downloads = marketStats[entryId]?.downloads ?: 0,
                chips =
                    listOf(
                        MarketBrowseChip(
                            label = artifactTypeLabel,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        MarketBrowseChip(
                            label =
                                stringResource(
                                    R.string.supported_app_versions_short,
                                    compatibilityLabel
                                ),
                            containerColor =
                                if (isCompatible) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                            contentColor =
                                if (isCompatible) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                        )
                    ),
                actionState =
                    when {
                        isInstalled -> MarketBrowseActionState.Installed
                        isInstalling -> MarketBrowseActionState.Installing()
                        item.issue.state == "open" -> MarketBrowseActionState.Available
                        else -> MarketBrowseActionState.Unavailable(MarketUnavailableKind.Warning)
                    }
            ),
        onViewDetails = { onViewDetails(item.issue) },
        onInstall = { onInstallRequest(item) }
    )
}

@Composable
fun rememberSkillMarketBrowseEntry(
    item: SkillMarketBrowseItem,
    marketStats: Map<String, MarketEntryStats>,
    installingSkills: Set<String>,
    installedSkillRepoUrls: Set<String>,
    installedSkillNames: Set<String>,
    onViewDetails: (GitHubIssue) -> Unit,
    onInstall: (SkillMarketBrowseItem) -> Unit
): MarketBrowseEntry {
    val context = LocalContext.current
    val repoUrl = item.repositoryUrl
    val entryId = item.entryId
    val isInstalling = repoUrl.isNotBlank() && repoUrl in installingSkills
    val isInstalled =
        (repoUrl.isNotBlank() && repoUrl in installedSkillRepoUrls) ||
            item.issue.title in installedSkillNames

    return MarketBrowseEntry(
        model =
            MarketBrowseCardModel(
                title = item.title,
                description = truncateMarketBrowseDescription(item.description),
                ownerUsername = item.ownerUsername,
                publisherAvatarUrl = item.publisherAvatarUrl,
                thumbsUpCount = item.issue.reactions?.thumbs_up ?: 0,
                heartCount = item.issue.reactions?.heart ?: 0,
                downloads = marketStats[entryId]?.downloads ?: 0,
                actionState =
                    when {
                        isInstalled -> MarketBrowseActionState.Installed
                        isInstalling -> MarketBrowseActionState.Installing()
                        item.issue.state == "open" -> MarketBrowseActionState.Available
                        else -> MarketBrowseActionState.Unavailable(MarketUnavailableKind.Info)
                    }
            ),
        onViewDetails = { onViewDetails(item.issue) },
        onInstall = {
            if (repoUrl.isBlank()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.skill_repo_url_not_found_cannot_install),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onInstall(item)
            }
        }
    )
}

@Composable
fun rememberMcpMarketBrowseEntry(
    item: McpMarketBrowseItem,
    marketStats: Map<String, MarketEntryStats>,
    installingPlugins: Set<String>,
    installProgress: Map<String, InstallProgress>,
    installedPluginIds: Set<String>,
    onViewDetails: (GitHubIssue) -> Unit,
    onInstall: (McpMarketBrowseItem) -> Unit
): MarketBrowseEntry {
    val pluginId = item.pluginId
    val entryId = item.entryId
    val isInstalling = pluginId in installingPlugins
    val isInstalled = pluginId in installedPluginIds
    val progress =
        when (val currentProgress = installProgress[pluginId]) {
            is InstallProgress.Downloading -> {
                currentProgress.progress.takeIf { it in 0..100 }?.div(100f)
            }

            else -> null
        }

    return MarketBrowseEntry(
        model =
            MarketBrowseCardModel(
                title = item.title,
                description = truncateMarketBrowseDescription(item.description),
                ownerUsername = item.ownerUsername,
                publisherAvatarUrl = item.publisherAvatarUrl,
                thumbsUpCount = item.issue.reactions?.thumbs_up ?: 0,
                heartCount = item.issue.reactions?.heart ?: 0,
                downloads = marketStats[entryId]?.downloads ?: 0,
                actionState =
                    when {
                        isInstalled -> MarketBrowseActionState.Installed
                        isInstalling -> MarketBrowseActionState.Installing(progress)
                        item.issue.state == "open" -> MarketBrowseActionState.Available
                        else -> MarketBrowseActionState.Unavailable(MarketUnavailableKind.Info)
                    }
            ),
        onViewDetails = { onViewDetails(item.issue) },
        onInstall = { onInstall(item) }
    )
}

private fun truncateMarketBrowseDescription(
    description: String,
    maxLength: Int = 100
): String {
    return if (description.length > maxLength) {
        description.take(maxLength) + "..."
    } else {
        description
    }
}
