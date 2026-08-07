package org.ciphrchat.app.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.crypto.SignalStoreAdapter
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.IdentityEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentIdentityRepository @Inject constructor(
    private val database: AppDatabase,
    private val signalStore: SignalStoreAdapter
) : IdentityRepository {

    override suspend fun create(displayName: String): Result<LocalIdentity> = withContext(Dispatchers.IO) {
        runCatching {
            require(displayName.trim().length in 1..40) { "Display name must be 1–40 characters" }
            
            // The Signal identity is persisted in SQLCipher. SQLCipher's passphrase is
            // wrapped by Android Keystore, so the app identity and Signal identity share
            // one stable public key without creating a second unrelated keypair.
            val pubKeyBytes = signalStore.identityKeyPair.publicKey.serialize()
            val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
            val hex = digest.take(16).joinToString("") { "%02x".format(it) }
            val fingerprint = hex.chunked(4).take(4).joinToString("-").uppercase()
            
            val entity = IdentityEntity(
                publicId = "ciphr:$hex",
                displayName = displayName.trim(),
                fingerprint = fingerprint,
                createdAt = System.currentTimeMillis()
            )

            // Save to DB
            database.identityDao().insertIdentity(entity)

            mapToModel(entity)
        }
    }

    override suspend fun current(): LocalIdentity? = withContext(Dispatchers.IO) {
        database.identityDao().getLocalIdentity()?.let { mapToModel(it) }
    }

    override suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.clearAllTables() // Clear all user data
        }
    }

    private fun mapToModel(entity: IdentityEntity): LocalIdentity {
        return LocalIdentity(
            displayName = entity.displayName,
            publicId = entity.publicId,
            fingerprint = entity.fingerprint,
            createdAtEpochMs = entity.createdAt
        )
    }
}
