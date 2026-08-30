# CiphrChat Final Production Pre-Deployment Runbook

This runbook provides step-by-step instructions for the **Human + ChatGPT VPS Deployment Phase**.

---

## Phase A: Read-Only VPS Inspection
Perform preliminary checks without modifying the live server:
```bash
# Check Docker daemon and running containers
docker ps -a
docker compose version

# Check available disk space (at least 60 GB recommended for file storage quota)
df -h /var/lib

# Check port availability
sudo netstat -tlunp | grep -E '4001|3478|5349|8080'
```

---

## Phase B: Backup Existing Relay Data
Preserve existing persistent mailbox records and identity state:
```bash
sudo mkdir -p /var/backups/ciphrchat
sudo tar -czvf /var/backups/ciphrchat/relay-backup-$(date +%Y%m%d%H%M%S).tar.gz /var/lib/ciphrchat
```

---

## Phase C: Build & Start Upgraded Relay
Upgrade the relay service while preserving existing volumes:
```bash
cd /opt/ciphrchat/omnichat/services/bootstrap-relay

# Copy environment template
cp .env.example .env

# Edit .env with your VPS public IP / domain and generate a random TURN_STATIC_AUTH_SECRET
nano .env

# Build and start containers
docker compose up -d --build
```

---

## Phase D: Configure Dedicated Large-File Storage
Ensure file storage volume and permissions are established:
```bash
docker compose exec relay ls -la /var/lib/ciphrchat/files
docker compose exec relay ls -la /var/lib/ciphrchat/mailbox
```

---

## Phase E: Start Coturn Media Relay
Ensure Coturn container is active and listening:
```bash
docker compose logs coturn
```

---

## Phase F: Production Health Checks
Verify all services are healthy:
```bash
# 1. Relay Health Endpoint
curl -s http://127.0.0.1:18081/health

# 2. Relay Info & Peer ID
curl -s http://127.0.0.1:18081/ciphrchat/info

# 3. Coturn TURN Port Check
nc -z -v -u 127.0.0.1 3478
```

---

## Phase G: Deploy Base Sepolia Smart Contract
Deploy `contracts/CiphrRelayRegistry.sol` to **Base Sepolia Testnet** (Chain ID: `84532`):
```bash
export RPC_URL="https://sepolia.base.org"
export PRIVATE_KEY="0x<YOUR_BASE_SEPOLIA_DEPLOYER_PRIVATE_KEY>"

forge create contracts/CiphrRelayRegistry.sol:CiphrRelayRegistry \
  --rpc-url "$RPC_URL" \
  --private-key "$PRIVATE_KEY" \
  --chain 84532
```
Save the returned contract address as `REGISTRY_CONTRACT_ADDRESS`.

---

## Phase H: Register Relay in Smart Contract
Register the public multiaddress of your deployed relay:
```bash
export CONTRACT_ADDRESS="0x<DEPLOYED_CONTRACT_ADDRESS>"
export RELAY_MULTIADDR="/ip4/<VPS_PUBLIC_IP>/tcp/4001/p2p/<RELAY_PEER_ID>"

cast send "$CONTRACT_ADDRESS" \
  "registerRelay(string,uint256)" \
  "$RELAY_MULTIADDR" 1000 \
  --rpc-url "$RPC_URL" \
  --private-key "$PRIVATE_KEY"

# Verify active relays
cast call "$CONTRACT_ADDRESS" "getActiveRelays(uint256,uint256)" 0 10 --rpc-url "$RPC_URL"
```

---

## Phase I: Collect Public Non-Secret Values
Prepare the configuration bundle for Android APK build:
- `BASE_SEPOLIA_RPC_URL="https://sepolia.base.org"`
- `REGISTRY_CONTRACT_ADDRESS="0x..."`
- `PUBLIC_RELAY_ADDRESS="/ip4/<VPS_PUBLIC_IP>/tcp/4001/p2p/<RELAY_PEER_ID>"`
- `TURN_PUBLIC_URL="turn:<VPS_PUBLIC_IP>:3478"`
- `BLOCKCHAIN_CHAIN_ID="84532"`

---

## Phase J: Build Final Android APK
Build the release or debug APK using Gradle properties (Linux / macOS / Windows):

### Linux / macOS:
```bash
cd omnichat
./gradlew assembleDebug -PbuildRust \
  -PciphrchatRelayAddress="$PUBLIC_RELAY_ADDRESS" \
  -PciphrchatBlockchainRpcUrl="$BASE_SEPOLIA_RPC_URL" \
  -PciphrchatRegistryContractAddress="$REGISTRY_CONTRACT_ADDRESS" \
  -PciphrchatBlockchainChainId=84532 \
  -PciphrchatTurnUrl="$TURN_PUBLIC_URL"
```

### Windows PowerShell:
```powershell
Set-Location omnichat
.\gradlew.bat assembleDebug -PbuildRust `
  -PciphrchatRelayAddress="$PUBLIC_RELAY_ADDRESS" `
  -PciphrchatBlockchainRpcUrl="$BASE_SEPOLIA_RPC_URL" `
  -PciphrchatRegistryContractAddress="$REGISTRY_CONTRACT_ADDRESS" `
  -PciphrchatBlockchainChainId=84532 `
  -PciphrchatTurnUrl="$TURN_PUBLIC_URL"
```

---

## Phase K: Two-Phone End-to-End Verification
1. **Pairing**: Scan QR code / import invitation between Phone A and Phone B.
2. **Text Messaging**: Send message across separate cellular networks; verify end-to-end encryption.
3. **5 GiB Large File Transfer**:
   - Send video / disk image attachment up to 5 GiB.
   - Verify progress bar and chunk streaming.
   - Interrupt connection, reconnect, and verify seamless resume.
   - Verify SHA-256 integrity check upon completion.
4. **Internet IP Privacy**:
   - Ensure "Hide my IP" is enabled in Settings.
   - Inspect network packets: verify all internet communication flows through relay and media flows through TURN relay without exposing peer IP.
5. **Audio-Only Calling**:
   - Tap Phone icon in chat.
   - Accept call; verify clear Opus mono audio with AEC and NS.
   - Test Mute and Speaker switching.
   - Hang up and verify immediate resource teardown.

---

## Phase L: Rollback Commands
In case of rollback:
```bash
# Stop new services
docker compose down

# Restore previous data
sudo rm -rf /var/lib/ciphrchat
sudo tar -xzvf /var/backups/ciphrchat/relay-backup-*.tar.gz -C /

# Checkout known-good stable baseline
git checkout v0.1.0-stable-baseline
docker compose up -d --build
```
