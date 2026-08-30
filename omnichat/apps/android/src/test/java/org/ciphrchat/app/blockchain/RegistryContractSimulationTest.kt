package org.ciphrchat.app.blockchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates on-chain state transitions of CiphrRelayRegistry.sol to verify registry business logic.
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
        val registry = SimulatedRegistry(BlockchainConfig.CHAIN_SEPOLIA)
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
        val registry = SimulatedRegistry(BlockchainConfig.CHAIN_SEPOLIA)
        val commitment = IdentityHashPrivacy.createBlindedCommitment("ciphr:8f4c2e10a9b3d5e71234567890abcdef")

        registry.anchorIdentity(commitment.identityHashHex, 1700000000L)
        assertTrue(registry.identityAnchors.containsKey(commitment.identityHashHex))
        assertFalse(registry.identityAnchors.containsKey("00".repeat(32)))
    }
}
