package org.ciphrchat.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.PrototypeIdentityRepository
import org.ciphrchat.app.messaging.InMemoryMessageRepository
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
        implementation: PrototypeIdentityRepository
    ): IdentityRepository = implementation

    @Provides
    @Singleton
    fun provideMessageRepository(
        implementation: InMemoryMessageRepository
    ): MessageRepository = implementation
}
