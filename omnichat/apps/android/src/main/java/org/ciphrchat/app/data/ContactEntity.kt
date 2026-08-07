package org.ciphrchat.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts")
data class ContactEntity(
    @androidx.room.PrimaryKey val contactId: String,
    val displayName: String,
    val peerId: String,
    val relayAddress: String,
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityKey: ByteArray,
    val discoveryToken: String = "",
    val verified: Boolean,
    val createdAtEpochMs: Long
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE")
    fun getAllForBackup(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    fun find(contactId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE peerId = :peerId LIMIT 1")
    fun findByPeerId(peerId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE contactId = :contactId")
    fun delete(contactId: String)
}
