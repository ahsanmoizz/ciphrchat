package org.ciphrchat.app.messaging

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Versioned plaintext content format. The complete result is encrypted by
 * Signal before it reaches any transport, so MIME names, call signaling, and file descriptors
 * are never exposed to relays or nearby adapters.
 */
object MessageContentCodec {
    private val MAGIC = "CIPHR_CONTENT_1".toByteArray(Charsets.US_ASCII)
    private const val TEXT = 1
    private const val ATTACHMENT = 2
    private const val FILE_DESCRIPTOR = 3
    private const val CALL_SIGNAL = 4
    private const val MAX_FIELD_BYTES = 64 * 1024
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

    data class Decoded(
        val text: String? = null,
        val attachment: Attachment? = null,
        val fileDescriptorJson: String? = null,
        val callSignalJson: String? = null
    )

    data class Attachment(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    fun encodeText(text: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            output.writeByte(TEXT)
            writeField(output, text.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
        }
        bytes.toByteArray()
    }

    fun encodeAttachment(fileName: String, mimeType: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(ATTACHMENT)
            writeField(output, fileName.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, mimeType.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, bytes, MAX_ATTACHMENT_BYTES)
        }
        encoded.toByteArray()
    }

    fun encodeFileDescriptor(descriptorJson: String): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(FILE_DESCRIPTOR)
            writeField(output, descriptorJson.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
        }
        encoded.toByteArray()
    }

    fun encodeCallSignal(signalJson: String): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(CALL_SIGNAL)
            writeField(output, signalJson.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
        }
        encoded.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded {
        if (!bytes.startsWith(MAGIC)) return Decoded(text = bytes.toString(Charsets.UTF_8))
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes, MAGIC.size, bytes.size - MAGIC.size)).use { input ->
                when (input.readUnsignedByte()) {
                    TEXT -> Decoded(text = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8))
                    ATTACHMENT -> Decoded(
                        attachment = Attachment(
                            readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8),
                            readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8),
                            readField(input, MAX_ATTACHMENT_BYTES)
                        )
                    )
                    FILE_DESCRIPTOR -> Decoded(
                        fileDescriptorJson = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                    )
                    CALL_SIGNAL -> Decoded(
                        callSignalJson = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                    )
                    else -> throw IllegalArgumentException("Unsupported CiphrChat content kind")
                }
            }
        }.getOrElse { throw IllegalArgumentException("Invalid CiphrChat message content", it) }
    }

    private fun writeField(output: DataOutputStream, bytes: ByteArray, max: Int) {
        require(bytes.size <= max) { "Content field is too large" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readField(input: DataInputStream, max: Int): ByteArray {
        val size = input.readInt()
        require(size in 0..max) { "Invalid CiphrChat content field" }
        return ByteArray(size).also(input::readFully)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
