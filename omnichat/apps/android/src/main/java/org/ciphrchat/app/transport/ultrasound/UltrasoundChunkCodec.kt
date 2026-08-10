package org.ciphrchat.app.transport.ultrasound

import java.nio.ByteBuffer

/** Fragments a transport envelope across the modem's 237-byte outer frame. */
object UltrasoundChunkCodec {
    private val magic = byteArrayOf(0x43, 0x48, 0x55, 0x32)
    private val ackMagic = byteArrayOf(0x43, 0x48, 0x55, 0x41)
    const val TRANSFER_ID_BYTES = 16
    private const val HEADER_BYTES = 4 + TRANSFER_ID_BYTES + 2 + 2
    const val MAX_CHUNK_BYTES = UltrasoundFrameCodec.MAX_PAYLOAD_BYTES - HEADER_BYTES

    data class Chunk(val transferId: ByteArray, val index: Int, val total: Int, val data: ByteArray)

    fun encode(chunk: Chunk): ByteArray {
        require(chunk.transferId.size == TRANSFER_ID_BYTES)
        require(chunk.total in 1..0xFFFF && chunk.index in 0 until chunk.total)
        require(chunk.data.size <= MAX_CHUNK_BYTES)
        return ByteBuffer.allocate(HEADER_BYTES + chunk.data.size).apply {
            put(magic)
            put(chunk.transferId)
            putShort(chunk.index.toShort())
            putShort(chunk.total.toShort())
            put(chunk.data)
        }.array()
    }

    fun decode(bytes: ByteArray): Chunk? {
        if (bytes.size < HEADER_BYTES || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val buffer = ByteBuffer.wrap(bytes)
        val actualMagic = ByteArray(magic.size)
        buffer.get(actualMagic)
        val id = ByteArray(TRANSFER_ID_BYTES)
        buffer.get(id)
        val index = buffer.short.toInt() and 0xFFFF
        val total = buffer.short.toInt() and 0xFFFF
        if (total == 0 || index >= total || bytes.size - HEADER_BYTES > MAX_CHUNK_BYTES) return null
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return Chunk(id, index, total, data)
    }

    fun encodeAcknowledgement(transferId: ByteArray): ByteArray {
        require(transferId.size == TRANSFER_ID_BYTES)
        return ackMagic + transferId
    }

    fun decodeAcknowledgement(bytes: ByteArray): ByteArray? {
        if (bytes.size != ackMagic.size + TRANSFER_ID_BYTES) return null
        if (!bytes.copyOfRange(0, ackMagic.size).contentEquals(ackMagic)) return null
        return bytes.copyOfRange(ackMagic.size, bytes.size)
    }
}
