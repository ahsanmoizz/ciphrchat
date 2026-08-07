package org.ciphrchat.app.identity

import android.util.Base64
import org.ciphrchat.app.data.ContactEntity
import org.json.JSONObject
import org.whispersystems.libsignal.state.PreKeyBundle

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
        val json = JSONObject(raw)
        require(json.optString("format") == "ciphrchat-invitation") { "Not a CiphrChat invitation" }
        require(json.optInt("version") == VERSION) { "Unsupported invitation version" }
        return ContactEntity(
            contactId = json.getString("contactId"),
            displayName = json.getString("displayName").trim().also { require(it.isNotEmpty()) },
            peerId = json.getString("peerId"),
            relayAddress = json.getString("relayAddress"),
            registrationId = json.getInt("registrationId"),
            deviceId = json.getInt("deviceId"),
            preKeyId = json.getInt("preKeyId"),
            preKey = decodeB64(json.getString("preKey")),
            signedPreKeyId = json.getInt("signedPreKeyId"),
            signedPreKey = decodeB64(json.getString("signedPreKey")),
            signedPreKeySignature = decodeB64(json.getString("signedPreKeySignature")),
            identityKey = decodeB64(json.getString("identityKey")),
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
}
