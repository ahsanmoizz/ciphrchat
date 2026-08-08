package org.ciphrchat.app.transport.infrared

import java.io.ByteArrayOutputStream

/** Small framed protocol for the camera/IR optical bearer. */
object InfraredFrameCodec {
    private val MAGIC = byteArrayOf(0x43, 0x49, 0x52, 0x31) // CIR1
    const val MAX_PAYLOAD_BYTES = 2048

    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Infrared payload is too large" }
        val out = ByteArrayOutputStream(8 + payload.size)
        out.write(MAGIC)
        out.write(payload.size ushr 8)
        out.write(payload.size)
        out.write(payload)
        val crc = crc16(payload)
        out.write(crc ushr 8)
        out.write(crc)
        return out.toByteArray()
    }

    fun decode(frame: ByteArray): ByteArray? {
        if (frame.size < MAGIC.size + 4 || !frame.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val length = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
        if (length !in 1..MAX_PAYLOAD_BYTES || frame.size != 8 + length) return null
        val payload = frame.copyOfRange(6, 6 + length)
        val expected = ((frame[6 + length].toInt() and 0xFF) shl 8) or (frame[7 + length].toInt() and 0xFF)
        return payload.takeIf { crc16(it) == expected }
    }

    fun crc16(bytes: ByteArray): Int {
        var crc = 0xFFFF
        bytes.forEach { value ->
            crc = crc xor ((value.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1 xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }

    /** OOK symbols: a long IR burst is one, a short calibration burst is zero. */
    fun toConsumerIrPattern(frame: ByteArray): IntArray {
        val pattern = ArrayList<Int>(frame.size * 8 * 2 + 2)
        pattern += 120_000
        pattern += 80_000
        frame.forEach { value ->
            for (bit in 7 downTo 0) {
                pattern += if (((value.toInt() ushr bit) and 1) == 1) 70_000 else 5_000
                pattern += 30_000
            }
        }
        return pattern.toIntArray()
    }
}
