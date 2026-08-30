# CiphrChat Decentralized Relay Registry (Base Sepolia)

This smart contract (`CiphrRelayRegistry.sol`) provides permissionless bootstrap relay discovery and blinded identity anchoring on **Base Sepolia Testnet** (Chain ID: `84532`).

## Security Constraints
- **Mainnet Protection**: The smart contract enforces `onlyTestnet` guard and explicitly reverts if executed on mainnet chain IDs (`1`, `56`, `137`, `42161`, `10`, `8453`).
- **Privacy**: Identity anchors store only blinded hashes `SHA-256(identity || salt)` to prevent identity enumeration.

## Deployment Instructions

### Prerequisites
- Base Sepolia testnet ETH from a faucet
- RPC endpoint (default: `https://sepolia.base.org`)
- Foundry (`forge`) or Hardhat

### Deployment
```bash
export RPC_URL="https://sepolia.base.org"
export PRIVATE_KEY="0x<YOUR_BASE_SEPOLIA_DEPLOYER_PRIVATE_KEY>"

forge create contracts/CiphrRelayRegistry.sol:CiphrRelayRegistry \
  --rpc-url "$RPC_URL" \
  --private-key "$PRIVATE_KEY" \
  --chain 84532
```

### Post-Deployment Registration
Once deployed at `<CONTRACT_ADDRESS>`:

1. **Register Relay Multiaddress**:
```bash
cast send <CONTRACT_ADDRESS> \
  "registerRelay(string,uint256)" \
  "/ip4/<VPS_PUBLIC_IP>/tcp/4001/p2p/<RELAY_PEER_ID>" 1000 \
  --rpc-url "$RPC_URL" \
  --private-key "$PRIVATE_KEY"
```

2. **Verify Active Relays**:
```bash
cast call <CONTRACT_ADDRESS> "getRelayCount()" --rpc-url "$RPC_URL"
cast call <CONTRACT_ADDRESS> "getActiveRelays(uint256,uint256)" 0 10 --rpc-url "$RPC_URL"
```
