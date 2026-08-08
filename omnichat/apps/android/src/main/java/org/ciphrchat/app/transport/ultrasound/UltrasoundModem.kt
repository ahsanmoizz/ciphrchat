package org.ciphrchat.app.transport.ultrasound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class UltrasoundModem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val SPACE_FREQUENCY = 18_000.0
        private const val MARK_FREQUENCY = 19_000.0
        private const val BAUD_RATE = 100.0
        private const val AMPLITUDE = 0.22
        private const val MAX_CAPTURE_SECONDS = 30
        private const val SYNC_MATCH_BITS = 48
    }

    private val samplesPerBit = (SAMPLE_RATE / BAUD_RATE).toInt()
    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listeningJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var listening = false
    private var capture = ShortArray(SAMPLE_RATE * MAX_CAPTURE_SECONDS)
    private var captureSize = 0
    private var lastAttemptSize = 0

    fun isListening(): Boolean = listening

    fun startListening(): Boolean {
        if (listening) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) return false
        return runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minimum * 2
            )
            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                stopListening()
                return false
            }
            listening = true
            captureSize = 0
            lastAttemptSize = 0
            listeningJob = scope.launch {
                val input = ShortArray(maxOf(minimum, samplesPerBit * 4))
                while (isActive && listening) {
                    val count = audioRecord?.read(input, 0, input.size) ?: -1
                    if (count > 0) {
                        appendSamples(input, count)
                        if (captureSize - lastAttemptSize >= samplesPerBit * 8) {
                            lastAttemptSize = captureSize
                            findAndEmitFrame()
                        }
                    }
                }
            }
            true
        }.getOrElse {
            stopListening()
            false
        }
    }

    fun stopListening() {
        listening = false
        listeningJob?.cancel()
        listeningJob = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        captureSize = 0
        lastAttemptSize = 0
    }

    /** Queues a bounded acoustic frame and returns whether it was accepted. */
    fun transmit(payload: ByteArray): Boolean {
        val frame = runCatching { UltrasoundFrameCodec.encode(payload) }.getOrNull() ?: return false
        val silence = ShortArray(SAMPLE_RATE / 10)
        val frameSamples = silence.size + frame.size * 8 * samplesPerBit + silence.size
        val audioBuffer = ShortArray(frameSamples)
        var sampleIndex = silence.size
        var phase = 0.0
        for (byte in frame) {
            for (bitIndex in 0 until 8) {
                val bit = (byte.toInt() ushr bitIndex) and 1
                val frequency = if (bit == 1) MARK_FREQUENCY else SPACE_FREQUENCY
                val phaseStep = 2.0 * Math.PI * frequency / SAMPLE_RATE
                repeat(samplesPerBit) {
                    audioBuffer[sampleIndex++] = (sin(phase) * Short.MAX_VALUE * AMPLITUDE).toInt().toShort()
                    phase += phaseStep
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                }
            }
        }
        return runCatching {
            audioTrack?.release()
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBuffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack?.write(audioBuffer, 0, audioBuffer.size)
            audioTrack?.play()
            true
        }.getOrElse {
            audioTrack?.release()
            audioTrack = null
            false
        }
    }

    private fun appendSamples(input: ShortArray, count: Int) {
        if (count >= capture.size) {
            input.copyInto(capture, 0, count - capture.size, count)
            captureSize = capture.size
            return
        }
        if (captureSize + count > capture.size) {
            val keep = capture.size / 2
            capture.copyInto(capture, 0, captureSize - keep, captureSize)
            captureSize = keep
        }
        input.copyInto(capture, captureSize, 0, count)
        captureSize += count
    }

    private fun findAndEmitFrame() {
        val preambleBits = UltrasoundFrameCodec.PREAMBLE_BYTES.size * 8
        val minimumBits = preambleBits + 8
        if (captureSize < minimumBits * samplesPerBit) return

        // Search the start phase in small steps. The long 0x55 preamble makes
        // this tolerant of independent microphone/audio-clock start times.
        val maxOffset = samplesPerBit - 1
        var foundOffset = -1
        for (offset in 0..maxOffset step 4) {
            var matches = 0
            for (bit in 0 until preambleBits) {
                val actual = classifyBit(offset + bit * samplesPerBit)
                val expected = (UltrasoundFrameCodec.PREAMBLE_BYTES[bit / 8].toInt() ushr (bit % 8)) and 1
                if (actual == expected) matches++
            }
            if (matches >= SYNC_MATCH_BITS) {
                foundOffset = offset
                break
            }
        }
        if (foundOffset < 0) {
            // Keep enough tail data to catch a preamble split over reads.
            val keep = minimumBits * samplesPerBit
            if (captureSize > keep) {
                capture.copyInto(capture, 0, captureSize - keep, captureSize)
                captureSize = keep
            }
            return
        }

        val lengthByteStart = foundOffset + preambleBits * samplesPerBit
        val codewordLength = readByte(lengthByteStart)
        if (codewordLength !in 18..255) {
            discardSamples(foundOffset + samplesPerBit)
            return
        }
        val totalBytes = UltrasoundFrameCodec.PREAMBLE_BYTES.size + 1 + codewordLength
        val totalSamples = totalBytes * 8 * samplesPerBit
        if (foundOffset + totalSamples > captureSize) return
        val frame = ByteArray(totalBytes)
        for (index in frame.indices) {
            frame[index] = readByte(foundOffset + index * 8 * samplesPerBit).toByte()
        }
        UltrasoundFrameCodec.decode(frame)?.let { _incomingData.tryEmit(it) }
        discardSamples(foundOffset + totalSamples)
    }

    private fun readByte(start: Int): Int {
        var value = 0
        repeat(8) { bit -> value = value or (classifyBit(start + bit * samplesPerBit) shl bit) }
        return value
    }

    private fun classifyBit(start: Int): Int {
        if (start < 0 || start + samplesPerBit > captureSize) return 0
        val space = goertzelPower(start, SPACE_FREQUENCY)
        val mark = goertzelPower(start, MARK_FREQUENCY)
        return if (mark > space) 1 else 0
    }

    private fun goertzelPower(start: Int, frequency: Double): Double {
        val coefficient = 2.0 * cos(2.0 * Math.PI * frequency / SAMPLE_RATE)
        var previous = 0.0
        var previous2 = 0.0
        for (index in start until start + samplesPerBit) {
            val current = capture[index].toDouble() + coefficient * previous - previous2
            previous2 = previous
            previous = current
        }
        return previous2 * previous2 + previous * previous - coefficient * previous * previous2
    }

    private fun discardSamples(count: Int) {
        if (count <= 0) return
        if (count >= captureSize) {
            captureSize = 0
            return
        }
        capture.copyInto(capture, 0, count, captureSize)
        captureSize -= count
    }
}
