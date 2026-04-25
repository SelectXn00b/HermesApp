package com.ai.assistance.operit.ui.features.packages.market

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

data class MarketBrowseChip(
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

data class MarketBrowseCardModel(
    val title: String,
    val description: String,
    val ownerUsername: String = "",
    val publisherAvatarUrl: String? = null,
    val thumbsUpCount: Int = 0,
    val heartCount: Int = 0,
    val downloads: Int = 0,
    val chips: List<MarketBrowseChip> = emptyList(),
    val actionState: MarketBrowseActionState = MarketBrowseActionState.Available
)

sealed interface MarketBrowseActionState {
    data object Available : MarketBrowseActionState
    data object Installed : MarketBrowseActionState
    data class Installing(val progress: Float? = null) : MarketBrowseActionState
    data class Unavailable(val kind: MarketUnavailableKind = MarketUnavailableKind.Info) :
        MarketBrowseActionState
}

enum class MarketUnavailableKind {
    Info,
    Warning
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MarketBrowseList(
    items: List<T>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    searchQuery: String,
    sortOption: MarketSortOption,
    onSearchQueryChanged: (String) -> Unit,
    onSortOptionChanged: (MarketSortOption) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    @StringRes searchPlaceholderRes: Int,
    @StringRes headerTitleRes: Int,
    @StringRes emptySearchTitleRes: Int,
    @StringRes emptyDefaultTitleRes: Int,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val showInitialLoading = isLoading && items.isEmpty()
    val isRefreshing = isLoading && items.isNotEmpty() && searchQuery.isBlank()

    LaunchedEffect(listState, items.size, searchQuery, hasMore, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                if (searchQuery.isNotBlank()) return@collect
                val lastItemIndex = items.size - 1
                if (hasMore && !isLoadingMore && items.isNotEmpty() && lastVisibleIndex >= (lastItemIndex - 2)) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        MarketBrowseControls(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            sortOption = sortOption,
            onSortOptionChanged = onSortOptionChanged,
            searchPlaceholderRes = searchPlaceholderRes
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (showInitialLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val listContent: @Composable () -> Unit = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = itemKey) { item ->
                            itemContent(item)
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (items.isEmpty() && !isLoading) {
                            item {
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            if (searchQuery.isNotBlank()) {
                                                Icons.Default.SearchOff
                                            } else {
                                                Icons.Default.Store
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    if (searchQuery.isNotBlank()) {
                                                        emptySearchTitleRes
                                                    } else {
                                                        emptyDefaultTitleRes
                                                    }
                                                ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    if (searchQuery.isNotBlank()) {
                                                        R.string.try_changing_keywords
                                                    } else {
                                                        R.string.refresh_or_try_again_later
                                                    }
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (searchQuery.isBlank()) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                        state = pullToRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        listContent()
                    }
                } else {
                    listContent()
                }
            }
        }
    }
}

@Composable
fun MarketBrowseCard(
    model: MarketBrowseCardModel,
    onViewDetails: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onViewDetails() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarketBrowseLeadingIcon(title = model.title)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (model.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                MarketBrowseMetaRow(
                    ownerUsername = model.ownerUsername,
                    downloads = model.downloads,
                    thumbsUpCount = model.thumbsUpCount,
                    heartCount = model.heartCount
                )
            }

            MarketBrowseInstallButton(
                state = model.actionState,
                onClick = onInstall
            )
        }
    }
}

@Composable
private fun MarketBrowseLeadingIcon(title: String) {
    val initial =
        title
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MarketBrowseMetaRow(
    ownerUsername: String,
    downloads: Int,
    thumbsUpCount: Int,
    heartCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (ownerUsername.isNotBlank()) {
            Text(
                text = ownerUsername,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        MarketStatsSummary(
            downloads = downloads
        )

        if (thumbsUpCount > 0) {
            MarketBrowseMetaCount(
                icon = Icons.Default.ThumbUp,
                count = thumbsUpCount,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (heartCount > 0) {
            MarketBrowseMetaCount(
                icon = Icons.Default.Favorite,
                count = heartCount,
                tint = Color(0xFFE91E63)
            )
        }
    }
}

@Composable
private fun MarketBrowseMetaCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = tint
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun MarketBrowseChipView(chip: MarketBrowseChip) {
    Surface(shape = RoundedCornerShape(999.dp), color = chip.containerColor) {
        Text(
            text = chip.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = chip.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MarketBrowseInstallButton(
    state: MarketBrowseActionState,
    onClick: () -> Unit
) {
    val containerColor =
        when (state) {
            MarketBrowseActionState.Installed -> MaterialTheme.colorScheme.secondaryContainer
            is MarketBrowseActionState.Installing -> MaterialTheme.colorScheme.primaryContainer
            MarketBrowseActionState.Available -> MaterialTheme.colorScheme.primary
            is MarketBrowseActionState.Unavailable -> MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        when (state) {
            MarketBrowseActionState.Installed -> MaterialTheme.colorScheme.onSecondaryContainer
            is MarketBrowseActionState.Installing -> MaterialTheme.colorScheme.onPrimaryContainer
            MarketBrowseActionState.Available -> MaterialTheme.colorScheme.onPrimary
            is MarketBrowseActionState.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val enabled = state == MarketBrowseActionState.Available

    Surface(shape = CircleShape, color = containerColor) {
        IconButton(
            onClick = {
                if (enabled) {
                    onClick()
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            when (state) {
                MarketBrowseActionState.Installed -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                MarketBrowseActionState.Available -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                is MarketBrowseActionState.Installing -> {
                    val progress = state.progress
                    if (progress != null) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                    }
                }

                is MarketBrowseActionState.Unavailable -> {
                    Icon(
                        imageVector =
                            when (state.kind) {
                                MarketUnavailableKind.Info -> Icons.Default.Info
                                MarketUnavailableKind.Warning -> Icons.Default.Warning
                            },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
