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
        TransportState(kind, TransportAvailability.EXPERIMENTAL, "Ultrasound demodulation is not production-ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        modem.startListening()
        _state.value = TransportState(kind, TransportAvailability.EXPERIMENTAL, "Ultrasound capture is experimental and not an authenticated message route")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        modem.stopListening()
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Peer discovery relies on demodulated incoming identity pings
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Unreachable("Ultrasound messaging is unavailable")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Rejected("Ultrasound messaging is experimental and disabled for automatic routing")
    }
}
