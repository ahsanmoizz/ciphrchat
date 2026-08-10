package org.ciphrchat.app.transport.ultrasound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UltrasoundChunkCodecTest {
    @Test
    fun chunkRoundTripsWithMaximumPayload() {
        val id = ByteArray(UltrasoundChunkCodec.TRANSFER_ID_BYTES) { it.toByte() }
        val data = ByteArray(UltrasoundChunkCodec.MAX_CHUNK_BYTES) { (it * 3).toByte() }
        val decoded = UltrasoundChunkCodec.decode(
            UltrasoundChunkCodec.encode(UltrasoundChunkCodec.Chunk(id, 2, 7, data))
        )

        requireNotNull(decoded)
        assertArrayEquals(id, decoded.transferId)
        assertEquals(2, decoded.index)
        assertEquals(7, decoded.total)
        assertArrayEquals(data, decoded.data)
    }

    @Test
    fun receiverAcknowledgementRoundTripsAndRejectsCorruption() {
        val id = ByteArray(UltrasoundChunkCodec.TRANSFER_ID_BYTES) { (it * 7).toByte() }
        val encoded = UltrasoundChunkCodec.encodeAcknowledgement(id)

        assertArrayEquals(id, UltrasoundChunkCodec.decodeAcknowledgement(encoded))
        assertNull(UltrasoundChunkCodec.decodeAcknowledgement(encoded.copyOf().also { it[0] = 0 }))
    }
}
