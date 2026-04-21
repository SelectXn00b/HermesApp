package com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.packages.market.CommentPostSuccessBehavior
import com.ai.assistance.operit.ui.features.packages.market.GitHubIssueMarketDefinition
import com.ai.assistance.operit.ui.features.packages.market.GitHubIssueMarketService
import com.ai.assistance.operit.ui.features.packages.market.IssueInteractionController
import com.ai.assistance.operit.ui.features.packages.market.IssueInteractionMessages
import com.ai.assistance.operit.ui.features.packages.market.MarketEntryStats
import com.ai.assistance.operit.ui.features.packages.market.SkillMarketBrowseItem
import com.ai.assistance.operit.ui.features.packages.market.MarketSortOption
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.buildMarketDisplayState
import com.ai.assistance.operit.ui.features.packages.market.loadMarketStatsMap
import com.ai.assistance.operit.ui.features.packages.market.normalizeMarketArtifactId
import com.ai.assistance.operit.ui.features.packages.utils.IssueBodyMetadataParser
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketDownloadTarget
import com.ai.assistance.operit.ui.features.packages.market.resolveSkillMarketEntryId
import com.ai.assistance.operit.ui.features.packages.market.toMarketEntryStats
import com.ai.assistance.operit.ui.features.packages.market.toRankMetric
import com.ai.assistance.operit.ui.features.packages.market.toSkillMarketBrowseItem
import com.ai.assistance.operit.ui.features.packages.market.updateMarketEntryStats
import com.ai.assistance.operit.ui.features.packages.utils.SkillIssueParser

