package org.ciphrchat.app.transport.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ciphrchat.app.transport.DiscoveredPeer
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.Reachability
import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.transport.TransportAdapter
import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportCapability
import org.ciphrchat.app.transport.TransportInboundBus
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportState
import org.ciphrchat.app.transport.TransportWireCodec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiAwareTransportAdapter @Inject constructor(
    private val wifiAwareService: WifiAwareService,
    private val inboundBus: TransportInboundBus
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.WIFI_AWARE
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "Wi-Fi Aware is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    override suspend fun start(): Result<Unit> {
        if (serverSocket?.isClosed == false) {
            _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Secure Wi-Fi Aware messaging ready")
            return Result.success(Unit)
        }
        if (!wifiAwareService.start()) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi Aware is unavailable on this device")
            return Result.failure(IllegalStateException("Wi-Fi Aware unavailable"))
        }
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            _state.value = TransportState(kind, TransportAvailability.ERROR, it.message ?: "Unable to create Wi-Fi Aware listener")
            return Result.failure(it)
        }
        serverSocket = socket
        scope.launch { acceptLoop(socket) }
        if (!wifiAwareService.startPublisherNetwork(socket.localPort)) {
            socket.close()
            serverSocket = null
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "Wi-Fi Aware data paths are unavailable on this device")
            return Result.failure(IllegalStateException("Wi-Fi Aware data path unavailable"))
        }
        _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Secure Wi-Fi Aware messaging ready")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        serverSocket?.close()
        serverSocket = null
        wifiAwareService.stop()
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> =
        Result.success(wifiAwareService.discoveredPeers.value)

    override suspend fun canReach(recipientId: String): Reachability =
        if (wifiAwareService.hasPeer(recipientId)) Reachability.Reachable
        else Reachability.Unreachable("Peer not discovered over Wi-Fi Aware")

    override suspend fun send(envelope: OutboundEnvelope): SendResult = withContext(Dispatchers.IO) {
        val connection = wifiAwareService.requestNetwork(envelope.recipientId)
            ?: return@withContext SendResult.Failed(IllegalStateException("Wi-Fi Aware data path unavailable"))
        try {
            connection.network.socketFactory
                .createSocket(connection.peerAddress, connection.peerPort)
                .use { socket ->
                    DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { output ->
                        TransportWireCodec.write(output, envelope)
                        output.flush()
                    }
                }
            SendResult.Accepted(kind, "wifi-aware-frame")
        } catch (error: Exception) {
            SendResult.Failed(error)
        } finally {
            connection.close()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive && !socket.isClosed) {
            runCatching { socket.accept() }
                .onSuccess { client ->
                    scope.launch {
                        client.use {
                            runCatching {
                                val envelope = TransportWireCodec.read(
                                    DataInputStream(BufferedInputStream(it.getInputStream()))
                                )
                                inboundBus.publish(kind, envelope)
                            }
                        }
                    }
                }
                .onFailure { error ->
                    if (!socket.isClosed) {
                        _state.value = TransportState(kind, TransportAvailability.ERROR, error.message ?: "Wi-Fi Aware listener stopped")
                    }
                }
        }
    }
}
