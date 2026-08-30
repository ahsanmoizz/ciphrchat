package org.ciphrchat.app.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import android.net.Uri
import org.ciphrchat.app.worker.PendingMessageRetryScheduler
import org.ciphrchat.app.crypto.SignalSessionManager
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.identity.InvitationCodec
import org.ciphrchat.app.security.MessageContentCipher
import org.ciphrchat.app.transport.internet.RustNetworkEvent
import org.ciphrchat.app.transport.internet.RustP2pManager
import org.ciphrchat.app.transport.internet.InternetWireCodec
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportInboundBus
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.calling.AudioCallManager
import org.ciphrchat.app.calling.CallSignal
import org.ciphrchat.app.identity.InvitationService
import org.ciphrchat.app.identity.ReciprocalPairingPolicy
import org.whispersystems.libsignal.SignalProtocolAddress
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.files.LargeFileTransferManager
import org.ciphrchat.app.files.FileTransferDescriptor
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentMessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val router: AutomaticRouter,
    private val contacts: ContactRepository,
    private val sessions: SignalSessionManager,
    private val contentCipher: MessageContentCipher,
    private val p2p: RustP2pManager,
    private val inboundBus: TransportInboundBus,
    private val identity: IdentityRepository,
    private val attachmentStore: AttachmentStore,
    private val largeFileManager: LargeFileTransferManager,
    private val invitationService: InvitationService,
    private val retryScheduler: PendingMessageRetryScheduler,
    private val audioCallManager: AudioCallManager
) : MessageRepository {

    private val dao = database.messageDao()
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        networkScope.launch {
            dao.requeueFailedMessages()
            if (dao.getMessagesPendingDelivery().isNotEmpty()) retryScheduler.scheduleNow()
        }
        networkScope.launch {
            p2p.events.collect { event ->
                when (event) {
                    is RustNetworkEvent.MessageReceived -> receive(event.peerId, event.payload)
                    is RustNetworkEvent.MailboxMessageReceived -> {
                        if (receive(event.peerId, event.payload)) {
                            p2p.acknowledgeMailbox(event.messageId)
                        }
                    }
                    is RustNetworkEvent.DeliveryAccepted -> updateDelivery(event.messageId, MessageStatus.SENT)
                    is RustNetworkEvent.DeliveryFailed -> updateDelivery(event.messageId, MessageStatus.QUEUED)
                    else -> Unit
                }
            }
        }
        networkScope.launch {
            inboundBus.events.collect { event -> receiveLocal(event.envelope, event.transport) }
        }
        networkScope.launch {
            audioCallManager.outgoingSignals.collect { signal ->
                runCatching {
                    val contact = contacts.find(signal.recipientId) ?: return@collect
                    val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
                    if (!sessions.hasSession(address)) {
                        sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
                    }
                    val signalJson = signal.toJson()
                    val payload = MessageContentCodec.encodeCallSignal(signalJson)
                    val localId = identity.current()?.publicId ?: "self"
                    val messageId = UUID.randomUUID().toString()
                    val ciphertext = sessions.encryptMessage(address, payload)
                    val envelope = OutboundEnvelope(
                        messageId = messageId,
                        senderId = localId,
                        recipientId = contact.contactId,
                        encryptedPayload = ciphertext.serialize(),
                        createdAtEpochMs = System.currentTimeMillis(),
                        expiresAtEpochMs = System.currentTimeMillis() + 60_000L,
                        protocolVersion = 1,
                        hopLimit = 8,
                        testOnly = false,
                        senderInvitation = invitationService.createInvitation().getOrNull() ?: ""
                    )
                    router.route(envelope)
                }
            }
        }
    }

    /** The repository starts its collectors during construction; this documents eager startup. */
    fun ensureStarted() = Unit

    override fun conversations(): Flow<List<ConversationSummary>> = combine(
        dao.getAllMessages(),
        contacts.observe()
    ) { messages, contactEntities ->
        val contactNames = contactEntities.associateBy { it.contactId }
        messages.groupBy { it.conversationId }.map { (conversationId, items) ->
            val last = items.maxByOrNull { it.createdAtEpochMs }
            ConversationSummary(
                id = conversationId,
                contactName = contactNames[conversationId]?.displayName ?: "Contact ${conversationId.takeLast(8)}",
                contactId = conversationId,
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
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "Message cannot be empty" }
            require(text.length <= 4_000) { "Message is too long" }

            val contact = contacts.find(recipientId)
                ?: error("Contact is not paired: scan or enter their invitation first")
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            if (!sessions.hasSession(address)) {
                sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
            }
            val trimmed = text.trim()
            sendContent(
                conversationId = conversationId,
                recipientId = recipientId,
                address = address,
                plaintext = MessageContentCodec.encodeText(trimmed),
                body = trimmed
            )
        }
    }

    override suspend fun sendAttachment(
        conversationId: String,
        recipientId: String,
        uri: Uri
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val contact = contacts.find(recipientId)
                ?: error("Contact is not paired: scan or enter their invitation first")
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            if (!sessions.hasSession(address)) {
                sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
            }

            val fileSize = getUriFileSize(uri)
            require(fileSize > 0) { "The selected attachment is empty" }
            require(fileSize <= FileTransferDescriptor.MAX_FILE_SIZE_BYTES) {
                "Attachment exceeds the 5 GiB maximum limit"
            }

            if (fileSize > AttachmentStore.MAX_ATTACHMENT_BYTES) {
                // > 5 MiB: Zero-retention large-file streaming transit path
                // Retain persistable URI permission where supported for resumable reads
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val fileName = getUriFileName(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val localId = identity.current()?.publicId ?: "self"

                val (descriptor, fileKey) = largeFileManager.prepareDescriptor(
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    senderId = localId,
                    recipientId = contact.contactId
                ).getOrThrow()

                val relayHttpUrl = BuildConfig.CIPHRCHAT_FILE_RELAY_HTTP_URL.ifBlank { "https://relay.ciphrchat.org" }

                // Launch concurrent streaming chunk upload through the zero-retention transit relay
                networkScope.launch {
                    largeFileManager.uploadFile(
                        relayBaseHttpUrl = relayHttpUrl,
                        uri = uri,
                        descriptor = descriptor,
                        fileKey = fileKey
                    )
                }

                // Send Signal-encrypted message containing ONLY the descriptor metadata and per-file key
                sendContent(
                    conversationId = conversationId,
                    recipientId = recipientId,
                    address = address,
                    plaintext = MessageContentCodec.encodeFileDescriptor(descriptor),
                    body = "Large File: ${descriptor.fileName} (${formatBytes(descriptor.fileSize)})",
                    attachmentFileName = descriptor.fileName,
                    attachmentMimeType = descriptor.mimeType,
                    attachmentStoragePath = "large_file:${descriptor.fileId}",
                    attachmentSizeBytes = descriptor.fileSize,
                    attachmentSha256 = descriptor.sha256
                )
            } else {
                // <= 5 MiB: Existing small attachment legacy path
                val input = attachmentStore.read(uri)
                require(input.bytes.isNotEmpty()) { "The selected attachment is empty" }
                val stored = attachmentStore.save(input.fileName, input.mimeType, input.bytes)
                sendContent(
                    conversationId = conversationId,
                    recipientId = recipientId,
                    address = address,
                    plaintext = MessageContentCodec.encodeAttachment(input.fileName, input.mimeType, input.bytes),
                    body = "Attachment: ${input.fileName}",
                    attachmentFileName = input.fileName,
                    attachmentMimeType = input.mimeType,
                    attachmentStoragePath = stored.path,
                    attachmentSizeBytes = stored.size,
                    attachmentSha256 = stored.sha256
                )
            }
        }
    }

    override suspend fun getOrDownloadLargeFile(message: ChatMessage): File? = withContext(Dispatchers.IO) {
        val fileName = message.attachmentFileName ?: return@withContext null
        val downloadDir = File(context.filesDir, "CiphrChat/downloads").apply { mkdirs() }
        val destinationFile = File(downloadDir, fileName)

        if (destinationFile.exists() && (message.attachmentSizeBytes <= 0L || destinationFile.length() == message.attachmentSizeBytes)) {
            return@withContext destinationFile
        }

        val sha256 = message.attachmentSha256
        val fileId = message.attachmentStoragePath?.removePrefix("large_file:")
        val descriptor = (if (!sha256.isNullOrBlank()) largeFileManager.findDescriptorBySha256(sha256) else null)
            ?: (if (!fileId.isNullOrBlank()) largeFileManager.findDescriptorByFileId(fileId) else null)
            ?: return@withContext null

        val relayHttpUrl = BuildConfig.CIPHRCHAT_FILE_RELAY_HTTP_URL.ifBlank { "https://relay.ciphrchat.org" }
        largeFileManager.downloadFile(relayHttpUrl, descriptor, destinationFile).getOrNull()
    }

    override suspend fun clearConversation(conversationId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(conversationId.isNotBlank()) { "Conversation is unavailable" }
                val messages = dao.listMessagesForConversation(conversationId)
                val deleted = dao.deleteConversation(conversationId)
                messages.mapNotNull { it.attachmentStoragePath }
                    .filter { !it.startsWith("large_file:") }
                    .distinct()
                    .forEach(attachmentStore::delete)
                deleted
            }
        }

    private suspend fun sendContent(
        conversationId: String,
        recipientId: String,
        address: SignalProtocolAddress,
        plaintext: ByteArray,
        body: String,
        attachmentFileName: String? = null,
        attachmentMimeType: String? = null,
        attachmentStoragePath: String? = null,
        attachmentSizeBytes: Long = 0L,
        attachmentSha256: String? = null
    ): ChatMessage {
        val ciphertext = sessions.encryptMessage(address, plaintext)
        val localIdentity = identity.current()?.publicId ?: error("Local identity is unavailable")
        val senderInvitation = invitationService.createInvitation().getOrElse {
            throw IllegalStateException("Could not prepare your secure sender identity", it)
        }
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = localIdentity,
            recipientId = recipientId,
            body = contentCipher.encrypt(body),
            encryptedPayload = ciphertext.serialize(),
            createdAtEpochMs = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null,
            attachmentFileName = attachmentFileName,
            attachmentMimeType = attachmentMimeType,
            attachmentStoragePath = attachmentStoragePath,
            attachmentSizeBytes = attachmentSizeBytes,
            attachmentSha256 = attachmentSha256
        )
        dao.insertMessage(entity)

        val envelope = OutboundEnvelope(
            protocolVersion = 2,
            messageId = entity.id,
            recipientId = recipientId,
            senderId = localIdentity,
            createdAtEpochMs = entity.createdAtEpochMs,
            expiresAtEpochMs = entity.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = ciphertext.serialize(),
            testOnly = false,
            senderInvitation = senderInvitation
        )
        val sendResult = router.route(envelope)
        val persisted = dao.findById(entity.id) ?: entity
        val finalEntity = when (sendResult) {
            is SendResult.Accepted -> persisted.copy(
                status = DeliveryStatusPolicy.merge(
                    persisted.status,
                    DeliveryStatusPolicy.statusFor(sendResult)
                ),
                selectedTransport = sendResult.transport.name
            )
            else -> persisted.copy(
                status = DeliveryStatusPolicy.merge(
                    persisted.status,
                    DeliveryStatusPolicy.statusFor(sendResult)
                )
            )
        }
        dao.updateMessage(finalEntity)
        if (finalEntity.status == MessageStatus.QUEUED ||
            finalEntity.status == MessageStatus.SENT && finalEntity.selectedTransport == "INTERNET_DIRECT"
        ) retryScheduler.schedule()
        return finalEntity.toModel()
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
        selectedTransport = selectedTransport,
        attachmentFileName = attachmentFileName,
        attachmentMimeType = attachmentMimeType,
        attachmentStoragePath = attachmentStoragePath,
        attachmentSizeBytes = attachmentSizeBytes
    )

    private fun decryptBody(entity: MessageEntity): String = runCatching {
        contentCipher.decrypt(entity.body)
    }.getOrElse { "Encrypted message" }

    private suspend fun receive(peerId: String, wirePayload: ByteArray): Boolean =
        runCatching {
            val envelope = JSONObject(wirePayload.toString(Charsets.UTF_8))
            val localId = identity.current()?.publicId ?: error("Local identity is unavailable")
            require(envelope.optInt("protocolVersion", 0) in 1..2) { "Unsupported Internet envelope version" }
            if (envelope.optString("wireType", "message") == "deliveryReceipt") {
                receiveDeliveryReceipt(peerId, localId, envelope)
                return@runCatching true
            }
            require(envelope.optString("wireType", "message") == "message") { "Unsupported Internet wire type" }
            require(!envelope.optBoolean("testOnly", false)) { "Test-only envelope rejected" }
            require(envelope.optString("recipientId") == localId) { "Envelope recipient mismatch" }
            val senderId = envelope.optString("senderId")
            val contact = resolveInternetSender(
                peerId = peerId,
                senderId = senderId,
                senderInvitation = envelope.optString("senderInvitation")
            )
            val messageId = envelope.getString("messageId")
            require(messageId.isNotBlank()) { "Envelope message ID is missing" }
            val createdAt = envelope.optLong("createdAtEpochMs", 0L)
            val expiresAt = envelope.optLong("expiresAtEpochMs", 0L)
            require(createdAt > 0L && expiresAt >= createdAt && expiresAt >= System.currentTimeMillis()) {
                "Expired Internet envelope"
            }
            val ciphertext = Base64.decode(envelope.getString("encryptedPayload"), Base64.NO_WRAP)
            require(ciphertext.isNotEmpty() && ciphertext.size <= MAX_ENCRYPTED_PAYLOAD_BYTES) {
                "Invalid encrypted payload"
            }
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            if (dao.findById(messageId) != null) {
                sendDeliveryReceipt(contact, messageId, localId)
                return@runCatching true
            }
            val plaintext = sessions.decryptSerializedMessage(address, ciphertext)
            persistIncoming(messageId, contact.contactId, ciphertext, plaintext, createdAt, "INTERNET_DIRECT")
            sendDeliveryReceipt(contact, messageId, localId)
            true
        }.getOrDefault(false)

    private suspend fun receiveDeliveryReceipt(peerId: String, localId: String, receipt: JSONObject) {
        require(receipt.optString("recipientId") == localId) { "Delivery receipt recipient mismatch" }
        val contact = contacts.findByPeerId(peerId)
            ?: error("Delivery receipt came from an unknown peer")
        require(contact.contactId == receipt.optString("senderId")) { "Delivery receipt sender mismatch" }
        val messageId = receipt.optString("messageId")
        require(messageId.isNotBlank()) { "Delivery receipt message ID is missing" }
        val message = dao.findById(messageId) ?: return
        require(message.recipientId == contact.contactId) { "Delivery receipt does not match the recipient" }
        dao.updateMessage(message.copy(status = MessageStatus.DELIVERED))
    }

    private fun sendDeliveryReceipt(
        contact: org.ciphrchat.app.data.ContactEntity,
        messageId: String,
        localId: String
    ) {
        if (contact.relayAddress.isBlank() || contact.peerId.startsWith("local:")) return
        if (!p2p.connectPeer(contact.peerId, contact.relayAddress)) return
        p2p.sendControlMessage(
            peerId = contact.peerId,
            messageId = "receipt:$messageId",
            payload = InternetWireCodec.encodeDeliveryReceipt(
                messageId = messageId,
                senderId = localId,
                recipientId = contact.contactId
            )
        )
    }

    private suspend fun receiveLocal(
        envelope: OutboundEnvelope,
        transport: org.ciphrchat.app.transport.TransportKind
    ) {
        val localId = identity.current()?.publicId ?: return
        if (envelope.testOnly || envelope.protocolVersion !in 1..2 || envelope.recipientId != localId ||
            envelope.expiresAtEpochMs < System.currentTimeMillis() || envelope.hopLimit !in 0..16) return
        runCatching {
            val contact = resolveLocalSender(envelope.senderId, envelope.senderInvitation)
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            val plaintext = sessions.decryptSerializedMessage(address, envelope.encryptedPayload)
            persistIncoming(
                envelope.messageId,
                contact.contactId,
                envelope.encryptedPayload,
                plaintext,
                envelope.createdAtEpochMs,
                transport.name
            )
        }
    }

    private suspend fun resolveInternetSender(
        peerId: String,
        senderId: String,
        senderInvitation: String
    ): org.ciphrchat.app.data.ContactEntity {
        contacts.findByPeerId(peerId)?.let { existing ->
            require(existing.contactId == senderId) { "Authenticated peer identity mismatch" }
            return existing
        }
        require(senderInvitation.isNotBlank()) { "Unknown sender did not include a reciprocal invitation" }
        val invited = ReciprocalPairingPolicy.validate(
            senderId,
            peerId,
            InvitationCodec.decode(senderInvitation)
        )
        contacts.save(invited)
        return invited
    }

    private suspend fun resolveLocalSender(
        senderId: String,
        senderInvitation: String
    ): org.ciphrchat.app.data.ContactEntity {
        contacts.find(senderId)?.let { return it }
        require(senderInvitation.isNotBlank()) { "Unknown local sender did not include a reciprocal invitation" }
        val invited = ReciprocalPairingPolicy.validate(
            senderId,
            null,
            InvitationCodec.decode(senderInvitation)
        )
        contacts.save(invited)
        return invited
    }

    private suspend fun persistIncoming(
        messageId: String,
        senderId: String,
        ciphertext: ByteArray,
        plaintext: ByteArray,
        createdAtEpochMs: Long,
        transport: String
    ) {
        val decoded = MessageContentCodec.decode(plaintext)

        decoded.callSignalJson?.let { signalJson ->
            val signal = CallSignal.fromJson(signalJson)
            if (signal != null) {
                val contactName = contacts.find(senderId)?.displayName ?: "Contact ${senderId.takeLast(6)}"
                when (signal) {
                    is CallSignal.Offer -> audioCallManager.onIncomingOffer(signal, contactName)
                    is CallSignal.Answer -> audioCallManager.onCallAnswer(signal)
                    is CallSignal.IceCandidate -> audioCallManager.onRemoteIceCandidate(signal)
                    is CallSignal.Reject -> audioCallManager.onCallReject(signal)
                    is CallSignal.Hangup -> audioCallManager.onCallHangup(signal)
                    is CallSignal.Ringing -> {}
                }
                return
            }
        }

        val fileDescriptor = decoded.fileDescriptor
        if (fileDescriptor != null) {
            largeFileManager.registerDescriptor(fileDescriptor)
            val localId = identity.current()?.publicId ?: "local"
            dao.insertMessage(
                MessageEntity(
                    id = messageId,
                    conversationId = senderId,
                    senderId = senderId,
                    recipientId = localId,
                    body = contentCipher.encrypt("Large File: ${fileDescriptor.fileName} (${formatBytes(fileDescriptor.fileSize)})"),
                    encryptedPayload = ciphertext,
                    createdAtEpochMs = createdAtEpochMs,
                    direction = MessageDirection.INCOMING,
                    status = MessageStatus.DELIVERED,
                    selectedTransport = transport,
                    attachmentFileName = fileDescriptor.fileName,
                    attachmentMimeType = fileDescriptor.mimeType,
                    attachmentStoragePath = "large_file:${fileDescriptor.fileId}",
                    attachmentSizeBytes = fileDescriptor.fileSize,
                    attachmentSha256 = fileDescriptor.sha256
                )
            )
            return
        }

        val attachment = decoded.attachment
        val stored = attachment?.let {
            require(it.bytes.size <= AttachmentStore.MAX_ATTACHMENT_BYTES) { "Attachment exceeds the supported size" }
            attachmentStore.save(it.fileName, it.mimeType, it.bytes)
        }
        val localId = identity.current()?.publicId ?: "local"
        dao.insertMessage(
            MessageEntity(
                id = messageId,
                conversationId = senderId,
                senderId = senderId,
                recipientId = localId,
                body = contentCipher.encrypt(decoded.text ?: "Attachment: ${attachment?.fileName ?: "file"}"),
                encryptedPayload = ciphertext,
                createdAtEpochMs = createdAtEpochMs,
                direction = MessageDirection.INCOMING,
                status = MessageStatus.DELIVERED,
                selectedTransport = transport,
                attachmentFileName = attachment?.fileName,
                attachmentMimeType = attachment?.mimeType,
                attachmentStoragePath = stored?.path,
                attachmentSizeBytes = stored?.size ?: 0L,
                attachmentSha256 = stored?.sha256
            )
        )
    }

    private fun getUriFileSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            }
        }.getOrNull()?.takeIf { it > 0 } ?: runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }.getOrDefault(0L)
    }

    private fun getUriFileName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private suspend fun updateDelivery(messageId: String, status: MessageStatus) {
        if (messageId.isBlank()) return
        val message = dao.findById(messageId) ?: return
        val mergedStatus = DeliveryStatusPolicy.merge(message.status, status)
        if (mergedStatus == message.status) return
        dao.updateMessage(message.copy(status = mergedStatus))
        if (status == MessageStatus.QUEUED) retryScheduler.schedule()
    }

    private companion object {
        const val MAX_ENCRYPTED_PAYLOAD_BYTES = 6 * 1024 * 1024
    }
}
