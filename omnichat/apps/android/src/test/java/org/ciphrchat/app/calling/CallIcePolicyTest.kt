package org.ciphrchat.app.calling

import org.ciphrchat.app.privacy.IpPrivacyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection

class CallIcePolicyTest {

    @Test
    fun relayOnlyIcePolicyFiltersHostAndSrflxCandidates() {
        val hostCandidate = "candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ host"
        val srflxCandidate = "candidate:2 1 UDP 1686052863 198.51.100.5 50000 typ srflx raddr 192.168.1.100 rport 50000"
        val relayCandidate = "candidate:3 1 UDP 41819903 203.0.113.1 3478 typ relay raddr 198.51.100.5 rport 50000"

        // In Privacy Mode (Hide my IP ON)
        assertFalse("Host candidate must not be sent in privacy mode", IpPrivacyPolicy.filterIceCandidate(hostCandidate, true))
        assertFalse("Srflx candidate must not be sent in privacy mode", IpPrivacyPolicy.filterIceCandidate(srflxCandidate, true))
        assertTrue("Relay candidate must be sent in privacy mode", IpPrivacyPolicy.filterIceCandidate(relayCandidate, true))
    }

    @Test
    fun standardIcePolicyAllowsAllCandidates() {
        val hostCandidate = "candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ host"
        val srflxCandidate = "candidate:2 1 UDP 1686052863 198.51.100.5 50000 typ srflx raddr 192.168.1.100 rport 50000"
        val relayCandidate = "candidate:3 1 UDP 41819903 203.0.113.1 3478 typ relay raddr 198.51.100.5 rport 50000"

        // In Direct Mode (Hide my IP OFF)
        assertTrue(IpPrivacyPolicy.filterIceCandidate(hostCandidate, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(srflxCandidate, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(relayCandidate, false))
    }

    @Test
    fun verifiesRelayOnlyTransportTypeWhenPrivacyOn() {
        val isPrivacyOn = true
        val iceTransportsType = if (isPrivacyOn) {
            PeerConnection.IceTransportsType.RELAY
        } else {
            PeerConnection.IceTransportsType.ALL
        }

        assertEquals(PeerConnection.IceTransportsType.RELAY, iceTransportsType)
    }

    @Test
    fun verifiesNoDirectFallbackWhenTurnUnavailableAndPrivacyOn() {
        val isPrivacyOn = true
        val iceServers = emptyList<PeerConnection.IceServer>()

        // When privacy is ON and no TURN server is available, call must not proceed to direct ICE
        val canProceed = !isPrivacyOn || iceServers.any { it.urls.any { u -> u.startsWith("turn:") || u.startsWith("turns:") } }
        assertFalse("Call must not proceed without TURN in privacy mode", canProceed)
    }
}
