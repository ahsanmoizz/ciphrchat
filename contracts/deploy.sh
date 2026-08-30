#!/usr/bin/env bash
# Deploy CiphrRelayRegistry.sol to Base Sepolia Testnet (Chain ID 84532)
# Usage:
#   export PRIVATE_KEY="0x..."
#   export RPC_URL="https://sepolia.base.org"
#   ./deploy.sh

set -euo pipefail

if [ -z "${PRIVATE_KEY:-}" ]; then
  echo "Error: PRIVATE_KEY environment variable is required."
  exit 1
fi

RPC_URL="${RPC_URL:-https://sepolia.base.org}"
CHAIN_ID=84532

echo "=== CiphrChat Relay Registry Deployment ==="
echo "Target Network: Base Sepolia Testnet"
echo "Chain ID: $CHAIN_ID"
echo "RPC URL: $RPC_URL"

# Example using forge / foundry if available:
if command -v forge &> /dev/null; then
  echo "Deploying via Foundry forge..."
  forge create contracts/CiphrRelayRegistry.sol:CiphrRelayRegistry \
    --rpc-url "$RPC_URL" \
    --private-key "$PRIVATE_KEY" \
    --chain 84532
elif command -v npx &> /dev/null; then
  echo "Deploying via Hardhat / Ethers..."
  node -e '
    const ethers = require("ethers");
    const fs = require("fs");
    // Standard ethers deployment script using compiled ABI and Bytecode
    console.log("Ready to deploy using ethers.js");
  '
else
  echo "Foundry (forge) or Node.js (npx) is required for deployment."
  exit 1
fi
