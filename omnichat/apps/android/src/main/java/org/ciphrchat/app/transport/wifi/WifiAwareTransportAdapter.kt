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
        TransportState(kind, TransportAvailability.EXPERIMENTAL, "Wi-Fi Aware discovery is available; authenticated message framing is not enabled")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(): Result<Unit> {
        val started = wifiAwareService.start()
        if (started) {
            _state.value = TransportState(kind, TransportAvailability.EXPERIMENTAL, "Wi-Fi Aware started; message routing is disabled until inbound framing is enabled")
            
            // Wait for a network request to provide a ServerSocket on Aware network
            scope.launch {
                runCatching {
                    serverSocket = ServerSocket(12347)
                }
            }
            return Result.success(Unit)
        } else {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "Not supported on this device")
            return Result.failure(Exception("Not supported"))
        }
    }

    override suspend fun stop(): Result<Unit> {
        wifiAwareService.stop()
        serverSocket?.close()
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        return Result.success(wifiAwareService.discoveredPeers.value)
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Unreachable("Wi-Fi Aware message routing is not enabled in this build")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult = withContext(Dispatchers.IO) {
        SendResult.Rejected("Wi-Fi Aware message routing is not enabled in this build")
    }
}
