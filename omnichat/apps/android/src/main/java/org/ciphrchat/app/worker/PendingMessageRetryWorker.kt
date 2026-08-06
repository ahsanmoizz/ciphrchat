package org.ciphrchat.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.ciphrchat.app.messaging.MessageRepository

import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.messaging.MessageStatus
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult

@HiltWorker
class PendingMessageRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val router: AutomaticRouter
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = database.messageDao()
        val pendingMessages = dao.getMessagesByStatus(MessageStatus.QUEUED)

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        var allSuccess = true
        for (message in pendingMessages) {
            val envelope = OutboundEnvelope(
                protocolVersion = 1,
                messageId = message.id,
                recipientId = message.recipientId,
                createdAtEpochMs = message.createdAtEpochMs,
                expiresAtEpochMs = message.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
                hopLimit = 3,
                encryptedPayload = message.body.encodeToByteArray(),
                testOnly = true
            )

            val result = router.route(envelope)
            val updated = when (result) {
                is SendResult.Accepted -> message.copy(
                    status = MessageStatus.SENT,
                    selectedTransport = result.transport.name
                )
                is SendResult.Rejected -> {
                    allSuccess = false
                    message // remains QUEUED
                }
                is SendResult.Failed -> message.copy(status = MessageStatus.FAILED)
            }

            if (updated != message) {
                dao.updateMessage(updated)
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
