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
    suspend fun getOrDownloadLargeFile(message: ChatMessage): File?
}
