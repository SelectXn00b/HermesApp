package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.MarketRankIssueEntryResponse
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import com.ai.assistance.operit.ui.features.packages.utils.SkillIssueParser

data class SkillMarketBrowseItem(
    val issue: GitHubIssue,
    val entryId: String,
    val title: String,
    val description: String,
    val repositoryUrl: String,
    val ownerUsername: String,
    val publisherAvatarUrl: String?
)

data class McpMarketBrowseItem(
    val issue: GitHubIssue,
    val entryId: String,
    val title: String,
    val description: String,
    val repositoryUrl: String,
    val ownerUsername: String,
    val publisherAvatarUrl: String?,
    val pluginId: String
)

fun GitHubIssue.toSkillMarketBrowseItem(): SkillMarketBrowseItem {
    val skillInfo = SkillIssueParser.parseSkillInfo(this)
    return SkillMarketBrowseItem(
        issue = this,
        entryId = resolveSkillMarketEntryId(this),
        title = skillInfo.title.ifBlank { title },
        description = skillInfo.description,
        repositoryUrl = skillInfo.repositoryUrl,
        ownerUsername = skillInfo.repositoryOwner,
        publisherAvatarUrl = user.avatarUrl
    )
}

fun MarketRankIssueEntryResponse.toSkillMarketBrowseItem(): SkillMarketBrowseItem {
    val skillInfo = SkillIssueParser.parseSkillInfo(issue)
    return SkillMarketBrowseItem(
        issue = issue,
        entryId = id,
        title = displayTitle.ifBlank { skillInfo.title.ifBlank { issue.title } },
        description = summaryDescription.ifBlank { skillInfo.description },
        repositoryUrl = skillInfo.repositoryUrl,
        ownerUsername = authorLogin.ifBlank { skillInfo.repositoryOwner },
        publisherAvatarUrl = issue.user.avatarUrl
    )
}

fun GitHubIssue.toMcpMarketBrowseItem(): McpMarketBrowseItem {
    val pluginInfo = MCPPluginParser.parsePluginInfo(this)
    return McpMarketBrowseItem(
        issue = this,
        entryId = resolveMcpMarketEntryId(this),
        title = pluginInfo.title.ifBlank { title },
        description = pluginInfo.description,
        repositoryUrl = pluginInfo.repositoryUrl,
        ownerUsername = pluginInfo.repositoryOwner,
        publisherAvatarUrl = user.avatarUrl,
        pluginId = title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    )
}

fun MarketRankIssueEntryResponse.toMcpMarketBrowseItem(): McpMarketBrowseItem {
    val pluginInfo = MCPPluginParser.parsePluginInfo(issue)
    return McpMarketBrowseItem(
        issue = issue,
        entryId = id,
        title = displayTitle.ifBlank { pluginInfo.title.ifBlank { issue.title } },
        description = summaryDescription.ifBlank { pluginInfo.description },
        repositoryUrl = pluginInfo.repositoryUrl,
        ownerUsername = authorLogin.ifBlank { pluginInfo.repositoryOwner },
        publisherAvatarUrl = issue.user.avatarUrl,
        pluginId = issue.title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    )
}
