# CiphrChat Threat Model

## 1. Overview
CiphrChat is designed to provide secure, resilient communication across multiple transport layers (Bluetooth, LAN, Internet P2P, Ultrasound, NFC). This document outlines the expected adversaries, trust boundaries, and mitigation strategies.

## 2. Adversaries
- **Local Network Eavesdroppers:** Entities monitoring Wi-Fi, BLE, or acoustic traffic.
- **Global Passive Adversaries (ISP/State):** Entities monitoring internet backbone traffic.
- **Active Network Attackers:** Entities attempting Man-in-the-Middle (MITM), Eclipse attacks, or Sybil attacks on the P2P swarm.
- **Physical Attackers:** Individuals with physical access to the device (seizure, theft).

## 3. Trust Boundaries & Assumptions
- The device OS (Android) is assumed to be uncompromised. If the OS is rooted/compromised, all guarantees are void.
- Hardware KeyStore is assumed to be secure and resistant to software-based extraction.
- The Signal Protocol (Double Ratchet, X3DH) implementation is assumed mathematically sound.

## 4. Specific Threats & Mitigations

### 4.1 Physical Device Seizure
**Threat:** An adversary confiscates the device and attempts to extract message history and private keys.
**Mitigation:** 
- All data at rest is encrypted using SQLCipher.
- The SQLCipher passphrase is derived from a high-entropy key wrapped by the Android Hardware KeyStore (requiring device unlock credentials/biometrics to access).
- Messages can be configured for automatic expiration (ephemeral messaging).

### 4.2 Bluetooth / Local Network Eavesdropping
**Threat:** An attacker uses a sniffer to capture BLE mesh payloads or Wi-Fi Direct traffic.
**Mitigation:** 
- Transport layers are completely ignorant of payload contents.
- All payloads are end-to-end encrypted using the Signal Protocol *before* being handed to the transport adapter.
- The Double Ratchet provides forward secrecy; past keys cannot be derived from a compromised current state.

### 4.3 Internet ISP Metadata Logging
**Threat:** An ISP attempts to map the social graph by analyzing IP connections.
**Mitigation:** 
- rust-libp2p traffic is encrypted (Noise) and multiplexed (Yamux).
- Circuit Relay masks direct IP connections between peers by bouncing traffic through volunteer nodes.
- Local transports (BLE/LAN) bypass the internet entirely, leaving no ISP metadata trail.

### 4.4 Libp2p Eclipse & Sybil Attacks
**Threat:** An attacker floods the DHT (Kademlia) to isolate a node or spoof identities.
**Mitigation:**
- Identity is not tied to DHT node IDs. Peer verification relies on out-of-band Safety Number validation (QR/NFC).
- If the identity key changes, the application blocks the message and warns the user of a potential MITM.
