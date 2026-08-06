package org.ciphrchat.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Currently empty, but schema will be added in subsequent plans
@Database(
    entities = [IdentityEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun messageDao(): MessageDao
}
