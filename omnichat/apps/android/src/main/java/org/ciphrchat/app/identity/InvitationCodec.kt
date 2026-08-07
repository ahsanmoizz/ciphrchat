package org.ciphrchat.app.identity

import android.util.Base64
import org.ciphrchat.app.data.ContactEntity
import org.json.JSONObject
import org.whispersystems.libsignal.state.PreKeyBundle
import java.security.MessageDigest

/** Versioned, self-contained invitation exchanged out of band (QR, NFC, or text). */
object InvitationCodec {
    private const val VERSION = 1

    fun encode(
        identity: LocalIdentity,
        peerId: String,
        relayAddress: String,
        bundle: PreKeyBundle
    ): String = JSONObject()
        .put("format", "ciphrchat-invitation")
        .put("version", VERSION)
        .put("contactId", identity.publicId)
        .put("displayName", identity.displayName)
        .put("peerId", peerId)
        .put("relayAddress", relayAddress)
        .put("registrationId", bundle.registrationId)
        .put("deviceId", bundle.deviceId)
        .put("preKeyId", bundle.preKeyId)
        .put("preKey", b64(bundle.preKey.serialize()))
        .put("signedPreKeyId", bundle.signedPreKeyId)
        .put("signedPreKey", b64(bundle.signedPreKey.serialize()))
        .put("signedPreKeySignature", b64(bundle.signedPreKeySignature))
        .put("identityKey", b64(bundle.identityKey.serialize()))
        .toString()

    fun decode(raw: String): ContactEntity {
        require(raw.length <= MAX_INVITATION_BYTES) { "Invitation is too large" }
        val json = JSONObject(raw)
        require(json.optString("format") == "ciphrchat-invitation") { "Not a CiphrChat invitation" }
        require(json.optInt("version") == VERSION) { "Unsupported invitation version" }
        val contactId = json.getString("contactId")
        val displayName = json.getString("displayName").trim()
        val peerId = json.getString("peerId")
        val relayAddress = json.getString("relayAddress")
        val identityKey = decodeB64(json.getString("identityKey"))
        require(contactId == identityPublicId(identityKey)) { "Invitation identity binding is invalid" }
        require(displayName.length in 1..40) { "Invitation display name is invalid" }
        require(peerId.length in 8..200) { "Invitation peer identity is invalid" }
        require(relayAddress.length in 16..512 && relayAddress.contains("/p2p/")) {
            "Invitation relay address is invalid"
        }
        require(relayAddress.startsWith("/ip4/") || relayAddress.startsWith("/ip6/") || relayAddress.startsWith("/dns4/") || relayAddress.startsWith("/dns6/")) {
            "Invitation relay address must use a routable host"
        }
        require(relayAddress.contains("/tcp/") || relayAddress.contains("/udp/")) {
            "Invitation relay address has no transport port"
        }
        return ContactEntity(
            contactId = contactId,
            displayName = displayName,
            peerId = peerId,
            relayAddress = relayAddress,
            registrationId = json.getInt("registrationId"),
            deviceId = json.getInt("deviceId"),
            preKeyId = json.getInt("preKeyId"),
            preKey = decodeB64(json.getString("preKey")),
            signedPreKeyId = json.getInt("signedPreKeyId"),
            signedPreKey = decodeB64(json.getString("signedPreKey")),
            signedPreKeySignature = decodeB64(json.getString("signedPreKeySignature")),
            identityKey = identityKey,
            verified = false,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }

    fun toBundle(contact: ContactEntity): PreKeyBundle = PreKeyBundle(
        contact.registrationId,
        contact.deviceId,
        contact.preKeyId,
        org.whispersystems.libsignal.ecc.Curve.decodePoint(contact.preKey, 0),
        contact.signedPreKeyId,
        org.whispersystems.libsignal.ecc.Curve.decodePoint(contact.signedPreKey, 0),
        contact.signedPreKeySignature,
        org.whispersystems.libsignal.IdentityKey(contact.identityKey, 0)
    )

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeB64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private fun identityPublicId(identityKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identityKey)
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return "ciphr:$hex"
    }

    private const val MAX_INVITATION_BYTES = 32 * 1024
}
