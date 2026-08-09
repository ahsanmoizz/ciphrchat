package org.ciphrchat.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.ciphrchat.app.data.AppDatabase
import org.ciphrchat.app.messaging.MessageStatus
import org.ciphrchat.app.messaging.DeliveryStatusPolicy
import org.ciphrchat.app.transport.AutomaticRouter
import org.ciphrchat.app.transport.OutboundEnvelope
import org.ciphrchat.app.transport.SendResult
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.InvitationService

@HiltWorker
class PendingMessageRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val router: AutomaticRouter,
    private val identity: IdentityRepository,
    private val invitationService: InvitationService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = database.messageDao()
        val pendingMessages = dao.getMessagesPendingDelivery()

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        var allSuccess = true
        val senderInvitation = invitationService.createInvitation().getOrElse {
            return Result.retry()
        }
        for (message in pendingMessages) {
            val envelope = OutboundEnvelope(
                protocolVersion = 2,
                messageId = message.id,
                recipientId = message.recipientId,
                senderId = identity.current()?.publicId ?: "",
                createdAtEpochMs = message.createdAtEpochMs,
                expiresAtEpochMs = message.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
                hopLimit = 3,
                encryptedPayload = message.encryptedPayload,
                testOnly = false,
                senderInvitation = senderInvitation
            )

            if (message.encryptedPayload.isEmpty()) {
                dao.updateMessage(message.copy(status = MessageStatus.FAILED))
                allSuccess = false
                continue
            }

            val result = router.route(envelope)
            val current = dao.findById(message.id) ?: continue
            if (current.status == MessageStatus.DELIVERED) continue
            val updated = when (result) {
                is SendResult.Accepted -> current.copy(
                    status = DeliveryStatusPolicy.statusFor(result),
                    selectedTransport = result.transport.name
                ).also { updatedMessage ->
                    if (updatedMessage.status != MessageStatus.DELIVERED) allSuccess = false
                }
                is SendResult.Rejected -> {
                    allSuccess = false
                    current
                }
                is SendResult.Failed -> {
                    allSuccess = false
                    current
                }
                is SendResult.Failure -> {
                    allSuccess = false
                    current
                }
                SendResult.Success -> {
                    allSuccess = false
                    current
                }
            }

            if (updated != current) {
                dao.updateMessage(updated)
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
