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
    private const val GROUP_MESSAGE = 6
    private const val GROUP_INVITE = 7
    private const val GROUP_LEAVE = 8
    private const val MAX_FIELD_BYTES = 4 * 1024
    private const val MAX_SIGNAL_BYTES = 64 * 1024
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

    data class GroupMessagePayload(
        val groupId: String,
        val groupMessageId: String,
        val senderId: String,
        val text: String? = null,
        val attachment: Attachment? = null,
        val fileDescriptor: FileTransferDescriptor? = null,
        val isForwarded: Boolean = false,
        val createdAtEpochMs: Long = System.currentTimeMillis()
    )

    data class GroupInvitePayload(
        val groupId: String,
        val groupName: String,
        val creatorId: String,
        val memberIds: List<String>,
        val createdAtEpochMs: Long = System.currentTimeMillis()
    )

    data class GroupLeavePayload(
        val groupId: String,
        val memberId: String,
        val createdAtEpochMs: Long = System.currentTimeMillis()
    )

    data class Decoded(
        val text: String? = null,
        val attachment: Attachment? = null,
        val fileDescriptor: FileTransferDescriptor? = null,
        val callSignalJson: String? = null,
        val fileControl: FileTransferControl? = null,
        val groupMessage: GroupMessagePayload? = null,
        val groupInvite: GroupInvitePayload? = null,
        val groupLeave: GroupLeavePayload? = null
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

    fun encodeGroupMessage(payload: GroupMessagePayload): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(GROUP_MESSAGE)
            writeField(output, payload.groupId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, payload.groupMessageId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, payload.senderId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            writeField(output, (payload.text ?: "").toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
            output.writeBoolean(payload.isForwarded)
            output.writeLong(payload.createdAtEpochMs)

            val att = payload.attachment
            output.writeBoolean(att != null)
            if (att != null) {
                writeField(output, att.fileName.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, att.mimeType.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, att.bytes, MAX_ATTACHMENT_BYTES)
            }

            val desc = payload.fileDescriptor
            output.writeBoolean(desc != null)
            if (desc != null) {
                writeField(output, desc.fileId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, desc.fileName.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                output.writeLong(desc.fileSize)
                writeField(output, desc.mimeType.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, desc.sha256.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                output.writeInt(desc.chunkSize)
                output.writeInt(desc.totalChunks)
                writeField(output, desc.fileKeyBase64.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, desc.senderId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                writeField(output, desc.recipientId.toByteArray(Charsets.UTF_8), MAX_FIELD_BYTES)
                output.writeLong(desc.createdAtEpochMs)
            }
        }
        encoded.toByteArray()
    }

    fun encodeGroupInvite(payload: GroupInvitePayload): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(GROUP_INVITE)
            val json = JSONObject()
                .put("groupId", payload.groupId)
                .put("groupName", payload.groupName)
                .put("creatorId", payload.creatorId)
                .put("memberIds", JSONArray(payload.memberIds))
                .put("createdAtEpochMs", payload.createdAtEpochMs)
            writeField(output, json.toString().toByteArray(Charsets.UTF_8), MAX_SIGNAL_BYTES)
        }
        encoded.toByteArray()
    }

    fun encodeGroupLeave(payload: GroupLeavePayload): ByteArray = ByteArrayOutputStream().use { encoded ->
        DataOutputStream(encoded).use { output ->
            output.write(MAGIC)
            output.writeByte(GROUP_LEAVE)
            val json = JSONObject()
                .put("groupId", payload.groupId)
                .put("memberId", payload.memberId)
                .put("createdAtEpochMs", payload.createdAtEpochMs)
            writeField(output, json.toString().toByteArray(Charsets.UTF_8), MAX_SIGNAL_BYTES)
        }
        encoded.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded {
        if (!bytes.startsWith(MAGIC)) return Decoded(text = bytes.toString(Charsets.UTF_8))
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes, MAGIC.size, bytes.size - MAGIC.size)).use { input ->
                when (input.readByte().toInt()) {
                    TEXT -> Decoded(
                        text = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                    )
                    ATTACHMENT -> {
                        val fileName = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val mimeType = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val payload = readField(input, MAX_ATTACHMENT_BYTES)
                        Decoded(
                            text = "Attachment: $fileName",
                            attachment = Attachment(
                                fileName = fileName,
                                mimeType = mimeType,
                                bytes = payload
                            )
                        )
                    }
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
                    GROUP_MESSAGE -> {
                        val groupId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val groupMessageId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val senderId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                        val textBytes = readField(input, MAX_FIELD_BYTES)
                        val text = if (textBytes.isNotEmpty()) textBytes.toString(Charsets.UTF_8) else null
                        val isForwarded = input.readBoolean()
                        val createdAtEpochMs = input.readLong()

                        val hasAttachment = input.readBoolean()
                        val attachment = if (hasAttachment) {
                            val fileName = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val mimeType = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val bytes = readField(input, MAX_ATTACHMENT_BYTES)
                            Attachment(fileName, mimeType, bytes)
                        } else null

                        val hasFileDescriptor = input.readBoolean()
                        val fileDescriptor = if (hasFileDescriptor) {
                            val fileId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val fileName = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val fileSize = input.readLong()
                            val mimeType = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val sha256 = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val chunkSize = input.readInt()
                            val totalChunks = input.readInt()
                            val fileKeyBase64 = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val descSenderId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val descRecipientId = readField(input, MAX_FIELD_BYTES).toString(Charsets.UTF_8)
                            val descCreatedAt = input.readLong()
                            FileTransferDescriptor(
                                fileId = fileId,
                                fileName = fileName,
                                fileSize = fileSize,
                                mimeType = mimeType,
                                sha256 = sha256,
                                chunkSize = chunkSize,
                                totalChunks = totalChunks,
                                fileKeyBase64 = fileKeyBase64,
                                senderId = descSenderId,
                                recipientId = descRecipientId,
                                createdAtEpochMs = descCreatedAt
                            )
                        } else null

                        Decoded(
                            groupMessage = GroupMessagePayload(
                                groupId = groupId,
                                groupMessageId = groupMessageId,
                                senderId = senderId,
                                text = text,
                                attachment = attachment,
                                fileDescriptor = fileDescriptor,
                                isForwarded = isForwarded,
                                createdAtEpochMs = createdAtEpochMs
                            )
                        )
                    }
                    GROUP_INVITE -> {
                        val jsonStr = readField(input, MAX_SIGNAL_BYTES).toString(Charsets.UTF_8)
                        val json = JSONObject(jsonStr)
                        val memberArray = json.optJSONArray("memberIds")
                        val members = if (memberArray != null) {
                            (0 until memberArray.length()).map { memberArray.getString(it) }
                        } else emptyList()
                        Decoded(
                            groupInvite = GroupInvitePayload(
                                groupId = json.getString("groupId"),
                                groupName = json.getString("groupName"),
                                creatorId = json.getString("creatorId"),
                                memberIds = members,
                                createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                    GROUP_LEAVE -> {
                        val jsonStr = readField(input, MAX_SIGNAL_BYTES).toString(Charsets.UTF_8)
                        val json = JSONObject(jsonStr)
                        Decoded(
                            groupLeave = GroupLeavePayload(
                                groupId = json.getString("groupId"),
                                memberId = json.getString("memberId"),
                                createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
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
