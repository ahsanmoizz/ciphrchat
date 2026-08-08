package org.ciphrchat.app.transport.infrared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfraredFrameCodecTest {
    @Test
    fun roundTripRejectsCorruption() {
        val payload = "ciphrchat-optical".toByteArray()
        val encoded = InfraredFrameCodec.encode(payload)
        assertArrayEquals(payload, InfraredFrameCodec.decode(encoded))
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 0x01).toByte()
        assertNull(InfraredFrameCodec.decode(encoded))
    }
}
