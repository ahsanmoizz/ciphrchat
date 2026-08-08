package org.ciphrchat.app.transport.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportInboundBus
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportWireCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcTransportCoordinator @Inject constructor(
    private val inboundBus: TransportInboundBus
) {
    companion object {
        val APPLICATION_ID = byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        private const val COMMAND_READ = 0x01
        private const val COMMAND_WRITE = 0x02
        private const val MAX_CHUNK = 240
        private const val MAX_FRAME_BYTES = 60 * 1024
        private const val TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nfcAdapter: NfcAdapter? = null
    private var pendingFrame: ByteArray? = null
    private var pendingResult: CompletableDeferred<SendResult>? = null
    private val incoming = ByteArrayOutputStream()
    private var incomingExpected = -1

    fun attach(activity: Activity) {
        nfcAdapter = activity.getSystemService(NfcAdapter::class.java)
        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> onTagDiscovered(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    fun detach(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
        nfcAdapter = null
    }

    suspend fun send(envelope: OutboundEnvelope): SendResult {
        val frame = runCatching {
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { out -> TransportWireCodec.write(out, envelope) }
                buffer.toByteArray()
            }
        }.getOrElse { return SendResult.Failed(it) }
        if (frame.size > MAX_FRAME_BYTES) return SendResult.Rejected("NFC envelope is too large")

        val result = CompletableDeferred<SendResult>()
        synchronized(this) {
            if (pendingFrame != null) return SendResult.Rejected("NFC transfer already waiting for a tap")
            pendingFrame = frame
            pendingResult = result
        }
        return withTimeoutOrNull(TIMEOUT_MS) { result.await() }
            ?: synchronized(this) {
                if (pendingResult === result) {
                    pendingFrame = null
                    pendingResult = null
                }
                SendResult.Rejected("Bring the other CiphrChat phone close to complete NFC transfer")
            }
    }

    fun hasPendingTransfer(): Boolean = synchronized(this) { pendingFrame != null }

    fun handleApdu(command: ByteArray): ByteArray {
        if (command.size < 4) return status(0x6D00)
        return when (command[1].toInt() and 0xFF) {
            COMMAND_READ -> {
                val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
                statusData(readPending(offset))
            }
            COMMAND_WRITE -> {
                val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
                acceptIncoming(offset, command.copyOfRange(4, command.size))
                status(0x9000)
            }
            else -> status(0x6D00)
        }
    }

    private fun onTagDiscovered(tag: Tag) {
        scope.launch {
            val isoDep = IsoDep.get(tag) ?: return@launch
            runCatching {
                isoDep.connect()
                isoDep.timeout = 1500
                val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, APPLICATION_ID.size.toByte()) + APPLICATION_ID + 0x00.toByte()
                check(isoDep.transceive(select).endsWithSuccess()) { "CiphrChat NFC service not available" }

                val outboundFrame = synchronized(this@NfcTransportCoordinator) { pendingFrame }
                val outboundResult = outboundFrame?.let { frame ->
                    writeRemote(isoDep, frame)
                    SendResult.Accepted(TransportKind.NFC_PAIRING, "nfc-tap")
                }
                readRemote(isoDep)
                if (outboundFrame != null) {
                    synchronized(this@NfcTransportCoordinator) {
                        if (pendingFrame === outboundFrame) pendingFrame = null
                    }
                }
                outboundResult
            }.onSuccess { result ->
                if (result != null) {
                    synchronized(this@NfcTransportCoordinator) {
                        pendingResult?.complete(result)
                        pendingResult = null
                    }
                }
            }.onFailure { error ->
                synchronized(this@NfcTransportCoordinator) {
                    pendingResult?.complete(SendResult.Failed(error))
                    pendingResult = null
                }
            }
            runCatching { isoDep.close() }
        }
    }

    private fun writeRemote(isoDep: IsoDep, frame: ByteArray) {
        val wire = ByteArray(4 + frame.size)
        wire[0] = (frame.size ushr 24).toByte()
        wire[1] = (frame.size ushr 16).toByte()
        wire[2] = (frame.size ushr 8).toByte()
        wire[3] = frame.size.toByte()
        frame.copyInto(wire, 4)
        var offset = 0
        while (offset < wire.size) {
            val take = minOf(MAX_CHUNK, wire.size - offset)
            val command = byteArrayOf(
                0x00,
                COMMAND_WRITE.toByte(),
                (offset ushr 8).toByte(),
                offset.toByte()
            ) + wire.copyOfRange(offset, offset + take)
            check(isoDep.transceive(command).endsWithSuccess()) { "NFC write rejected" }
            offset += take
        }
    }

    private fun readRemote(isoDep: IsoDep) {
        var remoteOffset = 0
        var expected = -1
        var firstResponse = true
        val result = ByteArrayOutputStream()
        while (remoteOffset < MAX_FRAME_BYTES) {
            val command = byteArrayOf(0x00, COMMAND_READ.toByte(), (remoteOffset ushr 8).toByte(), remoteOffset.toByte(), MAX_CHUNK.toByte())
            val response = isoDep.transceive(command)
            if (!response.endsWithSuccess()) break
            val body = response.copyOf(response.size - 2)
            if (body.isEmpty()) break
            if (firstResponse) {
                if (body.size < 4) break
                expected = ((body[0].toInt() and 0xFF) shl 24) or ((body[1].toInt() and 0xFF) shl 16) or
                    ((body[2].toInt() and 0xFF) shl 8) or (body[3].toInt() and 0xFF)
                result.write(body, 0, body.size - 4)
                remoteOffset = body.size - 4
                firstResponse = false
            } else {
                result.write(body)
                remoteOffset += body.size
            }
            if (body.size < MAX_CHUNK) break
            if (expected >= 0 && result.size() >= expected) break
        }
        val bytes = result.toByteArray()
        if (expected in 1..MAX_FRAME_BYTES && bytes.size >= expected) {
            publishIncoming(bytes.copyOf(expected))
        }
    }

    private fun readPending(offset: Int): ByteArray {
        val frame = synchronized(this) { pendingFrame } ?: return byteArrayOf()
        if (offset >= frame.size) return byteArrayOf()
        val payload = ByteArray(4 + minOf(MAX_CHUNK - 4, frame.size - offset))
        if (offset == 0) {
            payload[0] = (frame.size ushr 24).toByte()
            payload[1] = (frame.size ushr 16).toByte()
            payload[2] = (frame.size ushr 8).toByte()
            payload[3] = frame.size.toByte()
            frame.copyInto(payload, 4, 0, payload.size - 4)
        } else {
            // The reader advances by response body length. Offset zero is the
            // only response with a four-byte length prefix.
            return frame.copyOfRange(offset, minOf(frame.size, offset + MAX_CHUNK))
        }
        return payload
    }

    private fun acceptIncoming(offset: Int, bytes: ByteArray) {
        if (offset == 0) {
            incoming.reset()
            incomingExpected = if (bytes.size >= 4) {
                ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            } else -1
            if (bytes.size > 4) incoming.write(bytes, 4, bytes.size - 4)
        } else if (offset - 4 == incoming.size()) {
            incoming.write(bytes)
        }
        if (incomingExpected in 1..MAX_FRAME_BYTES && incoming.size() >= incomingExpected) {
            publishIncoming(incoming.toByteArray().copyOf(incomingExpected))
            incoming.reset()
            incomingExpected = -1
        }
    }

    private fun publishIncoming(frame: ByteArray) {
        runCatching {
            val envelope = TransportWireCodec.read(DataInputStream(ByteArrayInputStream(frame)))
            inboundBus.publish(TransportKind.NFC_PAIRING, envelope)
        }
    }

    private fun status(code: Int): ByteArray = byteArrayOf((code ushr 8).toByte(), code.toByte())
    private fun statusData(data: ByteArray): ByteArray = data + status(0x9000)
    private fun ByteArray.endsWithSuccess(): Boolean = size >= 2 && this[size - 2] == 0x90.toByte() && this[size - 1] == 0x00.toByte()
}
