package org.ciphrchat.app.transport.internet

import org.ciphrchat.app.transport.OutboundEnvelope
import java.util.Base64

/** JSON envelope used only inside the authenticated libp2p request-response payload. */
object InternetWireCodec {
    fun encode(envelope: OutboundEnvelope): ByteArray = buildString {
        append('{')
        appendJsonString("protocolVersion").append(':').append(envelope.protocolVersion)
        append(',').appendJsonString("wireType").append(':').appendJsonString("message")
        append(',').appendJsonString("messageId").append(':').appendJsonString(envelope.messageId)
        append(',').appendJsonString("recipientId").append(':').appendJsonString(envelope.recipientId)
        append(',').appendJsonString("senderId").append(':').appendJsonString(envelope.senderId)
        append(',').appendJsonString("createdAtEpochMs").append(':').append(envelope.createdAtEpochMs)
        append(',').appendJsonString("expiresAtEpochMs").append(':').append(envelope.expiresAtEpochMs)
        append(',').appendJsonString("hopLimit").append(':').append(envelope.hopLimit)
        append(',').appendJsonString("testOnly").append(':').append(envelope.testOnly)
        append(',').appendJsonString("senderInvitation").append(':')
            .appendJsonString(envelope.senderInvitation)
        append(',').appendJsonString("encryptedPayload").append(':')
            .appendJsonString(Base64.getEncoder().encodeToString(envelope.encryptedPayload))
        append('}')
    }.encodeToByteArray()

    fun encodeDeliveryReceipt(
        messageId: String,
        senderId: String,
        recipientId: String
    ): ByteArray = buildString {
        append('{')
        appendJsonString("protocolVersion").append(':').append(2)
        append(',').appendJsonString("wireType").append(':').appendJsonString("deliveryReceipt")
        append(',').appendJsonString("messageId").append(':').appendJsonString(messageId)
        append(',').appendJsonString("senderId").append(':').appendJsonString(senderId)
        append(',').appendJsonString("recipientId").append(':').appendJsonString(recipientId)
        append('}')
    }.encodeToByteArray()

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        return append('"')
    }
}
