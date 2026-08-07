package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.transport.OutboundEnvelope
import org.json.JSONObject
import java.util.Base64

/** JSON envelope used only inside the authenticated libp2p request-response payload. */
object InternetWireCodec {
    fun encode(envelope: OutboundEnvelope): ByteArray = JSONObject()
        .put("protocolVersion", envelope.protocolVersion)
        .put("messageId", envelope.messageId)
        .put("recipientId", envelope.recipientId)
        .put("senderId", envelope.senderId)
        .put("createdAtEpochMs", envelope.createdAtEpochMs)
        .put("expiresAtEpochMs", envelope.expiresAtEpochMs)
        .put("hopLimit", envelope.hopLimit)
        .put("testOnly", envelope.testOnly)
        .put("encryptedPayload", Base64.getEncoder().encodeToString(envelope.encryptedPayload))
        .toString()
        .encodeToByteArray()
}
