package org.ciphrchat.app.transport.ultrasound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UltrasoundFrameCodecTest {
    @Test
    fun frameRoundTripsAndCorrectsSmallBurstErrors() {
        val payload = "encrypted-envelope".toByteArray()
        val frame = UltrasoundFrameCodec.encode(payload)
        val corrupted = frame.copyOf()
        val codewordStart = UltrasoundFrameCodec.PREAMBLE_BYTES.size + 1
        corrupted[codewordStart + 3] = (corrupted[codewordStart + 3].toInt() xor 0x55).toByte()
        corrupted[codewordStart + 29] = (corrupted[codewordStart + 29].toInt() xor 0x11).toByte()

        assertArrayEquals(payload, UltrasoundFrameCodec.decode(corrupted))
    }

    @Test
    fun invalidChecksumIsRejected() {
        val frame = UltrasoundFrameCodec.encode("message".toByteArray())
        val corrupted = frame.copyOf()
        val codewordStart = UltrasoundFrameCodec.PREAMBLE_BYTES.size + 1
        for (offset in 0 until 5) {
            corrupted[codewordStart + 6 + offset] =
                (corrupted[codewordStart + 6 + offset].toInt() xor (offset + 1)).toByte()
        }

        assertNull(UltrasoundFrameCodec.decode(corrupted))
    }
}
