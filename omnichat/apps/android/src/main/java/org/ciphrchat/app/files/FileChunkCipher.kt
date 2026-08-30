package org.ciphrchat.app.files

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated AEAD cipher for chunked streaming file encryption/decryption (AES-GCM-256).
 * Derives unique deterministic 12-byte nonces from SHA-256(fileKey || chunkIndex) to guarantee
 * unique nonces per chunk and prevent chunk reordering or replay attacks.
 */
object FileChunkCipher {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val NONCE_BYTES = 12

    /**
     * Encrypts a single chunk of data with authenticated AES-GCM.
     * @param fileKey 32-byte (256-bit) symmetric key.
     * @param fileId Unique identifier of the file (bound in AAD).
     * @param chunkIndex 0-indexed chunk sequence number.
     * @param plaintext Raw chunk bytes.
     */
    fun encryptChunk(fileKey: ByteArray, fileId: String, chunkIndex: Int, plaintext: ByteArray): ByteArray {
        require(fileKey.size == 32) { "File key must be 32 bytes (256-bit)" }
        val nonce = deriveNonce(fileKey, chunkIndex)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(fileKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        // Bind fileId and chunkIndex into AAD to prevent chunk swapping across files or positions
        cipher.updateAAD(createAad(fileId, chunkIndex))

        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts an authenticated chunk of data with AES-GCM.
     * @param fileKey 32-byte (256-bit) symmetric key.
     * @param fileId Unique identifier of the file.
     * @param chunkIndex 0-indexed chunk sequence number.
     * @param ciphertext Encrypted chunk bytes with authentication tag.
     */
    fun decryptChunk(fileKey: ByteArray, fileId: String, chunkIndex: Int, ciphertext: ByteArray): ByteArray {
        require(fileKey.size == 32) { "File key must be 32 bytes (256-bit)" }
        val nonce = deriveNonce(fileKey, chunkIndex)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(fileKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(createAad(fileId, chunkIndex))

        return cipher.doFinal(ciphertext)
    }

    private fun deriveNonce(fileKey: ByteArray, chunkIndex: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(fileKey)
        digest.update(ByteBuffer.allocate(4).putInt(chunkIndex).array())
        val fullHash = digest.digest()
        return fullHash.copyOfRange(0, NONCE_BYTES)
    }

    private fun createAad(fileId: String, chunkIndex: Int): ByteArray {
        val fileIdBytes = fileId.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(fileIdBytes.size + 4)
            .put(fileIdBytes)
            .putInt(chunkIndex)
            .array()
    }
}
