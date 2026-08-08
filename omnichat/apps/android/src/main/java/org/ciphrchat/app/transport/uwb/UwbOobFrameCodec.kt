package org.ciphrchat.app.transport.uwb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Authenticated BLE control payload used to exchange UWB ranging parameters. */
object UwbOobFrameCodec {
    private const val MAGIC = 0x55434231 // UCB1
    private const val VERSION = 1
    const val HELLO = 1
    const val READY = 2

    data class Frame(
        val type: Int,
        val sessionId: Int,
        val channel: Int,
        val preambleIndex: Int,
        val address: ByteArray,
        val peerAddress: ByteArray?,
        val sessionKey: ByteArray
    )

    fun encode(frame: Frame): ByteArray {
        require(frame.address.size in 2..8)
        require(frame.peerAddress == null || frame.peerAddress.size in 2..8)
        require(frame.sessionKey.size == 16 || frame.sessionKey.size == 32)
        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC)
                out.writeByte(VERSION)
                out.writeByte(frame.type)
                out.writeInt(frame.sessionId)
                out.writeByte(frame.channel)
                out.writeByte(frame.preambleIndex)
                out.writeByte(frame.address.size)
                out.write(frame.address)
                out.writeByte(frame.peerAddress?.size ?: 0)
                frame.peerAddress?.let(out::write)
                out.writeByte(frame.sessionKey.size)
                out.write(frame.sessionKey)
            }
            buffer.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): Frame? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION) return null
            val type = input.readUnsignedByte()
            if (type !in HELLO..READY) return null
            val sessionId = input.readInt()
            val channel = input.readUnsignedByte()
            val preamble = input.readUnsignedByte()
            val address = ByteArray(input.readUnsignedByte()).also(input::readFully)
            val peerLength = input.readUnsignedByte()
            val peer = if (peerLength == 0) null else ByteArray(peerLength).also(input::readFully)
            val key = ByteArray(input.readUnsignedByte()).also(input::readFully)
            if (address.size !in 2..8 || (peer != null && peer.size !in 2..8) || key.size !in 16..32) return null
            Frame(type, sessionId, channel, preamble, address, peer, key)
        }
    }.getOrNull()
}
