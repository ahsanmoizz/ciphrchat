package org.ciphrchat.app.messaging

import org.json.JSONObject
import java.util.Base64

/**
 * Versioned plaintext content format. The complete result is encrypted by
 * Signal before it reaches any transport, so MIME names and file bytes are
 * never exposed to relays or nearby adapters.
 */
object MessageContentCodec {
    private const val VERSION = 1

    data class Decoded(
        val text: String? = null,
        val attachment: Attachment? = null
    )

    data class Attachment(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    fun encodeText(text: String): ByteArray = JSONObject()
        .put("version", VERSION)
        .put("kind", "text")
        .put("text", text)
        .toString()
        .toByteArray(Charsets.UTF_8)

    fun encodeAttachment(fileName: String, mimeType: String, bytes: ByteArray): ByteArray = JSONObject()
        .put("version", VERSION)
        .put("kind", "attachment")
        .put("fileName", fileName)
        .put("mimeType", mimeType)
        .put("data", Base64.getEncoder().encodeToString(bytes))
        .toString()
        .toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Decoded {
        val raw = bytes.toString(Charsets.UTF_8)
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return Decoded(text = raw)
        if (json.optInt("version", 0) != VERSION) return Decoded(text = raw)
        return when (json.optString("kind")) {
            "text" -> Decoded(text = json.optString("text"))
            "attachment" -> {
                val fileName = json.optString("fileName").ifBlank { "attachment" }
                val mimeType = json.optString("mimeType").ifBlank { "application/octet-stream" }
                val data = runCatching { Base64.getDecoder().decode(json.getString("data")) }.getOrNull()
                    ?: throw IllegalArgumentException("Attachment data is invalid")
                Decoded(attachment = Attachment(fileName, mimeType, data))
            }
            else -> Decoded(text = raw)
        }
    }
}
