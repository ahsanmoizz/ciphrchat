package org.ciphrchat.app.transport.ultrasound

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

@Singleton
class UltrasoundModem @Inject constructor() {
    private val sampleRate = 44100
    private val freqSpace = 18000.0 // 18kHz
    private val freqMark = 19000.0 // 19kHz
    private val baudRate = 50.0 // Slow for PoC
    
    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private val gf = GenericGF.DATA_MATRIX_FIELD_256
    private val rsEncoder = ReedSolomonEncoder(gf)
    private val rsDecoder = ReedSolomonDecoder(gf)
    private val eccBytes = 4

    fun startListening() {
        if (isListening) return
        
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        // Requires RECORD_AUDIO permission
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
            isListening = true
            
            // In a real implementation, start a thread to FFT process the mic input
            // to detect frequencies and demodulate into bytes.
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun transmit(payload: ByteArray) {
        val encodedPayload = encodeWithFEC(payload)
        
        val samplesPerBit = (sampleRate / baudRate).toInt()
        val totalSamples = encodedPayload.size * 8 * samplesPerBit
        val audioBuffer = ShortArray(totalSamples)
        
        var sampleIndex = 0
        for (byte in encodedPayload) {
            for (i in 0..7) {
                val bit = (byte.toInt() shr i) and 1
                val freq = if (bit == 1) freqMark else freqSpace
                
                for (j in 0 until samplesPerBit) {
                    val time = sampleIndex / sampleRate.toDouble()
                    val sample = (sin(2.0 * Math.PI * freq * time) * Short.MAX_VALUE).toInt().toShort()
                    audioBuffer[sampleIndex++] = sample
                }
            }
        }

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            audioBuffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        audioTrack?.write(audioBuffer, 0, audioBuffer.size)
        audioTrack?.play()
    }

    private fun encodeWithFEC(data: ByteArray): ByteArray {
        val toEncode = IntArray(data.size + eccBytes)
        for (i in data.indices) {
            toEncode[i] = data[i].toInt() and 0xFF
        }
        rsEncoder.encode(toEncode, eccBytes)
        
        val result = ByteArray(toEncode.size)
        for (i in toEncode.indices) {
            result[i] = toEncode[i].toByte()
        }
        return result
    }

    private fun decodeWithFEC(data: ByteArray): ByteArray? {
        val toDecode = IntArray(data.size)
        for (i in data.indices) {
            toDecode[i] = data[i].toInt() and 0xFF
        }
        
        return try {
            rsDecoder.decode(toDecode, eccBytes)
            val decodedLength = data.size - eccBytes
            val result = ByteArray(decodedLength)
            for (i in 0 until decodedLength) {
                result[i] = toDecode[i].toByte()
            }
            result
        } catch (e: Exception) {
            null // Decoding failed
        }
    }
}
