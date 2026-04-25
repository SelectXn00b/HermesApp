package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewStatusChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.packages.market.SKILL_MARKET_VISIBILITY_LABEL
import com.ai.assistance.operit.ui.features.packages.market.hasAnyLabelName
import com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel.SkillMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.SkillIssueParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (GitHubIssue) -> Unit,
    onNavigateToPublish: () -> Unit,
    onNavigateToDetail: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current

    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }
    val viewModel: SkillMarketViewModel = viewModel(
        factory = SkillMarketViewModel.Factory(context.applicationContext, skillRepository)
    )

    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val userPublishedSkills by viewModel.userPublishedSkills.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<GitHubIssue?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.loadUserPublishedSkills()
        }
    }

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = isLoading,
        errorMessage = errorMessage,
        isEmpty = userPublishedSkills.isEmpty(),
        onLogin = { showGitHubLogin = true },
        onPublish = onNavigateToPublish,
        publishContentDescription = stringResource(R.string.publish_new_skill),
        loginDescription = stringResource(R.string.need_login_github_manage_skills),
        loadingMessage = stringResource(R.string.loading_your_plugins),
        emptyIcon = Icons.Default.Extension,
        emptyTitle = stringResource(R.string.no_published_skills_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_skill),
        emptyActionLabel = stringResource(R.string.publish_new_skill)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(userPublishedSkills, key = { it.id }) { issue ->
                val info = remember(issue) { SkillIssueParser.parseSkillInfo(issue) }
                val isApproved = issue.hasAnyLabelName(setOf(SKILL_MARKET_VISIBILITY_LABEL))

                MarketManageItemCard(
                    title = issue.title,
                    description = info.description,
                    issueNumber = issue.number,
                    isOpen = issue.state == "open",
                    onClick = { onNavigateToDetail(issue) },
                    supportingContent = {
                        Spacer(modifier = Modifier.height(8.dp))
                        MarketManageReviewStatusChip(isApproved = isApproved)
                    },
                    actions = {
                        MarketManageSecondaryActionButton(
                            label = stringResource(R.string.edit),
                            icon = Icons.Default.Edit,
                            onClick = { onNavigateToEdit(issue) }
                        )
                        MarketManageDangerActionButton(
                            label = stringResource(R.string.remove),
                            icon = Icons.Default.Delete,
                            onClick = { showDeleteDialog = issue }
                        )
                    }
                )
            }
        }
    }

    val deletingIssue = showDeleteDialog
    if (deletingIssue != null) {
        MarketManageDeleteDialog(
            titleText = stringResource(R.string.confirm_delete_action),
            text = stringResource(R.string.confirm_remove_skill_from_market, deletingIssue.title),
            confirmText = stringResource(R.string.confirm),
            onConfirm = {
                showDeleteDialog = null
                viewModel.removeSkillFromMarket(deletingIssue.number)
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
