package org.ciphrchat.app.transport.internet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryAwaiterTest {
    @Test
    fun queueAcceptanceAloneIsNotDelivery() = runBlocking {
        val result = DeliveryAwaiter().await("message-1", 20L) { true }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("acknowledgement"))
    }

    @Test
    fun remoteAcknowledgementCompletesDelivery() = runBlocking {
        val awaiter = DeliveryAwaiter()
        val result = awaiter.await("message-2", 1_000L) {
            awaiter.accepted("message-2")
            true
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun remoteFailurePreservesItsReason() = runBlocking {
        val awaiter = DeliveryAwaiter()
        val result = awaiter.await("message-3", 1_000L) {
            awaiter.failed("message-3", "peer unavailable")
            true
        }

        assertEquals("peer unavailable", result.exceptionOrNull()?.message)
    }
}
