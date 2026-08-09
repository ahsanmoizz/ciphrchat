package org.ciphrchat.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.ciphrchat.app.messaging.PersistentMessageRepository
import javax.inject.Inject

@HiltAndroidApp
class CiphrChatApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var messageRepository: PersistentMessageRepository

    override fun onCreate() {
        super.onCreate()
        // Eagerly start inbound network and delivery-receipt collectors, including
        // when WorkManager wakes the app process while no Activity is open.
        messageRepository.ensureStarted()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
