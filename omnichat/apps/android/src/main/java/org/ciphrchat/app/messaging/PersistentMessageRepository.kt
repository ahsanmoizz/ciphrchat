package org.ciphrchat.app.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
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
import org.ciphrchat.app.files.FileTransferControl
import org.ciphrchat.app.files.FileTransferProgress
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
    private val audioCallManager: AudioCallManager,
    private val groupManager: org.ciphrchat.app.groups.GroupManager
) : MessageRepository {

    private val dao = database.messageDao()
    private val groupDao = database.groupDao()
    private val groupMessageDao = database.groupMessageDao()
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeInFlightMessages = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val activeInFlightDeliveries = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val groupFanOutSemaphore = kotlinx.coroutines.sync.Semaphore(4)
    private val directOutboxSemaphore = kotlinx.coroutines.sync.Semaphore(4)
    private val sentReconciliationAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val outboxSignal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    private val decryptedBodyCache = android.util.LruCache<String, String>(2000)

    init {
        networkScope.launch {
            dao.requeueFailedMessages()
            triggerOutboxFlush()
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
            p2p.mailboxReady.collect { isReady ->
                if (isReady) {
                    triggerOutboxFlush()
                }
            }
        }
        networkScope.launch {
            p2p.relayReservationReady.collect { isReady ->
                if (isReady) {
                    triggerOutboxFlush()
                }
            }
        }
        outboxScope.launch {
            outboxSignal.collect {
                processOutbox()
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

    fun triggerOutboxFlush() {
        outboxSignal.tryEmit(Unit)
    }

    suspend fun flushPendingOutbox(): Boolean = withContext(Dispatchers.IO) {
        processOutbox()
    }

    private suspend fun reconcileStaleSentMessages() {
        val now = System.currentTimeMillis()
        val staleSentDirect = dao.getMessagesByStatus(MessageStatus.SENT)
            .filter { it.direction == MessageDirection.OUTGOING && (now - it.createdAtEpochMs) > STALE_SENT_RECONCILIATION_THRESHOLD_MS }
        for (msg in staleSentDirect) {
            val attempts = sentReconciliationAttempts.getOrDefault(msg.id, 0)
            if (attempts < MAX_SENT_RECONCILIATION_ATTEMPTS) {
                sentReconciliationAttempts[msg.id] = attempts + 1
                dao.updateMessage(msg.copy(status = MessageStatus.QUEUED))
            } else {
                dao.updateMessage(msg.copy(status = MessageStatus.FAILED))
            }
        }

        val staleGroupDeliveries = groupMessageDao.getSentDeliveries()
            .filter { (now - it.lastAttemptEpochMs) > STALE_SENT_RECONCILIATION_THRESHOLD_MS }
        for (del in staleGroupDeliveries) {
            if (del.attempts < MAX_SENT_RECONCILIATION_ATTEMPTS) {
                groupMessageDao.updateDelivery(del.copy(status = MessageStatus.QUEUED, attempts = del.attempts + 1))
            } else {
                groupMessageDao.updateDelivery(del.copy(status = MessageStatus.FAILED))
            }
            updateGroupMessageAggregateStatus(del.groupMessageId)
        }
    }

    private suspend fun processOutbox(): Boolean {
        reconcileStaleSentMessages()
        val pendingDirect = dao.getMessagesPendingDelivery()
        val pendingGroup = groupMessageDao.getPendingDeliveries()
        if (pendingDirect.isEmpty() && pendingGroup.isEmpty()) return true

        var allDeliveredOrSent = true
        for (message in pendingDirect) {
            if (!activeInFlightMessages.add(message.id)) {
                continue
            }
            outboxScope.launch {
                try {
                    directOutboxSemaphore.withPermit {
                        val success = dispatchSingleOutboxMessage(message)
                        if (!success) {
                            allDeliveredOrSent = false
                        }
                    }
                } finally {
                    activeInFlightMessages.remove(message.id)
                }
            }
        }

        for (delivery in pendingGroup) {
            val key = "${delivery.groupMessageId}_${delivery.recipientPublicId}"
            if (!activeInFlightDeliveries.add(key)) {
                continue
            }
            outboxScope.launch {
                try {
                    groupFanOutSemaphore.withPermit {
                        val success = dispatchSingleGroupRecipientDelivery(delivery)
                        if (!success) {
                            allDeliveredOrSent = false
                        }
                    }
                } finally {
                    activeInFlightDeliveries.remove(key)
                }
            }
        }
        return allDeliveredOrSent
    }

    private suspend fun dispatchSingleGroupRecipientDelivery(delivery: org.ciphrchat.app.data.GroupRecipientDeliveryEntity): Boolean {
        val groupMessage = groupMessageDao.findById(delivery.groupMessageId) ?: return true
        if (delivery.status == MessageStatus.DELIVERED) return true

        val contact = contacts.find(delivery.recipientPublicId)
        if (contact == null) {
            groupMessageDao.updateDelivery(delivery.copy(status = MessageStatus.FAILED))
            updateGroupMessageAggregateStatus(delivery.groupMessageId)
            return false
        }

        val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
        if (!sessions.hasSession(address)) {
            sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
        }

        val attachment = if (groupMessage.attachmentStoragePath != null && groupMessage.attachmentFileName != null && groupMessage.attachmentMimeType != null) {
            val file = File(groupMessage.attachmentStoragePath)
            if (file.exists() && file.length() <= 5 * 1024 * 1024) {
                val bytes = runCatching {
                    attachmentStore.materialize(groupMessage.attachmentStoragePath, groupMessage.attachmentFileName).readBytes()
                }.getOrNull()
                if (bytes != null) {
                    MessageContentCodec.Attachment(groupMessage.attachmentFileName, groupMessage.attachmentMimeType, bytes)
                } else null
            } else null
        } else null

        val fileDescriptor = if (groupMessage.attachmentSha256 != null && attachment == null) {
            largeFileManager.findDescriptorBySha256(groupMessage.attachmentSha256)
        } else null

        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = groupMessage.groupId,
            groupMessageId = groupMessage.id,
            senderId = groupMessage.senderId,
            text = decryptGroupBody(groupMessage),
            attachment = attachment,
            fileDescriptor = fileDescriptor,
            isForwarded = groupMessage.isForwarded,
            createdAtEpochMs = groupMessage.createdAtEpochMs
        )

        val encodedPayload = MessageContentCodec.encodeGroupMessage(payload)
        val ciphertext = sessions.encryptMessage(address, encodedPayload)
        val senderInvitation = invitationService.createInvitation().getOrNull() ?: ""

        val envelope = OutboundEnvelope(
            protocolVersion = 2,
            messageId = "${groupMessage.id}_${contact.contactId}",
            recipientId = contact.contactId,
            senderId = groupMessage.senderId,
            createdAtEpochMs = groupMessage.createdAtEpochMs,
            expiresAtEpochMs = groupMessage.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = ciphertext.serialize(),
            testOnly = false,
            senderInvitation = senderInvitation
        )

        if (delivery.status == MessageStatus.QUEUED) {
            groupMessageDao.updateDelivery(delivery.copy(status = MessageStatus.ROUTING))
        }

        val sendResult = router.route(envelope)
        val updatedStatus = DeliveryStatusPolicy.merge(
            delivery.status,
            DeliveryStatusPolicy.statusFor(sendResult)
        )
        val transportName = when (sendResult) {
            is SendResult.Accepted -> sendResult.transport.name
            else -> null
        }

        val newStatus = if (updatedStatus == MessageStatus.DELIVERED || updatedStatus == MessageStatus.SENT) {
            updatedStatus
        } else {
            val newAttempts = delivery.attempts + 1
            if (newAttempts >= 5) MessageStatus.FAILED else MessageStatus.QUEUED
        }

        groupMessageDao.updateDelivery(
            delivery.copy(
                status = newStatus,
                selectedTransport = transportName ?: delivery.selectedTransport,
                attempts = delivery.attempts + 1,
                lastAttemptEpochMs = System.currentTimeMillis()
            )
        )
        updateGroupMessageAggregateStatus(delivery.groupMessageId)

        if (newStatus == MessageStatus.QUEUED) {
            retryScheduler.schedule()
            return false
        }
        return true
    }

    private suspend fun updateGroupMessageAggregateStatus(messageId: String) {
        val deliveries = groupMessageDao.getDeliveriesForMessage(messageId)
        if (deliveries.isEmpty()) return
        val aggregateStatus = when {
            deliveries.all { it.status == MessageStatus.DELIVERED } -> MessageStatus.DELIVERED
            deliveries.any { it.status == MessageStatus.SENT || it.status == MessageStatus.DELIVERED } -> MessageStatus.SENT
            deliveries.all { it.status == MessageStatus.FAILED } -> MessageStatus.FAILED
            else -> MessageStatus.QUEUED
        }
        val msg = groupMessageDao.findById(messageId)
        if (msg != null && msg.status != aggregateStatus) {
            groupMessageDao.updateMessage(msg.copy(status = aggregateStatus))
        }
    }

    private suspend fun dispatchSingleOutboxMessage(message: MessageEntity): Boolean {
        val dispatchStart = System.currentTimeMillis()
        MessageTimingTracker.recordDispatch(message.id, dispatchStart - message.createdAtEpochMs)

        val fresh = dao.findById(message.id) ?: return true
        if (fresh.status == MessageStatus.DELIVERED) return true

        if (fresh.encryptedPayload.isEmpty()) {
            dao.updateMessage(fresh.copy(status = MessageStatus.FAILED))
            return false
        }

        val senderInvitation = invitationService.createInvitation().getOrNull() ?: ""
        val envelope = OutboundEnvelope(
            protocolVersion = 2,
            messageId = fresh.id,
            recipientId = fresh.recipientId,
            senderId = fresh.senderId,
            createdAtEpochMs = fresh.createdAtEpochMs,
            expiresAtEpochMs = fresh.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = fresh.encryptedPayload,
            testOnly = false,
            senderInvitation = senderInvitation
        )

        if (fresh.status == MessageStatus.QUEUED) {
            dao.updateMessage(fresh.copy(status = MessageStatus.ROUTING))
        }

        val routeStart = System.currentTimeMillis()
        val sendResult = router.route(envelope)
        val routeDuration = System.currentTimeMillis() - routeStart
        MessageTimingTracker.recordRoute(fresh.id, sendResult.javaClass.simpleName, routeDuration, sendResult.toString())

        val current = dao.findById(fresh.id) ?: return false
        if (current.status == MessageStatus.DELIVERED) return true

        val updated = when (sendResult) {
            is SendResult.Accepted -> {
                MessageTimingTracker.recordRelayAccepted(fresh.id, System.currentTimeMillis() - fresh.createdAtEpochMs)
                current.copy(
                    status = DeliveryStatusPolicy.merge(
                        current.status,
                        DeliveryStatusPolicy.statusFor(sendResult)
                    ),
                    selectedTransport = sendResult.transport.name
                )
            }
            else -> {
                current.copy(
                    status = DeliveryStatusPolicy.merge(
                        current.status,
                        DeliveryStatusPolicy.statusFor(sendResult)
                    )
                )
            }
        }

        dao.updateMessage(updated)
        if (updated.status == MessageStatus.QUEUED) {
            retryScheduler.schedule()
            return false
        }
        return true
    }

    override fun conversations(): Flow<List<ConversationSummary>> = combine(
        dao.getLatestMessagesPerConversation(),
        groupDao.observeActiveGroups(),
        groupMessageDao.getLatestMessagesPerGroup(),
        contacts.observe()
    ) { latestMessages, activeGroups, latestGroupMessages, contactEntities ->
        val contactNames = contactEntities.associateBy { it.contactId }
        val directSummaries = latestMessages.map { message ->
            val conversationId = message.conversationId
            ConversationSummary(
                id = conversationId,
                contactName = contactNames[conversationId]?.displayName ?: "Contact ${conversationId.takeLast(8)}",
                contactId = conversationId,
                lastMessage = decryptBody(message),
                lastMessageEpochMs = message.createdAtEpochMs,
                isGroup = false,
                memberCount = 0
            )
        }

        val latestGroupByGroupId = latestGroupMessages.associateBy { it.groupId }
        val groupSummaries = activeGroups.map { group ->
            val latestMsg = latestGroupByGroupId[group.groupId]
            val lastMsgText = if (latestMsg != null) {
                decryptGroupBody(latestMsg)
            } else {
                "Group created"
            }
            val lastTime = latestMsg?.createdAtEpochMs ?: group.createdAtEpochMs
            ConversationSummary(
                id = group.groupId,
                contactName = group.name,
                contactId = group.groupId,
                lastMessage = lastMsgText,
                lastMessageEpochMs = lastTime,
                isGroup = true,
                memberCount = 0
            )
        }

        (directSummaries + groupSummaries).sortedByDescending { it.lastMessageEpochMs }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> {
        return if (conversationId.startsWith("group_")) {
            groupMessageDao.observeMessagesForGroup(conversationId).map { entities ->
                entities.map { entity ->
                    entity.copy(body = decryptGroupBody(entity)).toModel()
                }
            }
        } else {
            dao.getMessagesForConversation(conversationId).map { entities ->
                entities.map { entity ->
                    entity.copy(body = decryptBody(entity)).toModel()
                }
            }
        }
    }

    override fun isGroup(conversationId: String): Boolean {
        return conversationId.startsWith("group_")
    }

    override suspend fun send(
        conversationId: String,
        recipientId: String,
        text: String
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        if (conversationId.startsWith("group_")) {
            sendGroupText(conversationId, text)
        } else {
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
    }

    override suspend fun sendAttachment(
        conversationId: String,
        recipientId: String,
        uri: Uri
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        if (conversationId.startsWith("group_")) {
            sendGroupAttachment(conversationId, uri)
        } else {
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
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    val fileName = getUriFileName(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val localId = identity.current()?.publicId ?: "self"

                    val (descriptor, _) = largeFileManager.prepareDescriptor(
                        uri = uri,
                        fileName = fileName,
                        mimeType = mimeType,
                        senderId = localId,
                        recipientId = contact.contactId
                    ).getOrThrow()

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
    }

    override suspend fun createGroup(name: String, memberContactIds: List<String>): Result<String> = withContext(Dispatchers.IO) {
        val createResult = groupManager.createGroup(name, memberContactIds)
        createResult.fold(
            onSuccess = { (group, invitePayload) ->
                broadcastGroupInvite(invitePayload)
                Result.success(group.groupId)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun leaveGroup(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val leaveResult = groupManager.leaveGroup(groupId)
        leaveResult.fold(
            onSuccess = { leavePayload ->
                broadcastGroupLeave(leavePayload)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun broadcastGroupInvite(invite: MessageContentCodec.GroupInvitePayload) {
        val localId = identity.current()?.publicId ?: return
        val encodedInvite = MessageContentCodec.encodeGroupInvite(invite)
        val members = invite.memberIds.filter { it != localId }
        val senderInvitation = invitationService.createInvitation().getOrNull() ?: ""

        for (memberId in members) {
            runCatching {
                val contact = contacts.find(memberId) ?: return@runCatching
                val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
                if (!sessions.hasSession(address)) {
                    sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
                }
                val ciphertext = sessions.encryptMessage(address, encodedInvite)
                val envelope = OutboundEnvelope(
                    protocolVersion = 2,
                    messageId = UUID.randomUUID().toString(),
                    recipientId = contact.contactId,
                    senderId = localId,
                    createdAtEpochMs = System.currentTimeMillis(),
                    expiresAtEpochMs = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
                    hopLimit = 3,
                    encryptedPayload = ciphertext.serialize(),
                    testOnly = false,
                    senderInvitation = senderInvitation
                )
                router.route(envelope)
            }
        }
    }

    private suspend fun broadcastGroupLeave(leave: MessageContentCodec.GroupLeavePayload) {
        val localId = identity.current()?.publicId ?: return
        val encodedLeave = MessageContentCodec.encodeGroupLeave(leave)
        val members = groupDao.getActiveMembers(leave.groupId).filter { it.memberPublicId != localId }
        val senderInvitation = invitationService.createInvitation().getOrNull() ?: ""

        for (member in members) {
            runCatching {
                val contact = contacts.find(member.memberPublicId) ?: return@runCatching
                val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
                if (!sessions.hasSession(address)) {
                    sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
                }
                val ciphertext = sessions.encryptMessage(address, encodedLeave)
                val envelope = OutboundEnvelope(
                    protocolVersion = 2,
                    messageId = UUID.randomUUID().toString(),
                    recipientId = contact.contactId,
                    senderId = localId,
                    createdAtEpochMs = System.currentTimeMillis(),
                    expiresAtEpochMs = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
                    hopLimit = 3,
                    encryptedPayload = ciphertext.serialize(),
                    testOnly = false,
                    senderInvitation = senderInvitation
                )
                router.route(envelope)
            }
        }
    }

    private suspend fun sendGroupText(
        groupId: String,
        text: String,
        isForwarded: Boolean = false
    ): Result<ChatMessage> = runCatching {
        require(text.isNotBlank()) { "Message cannot be empty" }
        require(text.length <= 4_000) { "Message is too long" }
        val group = groupDao.findGroupById(groupId) ?: error("Group not found")
        require(group.isActive) { "Cannot send to a group you have left" }

        val localId = identity.current()?.publicId ?: error("Local identity is unavailable")
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val trimmed = text.trim()

        val groupMessage = org.ciphrchat.app.data.GroupMessageEntity(
            id = messageId,
            groupId = groupId,
            senderId = localId,
            body = contentCipher.encrypt(trimmed),
            createdAtEpochMs = now,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null,
            attachmentFileName = null,
            attachmentMimeType = null,
            attachmentStoragePath = null,
            attachmentSizeBytes = 0L,
            attachmentSha256 = null,
            isForwarded = isForwarded
        )
        groupMessageDao.insertMessage(groupMessage)
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(messageId, trimmed)
        }

        val members = groupDao.getActiveMembers(groupId).filter { it.memberPublicId != localId }
        if (members.isEmpty()) {
            groupMessageDao.updateMessage(groupMessage.copy(status = MessageStatus.DELIVERED))
        } else {
            val deliveries = members.map { member ->
                org.ciphrchat.app.data.GroupRecipientDeliveryEntity(
                    groupMessageId = messageId,
                    recipientPublicId = member.memberPublicId,
                    status = MessageStatus.QUEUED,
                    selectedTransport = null,
                    attempts = 0,
                    lastAttemptEpochMs = 0L
                )
            }
            groupMessageDao.insertDeliveries(deliveries)
        }

        groupDao.updateGroup(group.copy(updatedAtEpochMs = now))
        triggerOutboxFlush()
        groupMessage.copy(body = trimmed).toModel()
    }

    private suspend fun sendGroupAttachment(
        groupId: String,
        uri: Uri
    ): Result<ChatMessage> = runCatching {
        val group = groupDao.findGroupById(groupId) ?: error("Group not found")
        require(group.isActive) { "Cannot send to a group you have left" }

        val fileSize = getUriFileSize(uri)
        require(fileSize > 0) { "The selected attachment is empty" }
        require(fileSize <= FileTransferDescriptor.MAX_FILE_SIZE_BYTES) {
            "Attachment exceeds the 5 GiB maximum limit"
        }

        val localId = identity.current()?.publicId ?: error("Local identity is unavailable")
        val fileName = getUriFileName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val isVideoOrLargeFile = fileSize > AttachmentStore.MAX_ATTACHMENT_BYTES || mimeType.startsWith("video/")
        val (storedPath, sha256) = if (!isVideoOrLargeFile) {
            val input = attachmentStore.read(uri)
            val stored = attachmentStore.save(input.fileName, input.mimeType, input.bytes)
            Pair(stored.path, stored.sha256)
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val (descriptor, _) = largeFileManager.prepareDescriptor(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                senderId = localId,
                recipientId = groupId
            ).getOrThrow()
            Pair("large_file:${descriptor.fileId}", descriptor.sha256)
        }

        val plainText = if (isVideoOrLargeFile) "Large File: $fileName" else "Attachment: $fileName"
        val groupMessage = org.ciphrchat.app.data.GroupMessageEntity(
            id = messageId,
            groupId = groupId,
            senderId = localId,
            body = contentCipher.encrypt(plainText),
            createdAtEpochMs = now,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null,
            attachmentFileName = fileName,
            attachmentMimeType = mimeType,
            attachmentStoragePath = storedPath,
            attachmentSizeBytes = fileSize,
            attachmentSha256 = sha256,
            isForwarded = false
        )
        groupMessageDao.insertMessage(groupMessage)
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(messageId, plainText)
        }

        val members = groupDao.getActiveMembers(groupId).filter { it.memberPublicId != localId }
        if (members.isEmpty()) {
            groupMessageDao.updateMessage(groupMessage.copy(status = MessageStatus.DELIVERED))
        } else {
            val deliveries = members.map { member ->
                org.ciphrchat.app.data.GroupRecipientDeliveryEntity(
                    groupMessageId = messageId,
                    recipientPublicId = member.memberPublicId,
                    status = MessageStatus.QUEUED,
                    selectedTransport = null,
                    attempts = 0,
                    lastAttemptEpochMs = 0L
                )
            }
            groupMessageDao.insertDeliveries(deliveries)
        }

        groupDao.updateGroup(group.copy(updatedAtEpochMs = now))
        triggerOutboxFlush()
        groupMessage.copy(body = plainText).toModel()
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

        val relayHttpUrl = BuildConfig.CIPHRCHAT_FILE_RELAY_HTTP_URL
        if (relayHttpUrl.isBlank()) {
            largeFileManager.updateProgress(
                FileTransferProgress.Failed(descriptor.fileId, "File relay URL is not configured")
            )
            return@withContext null
        }

        val missing = largeFileManager.getLocalMissingChunks(destinationFile, descriptor.totalChunks)
        sendFileControlMessage(descriptor.senderId, FileTransferControl.Resume(descriptor.fileId, missing))

        largeFileManager.downloadFile(relayHttpUrl, descriptor, destinationFile).getOrNull()
    }

    override suspend fun clearConversation(conversationId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(conversationId.isNotBlank()) { "Conversation is unavailable" }
                if (conversationId.startsWith("group_")) {
                    val messages = groupMessageDao.listMessagesForGroup(conversationId)
                    synchronized(decryptedBodyCache) {
                        messages.forEach { decryptedBodyCache.remove(it.id) }
                    }
                    messages.mapNotNull { it.attachmentStoragePath }
                        .filter { !it.startsWith("large_file:") }
                        .distinct()
                        .forEach { path ->
                            val refs = dao.countOtherReferencesToAttachment(path, "") +
                                groupMessageDao.countOtherReferencesToAttachment(path, "")
                            if (refs <= 1) {
                                attachmentStore.delete(path)
                            }
                        }
                    messages.forEach { msg ->
                        groupMessageDao.deleteDeliveriesForMessage(msg.id)
                    }
                    groupMessageDao.deleteMessagesForGroup(conversationId)
                } else {
                    val messages = dao.listMessagesForConversation(conversationId)
                    val deleted = dao.deleteConversation(conversationId)
                    synchronized(decryptedBodyCache) {
                        messages.forEach { decryptedBodyCache.remove(it.id) }
                    }
                    messages.mapNotNull { it.attachmentStoragePath }
                        .filter { !it.startsWith("large_file:") }
                        .distinct()
                        .forEach { path ->
                            val refs = dao.countOtherReferencesToAttachment(path, "") +
                                groupMessageDao.countOtherReferencesToAttachment(path, "")
                            if (refs <= 1) {
                                attachmentStore.delete(path)
                            }
                        }
                    deleted
                }
            }
        }

    override suspend fun deleteMessage(messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(messageId.isNotBlank()) { "Message ID is required" }
                synchronized(decryptedBodyCache) {
                    decryptedBodyCache.remove(messageId)
                }
                val directMsg = dao.findById(messageId)
                if (directMsg != null) {
                    val path = directMsg.attachmentStoragePath
                    if (!path.isNullOrBlank() && !path.startsWith("large_file:")) {
                        val references = dao.countOtherReferencesToAttachment(path, messageId) +
                            groupMessageDao.countOtherReferencesToAttachment(path, messageId)
                        if (references == 0) {
                            attachmentStore.delete(path)
                        }
                    }
                    dao.deleteMessageById(messageId)
                    return@runCatching
                }

                val groupMsg = groupMessageDao.findById(messageId)
                if (groupMsg != null) {
                    val path = groupMsg.attachmentStoragePath
                    if (!path.isNullOrBlank() && !path.startsWith("large_file:")) {
                        val references = dao.countOtherReferencesToAttachment(path, messageId) +
                            groupMessageDao.countOtherReferencesToAttachment(path, messageId)
                        if (references == 0) {
                            attachmentStore.delete(path)
                        }
                    }
                    groupMessageDao.deleteDeliveriesForMessage(messageId)
                    groupMessageDao.deleteMessageById(messageId)
                    return@runCatching
                }
            }
        }

    override suspend fun retryMessage(messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(messageId.isNotBlank()) { "Message ID is required" }
                val directMsg = dao.findById(messageId)
                if (directMsg != null) {
                    if (directMsg.status != MessageStatus.DELIVERED) {
                        sentReconciliationAttempts.remove(messageId)
                        dao.updateMessage(directMsg.copy(status = MessageStatus.QUEUED))
                        triggerOutboxFlush()
                    }
                    return@runCatching
                }

                val groupMsg = groupMessageDao.findById(messageId)
                if (groupMsg != null) {
                    if (groupMsg.status != MessageStatus.DELIVERED) {
                        groupMessageDao.requeueFailedDeliveriesForMessage(messageId)
                        val deliveries = groupMessageDao.getDeliveriesForMessage(messageId)
                        deliveries.forEach { d ->
                            if (d.status != MessageStatus.DELIVERED) {
                                groupMessageDao.updateDelivery(d.copy(status = MessageStatus.QUEUED, attempts = 0))
                            }
                        }
                        groupMessageDao.updateMessage(groupMsg.copy(status = MessageStatus.QUEUED))
                        triggerOutboxFlush()
                    }
                    return@runCatching
                }
            }
        }

    override suspend fun forwardMessage(
        targetConversationId: String,
        targetRecipientId: String,
        originalMessage: ChatMessage
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            require(targetConversationId.isNotBlank()) {
                "Target conversation is required"
            }
            if (targetConversationId.startsWith("group_")) {
                forwardToGroup(targetConversationId, originalMessage)
            } else {
                forwardToDirect(targetConversationId, targetRecipientId, originalMessage)
            }
        }
    }

    private suspend fun forwardToGroup(
        groupId: String,
        originalMessage: ChatMessage
    ): ChatMessage {
        val group = groupDao.findGroupById(groupId) ?: error("Group not found")
        require(group.isActive) { "Cannot send to a group you have left" }
        val localId = identity.current()?.publicId ?: error("Local identity is unavailable")
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val text = originalMessage.body
        val groupMessage = org.ciphrchat.app.data.GroupMessageEntity(
            id = messageId,
            groupId = groupId,
            senderId = localId,
            body = contentCipher.encrypt(text),
            createdAtEpochMs = now,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null,
            attachmentFileName = originalMessage.attachmentFileName,
            attachmentMimeType = originalMessage.attachmentMimeType,
            attachmentStoragePath = originalMessage.attachmentStoragePath,
            attachmentSizeBytes = originalMessage.attachmentSizeBytes,
            attachmentSha256 = originalMessage.attachmentSha256,
            isForwarded = true
        )
        groupMessageDao.insertMessage(groupMessage)
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(messageId, text)
        }

        val members = groupDao.getActiveMembers(groupId).filter { it.memberPublicId != localId }
        if (members.isEmpty()) {
            groupMessageDao.updateMessage(groupMessage.copy(status = MessageStatus.DELIVERED))
        } else {
            val deliveries = members.map { member ->
                org.ciphrchat.app.data.GroupRecipientDeliveryEntity(
                    groupMessageId = messageId,
                    recipientPublicId = member.memberPublicId,
                    status = MessageStatus.QUEUED,
                    selectedTransport = null,
                    attempts = 0,
                    lastAttemptEpochMs = 0L
                )
            }
            groupMessageDao.insertDeliveries(deliveries)
        }

        groupDao.updateGroup(group.copy(updatedAtEpochMs = now))
        triggerOutboxFlush()
        return groupMessage.copy(body = text).toModel()
    }

    private suspend fun forwardToDirect(
        targetConversationId: String,
        targetRecipientId: String,
        originalMessage: ChatMessage
    ): ChatMessage {
        val contact = contacts.find(targetRecipientId)
            ?: error("Contact is not paired: scan or enter their invitation first")
        val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
        if (!sessions.hasSession(address)) {
            sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
        }

        val path = originalMessage.attachmentStoragePath
        val fileName = originalMessage.attachmentFileName
        return if (fileName != null && path != null) {
            if (path.startsWith("large_file:")) {
                val fileId = path.removePrefix("large_file:")
                val descriptor = largeFileManager.findDescriptorByFileId(fileId)
                    ?: error("Large file descriptor is unavailable")
                sendContent(
                    conversationId = targetConversationId,
                    recipientId = targetRecipientId,
                    address = address,
                    plaintext = MessageContentCodec.encodeFileDescriptor(descriptor),
                    body = "Large File: ${descriptor.fileName} (${formatBytes(descriptor.fileSize)})",
                    attachmentFileName = descriptor.fileName,
                    attachmentMimeType = descriptor.mimeType,
                    attachmentStoragePath = path,
                    attachmentSizeBytes = descriptor.fileSize,
                    attachmentSha256 = descriptor.sha256,
                    isForwarded = true
                )
            } else {
                val bytes = runCatching {
                    attachmentStore.materialize(path, fileName).readBytes()
                }.getOrNull() ?: error("Attachment file missing")
                sendContent(
                    conversationId = targetConversationId,
                    recipientId = targetRecipientId,
                    address = address,
                    plaintext = MessageContentCodec.encodeAttachment(
                        fileName,
                        originalMessage.attachmentMimeType ?: "application/octet-stream",
                        bytes
                    ),
                    body = originalMessage.body,
                    attachmentFileName = fileName,
                    attachmentMimeType = originalMessage.attachmentMimeType,
                    attachmentStoragePath = path,
                    attachmentSizeBytes = originalMessage.attachmentSizeBytes,
                    attachmentSha256 = originalMessage.attachmentSha256,
                    isForwarded = true
                )
            }
        } else {
            val text = originalMessage.body
            require(text.isNotBlank()) { "Message cannot be empty" }
            sendContent(
                conversationId = targetConversationId,
                recipientId = targetRecipientId,
                address = address,
                plaintext = MessageContentCodec.encodeText(text),
                body = text,
                isForwarded = true
            )
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
        attachmentSha256: String? = null,
        isForwarded: Boolean = false
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()

        val encryptStart = System.currentTimeMillis()
        val ciphertext = sessions.encryptMessage(address, plaintext)
        val encryptDuration = System.currentTimeMillis() - encryptStart
        MessageTimingTracker.recordEncrypt(messageId, encryptDuration)

        val localIdentity = identity.current()?.publicId ?: error("Local identity is unavailable")

        val entity = MessageEntity(
            id = messageId,
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
            attachmentSha256 = attachmentSha256,
            isForwarded = isForwarded
        )

        val persistStart = System.currentTimeMillis()
        dao.insertMessage(entity)
        val persistDuration = System.currentTimeMillis() - persistStart
        MessageTimingTracker.recordPersist(messageId, persistDuration)

        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(messageId, body)
        }

        // Asynchronously dispatch via Outbox without blocking foreground UI
        triggerOutboxFlush()

        return entity.toModel()
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
        attachmentSizeBytes = attachmentSizeBytes,
        attachmentSha256 = attachmentSha256,
        isForwarded = isForwarded
    )

    private fun decryptBody(entity: MessageEntity): String {
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.get(entity.id)?.let { return it }
        }
        val decrypted = runCatching {
            contentCipher.decrypt(entity.body)
        }.getOrElse { "Encrypted message" }
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(entity.id, decrypted)
        }
        return decrypted
    }

    private fun decryptGroupBody(entity: org.ciphrchat.app.data.GroupMessageEntity): String {
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.get(entity.id)?.let { return it }
        }
        val decrypted = runCatching {
            contentCipher.decrypt(entity.body)
        }.getOrElse { "Encrypted message" }
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(entity.id, decrypted)
        }
        return decrypted
    }

    private suspend fun receive(peerId: String, wirePayload: ByteArray): Boolean =
        runCatching {
            val receiveStart = System.currentTimeMillis()
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
            if (dao.findById(messageId) != null || groupMessageDao.findById(messageId) != null) {
                sendDeliveryReceipt(contact, messageId, localId)
                return@runCatching true
            }
            val plaintext = sessions.decryptSerializedMessage(address, ciphertext)
            persistIncoming(messageId, contact.contactId, ciphertext, plaintext, createdAt, "INTERNET_DIRECT")
            val receiveDuration = System.currentTimeMillis() - receiveStart
            MessageTimingTracker.recordReceive(messageId, receiveDuration)
            sendDeliveryReceipt(contact, messageId, localId)
            true
        }.getOrDefault(false)

    private suspend fun receiveDeliveryReceipt(peerId: String, localId: String, receipt: JSONObject) {
        val receiptStart = System.currentTimeMillis()
        require(receipt.optString("recipientId") == localId) { "Delivery receipt recipient mismatch" }
        val contact = contacts.findByPeerId(peerId)
            ?: error("Delivery receipt came from an unknown peer")
        require(contact.contactId == receipt.optString("senderId")) { "Delivery receipt sender mismatch" }
        val messageId = receipt.optString("messageId")
        require(messageId.isNotBlank()) { "Delivery receipt message ID is missing" }

        if (messageId.contains("_")) {
            val parts = messageId.split("_")
            if (parts.size >= 2) {
                val groupMessageId = parts[0]
                val recipientId = parts[1]
                val delivery = groupMessageDao.getDelivery(groupMessageId, recipientId)
                if (delivery != null) {
                    groupMessageDao.updateDelivery(delivery.copy(status = MessageStatus.DELIVERED))
                    updateGroupMessageAggregateStatus(groupMessageId)
                    val receiptDuration = System.currentTimeMillis() - receiptStart
                    MessageTimingTracker.recordReceipt(groupMessageId, receiptDuration)
                    return
                }
            }
        }

        val message = dao.findById(messageId) ?: return
        require(message.recipientId == contact.contactId) { "Delivery receipt does not match the recipient" }
        sentReconciliationAttempts.remove(messageId)
        dao.updateMessage(message.copy(status = MessageStatus.DELIVERED))
        val receiptDuration = System.currentTimeMillis() - receiptStart
        MessageTimingTracker.recordReceipt(messageId, receiptDuration)
        MessageTimingTracker.recordDelivered(messageId, System.currentTimeMillis() - message.createdAtEpochMs)
    }

    private fun sendDeliveryReceipt(
        contact: org.ciphrchat.app.data.ContactEntity,
        messageId: String,
        localId: String
    ) {
        if (contact.relayAddress.isBlank() || contact.peerId.startsWith("local:")) return
        if (!p2p.connectPeer(contact.peerId, contact.relayAddress)) return
        val receiptSent = p2p.sendControlMessage(
            peerId = contact.peerId,
            messageId = "receipt:$messageId",
            payload = InternetWireCodec.encodeDeliveryReceipt(
                messageId = messageId,
                senderId = localId,
                recipientId = contact.contactId
            )
        )
        if (receiptSent) {
            MessageTimingTracker.recordReceipt(messageId, 0L)
        }
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

    private suspend fun handleIncomingGroupMessage(
        groupMsg: MessageContentCodec.GroupMessagePayload,
        senderId: String,
        ciphertext: ByteArray,
        createdAtEpochMs: Long,
        transport: String
    ) {
        val localId = identity.current()?.publicId ?: "local"
        // 1. Idempotency: If message already received, acknowledge and return
        if (groupMessageDao.findById(groupMsg.groupMessageId) != null) {
            val contact = contacts.find(senderId)
            if (contact != null) {
                sendDeliveryReceipt(contact, "${groupMsg.groupMessageId}_$localId", localId)
            }
            return
        }

        // 2. Validate group, membership, and sender authenticity
        if (groupMsg.senderId != senderId) {
            return
        }
        val group = groupDao.findGroupById(groupMsg.groupId) ?: return
        val members = groupDao.getMembers(groupMsg.groupId)
        val senderMember = members.find { it.memberPublicId == senderId && it.membershipState == "ACTIVE" }
        if (senderMember == null && group.creatorPublicId != senderId) {
            return
        }

        // 3. Attachment / File
        val attachment = groupMsg.attachment
        val stored = attachment?.let {
            require(it.bytes.size <= AttachmentStore.MAX_ATTACHMENT_BYTES) { "Attachment exceeds supported size" }
            attachmentStore.save(it.fileName, it.mimeType, it.bytes)
        }

        groupMsg.fileDescriptor?.let { desc ->
            largeFileManager.registerDescriptor(desc)
        }

        val plainText = groupMsg.text ?: (if (groupMsg.fileDescriptor != null) "Large File: ${groupMsg.fileDescriptor.fileName}" else "Attachment: ${attachment?.fileName ?: "file"}")
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(groupMsg.groupMessageId, plainText)
        }

        val entity = org.ciphrchat.app.data.GroupMessageEntity(
            id = groupMsg.groupMessageId,
            groupId = groupMsg.groupId,
            senderId = senderId,
            body = contentCipher.encrypt(plainText),
            createdAtEpochMs = createdAtEpochMs,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED,
            selectedTransport = transport,
            attachmentFileName = attachment?.fileName ?: groupMsg.fileDescriptor?.fileName,
            attachmentMimeType = attachment?.mimeType ?: groupMsg.fileDescriptor?.mimeType,
            attachmentStoragePath = stored?.path ?: groupMsg.fileDescriptor?.let { "large_file:${it.fileId}" },
            attachmentSizeBytes = stored?.size ?: groupMsg.fileDescriptor?.fileSize ?: 0L,
            attachmentSha256 = stored?.sha256 ?: groupMsg.fileDescriptor?.sha256,
            isForwarded = groupMsg.isForwarded
        )
        groupMessageDao.insertMessage(entity)
        groupDao.updateGroup(group.copy(updatedAtEpochMs = createdAtEpochMs))

        // 4. Send delivery receipt back to sender
        val contact = contacts.find(senderId)
        if (contact != null) {
            sendDeliveryReceipt(contact, "${groupMsg.groupMessageId}_$localId", localId)
        }
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

        decoded.groupInvite?.let { invite ->
            groupManager.handleIncomingInvite(invite, senderId)
            return
        }

        decoded.groupLeave?.let { leave ->
            groupManager.handleIncomingLeave(leave, senderId)
            return
        }

        decoded.groupMessage?.let { groupMsg ->
            handleIncomingGroupMessage(groupMsg, senderId, ciphertext, createdAtEpochMs, transport)
            return
        }

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
                    is CallSignal.Ringing -> audioCallManager.onCallRinging(signal)
                }
                return
            }
        }

        decoded.fileControl?.let { control ->
            when (control) {
                is FileTransferControl.Offer -> {
                    handleIncomingFileOffer(
                        messageId = messageId,
                        senderId = senderId,
                        ciphertext = ciphertext,
                        createdAtEpochMs = createdAtEpochMs,
                        transport = transport,
                        fileDescriptor = control.descriptor
                    )
                    return
                }
                is FileTransferControl.Ready -> {
                    handleIncomingFileReady(senderId, control.fileId, control.missingChunks)
                    return
                }
                is FileTransferControl.Resume -> {
                    handleIncomingFileResume(senderId, control.fileId, control.missingChunks)
                    return
                }
                is FileTransferControl.Cancel -> {
                    largeFileManager.cancel(control.fileId)
                    return
                }
                is FileTransferControl.Complete -> {
                    largeFileManager.updateSenderTransferStatus(control.fileId, "COMPLETED")
                    return
                }
            }
        }

        val fileDescriptor = decoded.fileDescriptor
        if (fileDescriptor != null) {
            handleIncomingFileOffer(
                messageId = messageId,
                senderId = senderId,
                ciphertext = ciphertext,
                createdAtEpochMs = createdAtEpochMs,
                transport = transport,
                fileDescriptor = fileDescriptor
            )
            return
        }

        val attachment = decoded.attachment
        val stored = attachment?.let {
            require(it.bytes.size <= AttachmentStore.MAX_ATTACHMENT_BYTES) { "Attachment exceeds the supported size" }
            attachmentStore.save(it.fileName, it.mimeType, it.bytes)
        }
        val localId = identity.current()?.publicId ?: "local"
        val plainText = decoded.text ?: "Attachment: ${attachment?.fileName ?: "file"}"
        synchronized(decryptedBodyCache) {
            decryptedBodyCache.put(messageId, plainText)
        }
        dao.insertMessage(
            MessageEntity(
                id = messageId,
                conversationId = senderId,
                senderId = senderId,
                recipientId = localId,
                body = contentCipher.encrypt(plainText),
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

    private suspend fun handleIncomingFileOffer(
        messageId: String,
        senderId: String,
        ciphertext: ByteArray,
        createdAtEpochMs: Long,
        transport: String,
        fileDescriptor: FileTransferDescriptor
    ) {
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

        // 1. Send Signal-encrypted LARGE_FILE_READY to sender
        sendFileControlMessage(senderId, FileTransferControl.Ready(fileDescriptor.fileId))

        // 2. Start receiver download worker
        val relayHttpUrl = BuildConfig.CIPHRCHAT_FILE_RELAY_HTTP_URL
        if (relayHttpUrl.isNotBlank()) {
            val downloadDir = File(context.filesDir, "CiphrChat/downloads").apply { mkdirs() }
            val destinationFile = File(downloadDir, fileDescriptor.fileName)
            networkScope.launch {
                largeFileManager.downloadFile(relayHttpUrl, fileDescriptor, destinationFile)
            }
        } else {
            largeFileManager.updateProgress(
                FileTransferProgress.Failed(fileDescriptor.fileId, "File relay URL is not configured")
            )
        }
    }

    private suspend fun handleIncomingFileReady(
        senderId: String,
        fileId: String,
        requestedChunks: List<Int>?
    ) {
        val senderState = largeFileManager.getSenderTransfer(fileId) ?: return
        val relayHttpUrl = BuildConfig.CIPHRCHAT_FILE_RELAY_HTTP_URL
        if (relayHttpUrl.isBlank()) {
            largeFileManager.updateProgress(
                FileTransferProgress.Failed(fileId, "File relay URL is not configured")
            )
            return
        }

        val uri = Uri.parse(senderState.sourceUriString)
        val fileKey = Base64.decode(senderState.fileKeyBase64, Base64.NO_WRAP)
        networkScope.launch {
            largeFileManager.uploadFile(
                relayBaseHttpUrl = relayHttpUrl,
                uri = uri,
                descriptor = senderState.descriptor,
                fileKey = fileKey,
                missingChunkIndexes = requestedChunks?.toSet()
            )
        }
    }

    private suspend fun handleIncomingFileResume(
        senderId: String,
        fileId: String,
        missingChunks: List<Int>
    ) {
        handleIncomingFileReady(senderId, fileId, missingChunks)
    }

    private suspend fun sendFileControlMessage(
        recipientId: String,
        control: FileTransferControl
    ) {
        runCatching {
            val contact = contacts.find(recipientId) ?: return
            val address = SignalProtocolAddress(contact.contactId, contact.deviceId)
            if (!sessions.hasSession(address)) {
                sessions.processPreKeyBundle(address, InvitationCodec.toBundle(contact))
            }
            val payload = MessageContentCodec.encodeFileControl(control)
            val ciphertext = sessions.encryptMessage(address, payload)
            val localId = identity.current()?.publicId ?: "self"
            val messageId = UUID.randomUUID().toString()
            val envelope = OutboundEnvelope(
                messageId = messageId,
                senderId = localId,
                recipientId = contact.contactId,
                encryptedPayload = ciphertext.serialize(),
                createdAtEpochMs = System.currentTimeMillis(),
                expiresAtEpochMs = System.currentTimeMillis() + 60_000L,
                protocolVersion = 2,
                hopLimit = 8,
                testOnly = false,
                senderInvitation = invitationService.createInvitation().getOrNull() ?: ""
            )
            router.route(envelope)
        }
    }

    private fun getUriFileSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            }
        }.getOrNull()?.takeIf { it > 0 }
            ?: runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
            }.getOrNull()
            ?: 0L
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
        if (status == MessageStatus.DELIVERED) {
            sentReconciliationAttempts.remove(messageId)
        }
        val message = dao.findById(messageId) ?: return
        val mergedStatus = DeliveryStatusPolicy.merge(message.status, status)
        if (mergedStatus == message.status) return
        dao.updateMessage(message.copy(status = mergedStatus))
        if (status == MessageStatus.QUEUED) retryScheduler.schedule()
    }

    companion object {
        const val MAX_ENCRYPTED_PAYLOAD_BYTES = 6 * 1024 * 1024
        const val STALE_SENT_RECONCILIATION_THRESHOLD_MS = 60_000L
        const val MAX_SENT_RECONCILIATION_ATTEMPTS = 2
    }
}
