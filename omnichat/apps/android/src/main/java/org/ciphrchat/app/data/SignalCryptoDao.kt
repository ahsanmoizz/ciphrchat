package org.ciphrchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SignalCryptoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveIdentity(entity: SignalIdentityEntity)

    @Query("SELECT * FROM signal_identities WHERE addressName = :addressName")
    fun getIdentity(addressName: String): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePreKey(entity: SignalPreKeyEntity)

    @Query("SELECT * FROM signal_prekeys WHERE preKeyId = :preKeyId")
    fun getPreKey(preKeyId: Int): SignalPreKeyEntity?

    @Query("SELECT * FROM signal_prekeys ORDER BY preKeyId LIMIT 1")
    fun getAnyPreKey(): SignalPreKeyEntity?

    @Query("SELECT COUNT(*) FROM signal_prekeys WHERE preKeyId = :preKeyId")
    fun containsPreKey(preKeyId: Int): Int

    @Query("DELETE FROM signal_prekeys WHERE preKeyId = :preKeyId")
    fun removePreKey(preKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSignedPreKey(entity: SignalSignedPreKeyEntity)

    @Query("SELECT * FROM signal_signed_prekeys WHERE signedPreKeyId = :signedPreKeyId")
    fun getSignedPreKey(signedPreKeyId: Int): SignalSignedPreKeyEntity?

    @Query("SELECT * FROM signal_signed_prekeys")
    fun getAllSignedPreKeys(): List<SignalSignedPreKeyEntity>

    @Query("SELECT COUNT(*) FROM signal_signed_prekeys WHERE signedPreKeyId = :signedPreKeyId")
    fun containsSignedPreKey(signedPreKeyId: Int): Int

    @Query("DELETE FROM signal_signed_prekeys WHERE signedPreKeyId = :signedPreKeyId")
    fun removeSignedPreKey(signedPreKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSession(entity: SignalSessionEntity)

    @Query("SELECT * FROM signal_sessions WHERE addressName = :addressName")
    fun getSession(addressName: String): SignalSessionEntity?

    @Query("SELECT COUNT(*) FROM signal_sessions WHERE addressName = :addressName")
    fun containsSession(addressName: String): Int

    @Query("DELETE FROM signal_sessions WHERE addressName = :addressName")
    fun deleteSession(addressName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveLocalState(entity: SignalLocalStateEntity)

    @Query("SELECT * FROM signal_local_state WHERE id = 1")
    fun getLocalState(): SignalLocalStateEntity?
}
