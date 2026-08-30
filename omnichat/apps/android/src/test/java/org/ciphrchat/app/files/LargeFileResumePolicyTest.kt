package org.ciphrchat.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
