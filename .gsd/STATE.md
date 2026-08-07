# STATE.md — Project Memory

> Last Updated: 2026-08-07
> Status: Production hardening in progress

## Current truth

- Android debug and Rust/Docker CI are green for the latest pushed transport framing change when its runs complete.
- The app has persistent encrypted local state and an actual Internet/libp2p relay path.
- LAN, Wi-Fi Direct, and Bluetooth GATT now have bounded framed envelopes and inbound dispatch.
- Unsupported or incomplete media paths are explicitly experimental/unavailable; they must not report delivery.
- No live VPS relay, signed public APK, two-device hardware test, or independent security audit has been verified in this workspace.

## Operational blockers

- A VPS host/user/SSH key and the repository relay variable are required for live deployment.
- A user-owned Android release keystore must be configured as GitHub Secrets before publishing a signed APK.
- Hardware access is required to validate local transports and recovery/restart behavior.

## Rule

Evidence is required before changing any production gate to complete. See `docs/PRODUCTION_STATUS.md`.
