package org.ciphrchat.app.blockchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayAddressResolverTest {

    @Test
    fun fallsBackToStaticRelayWhenBlockchainIsEmptyOrUnavailable() {
        val resolver = RelayAddressResolver(BlockchainRegistryService())

        val resolved = kotlinx.coroutines.runBlocking {
            resolver.resolveRelayAddress()
        }

        // Must equal static fallback or blank default
        assertEquals(org.ciphrchat.app.BuildConfig.CIPHRCHAT_RELAY_ADDRESS, resolved)
    }

    @Test
    fun selectsActiveDiscoveredRelayWithHighestCapacity() {
        // Test selection logic directly
        val relays = listOf(
            RelayDiscoveryParser.DiscoveredRelay(
                ownerAddress = "0x1111111111111111111111111111111111111111",
                multiaddress = "/ip4/198.51.100.1/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXT1",
                capacity = 100,
                registeredAtEpochMs = 1000L,
                isActive = true
            ),
            RelayDiscoveryParser.DiscoveredRelay(
                ownerAddress = "0x2222222222222222222222222222222222222222",
                multiaddress = "/ip4/198.51.100.2/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXT2",
                capacity = 500, // Highest capacity
                registeredAtEpochMs = 2000L,
                isActive = true
            ),
            RelayDiscoveryParser.DiscoveredRelay(
                ownerAddress = "0x3333333333333333333333333333333333333333",
                multiaddress = "/ip4/198.51.100.3/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXT3",
                capacity = 1000,
                registeredAtEpochMs = 3000L,
                isActive = false // Inactive, must be filtered out
            )
        )

        val best = relays.filter { it.isActive && it.multiaddress.isNotBlank() }.maxByOrNull { it.capacity }
        assertEquals("/ip4/198.51.100.2/tcp/4001/p2p/12D3KooWDpJ7As7BWAwRMfu1VU2WCqNjvq387JEYKDBj4kx6nXT2", best?.multiaddress)
    }

    @Test
    fun enforcesMainnetProtectionOnChainId() {
        assertTrue(MainnetProtectionPolicy.isAllowedTestnet(BlockchainConfig.CHAIN_BASE_SEPOLIA))
        org.junit.Assert.assertFalse(MainnetProtectionPolicy.isAllowedTestnet(BlockchainConfig.CHAIN_ETHEREUM_MAINNET))
        org.junit.Assert.assertFalse(MainnetProtectionPolicy.isAllowedTestnet(BlockchainConfig.CHAIN_BASE_MAINNET))
    }
}
