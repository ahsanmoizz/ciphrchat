package org.ciphrchat.app.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TransportWireCodecTest {
    @Test
    fun roundTripPreservesAuthenticatedEnvelopeFields() {
        val original = envelope()
        val encoded = encode(original)

        val decoded = TransportWireCodec.read(DataInputStream(ByteArrayInputStream(encoded)))

        assertEquals(original.protocolVersion, decoded.protocolVersion)
        assertEquals(original.messageId, decoded.messageId)
        assertEquals(original.recipientId, decoded.recipientId)
        assertEquals(original.senderId, decoded.senderId)
        assertEquals(original.createdAtEpochMs, decoded.createdAtEpochMs)
        assertEquals(original.expiresAtEpochMs, decoded.expiresAtEpochMs)
        assertEquals(original.hopLimit, decoded.hopLimit)
        assertFalse(decoded.testOnly)
        assertArrayEquals(original.encryptedPayload, decoded.encryptedPayload)
        assertEquals(original.senderInvitation, decoded.senderInvitation)
    }

    @Test
    fun unsupportedProtocolVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransportWireCodec.read(
                DataInputStream(ByteArrayInputStream(encode(envelope(protocolVersion = 3))))
            )
        }
    }

    @Test
    fun legacyVersionOneStillDecodesWithoutReciprocalInvitation() {
        val decoded = TransportWireCodec.read(
            DataInputStream(ByteArrayInputStream(encode(envelope(protocolVersion = 1))))
        )

        assertEquals("", decoded.senderInvitation)
    }

    @Test
    fun invalidHopLimitIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransportWireCodec.read(
                DataInputStream(ByteArrayInputStream(encode(envelope(hopLimit = 17))))
            )
        }
    }

    @Test
    fun oversizedPayloadCannotBeWritten() {
        assertThrows(IllegalArgumentException::class.java) {
            encode(envelope(payload = ByteArray(8 * 1024 * 1024 + 1)))
        }
    }

    private fun encode(envelope: OutboundEnvelope): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output -> TransportWireCodec.write(output, envelope) }
        bytes.toByteArray()
    }

    private fun envelope(
        protocolVersion: Int = 2,
        hopLimit: Int = 3,
        payload: ByteArray = byteArrayOf(1, 2, 3, 4)
    ) = OutboundEnvelope(
        protocolVersion = protocolVersion,
        messageId = "message-123",
        recipientId = "ciphr:recipient",
        senderId = "ciphr:sender",
        createdAtEpochMs = 1_700_000_000_000,
        expiresAtEpochMs = 1_700_000_604_800_000,
        hopLimit = hopLimit,
        encryptedPayload = payload,
        testOnly = false,
        senderInvitation = "{\"format\":\"ciphrchat-invitation\"}"
    )
}
