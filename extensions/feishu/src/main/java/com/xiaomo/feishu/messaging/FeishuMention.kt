package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu messaging transport.
 */


/**
 * 飞书 @提及 处理
 * 对齐 OpenClaw mention.ts
 *
 * 功能：
 * - 提取消息中的 @提及 目标
 * - 检测是否为转发请求
 * - 格式化 @提及 标签
 */
object FeishuMention {

    /**
     * 提及目标信息
     */
    data class MentionTarget(
        val openId: String,
        val name: String,
        val key: String  // 占位符，例如 @_user_1
    )

    /**
     * 转义正则表达式元字符
     */
    private fun escapeRegex(input: String): String {
        return Regex.escape(input)
    }

    /**
     * 从消息事件中提取提及目标（排除机器人自己）
     *
     * @param mentions 提及列表
     * @param botOpenId 机器人 open_id
     * @return 提及目标列表
     */
    fun extractMentionTargets(
        mentions: List<Map<String, Any?>>,
        botOpenId: String? = null
    ): List<MentionTarget> {
        return mentions.mapNotNull { mention ->
            val id = mention["id"] as? Map<*, *>
            val openId = id?.get("open_id") as? String
            val name = mention["name"] as? String ?: ""
            val key = mention["key"] as? String ?: ""

            // 排除机器人自己 && 必须有 open_id
            if (openId != null && openId != botOpenId) {
                MentionTarget(openId, name, key)
            } else {
                null
            }
        }
    }

    /**
     * 检查是否为提及转发请求
     *
     * 规则：
     * - 群聊：消息提及机器人 + 至少一个其他用户
     * - 私聊：消息提及任何用户（无需提及机器人）
     *
     * @param mentions 提及列表
     * @param chatType 聊天类型（p2p/group）
     * @param botOpenId 机器人 open_id
     * @return 是否为转发请求
     */
    fun isMentionForwardRequest(
        mentions: List<Map<String, Any?>>,
        chatType: String,
        botOpenId: String?
    ): Boolean {
        if (mentions.isEmpty()) {
            return false
        }

        val isDirectMessage = chatType != "group"
        val hasOtherMention = mentions.any { mention ->
            val id = mention["id"] as? Map<*, *>
            val openId = id?.get("open_id") as? String
            openId != botOpenId
        }

        return if (isDirectMessage) {
            // 私聊：提及任何非机器人用户即触发
            hasOtherMention
        } else {
            // 群聊：需要同时提及机器人和其他用户
            val hasBotMention = mentions.any { mention ->
                val id = mention["id"] as? Map<*, *>
                val openId = id?.get("open_id") as? String
                openId == botOpenId
            }
            hasBotMention && hasOtherMention
        }
    }

    /**
     * 从文本中提取消息正文（移除 @ 占位符）
     *
     * @param text 原始文本
     * @param allMentionKeys 所有 @ 占位符列表
     * @return 清理后的文本
     */
    fun extractMessageBody(text: String, allMentionKeys: List<String>): String {
        var result = text

        // 移除所有 @ 占位符
        for (key in allMentionKeys) {
            result = result.replace(Regex(escapeRegex(key)), "")
        }

        // 压缩空白字符
        return result.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * 格式化 @提及 标签（文本消息）
     *
     * @param target 提及目标
     * @return 格式化的标签
     */
    fun formatMentionForText(target: MentionTarget): String {
        return """<at user_id="${target.openId}">${target.name}</at>"""
    }

    /**
     * 格式化 @所有人 标签（文本消息）
     */
    fun formatMentionAllForText(): String {
        return """<at user_id="all">Everyone</at>"""
    }

    /**
     * 格式化 @提及 标签（卡片消息 lark_md）
     *
     * @param target 提及目标
     * @return 格式化的标签
     */
    fun formatMentionForCard(target: MentionTarget): String {
        return """<at id=${target.openId}></at>"""
    }

    /**
     * 格式化 @所有人 标签（卡片消息 lark_md）
     */
    fun formatMentionAllForCard(): String {
        return """<at id=all></at>"""
    }

    /**
     * 构建带提及的文本消息
     *
     * @param targets 提及目标列表
     * @param messageBody 消息正文
     * @return 完整消息文本
     */
    fun buildMentionedMessage(targets: List<MentionTarget>, messageBody: String): String {
        val mentions = targets.joinToString(" ") { formatMentionForText(it) }
        return if (mentions.isNotEmpty()) {
            "$mentions $messageBody"
        } else {
            messageBody
        }
    }

    /**
     * 构建带提及的卡片内容（lark_md）
     *
     * @param targets 提及目标列表
     * @param cardContent 卡片内容
     * @return 带提及的卡片内容
     */
    fun buildMentionedCardContent(targets: List<MentionTarget>, cardContent: String): String {
        val mentions = targets.joinToString(" ") { formatMentionForCard(it) }
        return if (mentions.isNotEmpty()) {
            "$mentions\n\n$cardContent"
        } else {
            cardContent
        }
    }
}
