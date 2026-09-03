package org.ciphrchat.app.messaging

enum class MessageDirection { INCOMING, OUTGOING }
enum class MessageStatus { QUEUED, ROUTING, SENT, DELIVERED, FAILED }

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
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
)

data class ConversationSummary(
    val id: String,
    val contactName: String,
    val contactId: String,
    val lastMessage: String,
    val lastMessageEpochMs: Long,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val memberCount: Int = 0
)
