package org.ciphrchat.app.privacy

import org.ciphrchat.app.transport.TransportKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPrivacyPolicyTest {

    @Test
    fun blocksInternetDirectWhenPrivacyModeIsEnabled() {
        assertFalse(
            "INTERNET_DIRECT must be blocked when IP privacy is ON",
            IpPrivacyPolicy.isTransportAllowed(TransportKind.INTERNET_DIRECT, isIpPrivacyEnabled = true)
        )
    }

    @Test
    fun allowsInternetRelayAndLocalTransportsWhenPrivacyModeIsEnabled() {
        val allowedKinds = listOf(
            TransportKind.INTERNET_RELAY,
            TransportKind.WIFI_LAN,
            TransportKind.WIFI_DIRECT,
            TransportKind.WIFI_AWARE,
            TransportKind.BLUETOOTH_DIRECT,
            TransportKind.BLUETOOTH_MESH,
            TransportKind.ULTRASOUND,
            TransportKind.NFC_PAIRING,
            TransportKind.UWB_ASSIST
        )

        for (kind in allowedKinds) {
            assertTrue(
                "Transport $kind must be allowed when IP privacy is ON",
                IpPrivacyPolicy.isTransportAllowed(kind, isIpPrivacyEnabled = true)
            )
        }
    }

    @Test
    fun allowsAllTransportsWhenPrivacyModeIsDisabled() {
        for (kind in TransportKind.values()) {
            assertTrue(
                "Transport $kind must be allowed when IP privacy is OFF",
                IpPrivacyPolicy.isTransportAllowed(kind, isIpPrivacyEnabled = false)
            )
        }
    }

    @Test
    fun filtersIceCandidatesInPrivacyMode() {
        val hostCandidate = "candidate:1 1 UDP 2122252543 192.168.1.50 54321 typ host"
        val srflxCandidate = "candidate:2 1 UDP 1686052863 203.0.113.10 54321 typ srflx raddr 192.168.1.50 rport 54321"
        val relayCandidate = "candidate:3 1 UDP 41819903 198.51.100.24 3478 typ relay raddr 203.0.113.10 rport 54321"

        // When Privacy Mode is ON:
        assertFalse("Host candidate must be stripped in privacy mode", IpPrivacyPolicy.filterIceCandidate(hostCandidate, true))
        assertFalse("Srflx candidate must be stripped in privacy mode", IpPrivacyPolicy.filterIceCandidate(srflxCandidate, true))
        assertTrue("Relay candidate must be retained in privacy mode", IpPrivacyPolicy.filterIceCandidate(relayCandidate, true))

        // When Privacy Mode is OFF:
        assertTrue(IpPrivacyPolicy.filterIceCandidate(hostCandidate, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(srflxCandidate, false))
        assertTrue(IpPrivacyPolicy.filterIceCandidate(relayCandidate, false))
    }
}
