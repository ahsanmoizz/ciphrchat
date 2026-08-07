package org.ciphrchat.app.transport.internet

import android.util.Base64
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.transport.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetTransportAdapter @Inject constructor(
    private val rustP2pManager: RustP2pManager,
    private val contacts: ContactRepository
) : TransportAdapter {
    override val kind: TransportKind = TransportKind.INTERNET_DIRECT
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )

    private val _state = MutableStateFlow(
        if (BuildConfig.CIPHRCHAT_RELAY_ADDRESS.isBlank()) {
            TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.UNAVAILABLE, "Relay address is not configured")
        } else {
            TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.STARTING, "Relay client is not started")
        }
    )
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var started = false

    override suspend fun start(): Result<Unit> {
        if (BuildConfig.CIPHRCHAT_RELAY_ADDRESS.isBlank()) {
            val error = IllegalStateException("No public relay is configured for this build")
            _state.value = TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.UNAVAILABLE, error.message!!)
            return Result.failure(error)
        }
        val result = rustP2pManager.startSwarm()
        result.onSuccess {
            started = true
            _state.value = TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.AVAILABLE, "Rust libp2p relay client running")
        }.onFailure { error ->
            _state.value = TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.ERROR, error.message ?: "Unable to start relay client")
        }
        return result
    }

    override suspend fun stop(): Result<Unit> {
        started = false
        _state.value = TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.DISABLED_BY_USER, "Internet transport stopped")
        return Result.success(Unit)
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> {
        // In a full implementation, we'd query the Rust FFI for peers discovered via Kademlia
        return Result.success(emptyList())
    }

    override suspend fun canReach(recipientId: String): Reachability {
        if (!started) return Reachability.Unreachable("Internet relay client is not running")
        return if (contacts.find(recipientId) != null) Reachability.Reachable
        else Reachability.Unreachable("Contact has no Internet invitation")
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (!started) {
            return SendResult.Failed(IllegalStateException("Internet relay client is not running"))
        }
        if (envelope.testOnly) {
            return SendResult.Failed(IllegalStateException("Refusing to send a test-only envelope over the Internet"))
        }
        val contact = contacts.find(envelope.recipientId)
            ?: return SendResult.Rejected("Contact has not been paired")
        if (!rustP2pManager.connectPeer(contact.peerId, contact.relayAddress)) {
            return SendResult.Failed(IllegalStateException("Peer address was rejected by the native network"))
        }
        val payload = envelope.toWirePayload()
        return if (rustP2pManager.sendMessage(contact.peerId, envelope.messageId, payload)) {
            SendResult.Accepted(kind, "native-request-queued")
        } else {
            SendResult.Failed(IllegalStateException("Peer address is not registered or native transport is stopped"))
        }
    }

    private fun OutboundEnvelope.toWirePayload(): ByteArray = JSONObject()
        .put("protocolVersion", protocolVersion)
        .put("messageId", messageId)
        .put("senderId", senderId)
        .put("createdAtEpochMs", createdAtEpochMs)
        .put("expiresAtEpochMs", expiresAtEpochMs)
        .put("hopLimit", hopLimit)
        .put("encryptedPayload", Base64.encodeToString(encryptedPayload, Base64.NO_WRAP))
        .toString()
        .encodeToByteArray()
}
