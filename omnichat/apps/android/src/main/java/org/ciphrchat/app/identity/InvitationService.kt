package org.ciphrchat.app.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.BuildConfig
import org.ciphrchat.app.crypto.SignalSessionManager
import org.ciphrchat.app.data.ContactEntity
import org.ciphrchat.app.transport.internet.RustP2pManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitationService @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val contacts: ContactRepository,
    private val sessions: SignalSessionManager,
    private val p2p: RustP2pManager
) {
    suspend fun createInvitation(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val relay = BuildConfig.CIPHRCHAT_RELAY_ADDRESS.trim()
            require(relay.isNotBlank()) { "This build has no public relay configured" }
            val identity = identityRepository.current() ?: error("Create your identity first")
            p2p.startSwarm(relay).getOrThrow()
            val peerId = p2p.localPeerId() ?: error("Native peer identity is unavailable")
            InvitationCodec.encode(identity, peerId, relay, sessions.generatePreKeyBundle())
        }
    }

    suspend fun importInvitation(raw: String): Result<ContactEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val contact = InvitationCodec.decode(raw)
            require(contact.relayAddress.contains("/p2p/")) { "Invitation has no authenticated relay peer" }
            contacts.save(contact)
            p2p.connectPeer(contact.peerId, contact.relayAddress)
            contact
        }
    }
}
