package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfraredTransportAdapter @Inject constructor(
    private val context: Context
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.INFRARED
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.EXPERIMENTAL, "IR hardware can transmit only; no authenticated receiver is available")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?

    override suspend fun start(): Result<Unit> {
        if (irManager == null || !irManager.hasIrEmitter()) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "No IR hardware detected")
            return Result.failure(Exception("IR not supported"))
        }
        
        _state.value = TransportState(kind, TransportAvailability.EXPERIMENTAL, "IR hardware detected; CiphrChat IR messaging is unavailable")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Infrared is unidirectional and cannot discover peers
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Unreachable("IR messaging is unavailable")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Rejected("IR messaging requires an authenticated bidirectional receiver")
    }
}
