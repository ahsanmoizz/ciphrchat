package org.ciphrchat.app.identity

data class LocalIdentity(
    val displayName: String,
    val publicId: String,
    val fingerprint: String,
    val createdAtEpochMs: Long
)

interface IdentityRepository {
    suspend fun create(displayName: String): Result<LocalIdentity>
    suspend fun current(): LocalIdentity?
    suspend fun clear(): Result<Unit>
}
