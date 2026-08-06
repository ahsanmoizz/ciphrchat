package org.ciphrchat.app.identity

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prototype identity repository for the Phase 1 scaffold.
 * NOT the final cryptographic identity implementation.
 * Does not use Android Keystore or persistent storage.
 */
@Singleton
class PrototypeIdentityRepository @Inject constructor() : IdentityRepository {
    private val random = SecureRandom()
    @Volatile private var identity: LocalIdentity? = null

    override suspend fun create(displayName: String): Result<LocalIdentity> = runCatching {
        require(displayName.trim().length in 1..40) { "Display name must be 1–40 characters" }
        val bytes = ByteArray(16).also(random::nextBytes)
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val fingerprint = hex.chunked(4).take(4).joinToString("-")
        LocalIdentity(
            displayName = displayName.trim(),
            publicId = "ciphr:$hex",
            fingerprint = fingerprint,
            createdAtEpochMs = System.currentTimeMillis()
        ).also { identity = it }
    }

    override suspend fun current(): LocalIdentity? = identity

    override suspend fun clear(): Result<Unit> = runCatching {
        identity = null
    }
}
