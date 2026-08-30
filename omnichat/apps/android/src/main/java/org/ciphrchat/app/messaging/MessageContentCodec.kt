package org.ciphrchat.app.messaging

import org.ciphrchat.app.files.FileTransferDescriptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Versioned plaintext content format. The complete result is encrypted by
 * Signal before it reaches any transport, so MIME names, file descriptors,
 * and call signaling payloads are never exposed to relays or nearby adapters.
 */
object MessageContentCodec {
    private val MAGIC = "CIPHR_CONTENT_1".toByteArray(Charsets.US_ASCII)
    private const val TEXT = 1
    private const val ATTACHMENT = 2
    private const val FILE_DESCRIPTOR = 3
    private const val CALL_SIGNAL = 4
    private const val MAX_FIELD_BYTES = 4 * 1024
    private const val MAX_SIGNAL_BYTES = 64 * 1024
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

    data class Decoded(
        val text: String? = null,
        val attachment: Attachment? = null,
        val fileDescriptor: FileTransferDescriptor? = null,
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

    fun encodeFileDescriptor(descriptor: FileTransferDescriptor): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(FILE_DESCRIPTOR)
            writeField(output, descriptor.fileId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, descriptor.fileName.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            output.writeLong(descriptor.fileSize)
            writeField(output, descriptor.mimeType.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, descriptor.sha256.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            output.writeInt(descriptor.chunkSize)
            output.writeInt(descriptor.totalChunks)
            writeField(output, descriptor.fileKeyBase64.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, descriptor.senderId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, descriptor.recipientId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            output.writeLong(descriptor.createdAtEpochMs)
        }
        encoded.toByteArray()
    }

    fun encodeCallSignal(signalJson: String): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(CALL_SIGNAL)
            writeField(output, signalJson.toByteArray(Charsets.UTF_8), MAX_SIGNAL_BYTES)
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
                    FILE_DESCRIPTOR -> {
                        val fileId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val fileName = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val fileSize = input.readLong()
                        val mimeType = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val sha256 = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val chunkSize = input.readInt()
                        val totalChunks = input.readInt()
                        val fileKeyBase64 = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val senderId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val recipientId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val createdAtEpochMs = input.readLong()
                        Decoded(
                            fileDescriptor = FileTransferDescriptor(
                                fileId = fileId,
                                fileName = fileName,
                                fileSize = fileSize,
                                mimeType = mimeType,
                                sha256 = sha256,
                                chunkSize = chunkSize,
                                totalChunks = totalChunks,
                                fileKeyBase64 = fileKeyBase64,
                                senderId = senderId,
                                recipientId = recipientId,
                                createdAtEpochMs = createdAtEpochMs
                            )
                        )
                    }
                    CALL_SIGNAL -> Decoded(
                        callSignalJson = readField(input, MAX_SIGNAL_BYTES).toString(Charsets.UTF_8)
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
