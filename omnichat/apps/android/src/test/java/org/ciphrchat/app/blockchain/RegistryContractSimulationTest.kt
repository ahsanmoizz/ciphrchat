package org.ciphrchat.app.blockchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Simulates on-chain state transitions of CiphrRelayRegistry.sol and verifies deterministic ABI decoding.
 */
class RegistryContractSimulationTest {

    private data class SimulatedRelay(
        val owner: String,
        val multiaddr: String,
        val capacity: Long,
        val registeredAt: Long,
        var active: Boolean
    )

    private class SimulatedRegistry(val chainId: Long) {
        val relays = mutableMapOf<String, SimulatedRelay>()
        val relayOwners = mutableListOf<String>()
        val identityAnchors = mutableMapOf<String, Long>()

        init {
            MainnetProtectionPolicy.validateChainId(chainId)
        }

        fun registerRelay(caller: String, multiaddr: String, capacity: Long, timestamp: Long) {
            val validatedAddr = RelayDiscoveryParser.parseMultiaddress(multiaddr)
            if (!relays.containsKey(caller)) {
                relayOwners.add(caller)
            }
            relays[caller] = SimulatedRelay(caller, validatedAddr, capacity, timestamp, active = true)
        }

        fun setRelayStatus(caller: String, active: Boolean) {
            val relay = relays[caller] ?: throw IllegalStateException("RelayNotFound")
            relay.active = active
        }

        fun anchorIdentity(identityHashHex: String, timestamp: Long) {
            require(identityHashHex.length == 64) { "Invalid identity hash length" }
            identityAnchors[identityHashHex] = timestamp
        }

        fun getActiveRelays(offset: Int, limit: Int): List<SimulatedRelay> {
            val active = relayOwners.mapNotNull { relays[it] }.filter { it.active }
            if (offset >= active.size || limit <= 0) return emptyList()
            return active.drop(offset).take(limit)
        }
    }

    @Test
    fun testRegistrationAndQueryFlow() {
        val registry = SimulatedRegistry(BlockchainConfig.CHAIN_BASE_SEPOLIA)
        val relay1Addr = "/ip4/198.51.100.1/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN"
        val relay2Addr = "/dns4/relay2.ciphrchat.org/tcp/4001/p2p/12D3KooWTestPeer222222222222222222222222222222222222"

        registry.registerRelay("0xOwner1", relay1Addr, 1000, 1000000L)
        registry.registerRelay("0xOwner2", relay2Addr, 500, 1000500L)

        val active = registry.getActiveRelays(0, 10)
        assertEquals(2, active.size)
        assertEquals(relay1Addr, active[0].multiaddr)
        assertEquals(relay2Addr, active[1].multiaddr)

        // Deactivate owner 1
        registry.setRelayStatus("0xOwner1", false)
        val activeAfter = registry.getActiveRelays(0, 10)
        assertEquals(1, activeAfter.size)
        assertEquals(relay2Addr, activeAfter[0].multiaddr)
    }

    @Test
    fun testIdentityAnchoringAndVerification() {
        val registry = SimulatedRegistry(BlockchainConfig.CHAIN_BASE_SEPOLIA)
        val commitment = IdentityHashPrivacy.createBlindedCommitment("ciphr:8f4c2e10a9b3d5e71234567890abcdef")

        registry.anchorIdentity(commitment.identityHashHex, 1700000000L)
        assertTrue(registry.identityAnchors.containsKey(commitment.identityHashHex))
        assertFalse(registry.identityAnchors.containsKey("00".repeat(32)))
    }

    @Test
    fun testAbiDecoderWithSingleRelayFixture() {
        val service = BlockchainRegistryService()
        val singleRelay = BlockchainRegistryService.DecodedRelay(
            owner = "0x1111111111111111111111111111111111111111",
            multiaddr = "/ip4/198.51.100.1/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXTN",
            capacity = 1000L,
            registeredAt = 1700000000L,
            active = true
        )

        val abiHex = buildRelayArrayAbiHex(listOf(singleRelay))
        val decoded = service.decodeRelayInfoArray(abiHex)

        assertEquals(1, decoded.size)
        assertEquals(singleRelay.owner.lowercase(), decoded[0].owner.lowercase())
        assertEquals(singleRelay.multiaddr, decoded[0].multiaddr)
        assertEquals(singleRelay.capacity, decoded[0].capacity)
        assertEquals(singleRelay.registeredAt, decoded[0].registeredAt)
        assertTrue(decoded[0].active)
    }

