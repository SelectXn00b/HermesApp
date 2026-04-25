package com.ai.assistance.operit.data.model

/**
 * 角色群组聊天统计结果
 */
data class CharacterGroupChatStats(
    val characterGroupId: String?,
    val chatCount: Int,
    val messageCount: Int
)
