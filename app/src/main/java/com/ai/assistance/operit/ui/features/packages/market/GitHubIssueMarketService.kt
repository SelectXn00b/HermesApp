package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubReaction
import com.ai.assistance.operit.data.api.GitHubRepository
import com.ai.assistance.operit.data.preferences.GitHubUser

data class GitHubIssueMarketDefinition(
    val owner: String,
    val repo: String,
    val label: String,
    val pageSize: Int = 50,
    val labelColor: String = "1d76db",
    val labelDescription: String? = null
)

class GitHubIssueMarketService(
    private val githubApiService: GitHubApiService,
    private val definition: GitHubIssueMarketDefinition
) {
    fun buildQualifiedSearchQuery(rawQuery: String): String {
        return buildString {
            append(rawQuery)
            append(" repo:")
            append(definition.owner)
            append("/")
            append(definition.repo)
            append(" is:issue is:open")
            if (definition.label.isNotBlank()) {
                append(" label:")
                append(definition.label)
            }
        }
    }

    fun isLoggedOutRateLimit(errorMessage: String, isLoggedIn: Boolean): Boolean {
        if (isLoggedIn) return false

        val normalized = errorMessage.lowercase()
        return normalized.contains("http 403") || normalized.contains("rate")
    }

    suspend fun searchOpenIssues(
        rawQuery: String,
        page: Int = 1
    ): Result<List<GitHubIssue>> {
        return githubApiService.searchIssues(
            query = buildQualifiedSearchQuery(rawQuery),
            sort = "updated",
            order = "desc",
            page = page,
            perPage = definition.pageSize
        )
    }

    suspend fun createIssue(
        title: String,
        body: String,
        extraLabels: List<String> = emptyList(),
        includeDefaultLabel: Boolean = true
    ): Result<GitHubIssue> {
        if (includeDefaultLabel) {
            ensureDefaultLabelExists().getOrElse { error ->
                return Result.failure(error)
            }
        }

        val labels =
            if (includeDefaultLabel) {
                (extraLabels + definition.label)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
            } else {
                extraLabels
                    .map(String::trim)
                    .filter(String::isNotBlank)
            }

        return githubApiService.createIssue(
            owner = definition.owner,
            repo = definition.repo,
            title = title,
            body = body,
            labels = labels
        )
    }

    suspend fun updateIssueContent(
        issueNumber: Int,
        title: String? = null,
        body: String? = null
    ): Result<GitHubIssue> {
        return githubApiService.updateIssue(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber,
            title = title,
            body = body
        )
    }

    suspend fun updateIssueState(
        issueNumber: Int,
        state: String
    ): Result<GitHubIssue> {
        return githubApiService.updateIssue(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber,
            state = state
        )
    }

    suspend fun getUserPublishedIssues(
        creator: String,
        fallbackWithoutLabel: Boolean = false,
        perPage: Int = 100
    ): Result<List<GitHubIssue>> {
        val labeledResult = githubApiService.getRepositoryIssues(
            owner = definition.owner,
            repo = definition.repo,
            state = "all",
            labels = definition.label.takeIf { it.isNotBlank() },
            creator = creator,
            perPage = perPage
        )

        if (!fallbackWithoutLabel) {
            return labeledResult
        }

        return labeledResult.fold(
            onSuccess = { issues ->
                if (issues.isNotEmpty()) {
                    Result.success(issues)
                } else {
                    githubApiService.getRepositoryIssues(
                        owner = definition.owner,
                        repo = definition.repo,
                        state = "all",
                        labels = null,
                        creator = creator,
                        perPage = perPage
                    )
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getIssueComments(
        issueNumber: Int,
        perPage: Int = 50
    ): Result<List<GitHubComment>> {
        return githubApiService.getIssueComments(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber,
            perPage = perPage
        )
    }

    suspend fun createIssueComment(
        issueNumber: Int,
        body: String
    ): Result<GitHubComment> {
        return githubApiService.createIssueComment(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber,
            body = body
        )
    }

    suspend fun getIssueReactions(issueNumber: Int): Result<List<GitHubReaction>> {
        return githubApiService.getIssueReactions(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber
        )
    }

    suspend fun createIssueReaction(
        issueNumber: Int,
        content: String
    ): Result<GitHubReaction> {
        return githubApiService.createIssueReaction(
            owner = definition.owner,
            repo = definition.repo,
            issueNumber = issueNumber,
            content = content
        )
    }

    suspend fun getUser(username: String): Result<GitHubUser> {
        return githubApiService.getUser(username)
    }

    suspend fun getRepositoryByUrl(repositoryUrl: String): Result<GitHubRepository> {
        val repoRef =
            parseGitHubRepositoryUrl(repositoryUrl)
                ?: return Result.failure(IllegalArgumentException("Invalid repository URL: $repositoryUrl"))

        return githubApiService.getRepository(repoRef.owner, repoRef.repo)
    }

    private data class GitHubRepoRef(
        val owner: String,
        val repo: String
    )

    private suspend fun ensureDefaultLabelExists(): Result<Unit> {
        val defaultLabel = definition.label.trim()
        if (defaultLabel.isBlank()) {
            return Result.success(Unit)
        }

        fun labelExists(labels: List<com.ai.assistance.operit.data.api.GitHubLabel>): Boolean {
            return labels.any { it.name.equals(defaultLabel, ignoreCase = true) }
        }

        val existingLabels =
            githubApiService.getRepositoryLabels(
                owner = definition.owner,
                repo = definition.repo
            ).getOrElse { error ->
                return Result.failure(error)
            }
        if (labelExists(existingLabels)) {
            return Result.success(Unit)
        }

        val createResult =
            githubApiService.createLabel(
                owner = definition.owner,
                repo = definition.repo,
                name = defaultLabel,
                color = definition.labelColor,
                description = definition.labelDescription
            )
        if (createResult.isSuccess) {
            return Result.success(Unit)
        }

        val refreshedLabels =
            githubApiService.getRepositoryLabels(
                owner = definition.owner,
                repo = definition.repo
            ).getOrElse { error ->
                return Result.failure(error)
            }
        return if (labelExists(refreshedLabels)) {
            Result.success(Unit)
        } else {
            Result.failure(createResult.exceptionOrNull() ?: Exception("Failed to create default label"))
        }
    }

    private fun parseGitHubRepositoryUrl(repositoryUrl: String): GitHubRepoRef? {
        val normalized = repositoryUrl.trim().removeSuffix(".git")
        if (normalized.isBlank()) return null

        val githubPattern = Regex("""(?:https?://)?(?:www\.)?github\.com/([^/\s]+)/([^/\s#?]+)""")
        val match = githubPattern.find(normalized) ?: return null

        return GitHubRepoRef(
            owner = match.groupValues[1],
            repo = match.groupValues[2]
        )
    }
}
