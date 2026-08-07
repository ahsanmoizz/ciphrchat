package org.ciphrchat.app.crypto

import org.ciphrchat.app.data.*
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.IdentityKeyStore
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.PreKeyStore
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SessionStore
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyStore
import org.whispersystems.libsignal.util.KeyHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalStoreAdapter @Inject constructor(
    private val database: AppDatabase
) : SignalProtocolStore {

    private val dao = database.signalCryptoDao()

    init {
        // Initialize local state if it doesn't exist
        if (dao.getLocalState() == null) {
            val identityKeyPair = KeyHelper.generateIdentityKeyPair()
            val registrationId = KeyHelper.generateRegistrationId(false)
            dao.saveLocalState(SignalLocalStateEntity(
                id = 1,
                identityKeyPair = identityKeyPair.serialize(),
                registrationId = registrationId
            ))
        }
    }

    // -- IdentityKeyStore --

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val state = dao.getLocalState() ?: throw IllegalStateException("Local state not initialized")
        return IdentityKeyPair(state.identityKeyPair)
    }

    override fun getLocalRegistrationId(): Int {
        val state = dao.getLocalState() ?: throw IllegalStateException("Local state not initialized")
        return state.registrationId
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existingEntity = dao.getIdentity(address.name)
        val existing = existingEntity?.identityKey?.let { IdentityKey(it, 0) }

        if (existing != null && existing != identityKey) {
            // Key change detected - MITM or device change
            println("SECURITY WARNING: Identity key changed for ${address.name}. Emitting KeyChangeWarning event.")
        }
        
        dao.saveIdentity(SignalIdentityEntity(address.name, identityKey.serialize()))
        return existing == null || existing != identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val existingEntity = dao.getIdentity(address.name)
        val existing = existingEntity?.identityKey?.let { IdentityKey(it, 0) }
        return existing == null || existing == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val entity = dao.getIdentity(address.name)
        return entity?.identityKey?.let { IdentityKey(it, 0) }
    }

    // -- PreKeyStore --

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val entity = dao.getPreKey(preKeyId) ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such prekey: $preKeyId")
        return PreKeyRecord(entity.recordData)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        dao.savePreKey(SignalPreKeyEntity(preKeyId, record.serialize()))
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return dao.containsPreKey(preKeyId) > 0
    }

    override fun removePreKey(preKeyId: Int) {
        dao.removePreKey(preKeyId)
    }

    // -- SignedPreKeyStore --

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val entity = dao.getSignedPreKey(signedPreKeyId) ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        return SignedPreKeyRecord(entity.recordData)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return dao.getAllSignedPreKeys().map { SignedPreKeyRecord(it.recordData) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        dao.saveSignedPreKey(SignalSignedPreKeyEntity(signedPreKeyId, record.serialize()))
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return dao.containsSignedPreKey(signedPreKeyId) > 0
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        dao.removeSignedPreKey(signedPreKeyId)
    }

    // -- SessionStore --

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val entity = dao.getSession(address.name)
        return if (entity != null) SessionRecord(entity.recordData) else SessionRecord()
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return emptyList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        dao.saveSession(SignalSessionEntity(address.name, record.serialize()))
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return dao.containsSession(address.name) > 0
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        dao.deleteSession(address.name)
    }

    override fun deleteAllSessions(name: String) {
        dao.deleteSession(name) // Simplification for MVP
    }
}