    @Test
    fun testAbiDecoderWithMultipleRelaysAndInactiveFlag() {
        val service = BlockchainRegistryService()
        val relay1 = BlockchainRegistryService.DecodedRelay(
            owner = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            multiaddr = "/ip4/10.0.0.1/tcp/4001/p2p/12D3KooWTestPeer111111111111111111111111111111111111",
            capacity = 500L,
            registeredAt = 1690000000L,
            active = true
        )
        val relay2 = BlockchainRegistryService.DecodedRelay(
            owner = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            multiaddr = "/dns4/relay.ciphrchat.org/tcp/4001/p2p/12D3KooWTestPeer222222222222222222222222222222222222",
            capacity = 2000L,
            registeredAt = 1695000000L,
            active = false
        )

        val abiHex = buildRelayArrayAbiHex(listOf(relay1, relay2))
        val decoded = service.decodeRelayInfoArray(abiHex)

        assertEquals(2, decoded.size)
        assertEquals(relay1.multiaddr, decoded[0].multiaddr)
        assertTrue(decoded[0].active)
        assertEquals(relay2.multiaddr, decoded[1].multiaddr)
        assertFalse(decoded[1].active)
    }

    @Test
    fun testAbiDecoderWithEmptyArrayAndMalformedHex() {
        val service = BlockchainRegistryService()
        val emptyHex = buildRelayArrayAbiHex(emptyList())
        val decodedEmpty = service.decodeRelayInfoArray(emptyHex)
        assertTrue("Empty array ABI must return empty list", decodedEmpty.isEmpty())

        val malformed = service.decodeRelayInfoArray("0x1234")
        assertTrue("Malformed hex must return empty list safely", malformed.isEmpty())

        val emptyStr = service.decodeRelayInfoArray("")
        assertTrue(emptyStr.isEmpty())
    }

    private fun buildRelayArrayAbiHex(relays: List<BlockchainRegistryService.DecodedRelay>): String {
        // Word 0: Offset to array (0x20 = 32)
        val words = mutableListOf<String>()
        words.add(pad64(32L)) // array offset

        // Word 1: Length of array (N)
        words.add(pad64(relays.size.toLong()))

        if (relays.isEmpty()) {
            return "0x" + words.joinToString("")
        }

        // Next N words: relative offsets to each struct from start of array data (i.e. from word 1 position)
        // Array data starts at offset 32. Offsets table has size N * 32 bytes.
        // First struct starts at offset N * 32 bytes.
        var currentStructRelativeOffset = relays.size * 32L
        val structHexBlobs = mutableListOf<String>()

        for (relay in relays) {
            words.add(pad64(currentStructRelativeOffset))

            // Struct layout:
            // 0: owner address (32 bytes)
            // 32: offset of string relative to struct start = 160 (0xa0)
            // 64: capacity (32 bytes)
            // 96: registeredAt (32 bytes)
            // 128: active (32 bytes)
            // 160: string length (32 bytes)
            // 192: string bytes (padded to multiple of 32 bytes)
            val ownerHex = relay.owner.removePrefix("0x").padStart(64, '0')
            val strOffsetHex = pad64(160L)
            val capHex = pad64(relay.capacity)
            val regHex = pad64(relay.registeredAt)
            val actHex = pad64(if (relay.active) 1L else 0L)

            val strBytes = relay.multiaddr.toByteArray(Charsets.UTF_8)
            val strLenHex = pad64(strBytes.size.toLong())
            val paddedStrBytesHex = strBytes.joinToString("") { "%02x".format(it) }
                .padEnd(((strBytes.size + 31) / 32) * 64, '0')

            val structHex = ownerHex + strOffsetHex + capHex + regHex + actHex + strLenHex + paddedStrBytesHex
            structHexBlobs.add(structHex)
            currentStructRelativeOffset += structHex.length / 2
        }

        return "0x" + words.joinToString("") + structHexBlobs.joinToString("")
    }

    private fun pad64(value: Long): String {
        return java.lang.Long.toHexString(value).padStart(64, '0')
    }
}
