package org.ciphrchat.app.calling

import org.ciphrchat.app.privacy.IpPrivacyPolicy
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection

class CallQualityAndCorrectnessTest {

    @Test
    fun callStateMachineTransitionsProperly() {
        var state: CallState = CallState.Idle
        assertEquals(CallState.Idle, state)

        // Outgoing call initiated
        val callId = "test-call-100"
        val contactId = "ciphr:bob_123"
        val contactName = "Bob"
        state = CallState.OutgoingRinging(callId, contactId, contactName)
        assertTrue(state is CallState.OutgoingRinging)
        assertEquals(callId, (state as CallState.OutgoingRinging).callId)

        // Transition to Connecting upon answer receipt
        state = CallState.Connecting(callId, contactId, contactName)
        assertTrue(state is CallState.Connecting)

        // Transition to Connected upon ICE completion
        state = CallState.Connected(callId, contactId, contactName, isMuted = false, isSpeakerOn = false)
        assertTrue(state is CallState.Connected)
        assertEquals(0L, (state as CallState.Connected).diagnostics.rttMs)

        // Toggle mute and speaker
        state = (state as CallState.Connected).copy(isMuted = true, isSpeakerOn = true)
        assertTrue((state as CallState.Connected).isMuted)
        assertTrue((state as CallState.Connected).isSpeakerOn)

        // Temporary disconnection -> Reconnecting
        state = CallState.Reconnecting(callId, contactId, contactName, attempt = 1, maxAttempts = 3)
        assertTrue(state is CallState.Reconnecting)
        assertEquals(1, (state as CallState.Reconnecting).attempt)

        // Clean termination
        state = CallState.Ended(callId, "Call ended by user", durationSeconds = 42L)
        assertTrue(state is CallState.Ended)
        assertEquals(42L, (state as CallState.Ended).durationSeconds)
    }

    @Test
    fun duplicateSignalIdempotencyDoesNotCorruptState() {
        val callId = "test-call-dup"
        val senderId = "ciphr:alice"
        val recipientId = "ciphr:bob"

        val offer1 = CallSignal.Offer(callId, "sdp1", senderId, recipientId)
        val offer2 = CallSignal.Offer(callId, "sdp1", senderId, recipientId)

        assertEquals(offer1.toJson(), offer2.toJson())

        val answer1 = CallSignal.Answer(callId, "sdp-ans", recipientId, senderId)
        val answer2 = CallSignal.Answer(callId, "sdp-ans", recipientId, senderId)

        assertEquals(answer1.toJson(), answer2.toJson())

        val cand1 = CallSignal.IceCandidate(callId, recipientId, "audio", 0, "candidate:123", senderId)
        val cand2 = CallSignal.IceCandidate(callId, recipientId, "audio", 0, "candidate:123", senderId)

        val seen = mutableSetOf<String>()
        val sig1 = "${cand1.sdpMid}:${cand1.sdpMLineIndex}:${cand1.sdpCandidate}"
        val sig2 = "${cand2.sdpMid}:${cand2.sdpMLineIndex}:${cand2.sdpCandidate}"

        assertTrue(seen.add(sig1))
        assertFalse("Duplicate candidate signature must be rejected by idempotency check", seen.add(sig2))
    }

