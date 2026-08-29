package org.ciphrchat.app.blockchain

/**
 * Strict security policy preventing interaction with production mainnets.
 * Enforces testnet-only decentralized discovery to prevent real-fund loss or accidental mainnet transactions.
 */
object MainnetProtectionPolicy {

    /**
     * Validates that the target network is a supported testnet and NOT a forbidden production mainnet.
     * @param chainId The EVM Chain ID.
     * @throws SecurityException if the chain is a forbidden mainnet or not an approved testnet.
     */
    fun validateChainId(chainId: Long) {
        if (BlockchainConfig.FORBIDDEN_MAINNET_CHAINS.contains(chainId)) {
            throw SecurityException(
                "Mainnet interaction is strictly prohibited for decentralized relay discovery. Chain ID $chainId is a production mainnet."
            )
        }
        if (!BlockchainConfig.SUPPORTED_TESTNET_CHAINS.contains(chainId)) {
            throw IllegalArgumentException(
                "Unsupported chain ID $chainId. Only approved testnets (Sepolia, Arbitrum Sepolia, Local) are supported."
            )
        }
    }

    /**
     * Returns true if the chainId is a valid testnet, false otherwise.
     */
    fun isAllowedTestnet(chainId: Long): Boolean {
        return !BlockchainConfig.FORBIDDEN_MAINNET_CHAINS.contains(chainId) &&
                BlockchainConfig.SUPPORTED_TESTNET_CHAINS.contains(chainId)
    }
}
