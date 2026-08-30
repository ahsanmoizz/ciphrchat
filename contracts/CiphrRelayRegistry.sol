// SPDX-License-Identifier: AGPL-3.0
pragma solidity ^0.8.20;

/**
 * @title CiphrRelayRegistry
 * @notice Decentralized relay discovery registry and privacy-preserving identity anchoring for CiphrChat.
 * @dev Rejects Ethereum Mainnet and other production mainnets to enforce testnet-only operation.
 */
contract CiphrRelayRegistry {
    error MainnetNotSupported(uint256 chainId);
    error InvalidMultiaddress();
    error Unauthorized();
    error RelayNotFound();
    error InvalidIdentityHash();

    // Known forbidden Mainnet Chain IDs
    uint256 public constant ETHEREUM_MAINNET = 1;
    uint256 public constant BSC_MAINNET = 56;
    uint256 public constant POLYGON_MAINNET = 137;
    uint256 public constant ARBITRUM_ONE = 42161;
    uint256 public constant OPTIMISM_MAINNET = 10;
    uint256 public constant BASE_MAINNET = 8453;

    struct RelayInfo {
        address owner;
        string multiaddr;
        uint256 capacity;
        uint256 registeredAt;
        bool active;
    }

    struct IdentityAnchor {
        bytes32 identityHash;
        uint256 anchoredAt;
    }

    mapping(address => RelayInfo) public relays;
    address[] public relayOwners;
    mapping(bytes32 => IdentityAnchor) public identityAnchors;

    event RelayRegistered(address indexed owner, string multiaddr, uint256 capacity);
    event RelayStatusUpdated(address indexed owner, bool active);
    event IdentityAnchored(bytes32 indexed identityHash, uint256 timestamp);

    constructor() {
        _enforceTestnetOnly();
    }

    modifier onlyTestnet() {
        _enforceTestnetOnly();
        _;
    }

    function _enforceTestnetOnly() internal view {
        uint256 chainId = block.chainid;
        if (
            chainId == ETHEREUM_MAINNET ||
            chainId == BSC_MAINNET ||
            chainId == POLYGON_MAINNET ||
            chainId == ARBITRUM_ONE ||
            chainId == OPTIMISM_MAINNET ||
            chainId == BASE_MAINNET
        ) {
            revert MainnetNotSupported(chainId);
        }
    }

    /**
     * @notice Registers or updates a bootstrap relay.
     * @param multiaddr The libp2p multiaddress string (e.g. /ip4/1.2.3.4/tcp/4001/p2p/12D3KooW...)
     * @param capacity The concurrent connection capacity.
     */
    function registerRelay(string calldata multiaddr, uint256 capacity) external onlyTestnet {
        bytes memory rawAddr = bytes(multiaddr);
        if (rawAddr.length < 16 || rawAddr.length > 512) {
            revert InvalidMultiaddress();
        }

        if (relays[msg.sender].owner == address(0)) {
            relayOwners.push(msg.sender);
        }

        relays[msg.sender] = RelayInfo({
            owner: msg.sender,
            multiaddr: multiaddr,
            capacity: capacity,
            registeredAt: block.timestamp,
            active: true
        });

        emit RelayRegistered(msg.sender, multiaddr, capacity);
    }

    /**
     * @notice Updates the active status of the caller's relay.
     */
    function setRelayStatus(bool active) external onlyTestnet {
        if (relays[msg.sender].owner == address(0)) {
            revert RelayNotFound();
        }
        relays[msg.sender].active = active;
        emit RelayStatusUpdated(msg.sender, active);
    }

    /**
     * @notice Anchors a blinded identity hash for cryptographic presence without revealing identity public keys or IP addresses.
     * @param identityHash The keccak256 or SHA-256 blinded hash commitment of the identity.
     */
    function anchorIdentity(bytes32 identityHash) external onlyTestnet {
        if (identityHash == bytes32(0)) {
            revert InvalidIdentityHash();
        }
        identityAnchors[identityHash] = IdentityAnchor({
            identityHash: identityHash,
            anchoredAt: block.timestamp
        });
        emit IdentityAnchored(identityHash, block.timestamp);
    }

    /**
     * @notice Verifies if an identity hash has been anchored.
     */
    function verifyIdentityAnchor(bytes32 identityHash) external view returns (bool exists, uint256 anchoredAt) {
        IdentityAnchor memory anchor = identityAnchors[identityHash];
        return (anchor.anchoredAt > 0, anchor.anchoredAt);
    }

    /**
     * @notice Returns total registered relays.
     */
    function getRelayCount() external view returns (uint256) {
        return relayOwners.length;
    }

    /**
     * @notice Returns active relays with pagination.
     */
    function getActiveRelays(uint256 offset, uint256 limit) external view returns (RelayInfo[] memory) {
        uint256 total = relayOwners.length;
        if (offset >= total || limit == 0) {
            return new RelayInfo[](0);
        }

        uint256 end = offset + limit;
        if (end > total) {
            end = total;
        }

        uint256 count = 0;
        for (uint256 i = offset; i < end; i++) {
            if (relays[relayOwners[i]].active) {
                count++;
            }
        }

        RelayInfo[] memory activeList = new RelayInfo[](count);
        uint256 index = 0;
        for (uint256 i = offset; i < end; i++) {
            RelayInfo memory info = relays[relayOwners[i]];
            if (info.active) {
                activeList[index++] = info;
            }
        }

        return activeList;
    }
}
