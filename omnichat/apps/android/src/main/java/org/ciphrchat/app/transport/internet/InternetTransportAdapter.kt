package org.ciphrchat.app.transport.internet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetTransportAdapter @Inject constructor(
    private val rustP2pManager: RustP2pManager
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.INTERNET_DIRECT
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )

    private val _state = MutableStateFlow(
        TransportState(TransportAvailability.AVAILABLE, "Internet P2P Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        return runCatching {
            rustP2pManager.startSwarm()
            _state.value = TransportState(TransportAvailability.AVAILABLE, "Rust libp2p swarm running")
        }
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // In a full implementation, we'd query the Rust FFI for peers discovered via Kademlia
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        // Query Rust FFI to see if peer is in routing table
        return Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Failure(Exception("Internet send via Rust JNI not fully wired yet"))
    }
}
