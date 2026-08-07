package org.ciphrchat.app.transport.bluetooth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Stable, non-secret BLE lookup token for an already exchanged contact identity. */
object ContactDiscoveryToken {
    fun forContactId(contactId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(contactId.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(TOKEN_BYTES))
    }

    private const val TOKEN_BYTES = 12
}
