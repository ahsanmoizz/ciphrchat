package org.ciphrchat.app.transport

import kotlinx.coroutines.flow.StateFlow

interface TransportAdapter {
    val kind: TransportKind
    val capabilities: Set<TransportCapability>
    val state: StateFlow<TransportState>

    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun discoverPeers(): Result<List<DiscoveredPeer>>
    suspend fun canReach(recipientId: String): Reachability
    suspend fun send(envelope: OutboundEnvelope): SendResult
}
