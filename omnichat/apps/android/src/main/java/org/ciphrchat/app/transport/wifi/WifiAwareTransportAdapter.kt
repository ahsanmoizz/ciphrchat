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
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.aware.WifiAwareNetworkInfo

@Singleton
class WifiAwareTransportAdapter @Inject constructor(
    private val context: Context,
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
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return@withContext SendResult.Failure(Exception("Wi-Fi Aware networking requires Android 10 or newer"))
        }

        val targetId = String(envelope.recipientTag)
        val network = wifiAwareService.requestNetwork(targetId) 
            ?: return@withContext SendResult.Failure(Exception("Failed to form Wi-Fi Aware network"))
        
        runCatching {
            val socket = network.socketFactory.createSocket()
            
            // Extract the IP/Port dynamically from the WifiAwareNetworkInfo
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val awareInfo = capabilities?.transportInfo as? WifiAwareNetworkInfo
            
            val targetIpv6 = awareInfo?.peerIpv6Addr ?: Inet6Address.getByName("fe80::1") // Fallback if API fails
            val targetPort = awareInfo?.port?.takeIf { it > 0 } ?: 12347

            socket.connect(java.net.InetSocketAddress(targetIpv6, targetPort))
            
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
