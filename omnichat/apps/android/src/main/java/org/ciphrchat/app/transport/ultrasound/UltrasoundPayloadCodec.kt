package org.ciphrchat.app.transport.ultrasound

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** Compresses the verbose secure envelope before it crosses the low-rate audio link. */
object UltrasoundPayloadCodec {
    private val magic = byteArrayOf(0x43, 0x48, 0x55, 0x5A) // "CHUZ"
    private const val MAX_DECOMPRESSED_BYTES = 9 * 1024 * 1024

    fun encode(input: ByteArray): ByteArray {
        require(input.size <= MAX_DECOMPRESSED_BYTES) { "Nearby audio envelope is too large" }
        val compressed = ByteArrayOutputStream().use { buffer ->
            DeflaterOutputStream(buffer, Deflater(Deflater.BEST_SPEED, true)).use { it.write(input) }
            buffer.toByteArray()
        }
        return ByteBuffer.allocate(magic.size + Int.SIZE_BYTES + compressed.size).apply {
            put(magic)
            putInt(input.size)
            put(compressed)
        }.array()
    }

    fun decode(input: ByteArray): ByteArray? {
        if (input.size < magic.size + Int.SIZE_BYTES) return null
        if (!input.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val buffer = ByteBuffer.wrap(input)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        val expectedSize = buffer.int
        if (expectedSize !in 1..MAX_DECOMPRESSED_BYTES) return null
        val compressed = ByteArray(buffer.remaining()).also(buffer::get)
        return runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed), Inflater(true)).use { stream ->
                val output = ByteArrayOutputStream(minOf(expectedSize, 64 * 1024))
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    if (output.size() + count > expectedSize) return null
                    output.write(chunk, 0, count)
                }
                output.toByteArray().takeIf { it.size == expectedSize }
            }
        }.getOrNull()
    }
}
