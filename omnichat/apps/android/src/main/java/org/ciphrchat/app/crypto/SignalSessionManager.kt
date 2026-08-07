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
    fun generatePreKeyBundle(): PreKeyBundle {
        val identityKeyPair = store.identityKeyPair
        val registrationId = store.localRegistrationId
        
        val preKeys = KeyHelper.generatePreKeys(0, 100)
        val signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0)
        
        preKeys.forEach { store.storePreKey(it.id, it) }
        store.storeSignedPreKey(signedPreKey.id, signedPreKey)
        
        return PreKeyBundle(
            registrationId,
            1,
            preKeys[0].id,
            preKeys[0].keyPair.publicKey,
            signedPreKey.id,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKeyPair.publicKey
        )
    }

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
