package org.ciphrchat.app.messaging

import kotlinx.coroutines.flow.Flow
import android.net.Uri

import java.io.File

interface MessageRepository {
    fun conversations(): Flow<List<ConversationSummary>>
    fun messages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun send(conversationId: String, recipientId: String, text: String): Result<ChatMessage>
    suspend fun sendAttachment(conversationId: String, recipientId: String, uri: Uri): Result<ChatMessage>
    suspend fun clearConversation(conversationId: String): Result<Int>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun retryMessage(messageId: String): Result<Unit>
    suspend fun forwardMessage(targetConversationId: String, targetRecipientId: String, originalMessage: ChatMessage): Result<ChatMessage>
    suspend fun getOrDownloadLargeFile(message: ChatMessage): File?
}
