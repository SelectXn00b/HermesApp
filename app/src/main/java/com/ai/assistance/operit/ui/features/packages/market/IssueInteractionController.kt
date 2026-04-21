package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.api.GitHubReaction
import com.ai.assistance.operit.data.api.GitHubRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CommentPostSuccessBehavior {
    APPEND_TO_CACHE,
    RELOAD_FROM_SERVER
}

data class IssueInteractionMessages(
    val commentLoadFailed: (String) -> String,
    val commentLoadError: (String) -> String,
    val commentPostFailed: (String) -> String,
    val commentPostError: (String) -> String,
    val reactionFailed: (String) -> String,
    val reactionError: (String) -> String,
    val commentPostSuccess: String? = null,
    val reactionSuccess: String? = null
)

class IssueInteractionController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val marketService: GitHubIssueMarketService,
    private val logTag: String,
    private val onError: (String) -> Unit,
    private val messages: IssueInteractionMessages,
    private val avatarCachePrefs: SharedPreferences? = null
) {
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

    init {
        loadAvatarCacheFromPrefs()
    }

    fun clearReactionsCache() {
        _issueReactions.value = emptyMap()
    }

    fun loadIssueComments(issueNumber: Int, perPage: Int = 50) {
        scope.launch {
            try {
                _isLoadingComments.value = _isLoadingComments.value + issueNumber

                val result = marketService.getIssueComments(
                    issueNumber = issueNumber,
                    perPage = perPage
                )

                result.fold(
                    onSuccess = { comments ->
                        val currentComments = _issueComments.value.toMutableMap()
                        currentComments[issueNumber] = comments
                        _issueComments.value = currentComments
                        AppLogger.d(logTag, "Successfully loaded ${comments.size} comments for issue #$issueNumber")
                    },
                    onFailure = { error ->
                        onError(messages.commentLoadFailed(error.message ?: ""))
                        AppLogger.e(logTag, "Failed to load comments for issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                onError(messages.commentLoadError(e.message ?: ""))
                AppLogger.e(logTag, "Exception while loading comments for issue #$issueNumber", e)
            } finally {
                _isLoadingComments.value = _isLoadingComments.value - issueNumber
            }
        }
    }

    fun postIssueComment(
        issueNumber: Int,
        body: String,
        successBehavior: CommentPostSuccessBehavior = CommentPostSuccessBehavior.APPEND_TO_CACHE,
        perPage: Int = 50
    ) {
        scope.launch {
            try {
                _isPostingComment.value = _isPostingComment.value + issueNumber

                val result = marketService.createIssueComment(
                    issueNumber = issueNumber,
                    body = body
                )

                result.fold(
                    onSuccess = { newComment ->
                        when (successBehavior) {
                            CommentPostSuccessBehavior.APPEND_TO_CACHE -> {
                                val currentComments = _issueComments.value.toMutableMap()
                                val existingComments = currentComments[issueNumber] ?: emptyList()
                                currentComments[issueNumber] = existingComments + newComment
                                _issueComments.value = currentComments
                            }

                            CommentPostSuccessBehavior.RELOAD_FROM_SERVER -> {
                                loadIssueComments(issueNumber, perPage)
                            }
                        }

                        messages.commentPostSuccess?.let { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        AppLogger.d(logTag, "Successfully posted comment to issue #$issueNumber")
                    },
                    onFailure = { error ->
                        onError(messages.commentPostFailed(error.message ?: ""))
                        AppLogger.e(logTag, "Failed to post comment to issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                onError(messages.commentPostError(e.message ?: ""))
                AppLogger.e(logTag, "Exception while posting comment to issue #$issueNumber", e)
            } finally {
                _isPostingComment.value = _isPostingComment.value - issueNumber
            }
        }
    }

    fun fetchUserAvatar(username: String) {
        if (username.isBlank() || _userAvatarCache.value.containsKey(username)) {
            return
        }

        scope.launch {
            try {
                val result = marketService.getUser(username)
                result.fold(
                    onSuccess = { user ->
                        val currentCache = _userAvatarCache.value.toMutableMap()
                        currentCache[username] = user.avatarUrl
                        _userAvatarCache.value = currentCache
                        saveAvatarToPrefs(username, user.avatarUrl)
                        AppLogger.d(logTag, "Cached avatar for user: $username")
                    },
                    onFailure = { error ->
                        AppLogger.w(logTag, "Failed to fetch avatar for user $username: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(logTag, "Exception while fetching avatar for user $username", e)
            }
        }
    }

    fun fetchRepositoryInfo(repositoryUrl: String) {
        if (repositoryUrl.isBlank() || _repositoryCache.value.containsKey(repositoryUrl)) {
            return
        }

        scope.launch {
            try {
                val result = marketService.getRepositoryByUrl(repositoryUrl)
                result.fold(
                    onSuccess = { repository ->
                        val currentCache = _repositoryCache.value.toMutableMap()
                        currentCache[repositoryUrl] = repository
                        _repositoryCache.value = currentCache
                        AppLogger.d(logTag, "Successfully fetched repository info for $repositoryUrl")
                    },
                    onFailure = { error ->
                        AppLogger.w(logTag, "Failed to fetch repository info for $repositoryUrl: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.w(logTag, "Exception while fetching repository info for $repositoryUrl", e)
            }
        }
    }

    fun loadIssueReactions(issueNumber: Int, force: Boolean = false) {
        if (issueNumber in _isLoadingReactions.value) {
            return
        }

        if (!force && _issueReactions.value.containsKey(issueNumber)) {
            AppLogger.d(logTag, "Reactions for issue #$issueNumber already in cache.")
            return
        }

        scope.launch {
            try {
                _isLoadingReactions.value = _isLoadingReactions.value + issueNumber

                val result = marketService.getIssueReactions(issueNumber)
                result.fold(
                    onSuccess = { reactions ->
                        val currentReactions = _issueReactions.value.toMutableMap()
                        currentReactions[issueNumber] = reactions
                        _issueReactions.value = currentReactions
                        AppLogger.d(logTag, "Successfully loaded reactions for issue #$issueNumber")
                    },
                    onFailure = { error ->
                        AppLogger.e(logTag, "Failed to load reactions for issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(logTag, "Exception while loading reactions for issue #$issueNumber", e)
            } finally {
                _isLoadingReactions.value = _isLoadingReactions.value - issueNumber
            }
        }
    }

    fun addReactionToIssue(issueNumber: Int, reactionType: String) {
        if (issueNumber in _isReacting.value) {
            return
        }

        scope.launch {
            try {
                _isReacting.value = _isReacting.value + issueNumber

                val result = marketService.createIssueReaction(
                    issueNumber = issueNumber,
                    content = reactionType
                )

                result.fold(
                    onSuccess = { newReaction ->
                        val currentReactions = _issueReactions.value.toMutableMap()
                        val existingReactions = currentReactions[issueNumber] ?: emptyList()
                        currentReactions[issueNumber] = existingReactions + newReaction
                        _issueReactions.value = currentReactions

                        messages.reactionSuccess?.let { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        AppLogger.d(logTag, "Successfully added reaction to issue #$issueNumber")
                    },
                    onFailure = { error ->
                        onError(messages.reactionFailed(error.message ?: ""))
                        AppLogger.e(logTag, "Failed to add reaction to issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                onError(messages.reactionError(e.message ?: ""))
                AppLogger.e(logTag, "Exception while adding reaction to issue #$issueNumber", e)
            } finally {
                _isReacting.value = _isReacting.value - issueNumber
            }
        }
    }

    fun getCommentsForIssue(issueNumber: Int): List<GitHubComment> {
        return _issueComments.value[issueNumber] ?: emptyList()
    }

    fun isLoadingCommentsForIssue(issueNumber: Int): Boolean {
        return issueNumber in _isLoadingComments.value
    }

    fun isPostingCommentForIssue(issueNumber: Int): Boolean {
        return issueNumber in _isPostingComment.value
    }

    fun getUserAvatarUrl(username: String): String? {
        return _userAvatarCache.value[username]
    }

    fun getReactionsForIssue(issueNumber: Int): List<GitHubReaction> {
        return _issueReactions.value[issueNumber] ?: emptyList()
    }

    fun isLoadingReactionsForIssue(issueNumber: Int): Boolean {
        return issueNumber in _isLoadingReactions.value
    }

    fun isReactingToIssue(issueNumber: Int): Boolean {
        return issueNumber in _isReacting.value
    }

    fun getRepositoryInfo(repositoryUrl: String): GitHubRepository? {
        return _repositoryCache.value[repositoryUrl]
    }

    fun getReactionCount(issueNumber: Int, reactionType: String): Int {
        return getReactionsForIssue(issueNumber).count { it.content == reactionType }
    }

    fun hasUserReacted(issueNumber: Int, reactionType: String, currentUserLogin: String?): Boolean {
        if (currentUserLogin.isNullOrBlank()) return false
        return getReactionsForIssue(issueNumber).any {
            it.content == reactionType && it.user.login == currentUserLogin
        }
    }

    private fun loadAvatarCacheFromPrefs() {
        val prefs = avatarCachePrefs ?: return
        try {
            val cachedAvatars = prefs.all.mapNotNull { (key, value) ->
                if (value is String) key to value else null
            }.toMap()

            if (cachedAvatars.isNotEmpty()) {
                _userAvatarCache.value = cachedAvatars
                AppLogger.d(logTag, "Loaded ${cachedAvatars.size} avatar URLs from persistent cache")
            }

            if (cachedAvatars.size > 500) {
                cleanupAvatarCache()
            }
        } catch (e: Exception) {
            AppLogger.e(logTag, "Failed to load avatar cache from preferences", e)
        }
    }

    private fun cleanupAvatarCache() {
        val prefs = avatarCachePrefs ?: return
        try {
            val allEntries = prefs.all
            if (allEntries.size > 500) {
                val editor = prefs.edit()
                allEntries.keys.take(allEntries.size / 2).forEach { key ->
                    editor.remove(key)
                }
                editor.apply()
                AppLogger.d(logTag, "Cleaned up avatar cache, removed ${allEntries.size / 2} entries")
            }
        } catch (e: Exception) {
            AppLogger.e(logTag, "Failed to cleanup avatar cache", e)
        }
    }

    private fun saveAvatarToPrefs(username: String, avatarUrl: String) {
        val prefs = avatarCachePrefs ?: return
        try {
            prefs.edit().putString(username, avatarUrl).apply()
        } catch (e: Exception) {
            AppLogger.e(logTag, "Failed to save avatar to preferences", e)
        }
    }
}
