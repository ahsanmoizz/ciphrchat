package org.ciphrchat.app.messaging

import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun conversations(): Flow<List<ConversationSummary>>
    fun messages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun send(conversationId: String, recipientId: String, text: String): Result<ChatMessage>
}
