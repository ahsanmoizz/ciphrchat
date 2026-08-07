package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.transport.OutboundEnvelope
import org.junit.Assert.assertEquals
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
            testOnly = false
        )

        val json = InternetWireCodec.encode(envelope).toString(Charsets.UTF_8)
        assertTrue(json.contains("\"recipientId\":\"ciphr:recipient\""))
        assertTrue(json.contains("\"senderId\":\"ciphr:sender\""))
        assertTrue(json.contains("\"messageId\":\"message-1\""))
        assertTrue(json.contains("\"hopLimit\":3"))
        assertTrue(json.contains("\"testOnly\":false"))
        assertTrue(json.contains("\"encryptedPayload\":\""))
        assertEquals(1, json.count { it == '{' })
    }
}
