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
        TransportState(TransportAvailability.EXPERIMENTAL, "Mesh Routing Ready")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var isBatteryLow = false
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun start(): Result<Unit> {
        checkBatteryState()
        
        if (isBatteryLow) {
            _state.value = TransportState(TransportAvailability.UNAVAILABLE, "Battery too low for mesh routing")
            return Result.failure(Exception("Low Battery"))
        }

        // In a real implementation, we'd start our own background listeners or hook into GattServerManager.
        // For the prototype phase, we verify that the adapter can be started if the battery allows.
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Mesh Routing Active")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(TransportAvailability.AVAILABLE, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Mesh relies on Bluetooth direct peers plus potentially multi-hop paths if we had a routing table
        return bluetoothTransportAdapter.discoverPeers()
    }

    override suspend fun canReach(recipientId: String): Reachability {
        // Mesh can attempt to reach anyone not directly reachable
        val directReach = bluetoothTransportAdapter.canReach(recipientId)
        return if (directReach == Reachability.DIRECT) Reachability.DIRECT else Reachability.MESH_PATH
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        checkBatteryState()
        if (isBatteryLow) {
            return SendResult.Failure(Exception("Battery too low to forward"))
        }

        val myId = identityRepository.current()?.publicId ?: return SendResult.Failure(Exception("No identity"))

        if (!meshRouter.shouldForward(envelope, myId)) {
            return SendResult.Failure(Exception("Envelope dropped by mesh router"))
        }

        val forwardedEnvelope = meshRouter.prepareForForwarding(envelope)
        
        // Use direct adapter to broadcast to neighbors
        // For prototype, we'd loop over discovered peers and send to all (flooding)
        // Here we just call send which defaults to first peer or specific peer
        return bluetoothTransportAdapter.send(forwardedEnvelope)
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
