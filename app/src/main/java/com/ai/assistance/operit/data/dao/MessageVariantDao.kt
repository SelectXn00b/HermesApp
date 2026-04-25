package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.MessageVariantEntity

@Dao
interface MessageVariantDao {
    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    suspend fun getVariantsForChat(chatId: String): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp IN (:messageTimestamps) ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    suspend fun getVariantsForMessages(
        chatId: String,
        messageTimestamps: List<Long>,
    ): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp ORDER BY variantIndex ASC"
    )
    suspend fun getVariantsForMessage(
        chatId: String,
        messageTimestamp: Long,
    ): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND variantIndex = :variantIndex LIMIT 1"
    )
    suspend fun getVariantForMessage(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: MessageVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<MessageVariantEntity>)

    @Update
    suspend fun updateVariant(variant: MessageVariantEntity)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp")
    suspend fun deleteVariantsForMessage(chatId: String, messageTimestamp: Long)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp >= :messageTimestamp")
    suspend fun deleteVariantsFrom(chatId: String, messageTimestamp: Long)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId")
    suspend fun deleteAllVariantsForChat(chatId: String)
}
