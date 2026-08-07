# Architecture

## Overview

CiphrChat is organized as an Android client, a Rust networking workspace, and a small Rust HTTP bootstrap service.

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
        +-- Axum HTTP server
        +-- GET /health
```

## Main components

| Component | Location | Responsibility |
|---|---|---|
| Android application | `omnichat/apps/android` | UI, onboarding, identity, messages, transport adapters |
| Transport routing | `omnichat/apps/android/.../transport` | Selects a transport and coordinates delivery |
| Rust protocol | `omnichat/crates/ciphrchat-protocol` | Shared protocol types and rules |
| Rust routing | `omnichat/crates/ciphrchat-routing` | libp2p transport and discovery foundation |
| Android FFI | `omnichat/crates/ciphrchat-ffi` | JNI boundary for Rust functionality |
| Bootstrap service | `omnichat/services/bootstrap-relay` | HTTP health endpoint and deployment foundation |

## Current limitations

- Several UI actions are prototype placeholders, including restore, showing/scanning QR codes, nearby discovery, and invitations.
- Some experimental transports use mock or simplified behavior.
- `bootstrap-relay` is not yet a complete public libp2p relay server.
- The project should not be marketed as a production-secure messenger until its release hardening and independent security review are complete.
