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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE contacts ADD COLUMN discoveryToken TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentFileName TEXT")
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentMimeType TEXT")
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentStoragePath TEXT")
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentSizeBytes INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentSha256 TEXT")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN isForwarded INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId_createdAtEpochMs ON messages(conversationId, createdAtEpochMs)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_status ON messages(status)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS groups (
                    groupId TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    creatorPublicId TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_groups_groupId ON groups(groupId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_groups_updatedAtEpochMs ON groups(updatedAtEpochMs)")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS group_members (
                    groupId TEXT NOT NULL,
                    memberPublicId TEXT NOT NULL,
                    joinedAtEpochMs INTEGER NOT NULL,
                    membershipState TEXT NOT NULL DEFAULT 'ACTIVE',
                    PRIMARY KEY(groupId, memberPublicId)
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_groupId ON group_members(groupId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_memberPublicId ON group_members(memberPublicId)")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS group_messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    groupId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    body TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    direction TEXT NOT NULL,
                    status TEXT NOT NULL,
                    selectedTransport TEXT,
                    attachmentFileName TEXT,
                    attachmentMimeType TEXT,
                    attachmentStoragePath TEXT,
                    attachmentSizeBytes INTEGER NOT NULL DEFAULT 0,
                    attachmentSha256 TEXT,
                    isForwarded INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_groupId_createdAtEpochMs ON group_messages(groupId, createdAtEpochMs)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_status ON group_messages(status)")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS group_recipient_deliveries (
                    groupMessageId TEXT NOT NULL,
                    recipientPublicId TEXT NOT NULL,
                    status TEXT NOT NULL,
                    selectedTransport TEXT,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    lastAttemptEpochMs INTEGER NOT NULL DEFAULT 0,
                    errorMessage TEXT,
                    PRIMARY KEY(groupMessageId, recipientPublicId)
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_recipient_deliveries_groupMessageId ON group_recipient_deliveries(groupMessageId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_recipient_deliveries_recipientPublicId ON group_recipient_deliveries(recipientPublicId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_group_recipient_deliveries_status ON group_recipient_deliveries(status)")
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
        .addMigrations(MIGRATION_4_5)
        .addMigrations(MIGRATION_5_6)
        .addMigrations(MIGRATION_6_7)
        .addMigrations(MIGRATION_7_8)
        .addMigrations(MIGRATION_8_9)
        .build()
    }
}
