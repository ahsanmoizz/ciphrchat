package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.nfc.NfcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val nfcManager = context.getSystemService(Context.NFC_SERVICE) as NfcManager?
    private val nfcAdapter = nfcManager?.defaultAdapter

    override suspend fun start(): Result<Unit> {
        if (nfcAdapter == null) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "NFC not supported")
            return Result.failure(Exception("NFC not available"))
        }

        if (!nfcAdapter.isEnabled) {
            _state.value = TransportState(kind, TransportAvailability.UNAVAILABLE, "NFC disabled")
            return Result.failure(Exception("NFC disabled"))
        }

        // In full implementation, we'd enable Reader Mode or Host Card Emulation
        _state.value = TransportState(kind, TransportAvailability.AVAILABLE, "Tap another CiphrChat phone to transfer encrypted messages")
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
        return if (coordinator.hasPendingTransfer()) Reachability.Reachable
        else Reachability.Unreachable("Tap phones together to open an NFC transfer session")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        return coordinator.send(envelope)
    }
}
