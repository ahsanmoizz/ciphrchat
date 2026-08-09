package org.ciphrchat.app.identity

import org.ciphrchat.app.data.ContactEntity

/** Security checks for learning the sender after a one-way QR scan. */
object ReciprocalPairingPolicy {
    fun validate(
        envelopeSenderId: String,
        authenticatedPeerId: String?,
        invited: ContactEntity
    ): ContactEntity {
        require(invited.contactId == envelopeSenderId) { "Reciprocal invitation sender mismatch" }
        if (authenticatedPeerId != null) {
            require(invited.peerId == authenticatedPeerId) { "Reciprocal invitation peer mismatch" }
        }
        return invited
    }
}
