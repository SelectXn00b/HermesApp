package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.MarketRankIssueEntryResponse
import com.ai.assistance.operit.ui.features.packages.utils.IssueBodyMetadataParser
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

const val OPERIT_MARKET_OWNER = "AAswordman"
const val OPERIT_FORGE_REPO_NAME = "HermesForge"

private const val ARTIFACT_MARKET_JSON_PREFIX = "<!-- operit-market-json: "
private const val ARTIFACT_MARKET_PARSER_VERSION = "forge-v2"
private const val SCRIPT_MARKET_LABEL = "script-artifact"
private const val PACKAGE_MARKET_LABEL = "package-artifact"
private val APP_VERSION_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:\+(\d+))?$""")
private val MARKET_ARTIFACT_JSON =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

private data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int?
) {
    override fun toString(): String {
        return build?.let { "$major.$minor.$patch+$it" } ?: "$major.$minor.$patch"
    }
}

enum class PublishArtifactType(
    val wireValue: String,
    val marketRepo: String,
    val releaseTagPrefix: String,
    val titleLabel: String,
    val marketLabel: String,
    val marketLabelColor: String,
    val marketLabelDescription: String
) {
    SCRIPT(
        wireValue = "script",
        marketRepo = "OperitScriptMarket",
        releaseTagPrefix = "script",
        titleLabel = "Script",
        marketLabel = SCRIPT_MARKET_LABEL,
        marketLabelColor = "0e8a16",
        marketLabelDescription = "Published script artifacts managed by Operit."
    ),
    PACKAGE(
        wireValue = "package",
        marketRepo = "OperitPackageMarket",
        releaseTagPrefix = "package",
        titleLabel = "Package",
        marketLabel = PACKAGE_MARKET_LABEL,
        marketLabelColor = "1d76db",
        marketLabelDescription = "Published package artifacts managed by Operit."
    );

    fun marketDefinition(): GitHubIssueMarketDefinition {
        return GitHubIssueMarketDefinition(
            owner = OPERIT_MARKET_OWNER,
            repo = marketRepo,
            label = marketLabel,
            pageSize = 50,
            labelColor = marketLabelColor,
            labelDescription = marketLabelDescription
        )
    }

    companion object {
        fun fromWireValue(value: String?): PublishArtifactType? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}

enum class ArtifactMarketScope {
    ALL,
    SCRIPT_ONLY,
    PACKAGE_ONLY;

    fun supportedTypes(): List<PublishArtifactType> {
        return when (this) {
            ALL -> PublishArtifactType.entries
            SCRIPT_ONLY -> listOf(PublishArtifactType.SCRIPT)
            PACKAGE_ONLY -> listOf(PublishArtifactType.PACKAGE)
        }
    }
}

enum class PublishProgressStage {
    IDLE,
    VALIDATING,
    ENSURING_REPO,
    CREATING_RELEASE,
    UPLOADING_ASSET,
    REGISTERING_MARKET,
    COMPLETED
}

data class ForgeRepoInfo(
    val ownerLogin: String,
    val repoName: String,
    val htmlUrl: String,
    val existedBefore: Boolean
)

data class LocalPublishableArtifact(
    val type: PublishArtifactType,
    val packageName: String,
    val displayName: String,
    val description: String,
    val sourceFile: File,
    val inferredVersion: String? = null
)

data class PublishArtifactDescriptor(
    val type: PublishArtifactType,
    val normalizedId: String,
    val displayName: String,
    val description: String,
    val version: String,
    val sourceFile: File,
    val contentType: String,
    val assetName: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?
)

data class PublishReleaseDescriptor(
    val tagName: String,
    val releaseName: String,
    val releaseBody: String
)

data class MarketRegistrationPayload(
    val type: PublishArtifactType,
    val normalizedId: String,
    val publisherLogin: String,
    val forgeRepo: String,
    val releaseTag: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val version: String,
    val displayName: String,
    val description: String,
    val sourceFileName: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?
)

data class ArtifactMarketItem(
    val issue: GitHubIssue,
    val metadata: ArtifactMarketMetadata
)

@Serializable
data class ArtifactMarketMetadata(
    val type: String,
    val normalizedId: String,
    val publisherLogin: String,
    val forgeRepo: String,
    val releaseTag: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val version: String,
    val displayName: String,
    val description: String,
    val sourceFileName: String = "",
    val minSupportedAppVersion: String? = null,
    val maxSupportedAppVersion: String? = null
)

fun normalizeMarketArtifactId(raw: String): String {
    val normalized =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    return normalized.ifBlank { "artifact" }
}

fun parseArtifactMarketMetadata(body: String): ArtifactMarketMetadata? {
    return IssueBodyMetadataParser.parseCommentJson(
        body = body,
        prefix = ARTIFACT_MARKET_JSON_PREFIX,
        tag = "ArtifactMarketMetadata",
        metadataName = "artifact market metadata"
    )
}

fun toArtifactMarketItem(issue: GitHubIssue): ArtifactMarketItem? {
    val body = issue.body ?: return null
    val metadata = parseArtifactMarketMetadata(body) ?: return null
    return ArtifactMarketItem(issue = issue, metadata = metadata)
}

fun toArtifactMarketItem(entry: MarketRankIssueEntryResponse): ArtifactMarketItem? {
    val metadata =
        decodeArtifactMarketMetadata(entry.metadata)
            ?: entry.issue.body?.let(::parseArtifactMarketMetadata)
            ?: return null
    return ArtifactMarketItem(issue = entry.issue, metadata = metadata)
}

private fun decodeArtifactMarketMetadata(metadata: JsonElement?): ArtifactMarketMetadata? {
    return metadata?.let {
        runCatching {
            MARKET_ARTIFACT_JSON.decodeFromJsonElement<ArtifactMarketMetadata>(it)
        }.getOrNull()
    }
}

fun buildPublishArtifactDescriptor(
    type: PublishArtifactType,
    localArtifact: LocalPublishableArtifact,
    displayName: String,
    description: String,
    version: String,
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): PublishArtifactDescriptor {
    val normalizedId = normalizeMarketArtifactId(localArtifact.packageName)
    val cleanVersion =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .ifBlank { "1.0.0" }
    val extension = localArtifact.sourceFile.extension.lowercase().ifBlank { "bin" }
    val assetName = "$normalizedId-v$cleanVersion.$extension"
    return PublishArtifactDescriptor(
        type = type,
        normalizedId = normalizedId,
        displayName = displayName.trim().ifBlank { localArtifact.displayName },
        description = description.trim().ifBlank { localArtifact.description },
        version = cleanVersion,
        sourceFile = localArtifact.sourceFile,
        contentType = inferArtifactContentType(type, extension),
        assetName = assetName,
        minSupportedAppVersion = normalizeAppVersionOrNull(minSupportedAppVersion),
        maxSupportedAppVersion = normalizeAppVersionOrNull(maxSupportedAppVersion)
    )
}

fun buildPublishReleaseDescriptor(
    descriptor: PublishArtifactDescriptor
): PublishReleaseDescriptor {
    val tagName = "${descriptor.type.releaseTagPrefix}-${descriptor.normalizedId}-v${descriptor.version}"
    return PublishReleaseDescriptor(
        tagName = tagName,
        releaseName = "${descriptor.displayName} v${descriptor.version}",
        releaseBody =
            buildString {
                appendLine("${descriptor.type.titleLabel} artifact published by HermesForge.")
                appendLine()
                appendLine("Display name: ${descriptor.displayName}")
                appendLine("Normalized id: ${descriptor.normalizedId}")
                appendLine("Version: ${descriptor.version}")
                appendLine(
                    "Supported app versions: ${formatSupportedAppVersions(descriptor.minSupportedAppVersion, descriptor.maxSupportedAppVersion)}"
                )
            }
    )
}

fun buildArtifactMarketMetadata(
    payload: MarketRegistrationPayload
): ArtifactMarketMetadata {
    return ArtifactMarketMetadata(
        type = payload.type.wireValue,
        normalizedId = payload.normalizedId,
        publisherLogin = payload.publisherLogin,
        forgeRepo = payload.forgeRepo,
        releaseTag = payload.releaseTag,
        assetName = payload.assetName,
        downloadUrl = payload.downloadUrl,
        sha256 = payload.sha256,
        version = payload.version,
        displayName = payload.displayName,
        description = payload.description,
        sourceFileName = payload.sourceFileName,
        minSupportedAppVersion = payload.minSupportedAppVersion,
        maxSupportedAppVersion = payload.maxSupportedAppVersion
    )
}

fun buildArtifactMarketIssueBody(payload: MarketRegistrationPayload): String {
    val metadata = buildArtifactMarketMetadata(payload)
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    val metadataJson = json.encodeToString(metadata)
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    return buildString {
        appendLine("$ARTIFACT_MARKET_JSON_PREFIX$metadataJson -->")
        appendLine("<!-- operit-parser-version: $ARTIFACT_MARKET_PARSER_VERSION -->")
        appendLine()
        appendLine("## ${payload.type.titleLabel}")
        appendLine()
        appendLine(payload.description)
        appendLine()
        appendLine("## Artifact")
        appendLine()
        appendLine("- Publisher: `${payload.publisherLogin}`")
        appendLine("- Forge repo: `${payload.forgeRepo}`")
        appendLine("- Release tag: `${payload.releaseTag}`")
        appendLine("- Asset: `${payload.assetName}`")
        appendLine("- SHA-256: `${payload.sha256}`")
        appendLine("- Download: ${payload.downloadUrl}")
        appendLine()
        appendLine("## Metadata")
        appendLine()
        appendLine("| Field | Value |")
        appendLine("| --- | --- |")
        appendLine("| Type | ${payload.type.wireValue} |")
        appendLine("| Normalized ID | ${payload.normalizedId} |")
        appendLine("| Version | ${payload.version} |")
        appendLine("| Supported app versions | ${formatSupportedAppVersions(payload.minSupportedAppVersion, payload.maxSupportedAppVersion)} |")
        appendLine("| Source file | ${payload.sourceFileName.ifBlank { "-" }} |")
        appendLine("| Updated at | $timestamp |")
    }
}

fun normalizeAppVersionOrNull(value: String?): String? {
    return parseAppVersionOrNull(value)?.toString()
}

fun validateSupportedAppVersions(
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
) {
    val normalizedMin = normalizeAppVersionOrNull(minSupportedAppVersion)
    val normalizedMax = normalizeAppVersionOrNull(maxSupportedAppVersion)
    if (normalizedMin != null && normalizedMax != null) {
        require(compareAppVersions(normalizedMin, normalizedMax) <= 0) {
            "Minimum supported app version cannot be greater than maximum supported app version"
        }
    }
}

fun compareAppVersions(left: String, right: String): Int {
    val leftVersion = requireNotNull(parseAppVersionOrNull(left))
    val rightVersion = requireNotNull(parseAppVersionOrNull(right))

    if (leftVersion.major != rightVersion.major) {
        return leftVersion.major.compareTo(rightVersion.major)
    }
    if (leftVersion.minor != rightVersion.minor) {
        return leftVersion.minor.compareTo(rightVersion.minor)
    }
    if (leftVersion.patch != rightVersion.patch) {
        return leftVersion.patch.compareTo(rightVersion.patch)
    }

    val leftBuild = leftVersion.build ?: 0
    val rightBuild = rightVersion.build ?: 0
    return leftBuild.compareTo(rightBuild)
}

fun isAppVersionSupported(
    appVersion: String,
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): Boolean {
    val normalizedCurrent = normalizeAppVersionOrNull(appVersion) ?: return true
    val normalizedMin = normalizeAppVersionOrNull(minSupportedAppVersion)
    val normalizedMax = normalizeAppVersionOrNull(maxSupportedAppVersion)
    if (normalizedMin != null && compareAppVersions(normalizedCurrent, normalizedMin) < 0) {
        return false
    }
    if (normalizedMax != null && compareAppVersions(normalizedCurrent, normalizedMax) > 0) {
        return false
    }
    return true
}

fun formatSupportedAppVersions(
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): String {
    val normalizedMin = normalizeAppVersionOrNull(minSupportedAppVersion)
    val normalizedMax = normalizeAppVersionOrNull(maxSupportedAppVersion)
    return when {
        normalizedMin != null && normalizedMax != null -> "$normalizedMin - $normalizedMax"
        normalizedMin != null -> ">= $normalizedMin"
        normalizedMax != null -> "<= $normalizedMax"
        else -> "Any"
    }
}

private fun inferArtifactContentType(
    type: PublishArtifactType,
    extension: String
): String {
    return when {
        type == PublishArtifactType.PACKAGE && extension == "toolpkg" -> "application/zip"
        extension == "js" -> "application/javascript"
        extension == "ts" -> "text/plain"
        extension == "json" -> "application/json"
        extension == "hjson" -> "application/json"
        extension == "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}

private fun parseAppVersionOrNull(value: String?): AppVersion? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null

    val match =
        APP_VERSION_REGEX.matchEntire(normalized)
            ?: throw IllegalArgumentException("App version must use x.y.z or x.y.z+n format")

    return AppVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        build = match.groupValues[4].takeIf { it.isNotBlank() }?.toInt()
    )
}
