/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord.messaging

/**
 * Discord @提及处理
 * 参考 Feishu FeishuMention.kt
 */
object DiscordMention {
    private const val TAG = "DiscordMention"

    /**
     * 解析提及
     * 从消息内容中提取所有 @提及
     */
    fun parseMentions(content: String): List<String> {
        val mentions = mutableListOf<String>()
        val pattern = Regex("<@!?(\\d+)>")

        pattern.findAll(content).forEach { match ->
            val userId = match.groupValues[1]
            mentions.add(userId)
        }

        return mentions
    }

    /**
     * 格式化用户提及
     */
    fun formatUserMention(userId: String): String {
        return "<@$userId>"
    }

    /**
     * 格式化角色提及
     */
    fun formatRoleMention(roleId: String): String {
        return "<@&$roleId>"
    }

    /**
     * 格式化频道提及
     */
    fun formatChannelMention(channelId: String): String {
        return "<#$channelId>"
    }

    /**
     * 移除所有提及
     */
    fun stripMentions(content: String): String {
        return content
            .replace(Regex("<@!?\\d+>"), "") // 用户提及
            .replace(Regex("<@&\\d+>"), "") // 角色提及
            .replace(Regex("<#\\d+>"), "") // 频道提及
            .trim()
    }

    /**
     * 检查消息是否包含指定用户的提及
     */
    fun containsUserMention(content: String, userId: String): Boolean {
        val pattern = Regex("<@!?$userId>")
        return pattern.containsMatchIn(content)
    }

    /**
     * 替换提及为显示名称
     */
    fun replaceMentionsWithNames(
        content: String,
        userNames: Map<String, String>
    ): String {
        var result = content

        userNames.forEach { (userId, userName) ->
            val pattern = Regex("<@!?$userId>")
            result = result.replace(pattern, "@$userName")
        }

        return result
    }

    /**
     * @everyone 提及
     */
    const val MENTION_EVERYONE = "@everyone"

    /**
     * @here 提及
     */
    const val MENTION_HERE = "@here"
}
