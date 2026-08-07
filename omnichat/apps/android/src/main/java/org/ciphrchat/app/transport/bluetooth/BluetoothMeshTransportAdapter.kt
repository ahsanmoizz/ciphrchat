package org.ciphrchat.app.transport.bluetooth

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class BluetoothMeshTransportAdapter @Inject constructor(
    private val context: Context,
    private val bluetoothTransportAdapter: BluetoothTransportAdapter, // re-using direct adapter to send
    private val meshRouter: MeshRouter,
    private val identityRepository: IdentityRepository
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.BLUETOOTH_MESH
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.MULTI_HOP,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.EXPERIMENTAL, "Mesh forwarding requires authenticated neighbor routing")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var isBatteryLow = false
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(): Result<Unit> {
        checkBatteryState()
        
        if (isBatteryLow) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "Battery too low for mesh routing")
            return Result.failure(Exception("Low Battery"))
        }

        _state.value = TransportState(kind, TransportAvailability.EXPERIMENTAL, "Bluetooth direct messaging is available; mesh forwarding is disabled")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Mesh relies on Bluetooth direct peers plus potentially multi-hop paths if we had a routing table
        return bluetoothTransportAdapter.discoverPeers()
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Unreachable("Bluetooth mesh forwarding is disabled until neighbor routing is authenticated")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return SendResult.Rejected("Bluetooth mesh forwarding is disabled until neighbor routing is authenticated")
    }

    private fun checkBatteryState() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        if (level != -1 && scale != -1) {
            val batteryPct = level * 100 / scale.toFloat()
            isBatteryLow = batteryPct < 15.0f // Disable mesh routing below 15%
        }
    }
}
