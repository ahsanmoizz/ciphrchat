package org.ciphrchat.app.blockchain

/**
 * Configuration and network parameters for decentralized relay discovery and identity anchoring.
 */
object BlockchainConfig {
    // Chain IDs
    const val CHAIN_ETHEREUM_MAINNET = 1L
    const val CHAIN_BSC_MAINNET = 56L
    const val CHAIN_POLYGON_MAINNET = 137L
    const val CHAIN_ARBITRUM_ONE = 42161L
    const val CHAIN_OPTIMISM_MAINNET = 10L
    const val CHAIN_BASE_MAINNET = 8453L

    // Supported Testnet Chain IDs
    const val CHAIN_BASE_SEPOLIA = 84532L
    const val CHAIN_SEPOLIA = 11155111L
    const val CHAIN_ARBITRUM_SEPOLIA = 421614L
    const val CHAIN_LOCAL_HARDHAT = 31337L
    const val CHAIN_LOCAL_GANACHE = 1337L

    val FORBIDDEN_MAINNET_CHAINS = setOf(
        CHAIN_ETHEREUM_MAINNET,
        CHAIN_BSC_MAINNET,
        CHAIN_POLYGON_MAINNET,
        CHAIN_ARBITRUM_ONE,
        CHAIN_OPTIMISM_MAINNET,
        CHAIN_BASE_MAINNET
    )

    val SUPPORTED_TESTNET_CHAINS = setOf(
        CHAIN_BASE_SEPOLIA,
        CHAIN_SEPOLIA,
        CHAIN_ARBITRUM_SEPOLIA,
        CHAIN_LOCAL_HARDHAT,
        CHAIN_LOCAL_GANACHE
    )

    // Contract function 4-byte selectors (Keccak-256)
    // registerRelay(string,uint256) => 0x9c313271
    const val SELECTOR_REGISTER_RELAY = "9c313271"
    // setRelayStatus(bool) => 0x4858dae2
    const val SELECTOR_SET_RELAY_STATUS = "4858dae2"
    // anchorIdentity(bytes32) => 0x8b5b75ff
    const val SELECTOR_ANCHOR_IDENTITY = "8b5b75ff"
    // verifyIdentityAnchor(bytes32) => 0x41e8ef04
    const val SELECTOR_VERIFY_IDENTITY_ANCHOR = "41e8ef04"
    // getActiveRelays(uint256,uint256) => 0x3d30b925
    const val SELECTOR_GET_ACTIVE_RELAYS = "3d30b925"
    // getRelayCount() => 0x2170366a
    const val SELECTOR_GET_RELAY_COUNT = "2170366a"

    // Default Base Sepolia Testnet parameters (placeholder until official deployment)
    const val DEFAULT_BASE_SEPOLIA_REGISTRY_ADDRESS = "0x0000000000000000000000000000000000000000"
    const val DEFAULT_BASE_SEPOLIA_RPC_URL = "https://sepolia.base.org"
}
