package org.ciphrchat.app.transport.adapters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.ciphrchat.app.transport.*
import java.util.UUID

abstract class BaseMockTransportAdapter(
    final override val kind: TransportKind,
    initialAvailability: TransportAvailability,
    initialDetail: String,
    final override val capabilities: Set<TransportCapability>
) : TransportAdapter {

    private val mutableState = MutableStateFlow(
        TransportState(kind, initialAvailability, initialDetail)
    )
    final override val state: StateFlow<TransportState> = mutableState

    override suspend fun start(): Result<Unit> = runCatching {
        mutableState.value = mutableState.value.copy(
            availability = TransportAvailability.AVAILABLE,
            detail = "Prototype adapter ready"
        )
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        mutableState.value = mutableState.value.copy(
            availability = TransportAvailability.DISABLED_BY_USER,
            detail = "Disabled"
        )
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> = Result.success(emptyList())

    override suspend fun canReach(recipientId: String): Reachability {
        return if (state.value.availability == TransportAvailability.AVAILABLE) {
            Reachability.Reachable
        } else {
            Reachability.Unknown
        }
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (state.value.availability != TransportAvailability.AVAILABLE) {
            return SendResult.Rejected("Transport is not available")
        }
        return SendResult.Accepted(kind, UUID.randomUUID().toString())
    }
}
