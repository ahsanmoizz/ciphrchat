package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.transport.OutboundEnvelope
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val json = JSONObject(InternetWireCodec.encode(envelope).toString(Charsets.UTF_8))
        assertEquals("ciphr:recipient", json.getString("recipientId"))
        assertEquals("ciphr:sender", json.getString("senderId"))
        assertEquals("message-1", json.getString("messageId"))
        assertEquals(3, json.getInt("hopLimit"))
        assertFalse(json.getBoolean("testOnly"))
        assertTrue(json.getString("encryptedPayload").isNotBlank())
    }
}
