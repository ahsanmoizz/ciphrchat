package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.ciphrchat.app.transport.*
import org.ciphrchat.app.transport.nfc.NfcTransportCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcTransportAdapter @Inject constructor(
    private val context: Context,
    private val coordinator: NfcTransportCoordinator
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.NFC_PAIRING
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.PAIRING,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.STARTING, "NFC tap transfer is not started")
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val nfcManager = context.getSystemService(Context.NFC_SERVICE) as NfcManager?
    private val nfcAdapter = nfcManager?.defaultAdapter

    init {
        scope.launch {
            coordinator.verifiedPeer.collect { verified ->
                if (verified) {
                    _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "NFC message transfer verified with another CiphrChat phone")
                }
            }
        }
    }

    override suspend fun start(): Result<Unit> {
        if (nfcAdapter == null) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "NFC not supported")
            return Result.failure(Exception("NFC not available"))
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "NFC messaging requires host card emulation")
            return Result.failure(Exception("NFC host card emulation is unavailable"))
        }

        if (!nfcAdapter.isEnabled) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "NFC disabled")
            return Result.failure(Exception("NFC disabled"))
        }

        _state.value = if (coordinator.verifiedPeer.value) {
            TransportState(kind, TransportAvailability.AVAILABLE, "NFC message transfer verified with another CiphrChat phone")
        } else {
            TransportState(kind, TransportAvailability.STARTING, "NFC ready; keep both phones unlocked and CiphrChat open, then hold them together")
        }
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        _state.value = TransportState(kind, TransportAvailability.DISABLED_BY_USER, "Stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // Handled out-of-band via intent when tag is tapped
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        return Reachability.Reachable
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return coordinator.send(envelope)
    }
}
