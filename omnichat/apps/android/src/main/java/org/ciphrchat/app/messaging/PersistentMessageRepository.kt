package org.ciphrchat.app.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentMessageRepository @Inject constructor(
    private val database: AppDatabase,
    private val router: AutomaticRouter
) : MessageRepository {

    private val dao = database.messageDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            if (dao.countMessages() == 0) {
                seedMessages()
            }
        }
    }

    override fun conversations(): Flow<List<ConversationSummary>> {
        return dao.getAllMessages().map { messages ->
            val grouped = messages.groupBy { it.conversationId }
            grouped.map { (convId, msgs) ->
                val last = msgs.maxByOrNull { it.createdAtEpochMs }
                ConversationSummary(
                    id = convId,
                    contactName = contactName(convId),
                    contactId = "contact:$convId",
                    lastMessage = last?.body.orEmpty(),
                    lastMessageEpochMs = last?.createdAtEpochMs ?: 0L
                )
            }.sortedByDescending { it.lastMessageEpochMs }
        }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> {
        return dao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun send(
        conversationId: String,
        recipientId: String,
        text: String
    ): Result<ChatMessage> = runCatching {
        require(text.isNotBlank()) { "Message cannot be empty" }
        require(text.length <= 4_000) { "Message is too long" }

        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = "local",
            recipientId = recipientId,
            body = text.trim(),
            createdAtEpochMs = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null
        )
        dao.insertMessage(entity)

        val envelope = OutboundEnvelope(
            protocolVersion = 1,
            messageId = entity.id,
            recipientId = recipientId,
            createdAtEpochMs = entity.createdAtEpochMs,
            expiresAtEpochMs = entity.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = text.encodeToByteArray(),
            testOnly = true
        )

        val finalEntity = when (val result = router.route(envelope)) {
            is SendResult.Accepted -> entity.copy(
                status = MessageStatus.SENT,
                selectedTransport = result.transport.name
            )
            is SendResult.Rejected -> entity.copy(status = MessageStatus.QUEUED)
            is SendResult.Failed -> entity.copy(status = MessageStatus.FAILED)
        }
        dao.updateMessage(finalEntity)

        finalEntity.toModel()
    }

    private fun MessageEntity.toModel() = ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        body = body,
        createdAtEpochMs = createdAtEpochMs,
        direction = direction,
        status = status,
        selectedTransport = selectedTransport
    )

    private fun contactName(conversationId: String): String = when (conversationId) {
        "conv-sara" -> "Sara"
        "conv-ali" -> "Ali"
        "conv-usman" -> "Usman"
        else -> "Unknown"
    }

    private suspend fun seedMessages() {
        val now = System.currentTimeMillis()
        val seed = listOf(
            MessageEntity(
                id = "seed-1",
                conversationId = "conv-sara",
                senderId = "contact:conv-sara",
                recipientId = "local",
                body = "Hey! Have you tried CiphrChat yet?",
                createdAtEpochMs = now - 3600_000,
                direction = MessageDirection.INCOMING,
                status = MessageStatus.DELIVERED,
                selectedTransport = null
            ),
            MessageEntity(
                id = "seed-2",
                conversationId = "conv-sara",
                senderId = "local",
                recipientId = "contact:conv-sara",
                body = "Yes! It works over Bluetooth too 🔥",
                createdAtEpochMs = now - 3500_000,
                direction = MessageDirection.OUTGOING,
                status = MessageStatus.DELIVERED,
                selectedTransport = "INTERNET_DIRECT"
            ),
            MessageEntity(
                id = "seed-3",
                conversationId = "conv-ali",
                senderId = "contact:conv-ali",
                recipientId = "local",
                body = "Send me the APK when you get a chance",
                createdAtEpochMs = now - 7200_000,
                direction = MessageDirection.INCOMING,
                status = MessageStatus.DELIVERED,
                selectedTransport = null
            ),
            MessageEntity(
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
            MessageEntity(
                id = "seed-5",
                conversationId = "conv-usman",
                senderId = "contact:conv-usman",
                recipientId = "local",
                body = "Sure, see you there",
                createdAtEpochMs = now - 86000_000,
                direction = MessageDirection.INCOMING,
                status = MessageStatus.DELIVERED,
                selectedTransport = null
            )
        )
        seed.forEach { dao.insertMessage(it) }
    }
}
