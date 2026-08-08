package org.ciphrchat.app.transport.bluetooth

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.ciphrchat.app.identity.IdentityRepository
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BluetoothMeshTransportAdapter @Inject constructor(
    private val context: Context,
    private val bluetoothTransportAdapter: BluetoothTransportAdapter,
    private val meshRouter: MeshRouter,
    private val identityRepository: IdentityRepository,
    private val inboundBus: TransportInboundBus
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.BLUETOOTH_MESH
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.MULTI_HOP,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "Bluetooth mesh is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var forwardingJob: Job? = null
    private val forwardingMutex = Mutex()

    override suspend fun start(): Result<Unit> {
        checkBatteryState()
        if (isBatteryLow) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "Mesh routing paused to protect battery")
            return Result.failure(IllegalStateException("Battery too low for Bluetooth mesh"))
        }
        val direct = bluetoothTransportAdapter.start()
        if (direct.isFailure) {
            _state.value = TransportState(kind, TransportAvailability.PERMISSION_REQUIRED, "Bluetooth access is required for mesh routing")
            return direct
        }
        if (!started) {
            started = true
            forwardingJob = scope.launch {
                inboundBus.events.collect { event ->
                    if (!started) return@collect
                    val localId = identityRepository.current()?.publicId ?: return@collect
                    if (event.transport != TransportKind.BLUETOOTH_DIRECT && event.transport != TransportKind.BLUETOOTH_MESH) return@collect
                    val envelope = event.envelope
                    if (!meshRouter.shouldForward(envelope, localId)) return@collect
                    forwardingMutex.withLock {
                        bluetoothTransportAdapter.broadcastEnvelope(meshRouter.prepareForForwarding(envelope))
                    }
                }
            }
        }
        _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Authenticated Bluetooth mesh forwarding ready")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        started = false
        forwardingJob?.cancel()
        forwardingJob = null
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> =
        bluetoothTransportAdapter.discoverPeers()

    override suspend fun canReach(recipientId: String): Reachability =
        if (bluetoothTransportAdapter.hasDiscoveredPeers()) Reachability.Reachable
        else Reachability.Unreachable("No Bluetooth mesh neighbors discovered")

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        meshRouter.markOrigin(envelope)
        return bluetoothTransportAdapter.broadcastEnvelope(envelope)
    }

    private var isBatteryLow = false

    private fun checkBatteryState() {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        isBatteryLow = level >= 0 && scale > 0 && level * 100f / scale < 15f
    }
}
