package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

fun <T> buildMarketDisplayState(
    scope: CoroutineScope,
    baseItems: Flow<List<T>>,
    searchQuery: Flow<String>,
    searchResults: Flow<List<T>>,
    sortOption: Flow<MarketSortOption>,
    stats: Flow<Map<String, MarketEntryStats>>,
    idSelector: (T) -> String,
    updatedAtSelector: (T) -> String,
    titleSelector: (T) -> String,
    likesSelector: (T) -> Int
): StateFlow<List<T>> {
    return combine(baseItems, searchQuery, searchResults, sortOption, stats) {
            items,
            query,
            searchedItems,
            currentSortOption,
            currentStats ->
        val source = if (query.isBlank()) items else searchedItems
        sortMarketEntries(
            items = source,
            sortOption = currentSortOption,
            statsById = currentStats,
            idSelector = idSelector,
            updatedAtSelector = updatedAtSelector,
            titleSelector = titleSelector,
            likesSelector = likesSelector
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

suspend fun loadMarketStatsMap(
    marketStatsApiService: MarketStatsApiService,
    type: MarketStatsType,
    logTag: String,
    errorLabel: String
): Map<String, MarketEntryStats> {
    return marketStatsApiService.getStats(type.wireValue)
        .fold(
            onSuccess = { response ->
                response.items.mapValues { (_, value) -> value.toMarketEntryStats() }
            },
            onFailure = { error ->
                AppLogger.w(logTag, "Failed to load $errorLabel market stats: ${error.message}")
                emptyMap()
            }
        )
}

suspend fun loadMarketStatsMap(
    marketStatsApiService: MarketStatsApiService,
    types: List<MarketStatsType>,
    logTag: String
): Map<String, MarketEntryStats> {
    val stats = mutableMapOf<String, MarketEntryStats>()
    types.forEach { type ->
        stats.putAll(
            loadMarketStatsMap(
                marketStatsApiService = marketStatsApiService,
                type = type,
                logTag = logTag,
                errorLabel = type.wireValue
            )
        )
    }
    return stats
}

fun MutableStateFlow<Map<String, MarketEntryStats>>.updateMarketEntryStats(
    entryId: String,
    transform: (MarketEntryStats) -> MarketEntryStats
) {
    val current = value[entryId] ?: MarketEntryStats()
    value = value + (entryId to transform(current))
}
