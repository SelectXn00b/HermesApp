package com.ai.assistance.operit.ui.features.packages.utils

import com.ai.assistance.operit.data.api.GitHubIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

object SkillIssueParser {
    private const val TAG = "SkillIssueParser"

    @Serializable
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    data class SkillMetadata(
        val description: String = "",
        @JsonNames("repoUrl")
        val repositoryUrl: String,
        val category: String = "",
        val tags: String = "",
        @JsonNames("version")
        val version: String = ""
    )

    data class ParsedSkillInfo(
        val title: String,
        val description: String,
        val repositoryUrl: String = "",
        val category: String = "",
        val tags: String = "",
        val version: String = "",
        val repositoryOwner: String = ""
    )

    fun parseSkillInfo(issue: GitHubIssue): ParsedSkillInfo {
        val body = issue.body
        if (body.isNullOrBlank()) {
            return ParsedSkillInfo(
                title = issue.title,
                description = "无描述信息"
            )
        }

        val metadata = parseSkillMetadata(body)
        val extractedDescription = IssueBodyDescriptionExtractor.extractHumanDescriptionFromBody(body)

        return if (metadata != null) {
            ParsedSkillInfo(
                title = issue.title,
                description = metadata.description.ifBlank { extractedDescription.ifBlank { "无描述信息" } },
                repositoryUrl = metadata.repositoryUrl,
                category = metadata.category,
                tags = metadata.tags,
                version = metadata.version,
                repositoryOwner = extractRepositoryOwner(metadata.repositoryUrl)
            )
        } else {
            ParsedSkillInfo(
                title = issue.title,
                description = extractedDescription.ifBlank { "无描述信息" },
                repositoryUrl = "",
                repositoryOwner = ""
            )
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

    private fun extractRepositoryOwner(repositoryUrl: String): String {
        if (repositoryUrl.isBlank()) return ""

        val githubPattern = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = githubPattern.find(repositoryUrl)

        return match?.groupValues?.get(1) ?: ""
    }
}
