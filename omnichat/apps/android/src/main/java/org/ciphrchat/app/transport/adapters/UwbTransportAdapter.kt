package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import androidx.core.uwb.UwbClientSessionScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.bluetooth.BluetoothTransportAdapter
import org.ciphrchat.app.transport.bluetooth.GattServerManager
import org.ciphrchat.app.transport.uwb.UwbOobFrameCodec
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** UWB is used for authenticated proximity ranging; BLE remains the message bearer. */
@Singleton
class UwbTransportAdapter @Inject constructor(
    private val context: Context,
    private val bluetooth: BluetoothTransportAdapter,
    private val gattServer: GattServerManager
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.UWB_ASSIST
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.PAIRING,
        TransportCapability.RANGING,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "UWB proximity delivery is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handshakeMutex = Mutex()
    private val random = SecureRandom()
    private var manager: UwbManager? = null
    private var controlJob: Job? = null
    private var started = false
    private val sessions = mutableMapOf<Int, Session>()
    private val verifiedPeers = mutableMapOf<String, Long>()

    private data class Session(
        val mac: String,
        val key: ByteArray,
        val localAddress: ByteArray,
        val channel: Int,
        val preamble: Int,
        val scope: UwbClientSessionScope,
        var peerAddress: ByteArray? = null,
        var rangingJob: Job? = null
    )

    override suspend fun start(): Result<Unit> {
        if (started) return Result.success(Unit)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "UWB requires Android 12 or newer")
            return Result.failure(IllegalStateException("UWB API is unavailable"))
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)) {
            _state.value = TransportState(
                kind,
                TransportAvailability.UNAVAILABLE,
                "No UWB radio reported by this phone; Android version alone does not add UWB"
            )
            return Result.failure(IllegalStateException("UWB hardware is unavailable"))
        }
        return runCatching {
            val instance = UwbManager.createInstance(context)
            check(instance.isAvailable()) { "UWB is turned off" }
            manager = instance
            started = true
            controlJob?.cancel()
            controlJob = scope.launch {
                gattServer.incomingControl.collect { incoming -> handleControl(incoming.deviceAddress, incoming.payload) }
            }
            _state.value = TransportState(kind, TransportAvailability.STARTING, "UWB hardware ready; waiting to verify a nearby CiphrChat peer, with BLE carrying message bytes")
        }.onFailure {
            started = false
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, it.message ?: "UWB service unavailable")
        }
    }

    override suspend fun stop(): Result<Unit> {
        started = false
        controlJob?.cancel()
        controlJob = null
        sessions.values.forEach { it.rangingJob?.cancel() }
        sessions.clear()
        verifiedPeers.clear()
        manager = null
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> =
        Result.success(emptyList())

    override suspend fun canReach(recipientId: String): Reachability {
        val mac = bluetooth.deviceAddressForRecipient(recipientId)
            ?: return Reachability.Unreachable("Peer is not discovered over Bluetooth")
        if (verifiedPeers[mac]?.let { System.currentTimeMillis() - it < 30_000L } == true) return Reachability.Reachable
        val verified = withTimeoutOrNull(5_000L) { establishSession(mac) } == true
        return if (verified) Reachability.Reachable
        else Reachability.Unreachable("UWB ranging session was not established; keep the phones within 2.5 metres")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        val mac = bluetooth.deviceAddressForRecipient(envelope.recipientId)
            ?: return SendResult.Rejected("Peer is not discovered over Bluetooth")
        if (verifiedPeers[mac]?.let { System.currentTimeMillis() - it < 30_000L } != true) {
            return SendResult.Rejected("UWB proximity has not been verified")
        }
        return bluetooth.sendToDevice(mac, envelope)
    }

    private suspend fun establishSession(mac: String): Boolean = handshakeMutex.withLock {
        if (verifiedPeers[mac]?.let { System.currentTimeMillis() - it < 30_000L } == true) return@withLock true
        val uwb = manager ?: return@withLock false
        val sessionScope = runCatching { uwb.controllerSessionScope() }.getOrNull() ?: return@withLock false
        val sessionId = random.nextInt().let { if (it == 0) 1 else it }
        val key = ByteArray(16).also(random::nextBytes)
        val address = sessionScope.localAddress.address
        val channel = sessionScope.uwbComplexChannel
        sessions[sessionId] = Session(mac, key, address, channel.channel, channel.preambleIndex, sessionScope)
        val sent = bluetooth.sendControlToDevice(
            mac,
            UwbOobFrameCodec.encode(UwbOobFrameCodec.Frame(UwbOobFrameCodec.HELLO, sessionId, channel.channel, channel.preambleIndex, address, null, key))
        )
        if (sent !is SendResult.Accepted) {
            sessions.remove(sessionId)
            return@withLock false
        }
        repeat(50) {
            if (verifiedPeers[mac]?.let { System.currentTimeMillis() - it < 30_000L } == true) return@withLock true
            delay(100)
        }
        false
    }

    private suspend fun handleControl(mac: String, bytes: ByteArray) {
        val frame = UwbOobFrameCodec.decode(bytes) ?: return
        when (frame.type) {
            UwbOobFrameCodec.HELLO -> acceptHello(mac, frame)
            UwbOobFrameCodec.READY -> acceptReady(mac, frame)
        }
    }

    private suspend fun acceptHello(mac: String, frame: UwbOobFrameCodec.Frame) {
        val uwb = manager ?: return
        val controlee = runCatching { uwb.controleeSessionScope() }.getOrNull() ?: return
        val local = controlee.localAddress.address
        val session = Session(mac, frame.sessionKey, local, frame.channel, frame.preambleIndex, controlee, frame.address)
        sessions[frame.sessionId] = session
        val ready = UwbOobFrameCodec.Frame(UwbOobFrameCodec.READY, frame.sessionId, frame.channel, frame.preambleIndex, local, frame.address, frame.sessionKey)
        if (bluetooth.sendControlToDevice(mac, UwbOobFrameCodec.encode(ready)) is SendResult.Accepted) {
            startRanging(frame.sessionId, session, peerIsController = true)
        }
    }

    private suspend fun acceptReady(mac: String, frame: UwbOobFrameCodec.Frame) {
        val session = sessions[frame.sessionId] ?: return
        if (session.mac != mac || frame.peerAddress?.contentEquals(session.localAddress) != true) return
        session.peerAddress = frame.address
        startRanging(frame.sessionId, session, peerIsController = false)
    }

    private fun startRanging(sessionId: Int, session: Session, peerIsController: Boolean) {
        if (session.rangingJob != null || session.peerAddress == null) return
        val peer = UwbDevice.createForAddress(session.peerAddress!!)
        val parameters = RangingParameters(
            RangingParameters.CONFIG_PROVISIONED_UNICAST_DS_TWR,
            sessionId,
            0,
            session.key,
            null,
            UwbComplexChannel(session.channel, session.preamble),
            listOf(peer),
            RangingParameters.RANGING_UPDATE_RATE_FREQUENT,
            null,
            RangingParameters.RANGING_SLOT_DURATION_2_MILLIS,
            true
        )
        session.rangingJob = scope.launch {
            runCatching {
                session.scope.prepareSession(parameters).collect { result ->
                    if (result is RangingResult.RangingResultPosition) {
                        val distance = result.position.distance?.value
                        if (distance != null) {
                            val formatted = String.format(Locale.US, "%.2f", distance)
                            if (distance <= 2.5f) {
                                verifiedPeers[session.mac] = System.currentTimeMillis()
                                _state.value = TransportState(
                                    kind,
                                    TransportAvailability.AVAILABLE,
                                    "UWB measured $formatted m • proximity verified; BLE carries the encrypted message"
                                )
                            } else {
                                verifiedPeers.remove(session.mac)
                                _state.value = TransportState(
                                    kind,
                                    TransportAvailability.STARTING,
                                    "UWB measured $formatted m • move within 2.5 m to verify proximity"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
