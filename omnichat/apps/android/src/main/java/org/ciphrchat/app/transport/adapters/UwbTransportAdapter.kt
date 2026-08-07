package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UwbTransportAdapter @Inject constructor(
    private val context: Context
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.UWB_ASSIST
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.PAIRING,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(TransportAvailability.EXPERIMENTAL, "UWB PoC Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        // Requires SDK 31 for core UWB manager, but we mock for PoC
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = TransportState(TransportAvailability.UNAVAILABLE, "UWB requires Android 12+")
            return Result.failure(Exception("UWB not supported on OS version"))
        }
        
        _state.value = TransportState(TransportAvailability.AVAILABLE, "UWB ranging ready")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Discovers UWB endpoints
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.UNREACHABLE
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Failure(Exception("UWB used for ranging, not full payloads yet"))
    }
}
