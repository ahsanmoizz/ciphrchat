package org.ciphrchat.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.security.KeyManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS contacts (
                    contactId TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    peerId TEXT NOT NULL,
                    relayAddress TEXT NOT NULL,
                    registrationId INTEGER NOT NULL,
                    deviceId INTEGER NOT NULL,
                    preKeyId INTEGER NOT NULL,
                    preKey BLOB NOT NULL,
                    signedPreKeyId INTEGER NOT NULL,
                    signedPreKey BLOB NOT NULL,
                    signedPreKeySignature BLOB NOT NULL,
                    identityKey BLOB NOT NULL,
                    verified INTEGER NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN encryptedPayload BLOB NOT NULL DEFAULT X''")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: KeyManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = keyManager.getOrCreateDatabasePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ciphrchat-secure.db"
        )
        .openHelperFactory(factory)
        .addMigrations(MIGRATION_2_3)
        .addMigrations(MIGRATION_3_4)
        .build()
    }
}
