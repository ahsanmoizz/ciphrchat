package org.ciphrchat.app.transport.bluetooth

import org.ciphrchat.app.transport.OutboundEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRouterTest {
    private fun envelope(messageId: String, recipientId: String = "recipient", hopLimit: Int = 3) =
        OutboundEnvelope(
            protocolVersion = 1,
            messageId = messageId,
            recipientId = recipientId,
            senderId = "sender",
            createdAtEpochMs = 1L,
            expiresAtEpochMs = Long.MAX_VALUE,
            hopLimit = hopLimit,
            encryptedPayload = byteArrayOf(1, 2, 3),
            testOnly = false
        )

    @Test
    fun localRecipientIsNeverForwarded() {
        assertFalse(MeshRouter().shouldForward(envelope("local", recipientId = "me"), "me"))
    }

    @Test
    fun originIsMarkedBeforeFlooding() {
        val router = MeshRouter()
        val frame = envelope("origin")
        router.markOrigin(frame)

        assertFalse(router.shouldForward(frame, "another-node"))
    }

    @Test
    fun duplicateFramesAreForwardedOnlyOnce() {
        val router = MeshRouter()
        val frame = envelope("duplicate")

        assertTrue(router.shouldForward(frame, "relay-node"))
        assertFalse(router.shouldForward(frame, "relay-node"))
    }

    @Test
    fun forwardingConsumesOneHop() {
        val forwarded = MeshRouter().prepareForForwarding(envelope("hop", hopLimit = 2))

        assertEquals(1, forwarded.hopLimit)
    }
}
