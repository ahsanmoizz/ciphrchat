package org.ciphrchat.app.transport.uwb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UwbOobFrameCodecTest {
    @Test
    fun roundTripKeepsSecureRangingParameters() {
        val frame = UwbOobFrameCodec.Frame(
            UwbOobFrameCodec.HELLO,
            42,
            9,
            10,
            byteArrayOf(1, 2),
            null,
            ByteArray(16) { it.toByte() }
        )
        val decoded = UwbOobFrameCodec.decode(UwbOobFrameCodec.encode(frame))
        assertNotNull(decoded)
        assertEquals(frame.type, decoded!!.type)
        assertEquals(frame.sessionId, decoded.sessionId)
        assertArrayEquals(frame.address, decoded.address)
        assertArrayEquals(frame.sessionKey, decoded.sessionKey)
    }
}
