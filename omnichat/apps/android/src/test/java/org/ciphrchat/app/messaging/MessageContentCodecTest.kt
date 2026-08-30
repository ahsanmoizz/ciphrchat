package org.ciphrchat.app.messaging

import org.ciphrchat.app.files.FileTransferControl
import org.ciphrchat.app.files.FileTransferDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertTrue(decoded.fileControl is FileTransferControl.Offer)
    }

    @Test
    fun fileTransferControlReadyRoundTrips() {
        val control = FileTransferControl.Ready(
            fileId = "file-uuid-12345",
            missingChunks = listOf(0, 1, 2)
        )
        val encoded = MessageContentCodec.encodeFileControl(control)
        val decoded = MessageContentCodec.decode(encoded)

        assertEquals(control, decoded.fileControl)
    }

    @Test
    fun fileTransferControlResumeRoundTrips() {
        val control = FileTransferControl.Resume(
            fileId = "file-uuid-12345",
            missingChunks = listOf(4, 7, 12)
        )
        val encoded = MessageContentCodec.encodeFileControl(control)
        val decoded = MessageContentCodec.decode(encoded)

        assertEquals(control, decoded.fileControl)
    }

    @Test
    fun fileTransferControlCancelAndCompleteRoundTrip() {
        val cancel = FileTransferControl.Cancel(fileId = "file-uuid-12345")
        val decodedCancel = MessageContentCodec.decode(MessageContentCodec.encodeFileControl(cancel))
        assertEquals(cancel, decodedCancel.fileControl)

        val complete = FileTransferControl.Complete(fileId = "file-uuid-12345")
        val decodedComplete = MessageContentCodec.decode(MessageContentCodec.encodeFileControl(complete))
        assertEquals(complete, decodedComplete.fileControl)
    }

    @Test
    fun callSignalRoundTrips() {
        val signalJson = """{"type":"OFFER","callId":"call-123","sdp":"v=0\r\n","senderId":"alice","recipientId":"bob"}"""
        val encoded = MessageContentCodec.encodeCallSignal(signalJson)
        val decoded = MessageContentCodec.decode(encoded)

        assertEquals(signalJson, decoded.callSignalJson)
    }

    @Test
    fun legacyPlainTextRemainsReadable() {
        assertEquals("legacy", MessageContentCodec.decode("legacy".toByteArray()).text)
    }
}
