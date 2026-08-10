package org.ciphrchat.app.transport.adapters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.ultrasound.UltrasoundChunkCodec
import org.ciphrchat.app.transport.ultrasound.UltrasoundModem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UltrasoundTransportAdapter @Inject constructor(
    private val modem: UltrasoundModem,
    private val inboundBus: TransportInboundBus
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.ULTRASOUND
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "Nearby audio is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inboundJob: Job? = null
    private data class Assembly(val total: Int, val parts: Array<ByteArray?>, var received: Int = 0)
    private val assemblies = mutableMapOf<String, Assembly>()
    private val pendingAcknowledgements = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    override suspend fun start(): Result<Unit> {
        if (!modem.startListening()) {
            _state.value = TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, "Microphone permission is required for nearby audio")
            return Result.failure(SecurityException("Microphone permission is required"))
        }
        if (inboundJob == null) {
            inboundJob = scope.launch {
                modem.incomingData.collect { bytes ->
                    runCatching {
                        UltrasoundChunkCodec.decodeAcknowledgement(bytes)?.let { transferId ->
                            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Nearby audio peer acknowledged a transfer")
                            pendingAcknowledgements.remove(transferId.key())?.complete(Unit)
                            return@runCatching
                        }
                        val chunk = UltrasoundChunkCodec.decode(bytes) ?: return@runCatching
                        val key = chunk.transferId.key()
                        // Ignore our own speaker echo. Without this guard the
                        // sender could acknowledge itself and falsely report delivery.
                        if (pendingAcknowledgements.containsKey(key)) return@runCatching
                        val assembled = synchronized(assemblies) {
                            val current = assemblies.getOrPut(key) {
                                Assembly(chunk.total, arrayOfNulls(chunk.total))
                            }
                            if (current.total != chunk.total) {
                                assemblies.remove(key)
                                return@synchronized null
                            }
                            if (current.parts[chunk.index] == null) {
                                current.parts[chunk.index] = chunk.data
                                current.received++
                            }
                            if (current.received == current.total) {
                                assemblies.remove(key)
                                current.parts.filterNotNull().fold(ByteArray(0)) { acc, part -> acc + part }
                            } else null
                        } ?: return@runCatching
                        val envelope = TransportWireCodec.read(DataInputStream(ByteArrayInputStream(assembled)))
                        if (inboundBus.publish(kind, envelope)) {
                            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Nearby audio message received and acknowledged")
                            modem.transmit(UltrasoundChunkCodec.encodeAcknowledgement(chunk.transferId))
                        }
                    }
                }
            }
        }
        if (_state.value.availability != TransportAvailability.AVAILABLE) {
            _state.value = TransportState(kind, TransportAvailability.STARTING, "Nearby audio listening; waiting to verify another CiphrChat phone")
        }
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        modem.stopListening()
        inboundJob?.cancel()
        inboundJob = null
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Peer discovery relies on demodulated incoming identity pings
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return if (modem.isListening()) Reachability.Reachable
        else Reachability.Unreachable("Nearby audio is not started")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val bytes = runCatching {
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { out -> TransportWireCodec.write(out, envelope) }
                buffer.toByteArray()
            }
        }.getOrElse { return SendResult.Failed(it) }
        val transferId = ByteBuffer.allocate(16)
            .putLong(UUID.randomUUID().mostSignificantBits)
            .putLong(UUID.randomUUID().leastSignificantBits)
            .array()
        val transferKey = transferId.key()
        val acknowledgement = CompletableDeferred<Unit>()
        pendingAcknowledgements[transferKey] = acknowledgement
        val total = maxOf(1, (bytes.size + UltrasoundChunkCodec.MAX_CHUNK_BYTES - 1) / UltrasoundChunkCodec.MAX_CHUNK_BYTES)
        if (total > 0xFFFF) {
            pendingAcknowledgements.remove(transferKey)
            return SendResult.Rejected("Nearby audio envelope is too large")
        }
        return try {
            for (index in 0 until total) {
                val start = index * UltrasoundChunkCodec.MAX_CHUNK_BYTES
                val end = minOf(bytes.size, start + UltrasoundChunkCodec.MAX_CHUNK_BYTES)
                val frame = UltrasoundChunkCodec.encode(
                    UltrasoundChunkCodec.Chunk(transferId, index, total, bytes.copyOfRange(start, end))
                )
                if (!modem.transmit(frame)) {
                    return SendResult.Failed(IllegalStateException("Nearby audio transmission failed at chunk ${index + 1}/$total"))
                }
            }
            if (withTimeoutOrNull(12_000L) { acknowledgement.await() } == null) {
                _state.value = TransportState(kind, TransportAvailability.ERROR, "Nearby audio sent no verified receiver acknowledgement")
                SendResult.Rejected("Nearby audio was sent but no receiver acknowledgement was heard")
            } else {
                _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Nearby audio delivery acknowledged by the receiver")
                SendResult.Accepted(kind, "acoustic-acknowledged-$total-frames")
            }
        } finally {
            pendingAcknowledgements.remove(transferKey)
        }
    }

    private fun ByteArray.key(): String = joinToString("") { byte -> "%02x".format(byte) }
}
