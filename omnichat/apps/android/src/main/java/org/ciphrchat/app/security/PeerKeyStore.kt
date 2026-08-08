package org.ciphrchat.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the native libp2p private key encrypted at rest with Android Keystore. */
@Singleton
class PeerKeyStore @Inject constructor(
    private val context: Context
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val storedFile = File(context.filesDir, FILE_NAME)

    /**
     * Supplies native startup with a short-lived plaintext file, then removes it.
     * The stored file is always the authenticated Keystore-wrapped representation.
     */
    fun withNativeKeyFile(action: (String) -> Boolean): Boolean {
        val temporary = File.createTempFile("ciphrchat-peer-key-", ".tmp", context.cacheDir)
        temporary.delete()
        return try {
            val existing = readStoredOrLegacyKey()
            if (existing != null) {
                writePlaintext(temporary, existing)
                if (!isCurrentEncryptedContainer(storedFile)) {
                    persistEncrypted(existing)
                }
            }
            val result = action(temporary.absolutePath)
            if (temporary.isFile) {
                val generatedOrExisting = temporary.readBytes().also {
                    require(it.size in 1..MAX_PLAINTEXT_BYTES) { "Native peer key has an invalid size" }
                }
                persistEncrypted(generatedOrExisting)
            }
            result
        } finally {
            temporary.delete()
        }
    }

    private fun readStoredOrLegacyKey(): ByteArray? {
        if (!storedFile.isFile) return null
        val encoded = storedFile.readBytes().also {
            require(it.size in 1..MAX_CONTAINER_BYTES) { "Stored peer key is too large" }
        }
        return if (isEncryptedContainer(encoded)) {
            // A device may retain a legacy Keystore key whose IV policy can no
            // longer decrypt its container. The native layer can safely create
            // a fresh transport key; the app identity and conversations live
            // in the separate SQLCipher database.
            runCatching { decrypt(encoded) }.getOrNull()
        } else encoded
    }

    private fun isEncryptedContainer(file: File): Boolean =
        file.isFile && file.length() in 1L..MAX_CONTAINER_BYTES.toLong() && isEncryptedContainer(file.readBytes())

    private fun isEncryptedContainer(bytes: ByteArray): Boolean =
        (bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) ||
            (bytes.size > LEGACY_MAGIC.size && bytes.copyOfRange(0, LEGACY_MAGIC.size).contentEquals(LEGACY_MAGIC))

    private fun isCurrentEncryptedContainer(file: File): Boolean =
        file.isFile && file.length() > MAGIC.size &&
            file.readBytes().copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun writePlaintext(file: File, bytes: ByteArray) {
        require(bytes.size in 1..MAX_PLAINTEXT_BYTES) { "Peer key is invalid" }
        file.writeBytes(bytes)
    }

    private fun persistEncrypted(plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext)
        val encoded = MAGIC + cipher.iv + ciphertext
        require(encoded.size <= MAX_CONTAINER_BYTES) { "Encrypted peer key is too large" }
        val temporary = File(storedFile.parentFile, ".${storedFile.name}.${UUID.randomUUID()}.tmp")
        temporary.writeBytes(encoded)
        try {
            runCatching {
                Files.move(
                    temporary.toPath(),
                    storedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(temporary.toPath(), storedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun decrypt(encoded: ByteArray): ByteArray {
        val prefixSize = when {
            encoded.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) -> MAGIC.size
            encoded.copyOfRange(0, LEGACY_MAGIC.size).contentEquals(LEGACY_MAGIC) -> LEGACY_MAGIC.size
            else -> error("Stored peer key has an unsupported format")
        }
        val ivStart = prefixSize
        val ivEnd = ivStart + IV_BYTES
        require(encoded.size > ivEnd) { "Stored peer key is incomplete" }
        val iv = encoded.copyOfRange(ivStart, ivEnd)
        val ciphertext = encoded.copyOfRange(ivEnd, encoded.size)
        return runCatching { decryptWithKey(key(), iv, ciphertext) }
            .recoverCatching { decryptWithKey(legacyKey(), iv, ciphertext) }
            .getOrThrow().also {
            require(it.size in 1..MAX_PLAINTEXT_BYTES) { "Stored peer key is invalid" }
        }
    }

    private fun key(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return generateKey(KEY_ALIAS)
    }

    private fun legacyKey(): SecretKey {
        return keyStore.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey
            ?: error("Legacy peer key is unavailable")
    }

    private fun generateKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun decryptWithKey(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "CiphrChatPeerKeyWrapV2"
        const val LEGACY_KEY_ALIAS = "CiphrChatPeerKeyWrap"
        const val FILE_NAME = "ciphrchat-peer-key.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val MAX_PLAINTEXT_BYTES = 4096
        const val MAX_CONTAINER_BYTES = 8192
        val MAGIC = "CIPHR_PEER_KEY_V2\n".toByteArray(Charsets.UTF_8)
        val LEGACY_MAGIC = "CIPHR_PEER_KEY_V1\n".toByteArray(Charsets.UTF_8)
    }
}
