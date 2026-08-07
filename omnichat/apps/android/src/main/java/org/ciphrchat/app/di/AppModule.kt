package org.ciphrchat.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.ContactRepository
import org.ciphrchat.app.identity.PersistentContactRepository
import org.ciphrchat.app.messaging.MessageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideIdentityRepository(
        implementation: org.ciphrchat.app.identity.PersistentIdentityRepository
    ): IdentityRepository = implementation

    @Provides
    @Singleton
    fun provideContactRepository(
        implementation: PersistentContactRepository
    ): ContactRepository = implementation

    @Provides
    @Singleton
    fun provideMessageRepository(
        implementation: org.ciphrchat.app.messaging.PersistentMessageRepository
    ): MessageRepository = implementation
}
