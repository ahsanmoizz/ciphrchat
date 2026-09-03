package org.ciphrchat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.ciphrchat.app.messaging.ChatMessage
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.messaging.MessageStatus

@Entity(
    tableName = "group_messages",
    indices = [
        Index(value = ["groupId", "createdAtEpochMs"]),
        Index(value = ["status"])
    ]
)
data class GroupMessageEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val senderId: String,
    val body: String,
    val createdAtEpochMs: Long,
    val direction: MessageDirection,
    val status: MessageStatus,
    val selectedTransport: String? = null,
    val attachmentFileName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentStoragePath: String? = null,
    val attachmentSizeBytes: Long = 0L,
    val attachmentSha256: String? = null,
    val isForwarded: Boolean = false
) {
    fun toModel(): ChatMessage = ChatMessage(
        id = id,
        conversationId = groupId,
        senderId = senderId,
        recipientId = groupId,
        body = body,
        createdAtEpochMs = createdAtEpochMs,
        direction = direction,
        status = status,
        selectedTransport = selectedTransport,
        attachmentFileName = attachmentFileName,
        attachmentMimeType = attachmentMimeType,
        attachmentStoragePath = attachmentStoragePath,
        attachmentSizeBytes = attachmentSizeBytes,
        attachmentSha256 = attachmentSha256,
        isForwarded = isForwarded
    )
}

@Entity(
    tableName = "group_recipient_deliveries",
    primaryKeys = ["groupMessageId", "recipientPublicId"],
    indices = [
        Index(value = ["groupMessageId"]),
        Index(value = ["recipientPublicId"]),
        Index(value = ["status"])
    ]
)
data class GroupRecipientDeliveryEntity(
    val groupMessageId: String,
    val recipientPublicId: String,
    val status: MessageStatus,
    val selectedTransport: String? = null,
    val attempts: Int = 0,
    val lastAttemptEpochMs: Long = 0L,
    val errorMessage: String? = null
)
