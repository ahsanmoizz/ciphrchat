package org.ciphrchat.app.messaging

import android.util.Log

/**
 * Thread-safe per-message timing instrumentation for the zero-unnecessary-latency messaging pipeline.
 * Strictly guarantees that no plaintext message content, secrets, or cryptographic keys are ever logged.
 */
object MessageTimingTracker {
    private const val TAG = "MessagePipelineTiming"

    fun recordPersist(messageId: String, durationMs: Long) {
        logStage(messageId, "persist", durationMs)
    }

    fun recordEncrypt(messageId: String, durationMs: Long) {
        logStage(messageId, "encrypt", durationMs)
    }

    fun recordDispatch(messageId: String, elapsedSinceCreatedMs: Long) {
        logStage(messageId, "dispatch", elapsedSinceCreatedMs)
    }

    fun recordRoute(messageId: String, transport: String, durationMs: Long, result: String) {
        val safeId = safeId(messageId)
        safeLog(TAG, "Message [$safeId] stage=route transport=$transport duration=${durationMs}ms result=$result")
    }

    fun recordRelayAccepted(messageId: String, elapsedSinceCreatedMs: Long) {
        logStage(messageId, "relay_accepted", elapsedSinceCreatedMs)
    }

    fun recordReceive(messageId: String, durationMs: Long) {
        logStage(messageId, "receive", durationMs)
    }

    fun recordReceipt(messageId: String, durationMs: Long) {
        logStage(messageId, "receipt", durationMs)
    }

    fun recordDelivered(messageId: String, totalElapsedMs: Long) {
        logStage(messageId, "delivered", totalElapsedMs)
    }

    private fun safeId(messageId: String): String =
        if (messageId.length >= 8) messageId.take(8) else messageId

    private fun logStage(messageId: String, stage: String, durationMs: Long) {
        val safeId = safeId(messageId)
        safeLog(TAG, "Message [$safeId] stage=$stage elapsed=${durationMs}ms")
    }

    private fun safeLog(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            // In JVM unit test environment, android.util.Log is unmocked
        }
    }
}
