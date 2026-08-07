package org.ciphrchat.app.crypto

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
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.whispersystems.libsignal.util.KeyHelper

@Singleton
class SignalStoreAdapter @Inject constructor() : SignalProtocolStore {
    
    // In a real implementation, these maps would be backed by Room DAOs and SQLCipher.
    // For this prototype/scaffold phase, we implement them in-memory, but they fulfill the
    // Signal Protocol interfaces as if they were persisted.

    private val identityKeyPair: IdentityKeyPair = KeyHelper.generateIdentityKeyPair()
    private val localRegistrationId: Int = KeyHelper.generateRegistrationId(false)
    
    private val preKeys = ConcurrentHashMap<Int, ByteArray>()
    private val signedPreKeys = ConcurrentHashMap<Int, ByteArray>()
    private val sessions = ConcurrentHashMap<String, ByteArray>()
    private val identityKeys = ConcurrentHashMap<String, IdentityKey>()

    // -- IdentityKeyStore --

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return localRegistrationId
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = identityKeys[address.name]
        if (existing != null && existing != identityKey) {
            // Key change detected
            println("WARNING: Identity key changed for ${address.name}")
            // Here we would emit a KeyChangeWarning event
        }
        identityKeys[address.name] = identityKey
        return existing == null || existing != identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val existing = identityKeys[address.name]
        return existing == null || existing == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return identityKeys[address.name]
    }

    // -- PreKeyStore --

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val data = preKeys[preKeyId] ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such prekey: $preKeyId")
        return PreKeyRecord(data)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record.serialize()
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeys.containsKey(preKeyId)
    }

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    // -- SignedPreKeyStore --

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val data = signedPreKeys[signedPreKeyId] ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        return SignedPreKeyRecord(data)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeys.values.map { SignedPreKeyRecord(it) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record.serialize()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeys.containsKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    // -- SessionStore --

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val data = sessions[address.name]
        return if (data != null) SessionRecord(data) else SessionRecord()
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return emptyList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address.name] = record.serialize()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sessions.containsKey(address.name)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address.name)
    }

    override fun deleteAllSessions(name: String) {
        sessions.remove(name)
    }
}
