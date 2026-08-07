package org.ciphrchat.app.transport.adapters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.ultrasound.UltrasoundModem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UltrasoundTransportAdapter @Inject constructor(
    private val modem: UltrasoundModem
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.ULTRASOUND
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(TransportAvailability.EXPERIMENTAL, "Ultrasound PoC Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        modem.startListening()
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Listening for ultrasound pings")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        modem.stopListening()
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Peer discovery relies on demodulated incoming identity pings
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (envelope.encryptedPayload.size > 20) {
            return SendResult.Failure(Exception("Payload too large for ultrasound transmission"))
        }
        
        modem.transmit(envelope.encryptedPayload)
        return SendResult.Success
    }
}
