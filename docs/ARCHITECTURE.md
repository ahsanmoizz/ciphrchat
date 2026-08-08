# Architecture

## Overview

CiphrChat is organized as an Android client, a Rust networking workspace, and a deployable libp2p circuit-relay service.

```text
Android app (Kotlin + Compose)
        |
        +-- identity, encrypted local storage, messages
        +-- transport registry and automatic routing
        +-- Rust FFI for Internet/libp2p components

Rust workspace
        +-- ciphrchat-protocol
        +-- ciphrchat-routing (libp2p client foundation)
        +-- ciphrchat-ffi

Bootstrap relay service
        +-- libp2p relay v2 server over TCP and QUIC
        +-- Axum health/info endpoints
```

## Main components

| Component | Location | Responsibility |
|---|---|---|
| Android application | `omnichat/apps/android` | UI, onboarding, identity, messages, transport adapters |
| Transport routing | `omnichat/apps/android/.../transport` | Selects a transport and coordinates delivery |
| Rust protocol | `omnichat/crates/ciphrchat-protocol` | Shared protocol types and rules |
| Rust routing | `omnichat/crates/ciphrchat-routing` | libp2p transport and discovery foundation |
| Android FFI | `omnichat/crates/ciphrchat-ffi` | JNI boundary for Rust functionality |
| Bootstrap service | `omnichat/services/bootstrap-relay` | Persistent-key libp2p relay, health endpoints, Docker/VPS deployment |

## Current limitations

- Contact invitations now carry a peer circuit address and Signal pre-key bundle; scanning and paste/import are wired through the Android UI.
- Outgoing messages establish/use a Signal session and transport ciphertext through the libp2p request-response path. Message previews are encrypted before entering the SQLCipher database.
- Several local transports remain hardware- and permission-dependent; each path reports its actual capability and never simulates delivery.
- The relay is production-deployable, but this repository has not yet verified a live VPS, two-device delivery, signed release APK, or independent security review. Do not market the current debug download as a production security release.
- The authoritative release checklist is [PRODUCTION_STATUS.md](PRODUCTION_STATUS.md); it distinguishes code/CI verification from live-network and hardware verification.
