package org.ciphrchat.app.transport.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
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
        TransportState(kind, TransportAvailability.AVAILABLE, "Wi-Fi Connected; NSD Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        return runCatching {
            val port = LAN_PORT
            lanConnection.startServer(port)
            lanDiscovery.start(port).getOrThrow()
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Listening on port $port")
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
        return if (serviceInfo != null) Reachability.Reachable else Reachability.Unreachable("Peer not discovered on LAN")
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
