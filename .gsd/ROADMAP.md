# ROADMAP.md

> **Current Phase**: Phase 2
> **Milestone**: v0.2.0-dev (Persistent Local Foundation)

## Phases

### Phase 1: Foundation Scaffold
**Status**: ✅ Complete
**Objective**: Compiling Android debug APK, exact UI design, mocked transport adapters, prototype identity, Rust workspace, and CI.

### Phase 2: Persistent Local Foundation
**Status**: ✅ Complete
**Objective**: SQLCipher database, Android Keystore-wrapped database key, persistent identity, contact QR generation and scanning, persistent messages and queue, encrypted recovery file format.

### Phase 3: Real Local Network
**Status**: ✅ Complete
**Objective**: Shared LAN discovery, authenticated socket session, real Wi-Fi Direct, real Wi-Fi Aware, device-to-device test matrix.

### Phase 4: Bluetooth
**Status**: ✅ Complete
**Objective**: BLE advertisements, GATT transfer, reliable chunking and ACKs, mesh envelope forwarding, hop limit, battery-aware operation.

### Phase 5: Internet P2P
**Status**: ✅ Complete
**Objective**: rust-libp2p peer identity, QUIC/TCP, bootstrap discovery, Kademlia, NAT reachability, hole punching, circuit relay, rendezvous data.

### Phase 6: Production Encryption
**Status**: ✅ Complete
**Objective**: Pinned libsignal adapter, session establishment, ratcheted messages, key-change detection, safety-number/QR verification, migration tests.

### Phase 7: Experimental Routes
**Status**: ✅ Complete
**Objective**: Ultrasound modem PoC, Reed-Solomon correction, infrared hardware experiments, UWB-assisted confirmation, NFC pairing.

### Phase 8: Release Hardening
**Status**: ⬜ Not Started
**Objective**: Fuzzing, threat-model review, dependency review, reproducible builds, signed APK, public bootstrap/relay deployment, security audit.
