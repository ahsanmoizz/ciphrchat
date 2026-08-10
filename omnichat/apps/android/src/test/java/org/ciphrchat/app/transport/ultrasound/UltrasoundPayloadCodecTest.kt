package org.ciphrchat.app.transport.ultrasound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltrasoundPayloadCodecTest {
    @Test
    fun secureEnvelopeRoundTripsAndShrinksRepeatedInvitationData() {
        val payload = ("secure-invitation-and-envelope:" + "A".repeat(2_048)).toByteArray()
        val encoded = UltrasoundPayloadCodec.encode(payload)

        assertTrue(encoded.size < payload.size / 2)
        assertArrayEquals(payload, UltrasoundPayloadCodec.decode(encoded))
    }

    @Test
    fun corruptOrTruncatedPayloadIsRejected() {
        val encoded = UltrasoundPayloadCodec.encode("message".toByteArray())

        assertNull(UltrasoundPayloadCodec.decode(encoded.copyOf(encoded.size - 2)))
        assertNull(UltrasoundPayloadCodec.decode(encoded.copyOf().also { it[0] = 0 }))
    }
}
