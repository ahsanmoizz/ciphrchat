package org.ciphrchat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.messaging.MessageStatus

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "createdAtEpochMs"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val encryptedPayload: ByteArray,
    val createdAtEpochMs: Long,
    val direction: MessageDirection,
    val status: MessageStatus,
    val selectedTransport: String?,
    val attachmentFileName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentStoragePath: String? = null,
    val attachmentSizeBytes: Long = 0L,
    val attachmentSha256: String? = null,
    val isForwarded: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun findById(messageId: String): MessageEntity?

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT conversationId, MAX(createdAtEpochMs) AS maxCreatedAt
            FROM messages
            GROUP BY conversationId
        ) latest ON m.conversationId = latest.conversationId AND m.createdAtEpochMs = latest.maxCreatedAt
        ORDER BY m.createdAtEpochMs DESC
    """)
    fun getLatestMessagesPerConversation(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMs ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId")
    suspend fun listMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String): Int

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE attachmentStoragePath = :path AND id != :messageId")
    suspend fun countOtherReferencesToAttachment(path: String, messageId: String): Int

    @Query("SELECT * FROM messages ORDER BY createdAtEpochMs DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY createdAtEpochMs ASC")
    fun getAllForBackup(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessageForRestore(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE status = :status")
    suspend fun getMessagesByStatus(status: MessageStatus): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = 'QUEUED' OR status = 'ROUTING' ORDER BY createdAtEpochMs ASC")
    suspend fun getMessagesPendingDelivery(): List<MessageEntity>

    @Query("UPDATE messages SET status = 'QUEUED' WHERE status = 'FAILED'")
    suspend fun requeueFailedMessages(): Int
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int
}
