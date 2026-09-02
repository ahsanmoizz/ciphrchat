package org.ciphrchat.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IdentityEntity::class, 
        MessageEntity::class,
        ContactEntity::class,
        SignalIdentityEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        SignalSessionEntity::class,
        SignalLocalStateEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun signalCryptoDao(): SignalCryptoDao
}
