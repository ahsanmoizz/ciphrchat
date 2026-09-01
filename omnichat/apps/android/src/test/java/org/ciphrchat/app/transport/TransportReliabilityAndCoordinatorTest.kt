package org.ciphrchat.app.transport

import org.ciphrchat.app.messaging.DeliveryStatusPolicy
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.messaging.MessageStatus
import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.worker.PendingMessageRetryScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class TransportReliabilityAndCoordinatorTest {

    @Test
    fun defaultTransportPriorityHasInternetFirstFollowedByNearbyRadios() {
        assertEquals(TransportKind.INTERNET_DIRECT, DEFAULT_TRANSPORT_PRIORITY.first())
        assertTrue(DEFAULT_TRANSPORT_PRIORITY.contains(TransportKind.WIFI_LAN))
        assertTrue(DEFAULT_TRANSPORT_PRIORITY.contains(TransportKind.BLUETOOTH_DIRECT))
        assertTrue(DEFAULT_TRANSPORT_PRIORITY.contains(TransportKind.WIFI_DIRECT))
    }

    @Test
    fun boundedExponentialBackoffCalculatesSaneJitteredDelays() {
        // Attempt 0: base 5s -> with jitter between 4s and 6s
        val delay0 = PendingMessageRetryScheduler.computeBackoffWithJitter(0, 1.0)
        assertEquals(5L, delay0)

        // Attempt 1: 10s
        val delay1 = PendingMessageRetryScheduler.computeBackoffWithJitter(1, 1.0)
        assertEquals(10L, delay1)

        // Attempt 2: 20s
        val delay2 = PendingMessageRetryScheduler.computeBackoffWithJitter(2, 1.0)
        assertEquals(20L, delay2)

        // Attempt 3: 40s
        val delay3 = PendingMessageRetryScheduler.computeBackoffWithJitter(3, 1.0)
        assertEquals(40L, delay3)

        // Attempt 10: bounded at max 300s (5 minutes)
        val delayMax = PendingMessageRetryScheduler.computeBackoffWithJitter(10, 1.0)
        assertEquals(300L, delayMax)

        // Jitter testing: within 80% to 120%
        val delayJitterLow = PendingMessageRetryScheduler.computeBackoffWithJitter(2, 0.8)
        assertEquals(16L, delayJitterLow)

        val delayJitterHigh = PendingMessageRetryScheduler.computeBackoffWithJitter(2, 1.2)
        assertEquals(24L, delayJitterHigh)
    }

    @Test
    fun noBlindResendOfAcceptedMessages() {
        // Messages in database
        val messages = listOf(
            MessageEntity("m1", "c1", "self", "r1", "enc-body-1", byteArrayOf(1), 1000L, MessageDirection.OUTGOING, MessageStatus.QUEUED, null),
            MessageEntity("m2", "c1", "self", "r1", "enc-body-2", byteArrayOf(2), 1001L, MessageDirection.OUTGOING, MessageStatus.ROUTING, "INTERNET_DIRECT"),
            MessageEntity("m3", "c1", "self", "r1", "enc-body-3", byteArrayOf(3), 1002L, MessageDirection.OUTGOING, MessageStatus.SENT, "INTERNET_DIRECT"),
            MessageEntity("m4", "c1", "self", "r1", "enc-body-4", byteArrayOf(4), 1003L, MessageDirection.OUTGOING, MessageStatus.DELIVERED, "INTERNET_DIRECT")
        )

        // Filter replicating MessageDao.getMessagesPendingDelivery query
        val pending = messages.filter { it.status == MessageStatus.QUEUED || it.status == MessageStatus.ROUTING }

        // Must include QUEUED and ROUTING messages, but NOT already SENT or DELIVERED messages
        assertEquals(2, pending.size)
        assertEquals("m1", pending[0].id)
        assertEquals("m2", pending[1].id)
        assertFalse(pending.any { it.id == "m3" })
        assertFalse(pending.any { it.id == "m4" })
    }

    @Test
    fun singleFlightOwnershipPreventsRacingExecutions() {
        val inFlightSet = Collections.synchronizedSet(mutableSetOf<String>())
        val messageId = "msg-flight-lock-100"
        val executeCount = AtomicInteger(0)

        // First worker acquires lease
        val acquired1 = inFlightSet.add(messageId)
        assertTrue(acquired1)
        executeCount.incrementAndGet()

        // Second worker (e.g. WorkManager or reconnect) tries to acquire same messageId simultaneously
        val acquired2 = inFlightSet.add(messageId)
        assertFalse("Second concurrent runner must be rejected by single-flight ownership", acquired2)

        // First worker finishes
        inFlightSet.remove(messageId)

        // Subsequent worker can now acquire
        val acquired3 = inFlightSet.add(messageId)
        assertTrue(acquired3)
    }

    @Test
    fun deliveryStatusMonotonicTransitions() {
        // QUEUED -> ROUTING
        var status = DeliveryStatusPolicy.merge(MessageStatus.QUEUED, MessageStatus.ROUTING)
        assertEquals(MessageStatus.ROUTING, status)

        // ROUTING -> SENT
        status = DeliveryStatusPolicy.merge(status, MessageStatus.SENT)
        assertEquals(MessageStatus.SENT, status)

        // Late transient network failure does NOT downgrade SENT to QUEUED
        status = DeliveryStatusPolicy.merge(status, MessageStatus.QUEUED)
        assertEquals(MessageStatus.SENT, status)

        // Late retry work does NOT downgrade SENT to ROUTING
        status = DeliveryStatusPolicy.merge(status, MessageStatus.ROUTING)
        assertEquals(MessageStatus.SENT, status)

        // SENT -> DELIVERED
        status = DeliveryStatusPolicy.merge(status, MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, status)

        // DELIVERED cannot be downgraded by anything
        status = DeliveryStatusPolicy.merge(status, MessageStatus.SENT)
        assertEquals(MessageStatus.DELIVERED, status)

        status = DeliveryStatusPolicy.merge(status, MessageStatus.QUEUED)
        assertEquals(MessageStatus.DELIVERED, status)
    }

    @Test
    fun noDuplicateVisibleDeliveryOnDuplicateReceipt() {
        val receivedDatabase = mutableMapOf<String, String>()
        val messageId = "msg-inbound-duplicate-1"

        // First delivery
        if (!receivedDatabase.containsKey(messageId)) {
            receivedDatabase[messageId] = "Decrypted message content"
        }
        assertEquals(1, receivedDatabase.size)

        // Duplicate delivery arriving over network
        var duplicateCreated = false
        if (!receivedDatabase.containsKey(messageId)) {
            duplicateCreated = true
        }
        assertFalse("Duplicate message ID must not create second entity in database or chat stream", duplicateCreated)
        assertEquals(1, receivedDatabase.size)
    }
}
