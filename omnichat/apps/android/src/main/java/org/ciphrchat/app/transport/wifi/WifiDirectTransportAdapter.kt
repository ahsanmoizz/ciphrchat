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
        TransportState(TransportAvailability.AVAILABLE, "Wi-Fi Direct Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        return runCatching {
            wifiDirectManager.start()
            // We use the same generic socket server from LanConnection as Wi-Fi Direct provides a standard IP network.
            lanConnection.startServer(12346) 
            _state.value = TransportState(TransportAvailability.AVAILABLE, "Wi-Fi Direct Started")
        }
    }

    override suspend fun stop(): Result<Unit> {
        wifiDirectManager.stop()
        lanConnection.stopServer()
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(wifiDirectManager.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        // Recipient ID maps to P2P MAC address in this prototype phase
        val peer = wifiDirectManager.discoveredPeers.value.find { it.id == recipientId }
        return if (peer != null) Reachability.DIRECT else Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val targetMac = String(envelope.recipientTag)
        
        // 1. Connect P2P group
        val connected = suspendCoroutine { cont ->
            wifiDirectManager.connect(targetMac) { success ->
                cont.resume(success)
            }
        }
        if (!connected) return SendResult.Failure(Exception("Failed to form Wi-Fi Direct group"))

        // Wait a brief moment for group owner IP to be resolved (omitted robust retry loop for prototype)
        val info = wifiDirectManager.connectionInfo.value
        val goAddress = info?.groupOwnerAddress?.hostAddress 
            ?: return SendResult.Failure(Exception("Group owner IP not available"))

        // 2. Send payload over sockets
        val result = lanConnection.sendEnvelope(goAddress, 12346, envelope)
        return if (result.isSuccess) SendResult.Success else SendResult.Failure(result.exceptionOrNull() ?: Exception("Socket send failed"))
    }
}
