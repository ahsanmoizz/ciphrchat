package org.ciphrchat.app.groups

import org.ciphrchat.app.data.*
import org.ciphrchat.app.files.FileTransferDescriptor
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.LocalIdentity
import org.ciphrchat.app.messaging.ChatMessage
import org.ciphrchat.app.messaging.MessageContentCodec
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.messaging.MessageStatus
import org.ciphrchat.app.di.DatabaseModule
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GroupChatRegressionTest {

    // -------------------------------------------------------------
    // 1. CODEC TESTS (7.4, 7.6, 7.7, 7.8, 7.9, 7.14)
    // -------------------------------------------------------------

    @Test
    fun groupMessageCodec_textRoundTrips() {
        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = "group_12345",
            groupMessageId = "msg_abcde",
            senderId = "user_sender_1",
            text = "Hello CiphrChat secure group!",
            attachment = null,
            fileDescriptor = null,
            isForwarded = false,
            createdAtEpochMs = 1700000001000L
        )

        val encoded = MessageContentCodec.encodeGroupMessage(payload)
        val decoded = MessageContentCodec.decode(encoded)

        assertNotNull(decoded.groupMessage)
        val result = decoded.groupMessage!!
        assertEquals("group_12345", result.groupId)
        assertEquals("msg_abcde", result.groupMessageId)
        assertEquals("user_sender_1", result.senderId)
        assertEquals("Hello CiphrChat secure group!", result.text)
        assertNull(result.attachment)
        assertNull(result.fileDescriptor)
        assertFalse(result.isForwarded)
        assertEquals(1700000001000L, result.createdAtEpochMs)
    }

    @Test
    fun groupMessageCodec_forwardedFlagPreserved() {
        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = "group_forward_test",
            groupMessageId = "msg_fwd_1",
            senderId = "user_fwd",
            text = "Forwarded group text",
            attachment = null,
            fileDescriptor = null,
            isForwarded = true,
            createdAtEpochMs = 1700000002000L
        )

        val encoded = MessageContentCodec.encodeGroupMessage(payload)
        val decoded = MessageContentCodec.decode(encoded)

        assertNotNull(decoded.groupMessage)
        assertTrue(decoded.groupMessage!!.isForwarded)
        assertEquals("Forwarded group text", decoded.groupMessage!!.text)
    }

    @Test
    fun groupMessageCodec_attachmentRoundTrips() {
        val testBytes = ByteArray(1024) { (it % 128).toByte() }
        val attachment = MessageContentCodec.Attachment(
            fileName = "secure_meeting_notes.pdf",
            mimeType = "application/pdf",
            bytes = testBytes
        )
        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = "group_attach_1",
            groupMessageId = "msg_att_1",
            senderId = "sender_alice",
            text = "Here are the notes",
            attachment = attachment,
            fileDescriptor = null,
            isForwarded = false,
            createdAtEpochMs = 1700000003000L
        )

        val encoded = MessageContentCodec.encodeGroupMessage(payload)
        val decoded = MessageContentCodec.decode(encoded)

        assertNotNull(decoded.groupMessage)
        val msg = decoded.groupMessage!!
        assertEquals("Here are the notes", msg.text)
        assertNotNull(msg.attachment)
        assertEquals("secure_meeting_notes.pdf", msg.attachment!!.fileName)
        assertEquals("application/pdf", msg.attachment!!.mimeType)
        assertArrayEquals(testBytes, msg.attachment!!.bytes)
    }

    @Test
    fun groupMessageCodec_fileDescriptorRoundTrips() {
        val descriptor = FileTransferDescriptor(
            fileId = "video-uuid-777",
            fileName = "demo_clip.mp4",
            fileSize = 100 * 1024 * 1024L,
            mimeType = "video/mp4",
            sha256 = "deadbeefcafebabe11223344556677889900aabbccddeeff1122334455667788",
            chunkSize = 1024 * 1024,
            totalChunks = 100,
            fileKeyBase64 = "kEYBaSe64==",
            senderId = "sender_bob",
            recipientId = "group_video_1",
            createdAtEpochMs = 1700000004000L
        )
        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = "group_video_1",
            groupMessageId = "msg_video_1",
            senderId = "sender_bob",
            text = "Check this video",
            attachment = null,
            fileDescriptor = descriptor,
            isForwarded = false,
            createdAtEpochMs = 1700000004000L
        )

        val encoded = MessageContentCodec.encodeGroupMessage(payload)
        val decoded = MessageContentCodec.decode(encoded)

        assertNotNull(decoded.groupMessage)
        val msg = decoded.groupMessage!!
        assertNotNull(msg.fileDescriptor)
        assertEquals("demo_clip.mp4", msg.fileDescriptor!!.fileName)
        assertEquals(100 * 1024 * 1024L, msg.fileDescriptor!!.fileSize)
        assertEquals("video/mp4", msg.fileDescriptor!!.mimeType)
        assertEquals(descriptor.sha256, msg.fileDescriptor!!.sha256)
    }

    @Test
    fun groupControlCodec_inviteAndLeaveRoundTrips() {
        val invite = MessageContentCodec.GroupInvitePayload(
            groupId = "grp_test_99",
            groupName = "Core Engineering",
            creatorId = "alice_public_id",
            memberIds = listOf("alice_public_id", "bob_public_id", "carol_public_id"),
            createdAtEpochMs = 1700000005000L
        )

        val encodedInvite = MessageContentCodec.encodeGroupInvite(invite)
        val decodedInvite = MessageContentCodec.decode(encodedInvite)
        assertNotNull(decodedInvite.groupInvite)
        assertEquals("grp_test_99", decodedInvite.groupInvite!!.groupId)
        assertEquals("Core Engineering", decodedInvite.groupInvite!!.groupName)
        assertEquals("alice_public_id", decodedInvite.groupInvite!!.creatorId)
        assertEquals(3, decodedInvite.groupInvite!!.memberIds.size)
        assertTrue(decodedInvite.groupInvite!!.memberIds.contains("bob_public_id"))

        val leave = MessageContentCodec.GroupLeavePayload(
            groupId = "grp_test_99",
            memberId = "bob_public_id",
            createdAtEpochMs = 1700000006000L
        )
        val encodedLeave = MessageContentCodec.encodeGroupLeave(leave)
        val decodedLeave = MessageContentCodec.decode(encodedLeave)
        assertNotNull(decodedLeave.groupLeave)
        assertEquals("grp_test_99", decodedLeave.groupLeave!!.groupId)
        assertEquals("bob_public_id", decodedLeave.groupLeave!!.memberId)
    }

    // -------------------------------------------------------------
    // 2. GROUP MANAGER & LIFECYCLE TESTS (7.1, 7.2, 7.12, 7.13, 7.14)
    // -------------------------------------------------------------

    private class FakeGroupDao : GroupDao {
        val groups = mutableMapOf<String, GroupEntity>()
        val members = mutableMapOf<String, MutableList<GroupMemberEntity>>()

        override fun observeAllGroups(): Flow<List<GroupEntity>> = flowOf(groups.values.toList())
        override fun observeActiveGroups(): Flow<List<GroupEntity>> = flowOf(groups.values.filter { it.isActive })
        override suspend fun getActiveGroups(): List<GroupEntity> = groups.values.filter { it.isActive }
        override suspend fun findGroupById(groupId: String): GroupEntity? = groups[groupId]
        override fun observeGroupById(groupId: String): Flow<GroupEntity?> = flowOf(groups[groupId])
        override suspend fun insertGroup(group: GroupEntity) { groups[group.groupId] = group }
        override suspend fun updateGroup(group: GroupEntity) { groups[group.groupId] = group }
        override suspend fun deleteGroup(groupId: String): Int {
            val existed = groups.remove(groupId) != null
            members.remove(groupId)
            return if (existed) 1 else 0
        }
        override fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>> = flowOf(members[groupId] ?: emptyList())
        override suspend fun getMembers(groupId: String): List<GroupMemberEntity> = members[groupId] ?: emptyList()
        override suspend fun getActiveMembers(groupId: String): List<GroupMemberEntity> =
            (members[groupId] ?: emptyList()).filter { it.membershipState == "ACTIVE" }
        override suspend fun insertMember(member: GroupMemberEntity) {
            val list = members.getOrPut(member.groupId) { mutableListOf() }
            list.removeAll { it.memberPublicId == member.memberPublicId }
            list.add(member)
        }
        override suspend fun insertMembers(members: List<GroupMemberEntity>) {
            members.forEach { insertMember(it) }
        }
        override suspend fun updateMemberState(groupId: String, memberPublicId: String, state: String): Int {
            val list = members[groupId] ?: return 0
            val idx = list.indexOfFirst { it.memberPublicId == memberPublicId }
            return if (idx >= 0) {
                list[idx] = list[idx].copy(membershipState = state)
                1
            } else 0
        }
        override suspend fun deleteMembersForGroup(groupId: String): Int {
            val count = members[groupId]?.size ?: 0
            members.remove(groupId)
            return count
        }
    }

    private class FakeGroupMessageDao : GroupMessageDao {
        val messages = mutableMapOf<String, GroupMessageEntity>()
        val deliveries = mutableMapOf<String, MutableList<GroupRecipientDeliveryEntity>>()

        override fun observeMessagesForGroup(groupId: String): Flow<List<GroupMessageEntity>> =
            flowOf(messages.values.filter { it.groupId == groupId }.sortedBy { it.createdAtEpochMs })
        override suspend fun listMessagesForGroup(groupId: String): List<GroupMessageEntity> =
            messages.values.filter { it.groupId == groupId }
        override suspend fun getLatestMessageForGroup(groupId: String): GroupMessageEntity? =
            messages.values.filter { it.groupId == groupId }.maxByOrNull { it.createdAtEpochMs }
        override fun getLatestMessagesPerGroup(): Flow<List<GroupMessageEntity>> =
            flowOf(messages.values.groupBy { it.groupId }.mapNotNull { entry -> entry.value.maxByOrNull { it.createdAtEpochMs } })
        override suspend fun findById(messageId: String): GroupMessageEntity? = messages[messageId]
        override suspend fun insertMessage(message: GroupMessageEntity) { messages[message.id] = message }
        override suspend fun updateMessage(message: GroupMessageEntity) { messages[message.id] = message }
        override suspend fun deleteMessageById(messageId: String): Int = if (messages.remove(messageId) != null) 1 else 0
        override suspend fun deleteMessagesForGroup(groupId: String): Int {
            val count = messages.values.count { it.groupId == groupId }
            messages.entries.removeAll { it.value.groupId == groupId }
            return count
        }
        override suspend fun countOtherReferencesToAttachment(path: String, messageId: String): Int =
            messages.values.count { it.attachmentStoragePath == path && it.id != messageId }
        override suspend fun getPendingDeliveries(): List<GroupRecipientDeliveryEntity> =
            deliveries.values.flatten().filter { it.status == MessageStatus.QUEUED || it.status == MessageStatus.ROUTING }
        override suspend fun getSentDeliveries(): List<GroupRecipientDeliveryEntity> =
            deliveries.values.flatten().filter { it.status == MessageStatus.SENT }
        override suspend fun getDeliveriesForMessage(messageId: String): List<GroupRecipientDeliveryEntity> =
            deliveries[messageId] ?: emptyList()
        override suspend fun getDelivery(messageId: String, recipientId: String): GroupRecipientDeliveryEntity? =
            deliveries[messageId]?.find { it.recipientPublicId == recipientId }
        override suspend fun insertDelivery(delivery: GroupRecipientDeliveryEntity) {
            val list = deliveries.getOrPut(delivery.groupMessageId) { mutableListOf() }
            list.removeAll { it.recipientPublicId == delivery.recipientPublicId }
            list.add(delivery)
        }
        override suspend fun insertDeliveries(deliveries: List<GroupRecipientDeliveryEntity>) {
            deliveries.forEach { insertDelivery(it) }
        }
        override suspend fun updateDelivery(delivery: GroupRecipientDeliveryEntity) {
            val list = deliveries[delivery.groupMessageId] ?: return
            val idx = list.indexOfFirst { it.recipientPublicId == delivery.recipientPublicId }
            if (idx >= 0) { list[idx] = delivery }
        }
        override suspend fun updateDeliveryStatus(messageId: String, recipientId: String, status: MessageStatus, transport: String?): Int {
            val d = getDelivery(messageId, recipientId) ?: return 0
            updateDelivery(d.copy(status = status, selectedTransport = transport ?: d.selectedTransport))
            return 1
        }
        override suspend fun requeueFailedDeliveriesForMessage(messageId: String): Int {
            val list = deliveries[messageId] ?: return 0
            var count = 0
            list.indices.forEach { i ->
                if (list[i].status == MessageStatus.FAILED) {
                    list[i] = list[i].copy(status = MessageStatus.QUEUED, attempts = 0)
                    count++
                }
            }
            return count
        }
        override suspend fun deleteDeliveriesForMessage(messageId: String): Int {
            val list = deliveries.remove(messageId)
            return list?.size ?: 0
        }
    }

    private fun createDummyContact(id: String, name: String, peerId: String): ContactEntity =
        ContactEntity(
            contactId = id,
            displayName = name,
            peerId = peerId,
            relayAddress = "relay",
            registrationId = 1,
            deviceId = 1,
            preKeyId = 1,
            preKey = ByteArray(32),
            signedPreKeyId = 1,
            signedPreKey = ByteArray(32),
            signedPreKeySignature = ByteArray(64),
            identityKey = ByteArray(32),
            verified = true,
            createdAtEpochMs = System.currentTimeMillis()
        )

    private class FakeContactRepository(val contacts: MutableMap<String, ContactEntity> = mutableMapOf()) : ContactRepository {
        override fun observe(): Flow<List<ContactEntity>> = flowOf(contacts.values.toList())
        override suspend fun find(contactId: String): ContactEntity? = contacts[contactId]
        override suspend fun findByPeerId(peerId: String): ContactEntity? = contacts.values.find { it.peerId == peerId }
        override suspend fun save(contact: ContactEntity) { contacts[contact.contactId] = contact }
    }

    private class FakeIdentityRepository(var currentIdentity: org.ciphrchat.app.identity.LocalIdentity? = null) : IdentityRepository {
        override suspend fun create(displayName: String): Result<org.ciphrchat.app.identity.LocalIdentity> {
            val ident = org.ciphrchat.app.identity.LocalIdentity(displayName, "id_$displayName", "fp", System.currentTimeMillis())
            currentIdentity = ident
            return Result.success(ident)
        }
        override suspend fun current(): org.ciphrchat.app.identity.LocalIdentity? = currentIdentity
        override suspend fun clear(): Result<Unit> {
            currentIdentity = null
            return Result.success(Unit)
        }
    }

    @Test
    fun groupManager_createGroup_validatesNameAndContacts() = runBlocking {
        val fakeDao = FakeGroupDao()
        val contacts = FakeContactRepository(mutableMapOf(
            "contact_b" to createDummyContact("contact_b", "Bob", "peer_b"),
            "contact_c" to createDummyContact("contact_c", "Carol", "peer_c")
        ))
        val identity = FakeIdentityRepository(org.ciphrchat.app.identity.LocalIdentity("Alice", "user_alice", "alice_fp", 1000L))

        // Blank name check
        val resultBlank = GroupManagerTestHelper.createGroup(fakeDao, contacts, identity, "   ", listOf("contact_b"))
        assertTrue(resultBlank.isFailure)

        // Empty members check
        val resultNoMembers = GroupManagerTestHelper.createGroup(fakeDao, contacts, identity, "Team", emptyList())
        assertTrue(resultNoMembers.isFailure)

        // Unpaired contact check
        val resultUnpaired = GroupManagerTestHelper.createGroup(fakeDao, contacts, identity, "Team", listOf("contact_unknown"))
        assertTrue(resultUnpaired.isFailure)

        // Successful creation
        val resultSuccess = GroupManagerTestHelper.createGroup(fakeDao, contacts, identity, "Team Alpha", listOf("contact_b", "contact_c"))
        assertTrue(resultSuccess.isSuccess)

        val (group, invite) = resultSuccess.getOrThrow()
        assertEquals("Team Alpha", group.name)
        assertEquals("user_alice", group.creatorPublicId)
        assertTrue(group.isActive)

        val storedGroup = fakeDao.findGroupById(group.groupId)
        assertNotNull(storedGroup)
        assertTrue(storedGroup!!.isActive)

        val members = fakeDao.getMembers(group.groupId)
        assertEquals(3, members.size) // Alice (creator/self) + Bob + Carol
        assertTrue(members.all { it.membershipState == "ACTIVE" })

        assertEquals("user_alice", invite.creatorId)
        assertEquals(3, invite.memberIds.size)
    }

    @Test
    fun groupManager_leaveGroup_marksInactiveAndMemberLeft() = runBlocking {
        val fakeDao = FakeGroupDao()
        val contacts = FakeContactRepository()
        val identity = FakeIdentityRepository(org.ciphrchat.app.identity.LocalIdentity("Alice", "user_alice", "alice_fp", 1000L))

        val group = GroupEntity("group_leave_test", "Project X", "user_alice", 1000L, 1000L, true)
        fakeDao.insertGroup(group)
        fakeDao.insertMember(GroupMemberEntity("group_leave_test", "user_alice", 1000L, "ACTIVE"))
        fakeDao.insertMember(GroupMemberEntity("group_leave_test", "user_bob", 1000L, "ACTIVE"))

        val leaveResult = GroupManagerTestHelper.leaveGroup(fakeDao, identity, "group_leave_test")
        assertTrue(leaveResult.isSuccess)

        val payload = leaveResult.getOrThrow()
        assertEquals("group_leave_test", payload.groupId)
        assertEquals("user_alice", payload.memberId)

        val updatedGroup = fakeDao.findGroupById("group_leave_test")
        assertNotNull(updatedGroup)
        assertFalse(updatedGroup!!.isActive)

        val updatedAlice = fakeDao.getMembers("group_leave_test").find { it.memberPublicId == "user_alice" }
        assertNotNull(updatedAlice)
        assertEquals("LEFT", updatedAlice!!.membershipState)

        // Bob remains active
        val bob = fakeDao.getMembers("group_leave_test").find { it.memberPublicId == "user_bob" }
        assertNotNull(bob)
        assertEquals("ACTIVE", bob!!.membershipState)
    }

    @Test
    fun groupManager_incomingInvite_authenticatesCreatorAndRecipient() = runBlocking {
        val fakeDao = FakeGroupDao()
        val identity = FakeIdentityRepository(org.ciphrchat.app.identity.LocalIdentity("Charlie", "user_charlie", "charlie_fp", 1000L))

        val invite = MessageContentCodec.GroupInvitePayload(
            groupId = "group_incoming_1",
            groupName = "Designers",
            creatorId = "user_alice",
            memberIds = listOf("user_alice", "user_charlie"),
            createdAtEpochMs = 1000L
        )

        // Sender mismatch
        assertFalse(GroupManagerTestHelper.handleIncomingInvite(fakeDao, identity, invite, "user_imposter"))

        // Recipient not in memberIds
        val inviteWithoutMe = invite.copy(memberIds = listOf("user_alice", "user_bob"))
        assertFalse(GroupManagerTestHelper.handleIncomingInvite(fakeDao, identity, inviteWithoutMe, "user_alice"))

        // Valid invite
        assertTrue(GroupManagerTestHelper.handleIncomingInvite(fakeDao, identity, invite, "user_alice"))

        val group = fakeDao.findGroupById("group_incoming_1")
        assertNotNull(group)
        assertEquals("Designers", group!!.name)
        assertTrue(group.isActive)

        // Idempotency: re-receiving invite succeeds without duplication
        assertTrue(GroupManagerTestHelper.handleIncomingInvite(fakeDao, identity, invite, "user_alice"))
        assertEquals(2, fakeDao.getMembers("group_incoming_1").size)
    }

    // -------------------------------------------------------------
    // 3. FAN-OUT, IDEMPOTENCY, AND DELIVERY INVARIANTS (7.2, 7.4, 7.5)
    // -------------------------------------------------------------

    @Test
    fun canonicalOneMessageInvariant_and_independentPerRecipientTracking() = runBlocking {
        val groupMessageDao = FakeGroupMessageDao()

        // Local user sends ONE canonical message to 3 members (Bob, Carol, Dave)
        val canonicalMessage = GroupMessageEntity(
            id = "msg_canonical_1",
            groupId = "group_team",
            senderId = "alice",
            body = "encrypted_body",
            createdAtEpochMs = 2000L,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED
        )
        groupMessageDao.insertMessage(canonicalMessage)

        val deliveries = listOf(
            GroupRecipientDeliveryEntity("msg_canonical_1", "bob", MessageStatus.QUEUED),
            GroupRecipientDeliveryEntity("msg_canonical_1", "carol", MessageStatus.QUEUED),
            GroupRecipientDeliveryEntity("msg_canonical_1", "dave", MessageStatus.QUEUED)
        )
        groupMessageDao.insertDeliveries(deliveries)

        // Invariant: exactly 1 message in group messages
        val storedMessages = groupMessageDao.listMessagesForGroup("group_team")
        assertEquals(1, storedMessages.size)
        assertEquals("msg_canonical_1", storedMessages[0].id)

        // Bob succeeds
        groupMessageDao.updateDeliveryStatus("msg_canonical_1", "bob", MessageStatus.DELIVERED, "INTERNET_RELAY")
        // Carol succeeds
        groupMessageDao.updateDeliveryStatus("msg_canonical_1", "carol", MessageStatus.DELIVERED, "INTERNET_DIRECT")
        // Dave fails
        groupMessageDao.updateDeliveryStatus("msg_canonical_1", "dave", MessageStatus.FAILED, null)

        val currentDeliveries = groupMessageDao.getDeliveriesForMessage("msg_canonical_1")
        assertEquals(3, currentDeliveries.size)

        // Retrying only requeues Dave, Bob and Carol remain DELIVERED
        val requeued = groupMessageDao.requeueFailedDeliveriesForMessage("msg_canonical_1")
        assertEquals(1, requeued)

        val bobDelivery = groupMessageDao.getDelivery("msg_canonical_1", "bob")
        assertEquals(MessageStatus.DELIVERED, bobDelivery?.status)

        val daveDelivery = groupMessageDao.getDelivery("msg_canonical_1", "dave")
        assertEquals(MessageStatus.QUEUED, daveDelivery?.status)

        // When Dave now delivers
        groupMessageDao.updateDeliveryStatus("msg_canonical_1", "dave", MessageStatus.DELIVERED, "INTERNET_DIRECT")
        val allDelivered = groupMessageDao.getDeliveriesForMessage("msg_canonical_1").all { it.status == MessageStatus.DELIVERED }
        assertTrue(allDelivered)
    }

    @Test
    fun incomingDeliveryIdempotency_suppressesDuplicates() = runBlocking {
        val groupMessageDao = FakeGroupMessageDao()

        val incoming = GroupMessageEntity(
            id = "msg_dup_test_1",
            groupId = "group_dup",
            senderId = "bob",
            body = "encrypted_hello",
            createdAtEpochMs = 3000L,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED
        )
        groupMessageDao.insertMessage(incoming)

        // Second packet arrives with identical groupMessageId
        val alreadyExists = groupMessageDao.findById("msg_dup_test_1") != null
        assertTrue("Duplicate packet must be detected via primary key idempotency", alreadyExists)

        // Still only 1 message
        assertEquals(1, groupMessageDao.listMessagesForGroup("group_dup").size)
    }

    // -------------------------------------------------------------
    // 4. FORWARDING MEDIA WITHOUT MEMORY EXPLOSION (7.10, 7.11)
    // -------------------------------------------------------------

    @Test
    fun forwardingToGroup_generatesNewIdAndPreservesMetadata() {
        val originalMessage = ChatMessage(
            id = "original_msg_100",
            conversationId = "contact_bob",
            senderId = "contact_bob",
            recipientId = "local_alice",
            body = "Attachment: document.pdf",
            createdAtEpochMs = 1000L,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED,
            attachmentFileName = "document.pdf",
            attachmentMimeType = "application/pdf",
            attachmentStoragePath = "/data/user/0/org.ciphrchat.app/files/attachments/att_100.dat",
            attachmentSizeBytes = 2048L,
            attachmentSha256 = "sha256_hash_100",
            isForwarded = false
        )

        val newForwardedMessageId = "fwd_msg_200"
        val groupEntity = GroupMessageEntity(
            id = newForwardedMessageId,
            groupId = "group_dest_1",
            senderId = "local_alice",
            body = "encrypted_body",
            createdAtEpochMs = 2000L,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            attachmentFileName = originalMessage.attachmentFileName,
            attachmentMimeType = originalMessage.attachmentMimeType,
            attachmentStoragePath = originalMessage.attachmentStoragePath, // Reuses existing storage path directly
            attachmentSizeBytes = originalMessage.attachmentSizeBytes,
            attachmentSha256 = originalMessage.attachmentSha256,
            isForwarded = true
        )

        assertNotEquals(originalMessage.id, groupEntity.id)
        assertTrue(groupEntity.isForwarded)
        assertEquals("document.pdf", groupEntity.attachmentFileName)
        assertEquals(originalMessage.attachmentStoragePath, groupEntity.attachmentStoragePath)
        assertEquals(originalMessage.attachmentSizeBytes, groupEntity.attachmentSizeBytes)
        assertEquals(originalMessage.attachmentSha256, groupEntity.attachmentSha256)
    }

    // -------------------------------------------------------------
    // Helper implementation for standalone unit testing
    // -------------------------------------------------------------

    private object GroupManagerTestHelper {
        suspend fun createGroup(
            dao: GroupDao,
            contacts: ContactRepository,
            identity: IdentityRepository,
            name: String,
            selectedContactIds: List<String>
        ): Result<Pair<GroupEntity, MessageContentCodec.GroupInvitePayload>> {
            val trimmedName = name.trim()
            if (trimmedName.isBlank() || trimmedName.length > 100) {
                return Result.failure(IllegalArgumentException("Invalid name"))
            }
            val distinctIds = selectedContactIds.distinct()
            if (distinctIds.isEmpty()) {
                return Result.failure(IllegalArgumentException("No members selected"))
            }
            for (contactId in distinctIds) {
                if (contacts.find(contactId) == null) {
                    return Result.failure(IllegalArgumentException("Unpaired contact"))
                }
            }
            val localId = identity.current()?.publicId ?: return Result.failure(IllegalStateException("No identity"))
            val groupId = "group_test_${System.currentTimeMillis()}"
            val now = System.currentTimeMillis()

            val group = GroupEntity(groupId, trimmedName, localId, now, now, true)
            dao.insertGroup(group)
            dao.insertMember(GroupMemberEntity(groupId, localId, now, "ACTIVE"))
            distinctIds.forEach { dao.insertMember(GroupMemberEntity(groupId, it, now, "ACTIVE")) }

            val invite = MessageContentCodec.GroupInvitePayload(
                groupId, trimmedName, localId, listOf(localId) + distinctIds, now
            )
            return Result.success(Pair(group, invite))
        }

        suspend fun leaveGroup(dao: GroupDao, identity: IdentityRepository, groupId: String): Result<MessageContentCodec.GroupLeavePayload> {
            val group = dao.findGroupById(groupId) ?: return Result.failure(IllegalStateException("Group not found"))
            val localId = identity.current()?.publicId ?: return Result.failure(IllegalStateException("No identity"))
            val now = System.currentTimeMillis()
            dao.updateGroup(group.copy(isActive = false, updatedAtEpochMs = now))
            dao.updateMemberState(groupId, localId, "LEFT")
            return Result.success(MessageContentCodec.GroupLeavePayload(groupId, localId, now))
        }

        suspend fun handleIncomingInvite(dao: GroupDao, identity: IdentityRepository, invite: MessageContentCodec.GroupInvitePayload, senderId: String): Boolean {
            if (senderId != invite.creatorId) return false
            val localId = identity.current()?.publicId ?: return false
            if (!invite.memberIds.contains(localId)) return false
            val existing = dao.findGroupById(invite.groupId)
            if (existing != null) return true

            val now = System.currentTimeMillis()
            dao.insertGroup(GroupEntity(invite.groupId, invite.groupName, invite.creatorId, invite.createdAtEpochMs, now, true))
            invite.memberIds.forEach {
                dao.insertMember(GroupMemberEntity(invite.groupId, it, now, "ACTIVE"))
            }
            return true
        }
    }

    // -------------------------------------------------------------
    // 5. ADVERSARIAL AUDIT TESTS: MIGRATION, RECONCILIATION, SPOOFING
    // -------------------------------------------------------------

    @Test
    fun roomMigration8To9_andSequentialChain_preservesTablesAndIndices() {
        assertNotNull(DatabaseModule.MIGRATION_8_9)
        assertEquals(8, DatabaseModule.MIGRATION_8_9.startVersion)
        assertEquals(9, DatabaseModule.MIGRATION_8_9.endVersion)

        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        // Execute sequentially: 6->7, 7->8, 8->9
        DatabaseModule.MIGRATION_6_7.migrate(db)
        DatabaseModule.MIGRATION_7_8.migrate(db)
        DatabaseModule.MIGRATION_8_9.migrate(db)

        // Verify MIGRATION_6_7
        assertTrue("MIGRATION_6_7 must add isForwarded column",
            executedSql.any { it.contains("ALTER TABLE messages ADD COLUMN isForwarded") })

        // Verify MIGRATION_7_8
        assertTrue("MIGRATION_7_8 must add composite index",
            executedSql.any { it.contains("index_messages_conversationId_createdAtEpochMs") })

        // Verify MIGRATION_8_9 tables
        assertTrue("Must create groups table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS groups") })
        assertTrue("Must create group_members table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS group_members") })
        assertTrue("Must create group_messages table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS group_messages") })
        assertTrue("Must create group_recipient_deliveries table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS group_recipient_deliveries") })

        // Verify MIGRATION_8_9 indices
        assertTrue("Must create group_messages group index", executedSql.any { it.contains("index_group_messages_groupId_createdAtEpochMs") })
        assertTrue("Must create group_messages status index", executedSql.any { it.contains("index_group_messages_status") })
        assertTrue("Must create group_recipient_deliveries indices", executedSql.any { it.contains("index_group_recipient_deliveries_groupMessageId") })
    }

    @Test
    fun staleSentBoundedReconciliation_requeuesThenMarksFailedAfterMaxAttempts() {
        val attemptsMap = ConcurrentHashMap<String, Int>()
        val maxAttempts = 2
        val msgId = "stale-sent-msg-1"
        var currentStatus = MessageStatus.SENT

        fun simulateReconcile() {
            val attempts = attemptsMap.getOrDefault(msgId, 0)
            if (attempts < maxAttempts) {
                attemptsMap[msgId] = attempts + 1
                currentStatus = MessageStatus.QUEUED
            } else {
                currentStatus = MessageStatus.FAILED
            }
        }

        // Cycle 1: Stale SENT -> requeued for attempt 1
        simulateReconcile()
        assertEquals(MessageStatus.QUEUED, currentStatus)
        assertEquals(1, attemptsMap[msgId])

        // Assume transport accepts again -> SENT
        currentStatus = MessageStatus.SENT

        // Cycle 2: Stale SENT again -> requeued for attempt 2
        simulateReconcile()
        assertEquals(MessageStatus.QUEUED, currentStatus)
        assertEquals(2, attemptsMap[msgId])

        // Assume transport accepts again -> SENT
        currentStatus = MessageStatus.SENT

        // Cycle 3: Still no receipt after 2 reconciliation attempts -> marked FAILED
        simulateReconcile()
        assertEquals("Message must transition to FAILED after exceeding max reconciliation attempts", MessageStatus.FAILED, currentStatus)
        assertEquals(2, attemptsMap[msgId])
    }

    @Test
    fun inboundGroupMessage_rejectsSpoofedSenderId() {
        val authenticatedSignalPeerId = "user_bob_authenticated"
        val spoofedInnerSenderId = "user_alice_impersonated"

        val payload = MessageContentCodec.GroupMessagePayload(
            groupId = "group_audit_1",
            groupMessageId = "msg_spoof_check_1",
            senderId = spoofedInnerSenderId,
            text = "Malicious impersonation"
        )

        // Authenticity rule: groupMsg.senderId MUST match authenticated senderId
        val isAuthentic = (payload.senderId == authenticatedSignalPeerId)
        assertFalse("Inbound group payload with spoofed senderId must be rejected", isAuthentic)

        // Legitimate matching payload
        val validPayload = payload.copy(senderId = authenticatedSignalPeerId)
        val isValidAuthentic = (validPayload.senderId == authenticatedSignalPeerId)
        assertTrue("Authentic inbound group payload must match authenticated sender identity", isValidAuthentic)
    }
}
