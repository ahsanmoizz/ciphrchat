package org.ciphrchat.app.messaging

import org.ciphrchat.app.files.FileTransferDescriptor
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
    fun fiveMiBAttachmentRoundTrips() {
        val bytes = ByteArray(5 * 1024 * 1024) { (it * 17).toByte() }
        val decoded = MessageContentCodec.decode(
            MessageContentCodec.encodeAttachment("maximum.bin", "application/octet-stream", bytes)
        )

        assertArrayEquals(bytes, decoded.attachment?.bytes)
    }

    @Test
    fun fileTransferDescriptorRoundTrips() {
        val descriptor = FileTransferDescriptor(
            fileId = "file-uuid-12345",
            fileName = "ubuntu-24.04.iso",
            fileSize = 4L * 1024 * 1024 * 1024, // 4 GiB
            mimeType = "application/octet-stream",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            chunkSize = 1024 * 1024,
            totalChunks = 4096,
            fileKeyBase64 = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
            senderId = "sender-contact-id",
            recipientId = "recipient-contact-id",
            createdAtEpochMs = 1700000000000L
        )

        val encoded = MessageContentCodec.encodeFileDescriptor(descriptor)
        val decoded = MessageContentCodec.decode(encoded)

        assertEquals(descriptor, decoded.fileDescriptor)
    }

    @Test
    fun legacyPlainTextRemainsReadable() {
        assertEquals("legacy", MessageContentCodec.decode("legacy".toByteArray()).text)
    }
}
