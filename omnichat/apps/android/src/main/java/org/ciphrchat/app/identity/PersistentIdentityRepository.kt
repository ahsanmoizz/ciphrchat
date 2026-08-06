package org.ciphrchat.app.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.IdentityEntity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentIdentityRepository @Inject constructor(
    private val database: AppDatabase
) : IdentityRepository {

    companion object {
        private const val KEY_ALIAS = "CiphrChatIdentityKey"
    }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    override suspend fun create(displayName: String): Result<LocalIdentity> = withContext(Dispatchers.IO) {
        runCatching {
            require(displayName.trim().length in 1..40) { "Display name must be 1–40 characters" }
            
            // Delete existing key if any
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

            // Generate new EC KeyPair in Android Keystore
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .build()

            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()

            // Generate ID from public key
            val pubKeyBytes = keyPair.public.encoded
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
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
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
