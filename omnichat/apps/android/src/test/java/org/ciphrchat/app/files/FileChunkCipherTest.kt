package org.ciphrchat.app.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

class FileChunkCipherTest {

    @Test
    fun encryptsAndDecryptsChunkRoundtrip() {
        val fileKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val fileId = "test-file-uuid-1234"
        val chunkIndex = 0
        val plaintext = "Hello CiphrChat Large File Streaming Chunk".toByteArray(Charsets.UTF_8)

        val ciphertext = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintext)
        assertNotEquals(0, ciphertext.size)
        // AES-GCM includes 16-byte authentication tag
        assertEquals(plaintext.size + 16, ciphertext.size)

        val decrypted = FileChunkCipher.decryptChunk(fileKey, fileId, chunkIndex, ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun rejectsDecryptionWithCorruptedCiphertext() {
        val fileKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val fileId = "test-file-uuid-1234"
        val chunkIndex = 1
        val plaintext = "Payload that will be corrupted".toByteArray(Charsets.UTF_8)

        val ciphertext = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintext)
        ciphertext[0] = (ciphertext[0].toInt() xor 0xFF).toByte() // corrupt 1 byte

        try {
            FileChunkCipher.decryptChunk(fileKey, fileId, chunkIndex, ciphertext)
            fail("Expected AEAD authentication tag failure on corrupted chunk")
        } catch (e: Exception) {
            // Expected AEAD auth tag mismatch
        }
    }

    @Test
    fun rejectsDecryptionWithWrongChunkIndexOrFileId() {
        val fileKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val fileId = "test-file-uuid-1234"
        val chunkIndex = 2
        val plaintext = "Chunk tied to index 2 and specific fileId".toByteArray(Charsets.UTF_8)

        val ciphertext = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintext)

        // Wrong chunk index (reordering defense)
        try {
            FileChunkCipher.decryptChunk(fileKey, fileId, 3, ciphertext)
            fail("Expected failure when decrypting with wrong chunkIndex")
        } catch (e: Exception) {
            // Expected
        }

        // Wrong fileId (cross-file injection defense)
        try {
            FileChunkCipher.decryptChunk(fileKey, "different-file-uuid", chunkIndex, ciphertext)
            fail("Expected failure when decrypting with wrong fileId")
        } catch (e: Exception) {
            // Expected
        }
    }
}
