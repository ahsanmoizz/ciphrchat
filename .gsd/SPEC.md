# SPEC.md — Project Specification

> **Status**: `FINALIZED`

## Vision

CiphrChat is a minimal, open-source, local-first encrypted text messenger for Android. It gives each user one cryptographic identity and keeps each conversation continuous while automatically routing messages through Internet, shared Wi-Fi, Wi-Fi Direct, Wi-Fi Aware, Bluetooth, Bluetooth mesh, ultrasound, infrared, NFC pairing, UWB assistance, and future external adapters. Messages and contacts belong to the user's device. The interface stays simple: Chats, Connect, and Settings.

## Goals

1. **One Identity, Every Connection** — A user has one cryptographic identity across all transports; a chat never splits when the transport changes.
2. **Automatic Multi-Transport Routing** — The app selects the best available route (Internet → Wi-Fi LAN → Wi-Fi Aware → Wi-Fi Direct → Bluetooth → Mesh → Ultrasound → IR → Relay → Queue) without user intervention.
3. **Local-First, Privacy-First** — All contacts, messages, keys, and queues live on the user's device. No central phone/email registration, no tracking, no ads.
4. **Production-Honest Foundation** — Phase 1 delivers a compiling Android app with the exact UI, mock transport adapters, prototype identity, Rust core workspace, server scaffold, and CI — all clearly labeled as what's real vs. mocked.
5. **Open Source (AGPL-3.0)** — Source, builds, protocol, threat model, and releases are public.

## Non-Goals (Out of Scope)

- Social status/story system, public timeline
- Cryptocurrency, payments, blockchain
- Advertisement SDKs or tracking analytics
- Central phone-number or email account registration
- Public discoverable usernames in first release
- Voice/video calls in first release
- Large public groups, bots, mini-apps, AI assistants
- Dark mode (intentionally light-only for now)

## Users

Solo individuals and small groups who want private, resilient text communication that works even when traditional Internet connectivity is limited or unavailable. Users do not need to understand networking, ports, relays, radio protocols, or cryptography.

## Constraints

- **Platform**: Android (Kotlin + Jetpack Compose), Rust shared core (UniFFI), Rust server
- **Build baseline**: JDK 17, AGP 9.2.0, Gradle 9.4.1, Kotlin 2.3.21, Compose BOM 2026.04.01
- **Design**: White/black/light-purple palette per Changelly reference — no cyberpunk, no dark crypto look
- **Security**: Use reviewed protocols only; no custom crypto; mock crypto clearly labeled test-only
- **License**: AGPL-3.0

## Success Criteria

- [ ] Android debug APK builds and opens without crashing
- [ ] White/black/light-purple UI matches design system exactly
- [ ] Welcome → Create Identity → Enable Connections → Identity Ready flow works
- [ ] Bottom nav has exactly Chats, Connect, Settings
- [ ] Chats screen has sample conversations; Chat screen can add outgoing messages
- [ ] Connect screen lists all transport categories with honest availability
- [ ] AutomaticRouter exists with deterministic priority
- [ ] Every transport has an adapter class (mocked OK)
- [ ] Mock security explicitly labeled test-only
- [ ] Share CiphrChat shares safe official link
- [ ] Rust workspace compiles and tests pass
- [ ] Bootstrap-relay health service compiles
- [ ] No secrets committed
- [ ] ONE_HOUR_RESULT.md distinguishes completed vs. incomplete work
