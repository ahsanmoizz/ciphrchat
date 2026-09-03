package org.ciphrchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ciphrchat.app.messaging.MessageStatus

@Dao
interface GroupMessageDao {
    @Query("SELECT * FROM group_messages WHERE id = :messageId LIMIT 1")
    suspend fun findById(messageId: String): GroupMessageEntity?

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAtEpochMs ASC")
    fun observeMessagesForGroup(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAtEpochMs ASC")
    suspend fun listMessagesForGroup(groupId: String): List<GroupMessageEntity>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getLatestMessageForGroup(groupId: String): GroupMessageEntity?

    @Query("""
        SELECT gm.* FROM group_messages gm
        INNER JOIN (
            SELECT groupId, MAX(createdAtEpochMs) AS maxCreatedAt
            FROM group_messages
            GROUP BY groupId
        ) latest ON gm.groupId = latest.groupId AND gm.createdAtEpochMs = latest.maxCreatedAt
        ORDER BY gm.createdAtEpochMs DESC
    """)
    fun getLatestMessagesPerGroup(): Flow<List<GroupMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: GroupMessageEntity)

    @Update
    suspend fun updateMessage(message: GroupMessageEntity)

    @Query("DELETE FROM group_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String): Int

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun deleteMessagesForGroup(groupId: String): Int

    @Query("SELECT COUNT(*) FROM group_messages WHERE attachmentStoragePath = :path AND id != :messageId")
    suspend fun countOtherReferencesToAttachment(path: String, messageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(delivery: GroupRecipientDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveries(deliveries: List<GroupRecipientDeliveryEntity>)

    @Update
    suspend fun updateDelivery(delivery: GroupRecipientDeliveryEntity)

    @Query("SELECT * FROM group_recipient_deliveries WHERE groupMessageId = :messageId")
    suspend fun getDeliveriesForMessage(messageId: String): List<GroupRecipientDeliveryEntity>

    @Query("SELECT * FROM group_recipient_deliveries WHERE groupMessageId = :messageId AND recipientPublicId = :recipientId LIMIT 1")
    suspend fun getDelivery(messageId: String, recipientId: String): GroupRecipientDeliveryEntity?

    @Query("SELECT * FROM group_recipient_deliveries WHERE status = 'QUEUED' OR status = 'ROUTING' ORDER BY lastAttemptEpochMs ASC")
    suspend fun getPendingDeliveries(): List<GroupRecipientDeliveryEntity>

    @Query("UPDATE group_recipient_deliveries SET status = :status, selectedTransport = :transport WHERE groupMessageId = :messageId AND recipientPublicId = :recipientId")
    suspend fun updateDeliveryStatus(messageId: String, recipientId: String, status: MessageStatus, transport: String?): Int

    @Query("UPDATE group_recipient_deliveries SET status = 'QUEUED' WHERE groupMessageId = :messageId AND status = 'FAILED'")
    suspend fun requeueFailedDeliveriesForMessage(messageId: String): Int

    @Query("SELECT * FROM group_recipient_deliveries WHERE status = 'SENT'")
    suspend fun getSentDeliveries(): List<GroupRecipientDeliveryEntity>

    @Query("DELETE FROM group_recipient_deliveries WHERE groupMessageId = :messageId")
    suspend fun deleteDeliveriesForMessage(messageId: String): Int
}
