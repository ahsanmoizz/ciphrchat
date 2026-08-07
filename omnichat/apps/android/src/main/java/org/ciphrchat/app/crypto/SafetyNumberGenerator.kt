package org.ciphrchat.app.crypto

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.fingerprint.Fingerprint
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyNumberGenerator @Inject constructor(
    private val store: SignalStoreAdapter
) {
    fun generateFingerprint(
        remoteIdentifier: String,
        remoteIdentityKey: IdentityKey
    ): Fingerprint {
        val generator = NumericFingerprintGenerator(5200)
        
        // In a real scenario, identifiers might be phone numbers or UUIDs.
        val localIdentifier = "local-device" // Should be dynamic
        val localIdentityKey = store.identityKeyPair.publicKey
        
        return generator.createFor(
            1,
            localIdentifier.toByteArray(),
            localIdentityKey,
            remoteIdentifier.toByteArray(),
            remoteIdentityKey
        )
    }

    fun getQrCodeData(fingerprint: Fingerprint): String {
        // Return raw binary representation or specialized URI for the QR code
        return "ciphrchat:fingerprint?v=${fingerprint.displayText}"
    }
}
