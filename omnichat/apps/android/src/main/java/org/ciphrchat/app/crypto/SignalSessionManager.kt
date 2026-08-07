package org.ciphrchat.app.crypto

import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalSessionManager @Inject constructor(
    private val store: SignalStoreAdapter
) {
    @Synchronized
    fun generatePreKeyBundle(): PreKeyBundle {
        val identityKeyPair = store.identityKeyPair
        val registrationId = store.localRegistrationId

        val preKey = store.loadExistingPreKey() ?: KeyHelper.generatePreKeys(0, 100).first().also {
            store.storePreKey(it.id, it)
        }
        val signedPreKey = store.loadExistingSignedPreKey() ?: KeyHelper.generateSignedPreKey(identityKeyPair, 0).also {
            store.storeSignedPreKey(it.id, it)
        }
        
        return PreKeyBundle(
            registrationId,
            1,
            preKey.id,
            preKey.keyPair.publicKey,
            signedPreKey.id,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKeyPair.publicKey
        )
    }

    fun hasSession(address: SignalProtocolAddress): Boolean = store.containsSession(address)

    fun processPreKeyBundle(address: SignalProtocolAddress, bundle: PreKeyBundle) {
        val builder = SessionBuilder(store, address)
        builder.process(bundle)
    }

    fun encryptMessage(address: SignalProtocolAddress, plaintext: ByteArray): CiphertextMessage {
        val cipher = SessionCipher(store, address)
        return cipher.encrypt(plaintext)
    }

    fun decryptMessage(address: SignalProtocolAddress, ciphertext: CiphertextMessage): ByteArray {
        val cipher = SessionCipher(store, address)
        return when (ciphertext) {
            is PreKeySignalMessage -> cipher.decrypt(ciphertext)
            is SignalMessage -> cipher.decrypt(ciphertext)
            else -> throw IllegalArgumentException("Unknown ciphertext message type")
        }
    }
}
