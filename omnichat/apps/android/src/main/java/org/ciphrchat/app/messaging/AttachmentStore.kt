package org.ciphrchat.app.messaging

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/** Stores local attachment copies encrypted at rest in the app-private CiphrChat directory. */
@Singleton
class AttachmentStore @Inject constructor(@ApplicationContext private val context: Context) {
    data class Input(val fileName: String, val mimeType: String, val bytes: ByteArray)
    data class Stored(val path: String, val size: Long, val sha256: String)

    fun read(uri: Uri): Input {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val name = displayName(resolver, uri) ?: guessName(mime)
        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBounded(MAX_ATTACHMENT_BYTES)
        } ?: error("The selected file could not be opened")
        return Input(name, mime, bytes)
    }

    fun save(fileName: String, mimeType: String, bytes: ByteArray): Stored {
        require(bytes.size in 1..MAX_ATTACHMENT_BYTES) {
            "Attachments must be between 1 byte and ${MAX_ATTACHMENT_BYTES / (1024 * 1024)} MiB"
        }
        val directory = File(context.filesDir, "CiphrChat/attachments").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${randomHex(12)}.bin")
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        FileOutputStream(target).use { output ->
            output.write(iv)
            output.write(cipher.doFinal(bytes))
        }
        return Stored(target.absolutePath, bytes.size.toLong(), sha256(bytes))
    }

    fun materialize(path: String, fileName: String): File {
        val source = File(path)
        require(source.isFile) { "Attachment is no longer available" }
        val encrypted = source.readBytes()
        require(encrypted.size > IV_BYTES) { "Attachment is incomplete" }
        val iv = encrypted.copyOfRange(0, IV_BYTES)
        val ciphertext = encrypted.copyOfRange(IV_BYTES, encrypted.size)
        val plaintext = runCatching { decryptWithKey(key(), iv, ciphertext) }
            .recoverCatching { decryptWithKey(legacyKey(), iv, ciphertext) }
            .getOrThrow()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "attachment" }
        val directory = File(context.cacheDir, "shared_attachments").apply { mkdirs() }
        val target = File(directory, "shared-attachment-$safeName")
        FileOutputStream(target).use { it.write(plaintext) }
        return target
    }

    fun delete(path: String): Boolean {
        val attachmentRoot = File(context.filesDir, "CiphrChat/attachments").canonicalFile
        val target = File(path).canonicalFile
        if (target.parentFile != attachmentRoot) return false
        return !target.exists() || target.delete()
    }

    @Volatile private var cachedKey: SecretKey? = null
    @Volatile private var cachedLegacyKey: SecretKey? = null

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .build())
        }.generateKey()
        cachedKey = key
        return key
    }

    private fun legacyKey(): SecretKey {
        cachedLegacyKey?.let { return it }
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey
            ?: error("Legacy attachment key is unavailable")
        cachedLegacyKey = key
        return key
    }

    private fun decryptWithKey(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            doFinal(ciphertext)
        }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun guessName(mime: String): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return if (extension.isNullOrBlank()) "attachment" else "attachment.$extension"
    }

    private fun randomHex(length: Int): String = ByteArray(length).also(SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(it) }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) error("Attachment exceeds ${maxBytes / (1024 * 1024)} MiB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
        const val IV_BYTES = 12
        const val KEY_ALIAS = "CiphrChatAttachmentKeyV2"
        const val LEGACY_KEY_ALIAS = "CiphrChatAttachmentKey"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
