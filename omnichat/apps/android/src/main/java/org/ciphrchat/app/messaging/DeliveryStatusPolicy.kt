package org.ciphrchat.app.messaging

import org.ciphrchat.app.transport.SendResult

/** Transient route failures remain queued; transport acceptance is sent, not delivered. */
object DeliveryStatusPolicy {
    fun statusFor(result: SendResult): MessageStatus = when (result) {
        is SendResult.Accepted -> MessageStatus.SENT
        is SendResult.Rejected,
        is SendResult.Failed,
        is SendResult.Failure,
        SendResult.Success -> MessageStatus.QUEUED
    }

    fun merge(current: MessageStatus, next: MessageStatus): MessageStatus = when {
        current == MessageStatus.DELIVERED -> MessageStatus.DELIVERED
        current == MessageStatus.SENT && (next == MessageStatus.QUEUED || next == MessageStatus.ROUTING) -> MessageStatus.SENT
        else -> next
    }
}

