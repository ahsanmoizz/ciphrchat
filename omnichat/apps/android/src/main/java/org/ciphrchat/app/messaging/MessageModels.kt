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
    val selectedTransport: String? = null
)

data class ConversationSummary(
    val id: String,
    val contactName: String,
    val contactId: String,
    val lastMessage: String,
    val lastMessageEpochMs: Long,
    val unreadCount: Int = 0
)
