package org.ciphrchat.app.transport.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.ciphrchat.app.transport.*
import java.io.DataOutputStream
import java.net.Inet6Address
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Singleton
class WifiAwareTransportAdapter @Inject constructor(
    private val wifiAwareService: WifiAwareService
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.WIFI_AWARE
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(TransportAvailability.AVAILABLE, "Wi-Fi Aware Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(): Result<Unit> {
        val started = wifiAwareService.start()
        if (started) {
            _state.value = TransportState(TransportAvailability.AVAILABLE, "Wi-Fi Aware Started")
            
            // Wait for a network request to provide a ServerSocket on Aware network
            scope.launch {
                runCatching {
                    serverSocket = ServerSocket(12347)
                }
            }
            return Result.success(Unit)
        } else {
            _state.value = TransportState(TransportAvailability.UNAVAILABLE, "Not supported on this device")
            return Result.failure(Exception("Not supported"))
        }
    }

    override suspend fun stop(): Result<Unit> {
        wifiAwareService.stop()
        serverSocket?.close()
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(wifiAwareService.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        val peer = wifiAwareService.discoveredPeers.value.find { it.id == recipientId }
        return if (peer != null) Reachability.DIRECT else Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult = withContext(Dispatchers.IO) {
        val targetId = String(envelope.recipientTag)
        val network = wifiAwareService.requestNetwork(targetId) 
            ?: return@withContext SendResult.Failure(Exception("Failed to form Wi-Fi Aware network"))
        
        runCatching {
            val socket = network.socketFactory.createSocket()
            // Note: In a real Aware implementation, IPv6 LLA is discovered out of band via Aware messaging. 
            // For this phase, we mock the IP to just verify the network pipeline compiles and runs.
            val mockIpv6 = Inet6Address.getByName("fe80::1")
            socket.connect(java.net.InetSocketAddress(mockIpv6, 12347))
            
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(1)
            out.writeInt(envelope.encryptedPayload.size)
            out.write(envelope.encryptedPayload)
            out.flush()
            socket.close()
        }.fold(
            onSuccess = { SendResult.Success },
            onFailure = { SendResult.Failure(it) }
        )
    }
}
