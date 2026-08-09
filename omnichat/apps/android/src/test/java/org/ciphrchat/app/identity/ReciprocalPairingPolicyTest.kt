package org.ciphrchat.app.identity

import org.ciphrchat.app.data.ContactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReciprocalPairingPolicyTest {
    @Test
    fun authenticatedPeerAndSenderAreAccepted() {
        val contact = contact()

        assertEquals(contact, ReciprocalPairingPolicy.validate("ciphr:alice", "peer-alice", contact))
    }

    @Test
    fun envelopeCannotSubstituteAnotherSender() {
        assertThrows(IllegalArgumentException::class.java) {
            ReciprocalPairingPolicy.validate("ciphr:mallory", "peer-alice", contact())
        }
    }

    @Test
    fun internetPeerMustMatchAuthenticatedLibp2pPeer() {
        assertThrows(IllegalArgumentException::class.java) {
            ReciprocalPairingPolicy.validate("ciphr:alice", "peer-mallory", contact())
        }
    }

    private fun contact() = ContactEntity(
        contactId = "ciphr:alice",
        displayName = "Alice",
        peerId = "peer-alice",
        relayAddress = "/ip4/127.0.0.1/tcp/4001/p2p/relay",
        registrationId = 1,
        deviceId = 1,
        preKeyId = 1,
        preKey = byteArrayOf(1),
        signedPreKeyId = 1,
        signedPreKey = byteArrayOf(2),
        signedPreKeySignature = byteArrayOf(3),
        identityKey = byteArrayOf(4),
        verified = false,
        createdAtEpochMs = 1L
    )
}
