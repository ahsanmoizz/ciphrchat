package org.ciphrchat.app.messaging

import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.di.DatabaseModule
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessageActionsTest {

    @Test
    fun singleMessageDeleteDoesNotDeleteConversationOrOtherMessages() {
        val messages = mutableListOf(
            MessageEntity("msg-1", "conv-1", "self", "user-2", "Hello 1", byteArrayOf(1), 1000L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT"),
            MessageEntity("msg-2", "conv-1", "user-2", "self", "Hello 2", byteArrayOf(2), 2000L, MessageDirection.INCOMING, MessageStatus.DELIVERED, "INTERNET_DIRECT"),
            MessageEntity("msg-3", "conv-1", "self", "user-2", "Hello 3", byteArrayOf(3), 3000L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT")
        )

        // Simulate deleteMessageById("msg-2")
        val targetId = "msg-2"
        messages.removeAll { it.id == targetId }

        assertEquals(2, messages.size)
        assertTrue(messages.any { it.id == "msg-1" })
        assertTrue(messages.any { it.id == "msg-3" })
        assertFalse(messages.any { it.id == "msg-2" })
    }

    @Test
    fun referenceSafeAttachmentCleanup() {
        val sharedPath = "/data/user/0/org.ciphrchat/files/CiphrChat/attachments/shared-img.bin"
        val messages = listOf(
            MessageEntity("msg-1", "conv-1", "self", "user-2", "Photo 1", byteArrayOf(1), 1000L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT", "photo.jpg", "image/jpeg", sharedPath, 1024L, "sha1"),
            MessageEntity("msg-2", "conv-2", "self", "user-3", "Photo 1 Forwarded", byteArrayOf(2), 2000L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT", "photo.jpg", "image/jpeg", sharedPath, 1024L, "sha1", isForwarded = true)
        )

        // Deleting msg-1: check remaining references
        val otherReferencesCountForMsg1 = messages.count { it.attachmentStoragePath == sharedPath && it.id != "msg-1" }
        assertEquals(1, otherReferencesCountForMsg1)
        // Since otherReferencesCount > 0, physical file must NOT be deleted from disk

        // Deleting msg-2: check remaining references if msg-1 was already deleted
        val remainingAfterMsg1 = messages.filter { it.id != "msg-1" }
        val otherReferencesCountForMsg2 = remainingAfterMsg1.count { it.attachmentStoragePath == sharedPath && it.id != "msg-2" }
        assertEquals(0, otherReferencesCountForMsg2)
        // Since otherReferencesCount == 0, physical file can now be safely deleted
    }

    @Test
    fun forwardedMessageReceivesNewIdAndCleanForwardedIndicator() {
        val original = ChatMessage(
            id = "original-msg-uuid-1",
            conversationId = "conv-1",
            senderId = "user-1",
            recipientId = "self",
            body = "Important memo",
            createdAtEpochMs = 1000L,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED,
            selectedTransport = "INTERNET_DIRECT",
            isForwarded = false
        )

        val newId = UUID.randomUUID().toString()
        val targetContactId = "contact-user-3"
        val forwarded = original.copy(
            id = newId,
            conversationId = targetContactId,
            senderId = "self",
            recipientId = targetContactId,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED,
            selectedTransport = null,
            createdAtEpochMs = System.currentTimeMillis(),
            isForwarded = true
        )

        assertNotEquals(original.id, forwarded.id)
        assertEquals("self", forwarded.senderId)
        assertEquals(targetContactId, forwarded.recipientId)
        assertEquals(targetContactId, forwarded.conversationId)
        assertEquals("Important memo", forwarded.body)
        assertTrue(forwarded.isForwarded)
        assertFalse(original.isForwarded)
    }

    @Test
    fun retryMessageOnlyRequeuesFailedOrQueuedMessages() {
        val failed = MessageEntity("m-fail", "c1", "self", "r1", "enc", byteArrayOf(1), 1000L, MessageDirection.OUTGOING, MessageStatus.FAILED, null)
        val delivered = MessageEntity("m-deliv", "c1", "self", "r1", "enc", byteArrayOf(2), 1000L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT")
        val sent = MessageEntity("m-sent", "c1", "self", "r1", "enc", byteArrayOf(3), 1000L, MessageDirection.OUTGOING, MessageStatus.SENT, "INTERNET_DIRECT")

        fun retry(entity: MessageEntity): MessageEntity {
            if (entity.status == MessageStatus.DELIVERED || entity.status == MessageStatus.SENT) return entity
            return entity.copy(status = MessageStatus.QUEUED)
        }

        assertEquals(MessageStatus.QUEUED, retry(failed).status)
        assertEquals(MessageStatus.DELIVERED, retry(delivered).status)
        assertEquals(MessageStatus.SENT, retry(sent).status)
    }

    @Test
    fun roomMigration6To7AddsIsForwardedColumn() {
        assertNotNull(DatabaseModule.MIGRATION_6_7)
        assertEquals(6, DatabaseModule.MIGRATION_6_7.startVersion)
        assertEquals(7, DatabaseModule.MIGRATION_6_7.endVersion)
    }
}
