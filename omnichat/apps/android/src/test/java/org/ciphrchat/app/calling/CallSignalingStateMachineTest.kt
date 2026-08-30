package org.ciphrchat.app.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSignalingStateMachineTest {

    @Test
    fun serializesAndDeserializesCallSignals() {
        val offer = CallSignal.Offer(
            callId = "call-1234",
            sdp = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n",
            senderId = "sender-peer",
            recipientId = "recipient-peer"
        )
        val jsonStr = offer.toJson()
        val parsed = CallSignal.fromJson(jsonStr) as? CallSignal.Offer

        assertNotNull(parsed)
        assertEquals(offer.callId, parsed?.callId)
        assertEquals(offer.sdp, parsed?.sdp)
        assertEquals(offer.senderId, parsed?.senderId)
        assertEquals(offer.recipientId, parsed?.recipientId)
    }

    @Test
    fun handlesAnswerAndHangupSignals() {
        val answer = CallSignal.Answer(
            callId = "call-5678",
            sdp = "v=0\r\nm=audio 9 ...",
            senderId = "recipient-peer",
            recipientId = "sender-peer"
        )
        val parsedAnswer = CallSignal.fromJson(answer.toJson()) as? CallSignal.Answer
        assertNotNull(parsedAnswer)
        assertEquals("call-5678", parsedAnswer?.callId)

        val hangup = CallSignal.Hangup(
            callId = "call-5678",
            senderId = "recipient-peer",
            recipientId = "sender-peer",
            durationSeconds = 125L
        )
        val parsedHangup = CallSignal.fromJson(hangup.toJson()) as? CallSignal.Hangup
        assertNotNull(parsedHangup)
        assertEquals(125L, parsedHangup?.durationSeconds)
    }

    @Test
    fun handlesRejectAndRingingSignals() {
        val reject = CallSignal.Reject(
            callId = "call-9999",
            senderId = "recipient-peer",
            recipientId = "sender-peer",
            reason = "Decline"
        )
        val parsedReject = CallSignal.fromJson(reject.toJson()) as? CallSignal.Reject
        assertNotNull(parsedReject)
        assertEquals("Decline", parsedReject?.reason)

        val ringing = CallSignal.Ringing(
            callId = "call-9999",
            senderId = "recipient-peer",
            recipientId = "sender-peer"
        )
        val parsedRinging = CallSignal.fromJson(ringing.toJson()) as? CallSignal.Ringing
        assertNotNull(parsedRinging)
        assertEquals("call-9999", parsedRinging?.callId)
    }
}
