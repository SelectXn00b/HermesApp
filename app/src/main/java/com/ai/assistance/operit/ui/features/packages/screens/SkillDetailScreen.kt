package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailAction
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
import com.ai.assistance.operit.ui.features.packages.market.resolveSkillMarketEntryId
import com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel.SkillMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.SkillIssueParser

@Composable
fun SkillDetailScreen(
    issue: GitHubIssue,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }
    val viewModel: SkillMarketViewModel =
        viewModel(factory = SkillMarketViewModel.Factory(context.applicationContext, skillRepository))

    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)

    val installingSkills by viewModel.installingSkills.collectAsState()
    val installedSkillNames by viewModel.installedSkillNames.collectAsState()
    val installedSkillRepoUrls by viewModel.installedSkillRepoUrls.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val commentsMap by viewModel.issueComments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()
    val reactionsMap by viewModel.issueReactions.collectAsState()
    val isLoadingReactions by viewModel.isLoadingReactions.collectAsState()
    val isReacting by viewModel.isReacting.collectAsState()
    val userAvatarCache by viewModel.userAvatarCache.collectAsState()
    val repositoryCache by viewModel.repositoryCache.collectAsState()
    val marketStats by viewModel.marketStats.collectAsState()

    val skillInfo = remember(issue) { SkillIssueParser.parseSkillInfo(issue) }
    val repoUrl = skillInfo.repositoryUrl
    val entryId = remember(issue) { resolveSkillMarketEntryId(issue) }
    val currentComments = commentsMap[issue.number].orEmpty()
    val currentReactions = reactionsMap[issue.number].orEmpty()
    val repositoryInfo = repositoryCache[repoUrl]
    val downloads = marketStats[entryId]?.downloads ?: 0
    val likes = if (currentReactions.isNotEmpty()) currentReactions.count { it.content == "+1" } else issue.reactions?.thumbs_up ?: 0
    val favorites = if (currentReactions.isNotEmpty()) currentReactions.count { it.content == "heart" } else issue.reactions?.heart ?: 0
    val authorAvatarUrl = repositoryInfo?.owner?.avatarUrl ?: userAvatarCache[skillInfo.repositoryOwner]
    val currentUserLogin = currentUser?.login
    val hasThumbsUp = currentUserLogin != null && currentReactions.any { it.content == "+1" && it.user.login == currentUserLogin }
    val hasHeart = currentUserLogin != null && currentReactions.any { it.content == "heart" && it.user.login == currentUserLogin }
    val isInstalling = repoUrl.isNotBlank() && repoUrl in installingSkills
    val isInstalled = (repoUrl.isNotBlank() && repoUrl in installedSkillRepoUrls) || issue.title in installedSkillNames

    var showCommentDialog by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(issue.number, entryId) {
        viewModel.refreshInstalledSkills()
        viewModel.loadIssueComments(issue.number)
        viewModel.loadIssueReactions(issue.number)
        viewModel.ensureMarketStatsLoaded(entryId)
        if (repoUrl.isNotBlank()) {
            viewModel.fetchRepositoryInfo(repoUrl)
        }
        if (skillInfo.repositoryOwner.isNotBlank()) {
            viewModel.fetchUserAvatar(skillInfo.repositoryOwner)
        }
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val header =
        UnifiedMarketDetailHeader(
            title = issue.title,
            fallbackAvatarText = marketDetailInitial(issue.title),
            participants =
                listOf(
                    UnifiedMarketDetailParticipant(
                        roleLabel = stringResource(R.string.market_detail_author_role),
                        name = skillInfo.repositoryOwner.ifBlank { stringResource(R.string.mcp_plugin_unknown_author) },
                        avatarUrl = authorAvatarUrl,
                        fallbackAvatarText = marketDetailInitial(skillInfo.repositoryOwner.ifBlank { issue.title })
                    ),
                    UnifiedMarketDetailParticipant(
                        roleLabel = stringResource(R.string.market_detail_sharer_role),
                        name = issue.user.login,
                        avatarUrl = issue.user.avatarUrl,
                        fallbackAvatarText = marketDetailInitial(issue.user.login)
                    )
                ),
            badges = buildSkillBadges(skillInfo),
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
            if (skillInfo.description.isNotBlank()) {
                add(
                    UnifiedMarketDetailSection(
                        title = stringResource(R.string.market_detail_about_title),
                        body = skillInfo.description,
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
                    value = "Skill",
                    icon = Icons.Default.Info
                )
            )
            if (skillInfo.version.isNotBlank()) {
                add(
                    UnifiedMarketDetailInfoRow(
                        label = stringResource(R.string.version_label),
                        value = skillInfo.version,
                        icon = Icons.Default.Update
                    )
                )
            }
            if (skillInfo.category.isNotBlank()) {
                add(
                    UnifiedMarketDetailInfoRow(
                        label = stringResource(R.string.market_detail_category_label),
                        value = skillInfo.category,
                        icon = Icons.Default.Info
                    )
                )
            }
            if (repoUrl.isNotBlank()) {
                add(
                    UnifiedMarketDetailInfoRow(
                        label = stringResource(R.string.mcp_plugin_repository),
                        value = repoUrl,
                        icon = Icons.Default.Code
                    )
                )
            }
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
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        isSelected = hasThumbsUp,
                        enabled = currentUser != null,
                        onClick = {
                            if (!hasThumbsUp) {
                                viewModel.addReactionToIssue(issue.number, "+1")
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
                                viewModel.addReactionToIssue(issue.number, "heart")
                            }
                        }
                    )
                )
        )

    val commentsState =
        UnifiedMarketDetailCommentsState(
            title = stringResource(R.string.mcp_plugin_comments, currentComments.size),
            comments = currentComments,
            isLoading = issue.number in isLoadingComments,
            isPosting = issue.number in isPostingComment,
            canPost = currentUser != null,
            postHint = if (currentUser == null) stringResource(R.string.mcp_plugin_login_required) else null,
            onRefresh = { viewModel.loadIssueComments(issue.number) },
            onRequestPost = { showCommentDialog = true }
        )

    UnifiedMarketDetailScreen(
        onNavigateBack = onNavigateBack,
        header = header,
        primaryAction =
            UnifiedMarketDetailAction(
                label =
                    when {
                        isInstalled -> stringResource(R.string.installed)
                        isInstalling -> stringResource(R.string.installing_progress)
                        else -> stringResource(R.string.install)
                    },
                onClick = {
                    if (repoUrl.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.skill_repo_url_not_found), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.installSkillFromIssue(issue)
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
            if (repoUrl.isNotBlank()) {
                UnifiedMarketDetailAction(
                    label = stringResource(R.string.mcp_plugin_repository),
                    onClick = { openExternalUrl(context, repoUrl) },
                    icon = Icons.Default.Code
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
                    viewModel.postIssueComment(issue.number, commentText)
                    showCommentDialog = false
                    commentText = ""
                }
            },
            isPosting = issue.number in isPostingComment
        )
    }
}

private fun buildSkillBadges(
    skillInfo: SkillIssueParser.ParsedSkillInfo
): List<String> {
    return buildList {
        add("skill")
        if (skillInfo.category.isNotBlank()) {
            add(skillInfo.category)
        }
        if (skillInfo.version.isNotBlank()) {
            add("版本 ${normalizeDetailVersionBadge(skillInfo.version)}")
        }
    }
}

private fun normalizeDetailVersionBadge(value: String): String {
    val trimmed = value.trim()
    return trimmed.removePrefix("v").removePrefix("V").ifBlank { trimmed }
}

private fun openExternalUrl(
    context: Context,
    url: String
) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