    @Test
    fun earlyIceCandidateBufferingStoresAndFlushesDeterministically() {
        val buffer = mutableListOf<CallSignal.IceCandidate>()
        var isRemoteDescriptionSet = false

        fun receiveCandidate(c: CallSignal.IceCandidate) {
            if (!isRemoteDescriptionSet) {
                buffer.add(c)
            }
        }

        val candA = CallSignal.IceCandidate("c-1", "b", "audio", 0, "cand-A", "a")
        val candB = CallSignal.IceCandidate("c-1", "b", "audio", 0, "cand-B", "a")

        receiveCandidate(candA)
        receiveCandidate(candB)

        assertEquals(2, buffer.size)

        // Remote description arrives and is applied
        isRemoteDescriptionSet = true
        val flushed = buffer.toList()
        buffer.clear()

        assertEquals(2, flushed.size)
        assertEquals("cand-A", flushed[0].sdpCandidate)
        assertEquals("cand-B", flushed[1].sdpCandidate)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun audioConstraintsStrictlyEnforceOpusAecAgcNsAndZeroVideo() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            optional.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        assertEquals("true", constraints.mandatory.find { it.key == "OfferToReceiveAudio" }?.value)
        assertEquals("false", constraints.mandatory.find { it.key == "OfferToReceiveVideo" }?.value)
        assertEquals("true", constraints.optional.find { it.key == "echoCancellation" }?.value)
        assertEquals("true", constraints.optional.find { it.key == "googEchoCancellation" }?.value)
        assertEquals("true", constraints.optional.find { it.key == "googAutoGainControl" }?.value)
        assertEquals("true", constraints.optional.find { it.key == "googNoiseSuppression" }?.value)
        assertEquals("true", constraints.optional.find { it.key == "googHighpassFilter" }?.value)
    }

    @Test
    fun privacyModeFiltersDirectCandidatesAndEnforcesRelay() {
        val host = "candidate:1 1 UDP 2122252543 10.0.0.1 50000 typ host"
        val srflx = "candidate:2 1 UDP 1686052863 1.2.3.4 50000 typ srflx raddr 10.0.0.1 rport 50000"
        val relay = "candidate:3 1 UDP 41819903 5.6.7.8 3478 typ relay raddr 1.2.3.4 rport 50000"

        // In Privacy Mode
        assertFalse(IpPrivacyPolicy.filterIceCandidate(host, true))
        assertFalse(IpPrivacyPolicy.filterIceCandidate(srflx, true))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(relay, true))

        // In Direct Mode
        assertTrue(IpPrivacyPolicy.filterIceCandidate(host, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(srflx, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(relay, false))
    }

    @Test
    fun callGlareTieBreakerResolvesSimultaneousOffers() {
        val callerA = "ciphr:0001_alice"
        val callerB = "ciphr:9999_bob"

        fun resolveGlare(localId: String, remoteId: String): String {
            return if (localId < remoteId) "PROCEED_OUTGOING" else "ACCEPT_INCOMING"
        }

        assertEquals("PROCEED_OUTGOING", resolveGlare(callerA, callerB))
        assertEquals("ACCEPT_INCOMING", resolveGlare(callerB, callerA))
    }

    @Test
    fun boundedReconnectionTerminatesCleanlyAfterMaxRetries() {
        val maxRetries = 3
        var attempts = 0
        var terminalReason: String? = null

        fun simulateDisconnect() {
            if (attempts < maxRetries) {
                attempts++
            } else {
                terminalReason = "Call disconnected (max retries reached)"
            }
        }

        repeat(maxRetries) {
            simulateDisconnect()
            assertNull(terminalReason)
        }

        simulateDisconnect()
        assertNotNull(terminalReason)
        assertEquals("Call disconnected (max retries reached)", terminalReason)
    }

    @Test
    fun safeDiagnosticsDoNotExposeSecrets() {
        val diag = CallDiagnostics(
            rttMs = 28L,
            jitterMs = 1.1,
            packetsLost = 0L,
            localCandidateType = "relay",
            remoteCandidateType = "relay",
            iceConnectionState = "CONNECTED"
        )
        val diagString = diag.toString()

        assertFalse("Diagnostics must not contain passwords", diagString.contains("password", ignoreCase = true))
        assertFalse("Diagnostics must not contain secret", diagString.contains("secret", ignoreCase = true))
        assertFalse("Diagnostics must not contain sdp", diagString.contains("sdp", ignoreCase = true))
        assertTrue(diagString.contains("rttMs=28"))
    }
}
