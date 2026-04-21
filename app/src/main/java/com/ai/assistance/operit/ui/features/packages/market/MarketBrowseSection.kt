package com.ai.assistance.operit.ui.features.packages.market

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class MarketBrowseSectionConfig(
    @StringRes val searchPlaceholderRes: Int,
    @StringRes val headerTitleRes: Int,
    @StringRes val emptySearchTitleRes: Int,
    @StringRes val emptyDefaultTitleRes: Int
)

data class MarketBrowseEntry(
    val model: MarketBrowseCardModel,
    val onViewDetails: () -> Unit,
    val onInstall: () -> Unit
)

@Composable
fun <T> MarketBrowseSection(
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
    config: MarketBrowseSectionConfig,
    itemKey: (T) -> Any,
    entryFactory: @Composable (T) -> MarketBrowseEntry,
    modifier: Modifier = Modifier
) {
    MarketBrowseList(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        searchQuery = searchQuery,
        sortOption = sortOption,
        onSearchQueryChanged = onSearchQueryChanged,
        onSortOptionChanged = onSortOptionChanged,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        searchPlaceholderRes = config.searchPlaceholderRes,
        headerTitleRes = config.headerTitleRes,
        emptySearchTitleRes = config.emptySearchTitleRes,
        emptyDefaultTitleRes = config.emptyDefaultTitleRes,
        itemKey = itemKey,
        itemContent = { item ->
            val entry = entryFactory(item)
            MarketBrowseCard(
                model = entry.model,
                onViewDetails = entry.onViewDetails,
                onInstall = entry.onInstall
            )
        },
        modifier = modifier
    )
}
