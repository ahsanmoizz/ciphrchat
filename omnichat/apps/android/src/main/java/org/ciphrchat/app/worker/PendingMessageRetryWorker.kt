package org.ciphrchat.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.ciphrchat.app.messaging.PersistentMessageRepository

@HiltWorker
class PendingMessageRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PersistentMessageRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val allDelivered = repository.flushPendingOutbox()
        return if (allDelivered) Result.success() else Result.retry()
    }
}
