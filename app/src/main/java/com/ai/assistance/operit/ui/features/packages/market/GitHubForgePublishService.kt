package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PublishArtifactRequest(
    val localArtifact: LocalPublishableArtifact,
    val displayName: String,
    val description: String,
    val version: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?
)

sealed class PublishAttemptResult {
    data class NeedsForgeInitialization(
        val publisherLogin: String
    ) : PublishAttemptResult()

    data class Success(
        val issue: GitHubIssue,
        val forgeRepo: ForgeRepoInfo,
        val release: GitHubRelease,
        val asset: GitHubReleaseAsset,
        val payload: MarketRegistrationPayload
    ) : PublishAttemptResult()

    data class RegistrationRetryRequired(
        val payload: MarketRegistrationPayload,
        val errorMessage: String
    ) : PublishAttemptResult()
}

class GitHubForgePublishService(
    private val context: Context,
    private val githubApiService: GitHubApiService
) {
    private val githubAuth = GitHubAuthPreferences.getInstance(context)

    suspend fun publishArtifact(
        request: PublishArtifactRequest,
        allowCreateForgeRepo: Boolean,
        onProgress: (PublishProgressStage) -> Unit = {}
    ): Result<PublishAttemptResult> = withContext(Dispatchers.IO) {
        try {
            if (!githubAuth.isLoggedIn()) {
                return@withContext Result.failure(Exception("GitHub login required"))
            }

            onProgress(PublishProgressStage.VALIDATING)
            val sourceFile = request.localArtifact.sourceFile
            validateSourceFile(sourceFile)
            validateSupportedAppVersions(
                minSupportedAppVersion = request.minSupportedAppVersion,
                maxSupportedAppVersion = request.maxSupportedAppVersion
            )

            val currentUser =
                githubApiService.getCurrentUser().getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            onProgress(PublishProgressStage.ENSURING_REPO)
            val forgeRepoResult =
                ensureForgeRepository(
                    publisherLogin = currentUser.login,
                    allowCreateForgeRepo = allowCreateForgeRepo
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            if (forgeRepoResult == null) {
                return@withContext Result.success(
                    PublishAttemptResult.NeedsForgeInitialization(currentUser.login)
                )
            }

            val descriptor =
                buildPublishArtifactDescriptor(
                    type = request.localArtifact.type,
                    localArtifact = request.localArtifact,
                    displayName = request.displayName,
                    description = request.description,
                    version = request.version,
                    minSupportedAppVersion = request.minSupportedAppVersion,
                    maxSupportedAppVersion = request.maxSupportedAppVersion
                )
            val releaseDescriptor = buildPublishReleaseDescriptor(descriptor)

            onProgress(PublishProgressStage.CREATING_RELEASE)
            val release =
                ensureRelease(
                    owner = currentUser.login,
                    repo = forgeRepoResult.repoName,
                    releaseDescriptor = releaseDescriptor
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            onProgress(PublishProgressStage.UPLOADING_ASSET)
            val fileBytes = sourceFile.readBytes()
            val uploadedAsset =
                uploadAssetReplacingExisting(
                    owner = currentUser.login,
                    repo = forgeRepoResult.repoName,
                    release = release,
                    descriptor = descriptor,
                    content = fileBytes
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            val payload =
                MarketRegistrationPayload(
                    type = descriptor.type,
                    normalizedId = descriptor.normalizedId,
                    publisherLogin = currentUser.login,
                    forgeRepo = forgeRepoResult.repoName,
                    releaseTag = releaseDescriptor.tagName,
                    assetName = uploadedAsset.name,
                    downloadUrl = uploadedAsset.browser_download_url,
                    sha256 = sha256Hex(fileBytes),
                    version = descriptor.version,
                    displayName = descriptor.displayName,
                    description = descriptor.description,
                    sourceFileName = sourceFile.name,
                    minSupportedAppVersion = descriptor.minSupportedAppVersion,
                    maxSupportedAppVersion = descriptor.maxSupportedAppVersion
                )

            onProgress(PublishProgressStage.REGISTERING_MARKET)
            val issue =
                registerOrUpdateMarketIssue(payload).getOrElse { error ->
                    return@withContext Result.success(
                        PublishAttemptResult.RegistrationRetryRequired(
                            payload = payload,
                            errorMessage = error.message ?: "Failed to register market entry"
                        )
                    )
                }

            onProgress(PublishProgressStage.COMPLETED)
            Result.success(
                PublishAttemptResult.Success(
                    issue = issue,
                    forgeRepo = forgeRepoResult,
                    release = release,
                    asset = uploadedAsset,
                    payload = payload
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retryMarketRegistration(
        payload: MarketRegistrationPayload
    ): Result<GitHubIssue> = withContext(Dispatchers.IO) {
        registerOrUpdateMarketIssue(payload)
    }

    private suspend fun ensureForgeRepository(
        publisherLogin: String,
        allowCreateForgeRepo: Boolean
    ): Result<ForgeRepoInfo?> {
        val existingRepo = githubApiService.getRepository(publisherLogin, OPERIT_FORGE_REPO_NAME)
        val existingRepoValue = existingRepo.getOrNull()
        if (existingRepoValue != null) {
            if (existingRepoValue.size == 0) {
                initializeForgeRepository(
                    owner = publisherLogin,
                    repo = existingRepoValue.name
                ).getOrElse { error ->
                    return Result.failure(error)
                }
            }
            return Result.success(
                ForgeRepoInfo(
                    ownerLogin = publisherLogin,
                    repoName = existingRepoValue.name,
                    htmlUrl = existingRepoValue.html_url,
                    existedBefore = true
                )
            )
        }

        val failureMessage = existingRepo.exceptionOrNull()?.message.orEmpty()
        if (!failureMessage.contains("HTTP 404")) {
            return Result.failure(existingRepo.exceptionOrNull() ?: Exception("Failed to load OperitForge"))
        }

        if (!allowCreateForgeRepo) {
            return Result.success(null)
        }

        return githubApiService.createRepository(
            name = OPERIT_FORGE_REPO_NAME,
            description = "Operit publish-only artifact repository for release assets.",
            isPrivate = false,
            autoInit = true
        ).map { repo ->
            ForgeRepoInfo(
                ownerLogin = publisherLogin,
                repoName = repo.name,
                htmlUrl = repo.html_url,
                existedBefore = false
            )
        }
    }

    private suspend fun initializeForgeRepository(
        owner: String,
        repo: String
    ): Result<Unit> {
        return githubApiService.createTextFile(
            owner = owner,
            repo = repo,
            path = "README.md",
            message = "Initialize OperitForge repository",
            textContent =
                buildString {
                    appendLine("# OperitForge")
                    appendLine()
                    appendLine("This repository stores release assets published from Operit.")
                }
        )
    }

    private suspend fun ensureRelease(
        owner: String,
        repo: String,
        releaseDescriptor: PublishReleaseDescriptor
    ): Result<GitHubRelease> {
        val existing =
            githubApiService.findReleaseByTag(owner, repo, releaseDescriptor.tagName).getOrElse { error ->
                return Result.failure(error)
            }

        return if (existing == null) {
            githubApiService.createRelease(
                owner = owner,
                repo = repo,
                tagName = releaseDescriptor.tagName,
                name = releaseDescriptor.releaseName,
                body = releaseDescriptor.releaseBody,
                draft = false,
                prerelease = false
            )
        } else {
            githubApiService.updateRelease(
                owner = owner,
                repo = repo,
                releaseId = existing.id,
                name = releaseDescriptor.releaseName,
                body = releaseDescriptor.releaseBody,
                draft = false,
                prerelease = false
            )
        }
    }

    private suspend fun uploadAssetReplacingExisting(
        owner: String,
        repo: String,
        release: GitHubRelease,
        descriptor: PublishArtifactDescriptor,
        content: ByteArray
    ): Result<GitHubReleaseAsset> {
        release.assets
            .firstOrNull { it.name.equals(descriptor.assetName, ignoreCase = true) }
            ?.let { existingAsset ->
                githubApiService.deleteReleaseAsset(owner, repo, existingAsset.id).getOrElse { error ->
                    return Result.failure(error)
                }
            }

        return githubApiService.uploadReleaseAsset(
            owner = owner,
            repo = repo,
            releaseId = release.id,
            assetName = descriptor.assetName,
            contentType = descriptor.contentType,
            content = content
        )
    }

    private suspend fun registerOrUpdateMarketIssue(
        payload: MarketRegistrationPayload
    ): Result<GitHubIssue> {
        val marketService =
            GitHubIssueMarketService(
                githubApiService = githubApiService,
                definition = payload.type.marketDefinition()
            )
        val body = buildArtifactMarketIssueBody(payload)

        val existingIssues =
            marketService.getUserPublishedIssues(
                creator = payload.publisherLogin,
                perPage = 100
            ).getOrElse { error ->
                return Result.failure(error)
            }

        val existingIssue =
            existingIssues.firstOrNull { issue ->
                val metadata = issue.body?.let(::parseArtifactMarketMetadata)
                metadata?.type == payload.type.wireValue &&
                    metadata.normalizedId == payload.normalizedId
            }

        return if (existingIssue != null) {
            marketService.updateIssueContent(
                issueNumber = existingIssue.number,
                title = payload.displayName,
                body = body
            )
        } else {
            marketService.createIssue(
                title = payload.displayName,
                body = body
            )
        }
    }

    private fun validateSourceFile(file: File) {
        require(file.exists()) { "Source file not found: ${file.absolutePath}" }
        require(file.isFile) { "Source path is not a file: ${file.absolutePath}" }
        require(file.canRead()) { "Cannot read source file: ${file.absolutePath}" }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
