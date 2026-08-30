package org.ciphrchat.app.blockchain

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RelayDiscoveryParserTest {

    @Test
    fun parsesValidIp4AndDnsMultiaddresses() {
        val validIp4 = "/ip4/198.51.100.24/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN"
        val parsedIp4 = RelayDiscoveryParser.parseMultiaddress(validIp4)
        assertEquals(validIp4, parsedIp4)

        val validDns = "/dns4/relay.ciphrchat.org/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN"
        val parsedDns = RelayDiscoveryParser.parseMultiaddress(validDns)
        assertEquals(validDns, parsedDns)

        val validQuic = "/ip4/198.51.100.24/udp/4001/quic-v1/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN"
        val parsedQuic = RelayDiscoveryParser.parseMultiaddress(validQuic)
        assertEquals(validQuic, parsedQuic)
    }

    @Test
    fun rejectsInvalidMultiaddresses() {
        val invalidMissingPeerId = "/ip4/198.51.100.24/tcp/4001"
        try {
            RelayDiscoveryParser.parseMultiaddress(invalidMissingPeerId)
            fail("Expected exception for multiaddress missing /p2p/")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        val invalidProtocol = "https://relay.ciphrchat.org:4001"
        try {
            RelayDiscoveryParser.parseMultiaddress(invalidProtocol)
            fail("Expected exception for HTTPS URL instead of libp2p multiaddress")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        val invalidNoPort = "/ip4/198.51.100.24/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN"
        try {
            RelayDiscoveryParser.parseMultiaddress(invalidNoPort)
            fail("Expected exception for missing transport port")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
