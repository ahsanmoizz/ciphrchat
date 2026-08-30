package org.ciphrchat.app.blockchain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MainnetProtectionPolicyTest {

    @Test
    fun rejectsForbiddenMainnetChains() {
        val forbidden = listOf(
            BlockchainConfig.CHAIN_ETHEREUM_MAINNET,
            BlockchainConfig.CHAIN_BSC_MAINNET,
            BlockchainConfig.CHAIN_POLYGON_MAINNET,
            BlockchainConfig.CHAIN_ARBITRUM_ONE,
            BlockchainConfig.CHAIN_OPTIMISM_MAINNET,
            BlockchainConfig.CHAIN_BASE_MAINNET
        )

        for (chainId in forbidden) {
            assertFalse("Chain $chainId must not be allowed", MainnetProtectionPolicy.isAllowedTestnet(chainId))
            try {
                MainnetProtectionPolicy.validateChainId(chainId)
                fail("Expected SecurityException for mainnet chain ID $chainId")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("Mainnet interaction is strictly prohibited") == true)
            }
        }
    }

    @Test
    fun acceptsApprovedTestnetsIncludingBaseSepolia() {
        val allowed = listOf(
            BlockchainConfig.CHAIN_BASE_SEPOLIA,
            BlockchainConfig.CHAIN_SEPOLIA,
            BlockchainConfig.CHAIN_ARBITRUM_SEPOLIA,
            BlockchainConfig.CHAIN_LOCAL_HARDHAT,
            BlockchainConfig.CHAIN_LOCAL_GANACHE
        )

        for (chainId in allowed) {
            assertTrue("Testnet $chainId must be allowed", MainnetProtectionPolicy.isAllowedTestnet(chainId))
            MainnetProtectionPolicy.validateChainId(chainId) // Should not throw
        }
    }

    @Test
    fun rejectsUnrecognizedChainIds() {
        val unknown = 999999L
        assertFalse(MainnetProtectionPolicy.isAllowedTestnet(unknown))
        try {
            MainnetProtectionPolicy.validateChainId(unknown)
            fail("Expected IllegalArgumentException for unknown chain ID")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported chain ID") == true)
        }
    }
}
