package org.ciphrchat.app.di

import android.content.Context
import androidx.room.Room
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
        .build()
    }
}
