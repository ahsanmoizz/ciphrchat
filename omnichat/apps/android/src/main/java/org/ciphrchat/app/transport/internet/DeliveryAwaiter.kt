package org.ciphrchat.app.transport.internet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** Correlates native request-response callbacks with the message that is waiting for proof. */
class DeliveryAwaiter {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()

    suspend fun await(
        messageId: String,
        timeoutMs: Long,
        enqueue: () -> Boolean
    ): Result<Unit> {
        if (messageId.isBlank()) return Result.failure(IllegalArgumentException("Message ID is missing"))
        val completion = CompletableDeferred<Result<Unit>>()
        pending.remove(messageId)?.cancel()
        pending[messageId] = completion
        if (!enqueue()) {
            pending.remove(messageId, completion)
            return Result.failure(IllegalStateException("Message could not enter the secure network queue"))
        }
        val result = withTimeoutOrNull(timeoutMs) { completion.await() }
            ?: Result.failure(IllegalStateException("Remote delivery acknowledgement timed out"))
        pending.remove(messageId, completion)
        return result
    }

    fun accepted(messageId: String) {
        pending.remove(messageId)?.complete(Result.success(Unit))
    }

    fun failed(messageId: String, reason: String) {
        if (messageId.isNotBlank()) {
            pending.remove(messageId)?.complete(Result.failure(IllegalStateException(reason)))
        }
    }
}
