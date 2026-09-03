package org.ciphrchat.app.transport.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.Reachability
import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportAdapter
import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportCapability
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanTransportAdapter @Inject constructor(
    private val lanDiscovery: LanDiscovery,
    private val lanConnection: LanConnection
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.WIFI_LAN
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "Local discovery is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        if (_state.value.availability == TransportAvailability.AVAILABLE) {
            return Result.success(Unit)
        }
        return runCatching {
            val port = LAN_PORT
            lanConnection.startServer(port)
            lanDiscovery.start(port).getOrThrow()
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Listening on port $port")
        }.onFailure { error ->
            _state.value = TransportState(kind, TransportAvailability.ERROR, error.message ?: "LAN transport failed")
        }
    }

    override suspend fun stop(): Result<Unit> {
        lanDiscovery.stop()
        lanConnection.stopServer()
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(lanDiscovery.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        val serviceInfo = lanDiscovery.getResolvedService(recipientId)
        return if (serviceInfo != null) Reachability.Reachable else Reachability.Unknown
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val serviceInfo = lanDiscovery.getResolvedService(envelope.recipientId)
            ?: return SendResult.Rejected("Peer not found on LAN")

        val host = serviceInfo.host?.hostAddress ?: return SendResult.Failed(Exception("Host address missing"))
        val port = serviceInfo.port

        val result = lanConnection.sendEnvelope(host, port, envelope)
        return if (result.isSuccess) SendResult.Accepted(kind, "lan-frame")
        else SendResult.Failed(result.exceptionOrNull() ?: Exception("Send failed"))
    }

    private companion object { const val LAN_PORT = 40123 }
}
