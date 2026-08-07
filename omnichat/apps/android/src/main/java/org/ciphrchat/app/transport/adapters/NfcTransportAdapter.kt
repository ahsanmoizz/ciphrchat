package org.ciphrchat.app.transport.adapters

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ciphrchat.app.transport.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcTransportAdapter @Inject constructor(
    private val context: Context
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.NFC_PAIRING
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.PAIRING,
        TransportCapability.SMALL_TEXT,
        TransportCapability.OFFLINE
    )

    private val _state = MutableStateFlow(
        TransportState(kind, TransportAvailability.EXPERIMENTAL, "NFC is reserved for explicit pairing")
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
        _state.value = TransportState(kind, TransportAvailability.EXPERIMENTAL, "NFC detected; explicit pairing flow is not a message route")
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
        return Reachability.Unreachable("NFC is reserved for explicit pairing")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        // Envelopes are staged for transmission on next tap
        return SendResult.Failure(Exception("NFC sends require physical tap event"))
    }
}
