package org.ciphrchat.app.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.flow.map
import org.ciphrchat.app.crypto.SignalSessionManager
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.identity.InvitationCodec
import org.ciphrchat.app.security.MessageContentCipher
import org.ciphrchat.app.transport.internet.RustNetworkEvent
import org.ciphrchat.app.transport.internet.RustP2pManager
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import org.whispersystems.libsignal.SignalProtocolAddress
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentMessageRepository @Inject constructor(
    private val database: AppDatabase,
    private val router: AutomaticRouter,
    private val contacts: ContactRepository,
    private val sessions: SignalSessionManager,
    private val contentCipher: MessageContentCipher,
    private val p2p: RustP2pManager
) : MessageRepository {

    private val dao = database.messageDao()
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        networkScope.launch {
            p2p.events.collect { event ->
                if (event is RustNetworkEvent.MessageReceived) {
                    receive(event.peerId, event.payload)
                }
            }
        }
    }

    override fun conversations(): Flow<List<ConversationSummary>> = dao.getAllMessages().map { messages ->
        messages.groupBy { it.conversationId }.map { (conversationId, items) ->
            val last = items.maxByOrNull { it.createdAtEpochMs }
            ConversationSummary(
                id = conversationId,
                contactName = contactName(conversationId),
                contactId = last?.recipientId ?: conversationId,
                lastMessage = last?.let(::decryptBody).orEmpty(),
                lastMessageEpochMs = last?.createdAtEpochMs ?: 0L
            )
        }.sortedByDescending { it.lastMessageEpochMs }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> =
        dao.getMessagesForConversation(conversationId).map { entities -> entities.map { it.toModel() } }

    override suspend fun send(
        conversationId: String,
        recipientId: String,
        text: String
    ): Result<ChatMessage> = runCatching {
        require(text.isNotBlank()) { "Message cannot be empty" }
        require(text.length <= 4_000) { "Message is too long" }

        val contact = contacts.find(recipientId)
            ?: error("Contact is not paired: scan or enter their invitation first")
        val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
        if (!sessions.hasSession(address)) {
            sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
        }
        val trimmed = text.trim()
        val ciphertext = sessions.encryptMessage(address, trimmed.toByteArray(Charsets.UTF_8))
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = "local",
            recipientId = recipientId,
            body = contentCipher.encrypt(trimmed),
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
            encryptedPayload = ciphertext.serialize(),
            testOnly = false
        )
        val finalEntity = when (val result = router.route(envelope)) {
            is SendResult.Accepted -> entity.copy(status = MessageStatus.SENT, selectedTransport = result.transport.name)
            is SendResult.Rejected -> entity.copy(status = MessageStatus.QUEUED)
            is SendResult.Failed -> entity.copy(status = MessageStatus.FAILED)
            is SendResult.Failure -> entity.copy(status = MessageStatus.FAILED)
            SendResult.Success -> entity.copy(status = MessageStatus.SENT)
        }
        dao.updateMessage(finalEntity)
        finalEntity.toModel()
    }

    private fun MessageEntity.toModel() = ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        body = decryptBody(this),
        createdAtEpochMs = createdAtEpochMs,
        direction = direction,
        status = status,
        selectedTransport = selectedTransport
    )

    private fun contactName(conversationId: String): String = "Contact ${conversationId.takeLast(8)}"

    private fun decryptBody(entity: MessageEntity): String = runCatching {
        contentCipher.decrypt(entity.body)
    }.getOrElse { "Encrypted message" }

    private suspend fun receive(peerId: String, wirePayload: ByteArray) {
        runCatching {
            val contact = contacts.findByPeerId(peerId) ?: return
            val envelope = JSONObject(wirePayload.toString(Charsets.UTF_8))
            val messageId = envelope.getString("messageId")
            val ciphertext = Base64.decode(envelope.getString("encryptedPayload"), Base64.NO_WRAP)
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            val plaintext = sessions.decryptSerializedMessage(address, ciphertext)
            dao.insertMessage(
                MessageEntity(
                    id = messageId,
                    conversationId = contact.contactId,
                    senderId = contact.contactId,
                    recipientId = "local",
                    body = contentCipher.encrypt(plaintext.toString(Charsets.UTF_8)),
                    createdAtEpochMs = envelope.optLong("createdAtEpochMs", System.currentTimeMillis()),
                    direction = MessageDirection.INCOMING,
                    status = MessageStatus.DELIVERED,
                    selectedTransport = "INTERNET_DIRECT"
                )
            )
        }
    }
}
