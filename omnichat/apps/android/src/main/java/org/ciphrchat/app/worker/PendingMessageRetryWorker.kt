package org.ciphrchat.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.ciphrchat.app.messaging.MessageRepository

@HiltWorker
class PendingMessageRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Phase 1: repository has no persistent pending-query API yet.
        // Return success and record this limitation in ONE_HOUR_RESULT.md.
        return Result.success()
    }
}
