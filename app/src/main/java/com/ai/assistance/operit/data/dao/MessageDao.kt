package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.MessageEntity

/** 消息DAO接口，定义对消息表的数据访问方法 */
@Dao
interface MessageDao {
    /** 获取消息总数 */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int

    /** 获取指定聊天的所有消息，按时间戳排序 */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getMessagesForChatAsc(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesForChatDesc(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT chatId AS chatId, COUNT(*) AS count FROM messages GROUP BY chatId")
    suspend fun getMessageCountsByChatId(): List<ChatMessageCount>

    /** 插入单条消息并返回消息ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    /** 批量插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /** 更新消息内容 */
    @Query("UPDATE messages SET content = :content WHERE messageId = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    /** 更新整条消息 */
    @Update
    suspend fun updateMessage(message: MessageEntity)

    /** 获取指定聊天中最大的序号 */
    @Query("SELECT MAX(orderIndex) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxOrderIndex(chatId: String): Int?

    /** 删除指定聊天的所有消息 */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)

    /** 根据时间戳查找消息 */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestamp(chatId: String, timestamp: Long): MessageEntity?

    /** 删除指定聊天中从某个时间戳开始的所有消息 */
    @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp >= :timestamp")
    suspend fun deleteMessagesFrom(chatId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp = :timestamp")
    suspend fun deleteMessageByTimestamp(chatId: String, timestamp: Long)

    @Query(
        "UPDATE messages SET selectedVariantIndex = :selectedVariantIndex WHERE chatId = :chatId AND timestamp = :timestamp"
    )
    suspend fun updateSelectedVariantIndex(
        chatId: String,
        timestamp: Long,
        selectedVariantIndex: Int,
    )

    /** 查找包含特定关键词的聊天ID列表（不重复） */
    @Query("SELECT DISTINCT chatId FROM messages WHERE content LIKE '%' || :query || '%' ESCAPE '\\' COLLATE NOCASE")
    suspend fun searchChatIdsByContent(query: String): List<String>

    /** 批量重命名消息中的角色名 */
    @Query("UPDATE messages SET roleName = :newName WHERE roleName = :oldName")
    suspend fun renameRoleName(oldName: String, newName: String): Int
}
