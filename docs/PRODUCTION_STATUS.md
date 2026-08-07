# Production status

This is the release truth for the repository. Green CI proves that checked-in code builds and passes automated checks; it does not prove hardware coverage, a live relay, or an independent security audit.

## Verified in the repository

- Android debug build, lint, Rust tests/clippy/formatting, relay checks, and relay container build run in GitHub Actions.
- Local identity, Signal pre-key/session records, contacts, encrypted message previews, encrypted Signal payloads, and queued outbox payloads persist in SQLCipher-backed Room storage.
- Recovery export/import is a password-protected AES-GCM snapshot containing identity, Signal state, contacts, messages, and sessions.
- Internet messaging code uses the Rust/libp2p request-response client and a persistent-key circuit relay service; a live relay and two-device delivery are not yet verified.
- LAN, Wi-Fi Direct, and Bluetooth GATT code uses bounded framed envelopes and feeds received frames into the message repository; Signal decryption still happens at the application endpoint. Hardware interoperability is not yet verified.
- IR, NFC, UWB, ultrasound, Wi-Fi Aware messaging, and Bluetooth mesh forwarding are explicitly experimental or unavailable and cannot report successful message delivery.

## Required before calling a public release production-ready

1. Configure `CIPHRCHAT_RELAY_ADDRESS` with the deployed relay multiaddress.
2. Deploy the relay to a VPS and verify `/health`, `/info`, TCP/UDP reachability, persistent relay identity, and two-device delivery.
3. Configure the Android release secrets in the root README, create a tag, and verify the APK signature and checksum.
4. Run the two-device pairing, restart, recovery, offline-queue, reconnect, duplicate-delivery, and key-change test matrix on supported Android hardware.
5. Complete an independent security review before advertising the application as audited secure messaging software.

Until those gates are recorded, the stable user download remains a development/debug artifact and the UI must continue to show unavailable/experimental transports honestly.
