package org.ciphrchat.app.transport.infrared

/** Timestamped decoder used by CameraX; kept pure so the optical protocol is testable. */
class InfraredOpticalDecoder(
    private val symbolMs: Long = 100L
) {
    private val magic = byteArrayOf(0x43, 0x49, 0x52, 0x31)
    private val bits = ArrayDeque<Int>()
    private var lastSymbolMs = Long.MIN_VALUE
    private var baseline = 0f
    private var collecting = false
    private var expectedBytes = -1
    private var expectedPayloadBytes = -1

    fun reset() {
        bits.clear()
        lastSymbolMs = Long.MIN_VALUE
        baseline = 0f
        collecting = false
        expectedBytes = -1
        expectedPayloadBytes = -1
    }

    fun offer(intensity: Int, timestampMs: Long): ByteArray? {
        val value = intensity.coerceIn(0, 255).toFloat()
        if (baseline == 0f) baseline = value else if (!collecting) baseline = baseline * 0.9f + value * 0.1f
        if (lastSymbolMs != Long.MIN_VALUE && timestampMs - lastSymbolMs < symbolMs) return null
        lastSymbolMs = timestampMs
        val threshold = baseline + maxOf(10f, baseline * 0.18f)
        bits.addLast(if (value > threshold) 1 else 0)
        while (bits.size > 64) bits.removeFirst()

        if (!collecting && bits.size >= 32 && lastBytes(bits, 32).contentEquals(magic)) {
            collecting = true
            while (bits.isNotEmpty()) bits.removeFirst()
            return null
        }
        if (!collecting) return null
        if (expectedBytes < 0 && bits.size >= 16) {
            expectedPayloadBytes = readBits(bits, 16)
            expectedBytes = expectedPayloadBytes + 2
            if (expectedPayloadBytes !in 1..InfraredFrameCodec.MAX_PAYLOAD_BYTES) {
                reset()
                return null
            }
            repeat(16) { bits.removeFirst() }
        }
        if (expectedBytes > 0 && bits.size >= expectedBytes * 8) {
            val frame = ByteArray(expectedBytes) { readBits(bits, 8, it * 8).toByte() }
            val payloadLength = expectedPayloadBytes
            reset()
            return InfraredFrameCodec.decode(
                magic + byteArrayOf((payloadLength ushr 8).toByte(), payloadLength.toByte()) + frame
            )
        }
        return null
    }

    private fun lastBytes(values: ArrayDeque<Int>, count: Int): ByteArray {
        val list = values.toList().takeLast(count)
        return ByteArray(count / 8) { index ->
            list.subList(index * 8, index * 8 + 8).fold(0) { acc, bit -> (acc shl 1) or bit }.toByte()
        }
    }

    private fun readBits(values: ArrayDeque<Int>, count: Int, start: Int = 0): Int {
        val list = values.toList()
        return (start until start + count).fold(0) { acc, index -> (acc shl 1) or list[index] }
    }
}
