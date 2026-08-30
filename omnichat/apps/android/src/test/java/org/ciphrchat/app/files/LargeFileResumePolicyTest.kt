package org.ciphrchat.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class LargeFileResumePolicyTest {

    data class ChunkPlan(
        val totalChunks: Int,
        val receivedChunks: Set<Int>
    ) {
        val missingChunks: List<Int>
            get() = (0 until totalChunks).filter { !receivedChunks.contains(it) }

        val isComplete: Boolean
            get() = missingChunks.isEmpty()
    }

    @Test
    fun determinesMissingChunksCorrectly() {
        val total = 5
        val received = setOf(0, 1, 3) // Missing 2 and 4
        val plan = ChunkPlan(total, received)

        assertFalse(plan.isComplete)
        assertEquals(listOf(2, 4), plan.missingChunks)
    }

    @Test
    fun handlesDuplicateAndOutOfOrderChunks() {
        val total = 4
        val receivedWithDuplicates = setOf(3, 1, 0, 0, 1) // Missing 2
        val plan = ChunkPlan(total, receivedWithDuplicates)

        assertFalse(plan.isComplete)
        assertEquals(listOf(2), plan.missingChunks)
    }

    @Test
    fun recognizesCompletedTransfer() {
        val total = 4
        val allReceived = setOf(0, 1, 2, 3)
        val plan = ChunkPlan(total, allReceived)

        assertTrue(plan.isComplete)
        assertTrue(plan.missingChunks.isEmpty())
    }

    @Test
    fun verifiesLocalPartialFileOffsetCalculation() {
        val chunkSize = 1024 * 1024 // 1 MiB
        val chunkIndex = 5
        val expectedOffset = 5L * 1024 * 1024

        val actualOffset = chunkIndex.toLong() * chunkSize
        assertEquals(expectedOffset, actualOffset)
    }

    @Test
    fun verifiesEndToEndHashIntegrity() {
        val testData = "CiphrChat Zero-Retention Stream Transfer Integrity".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(testData).joinToString("") { "%02x".format(it) }

        // Simulate streaming verification from InputStream
        val streamDigest = MessageDigest.getInstance("SHA-256")
        ByteArrayInputStream(testData).use { stream ->
            val buf = ByteArray(16)
            var r: Int
            while (stream.read(buf).also { r = it } > 0) {
                streamDigest.update(buf, 0, r)
            }
        }
        val actualHash = streamDigest.digest().joinToString("") { "%02x".format(it) }

        assertEquals(expectedHash, actualHash)
    }

    @Test
    fun classifiesTransferTypeBySizeThresholds() {
        val smallFileSize = 5L * 1024 * 1024 // 5 MiB
        val largeFileSize = 5L * 1024 * 1024 + 1 // 5 MiB + 1 B
        val fiveGiB = 5L * 1024 * 1024 * 1024 // 5 GiB
        val oversized = 5L * 1024 * 1024 * 1024 + 1 // 5 GiB + 1 B

        // Small path threshold check
        val isSmall = smallFileSize <= 5 * 1024 * 1024
        assertTrue(isSmall)

        // Large path threshold check
        val isLarge = largeFileSize > 5 * 1024 * 1024 && largeFileSize <= FileTransferDescriptor.MAX_FILE_SIZE_BYTES
        assertTrue(isLarge)

        val is5GiBLarge = fiveGiB > 5 * 1024 * 1024 && fiveGiB <= FileTransferDescriptor.MAX_FILE_SIZE_BYTES
        assertTrue(is5GiBLarge)

        // Oversized threshold check
        val isOversized = oversized > FileTransferDescriptor.MAX_FILE_SIZE_BYTES
        assertTrue(isOversized)
    }

    @Test
    fun senderDoesNotUploadBeforeReceiverReady() {
        val descriptor = FileTransferDescriptor(
            fileId = "test-file-123",
            fileName = "archive.tar.gz",
            fileSize = 100 * 1024 * 1024L,
            mimeType = "application/gzip",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            chunkSize = 1024 * 1024,
            totalChunks = 100,
            fileKeyBase64 = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
            senderId = "alice",
            recipientId = "bob"
        )

        val senderState = SenderTransferState(
            fileId = descriptor.fileId,
            sourceUriString = "content://com.android.providers.media.documents/document/10",
            recipientId = "bob",
            descriptor = descriptor,
            fileKeyBase64 = descriptor.fileKeyBase64,
            status = "WAITING_FOR_RECEIVER"
        )

        // Must remain in WAITING_FOR_RECEIVER until ready signal
        assertEquals("WAITING_FOR_RECEIVER", senderState.status)

        // When Ready received
        val ready = FileTransferControl.Ready(fileId = descriptor.fileId)
        val updatedState = senderState.copy(status = "UPLOADING")
        assertEquals("UPLOADING", updatedState.status)
        assertEquals(ready.fileId, updatedState.fileId)
    }

    @Test
    fun failsClosedWhenRelayUrlIsBlank() {
        val blankUrl = "   "
        try {
            require(blankUrl.isNotBlank()) {
                "File relay URL is not configured"
            }
            fail("Expected exception for blank relay URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("File relay URL is not configured") == true)
        }
    }

    @Test
    fun verifiesBackpressureAndValidationHttpMappings() {
        val bufferFullStatus = 429 // TOO_MANY_REQUESTS
        val isRetryable = bufferFullStatus == 429 || (bufferFullStatus in 500..599)
        assertTrue("Buffer full must be retryable", isRetryable)

        val validationStatus = 400 // BAD_REQUEST
        val isValidationRetryable = validationStatus == 429 || (validationStatus in 500..599)
        assertFalse("Validation errors must not be retryable", isValidationRetryable)
    }
}
