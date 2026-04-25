package com.ai.assistance.operit.ui.features.packages.utils

import com.ai.assistance.operit.data.api.GitHubIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * MCP 插件信息解析工具类
 */
object MCPPluginParser {
    private const val TAG = "MCPPluginParser"

    @Serializable
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    data class MCPMetadata(
        val description: String = "",
        val repositoryUrl: String,
        @JsonNames("installCommand") // 兼容旧的 installCommand
        val installConfig: String, // 改为安装配置
        val category: String,
        val tags: String,
        val version: String
    )

    data class ParsedPluginInfo(
        val title: String,
        val description: String,
        val repositoryUrl: String = "",
        val installConfig: String = "", // 改为安装配置
        val category: String = "",
        val tags: String = "",
        val version: String = "",
        val repositoryOwner: String = "", // 仓库所有者（插件作者）
        val repositoryOwnerAvatarUrl: String = "" // 仓库所有者头像URL
    )

    /**
     * 从 GitHub Issue 解析插件信息
     */
    fun parsePluginInfo(issue: GitHubIssue): ParsedPluginInfo {
        val body = issue.body ?: return ParsedPluginInfo(
            title = issue.title,
            description = "无描述信息"
        )

        // 尝试解析 JSON 元数据
        val metadata = parseMCPMetadata(body)
        val extractedDescription =
            sanitizeMcpDescription(IssueBodyDescriptionExtractor.extractHumanDescriptionFromBody(body))

        return if (metadata != null) {
            ParsedPluginInfo(
                title = issue.title,
                description = sanitizeMcpDescription(metadata.description)
                    .ifBlank { extractedDescription.ifBlank { "无描述信息" } },
                repositoryUrl = metadata.repositoryUrl,
                installConfig = metadata.installConfig,
                category = metadata.category,
                tags = metadata.tags,
                version = metadata.version,
                repositoryOwner = extractRepositoryOwner(metadata.repositoryUrl)
            )
        } else {
            ParsedPluginInfo(
                title = issue.title,
                description = extractedDescription.ifBlank { "无描述信息" },
                repositoryUrl = "",
                repositoryOwner = ""
            )
        }
    }

    /**
     * 解析隐藏在注释中的 JSON 元数据
     */
    private fun parseMCPMetadata(body: String): MCPMetadata? {
        return IssueBodyMetadataParser.parseCommentJson(
            body = body,
            prefix = "<!-- operit-mcp-json: ",
            tag = TAG,
            metadataName = "MCP metadata"
        )
    }

    /**
     * 从GitHub URL中提取仓库所有者
     */
    private fun extractRepositoryOwner(repositoryUrl: String): String {
        if (repositoryUrl.isBlank()) return ""
        
        val githubPattern = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = githubPattern.find(repositoryUrl)

        return match?.groupValues?.get(1) ?: ""
    }

    private fun sanitizeMcpDescription(raw: String): String {
        return raw
            .trim()
            .replace(Regex("""^(?:[-*+]\s*)?(?:\*\*\s*)?描述\s*[:：]\s*(?:\*\*)?\s*"""), "")
            .replace(
                Regex(
                    """^(?:[-*+]\s*)?(?:\*\*\s*)?(description|desc|summary|introduction)\s*[:：]\s*(?:\*\*)?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
    }
} 
