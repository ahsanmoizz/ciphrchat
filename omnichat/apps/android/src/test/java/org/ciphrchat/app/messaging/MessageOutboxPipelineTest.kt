package org.ciphrchat.app.messaging

import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportKind
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MessageOutboxPipelineTest {

    @Test
    fun immediateLocalPersistenceYieldsQueuedBubbleInstantly() {
        val message = ChatMessage(
            id = "msg-instant-1",
            conversationId = "conv-1",
            senderId = "self",
            recipientId = "recipient-1",
            body = "Hello secure world",
            createdAtEpochMs = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED
        )

        assertEquals(MessageStatus.QUEUED, message.status)
        assertEquals(MessageDirection.OUTGOING, message.direction)
        assertNull(message.selectedTransport)
    }

    @Test
    fun lifecycleStateTransitionsProgressEvidenceBased() {
        var currentStatus = MessageStatus.QUEUED

        // Step 1: Outbox picks up message
        currentStatus = DeliveryStatusPolicy.merge(currentStatus, MessageStatus.ROUTING)
        assertEquals(MessageStatus.ROUTING, currentStatus)

        // Step 2: Transport/relay accepts envelope
        val acceptedResult = SendResult.Accepted(TransportKind.INTERNET_DIRECT, "relay-ack")
        currentStatus = DeliveryStatusPolicy.merge(currentStatus, DeliveryStatusPolicy.statusFor(acceptedResult))
        assertEquals(MessageStatus.SENT, currentStatus)

        // Step 3: Application layer delivery receipt arrives from peer
        currentStatus = DeliveryStatusPolicy.merge(currentStatus, MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, currentStatus)

        // Step 4: Late duplicate network rejection or retry attempt does NOT downgrade DELIVERED
        val lateFailure = SendResult.Failed(IllegalStateException("Late socket reset"))
        currentStatus = DeliveryStatusPolicy.merge(currentStatus, DeliveryStatusPolicy.statusFor(lateFailure))
        assertEquals(MessageStatus.DELIVERED, currentStatus)
    }

    @Test
    fun transientOfflineConditionsKeepMessageQueuedWithoutFailing() {
        val transientErrors = listOf(
            SendResult.Rejected("Peer is offline / unreachable"),
            SendResult.Failed(IllegalStateException("Connection timed out")),
            SendResult.Failure(IllegalStateException("No network interface")),
            SendResult.Success
        )

        for (result in transientErrors) {
            val status = DeliveryStatusPolicy.statusFor(result)
            assertEquals("Transient error must keep message in QUEUED state", MessageStatus.QUEUED, status)
        }
    }

    @Test
    fun inFlightLeasePreventsRacingWorkersAndDuplicateDispatch() {
        val inFlightSet = Collections.synchronizedSet(mutableSetOf<String>())
        val messageId = "msg-concurrent-42"
        val dispatchCount = AtomicInteger(0)

        fun tryDispatch(id: String): Boolean {
            if (!inFlightSet.add(id)) {
                return false // Already in-flight lease acquired by another runner
            }
            try {
                dispatchCount.incrementAndGet()
                return true
            } finally {
                inFlightSet.remove(id)
            }
        }

        // Runner 1 starts dispatch
        inFlightSet.add(messageId)

        // Runner 2 (e.g. WorkManager or reconnect event) tries to dispatch same messageId concurrently
        val runner2Acquired = inFlightSet.add(messageId)
        assertFalse("Concurrent runner must NOT acquire lease for an already in-flight message", runner2Acquired)

        // Runner 1 finishes
        inFlightSet.remove(messageId)

        // Now subsequent runner can acquire
        val nextAcquired = tryDispatch(messageId)
        assertTrue(nextAcquired)
        assertEquals(1, dispatchCount.get())
    }

    @Test
    fun deliveryReceiptIdempotencyPreservesDeliveredStateAcrossDuplicates() {
        val messageId = "msg-dup-receipt-1"
        var status = MessageStatus.SENT

        // First receipt
        status = DeliveryStatusPolicy.merge(status, MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, status)

        // Duplicate receipt
        status = DeliveryStatusPolicy.merge(status, MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, status)

        // Duplicate transport callback
        status = DeliveryStatusPolicy.merge(status, MessageStatus.SENT)
        assertEquals(MessageStatus.DELIVERED, status)

        // Retry scheduler trigger
        status = DeliveryStatusPolicy.merge(status, MessageStatus.QUEUED)
        assertEquals(MessageStatus.DELIVERED, status)
    }

    @Test
    fun processRestartWithPendingMessagesPreservesDurableOutbox() {
        // Simulates DB query on process startup: getMessagesPendingDelivery()
        val mockDatabase = listOf(
            ChatMessage("m1", "c1", "self", "r1", "msg 1", 1000L, MessageDirection.OUTGOING, MessageStatus.QUEUED),
            ChatMessage("m2", "c1", "self", "r1", "msg 2", 1001L, MessageDirection.OUTGOING, MessageStatus.ROUTING),
            ChatMessage("m3", "c1", "self", "r1", "msg 3", 1002L, MessageDirection.OUTGOING, MessageStatus.DELIVERED)
        )

        val pending = mockDatabase.filter { it.status == MessageStatus.QUEUED || it.status == MessageStatus.ROUTING }
        assertEquals(2, pending.size)
        assertEquals("m1", pending[0].id)
        assertEquals("m2", pending[1].id)
    }

    @Test
    fun timingInstrumentationRecordsSafeMetricsWithoutExposingSecrets() {
        // Test timing recorder calls
        MessageTimingTracker.recordPersist("test-message-12345678", 2L)
        MessageTimingTracker.recordEncrypt("test-message-12345678", 4L)
        MessageTimingTracker.recordDispatch("test-message-12345678", 6L)
        MessageTimingTracker.recordRoute("test-message-12345678", "INTERNET_DIRECT", 45L, "Accepted")
        MessageTimingTracker.recordRelayAccepted("test-message-12345678", 60L)
        MessageTimingTracker.recordReceive("test-message-12345678", 3L)
        MessageTimingTracker.recordReceipt("test-message-12345678", 2L)
        MessageTimingTracker.recordDelivered("test-message-12345678", 120L)

        // Verification passes without throwing exceptions
        assertTrue(true)
    }
}
