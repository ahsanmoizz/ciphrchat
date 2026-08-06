package org.ciphrchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "local_identity")
data class IdentityEntity(
    @PrimaryKey
    val publicId: String,
    val displayName: String,
    val fingerprint: String,
    val createdAt: Long
)

@Dao
interface IdentityDao {
    @Query("SELECT * FROM local_identity LIMIT 1")
    fun getLocalIdentity(): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIdentity(identity: IdentityEntity)
}
