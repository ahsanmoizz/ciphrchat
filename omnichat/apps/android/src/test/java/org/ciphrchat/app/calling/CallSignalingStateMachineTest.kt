package org.ciphrchat.app.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertEquals("sender-peer", parsedAnswer?.recipientId)

        val hangup = CallSignal.Hangup(
            callId = "call-5678",
            senderId = "recipient-peer",
            recipientId = "sender-peer",
            durationSeconds = 125L
        )
        val parsedHangup = CallSignal.fromJson(hangup.toJson()) as? CallSignal.Hangup
        assertNotNull(parsedHangup)
        assertEquals(125L, parsedHangup?.durationSeconds)
        assertEquals("sender-peer", parsedHangup?.recipientId)
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
        assertEquals("sender-peer", parsedReject?.recipientId)

        val ringing = CallSignal.Ringing(
            callId = "call-9999",
            senderId = "recipient-peer",
            recipientId = "sender-peer"
        )
        val parsedRinging = CallSignal.fromJson(ringing.toJson()) as? CallSignal.Ringing
        assertNotNull(parsedRinging)
        assertEquals("call-9999", parsedRinging?.callId)
        assertEquals("sender-peer", parsedRinging?.recipientId)
    }

    @Test
    fun handlesIceCandidateSignal() {
        val ice = CallSignal.IceCandidate(
            callId = "call-ice-1",
            recipientId = "recipient-peer",
            sdpMid = "audio",
            sdpMLineIndex = 0,
            sdpCandidate = "candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ relay"
        )
        val parsedIce = CallSignal.fromJson(ice.toJson()) as? CallSignal.IceCandidate
        assertNotNull(parsedIce)
        assertEquals("call-ice-1", parsedIce?.callId)
        assertEquals("recipient-peer", parsedIce?.recipientId)
        assertEquals("audio", parsedIce?.sdpMid)
        assertEquals(0, parsedIce?.sdpMLineIndex)
        assertEquals("candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ relay", parsedIce?.sdpCandidate)
    }
}
