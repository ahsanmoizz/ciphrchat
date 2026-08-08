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
            val identity = identityRepository.current() ?: error("Create your identity first")
            val peerId = if (relay.isNotBlank()) {
                runCatching {
                    p2p.startSwarm(relay).getOrThrow()
                    p2p.localPeerId() ?: error("Native peer identity is unavailable")
                }.getOrElse {
                    // Keep nearby pairing usable even if the optional native
                    // relay client is temporarily unavailable.
                    "local:${identity.publicId}"
                }
            } else {
                // Nearby transports do not need a libp2p relay. Keep QR/NFC
                // pairing usable for Bluetooth and local routes in a build
                // that was not compiled with a public relay address.
                "local:${identity.publicId}"
            }
            InvitationCodec.encode(identity, peerId, relay, sessions.generatePreKeyBundle())
        }
    }

    suspend fun importInvitation(raw: String): Result<ContactEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val contact = InvitationCodec.decode(raw)
            contacts.save(contact)
            if (contact.relayAddress.isNotBlank()) {
                p2p.connectPeer(contact.peerId, contact.relayAddress)
            }
            contact
        }
    }
}
