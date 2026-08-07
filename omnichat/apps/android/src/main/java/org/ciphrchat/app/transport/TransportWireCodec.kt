package org.ciphrchat.app.transport

import java.io.DataInput
import java.io.DataOutput

/** Length-bounded framing for local transports. Signal encryption remains end-to-end. */
object TransportWireCodec {
    private const val MAGIC = 0x43495048 // "CIPH"
    private const val MAX_FIELD_BYTES = 4 * 1024
    private const val MAX_PAYLOAD_BYTES = 1 * 1024 * 1024

    fun write(output: DataOutput, envelope: OutboundEnvelope) {
        val messageId = envelope.messageId.toByteArray(Charsets.UTF_8)
        val recipientId = envelope.recipientId.toByteArray(Charsets.UTF_8)
        val senderId = envelope.senderId.toByteArray(Charsets.UTF_8)
        require(messageId.size <= MAX_FIELD_BYTES && recipientId.size <= MAX_FIELD_BYTES && senderId.size <= MAX_FIELD_BYTES)
        require(envelope.encryptedPayload.size <= MAX_PAYLOAD_BYTES)
        output.writeInt(MAGIC)
        output.writeInt(envelope.protocolVersion)
        writeField(output, messageId)
        writeField(output, recipientId)
        writeField(output, senderId)
        output.writeLong(envelope.createdAtEpochMs)
        output.writeLong(envelope.expiresAtEpochMs)
        output.writeInt(envelope.hopLimit)
        output.writeBoolean(envelope.testOnly)
        writeField(output, envelope.encryptedPayload)
    }

    fun read(input: DataInput): OutboundEnvelope {
        require(input.readInt() == MAGIC) { "Invalid CiphrChat local transport frame" }
        val protocolVersion = input.readInt()
        val messageId = readField(input).toString(Charsets.UTF_8)
        val recipientId = readField(input).toString(Charsets.UTF_8)
        val senderId = readField(input).toString(Charsets.UTF_8)
        val createdAt = input.readLong()
        val expiresAt = input.readLong()
        val hopLimit = input.readInt()
        val testOnly = input.readBoolean()
        val payload = readField(input, MAX_PAYLOAD_BYTES)
        require(protocolVersion == 1) { "Unsupported CiphrChat local transport version" }
        require(hopLimit in 0..16) { "Invalid local transport hop limit" }
        require(messageId.isNotBlank() && recipientId.isNotBlank() && senderId.isNotBlank()) { "Incomplete CiphrChat envelope" }
        return OutboundEnvelope(protocolVersion, messageId, recipientId, senderId, createdAt, expiresAt, hopLimit, payload, testOnly)
    }

    private fun writeField(output: DataOutput, value: ByteArray) {
        output.writeInt(value.size)
        output.write(value)
    }

    private fun readField(input: DataInput, maxBytes: Int = MAX_FIELD_BYTES): ByteArray {
        val length = input.readInt()
        require(length in 0..maxBytes) { "Invalid local transport frame length" }
        return ByteArray(length).also(input::readFully)
    }
}
