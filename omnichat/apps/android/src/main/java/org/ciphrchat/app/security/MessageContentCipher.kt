package org.ciphrchat.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts message previews before they enter the SQLCipher database. */
@Singleton
class MessageContentCipher @Inject constructor() {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Volatile private var cachedKey: SecretKey? = null
    @Volatile private var cachedLegacyKey: SecretKey? = null

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(1 + iv.size + ciphertext.size)
            .put(iv.size.toByte()).put(iv).put(ciphertext).array(), Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        val encoded = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(encoded)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in 12..16) { "Invalid encrypted message" }
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return runCatching { decryptWithKey(key(), iv, ciphertext) }
            .recoverCatching { decryptWithKey(legacyKey(), iv, ciphertext) }
            .getOrThrow()
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        val key = existing ?: generateKey(KEY_ALIAS)
        cachedKey = key
        return key
    }

    private fun legacyKey(): SecretKey {
        cachedLegacyKey?.let { return it }
        val key = keyStore.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey
            ?: error("Legacy message content key is unavailable")
        cachedLegacyKey = key
        return key
    }

    private fun generateKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(false)
            .build())
        return generator.generateKey()
    }

    private fun decryptWithKey(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private companion object {
        const val KEY_ALIAS = "CiphrChatMessageContentKeyV2"
        const val LEGACY_KEY_ALIAS = "CiphrChatMessageContentKey"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
