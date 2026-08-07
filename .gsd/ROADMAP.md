# Roadmap (truthful status)

This directory is internal development history, not a public release promise. The repository is not yet an audited production release.

## Completed and CI-verified

- Persistent SQLCipher-backed identity, Signal state, contacts, encrypted previews, outbox payloads, and password-protected recovery snapshots.
- Rust/libp2p client foundation, JNI bridge, relay service, Docker Compose deployment, health/info endpoints, and manual VPS workflow.
- Invitation pairing and Internet request-response delivery path.
- Bounded authenticated local envelope framing for LAN, Wi-Fi Direct, and Bluetooth GATT with inbound event dispatch.
- Android debug build/lint and Rust/Docker CI.
- Fail-closed signed-release workflow that requires a real keystore and verifies the APK signature/checksum.

## Still required for a production release

- Deploy and verify a live VPS relay, configure `CIPHRCHAT_RELAY_ADDRESS`, and complete two-device Internet delivery.
- Configure a release keystore and publish a signed versioned APK.
- Execute the Android hardware test matrix: restart, recovery, pairing, queue/retry, reconnect, duplicate delivery, key change, LAN, Wi-Fi Direct, and Bluetooth GATT.
- Finish or keep disabled the authenticated protocols for Wi-Fi Aware messaging, Bluetooth mesh forwarding, ultrasound, IR, NFC message transfer, and UWB payloads.
- Complete threat-model review, dependency/signing review, and independent security assessment.

See `docs/PRODUCTION_STATUS.md` for the current release gates. Do not mark these items complete without evidence.
