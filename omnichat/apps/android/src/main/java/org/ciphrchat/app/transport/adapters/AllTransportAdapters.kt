package org.ciphrchat.app.transport.adapters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalTransportAdapter @Inject constructor() : TransportAdapter {
    override val kind = TransportKind.EXTERNAL
    override val capabilities = emptySet<TransportCapability>()
    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.UNAVAILABLE, "No supported external transport is connected")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> = Result.failure(UnsupportedOperationException("External transport is not configured"))
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> = Result.success(emptyList())
    override suspend fun canReach(recipientId: String): Reachability = Reachability.Unreachable("External transport is unavailable")
    override suspend fun send(envelope: OutboundEnvelope): SendResult = SendResult.Rejected("External transport is unavailable")
}
