package org.ciphrchat.app.transport.wifi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.lan.LanConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class WifiDirectTransportAdapter @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val lanConnection: LanConnection
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.WIFI_DIRECT
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, "Nearby Wi-Fi permission is required")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        return runCatching {
            if (!wifiDirectManager.start()) {
                error("Wi-Fi Direct is unavailable or nearby permission is missing")
            }
            // We use the same generic socket server from LanConnection as Wi-Fi Direct provides a standard IP network.
            lanConnection.startServer(DIRECT_PORT, kind)
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Wi-Fi Direct Started")
        }.onFailure { error ->
            _state.value = TransportState(kind, TransportAvailability.ERROR, error.message ?: "Wi-Fi Direct failed")
        }
    }

    override suspend fun stop(): Result<Unit> {
        wifiDirectManager.stop()
        lanConnection.stopServer(DIRECT_PORT)
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(wifiDirectManager.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        val peer = wifiDirectManager.discoveredPeers.value.find { it.id == recipientId }
        return if (peer != null) Reachability.Reachable else Reachability.Unreachable("Peer not discovered")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val targetMac = envelope.recipientId
        
        // 1. Connect P2P group
        val connected = suspendCoroutine { cont ->
            wifiDirectManager.connect(targetMac) { success ->
                cont.resume(success)
            }
        }
        if (!connected) return SendResult.Failed(Exception("Failed to form Wi-Fi Direct group"))

        val info = wifiDirectManager.connectionInfo.value
        val goAddress = info?.groupOwnerAddress?.hostAddress 
            ?: return SendResult.Failed(Exception("Group owner IP not available"))

        // 2. Send payload over sockets
        val result = lanConnection.sendEnvelope(goAddress, DIRECT_PORT, envelope)
        return if (result.isSuccess) SendResult.Accepted(kind, "wifi-direct-frame")
        else SendResult.Failed(result.exceptionOrNull() ?: Exception("Socket send failed"))
    }

    private companion object { const val DIRECT_PORT = 40124 }
}
