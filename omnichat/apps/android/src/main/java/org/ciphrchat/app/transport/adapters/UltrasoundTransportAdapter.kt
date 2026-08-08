package org.ciphrchat.app.transport.adapters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.ciphrchat.app.transport.*
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

    override suspend fun start(): Result<Unit> {
        if (!modem.startListening()) {
            _state.value = TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, "Microphone permission is required for nearby audio")
            return Result.failure(SecurityException("Microphone permission is required"))
        }
        if (inboundJob == null) {
            inboundJob = scope.launch {
                modem.incomingData.collect { bytes ->
                    runCatching {
                        val envelope = TransportWireCodec.read(DataInputStream(ByteArrayInputStream(bytes)))
                        inboundBus.publish(kind, envelope)
                    }
                }
            }
        }
        _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Secure nearby audio messaging ready")
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
        return if (modem.transmit(bytes)) {
            SendResult.Accepted(kind, "acoustic-broadcast")
        } else {
            SendResult.Failed(IllegalStateException("Nearby audio frame is too large or unavailable"))
        }
    }
}
