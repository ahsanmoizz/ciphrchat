# Changelog

All notable changes to OmniChat will be documented in this file.

## [0.1.0-dev.10] - 2026-08-10

### Fixed
- Conventional Internet delivery now uses the encrypted relay mailbox without requiring an optional circuit reservation.
- Manual pairing rejects short identity fingerprints with a clear request for the full secure invitation.
- NFC tap messaging assigns reader and host-card-emulation roles per transfer and fixes multi-chunk receive offsets.
- Nearby audio reports success only after a receiver acknowledgement and rejects the sender's own acoustic echo.
- UWB is presented and routed as proximity-verified BLE delivery; unverified consumer IR is no longer an automatic message route.
- CI verifies an offline-recipient mailbox round trip against the deployed relay before a build can pass.

## [0.1.0-dev] - 2026-08-07

### Added
- Initial repository structure
- Android app shell with Jetpack Compose
- Design system (white/black/light-purple theme)
- All 8 screens: Welcome, Create Identity, Enable Connections, Identity Ready, Chats, Chat, Connect, Settings
- Prototype identity generation
- In-memory message repository with seed data
- 12 mock transport adapters
- AutomaticRouter with deterministic priority
- Transport capability detection
- APK sharing via share sheet
- Rust workspace: protocol, routing, FFI crates
- Bootstrap-relay server scaffold
- GitHub Actions CI workflows
