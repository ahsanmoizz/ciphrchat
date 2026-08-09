package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.transport.OutboundEnvelope
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetWireCodecTest {
    @Test
    fun encodeIncludesRoutingIdentityAndPayloadMetadata() {
        val envelope = OutboundEnvelope(
            protocolVersion = 1,
            messageId = "message-1",
            recipientId = "ciphr:recipient",
            senderId = "ciphr:sender",
            createdAtEpochMs = 100L,
            expiresAtEpochMs = 200L,
            hopLimit = 3,
            encryptedPayload = byteArrayOf(1, 2, 3),
            testOnly = false,
            senderInvitation = "{\"format\":\"ciphrchat-invitation\",\"displayName\":\"Alice\"}"
        )

        val json = InternetWireCodec.encode(envelope).toString(Charsets.UTF_8)
        assertTrue(json.contains("\"recipientId\":\"ciphr:recipient\""))
        assertTrue(json.contains("\"senderId\":\"ciphr:sender\""))
        assertTrue(json.contains("\"messageId\":\"message-1\""))
        assertTrue(json.contains("\"hopLimit\":3"))
        assertTrue(json.contains("\"testOnly\":false"))
        assertTrue(json.contains("\"senderInvitation\":\"{\\\"format\\\":\\\"ciphrchat-invitation\\\""))
        assertTrue(json.contains("\"encryptedPayload\":\""))
        assertTrue(json.contains("\"wireType\":\"message\""))
    }

    @Test
    fun deliveryReceiptIdentifiesBothAuthenticatedParties() {
        val json = InternetWireCodec.encodeDeliveryReceipt(
            messageId = "message-1",
            senderId = "ciphr:bob",
            recipientId = "ciphr:alice"
        ).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"wireType\":\"deliveryReceipt\""))
        assertTrue(json.contains("\"messageId\":\"message-1\""))
        assertTrue(json.contains("\"senderId\":\"ciphr:bob\""))
        assertTrue(json.contains("\"recipientId\":\"ciphr:alice\""))
    }
}
