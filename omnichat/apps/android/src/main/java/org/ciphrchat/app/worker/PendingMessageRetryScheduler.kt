package org.ciphrchat.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Centralizes durable delivery retries so restored routes can wake queued messages immediately with bounded backoff and jitter. */
@Singleton
class PendingMessageRetryScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lastImmediateScheduleAt = AtomicLong(0L)
    private val retryAttempt = AtomicInteger(0)

    fun schedule(attempt: Int = retryAttempt.incrementAndGet()) {
        val delaySec = computeBackoffWithJitter(attempt)
        enqueue(delaySeconds = delaySec, ExistingWorkPolicy.KEEP)
    }

    fun resetAttempts() {
        retryAttempt.set(0)
    }

    fun scheduleNow() {
        resetAttempts()
        val now = System.currentTimeMillis()
        val previous = lastImmediateScheduleAt.get()
        if (now - previous < IMMEDIATE_DEBOUNCE_MS ||
            !lastImmediateScheduleAt.compareAndSet(previous, now)
        ) return
        enqueue(delaySeconds = 0L, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(delaySeconds: Long, policy: ExistingWorkPolicy) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<PendingMessageRetryWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }

    companion object {
        private const val WORK_NAME = "ciphrchat-pending-message-delivery"
        private const val IMMEDIATE_DEBOUNCE_MS = 2_000L
        private const val BASE_BACKOFF_SECONDS = 5L
        private const val MAX_BACKOFF_SECONDS = 300L // 5 minutes max

        fun computeBackoffWithJitter(attempt: Int, randomFactor: Double = Random.nextDouble(0.8, 1.2)): Long {
            val safeAttempt = attempt.coerceIn(0, 6)
            val exponentialSec = (BASE_BACKOFF_SECONDS * (1L shl safeAttempt)).coerceAtMost(MAX_BACKOFF_SECONDS)
            return (exponentialSec * randomFactor).toLong().coerceIn(1L, MAX_BACKOFF_SECONDS)
        }
    }
}