class SkillMarketViewModel(
    private val context: Context,
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val githubApiService = GitHubApiService(context)
    private val marketStatsApiService = MarketStatsApiService()
    private val marketService = GitHubIssueMarketService(githubApiService, MARKET_DEFINITION)
    val githubAuth = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var searchJob: Job? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isRateLimitError = MutableStateFlow(false)
    val isRateLimitError: StateFlow<Boolean> = _isRateLimitError.asStateFlow()

    private val _skillItems = MutableStateFlow<List<SkillMarketBrowseItem>>(emptyList())
    private val _searchResultItems = MutableStateFlow<List<SkillMarketBrowseItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(MarketSortOption.UPDATED)
    val sortOption: StateFlow<MarketSortOption> = _sortOption.asStateFlow()

    private val _marketStats = MutableStateFlow<Map<String, MarketEntryStats>>(emptyMap())
    val marketStats: StateFlow<Map<String, MarketEntryStats>> = _marketStats.asStateFlow()

    val skillItems: StateFlow<List<SkillMarketBrowseItem>> =
        buildMarketDisplayState(
            scope = viewModelScope,
            baseItems = _skillItems,
            searchQuery = _searchQuery,
            searchResults = _searchResultItems,
            sortOption = _sortOption,
            stats = _marketStats,
            idSelector = { it.entryId },
            updatedAtSelector = { it.issue.updated_at },
            titleSelector = { it.title },
            likesSelector = { it.issue.reactions?.thumbs_up ?: 0 }
        )

    private val _installingSkills = MutableStateFlow<Set<String>>(emptySet())
    val installingSkills: StateFlow<Set<String>> = _installingSkills.asStateFlow()

    private val _installedSkillRepoUrls = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillRepoUrls: StateFlow<Set<String>> = _installedSkillRepoUrls.asStateFlow()

    private val _installedSkillNames = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillNames: StateFlow<Set<String>> = _installedSkillNames.asStateFlow()

    private val _userPublishedSkills = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val userPublishedSkills: StateFlow<List<GitHubIssue>> = _userPublishedSkills.asStateFlow()

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("skill_publish_draft", Context.MODE_PRIVATE)

    private val issueInteractionController = IssueInteractionController(
        scope = viewModelScope,
        context = context,
        marketService = marketService,
        logTag = TAG,
        onError = { _errorMessage.value = it },
        messages = IssueInteractionMessages(
            commentLoadFailed = { context.getString(R.string.skillmarket_load_comments_failed, it) },
            commentLoadError = { context.getString(R.string.skillmarket_load_comments_failed, it) },
            commentPostFailed = { context.getString(R.string.skillmarket_post_comment_failed, it) },
            commentPostError = { context.getString(R.string.skillmarket_post_comment_failed, it) },
            reactionFailed = { context.getString(R.string.skillmarket_like_failed, it) },
            reactionError = { context.getString(R.string.skillmarket_like_error, it) }
        )
    )

    val issueComments = issueInteractionController.issueComments
    val isLoadingComments = issueInteractionController.isLoadingComments
    val isPostingComment = issueInteractionController.isPostingComment
    val userAvatarCache = issueInteractionController.userAvatarCache
    val issueReactions = issueInteractionController.issueReactions
    val isLoadingReactions = issueInteractionController.isLoadingReactions
    val isReacting = issueInteractionController.isReacting
    val repositoryCache = issueInteractionController.repositoryCache

    data class PublishDraft(
        val title: String = "",
        val description: String = "",
        val repositoryUrl: String = ""
    )

    val publishDraft: PublishDraft
        get() = PublishDraft(
            title = sharedPrefs.getString("title", "") ?: "",
            description = sharedPrefs.getString("description", "") ?: "",
            repositoryUrl = sharedPrefs.getString("repositoryUrl", "") ?: ""
        )

    @Serializable
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private data class SkillMetadata(
        val description: String = "",
        val repositoryUrl: String,
        val category: String = "",
        val tags: String = "",
        val version: String = ""
    )

    class Factory(
        private val context: Context,
        private val skillRepository: SkillRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SkillMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SkillMarketViewModel(context, skillRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "SkillMarketViewModel"
        private val MARKET_DEFINITION = GitHubIssueMarketDefinition(
            owner = "AAswordman",
            repo = "OperitSkillMarket",
            label = "skill-plugin",
            pageSize = 50
        )
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _isLoading.value = false
            _searchResultItems.value = emptyList()
            _errorMessage.value = null
            _isRateLimitError.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            searchSkillMarketIssues(trimmedQuery)
        }
    }

    fun onSortOptionChanged(option: MarketSortOption) {
        _sortOption.value = option
        loadSkillMarketData()
    }

    fun ensureMarketStatsLoaded(entryId: String? = null) {
        if (entryId != null && _marketStats.value.containsKey(entryId)) {
            return
        }
        if (entryId == null && _marketStats.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            refreshMarketStats()
        }
    }

    private suspend fun searchSkillMarketIssues(rawQuery: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _isRateLimitError.value = false

        val isLoggedIn = try {
            githubAuth.isLoggedIn()
        } catch (_: Exception) {
            false
        }

        try {
            val result = marketService.searchOpenIssues(rawQuery = rawQuery, page = 1)

            if (rawQuery != _searchQuery.value.trim()) return

            result.fold(
                onSuccess = { issues ->
                    _searchResultItems.value = issues.map { it.toSkillMarketBrowseItem() }
                },
                onFailure = { error ->
                    val msg = error.message ?: "Unknown error"
                    _errorMessage.value = context.getString(R.string.skillmarket_load_failed, msg)
                    _searchResultItems.value = emptyList()

                    if (marketService.isLoggedOutRateLimit(msg, isLoggedIn)) {
                        _isRateLimitError.value = true
                    }

                    AppLogger.e(TAG, "Failed to search skill market data", error)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (rawQuery == _searchQuery.value.trim()) {
                _errorMessage.value = context.getString(R.string.skillmarket_network_error, e.message ?: "")
                _searchResultItems.value = emptyList()
                AppLogger.e(TAG, "Exception while searching skill market data", e)
            }
        } finally {
            if (rawQuery == _searchQuery.value.trim()) {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun saveDraft(title: String, description: String, repositoryUrl: String) {
        sharedPrefs.edit().apply {
            putString("title", title)
            putString("description", description)
            putString("repositoryUrl", repositoryUrl)
            apply()
        }
    }

    fun clearDraft() {
        sharedPrefs.edit().clear().apply()
    }

    fun parseSkillInfoFromIssue(issue: GitHubIssue): PublishDraft {
        val body = issue.body ?: return PublishDraft(title = issue.title)
        val metadata = parseSkillMetadata(body)
        return if (metadata != null) {
            PublishDraft(
                title = issue.title,
                description = metadata.description,
                repositoryUrl = metadata.repositoryUrl
            )
        } else {
            PublishDraft(
                title = issue.title,
                description = "Unable to parse Skill description, please fill manually.",
                repositoryUrl = ""
            )
        }
    }

    fun initiateGitHubLogin(context: Context) {
        try {
            val authUrl = githubAuth.getAuthorizationUrl()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = context.getString(R.string.skillmarket_login_failed, e.message ?: "")
            AppLogger.e(TAG, "Failed to initiate GitHub login", e)
        }
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, context.getString(R.string.skillmarket_logged_out), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_logout_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    fun loadSkillMarketData() {
        viewModelScope.launch {
            _isLoading.value = true
            _isLoadingMore.value = false
            _errorMessage.value = null
            _isRateLimitError.value = false
            _hasMore.value = false
            currentPage = 1
            totalPages = 1

            val isLoggedIn = try {
                githubAuth.isLoggedIn()
            } catch (_: Exception) {
                false
            }

            try {
                refreshMarketStats()
                val result =
                    marketStatsApiService.getRankPage(
                        type = MarketStatsType.SKILL.wireValue,
                        metric = _sortOption.value.toRankMetric(),
                        page = 1
                    )

                result.fold(
                    onSuccess = { rankPage ->
                        currentPage = rankPage.page
                        totalPages = rankPage.totalPages.coerceAtLeast(1)
                        rankPage.items.forEach { entry ->
                            _marketStats.updateMarketEntryStats(entry.id) {
                                entry.toMarketEntryStats()
                            }
                        }
                        _skillItems.value = rankPage.items.map { it.toSkillMarketBrowseItem() }
                        _hasMore.value = currentPage < totalPages
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "Unknown error"
                        _errorMessage.value = context.getString(R.string.skillmarket_load_failed, msg)
                        _skillItems.value = emptyList()
                        _hasMore.value = false

                        if (marketService.isLoggedOutRateLimit(msg, isLoggedIn)) {
                            _isRateLimitError.value = true
                        }

                        AppLogger.e(TAG, "Failed to load skill market data", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_network_error, e.message ?: "")
                _skillItems.value = emptyList()
                AppLogger.e(TAG, "Exception while loading skill market data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreSkillMarketData() {
        if (_searchQuery.value.isNotBlank() || _isLoading.value || _isLoadingMore.value || !_hasMore.value) {
            return
        }

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextPage = currentPage + 1
                val result =
                    marketStatsApiService.getRankPage(
                        type = MarketStatsType.SKILL.wireValue,
                        metric = _sortOption.value.toRankMetric(),
                        page = nextPage
                    )

                result.fold(
                    onSuccess = { rankPage ->
                        currentPage = rankPage.page
                        totalPages = rankPage.totalPages.coerceAtLeast(1)
                        rankPage.items.forEach { entry ->
                            _marketStats.updateMarketEntryStats(entry.id) {
                                entry.toMarketEntryStats()
                            }
                        }
                        _skillItems.value =
                            (_skillItems.value + rankPage.items.map { it.toSkillMarketBrowseItem() })
                                .distinctBy { it.issue.id }
                        _hasMore.value = currentPage < totalPages
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "Unknown error"
                        _errorMessage.value = context.getString(R.string.skillmarket_load_failed, msg)
                        _hasMore.value = false
                        AppLogger.e(TAG, "Failed to load more skill market data", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_network_error, e.message ?: "")
                _hasMore.value = false
                AppLogger.e(TAG, "Exception while loading more skill market data", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun refreshInstalledSkills() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                try {
                    skillRepository.getAvailableSkillPackages()
                } catch (_: Exception) {
                    emptyMap()
                }
            }

            val installedNames = installed.keys.toSet()
            val installedRepoUrls = installed.values.mapNotNull { pkg ->
                try {
                    val marker = pkg.directory.resolve(".operit_repo_url")
                    if (marker.exists() && marker.isFile) {
                        marker.readText().trim().ifBlank { null }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }.toSet()

            _installedSkillNames.value = installedNames
            _installedSkillRepoUrls.value = installedRepoUrls
        }
    }

    fun loadUserPublishedSkills() {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = context.getString(R.string.skillmarket_unable_get_user_info)
                    return@launch
                }

                val finalResult = marketService.getUserPublishedIssues(
                    creator = userInfo.login,
                    fallbackWithoutLabel = true
                )

                finalResult.fold(
                    onSuccess = { issues ->
                        _userPublishedSkills.value = issues
                    },
                    onFailure = { error ->
                        _errorMessage.value = context.getString(R.string.skillmarket_load_published_failed, error.message ?: "")
                        AppLogger.e(TAG, "Failed to load user published skills", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_network_error, e.message ?: "")
                AppLogger.e(TAG, "Network error while loading user published skills", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeSkillFromMarket(issueNumber: Int) {
        viewModelScope.launch {
            try {
                if (!githubAuth.isLoggedIn()) {
                    _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                    return@launch
                }

                _isLoading.value = true
                val result = marketService.updateIssueState(
                    issueNumber = issueNumber,
                    state = "closed"
                )

                result.fold(
                    onSuccess = {
                        Toast.makeText(context, context.getString(R.string.skillmarket_removed_from_market), Toast.LENGTH_SHORT).show()
                        loadUserPublishedSkills()
                        loadSkillMarketData()
                    },
                    onFailure = { error ->
                        _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, error.message ?: "")
                        AppLogger.e(TAG, "Failed to remove skill from market", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to remove skill from market", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadIssueComments(issueNumber: Int) {
        issueInteractionController.loadIssueComments(issueNumber, perPage = 50)
    }

    fun postIssueComment(issueNumber: Int, body: String) {
        val text = body.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }

            issueInteractionController.postIssueComment(
                issueNumber = issueNumber,
                body = text,
                successBehavior = CommentPostSuccessBehavior.RELOAD_FROM_SERVER,
                perPage = 50
            )
        }
    }

    suspend fun publishSkill(
        title: String,
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): Boolean {
        return try {
            if (!githubAuth.isLoggedIn()) return false

            val body = buildSkillPublishIssueBody(
                description = description,
                repositoryUrl = repositoryUrl,
                version = version
            )

            val result = marketService.createIssue(
                title = title,
                body = body
            )

            if (result.isSuccess) return true

            val errMsg = result.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("422")) {
                val retry = marketService.createIssue(
                    title = title,
                    body = body,
                    includeDefaultLabel = false
                )
                retry.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to publish skill", e)
            false
        }
    }

    suspend fun updatePublishedSkill(
        issueNumber: Int,
        title: String,
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): Boolean {
        return try {
            if (!githubAuth.isLoggedIn()) return false

            val body = buildSkillPublishIssueBody(
                description = description,
                repositoryUrl = repositoryUrl,
                version = version
            )

            val result = marketService.updateIssueContent(
                issueNumber = issueNumber,
                title = title,
                body = body
            )

            result.isSuccess
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update skill", e)
            false
        }
    }

    private fun buildSkillPublishIssueBody(
        description: String,
        repositoryUrl: String,
        version: String = "v1"
    ): String {
        return buildString {
            try {
                val metadata = SkillMetadata(
                    description = description,
                    repositoryUrl = repositoryUrl,
                    version = version
                )
                val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
                val metadataJson = json.encodeToString(metadata)
                appendLine("<!-- operit-skill-json: $metadataJson -->")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to serialize skill metadata", e)
            }

            appendLine("<!-- operit-parser-version: $version -->")
            appendLine()

            appendLine(context.getString(R.string.skill_publish_body_section_skill_info))
            appendLine()
            appendLine(description)
            appendLine()

            if (repositoryUrl.isNotBlank()) {
                appendLine(context.getString(R.string.skill_publish_body_section_repo_info))
                appendLine()
                appendLine(context.getString(R.string.skill_publish_body_label_repo_url, repositoryUrl))
                appendLine()
            }

            if (repositoryUrl.isNotBlank()) {
                appendLine(context.getString(R.string.skill_publish_body_section_install_method))
                appendLine()
                appendLine(context.getString(R.string.skill_publish_body_install_step1))
                appendLine(context.getString(R.string.skill_publish_body_install_step2))
                appendLine(context.getString(R.string.skill_publish_body_install_step3, repositoryUrl))
                appendLine(context.getString(R.string.skill_publish_body_install_step4))
                appendLine()
            }

            appendLine(context.getString(R.string.skill_publish_body_section_tech_info))
            appendLine()
            appendLine(context.getString(R.string.skill_publish_body_table_header))
            appendLine(context.getString(R.string.skill_publish_body_table_separator))
            appendLine(context.getString(R.string.skill_publish_body_table_row_platform))
            appendLine(context.getString(R.string.skill_publish_body_table_row_parser_version))
            appendLine(
                context.getString(
                    R.string.skill_publish_body_table_row_publish_time,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                )
            )
            appendLine()
        }
    }

    private fun parseSkillMetadata(body: String): SkillMetadata? {
        return IssueBodyMetadataParser.parseCommentJson(
            body = body,
            prefix = "<!-- operit-skill-json: ",
            tag = TAG,
            metadataName = "skill metadata"
        )
    }

    fun installSkillFromIssue(issue: GitHubIssue) {
        val skillInfo = SkillIssueParser.parseSkillInfo(issue)
        installSkillFromRepoUrlInternal(
            repoUrl = skillInfo.repositoryUrl,
            statsId = resolveSkillMarketEntryId(issue),
            downloadTarget = resolveMarketDownloadTarget(skillInfo.repositoryUrl, issue.html_url)
        )
    }

    fun installSkill(item: SkillMarketBrowseItem) {
        installSkillFromRepoUrlInternal(
            repoUrl = item.repositoryUrl,
            statsId = item.entryId,
            downloadTarget = resolveMarketDownloadTarget(item.repositoryUrl, item.issue.html_url)
        )
    }

    fun installSkillFromRepoUrl(repoUrl: String) {
        installSkillFromRepoUrlInternal(
            repoUrl = repoUrl,
            statsId = normalizeMarketArtifactId(repoUrl),
            downloadTarget = resolveMarketDownloadTarget(repoUrl, repoUrl)
        )
    }

    private fun installSkillFromRepoUrlInternal(
        repoUrl: String,
        statsId: String,
        downloadTarget: String
    ) {
        val key = repoUrl.trim()
        if (key.isBlank()) {
            _errorMessage.value = context.getString(R.string.skillmarket_invalid_repo_url)
            return
        }

        viewModelScope.launch {
            _installingSkills.value = _installingSkills.value + key
            try {
                trackSkillDownload(statsId, downloadTarget)
                val result = skillRepository.importSkillFromGitHubRepo(key)
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                if (isSkillInstallSuccess(result)) {
                    _installedSkillRepoUrls.value = _installedSkillRepoUrls.value + key
                    refreshInstalledSkills()
                }
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_install_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to install skill from repo", e)
            } finally {
                _installingSkills.value = _installingSkills.value - key
            }
        }
    }

    private suspend fun refreshMarketStats() {
        _marketStats.value =
            loadMarketStatsMap(
                marketStatsApiService = marketStatsApiService,
                type = MarketStatsType.SKILL,
                logTag = TAG,
                errorLabel = "skill"
            )
    }

    private fun isSkillInstallSuccess(message: String): Boolean {
        return message.startsWith(context.getString(R.string.skill_imported, ""), ignoreCase = true) ||
            message.startsWith(context.getString(R.string.skill_imported_with_desc, "", ""), ignoreCase = true)
    }

    private suspend fun trackSkillDownload(statsId: String, targetUrl: String) {
        marketStatsApiService.trackDownload(
            type = MarketStatsType.SKILL.wireValue,
            id = statsId,
            targetUrl = targetUrl
        ).onSuccess {
            _marketStats.updateMarketEntryStats(statsId) { current ->
                current.copy(downloads = current.downloads + 1)
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to track skill download for $statsId: ${error.message}")
        }
    }

    fun fetchUserAvatar(username: String) {
        issueInteractionController.fetchUserAvatar(username)
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        issueInteractionController.fetchRepositoryInfo(repositoryUrl)
    }

    fun loadIssueReactions(issueNumber: Int, force: Boolean = false) {
        issueInteractionController.loadIssueReactions(issueNumber, force)
    }

    fun addReactionToIssue(issueNumber: Int, reactionType: String) {
        issueInteractionController.addReactionToIssue(issueNumber, reactionType)
    }
}
