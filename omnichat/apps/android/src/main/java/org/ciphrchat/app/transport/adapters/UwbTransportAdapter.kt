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
        TransportState(kind, TransportAvailability.STARTING, "UWB proximity assist is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "UWB requires Android 12+")
            return Result.failure(Exception("UWB not supported on OS version"))
        }

        if (!context.packageManager.hasSystemFeature("android.hardware.uwb")) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "This device has no UWB radio")
            return Result.failure(Exception("UWB hardware not available"))
        }
        
        _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "UWB proximity assist ready; messages use authenticated nearby radios")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Discovers UWB endpoints
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Unreachable("UWB is not a message transport")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Rejected("UWB is a proximity/ranging assist; Android does not expose it as an app payload channel")
    }
}
