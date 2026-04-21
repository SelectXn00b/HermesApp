package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.packages.components.MarketManageLabelChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManagePrimaryActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewStatusChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.packages.market.ARTIFACT_MARKET_VISIBILITY_LABELS
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.hasAnyLabelName
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.ArtifactIssueParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (GitHubIssue) -> Unit,
    onNavigateToPublish: () -> Unit,
    onNavigateToDetail: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArtifactMarketViewModel =
        viewModel(
            key = "artifact-manage-all",
            factory = ArtifactMarketViewModel.Factory(context.applicationContext, ArtifactMarketScope.ALL)
        )

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val userPublishedArtifacts by viewModel.userPublishedArtifacts.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<GitHubIssue?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.loadUserPublishedArtifacts()
        }
    }

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = isLoading,
        errorMessage = errorMessage,
        isEmpty = userPublishedArtifacts.isEmpty(),
        onLogin = { showGitHubLogin = true },
        onPublish = onNavigateToPublish,
        publishContentDescription = stringResource(R.string.publish_new_artifact),
        loginDescription = stringResource(R.string.need_login_github_manage_artifacts),
        loadingMessage = stringResource(R.string.loading_your_artifacts),
        emptyIcon = Icons.Default.Store,
        emptyTitle = stringResource(R.string.no_published_artifacts_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_artifact),
        emptyActionLabel = stringResource(R.string.publish_new_artifact)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(userPublishedArtifacts, key = { it.id }) { issue ->
                val info = remember(issue) { ArtifactIssueParser.parseArtifactInfo(issue) }
                val isApproved = issue.hasAnyLabelName(ARTIFACT_MARKET_VISIBILITY_LABELS)
                MarketManageItemCard(
                    title = info.title,
                    description = info.description,
                    issueNumber = issue.number,
                    isOpen = issue.state == "open",
                    onClick = { onNavigateToDetail(issue) },
                    supportingContent = {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarketManageReviewStatusChip(isApproved = isApproved)
                            ArtifactTypeBadge(info)
                        }
                    },
                    actions = {
                        MarketManageSecondaryActionButton(
                            label = stringResource(R.string.edit),
                            icon = Icons.Default.Edit,
                            onClick = { onNavigateToEdit(issue) }
                        )
                        if (issue.state == "open") {
                            MarketManageDangerActionButton(
                                label = stringResource(R.string.remove),
                                icon = Icons.Default.Delete,
                                onClick = { showDeleteDialog = issue }
                            )
                        } else {
                            MarketManagePrimaryActionButton(
                                label = stringResource(R.string.republish),
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.reopenArtifactInMarket(issue, info.title) }
                            )
                        }
                    }
                )
            }
        }
    }

    showDeleteDialog?.let { issue ->
        val info = ArtifactIssueParser.parseArtifactInfo(issue)
        MarketManageDeleteDialog(
            text = stringResource(R.string.confirm_remove_artifact_from_market, info.title),
            onConfirm = {
                viewModel.removeArtifactFromMarket(issue, info.title)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showGitHubLogin) {
        GitHubLoginWebViewDialog(
            onDismissRequest = { showGitHubLogin = false }
        )
    }
}

@Composable
private fun ArtifactTypeBadge(info: ArtifactIssueParser.ParsedArtifactInfo) {
    val label =
        when (info.type) {
            PublishArtifactType.PACKAGE -> stringResource(R.string.artifact_type_package)
            PublishArtifactType.SCRIPT -> stringResource(R.string.artifact_type_script)
            null -> info.metadata?.type ?: "-"
        }

    MarketManageLabelChip(
        text = label,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
