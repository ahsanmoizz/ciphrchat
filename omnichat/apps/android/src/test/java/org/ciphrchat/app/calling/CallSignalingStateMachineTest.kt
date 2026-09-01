package org.ciphrchat.app.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            recipientId = "recipient-peer",
            isIceRestart = false
        )
        val jsonStr = offer.toJson()
        val parsed = CallSignal.fromJson(jsonStr) as? CallSignal.Offer

        assertNotNull(parsed)
        assertEquals(offer.callId, parsed?.callId)
        assertEquals(offer.sdp, parsed?.sdp)
        assertEquals(offer.senderId, parsed?.senderId)
        assertEquals(offer.recipientId, parsed?.recipientId)
        assertFalse(parsed?.isIceRestart ?: true)
    }

    @Test
    fun serializesAndDeserializesIceRestartOffer() {
        val offer = CallSignal.Offer(
            callId = "call-restart-1",
            sdp = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n",
            senderId = "sender-peer",
            recipientId = "recipient-peer",
            isIceRestart = true
        )
        val parsed = CallSignal.fromJson(offer.toJson()) as? CallSignal.Offer
        assertNotNull(parsed)
        assertTrue(parsed?.isIceRestart == true)
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
            sdpCandidate = "candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ relay",
            senderId = "sender-peer"
        )
        val parsedIce = CallSignal.fromJson(ice.toJson()) as? CallSignal.IceCandidate
        assertNotNull(parsedIce)
        assertEquals("call-ice-1", parsedIce?.callId)
        assertEquals("recipient-peer", parsedIce?.recipientId)
        assertEquals("audio", parsedIce?.sdpMid)
        assertEquals(0, parsedIce?.sdpMLineIndex)
        assertEquals("candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ relay", parsedIce?.sdpCandidate)
        assertEquals("sender-peer", parsedIce?.senderId)
    }

    @Test
    fun verifiesIceCandidateBufferingOrderPreserved() {
        // Simulates early ICE candidate arrival before remote description is set
        val candidateBuffer = mutableListOf<CallSignal.IceCandidate>()
        val seenSignatures = mutableSetOf<String>()

        fun bufferCandidate(c: CallSignal.IceCandidate) {
            val sig = "${c.sdpMid}:${c.sdpMLineIndex}:${c.sdpCandidate}"
            if (seenSignatures.add(sig)) {
                candidateBuffer.add(c)
            }
        }

        val cand1 = CallSignal.IceCandidate("call-1", "peer-b", "audio", 0, "candidate:1 ...", "peer-a")
        val cand2 = CallSignal.IceCandidate("call-1", "peer-b", "audio", 0, "candidate:2 ...", "peer-a")
        val candDup = CallSignal.IceCandidate("call-1", "peer-b", "audio", 0, "candidate:1 ...", "peer-a")

        bufferCandidate(cand1)
        bufferCandidate(cand2)
        bufferCandidate(candDup)

        assertEquals(2, candidateBuffer.size)
        assertEquals("candidate:1 ...", candidateBuffer[0].sdpCandidate)
        assertEquals("candidate:2 ...", candidateBuffer[1].sdpCandidate)

        // Drain on remote description set
        val flushed = candidateBuffer.toList()
        candidateBuffer.clear()

        assertEquals(2, flushed.size)
        assertTrue(candidateBuffer.isEmpty())
    }

    @Test
    fun verifiesCallGlareDeterministicTieBreaking() {
        val aliceId = "ciphr:alice_0123"
        val bobId = "ciphr:bob_4567"

        // Rule: Lower lexicographical public ID has precedence in glare collision
        val aliceWins = aliceId < bobId
        assertTrue("Alice ID is lexicographically lower than Bob ID", aliceWins)

        // When Alice receives Bob's offer while Alice is already calling Bob:
        // Alice has priority -> Alice rejects Bob's offer with Glare reason
        val aliceAction = if (aliceId < bobId) "REJECT_INCOMING_GLARE" else "ACCEPT_INCOMING_GLARE"
        assertEquals("REJECT_INCOMING_GLARE", aliceAction)

        // When Bob receives Alice's offer while Bob is calling Alice:
        // Bob yields priority -> Bob cancels local outgoing call and accepts Alice's offer
        val bobAction = if (bobId < aliceId) "REJECT_INCOMING_GLARE" else "ACCEPT_INCOMING_GLARE"
        assertEquals("ACCEPT_INCOMING_GLARE", bobAction)
    }

    @Test
    fun verifiesBoundedReconnectionInvariants() {
        val maxAttempts = 3
        var currentAttempt = 0
        var callState: CallState = CallState.Connected("call-1", "peer-b", "Bob")

        fun onDisconnect() {
            if (currentAttempt < maxAttempts) {
                currentAttempt++
                callState = CallState.Reconnecting("call-1", "peer-b", "Bob", currentAttempt, maxAttempts)
            } else {
                callState = CallState.Ended("call-1", "Call disconnected (max retries reached)", 0L)
            }
        }

        // Attempt 1
        onDisconnect()
        assertTrue(callState is CallState.Reconnecting)
        assertEquals(1, (callState as CallState.Reconnecting).attempt)

        // Attempt 2
        onDisconnect()
        assertEquals(2, (callState as CallState.Reconnecting).attempt)

        // Attempt 3
        onDisconnect()
        assertEquals(3, (callState as CallState.Reconnecting).attempt)

        // Exceeded -> Clean termination
        onDisconnect()
        assertTrue(callState is CallState.Ended)
        assertEquals("Call disconnected (max retries reached)", (callState as CallState.Ended).reason)
    }

    @Test
    fun verifiesCallDiagnosticsStructure() {
        val diag = CallDiagnostics(
            rttMs = 45L,
            jitterMs = 2.4,
            packetsLost = 0L,
            localCandidateType = "relay",
            remoteCandidateType = "relay",
            iceConnectionState = "CONNECTED"
        )
        val connectedState = CallState.Connected(
            callId = "call-diag-1",
            contactId = "peer-b",
            contactName = "Bob",
            diagnostics = diag
        )

        assertEquals(45L, connectedState.diagnostics.rttMs)
        assertEquals(2.4, connectedState.diagnostics.jitterMs, 0.001)
        assertEquals("relay", connectedState.diagnostics.localCandidateType)
        assertEquals("relay", connectedState.diagnostics.remoteCandidateType)
        assertEquals("CONNECTED", connectedState.diagnostics.iceConnectionState)
    }
}

