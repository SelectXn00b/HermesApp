package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketItem
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailAction
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailBanner
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailCommentDialog
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailCommentsState
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailHeader
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailInfoRow
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailMetric
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailParticipant
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailReactionOption
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailReactionsState
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailScreen
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailSection
import com.ai.assistance.operit.ui.features.packages.market.formatMarketDetailCompactDate
import com.ai.assistance.operit.ui.features.packages.market.formatMarketDetailDate
import com.ai.assistance.operit.ui.features.packages.market.marketDetailInitial
import com.ai.assistance.operit.ui.features.packages.market.resolveArtifactMarketEntryId
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.ArtifactIssueParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactDetailScreen(
    issue: GitHubIssue,
    onNavigateBack: () -> Unit = {}
) {
    val info = remember(issue) { ArtifactIssueParser.parseArtifactInfo(issue) }
    val artifactType = info.type
    if (artifactType == null) {
        InvalidArtifactMetadataScreen()
        return
    }

    val context = LocalContext.current
    val viewModel: ArtifactMarketViewModel =
        viewModel(
            key = "artifact-detail-${artifactType.wireValue}",
            factory =
                ArtifactMarketViewModel.Factory(
                    context.applicationContext,
                    if (artifactType == PublishArtifactType.PACKAGE) {
                        ArtifactMarketScope.PACKAGE_ONLY
                    } else {
                        ArtifactMarketScope.SCRIPT_ONLY
                    }
                )
        )

    val currentUser by viewModel.currentUser.collectAsState()
    val comments by viewModel.issueComments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()
    val issueReactions by viewModel.issueReactions.collectAsState()
    val isLoadingReactions by viewModel.isLoadingReactions.collectAsState()
    val isReacting by viewModel.isReacting.collectAsState()
    val userAvatarCache by viewModel.userAvatarCache.collectAsState()
    val installedIds by viewModel.installedArtifactIds.collectAsState()
    val installingIds by viewModel.installingIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val marketStats by viewModel.marketStats.collectAsState()

    val entryId = remember(info.metadata) { info.metadata?.let(::resolveArtifactMarketEntryId).orEmpty() }
    val currentComments = comments[issue.number].orEmpty()
    val currentReactions = issueReactions[issue.number].orEmpty()
    val downloads = marketStats[entryId]?.downloads ?: 0
    val likes = if (currentReactions.isNotEmpty()) currentReactions.count { it.content == "+1" } else issue.reactions?.thumbs_up ?: 0
    val favorites = if (currentReactions.isNotEmpty()) currentReactions.count { it.content == "heart" } else issue.reactions?.heart ?: 0
    val currentUserLogin = currentUser?.login
    val hasThumbsUp = currentUserLogin != null && currentReactions.any { it.content == "+1" && it.user.login == currentUserLogin }
    val hasHeart = currentUserLogin != null && currentReactions.any { it.content == "heart" && it.user.login == currentUserLogin }
    val isInstalled = installedIds.contains(info.normalizedId)
    val isInstalling = installingIds.contains(info.normalizedId)
    val isCompatible = info.metadata?.let(viewModel::isCompatible) ?: true
    val publisherAvatarUrl = userAvatarCache[info.publisherLogin]

    var commentText by remember { mutableStateOf("") }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showCompatibilityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(issue.number, entryId) {
        viewModel.loadIssueComments(issue.number, artifactType)
        viewModel.loadIssueReactions(issue.number, artifactType)
        viewModel.refreshInstalledArtifacts()
        viewModel.ensureMarketStatsLoaded(entryId)
        if (info.publisherLogin.isNotBlank()) {
            viewModel.fetchUserAvatar(info.publisherLogin)
        }
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val primaryActionLabel =
        when {
            isInstalled -> stringResource(R.string.downloaded_already)
            isInstalling -> stringResource(R.string.downloading)
            artifactType == PublishArtifactType.SCRIPT -> stringResource(R.string.download_script)
            else -> stringResource(R.string.download_package)
        }

    val header =
        UnifiedMarketDetailHeader(
            title = info.title,
            fallbackAvatarText = marketDetailInitial(info.title),
            participants =
                listOf(
                    UnifiedMarketDetailParticipant(
                        roleLabel = stringResource(R.string.market_detail_publisher_role),
                        name = info.publisherLogin.ifBlank { "-" },
                        avatarUrl = publisherAvatarUrl,
                        fallbackAvatarText = marketDetailInitial(info.publisherLogin)
                    ),
                    UnifiedMarketDetailParticipant(
                        roleLabel = stringResource(R.string.market_detail_sharer_role),
                        name = issue.user.login,
                        avatarUrl = issue.user.avatarUrl,
                        fallbackAvatarText = marketDetailInitial(issue.user.login)
                    )
                ),
            badges =
                buildArtifactBadges(
                    info = info,
                    artifactType = artifactType,
                    supportedVersionLabel = info.metadata?.let(viewModel::supportedVersionLabel)
                ),
            metrics =
                listOf(
                    UnifiedMarketDetailMetric(
                        value = downloads.toString(),
                        label = stringResource(R.string.market_sort_downloads)
                    ),
                    UnifiedMarketDetailMetric(
                        value = likes.toString(),
                        label = stringResource(R.string.market_sort_likes)
                    ),
                    UnifiedMarketDetailMetric(
                        value = formatMarketDetailCompactDate(issue.created_at),
                        label = stringResource(R.string.market_detail_published_label)
                    )
                ),
            statusLabel =
                if (issue.state == "open") {
                    stringResource(R.string.market_detail_status_available)
                } else {
                    stringResource(R.string.market_detail_status_closed)
                }
        )

    val sections =
        buildList {
            if (info.description.isNotBlank()) {
                add(
                    UnifiedMarketDetailSection(
                        title = stringResource(R.string.market_detail_about_title),
                        body = info.description,
                        icon = Icons.Default.Info,
                        showTitle = false
                    )
                )
            }
        }

    val metadataRows =
        buildList {
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.type_label),
                    value =
                        when (artifactType) {
                            PublishArtifactType.PACKAGE -> stringResource(R.string.artifact_type_package)
                            PublishArtifactType.SCRIPT -> stringResource(R.string.artifact_type_script)
                        },
                    icon = Icons.Default.Tag
                )
            )
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.version_label),
                    value = info.version.ifBlank { "-" },
                    icon = Icons.Default.Update
                )
            )
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.supported_app_versions),
                    value = info.metadata?.let(viewModel::supportedVersionLabel) ?: "-",
                    icon = Icons.Default.Info
                )
            )
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.current_app_version_label),
                    value = viewModel.currentAppVersion,
                    icon = Icons.Default.Info
                )
            )
            addIfNotBlank(stringResource(R.string.asset_file_label), info.assetName, Icons.Default.Info)
            addIfNotBlank(stringResource(R.string.forge_repo_label), info.forgeRepo, Icons.Default.Info)
            addIfNotBlank(stringResource(R.string.release_tag_label), info.releaseTag, Icons.Default.Tag)
            addIfNotBlank(stringResource(R.string.sha256_label), info.sha256, Icons.Default.Info)
            addIfNotBlank(stringResource(R.string.source_file_label), info.sourceFileName, Icons.Default.Info)
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.market_detail_published_label),
                    value = formatMarketDetailDate(issue.created_at),
                    icon = Icons.Default.CalendarToday
                )
            )
            add(
                UnifiedMarketDetailInfoRow(
                    label = stringResource(R.string.updated_at_label),
                    value = formatMarketDetailDate(issue.updated_at),
                    icon = Icons.Default.Update
                )
            )
        }

    val reactionsState =
        UnifiedMarketDetailReactionsState(
            title = stringResource(R.string.mcp_plugin_community_feedback),
            helperText = if (currentUser == null) stringResource(R.string.mcp_plugin_login_required) else null,
            isLoading = issue.number in isLoadingReactions,
            isMutating = issue.number in isReacting,
            options =
                listOf(
                    UnifiedMarketDetailReactionOption(
                        label = stringResource(R.string.market_detail_like_action),
                        count = likes,
                        icon = Icons.Default.ThumbUp,
                        tint = MaterialTheme.colorScheme.primary,
                        isSelected = hasThumbsUp,
                        enabled = currentUser != null,
                        onClick = {
                            if (!hasThumbsUp) {
                                viewModel.addReactionToIssue(issue.number, artifactType, "+1")
                            }
                        }
                    ),
                    UnifiedMarketDetailReactionOption(
                        label = stringResource(R.string.market_detail_favorite_action),
                        count = favorites,
                        icon = Icons.Default.Favorite,
                        tint = Color(0xFFE91E63),
                        isSelected = hasHeart,
                        enabled = currentUser != null,
                        onClick = {
                            if (!hasHeart) {
                                viewModel.addReactionToIssue(issue.number, artifactType, "heart")
                            }
                        }
                    )
                )
        )

    val commentsState =
        UnifiedMarketDetailCommentsState(
            title = stringResource(R.string.comments_with_count, currentComments.size),
            comments = currentComments,
            isLoading = issue.number in isLoadingComments,
            isPosting = issue.number in isPostingComment,
            canPost = currentUser != null,
            postHint = if (currentUser == null) stringResource(R.string.mcp_plugin_login_required) else null,
            onRefresh = { viewModel.loadIssueComments(issue.number, artifactType) },
            onRequestPost = { showCommentDialog = true }
        )

    UnifiedMarketDetailScreen(
        onNavigateBack = onNavigateBack,
        header = header,
        primaryAction =
            UnifiedMarketDetailAction(
                label = primaryActionLabel,
                onClick = {
                    val metadata = info.metadata
                    if (metadata != null) {
                        if (viewModel.isCompatible(metadata)) {
                            viewModel.installArtifact(ArtifactMarketItem(issue = issue, metadata = metadata))
                        } else {
                            showCompatibilityDialog = true
                        }
                    }
                },
                enabled = issue.state == "open" && !isInstalled && !isInstalling,
                isLoading = isInstalling,
                icon =
                    when {
                        isInstalling -> null
                        isInstalled -> Icons.Default.Check
                        else -> Icons.Default.Download
                    }
            ),
        secondaryAction =
            if (info.downloadUrl.isNotBlank()) {
                UnifiedMarketDetailAction(
                    label = stringResource(R.string.open_asset),
                    onClick = { openExternalUrl(context, info.downloadUrl) },
                    icon = Icons.Default.Info
                )
            } else {
                null
            },
        banner =
            if (!isCompatible && info.metadata != null) {
                UnifiedMarketDetailBanner(
                    title = stringResource(R.string.unsupported_artifact_version_title),
                    message =
                        stringResource(
                            R.string.unsupported_artifact_version_message,
                            info.title,
                            viewModel.currentAppVersion,
                            viewModel.supportedVersionLabel(info.metadata)
                        ),
                    icon = Icons.Default.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                null
            },
        sections = sections,
        metadataTitle = stringResource(R.string.metadata_title),
        metadataRows = metadataRows,
        reactions = reactionsState,
        comments = commentsState
    )

    if (showCommentDialog) {
        UnifiedMarketDetailCommentDialog(
            commentText = commentText,
            onCommentTextChange = { commentText = it },
            onDismiss = {
                showCommentDialog = false
                commentText = ""
            },
            onPost = {
                if (commentText.isNotBlank()) {
                    viewModel.postIssueComment(issue.number, artifactType, commentText)
                    showCommentDialog = false
                    commentText = ""
                }
            },
            isPosting = issue.number in isPostingComment
        )
    }

    if (showCompatibilityDialog && info.metadata != null) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            title = { Text(stringResource(R.string.unsupported_artifact_version_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.unsupported_artifact_version_message,
                        info.title,
                        viewModel.currentAppVersion,
                        viewModel.supportedVersionLabel(info.metadata)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.installArtifact(ArtifactMarketItem(issue = issue, metadata = info.metadata))
                        showCompatibilityDialog = false
                    }
                ) {
                    Text(stringResource(R.string.continue_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompatibilityDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InvalidArtifactMetadataScreen() {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.invalid_artifact_metadata),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildArtifactBadges(
    info: ArtifactIssueParser.ParsedArtifactInfo,
    artifactType: PublishArtifactType,
    supportedVersionLabel: String?
): List<String> {
    return buildList {
        add(
            when (artifactType) {
                PublishArtifactType.PACKAGE -> "toolpkg"
                PublishArtifactType.SCRIPT -> "js"
            }
        )
        supportedVersionLabel
            ?.takeIf { it.isNotBlank() && !it.equals("Any", ignoreCase = true) }
            ?.let { add("适配 $it") }
        if (info.version.isNotBlank()) {
            add("版本 ${normalizeDetailVersionBadge(info.version)}")
        }
    }
}

private fun normalizeDetailVersionBadge(value: String): String {
    val trimmed = value.trim()
    return trimmed.removePrefix("v").removePrefix("V").ifBlank { trimmed }
}

private fun MutableList<UnifiedMarketDetailInfoRow>.addIfNotBlank(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    if (value.isNotBlank()) {
        add(UnifiedMarketDetailInfoRow(label = label, value = value, icon = icon))
    }
}

private fun openExternalUrl(
    context: android.content.Context,
    url: String
) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
