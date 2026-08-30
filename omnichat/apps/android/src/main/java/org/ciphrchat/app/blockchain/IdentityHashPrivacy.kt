package org.ciphrchat.app.blockchain

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Privacy-preserving identity anchoring helper.
 * Computes a zero-knowledge salted commitment hash of a user's identity
 * to anchor cryptographic existence on-chain without revealing identity keys, public IDs, or IP addresses.
 */
object IdentityHashPrivacy {

    private const val SALT_BYTES = 32

    data class BlindedCommitment(
        val identityHashHex: String,
        val saltHex: String
    )

    /**
     * Generates a blinded commitment from an identity public key or public ID and a random 32-byte salt.
     * @param identityRaw The public identity string (e.g. ciphr:0123456789abcdef).
     * @return BlindedCommitment containing the 32-byte hash and secret salt.
     */
    fun createBlindedCommitment(identityRaw: String): BlindedCommitment {
        require(identityRaw.isNotBlank()) { "Identity cannot be blank" }
        val salt = ByteArray(SALT_BYTES).apply { SecureRandom().nextBytes(this) }
        val hash = computeHash(identityRaw.toByteArray(Charsets.UTF_8), salt)
        return BlindedCommitment(
            identityHashHex = bytesToHex(hash),
            saltHex = bytesToHex(salt)
        )
    }

    /**
     * Verifies that a given identity matches a blinded commitment when supplied with the original salt.
     */
    fun verifyCommitment(identityRaw: String, saltHex: String, expectedHashHex: String): Boolean {
        return runCatching {
            val salt = hexToBytes(saltHex)
            require(salt.size == SALT_BYTES) { "Salt must be $SALT_BYTES bytes" }
            val computed = computeHash(identityRaw.toByteArray(Charsets.UTF_8), salt)
            bytesToHex(computed).equals(expectedHashHex.removePrefix("0x"), ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * Computes SHA-256(identityBytes || salt).
     */
    fun computeHash(identityBytes: ByteArray, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(identityBytes)
        digest.update(salt)
        return digest.digest()
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").trim()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
