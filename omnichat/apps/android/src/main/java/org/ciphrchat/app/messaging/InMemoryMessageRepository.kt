package org.ciphrchat.app.messaging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory message repository with seed data for the Phase 1 scaffold.
 * Provides sample conversations with Sara, Ali, and Usman.
 */
@Singleton
class InMemoryMessageRepository @Inject constructor(
    private val router: AutomaticRouter
) : MessageRepository {

    private val messageMap = MutableStateFlow<Map<String, List<ChatMessage>>>(seedMessages())

    override fun conversations(): Flow<List<ConversationSummary>> = messageMap.map { map ->
        map.map { (conversationId, messages) ->
            val last = messages.maxByOrNull { it.createdAtEpochMs }
            ConversationSummary(
                id = conversationId,
                contactName = contactName(conversationId),
                contactId = "contact:$conversationId",
                lastMessage = last?.body.orEmpty(),
                lastMessageEpochMs = last?.createdAtEpochMs ?: 0L
            )
        }.sortedByDescending { it.lastMessageEpochMs }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> =
        messageMap.map { it[conversationId].orEmpty() }

    override suspend fun send(
        conversationId: String,
        recipientId: String,
        text: String
    ): Result<ChatMessage> = runCatching {
        require(text.isNotBlank()) { "Message cannot be empty" }
        require(text.length <= 4_000) { "Message is too long" }

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = "local",
            recipientId = recipientId,
            body = text.trim(),
            createdAtEpochMs = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED
        )
        append(message)

        val envelope = OutboundEnvelope(
            protocolVersion = 1,
            messageId = message.id,
            recipientId = recipientId,
            createdAtEpochMs = message.createdAtEpochMs,
            expiresAtEpochMs = message.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = text.encodeToByteArray(),
            testOnly = true
        )

        when (val result = router.route(envelope)) {
            is SendResult.Accepted -> replace(
                message.copy(
                    status = MessageStatus.SENT,
                    selectedTransport = result.transport.name
                )
            )
            is SendResult.Rejected -> replace(message.copy(status = MessageStatus.QUEUED))
            is SendResult.Failed -> replace(message.copy(status = MessageStatus.FAILED))
            is SendResult.Failure -> replace(message.copy(status = MessageStatus.FAILED))
            SendResult.Success -> replace(message.copy(status = MessageStatus.SENT))
        }

        message
    }

    private fun append(message: ChatMessage) {
        messageMap.update { current ->
            current + (message.conversationId to (current[message.conversationId].orEmpty() + message))
        }
    }

    private fun replace(message: ChatMessage) {
        messageMap.update { current ->
            current + (
                message.conversationId to current[message.conversationId].orEmpty().map {
                    if (it.id == message.id) message else it
                }
            )
        }
    }

    private fun contactName(conversationId: String): String = when (conversationId) {
        "conv-sara" -> "Sara"
        "conv-ali" -> "Ali"
        "conv-usman" -> "Usman"
        else -> "Unknown"
    }

    companion object {
        private fun seedMessages(): Map<String, List<ChatMessage>> {
            val now = System.currentTimeMillis()
            return mapOf(
                "conv-sara" to listOf(
                    ChatMessage(
                        id = "seed-1",
                        conversationId = "conv-sara",
                        senderId = "contact:conv-sara",
                        recipientId = "local",
                        body = "Hey! Have you tried CiphrChat yet?",
                        createdAtEpochMs = now - 3600_000,
                        direction = MessageDirection.INCOMING,
                        status = MessageStatus.DELIVERED
                    ),
                    ChatMessage(
                        id = "seed-2",
                        conversationId = "conv-sara",
                        senderId = "local",
                        recipientId = "contact:conv-sara",
                        body = "Yes! It works over Bluetooth too 🔥",
                        createdAtEpochMs = now - 3500_000,
                        direction = MessageDirection.OUTGOING,
                        status = MessageStatus.DELIVERED,
                        selectedTransport = "INTERNET_DIRECT"
                    )
                ),
                "conv-ali" to listOf(
                    ChatMessage(
                        id = "seed-3",
                        conversationId = "conv-ali",
                        senderId = "contact:conv-ali",
                        recipientId = "local",
                        body = "Send me the APK when you get a chance",
                        createdAtEpochMs = now - 7200_000,
                        direction = MessageDirection.INCOMING,
                        status = MessageStatus.DELIVERED
                    )
                ),
                "conv-usman" to listOf(
                    ChatMessage(
                        id = "seed-4",
                        conversationId = "conv-usman",
                        senderId = "local",
                        recipientId = "contact:conv-usman",
                        body = "Meeting at 5?",
                        createdAtEpochMs = now - 86400_000,
                        direction = MessageDirection.OUTGOING,
                        status = MessageStatus.SENT,
                        selectedTransport = "WIFI_LAN"
                    ),
                    ChatMessage(
                        id = "seed-5",
                        conversationId = "conv-usman",
                        senderId = "contact:conv-usman",
                        recipientId = "local",
                        body = "Sure, see you there",
                        createdAtEpochMs = now - 86000_000,
                        direction = MessageDirection.INCOMING,
                        status = MessageStatus.DELIVERED
                    )
                )
            )
        }
    }
}
