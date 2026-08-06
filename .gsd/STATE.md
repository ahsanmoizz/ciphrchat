# STATE.md — Project Memory

> **Last Updated**: 2026-08-07
> **Current Phase**: Phase 3 — Real Local Network
> **Session**: Active (resumed 2026-08-07)

## Active Context

- Phase 1 Foundation completed
- Phase 2 Persistent Local Foundation completed and **VERIFIED**
- Phase 3 Planning complete. Ready for execution.

## Decisions Made

- Following directive exactly — no redesign
- Using pinned dependency versions from directive Section 9.4
- Phase 1 foundation is a "runnable scaffold" not production-secure
- All mock/stub code must be explicitly labeled test-only

## Blockers

- None currently

## Key Facts

- Product: OmniChat — "One identity. Every connection."
- License: AGPL-3.0
- Platform: Android (Kotlin/Compose) + Rust core + Rust server
- Design: White/black/light-purple, Changelly-inspired
- Navigation: Exactly 3 tabs — Chats, Connect, Settings
- Transport adapters: 12 kinds (Internet, Wi-Fi LAN, Wi-Fi Aware, Wi-Fi Direct, BT Direct, BT Mesh, Ultrasound, IR, NFC, UWB, Internet Relay, External)
- Seed contacts: Sara, Ali, Usman
