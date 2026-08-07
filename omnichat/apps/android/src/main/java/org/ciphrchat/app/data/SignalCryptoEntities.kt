package org.ciphrchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey val addressName: String,
    val identityKey: ByteArray
)

@Entity(tableName = "signal_prekeys")
data class SignalPreKeyEntity(
    @PrimaryKey val preKeyId: Int,
    val recordData: ByteArray
)

@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey val signedPreKeyId: Int,
    val recordData: ByteArray
)

@Entity(tableName = "signal_sessions")
data class SignalSessionEntity(
    @PrimaryKey val addressName: String,
    val recordData: ByteArray
)

@Entity(tableName = "signal_local_state")
data class SignalLocalStateEntity(
    @PrimaryKey val id: Int = 1,
    val identityKeyPair: ByteArray,
    val registrationId: Int
)
