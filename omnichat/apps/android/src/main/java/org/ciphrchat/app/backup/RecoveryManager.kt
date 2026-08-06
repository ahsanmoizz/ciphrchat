package org.ciphrchat.app.backup

import android.content.Context
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ciphrchat.app.security.KeyManager

@Singleton
class RecoveryManager @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager
) {
    suspend fun exportRecoveryFile(outputStream: OutputStream, passwordForExport: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Get the database passphrase
            val dbPassphrase = keyManager.getOrCreateDatabasePassphrase()
            
            // Format: CIPHR_RECOVERY_V1
            val header = "CIPHR_RECOVERY_V1\n".toByteArray(Charsets.UTF_8)
            
            // Generate symmetric key from password (in real prod, use PBKDF2 or Argon2. Using SHA-256 for phase 2 mockup)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(passwordForExport.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val ciphertext = cipher.doFinal(dbPassphrase)

            outputStream.use { out ->
                out.write(header)
                out.write(iv)
                out.write(ciphertext)
            }
        }
    }
}
