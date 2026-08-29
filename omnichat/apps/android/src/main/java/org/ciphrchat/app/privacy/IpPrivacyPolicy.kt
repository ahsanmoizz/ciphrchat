package org.ciphrchat.app.privacy

import org.ciphrchat.app.transport.TransportKind

/**
 * Enforces IP privacy rules for transport selection and WebRTC ICE candidate emission.
 */
object IpPrivacyPolicy {

    /**
     * Determines whether a transport kind is allowed when IP Privacy mode is enabled.
     * When IP privacy is ON:
     * - INTERNET_DIRECT (direct peer-to-peer IP connection over Internet) is BLOCKED.
     * - INTERNET_RELAY (relayed over VPS circuit/mailbox) is ALLOWED.
     * - Local transports (LAN, BLE, Wi-Fi Direct, NFC, Ultrasound, UWB) are ALLOWED as they operate strictly locally.
     */
    fun isTransportAllowed(kind: TransportKind, isIpPrivacyEnabled: Boolean): Boolean {
        if (!isIpPrivacyEnabled) return true
        return kind != TransportKind.INTERNET_DIRECT
    }

    /**
     * Filters ICE candidate strings when IP Privacy is enabled.
     * Host and server reflexive (srflx/stun) candidates that expose the local or public IP
     * must be stripped; only relay candidates (typ relay) are permitted.
     */
    fun filterIceCandidate(sdpCandidate: String, isIpPrivacyEnabled: Boolean): Boolean {
        if (!isIpPrivacyEnabled) return true
        val lower = sdpCandidate.lowercase()
        // If privacy mode is ON, allow only relay candidates
        return lower.contains("typ relay")
    }
}
