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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
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
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageGitHubLabelChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.packages.components.MarketManagePrimaryActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewStatusChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.packages.market.MCP_MARKET_VISIBILITY_LABEL
import com.ai.assistance.operit.ui.features.packages.market.hasAnyLabelName
import com.ai.assistance.operit.ui.features.packages.market.withoutLabelNames
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (GitHubIssue) -> Unit,
    onNavigateToPublish: () -> Unit,
    viewModel: MCPMarketViewModel = viewModel()
) {
    val context = LocalContext.current
    val githubAuth = GitHubAuthPreferences.getInstance(context)

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val userPublishedPlugins by viewModel.userPublishedPlugins.collectAsState()
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf<GitHubIssue?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.loadUserPublishedPlugins()
        }
    }

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = isLoading,
        errorMessage = errorMessage,
        isEmpty = userPublishedPlugins.isEmpty(),
        onLogin = { showGitHubLogin = true },
        onPublish = onNavigateToPublish,
        publishContentDescription = stringResource(R.string.publish_new_plugin),
        loginDescription = stringResource(R.string.need_login_github_manage_plugins),
        loadingMessage = stringResource(R.string.loading_your_plugins),
        emptyIcon = Icons.Default.Extension,
        emptyTitle = stringResource(R.string.no_published_plugins_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_mcp_plugin),
        emptyActionLabel = null
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(userPublishedPlugins, key = { it.id }) { plugin ->
                val pluginInfo = remember(plugin) {
                    MCPPluginParser.parsePluginInfo(plugin)
                }
                val isApproved = plugin.hasAnyLabelName(setOf(MCP_MARKET_VISIBILITY_LABEL))
                val displayLabels = plugin.labels.withoutLabelNames(setOf(MCP_MARKET_VISIBILITY_LABEL))
                val description =
                    pluginInfo.description.take(150) +
                        if (pluginInfo.description.length > 150) "..." else ""

                MarketManageItemCard(
                    title = plugin.title,
                    description = description,
                    issueNumber = plugin.number,
                    isOpen = plugin.state == "open",
                    supportingContent = {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarketManageReviewStatusChip(isApproved = isApproved)
                            displayLabels.take(2).forEach { label ->
                                MarketManageGitHubLabelChip(
                                    text = label.name,
                                    colorHex = label.color
                                )
                            }
                        }
                        if (displayLabels.size > 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MarketManageGitHubLabelChip(
                                text = "+${displayLabels.size - 2}",
                                colorHex = "9e9e9e"
                            )
                        }
                    },
                    actions = {
                        MarketManageSecondaryActionButton(
                            label = stringResource(R.string.edit),
                            icon = Icons.Default.Edit,
                            onClick = { onNavigateToEdit(plugin) }
                        )
                        if (plugin.state == "open") {
                            MarketManageDangerActionButton(
                                label = stringResource(R.string.remove),
                                icon = Icons.Default.Delete,
                                onClick = { showDeleteDialog = plugin }
                            )
                        } else {
                            MarketManagePrimaryActionButton(
                                label = stringResource(R.string.republish),
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.reopenPublishedPlugin(plugin.number, plugin.title) }
                            )
                        }
                    }
                )
            }
        }
    }

    showDeleteDialog?.let { plugin ->
        MarketManageDeleteDialog(
            text = stringResource(R.string.confirm_remove_plugin_from_market, plugin.title),
            onConfirm = {
                viewModel.deletePublishedPlugin(plugin.number, plugin.title)
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
