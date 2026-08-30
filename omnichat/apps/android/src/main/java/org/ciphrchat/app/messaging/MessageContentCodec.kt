package org.ciphrchat.app.messaging

import org.ciphrchat.app.files.FileTransferControl
import org.ciphrchat.app.files.FileTransferDescriptor
import org.json.JSONArray
import org.json.JSONObject
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
    private const val FILE_TRANSFER_CONTROL = 5
    private const val MAX_FIELD_BYTES = 4 * 1024
    private const val MAX_SIGNAL_BYTES = 64 * 1024
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

    data class Decoded(
        val text: String? = null,
        val attachment: Attachment? = null,
        val fileDescriptor: FileTransferDescriptor? = null,
        val callSignalJson: String? = null,
        val fileControl: FileTransferControl? = null
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

    fun encodeFileControl(control: FileTransferControl): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(FILE_TRANSFER_CONTROL)
            val json = when (control) {
                is FileTransferControl.Offer -> JSONObject()
                    .put("type", "OFFER")
                    .put("fileId", control.descriptor.fileId)
                    .put("fileName", control.descriptor.fileName)
                    .put("fileSize", control.descriptor.fileSize)
                    .put("mimeType", control.descriptor.mimeType)
                    .put("sha256", control.descriptor.sha256)
                    .put("chunkSize", control.descriptor.chunkSize)
                    .put("totalChunks", control.descriptor.totalChunks)
                    .put("fileKeyBase64", control.descriptor.fileKeyBase64)
                    .put("senderId", control.descriptor.senderId)
                    .put("recipientId", control.descriptor.recipientId)
                    .put("createdAtEpochMs", control.descriptor.createdAtEpochMs)
                is FileTransferControl.Ready -> JSONObject()
                    .put("type", "READY")
                    .put("fileId", control.fileId)
                    .apply {
                        if (control.missingChunks != null) {
                            put("missingChunks", JSONArray(control.missingChunks))
                        }
                    }
                is FileTransferControl.Resume -> JSONObject()
                    .put("type", "RESUME")
                    .put("fileId", control.fileId)
                    .put("missingChunks", JSONArray(control.missingChunks))
                is FileTransferControl.Cancel -> JSONObject()
                    .put("type", "CANCEL")
                    .put("fileId", control.fileId)
                is FileTransferControl.Complete -> JSONObject()
                    .put("type", "COMPLETE")
                    .put("fileId", control.fileId)
            }
            writeField(output, json.toString().toByteArray(Charsets.UTF_8), MAX_SIGNAL_BYTES)
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
                        val descriptor = FileTransferDescriptor(
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
                        Decoded(
                            fileDescriptor = descriptor,
                            fileControl = FileTransferControl.Offer(descriptor)
                        )
                    }
                    CALL_SIGNAL -> Decoded(
                        callSignalJson = readField(input, MAX_SIGNAL_BYTES).toString(Charsets.UTF_8)
                    )
                    FILE_TRANSFER_CONTROL -> {
                        val controlJson = readField(input, MAX_SIGNAL_BYTES).toString(Charsets.UTF_8)
                        val json = JSONObject(controlJson)
                        val control = when (val type = json.getString("type")) {
                            "OFFER" -> {
                                val descriptor = FileTransferDescriptor(
                                    fileId = json.getString("fileId"),
                                    fileName = json.getString("fileName"),
                                    fileSize = json.getLong("fileSize"),
                                    mimeType = json.getString("mimeType"),
                                    sha256 = json.getString("sha256"),
                                    chunkSize = json.getInt("chunkSize"),
                                    totalChunks = json.getInt("totalChunks"),
                                    fileKeyBase64 = json.getString("fileKeyBase64"),
                                    senderId = json.getString("senderId"),
                                    recipientId = json.getString("recipientId"),
                                    createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
                                )
                                FileTransferControl.Offer(descriptor)
                            }
                            "READY" -> {
                                val fileId = json.getString("fileId")
                                val chunks = json.optJSONArray("missingChunks")?.let { arr ->
                                    (0 until arr.length()).map { arr.getInt(it) }
                                }
                                FileTransferControl.Ready(fileId, chunks)
                            }
                            "RESUME" -> {
                                val fileId = json.getString("fileId")
                                val chunks = json.optJSONArray("missingChunks")?.let { arr ->
                                    (0 until arr.length()).map { arr.getInt(it) }
                                } ?: emptyList()
                                FileTransferControl.Resume(fileId, chunks)
                            }
                            "CANCEL" -> FileTransferControl.Cancel(json.getString("fileId"))
                            "COMPLETE" -> FileTransferControl.Complete(json.getString("fileId"))
                            else -> throw IllegalArgumentException("Unknown file control type: $type")
                        }
                        Decoded(
                            fileControl = control,
                            fileDescriptor = (control as? FileTransferControl.Offer)?.descriptor
                        )
                    }
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
