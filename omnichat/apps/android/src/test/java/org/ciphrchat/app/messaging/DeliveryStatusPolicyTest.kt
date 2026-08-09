package org.ciphrchat.app.messaging

import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryStatusPolicyTest {
    @Test
    fun networkAcknowledgementIsSentUntilApplicationReceiptArrives() {
        assertEquals(
            MessageStatus.SENT,
            DeliveryStatusPolicy.statusFor(
                SendResult.Accepted(TransportKind.INTERNET_DIRECT, "remote-network-ack")
            )
        )
    }

    @Test
    fun localTransportAcceptanceIsSent() {
        assertEquals(
            MessageStatus.SENT,
            DeliveryStatusPolicy.statusFor(
                SendResult.Accepted(TransportKind.BLUETOOTH_DIRECT, "ble-gatt-frame")
            )
        )
    }

    @Test
    fun transientFailuresNeverBecomePermanentFailed() {
        val results = listOf(
            SendResult.Rejected("peer is temporarily offline"),
            SendResult.Failed(IllegalStateException("dial failed")),
            SendResult.Failure(IllegalStateException("radio busy")),
            SendResult.Success
        )

        results.forEach { result ->
            assertEquals(MessageStatus.QUEUED, DeliveryStatusPolicy.statusFor(result))
        }
    }
}
