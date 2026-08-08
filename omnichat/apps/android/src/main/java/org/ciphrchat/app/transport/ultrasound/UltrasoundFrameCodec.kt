package org.ciphrchat.app.transport.ultrasound

import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import java.util.zip.CRC32

/**
 * Bounded frame format for the acoustic carrier.
 *
 * The outer header lets a receiver know the Reed-Solomon codeword length. The
 * protected inner header and CRC reject false tone detections before bytes are
 * handed to TransportWireCodec.
 */
object UltrasoundFrameCodec {
    private val magic = byteArrayOf(0x43, 0x48, 0x55, 0x31)
    private const val ECC_BYTES = 8
    private const val MAX_CODEWORD_BYTES = 255
    const val MAX_PAYLOAD_BYTES = MAX_CODEWORD_BYTES - ECC_BYTES - 10

    private val field = GenericGF.DATA_MATRIX_FIELD_256
    private val encoder = ReedSolomonEncoder(field)
    private val decoder = ReedSolomonDecoder(field)

    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "Ultrasound payload exceeds ${MAX_PAYLOAD_BYTES} bytes"
        }
        val raw = ByteArray(magic.size + 2 + payload.size + 4)
        magic.copyInto(raw)
        raw[4] = (payload.size ushr 8).toByte()
        raw[5] = payload.size.toByte()
        payload.copyInto(raw, 6)
        val crc = CRC32().apply { update(payload) }.value
        val crcOffset = 6 + payload.size
        raw[crcOffset] = (crc ushr 24).toByte()
        raw[crcOffset + 1] = (crc ushr 16).toByte()
        raw[crcOffset + 2] = (crc ushr 8).toByte()
        raw[crcOffset + 3] = crc.toByte()

        val codeword = IntArray(raw.size + ECC_BYTES) { index ->
            if (index < raw.size) raw[index].toInt() and 0xFF else 0
        }
        encoder.encode(codeword, ECC_BYTES)

        // Repeated sync bytes provide a receiver clock and a false-detection
        // guard before the length-bearing outer header.
        val frame = ByteArray(PREAMBLE_BYTES.size + 1 + codeword.size)
        PREAMBLE_BYTES.copyInto(frame)
        frame[PREAMBLE_BYTES.size] = codeword.size.toByte()
        codeword.forEachIndexed { index, value -> frame[PREAMBLE_BYTES.size + 1 + index] = value.toByte() }
        return frame
    }

    fun decode(frame: ByteArray): ByteArray? {
        if (frame.size < PREAMBLE_BYTES.size + 1 + ECC_BYTES + 10) return null
        if (!frame.copyOfRange(0, PREAMBLE_BYTES.size).contentEquals(PREAMBLE_BYTES)) return null
        val codewordLength = frame[PREAMBLE_BYTES.size].toInt() and 0xFF
        if (codewordLength !in (ECC_BYTES + 10)..MAX_CODEWORD_BYTES) return null
        if (frame.size < PREAMBLE_BYTES.size + 1 + codewordLength) return null

        val codeword = IntArray(codewordLength) { index ->
            frame[PREAMBLE_BYTES.size + 1 + index].toInt() and 0xFF
        }
        return try {
            decoder.decode(codeword, ECC_BYTES)
            val rawLength = codewordLength - ECC_BYTES
            if (rawLength < 10) return null
            if (!ByteArray(magic.size) { index -> codeword[index].toByte() }.contentEquals(magic)) return null
            val payloadLength = ((codeword[4] and 0xFF) shl 8) or (codeword[5] and 0xFF)
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES || payloadLength + 10 > rawLength) return null
            val payload = ByteArray(payloadLength) { index -> codeword[6 + index].toByte() }
            val crcOffset = 6 + payloadLength
            val expected = ((codeword[crcOffset] and 0xFF) shl 24) or
                ((codeword[crcOffset + 1] and 0xFF) shl 16) or
                ((codeword[crcOffset + 2] and 0xFF) shl 8) or
                (codeword[crcOffset + 3] and 0xFF)
            val actual = CRC32().apply { update(payload) }.value.toInt()
            if (expected != actual) null else payload
        } catch (_: Exception) {
            null
        }
    }

    val PREAMBLE_BYTES: ByteArray = ByteArray(8) { 0x55.toByte() }
}
