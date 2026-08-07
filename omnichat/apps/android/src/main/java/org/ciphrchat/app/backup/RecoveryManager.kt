package org.ciphrchat.app.backup

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.data.ContactEntity
import org.ciphrchat.app.data.IdentityEntity
import org.ciphrchat.app.data.MessageEntity
import org.ciphrchat.app.data.SignalIdentityEntity
import org.ciphrchat.app.data.SignalLocalStateEntity
import org.ciphrchat.app.data.SignalPreKeyEntity
import org.ciphrchat.app.data.SignalSessionEntity
import org.ciphrchat.app.data.SignalSignedPreKeyEntity
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.messaging.MessageStatus
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Password-protected, self-contained recovery snapshot.
 *
 * The previous format exported only the SQLCipher passphrase. That could not
 * restore an identity after reinstall because the database rows and Signal
 * private records were absent. V2 includes all application state needed to
 * resume the identity and conversations, encrypted as one authenticated blob.
 */
@Singleton
class RecoveryManager @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun exportRecoveryFile(
        outputStream: OutputStream,
        passwordForExport: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(passwordForExport.length >= MIN_PASSWORD_LENGTH) {
                "Recovery password must be at least $MIN_PASSWORD_LENGTH characters"
            }
            val payload = buildSnapshot().toString().toByteArray(Charsets.UTF_8)
            val salt = randomBytes(SALT_BYTES)
            val iv = randomBytes(IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passwordForExport, salt), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(payload)

            outputStream.use { out ->
                out.write(HEADER)
                out.write(salt)
                out.write(iv)
                out.write(ciphertext)
            }
        }
    }

    suspend fun importRecoveryFile(
        inputStream: InputStream,
        passwordForImport: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(passwordForImport.length >= MIN_PASSWORD_LENGTH) {
                "Recovery password must be at least $MIN_PASSWORD_LENGTH characters"
            }
            val bytes = inputStream.use { it.readBytes() }
            require(bytes.size <= MAX_FILE_BYTES) { "Recovery file is too large" }
            require(bytes.size > HEADER.size + SALT_BYTES + IV_BYTES) { "Recovery file is incomplete" }
            require(bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER)) {
                "Unsupported recovery file format"
            }

            val saltStart = HEADER.size
            val ivStart = saltStart + SALT_BYTES
            val ciphertextStart = ivStart + IV_BYTES
            val salt = bytes.copyOfRange(saltStart, ivStart)
            val iv = bytes.copyOfRange(ivStart, ciphertextStart)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passwordForImport, salt), GCMParameterSpec(128, iv))
            val snapshot = JSONObject(String(cipher.doFinal(bytes.copyOfRange(ciphertextStart, bytes.size)), Charsets.UTF_8))
            require(snapshot.optInt("formatVersion", 0) == 2) { "Unsupported recovery snapshot version" }
            restoreSnapshot(snapshot)
        }
    }

    private fun buildSnapshot(): JSONObject {
        val identity = database.identityDao().getLocalIdentity()
        require(identity != null) { "Create an identity before exporting recovery" }
        val signal = database.signalCryptoDao()
        val root = JSONObject()
            .put("formatVersion", 2)
            .put("identity", identity.toJson())
            .put("contacts", JSONArray(database.contactDao().getAllForBackup().map { it.toJson() }))
            .put("messages", JSONArray(database.messageDao().getAllForBackup().map { it.toJson() }))
            .put("signalIdentities", JSONArray(signal.getAllIdentitiesForBackup().map { it.toJson() }))
            .put("signalPreKeys", JSONArray(signal.getAllPreKeysForBackup().map { it.toJson() }))
            .put("signalSignedPreKeys", JSONArray(signal.getAllSignedPreKeys().map { it.toJson() }))
            .put("signalSessions", JSONArray(signal.getAllSessionsForBackup().map { it.toJson() }))
        signal.getLocalState()?.let { root.put("signalLocalState", it.toJson()) }
        return root
    }

    private fun restoreSnapshot(snapshot: JSONObject) {
        val identity = snapshot.getJSONObject("identity").toIdentity()
        val contacts = snapshot.getJSONArray("contacts").objects().map { it.toContact() }
        val messages = snapshot.getJSONArray("messages").objects().map { it.toMessage() }
        val signalIdentities = snapshot.getJSONArray("signalIdentities").objects().map { it.toSignalIdentity() }
        val preKeys = snapshot.getJSONArray("signalPreKeys").objects().map { it.toPreKey() }
        val signedPreKeys = snapshot.getJSONArray("signalSignedPreKeys").objects().map { it.toSignedPreKey() }
        val sessions = snapshot.getJSONArray("signalSessions").objects().map { it.toSession() }
        val localState = snapshot.optJSONObject("signalLocalState")?.toLocalState()

        database.runInTransaction {
            database.clearAllTables()
            database.identityDao().insertIdentity(identity)
            contacts.forEach(database.contactDao()::save)
            messages.forEach(database.messageDao()::insertMessageForRestore)
            val signal = database.signalCryptoDao()
            signalIdentities.forEach(signal::saveIdentity)
            preKeys.forEach(signal::savePreKey)
            signedPreKeys.forEach(signal::saveSignedPreKey)
            sessions.forEach(signal::saveSession)
            localState?.let(signal::saveLocalState)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private fun randomBytes(size: Int) = ByteArray(size).apply { SecureRandom().nextBytes(this) }

    private companion object {
        val HEADER = "CIPHR_RECOVERY_V2\n".toByteArray(Charsets.UTF_8)
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val PBKDF2_ITERATIONS = 600_000
        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_FILE_BYTES = 16 * 1024 * 1024
    }
}

private fun ByteArray.encode() = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.decodeBytes() = Base64.decode(this, Base64.NO_WRAP)

private fun IdentityEntity.toJson() = JSONObject()
    .put("publicId", publicId).put("displayName", displayName)
    .put("fingerprint", fingerprint).put("createdAt", createdAt)
private fun JSONObject.toIdentity() = IdentityEntity(
    getString("publicId"), getString("displayName"), getString("fingerprint"), getLong("createdAt")
)

private fun ContactEntity.toJson() = JSONObject()
    .put("contactId", contactId).put("displayName", displayName).put("peerId", peerId)
    .put("relayAddress", relayAddress).put("registrationId", registrationId).put("deviceId", deviceId)
    .put("preKeyId", preKeyId).put("preKey", preKey.encode()).put("signedPreKeyId", signedPreKeyId)
    .put("signedPreKey", signedPreKey.encode()).put("signedPreKeySignature", signedPreKeySignature.encode())
    .put("identityKey", identityKey.encode()).put("verified", verified).put("createdAtEpochMs", createdAtEpochMs)
private fun JSONObject.toContact() = ContactEntity(
    getString("contactId"), getString("displayName"), getString("peerId"), getString("relayAddress"),
    getInt("registrationId"), getInt("deviceId"), getInt("preKeyId"), getString("preKey").decodeBytes(),
    getInt("signedPreKeyId"), getString("signedPreKey").decodeBytes(), getString("signedPreKeySignature").decodeBytes(),
    getString("identityKey").decodeBytes(), getBoolean("verified"), getLong("createdAtEpochMs")
)

private fun MessageEntity.toJson() = JSONObject()
    .put("id", id).put("conversationId", conversationId).put("senderId", senderId).put("recipientId", recipientId)
    .put("body", body).put("encryptedPayload", encryptedPayload.encode()).put("createdAtEpochMs", createdAtEpochMs)
    .put("direction", direction.name).put("status", status.name).put("selectedTransport", selectedTransport)
private fun JSONObject.toMessage() = MessageEntity(
    getString("id"), getString("conversationId"), getString("senderId"), getString("recipientId"), getString("body"),
    getString("encryptedPayload").decodeBytes(), getLong("createdAtEpochMs"),
    MessageDirection.valueOf(getString("direction")), MessageStatus.valueOf(getString("status")),
    optString("selectedTransport").takeUnless { it == "" || it == "null" }
)

private fun SignalIdentityEntity.toJson() = JSONObject().put("addressName", addressName).put("identityKey", identityKey.encode())
private fun JSONObject.toSignalIdentity() = SignalIdentityEntity(getString("addressName"), getString("identityKey").decodeBytes())
private fun SignalPreKeyEntity.toJson() = JSONObject().put("preKeyId", preKeyId).put("recordData", recordData.encode())
private fun JSONObject.toPreKey() = SignalPreKeyEntity(getInt("preKeyId"), getString("recordData").decodeBytes())
private fun SignalSignedPreKeyEntity.toJson() = JSONObject().put("signedPreKeyId", signedPreKeyId).put("recordData", recordData.encode())
private fun JSONObject.toSignedPreKey() = SignalSignedPreKeyEntity(getInt("signedPreKeyId"), getString("recordData").decodeBytes())
private fun SignalSessionEntity.toJson() = JSONObject().put("addressName", addressName).put("recordData", recordData.encode())
private fun JSONObject.toSession() = SignalSessionEntity(getString("addressName"), getString("recordData").decodeBytes())
private fun SignalLocalStateEntity.toJson() = JSONObject()
    .put("id", id).put("identityKeyPair", identityKeyPair.encode()).put("registrationId", registrationId)
private fun JSONObject.toLocalState() = SignalLocalStateEntity(
    getInt("id"), getString("identityKeyPair").decodeBytes(), getInt("registrationId")
)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
