package com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.GitHubReaction
import com.ai.assistance.operit.data.api.GitHubRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketItem
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketMetadata
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.ForgeRepoInfo
import com.ai.assistance.operit.ui.features.packages.market.GitHubForgePublishService
import com.ai.assistance.operit.ui.features.packages.market.GitHubIssueMarketService
import com.ai.assistance.operit.ui.features.packages.market.LocalPublishableArtifact
import com.ai.assistance.operit.ui.features.packages.market.MarketEntryStats
import com.ai.assistance.operit.ui.features.packages.market.MarketRegistrationPayload
import com.ai.assistance.operit.ui.features.packages.market.MarketSortOption
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactRequest
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.PublishAttemptResult
import com.ai.assistance.operit.ui.features.packages.market.PublishProgressStage
import com.ai.assistance.operit.ui.features.packages.market.buildMarketDisplayState
import com.ai.assistance.operit.ui.features.packages.market.formatSupportedAppVersions
import com.ai.assistance.operit.ui.features.packages.market.isAppVersionSupported
import com.ai.assistance.operit.ui.features.packages.market.loadMarketStatsMap
import com.ai.assistance.operit.ui.features.packages.market.normalizeAppVersionOrNull
import com.ai.assistance.operit.ui.features.packages.market.normalizeMarketArtifactId
import com.ai.assistance.operit.ui.features.packages.market.resolveArtifactMarketEntryId
import com.ai.assistance.operit.ui.features.packages.market.resolveArtifactMarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketDownloadTarget
import com.ai.assistance.operit.ui.features.packages.market.toRankMetric
import com.ai.assistance.operit.ui.features.packages.market.toMarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.toMarketEntryStats
import com.ai.assistance.operit.ui.features.packages.market.toArtifactMarketItem
import com.ai.assistance.operit.ui.features.packages.market.updateMarketEntryStats
import com.ai.assistance.operit.ui.features.packages.utils.ArtifactIssueParser
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtifactMarketViewModel(
    private val context: Context,
    private val scope: ArtifactMarketScope
) : ViewModel() {
    private val githubApiService = GitHubApiService(context)
    private val marketStatsApiService = MarketStatsApiService()
    private val githubAuth = GitHubAuthPreferences.getInstance(context)
    private val forgePublishService = GitHubForgePublishService(context, githubApiService)
    private val packageManager =
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    private val avatarCachePrefs: SharedPreferences =
        context.getSharedPreferences("github_avatar_cache", Context.MODE_PRIVATE)
    private val marketServices =
        PublishArtifactType.entries.associateWith { type ->
            GitHubIssueMarketService(githubApiService, type.marketDefinition())
        }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(MarketSortOption.UPDATED)
    val sortOption: StateFlow<MarketSortOption> = _sortOption.asStateFlow()

    private val _marketStats = MutableStateFlow<Map<String, MarketEntryStats>>(emptyMap())
    val marketStats: StateFlow<Map<String, MarketEntryStats>> = _marketStats.asStateFlow()

    private val _marketItems = MutableStateFlow<List<ArtifactMarketItem>>(emptyList())
    private val _searchResultItems = MutableStateFlow<List<ArtifactMarketItem>>(emptyList())

    val marketItems: StateFlow<List<ArtifactMarketItem>> =
        buildMarketDisplayState(
            scope = viewModelScope,
            baseItems = _marketItems,
            searchQuery = _searchQuery,
            searchResults = _searchResultItems,
            sortOption = _sortOption,
            stats = _marketStats,
            idSelector = { resolveArtifactMarketEntryId(it.metadata) },
            updatedAtSelector = { it.issue.updated_at },
            titleSelector = { it.metadata.displayName.ifBlank { it.issue.title } },
            likesSelector = { it.issue.reactions?.thumbs_up ?: 0 }
        )

    private val _publishableArtifacts = MutableStateFlow<List<LocalPublishableArtifact>>(emptyList())
    val publishableArtifacts: StateFlow<List<LocalPublishableArtifact>> = _publishableArtifacts.asStateFlow()

    private val _userPublishedArtifacts = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val userPublishedArtifacts: StateFlow<List<GitHubIssue>> = _userPublishedArtifacts.asStateFlow()

    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    val installingIds: StateFlow<Set<String>> = _installingIds.asStateFlow()

    private val _installedArtifactIds = MutableStateFlow<Set<String>>(emptySet())
    val installedArtifactIds: StateFlow<Set<String>> = _installedArtifactIds.asStateFlow()

    private val _publishProgressStage = MutableStateFlow(PublishProgressStage.IDLE)
    val publishProgressStage: StateFlow<PublishProgressStage> = _publishProgressStage.asStateFlow()

    private val _publishMessage = MutableStateFlow<String?>(null)
    val publishMessage: StateFlow<String?> = _publishMessage.asStateFlow()

    private val _publishErrorMessage = MutableStateFlow<String?>(null)
    val publishErrorMessage: StateFlow<String?> = _publishErrorMessage.asStateFlow()

    private val _publishSuccessMessage = MutableStateFlow<String?>(null)
    val publishSuccessMessage: StateFlow<String?> = _publishSuccessMessage.asStateFlow()

    private val _requiresForgeInitialization = MutableStateFlow(false)
    val requiresForgeInitialization: StateFlow<Boolean> = _requiresForgeInitialization.asStateFlow()

    private val _registrationRetryAvailable = MutableStateFlow(false)
    val registrationRetryAvailable: StateFlow<Boolean> = _registrationRetryAvailable.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        githubAuth.isLoggedInFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val currentUser: StateFlow<GitHubUser?> =
        githubAuth.userInfoFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    private val _issueComments = MutableStateFlow<Map<Int, List<GitHubComment>>>(emptyMap())
    val issueComments: StateFlow<Map<Int, List<GitHubComment>>> = _issueComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingComments: StateFlow<Set<Int>> = _isLoadingComments.asStateFlow()

    private val _isPostingComment = MutableStateFlow<Set<Int>>(emptySet())
    val isPostingComment: StateFlow<Set<Int>> = _isPostingComment.asStateFlow()

    private val _userAvatarCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAvatarCache: StateFlow<Map<String, String>> = _userAvatarCache.asStateFlow()

    private val _issueReactions = MutableStateFlow<Map<Int, List<GitHubReaction>>>(emptyMap())
    val issueReactions: StateFlow<Map<Int, List<GitHubReaction>>> = _issueReactions.asStateFlow()

    private val _isLoadingReactions = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingReactions: StateFlow<Set<Int>> = _isLoadingReactions.asStateFlow()

    private val _isReacting = MutableStateFlow<Set<Int>>(emptySet())
    val isReacting: StateFlow<Set<Int>> = _isReacting.asStateFlow()

    private val _repositoryCache = MutableStateFlow<Map<String, GitHubRepository>>(emptyMap())
    val repositoryCache: StateFlow<Map<String, GitHubRepository>> = _repositoryCache.asStateFlow()

    private val supportedTypes = scope.supportedTypes()
    private val currentBrowsePages = mutableMapOf<PublishArtifactType, Int>()
    private val totalBrowsePages = mutableMapOf<PublishArtifactType, Int>()
    private var searchJob: Job? = null
    private var pendingPublishRequest: PublishArtifactRequest? = null
    private var pendingMarketRegistrationPayload: MarketRegistrationPayload? = null

    init {
        loadAvatarCacheFromPrefs()
        loadMarketData()
        refreshPublishableArtifacts()
        refreshInstalledArtifacts()
    }

    val currentAppVersion: String
        get() = BuildConfig.VERSION_NAME.trim().ifBlank { "unknown" }

    fun initiateGitHubLogin(context: Context) {
        try {
            val authUrl = githubAuth.getAuthorizationUrl()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Failed to open GitHub login"
            AppLogger.e(TAG, "Failed to initiate GitHub login", e)
        }
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, "Logged out from GitHub", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to logout from GitHub"
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    fun loadMarketData() {
        viewModelScope.launch {
            _isLoading.value = true
            _isLoadingMore.value = false
            _errorMessage.value = null
            _hasMore.value = false
            currentBrowsePages.clear()
            totalBrowsePages.clear()
            try {
                refreshMarketStats()
                loadBrowsePages(reset = true)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load market data"
                AppLogger.e(TAG, "Failed to load market data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreMarketData() {
        if (_searchQuery.value.isNotBlank() || _isLoading.value || _isLoadingMore.value || !_hasMore.value) {
            return
        }

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                loadBrowsePages(reset = false)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load more market data"
                AppLogger.e(TAG, "Failed to load more market data", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _isLoading.value = false
            _errorMessage.value = null
            _searchResultItems.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            searchMarket(trimmed)
        }
    }

    fun onSortOptionChanged(option: MarketSortOption) {
        _sortOption.value = option
        loadMarketData()
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

    private suspend fun searchMarket(query: String) {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            searchOpenIssues(query).fold(
                onSuccess = { items ->
                    if (query == _searchQuery.value.trim()) {
                        _searchResultItems.value = items
                    }
                },
                onFailure = { error ->
                    if (query == _searchQuery.value.trim()) {
                        _errorMessage.value = error.message ?: "Failed to search market data"
                        _searchResultItems.value = emptyList()
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (query == _searchQuery.value.trim()) {
                _errorMessage.value = e.message ?: "Failed to search market data"
                _searchResultItems.value = emptyList()
                AppLogger.e(TAG, "Failed to search market data", e)
            }
        } finally {
            if (query == _searchQuery.value.trim()) {
                _isLoading.value = false
            }
        }
    }

    fun refreshPublishableArtifacts() {
        viewModelScope.launch {
            val artifacts =
                withContext(Dispatchers.IO) {
                    packageManager.getPublishablePackageSources()
                        .mapNotNull { source ->
                            val type = inferArtifactType(source.isToolPkg, source.fileExtension) ?: return@mapNotNull null
                            if (type !in supportedTypes) return@mapNotNull null
                            LocalPublishableArtifact(
                                type = type,
                                packageName = source.packageName,
                                displayName = source.displayName,
                                description = source.description,
                                sourceFile = File(source.sourcePath),
                                inferredVersion = source.inferredVersion
                            )
                        }
                        .sortedWith(compareBy<LocalPublishableArtifact> { it.type.ordinal }.thenBy { it.displayName.lowercase() })
                }
            _publishableArtifacts.value = artifacts
        }
    }

    fun refreshInstalledArtifacts() {
        viewModelScope.launch {
            val installed =
                withContext(Dispatchers.IO) {
                    packageManager.getPublishablePackageSources()
                        .mapNotNull { source ->
                            val type = inferArtifactType(source.isToolPkg, source.fileExtension) ?: return@mapNotNull null
                            if (type !in supportedTypes) return@mapNotNull null
                            normalizeMarketArtifactId(source.packageName)
                        }
                        .toSet()
                }
            _installedArtifactIds.value = installed
        }
    }

    fun loadUserPublishedArtifacts() {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "GitHub login required"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = "Unable to read GitHub user info"
                    return@launch
                }

                aggregateResults { service ->
                    service.getUserPublishedIssues(
                        creator = userInfo.login,
                        fallbackWithoutLabel = true
                    )
                }.fold(
                    onSuccess = { issues ->
                        _userPublishedArtifacts.value = issues.sortedByDescending { it.updated_at }
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: "Failed to load published artifacts"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load published artifacts"
                AppLogger.e(TAG, "Failed to load user published artifacts", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeArtifactFromMarket(issue: GitHubIssue, title: String) {
        updateArtifactIssueState(issue = issue, state = "closed", successMessage = "已从市场移除「$title」")
    }

    fun reopenArtifactInMarket(issue: GitHubIssue, title: String) {
        updateArtifactIssueState(issue = issue, state = "open", successMessage = "已重新发布「$title」")
    }

    private fun updateArtifactIssueState(issue: GitHubIssue, state: String, successMessage: String) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "GitHub login required"
                return@launch
            }

            val type = artifactTypeFromIssue(issue)
            if (type == null) {
                _errorMessage.value = "Invalid artifact metadata"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            try {
                marketService(type).updateIssueState(issue.number, state).fold(
                    onSuccess = {
                        Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                        _userPublishedArtifacts.value =
                            _userPublishedArtifacts.value.map { existing ->
                                if (existing.id == issue.id) existing.copy(state = state) else existing
                            }
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: "Failed to update market entry"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update market entry"
                AppLogger.e(TAG, "Failed to update market entry state", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadIssueComments(issueNumber: Int, type: PublishArtifactType) {
        viewModelScope.launch {
            try {
                _isLoadingComments.value = _isLoadingComments.value + issueNumber
                marketService(type).getIssueComments(issueNumber = issueNumber, perPage = 50).fold(
                    onSuccess = { comments ->
                        _issueComments.value = _issueComments.value + (issueNumber to comments)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load comments: ${error.message.orEmpty()}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load comments: ${e.message.orEmpty()}"
                AppLogger.e(TAG, "Failed to load comments for issue #$issueNumber", e)
            } finally {
                _isLoadingComments.value = _isLoadingComments.value - issueNumber
            }
        }
    }

    fun postIssueComment(issueNumber: Int, type: PublishArtifactType, body: String) {
        val text = body.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "GitHub login required"
                return@launch
            }
            try {
                _isPostingComment.value = _isPostingComment.value + issueNumber
                marketService(type).createIssueComment(issueNumber = issueNumber, body = text).fold(
                    onSuccess = {
                        loadIssueComments(issueNumber, type)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to post comment: ${error.message.orEmpty()}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to post comment: ${e.message.orEmpty()}"
                AppLogger.e(TAG, "Failed to post comment for issue #$issueNumber", e)
            } finally {
                _isPostingComment.value = _isPostingComment.value - issueNumber
            }
        }
    }

    fun loadIssueReactions(issueNumber: Int, type: PublishArtifactType, force: Boolean = false) {
        if (!force && _issueReactions.value.containsKey(issueNumber)) return

        viewModelScope.launch {
            try {
                _isLoadingReactions.value = _isLoadingReactions.value + issueNumber
                marketService(type).getIssueReactions(issueNumber).fold(
                    onSuccess = { reactions ->
                        _issueReactions.value = _issueReactions.value + (issueNumber to reactions)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load reactions: ${error.message.orEmpty()}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load reactions: ${e.message.orEmpty()}"
                AppLogger.e(TAG, "Failed to load reactions for issue #$issueNumber", e)
            } finally {
                _isLoadingReactions.value = _isLoadingReactions.value - issueNumber
            }
        }
    }

    fun addReactionToIssue(issueNumber: Int, type: PublishArtifactType, reactionType: String) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "GitHub login required"
                return@launch
            }
            try {
                _isReacting.value = _isReacting.value + issueNumber
                marketService(type).createIssueReaction(issueNumber, reactionType).fold(
                    onSuccess = { newReaction ->
                        val existing = _issueReactions.value[issueNumber].orEmpty()
                        _issueReactions.value = _issueReactions.value + (issueNumber to (existing + newReaction))
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to add reaction: ${error.message.orEmpty()}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add reaction: ${e.message.orEmpty()}"
                AppLogger.e(TAG, "Failed to add reaction for issue #$issueNumber", e)
            } finally {
                _isReacting.value = _isReacting.value - issueNumber
            }
        }
    }

    fun fetchUserAvatar(username: String) {
        if (username.isBlank() || _userAvatarCache.value.containsKey(username)) return

        viewModelScope.launch {
            try {
                githubApiService.getUser(username).fold(
                    onSuccess = { user ->
                        _userAvatarCache.value = _userAvatarCache.value + (username to user.avatarUrl)
                        saveAvatarToPrefs(username, user.avatarUrl)
                    },
                    onFailure = { error ->
                        AppLogger.w(TAG, "Failed to fetch avatar for $username: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to fetch avatar for $username", e)
            }
        }
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        if (repositoryUrl.isBlank() || _repositoryCache.value.containsKey(repositoryUrl)) return

        viewModelScope.launch {
            marketService(supportedTypes.first()).getRepositoryByUrl(repositoryUrl).fold(
                onSuccess = { repo ->
                    _repositoryCache.value = _repositoryCache.value + (repositoryUrl to repo)
                },
                onFailure = { error ->
                    AppLogger.w(TAG, "Failed to fetch repository info for $repositoryUrl: ${error.message}")
                }
            )
        }
    }

    fun requestPublish(
        packageName: String,
        displayName: String,
        description: String,
        version: String,
        minSupportedAppVersion: String?,
        maxSupportedAppVersion: String?
    ) {
        val localArtifact = _publishableArtifacts.value.firstOrNull { it.packageName == packageName }
        if (localArtifact == null) {
            _publishErrorMessage.value = "Local artifact not found"
            return
        }

        val request =
            PublishArtifactRequest(
                localArtifact = localArtifact,
                displayName = displayName,
                description = description,
                version = version,
                minSupportedAppVersion = minSupportedAppVersion,
                maxSupportedAppVersion = maxSupportedAppVersion
            )
        executePublish(request, allowCreateForgeRepo = false)
    }

    fun confirmForgeInitializationAndPublish() {
        val request = pendingPublishRequest ?: return
        _requiresForgeInitialization.value = false
        executePublish(request, allowCreateForgeRepo = true)
    }

    fun dismissForgeInitializationPrompt() {
        pendingPublishRequest = null
        _requiresForgeInitialization.value = false
        _publishProgressStage.value = PublishProgressStage.IDLE
    }

    fun retryPendingMarketRegistration() {
        val payload = pendingMarketRegistrationPayload ?: return
        viewModelScope.launch {
            _publishProgressStage.value = PublishProgressStage.REGISTERING_MARKET
            _publishErrorMessage.value = null
            forgePublishService.retryMarketRegistration(payload).fold(
                onSuccess = {
                    _registrationRetryAvailable.value = false
                    pendingMarketRegistrationPayload = null
                    _publishProgressStage.value = PublishProgressStage.COMPLETED
                    _publishSuccessMessage.value = "Market registration completed"
                    loadMarketData()
                    loadUserPublishedArtifacts()
                },
                onFailure = { error ->
                    _publishErrorMessage.value = error.message ?: "Failed to register market entry"
                    _publishProgressStage.value = PublishProgressStage.IDLE
                }
            )
        }
    }

    fun installArtifact(item: ArtifactMarketItem) {
        val itemId = item.metadata.normalizedId
        viewModelScope.launch {
            _installingIds.value = _installingIds.value + itemId
            _errorMessage.value = null
            try {
                trackArtifactDownload(item)
                val tempFile = withContext(Dispatchers.IO) { downloadArtifactToTempFile(item) }
                val importResult =
                    withContext(Dispatchers.IO) {
                        packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)
                    }
                if (!importResult.startsWith("Successfully imported", ignoreCase = true)) {
                    _errorMessage.value = importResult
                } else {
                    refreshPublishableArtifacts()
                    refreshInstalledArtifacts()
                }
                tempFile.delete()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to install artifact"
                AppLogger.e(TAG, "Failed to install artifact", e)
            } finally {
                _installingIds.value = _installingIds.value - itemId
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPublishMessages() {
        _publishMessage.value = null
        _publishErrorMessage.value = null
        _publishSuccessMessage.value = null
        if (_publishProgressStage.value == PublishProgressStage.COMPLETED) {
            _publishProgressStage.value = PublishProgressStage.IDLE
        }
    }

    fun isCompatible(metadata: ArtifactMarketMetadata): Boolean {
        return runCatching {
            isAppVersionSupported(
                appVersion = currentAppVersion,
                minSupportedAppVersion = metadata.minSupportedAppVersion,
                maxSupportedAppVersion = metadata.maxSupportedAppVersion
            )
        }.getOrElse { error ->
            AppLogger.e(
                TAG,
                "Failed to evaluate artifact compatibility for current=$currentAppVersion, min=${metadata.minSupportedAppVersion}, max=${metadata.maxSupportedAppVersion}",
                error
            )
            false
        }
    }

    fun supportedVersionLabel(metadata: ArtifactMarketMetadata): String {
        return runCatching {
            formatSupportedAppVersions(
                minSupportedAppVersion = metadata.minSupportedAppVersion,
                maxSupportedAppVersion = metadata.maxSupportedAppVersion
            )
        }.getOrElse { error ->
            AppLogger.e(
                TAG,
                "Failed to format supported app versions for min=${metadata.minSupportedAppVersion}, max=${metadata.maxSupportedAppVersion}",
                error
            )
            buildString {
                append("Invalid")
                val rawParts =
                    listOfNotNull(
                        metadata.minSupportedAppVersion?.takeIf { it.isNotBlank() }?.let { "min=$it" },
                        metadata.maxSupportedAppVersion?.takeIf { it.isNotBlank() }?.let { "max=$it" }
                    )
                if (rawParts.isNotEmpty()) {
                    append(" (")
                    append(rawParts.joinToString(", "))
                    append(")")
                }
            }
        }
    }

    private fun executePublish(request: PublishArtifactRequest, allowCreateForgeRepo: Boolean) {
        viewModelScope.launch {
            _publishErrorMessage.value = null
            _publishSuccessMessage.value = null
            _registrationRetryAvailable.value = false
            pendingMarketRegistrationPayload = null
            pendingPublishRequest = request

            forgePublishService.publishArtifact(
                request = request,
                allowCreateForgeRepo = allowCreateForgeRepo,
                onProgress = { stage ->
                    _publishProgressStage.value = stage
                    _publishMessage.value = stageMessage(stage)
                }
            ).fold(
                onSuccess = { result ->
                    when (result) {
                        is PublishAttemptResult.NeedsForgeInitialization -> {
                            _requiresForgeInitialization.value = true
                            _publishProgressStage.value = PublishProgressStage.IDLE
                            _publishMessage.value = null
                        }

                        is PublishAttemptResult.Success -> {
                            pendingPublishRequest = null
                            _publishProgressStage.value = PublishProgressStage.COMPLETED
                            _publishSuccessMessage.value = buildSuccessMessage(result.forgeRepo, result.payload)
                            loadMarketData()
                            loadUserPublishedArtifacts()
                        }

                        is PublishAttemptResult.RegistrationRetryRequired -> {
                            pendingMarketRegistrationPayload = result.payload
                            _registrationRetryAvailable.value = true
                            _publishProgressStage.value = PublishProgressStage.IDLE
                            _publishErrorMessage.value = result.errorMessage
                        }
                    }
                },
                onFailure = { error ->
                    _publishProgressStage.value = PublishProgressStage.IDLE
                    _publishErrorMessage.value = formatPublishErrorMessage(error.message ?: "Publish failed")
                    AppLogger.e(TAG, "Failed to publish artifact", error)
                }
            )
        }
    }

    private suspend fun searchOpenIssues(query: String): Result<List<ArtifactMarketItem>> {
        return aggregateResults { service -> service.searchOpenIssues(rawQuery = query, page = 1) }
            .map { issues -> issues.mapNotNull(::toArtifactMarketItem) }
    }

    private suspend fun loadBrowsePages(reset: Boolean) {
        val loadedItems = mutableListOf<ArtifactMarketItem>()
        var firstError: Throwable? = null

        for (type in supportedTypes) {
            val nextPage =
                if (reset) {
                    1
                } else {
                    val totalPages = totalBrowsePages[type] ?: Int.MAX_VALUE
                    val candidate = (currentBrowsePages[type] ?: 0) + 1
                    if (candidate > totalPages) continue
                    candidate
                }

            marketStatsApiService.getRankPage(
                type = type.toMarketStatsType().wireValue,
                metric = _sortOption.value.toRankMetric(),
                page = nextPage
            ).fold(
                onSuccess = { page ->
                    currentBrowsePages[type] = page.page
                    totalBrowsePages[type] = page.totalPages.coerceAtLeast(1)
                    page.items.forEach { entry ->
                        _marketStats.updateMarketEntryStats(entry.id) {
                            entry.toMarketEntryStats()
                        }
                    }
                    loadedItems += page.items.mapNotNull { entry -> toArtifactMarketItem(entry) }
                },
                onFailure = { error ->
                    if (firstError == null) {
                        firstError = error
                    }
                    AppLogger.e(
                        TAG,
                        "Failed to load ${type.wireValue} market browse page $nextPage",
                        error
                    )
                }
            )
        }

        if (loadedItems.isNotEmpty()) {
            val mergedItems =
                if (reset) {
                    loadedItems
                } else {
                    _marketItems.value + loadedItems
                }
            _marketItems.value = mergedItems.distinctBy { it.issue.id }
        } else if (reset) {
            _marketItems.value = emptyList()
        }

        _hasMore.value =
            supportedTypes.any { type ->
                val currentPage = currentBrowsePages[type] ?: 0
                val totalPages = totalBrowsePages[type] ?: 0
                totalPages > 0 && currentPage < totalPages
            }

        firstError?.let { error ->
            _errorMessage.value = error.message ?: "Failed to load market data"
        }
    }

    private suspend fun aggregateResults(
        request: suspend (GitHubIssueMarketService) -> Result<List<GitHubIssue>>
    ): Result<List<GitHubIssue>> {
        val aggregated = mutableListOf<GitHubIssue>()
        for (type in supportedTypes) {
            request(marketService(type)).fold(
                onSuccess = { aggregated += it },
                onFailure = { return Result.failure(it) }
            )
        }
        return Result.success(
            aggregated
                .distinctBy { it.id }
                .sortedByDescending { it.updated_at }
        )
    }

    private fun stageMessage(stage: PublishProgressStage): String? {
        return when (stage) {
            PublishProgressStage.IDLE -> null
            PublishProgressStage.VALIDATING -> "正在校验本地条目"
            PublishProgressStage.ENSURING_REPO -> "正在检查 HermesForge 仓库"
            PublishProgressStage.CREATING_RELEASE -> "正在创建或更新 GitHub Release"
            PublishProgressStage.UPLOADING_ASSET -> "正在上传发布资源"
            PublishProgressStage.REGISTERING_MARKET -> "正在登记市场条目"
            PublishProgressStage.COMPLETED -> "发布完成"
        }
    }

    private fun buildSuccessMessage(forgeRepo: ForgeRepoInfo, payload: MarketRegistrationPayload): String {
        return buildString {
            append("已发布「${payload.displayName}」到 ${forgeRepo.repoName}")
            append("\n类型: ${payload.type.wireValue}")
            append("\nRelease Tag: ${payload.releaseTag}")
            append("\n资源文件: ${payload.assetName}")
            append("\n支持软件版本: ${formatSupportedAppVersions(payload.minSupportedAppVersion, payload.maxSupportedAppVersion)}")
        }
    }

    private fun formatPublishErrorMessage(rawMessage: String): String {
        val message = rawMessage.trim()
        return when {
            message.contains("Repository is empty", ignoreCase = true) ->
                "发布仓库还是空的，GitHub 无法创建 Release。现在新仓库会自动初始化；如果这是旧的空仓库，请重试一次。"

            message.contains("Validation Failed", ignoreCase = true) ->
                "GitHub 拒绝了这次发布请求，请检查仓库状态和发布信息。"

            message.startsWith("HTTP ", ignoreCase = true) ->
                message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "发布失败" }

            else -> message
        }
    }

    private fun downloadArtifactToTempFile(item: ArtifactMarketItem): File {
        val downloadDir = File(context.cacheDir, "market_downloads")
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw IllegalStateException("Failed to create market download cache")
        }

        val targetFile = File(downloadDir, item.metadata.assetName)
        val connection = URL(item.metadata.downloadUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.requestMethod = "GET"

        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download failed: HTTP $code")
            }

            val inputStream = connection.inputStream ?: throw IllegalStateException("Empty download stream")
            writeStreamToFile(inputStream, targetFile)
        } finally {
            connection.disconnect()
        }

        val actualSha256 = sha256Hex(targetFile)
        if (!actualSha256.equals(item.metadata.sha256, ignoreCase = true)) {
            targetFile.delete()
            throw IllegalStateException("Downloaded file sha256 mismatch")
        }

        return targetFile
    }

    private suspend fun refreshMarketStats() {
        _marketStats.value =
            loadMarketStatsMap(
                marketStatsApiService = marketStatsApiService,
                types = supportedTypes.map { it.toMarketStatsType() },
                logTag = TAG
            )
    }

    private suspend fun trackArtifactDownload(item: ArtifactMarketItem) {
        val statsType = resolveArtifactMarketStatsType(item.metadata) ?: return
        val entryId = resolveArtifactMarketEntryId(item.metadata)
        val targetUrl =
            resolveMarketDownloadTarget(
                preferredUrl = item.metadata.downloadUrl,
                fallbackUrl = item.issue.html_url
            )

        marketStatsApiService.trackDownload(
            type = statsType.wireValue,
            id = entryId,
            targetUrl = targetUrl
        ).onSuccess {
            _marketStats.updateMarketEntryStats(entryId) { current ->
                current.copy(downloads = current.downloads + 1)
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to track artifact download for $entryId: ${error.message}")
        }
    }

    private fun writeStreamToFile(inputStream: InputStream, targetFile: File) {
        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun marketService(type: PublishArtifactType): GitHubIssueMarketService {
        return marketServices.getValue(type)
    }

    private fun inferArtifactType(isToolPkg: Boolean, fileExtension: String): PublishArtifactType? {
        val normalizedExtension = fileExtension.lowercase()
        return when {
            isToolPkg && normalizedExtension == "toolpkg" -> PublishArtifactType.PACKAGE
            !isToolPkg && normalizedExtension != "toolpkg" -> PublishArtifactType.SCRIPT
            else -> null
        }
    }

    private fun artifactTypeFromIssue(issue: GitHubIssue): PublishArtifactType? {
        return ArtifactIssueParser.parseArtifactInfo(issue).type
    }

    private fun loadAvatarCacheFromPrefs() {
        try {
            val cachedAvatars =
                avatarCachePrefs.all.mapNotNull { (key, value) ->
                    if (value is String) key to value else null
                }.toMap()
            if (cachedAvatars.isNotEmpty()) {
                _userAvatarCache.value = cachedAvatars
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load avatar cache from preferences", e)
        }
    }

    private fun saveAvatarToPrefs(username: String, avatarUrl: String) {
        try {
            avatarCachePrefs.edit().putString(username, avatarUrl).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save avatar to preferences", e)
        }
    }

    class Factory(
        private val context: Context,
        private val scope: ArtifactMarketScope
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtifactMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ArtifactMarketViewModel(context, scope) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "ArtifactMarketViewModel"
    }
}
