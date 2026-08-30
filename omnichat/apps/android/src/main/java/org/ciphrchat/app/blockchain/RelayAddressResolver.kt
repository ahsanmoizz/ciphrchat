package org.ciphrchat.app.blockchain

import org.ciphrchat.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the active relay multiaddress for Internet transport startup:
 * 1. Validates Base Sepolia testnet parameters (MainnetProtectionPolicy).
 * 2. Queries the CiphrRelayRegistry smart contract on Base Sepolia for active relays.
 * 3. Selects the best active discovered relay with highest capacity.
 * 4. Falls back to the compiled static BuildConfig.CIPHRCHAT_RELAY_ADDRESS if blockchain is
 *    unavailable, empty, or returns an error.
 */
@Singleton
class RelayAddressResolver @Inject constructor(
    private val blockchainService: BlockchainRegistryService
) {
    suspend fun resolveRelayAddress(): String {
        val staticFallback = BuildConfig.CIPHRCHAT_RELAY_ADDRESS

        val rpcUrl = BuildConfig.CIPHRCHAT_BLOCKCHAIN_RPC_URL
        val contractAddress = BuildConfig.CIPHRCHAT_REGISTRY_CONTRACT_ADDRESS

        // If blockchain configuration is not set, use static fallback immediately
        if (rpcUrl.isBlank() && contractAddress.isBlank()) {
            return staticFallback
        }

        val discoveredResult = runCatching {
            blockchainService.getActiveRelays()
        }.getOrNull()

        val discoveredRelays = discoveredResult?.getOrNull()
        if (!discoveredRelays.isNullOrEmpty()) {
            val bestRelay = discoveredRelays
                .filter { it.isActive && it.multiaddress.isNotBlank() }
                .maxByOrNull { it.capacity }
            if (bestRelay != null && bestRelay.multiaddress.isNotBlank()) {
                return bestRelay.multiaddress
            }
        }

        return staticFallback
    }
}
