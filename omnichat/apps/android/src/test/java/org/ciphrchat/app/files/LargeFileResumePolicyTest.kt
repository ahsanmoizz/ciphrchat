package org.ciphrchat.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFileResumePolicyTest {

    data class ChunkPlan(
        val totalChunks: Int,
        val uploadedChunks: Set<Int>
    ) {
        val missingChunks: List<Int>
            get() = (0 until totalChunks).filter { !uploadedChunks.contains(it) }

        val isComplete: Boolean
            get() = missingChunks.isEmpty()
    }

    @Test
    fun determinesMissingChunksCorrectly() {
        val total = 5
        val uploaded = setOf(0, 1, 3) // Missing 2 and 4
        val plan = ChunkPlan(total, uploaded)

        assertFalse(plan.isComplete)
        assertEquals(listOf(2, 4), plan.missingChunks)
    }

    @Test
    fun handlesDuplicateAndOutOfOrderChunks() {
        val total = 4
        val uploadedWithDuplicates = setOf(3, 1, 0, 0, 1) // Missing 2
        val plan = ChunkPlan(total, uploadedWithDuplicates)

        assertFalse(plan.isComplete)
        assertEquals(listOf(2), plan.missingChunks)
    }

    @Test
    fun recognizesCompletedTransfer() {
        val total = 4
        val allUploaded = setOf(0, 1, 2, 3)
        val plan = ChunkPlan(total, allUploaded)

        assertTrue(plan.isComplete)
        assertTrue(plan.missingChunks.isEmpty())
    }
}
