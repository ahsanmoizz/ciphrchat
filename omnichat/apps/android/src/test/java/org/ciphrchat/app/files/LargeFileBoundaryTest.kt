package org.ciphrchat.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LargeFileBoundaryTest {

    @Test
    fun acceptsValid5GiBMetadataBoundary() {
        val maxBytes = 5L * 1024 * 1024 * 1024 // 5 GiB
        val chunkSize = 1024 * 1024 // 1 MiB
        val totalChunks = 5120

        val descriptor = FileTransferDescriptor(
            fileId = "550e8400-e29b-41d4-a716-446655440000",
            fileName = "database_backup.iso",
            fileSize = maxBytes,
            mimeType = "application/octet-stream",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            fileKeyBase64 = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
            senderId = "sender-peer",
            recipientId = "recipient-peer"
        )

        assertEquals(maxBytes, descriptor.fileSize)
        assertEquals(totalChunks, descriptor.totalChunks)
    }

    @Test
    fun rejectsFilesExceeding5GiB() {
        val oversizedBytes = 5L * 1024 * 1024 * 1024 + 1 // 5 GiB + 1 byte
        val chunkSize = 1024 * 1024
        val totalChunks = 5121

        try {
            FileTransferDescriptor(
                fileId = "550e8400-e29b-41d4-a716-446655440000",
                fileName = "too_large.bin",
                fileSize = oversizedBytes,
                mimeType = "application/octet-stream",
                sha256 = "dummy-sha",
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                fileKeyBase64 = "dummy-key",
                senderId = "sender",
                recipientId = "recipient"
            )
            fail("Expected IllegalArgumentException for file > 5 GiB")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun rejectsZeroByteFiles() {
        try {
            FileTransferDescriptor(
                fileId = "550e8400-e29b-41d4-a716-446655440000",
                fileName = "empty.txt",
                fileSize = 0L,
                mimeType = "text/plain",
                sha256 = "dummy-sha",
                chunkSize = 1024 * 1024,
                totalChunks = 0,
                fileKeyBase64 = "dummy-key",
                senderId = "sender",
                recipientId = "recipient"
            )
            fail("Expected IllegalArgumentException for 0 byte file")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun rejectsInvalidPathCharactersInFileId() {
        val dangerousFileIds = listOf(
            "../../etc/passwd",
            "file/with/slashes",
            "file\\with\\backslashes",
            "file with spaces",
            "file.with.dots"
        )

        for (badId in dangerousFileIds) {
            try {
                FileTransferDescriptor(
                    fileId = badId,
                    fileName = "test.txt",
                    fileSize = 1024,
                    mimeType = "text/plain",
                    sha256 = "dummy-sha",
                    chunkSize = 1024,
                    totalChunks = 1,
                    fileKeyBase64 = "dummy-key",
                    senderId = "sender",
                    recipientId = "recipient"
                )
                fail("Expected IllegalArgumentException for dangerous fileId: $badId")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }
}
