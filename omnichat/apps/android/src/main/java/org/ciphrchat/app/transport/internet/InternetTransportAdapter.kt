package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.transport.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.ciphrchat.app.worker.PendingMessageRetryScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetTransportAdapter @Inject constructor(
    private val rustP2pManager: RustP2pManager,
    private val contacts: ContactRepository,
    private val retryScheduler: PendingMessageRetryScheduler
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            combine(
                rustP2pManager.relayReservationReady,
                rustP2pManager.mailboxReady
            ) { relayReady, mailboxReady -> relayReady to mailboxReady }
                .collect { (relayReady, mailboxReady) ->
                if (mailboxReady) {
                    started = true
                    _state.value = TransportState(
                        kind,
                        TransportAvailability.AVAILABLE,
                        "Internet ready • encrypted offline delivery active"
                    )
                    retryScheduler.scheduleNow()
                } else if (relayReady) {
                    _state.value = TransportState(
                        kind,
                        TransportAvailability.STARTING,
                        "Relay connected • verifying encrypted offline delivery"
                    )
                } else if (started) {
                    _state.value = TransportState(
                        kind,
                        TransportAvailability.STARTING,
                        "Connecting to the secure Internet relay"
                    )
                }
            }
        }
        scope.launch {
            rustP2pManager.events.collect { event ->
                if (event is RustNetworkEvent.MailboxUnavailable) {
                    _state.value = TransportState(
                        kind,
                        TransportAvailability.ERROR,
                        event.detail
                    )
                }
            }
        }
    }

    override suspend fun start(): Result<Unit> {
        if (started) return Result.success(Unit)
        if (BuildConfig.CIPHRCHAT_RELAY_ADDRESS.isBlank()) {
            val error = IllegalStateException("No public relay is configured for this build")
            _state.value = TransportState(TransportKind.INTERNET_DIRECT, TransportAvailability.UNAVAILABLE, error.message!!)
            return Result.failure(error)
        }
        val result = rustP2pManager.startSwarm()
        result.onSuccess {
            started = true
            _state.value = if (rustP2pManager.mailboxReady.value) {
                TransportState(kind, TransportAvailability.AVAILABLE, "Internet ready • encrypted offline delivery active")
            } else {
                TransportState(kind, TransportAvailability.STARTING, "Connecting and verifying encrypted offline delivery")
            }
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
        if (!started) {
            val startResult = start()
            if (startResult.isFailure) {
                return Reachability.Unreachable(
                    "Internet relay client could not start: ${startResult.exceptionOrNull()?.message}"
                )
            }
        }
        val contact = contacts.find(recipientId)
            ?: return Reachability.Unreachable("Contact has no Internet invitation")
        return if (!rustP2pManager.mailboxReady.value) {
            Reachability.Unreachable("Encrypted offline delivery is still being verified")
        } else if (contact.relayAddress.isNotBlank() && !contact.peerId.startsWith("local:")) {
            Reachability.Reachable
        } else {
            Reachability.Unreachable("Contact invitation has no Internet peer address")
        }
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (!started) {
            return SendResult.Failed(IllegalStateException("Internet relay client is not running"))
        }
        if (!rustP2pManager.mailboxReady.value) {
            return SendResult.Rejected("Encrypted Internet delivery is still connecting")
        }
        if (envelope.testOnly) {
            return SendResult.Failed(IllegalStateException("Refusing to send a test-only envelope over the Internet"))
        }
        val contact = contacts.find(envelope.recipientId)
            ?: return SendResult.Rejected("Contact has not been paired")
        if (contact.relayAddress.isBlank() || contact.peerId.startsWith("local:")) {
            return SendResult.Rejected("Contact invitation does not contain an Internet route")
        }
        // A live relay circuit can deliver immediately when both peers are online. The
        // encrypted mailbox remains the reliable path when that optional circuit is absent.
        rustP2pManager.connectPeer(contact.peerId, contact.relayAddress)
        val payload = InternetWireCodec.encode(envelope)
        return rustP2pManager.sendMessageAwaitingDelivery(
            contact.peerId,
            envelope.messageId,
            payload,
            envelope.expiresAtEpochMs
        ).fold(
            onSuccess = { SendResult.Accepted(kind, "encrypted-internet-delivery") },
            onFailure = { SendResult.Failed(it) }
        )
    }

}
