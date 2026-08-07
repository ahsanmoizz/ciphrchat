package org.ciphrchat.app.messaging

import org.ciphrchat.app.crypto.SignalSessionManager
import org.ciphrchat.app.transport.OutboundEnvelope
import org.whispersystems.libsignal.SignalProtocolAddress
import javax.inject.Inject
import javax.inject.Singleton
import org.ciphrchat.app.transport.TransportManager
import kotlinx.coroutines.runBlocking

@Singleton
class AutomaticRouter @Inject constructor(
    private val signalSessionManager: SignalSessionManager,
    private val transportManager: TransportManager
) {
    fun encryptAndRoute(recipientTag: ByteArray, plaintext: ByteArray): OutboundEnvelope {
        val address = SignalProtocolAddress(String(recipientTag), 1)
        
        // Encrypt the payload using the Signal Double Ratchet session
        val ciphertextMessage = signalSessionManager.encryptMessage(address, plaintext)
        
        val envelope = OutboundEnvelope(
            messageId = java.util.UUID.randomUUID().toString().toByteArray(),
            recipientTag = recipientTag,
            encryptedPayload = ciphertextMessage.serialize(),
            hopLimit = 5
        )
        
        // Broadcast the envelope via the transport layer
        runBlocking {
            transportManager.send(envelope)
        }
        
        return envelope
    }
}
