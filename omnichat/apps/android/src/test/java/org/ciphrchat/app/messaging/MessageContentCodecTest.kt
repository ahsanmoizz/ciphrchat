package org.ciphrchat.app.messaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageContentCodecTest {
    @Test
    fun textRoundTrips() {
        val decoded = MessageContentCodec.decode(MessageContentCodec.encodeText("hello • secure"))
        assertEquals("hello • secure", decoded.text)
    }

    @Test
    fun attachmentRoundTripsWithMimeAndBytes() {
        val bytes = ByteArray(4096) { (it * 31).toByte() }
        val decoded = MessageContentCodec.decode(
            MessageContentCodec.encodeAttachment("photo.jpg", "image/jpeg", bytes)
        )
        assertEquals("photo.jpg", decoded.attachment?.fileName)
        assertEquals("image/jpeg", decoded.attachment?.mimeType)
        assertArrayEquals(bytes, decoded.attachment?.bytes)
    }

    @Test
    fun legacyPlainTextRemainsReadable() {
        assertEquals("legacy", MessageContentCodec.decode("legacy".toByteArray()).text)
    }
}
