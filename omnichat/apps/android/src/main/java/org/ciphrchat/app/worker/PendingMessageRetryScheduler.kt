package org.ciphrchat.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Centralizes durable delivery retries so restored routes can wake queued messages immediately. */
@Singleton
class PendingMessageRetryScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lastImmediateScheduleAt = AtomicLong(0L)

    fun schedule() = enqueue(delaySeconds = 10L, ExistingWorkPolicy.KEEP)

    fun scheduleNow() {
        val now = System.currentTimeMillis()
        val previous = lastImmediateScheduleAt.get()
        if (now - previous < IMMEDIATE_DEBOUNCE_MS ||
            !lastImmediateScheduleAt.compareAndSet(previous, now)
        ) return
        enqueue(delaySeconds = 0L, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(delaySeconds: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<PendingMessageRetryWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        private const val WORK_NAME = "ciphrchat-pending-message-delivery"
        private const val IMMEDIATE_DEBOUNCE_MS = 3_000L
    }
}
