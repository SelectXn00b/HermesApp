package com.ai.assistance.operit.ui.features.packages.screens

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketBrowseConfig
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketItem
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.BindMarketSearchToTopBar
import com.ai.assistance.operit.ui.features.packages.market.MarketBrowseSection
import com.ai.assistance.operit.ui.features.packages.market.McpMarketBrowseConfig
import com.ai.assistance.operit.ui.features.packages.market.SkillMarketBrowseConfig
import com.ai.assistance.operit.ui.features.packages.market.rememberArtifactMarketBrowseEntry
import com.ai.assistance.operit.ui.features.packages.market.rememberMcpMarketBrowseEntry
import com.ai.assistance.operit.ui.features.packages.market.rememberSkillMarketBrowseEntry
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel.SkillMarketViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class MarketHomeTab(@StringRes val labelRes: Int) {
    ARTIFACT(R.string.market_tab_artifact),
    SKILL(R.string.market_tab_skill),
    MCP(R.string.market_tab_mcp),
    MINE(R.string.market_tab_mine)
}

private data class MarketMineAuthState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val currentUser: GitHubUser? = null
)

@Composable
fun UnifiedMarketScreen(
    initialTab: MarketHomeTab = MarketHomeTab.ARTIFACT,
    onNavigateToArtifactPublish: () -> Unit = {},
    onNavigateToArtifactManage: () -> Unit = {},
    onNavigateToArtifactDetail: (GitHubIssue) -> Unit = {},
    onNavigateToSkillPublish: () -> Unit = {},
    onNavigateToSkillManage: () -> Unit = {},
    onNavigateToSkillDetail: (GitHubIssue) -> Unit = {},
    onNavigateToMcpPublish: () -> Unit = {},
    onNavigateToMcpManage: () -> Unit = {},
    onNavigateToMcpDetail: (GitHubIssue) -> Unit = {}
) {
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val tabStateHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            MarketHomeTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            tabStateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    MarketHomeTab.ARTIFACT -> ArtifactMarketPane(
                        onNavigateToDetail = onNavigateToArtifactDetail
                    )

                    MarketHomeTab.SKILL -> SkillMarketPane(
                        onNavigateToDetail = onNavigateToSkillDetail
                    )

                    MarketHomeTab.MCP -> McpMarketPane(
                        onNavigateToDetail = onNavigateToMcpDetail
                    )

                    MarketHomeTab.MINE -> MarketMinePane(
                        onManageArtifact = onNavigateToArtifactManage,
                        onManageSkill = onNavigateToSkillManage,
                        onManageMcp = onNavigateToMcpManage,
                        onPublishArtifact = onNavigateToArtifactPublish,
                        onPublishSkill = onNavigateToSkillPublish,
                        onPublishMcp = onNavigateToMcpPublish
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactMarketPane(
    onNavigateToDetail: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArtifactMarketViewModel =
        viewModel(
            key = "artifact-market-all",
            factory = ArtifactMarketViewModel.Factory(
                context.applicationContext,
                ArtifactMarketScope.ALL
            )
        )
    val items by viewModel.marketItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val marketStats by viewModel.marketStats.collectAsState()
    val installingIds by viewModel.installingIds.collectAsState()
    val installedArtifactIds by viewModel.installedArtifactIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var pendingUnsupportedInstall by remember { mutableStateOf<ArtifactMarketItem?>(null) }

    BindMarketSearchToTopBar(
        enabled = true,
        searchQuery = searchQuery,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        searchPlaceholderRes = ArtifactMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(Unit) {
        viewModel.refreshInstalledArtifacts()
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    MarketBrowseSection(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        searchQuery = searchQuery,
        sortOption = sortOption,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onRefresh = {
            viewModel.loadMarketData()
            viewModel.refreshInstalledArtifacts()
        },
        onLoadMore = viewModel::loadMoreMarketData,
        config = ArtifactMarketBrowseConfig,
        itemKey = { it.issue.id },
        entryFactory = { item ->
            rememberArtifactMarketBrowseEntry(
                item = item,
                marketStats = marketStats,
                installingIds = installingIds,
                installedArtifactIds = installedArtifactIds,
                isCompatible = viewModel.isCompatible(item.metadata),
                compatibilityLabel = viewModel.supportedVersionLabel(item.metadata),
                onViewDetails = onNavigateToDetail,
                onInstallRequest = { candidate ->
                    if (viewModel.isCompatible(candidate.metadata)) {
                        viewModel.installArtifact(candidate)
                    } else {
                        pendingUnsupportedInstall = candidate
                    }
                }
            )
        }
    )

    pendingUnsupportedInstall?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingUnsupportedInstall = null },
            title = { Text(stringResource(R.string.unsupported_artifact_version_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.unsupported_artifact_version_message,
                        item.metadata.displayName,
                        viewModel.currentAppVersion,
                        viewModel.supportedVersionLabel(item.metadata)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.installArtifact(item)
                        pendingUnsupportedInstall = null
                    }
                ) {
                    Text(stringResource(R.string.continue_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnsupportedInstall = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SkillMarketPane(
    onNavigateToDetail: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }
    val viewModel: SkillMarketViewModel =
        viewModel(
            factory = SkillMarketViewModel.Factory(
                context.applicationContext,
                skillRepository
            )
        )
    val items by viewModel.skillItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val marketStats by viewModel.marketStats.collectAsState()
    val installingSkills by viewModel.installingSkills.collectAsState()
    val installedSkillRepoUrls by viewModel.installedSkillRepoUrls.collectAsState()
    val installedSkillNames by viewModel.installedSkillNames.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BindMarketSearchToTopBar(
        enabled = true,
        searchQuery = searchQuery,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        searchPlaceholderRes = SkillMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(Unit) {
        viewModel.refreshInstalledSkills()
        if (searchQuery.isBlank() && items.isEmpty() && !isLoading) {
            viewModel.loadSkillMarketData()
        }
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    MarketBrowseSection(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        searchQuery = searchQuery,
        sortOption = sortOption,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onRefresh = {
            viewModel.loadSkillMarketData()
            viewModel.refreshInstalledSkills()
        },
        onLoadMore = viewModel::loadMoreSkillMarketData,
        config = SkillMarketBrowseConfig,
        itemKey = { it.issue.id },
        entryFactory = { item ->
            rememberSkillMarketBrowseEntry(
                item = item,
                marketStats = marketStats,
                installingSkills = installingSkills,
                installedSkillRepoUrls = installedSkillRepoUrls,
                installedSkillNames = installedSkillNames,
                onViewDetails = onNavigateToDetail,
                onInstall = viewModel::installSkill
            )
        }
    )
}

@Composable
private fun McpMarketPane(
    onNavigateToDetail: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val mcpRepository = remember { MCPRepository(context.applicationContext) }
    val viewModel: MCPMarketViewModel =
        viewModel(
            factory = MCPMarketViewModel.Factory(
                context.applicationContext,
                mcpRepository
            )
        )
    val items by viewModel.mcpItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val marketStats by viewModel.marketStats.collectAsState()
    val installingPlugins by viewModel.installingPlugins.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installedPluginIds by viewModel.installedPluginIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BindMarketSearchToTopBar(
        enabled = true,
        searchQuery = searchQuery,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        searchPlaceholderRes = McpMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(Unit) {
        if (searchQuery.isBlank() && items.isEmpty() && !isLoading) {
            viewModel.loadMCPMarketData()
        }
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    MarketBrowseSection(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        searchQuery = searchQuery,
        sortOption = sortOption,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onRefresh = viewModel::loadMCPMarketData,
        onLoadMore = viewModel::loadMoreMCPMarketData,
        config = McpMarketBrowseConfig,
        itemKey = { it.issue.id },
        entryFactory = { item ->
            rememberMcpMarketBrowseEntry(
                item = item,
                marketStats = marketStats,
                installingPlugins = installingPlugins,
                installProgress = installProgress,
                installedPluginIds = installedPluginIds,
                onViewDetails = onNavigateToDetail,
                onInstall = viewModel::installMcp
            )
        }
    )
}

@Composable
private fun MarketMinePane(
    onManageArtifact: () -> Unit,
    onManageSkill: () -> Unit,
    onManageMcp: () -> Unit,
    onPublishArtifact: () -> Unit,
    onPublishSkill: () -> Unit,
    onPublishMcp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val authState by produceState(initialValue = MarketMineAuthState(), githubAuth) {
        value = MarketMineAuthState(isLoading = true)

        val initialLoggedIn = githubAuth.isLoggedIn()
        val initialUser = githubAuth.getCurrentUserInfo()
        value =
            MarketMineAuthState(
                isLoading = false,
                isLoggedIn = initialLoggedIn,
                currentUser = if (initialLoggedIn) initialUser else null
            )

        githubAuth.isLoggedInFlow
            .combine(githubAuth.userInfoFlow) { isLoggedIn, currentUser ->
                MarketMineAuthState(
                    isLoading = false,
                    isLoggedIn = isLoggedIn,
                    currentUser = if (isLoggedIn) currentUser else null
                )
            }
            .collect { value = it }
    }
    var showLoginDialog by remember { mutableStateOf(false) }

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = { _ -> },
        searchPlaceholderRes = ArtifactMarketBrowseConfig.searchPlaceholderRes
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (authState.isLoading) {
            MarketAccountLoadingCard()
        } else {
            MarketAccountCard(
                isLoggedIn = authState.isLoggedIn,
                currentUser = authState.currentUser,
                onLogin = { showLoginDialog = true },
                onLogout = {
                    coroutineScope.launch {
                        githubAuth.logout()
                    }
                }
            )

            MarketMineSectionTitle(titleRes = R.string.market_section_manage)
            MarketMineActionCard(
                title = stringResource(R.string.market_manage_artifact),
                onClick = {
                    if (authState.isLoggedIn) {
                        onManageArtifact()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Settings
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_manage_skill),
                onClick = {
                    if (authState.isLoggedIn) {
                        onManageSkill()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Settings
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_manage_mcp),
                onClick = {
                    if (authState.isLoggedIn) {
                        onManageMcp()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Settings
            )

            Spacer(modifier = Modifier.height(4.dp))
            MarketMineSectionTitle(titleRes = R.string.market_section_publish)
            MarketMineActionCard(
                title = stringResource(R.string.market_publish_artifact),
                onClick = {
                    if (authState.isLoggedIn) {
                        onPublishArtifact()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Add
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_publish_skill),
                onClick = {
                    if (authState.isLoggedIn) {
                        onPublishSkill()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Add
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_publish_mcp),
                onClick = {
                    if (authState.isLoggedIn) {
                        onPublishMcp()
                    } else {
                        showLoginDialog = true
                    }
                },
                icon = Icons.Default.Add
            )
        }
    }

    if (showLoginDialog) {
        GitHubLoginWebViewDialog(
            onDismissRequest = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }
}

@Composable
private fun MarketAccountLoadingCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.app_content_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketAccountCard(
    isLoggedIn: Boolean,
    currentUser: GitHubUser?,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (isLoggedIn && currentUser != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentUser.avatarUrl.takeIf { it.isNotBlank() }?.let { avatarUrl ->
                    Image(
                        painter = rememberAsyncImagePainter(avatarUrl),
                        contentDescription = stringResource(R.string.user_avatar),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } ?: Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUser.name?.takeIf { it.isNotBlank() } ?: currentUser.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "@${currentUser.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.please_login_github_first),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.market_login_required_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(onClick = onLogin) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.login_github))
                }
            }
        }
    }
}

@Composable
private fun MarketMineSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MarketMineActionCard(
    title: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
