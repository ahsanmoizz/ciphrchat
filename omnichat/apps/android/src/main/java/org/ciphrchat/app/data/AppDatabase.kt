package org.ciphrchat.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Currently empty, but schema will be added in subsequent plans
@Database(
    entities = [], // We will add IdentityEntity and MessageEntity later
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAO getters will be added here
}
