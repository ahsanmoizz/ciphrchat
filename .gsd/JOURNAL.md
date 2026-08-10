# JOURNAL.md — Session Log

> Session log entries will be added as work progresses.

## Production hardening — 2026-08-07

- Final code commit `912a089` passed Android build, lint, Android unit tests, APK artifact publication, and latest APK publication in [run 31211538741](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31211538741).
- The same commit passed Rust formatting, clippy, workspace tests, relay tests, and relay container build in [run 31211538286](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31211538286).
- Remaining evidence is external: live VPS deployment, two-device delivery/restart/recovery/offline/key-change tests, signed release execution, and independent security review.

## CI evidence update — 2026-08-07

- Commit `7c7347043e9c78772eb0ec398a1cd1af37cf87ba` passed Android build, lint, JVM unit tests, APK artifact publication, and latest APK publication in [run 31214031239](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31214031239).
- The same commit passed Rust formatting, clippy, workspace tests, relay tests, and relay container build in [run 31214031085](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31214031085).
- The Android test suite avoids Android framework `JSONObject` stubs on the plain JVM; the Internet wire encoder is now pure Kotlin while other Android-side JSON parsing remains on Android APIs.

## CI evidence update â€” 2026-08-08

- Commit `151637a843b7cdefe8bab04b7c266cf544af90e9` passed Android build, lint, JVM unit tests, APK artifact publication, and latest APK publication in [run 31217865760](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31217865760).
- The same commit passed Rust formatting, clippy, workspace tests, relay tests, and relay container build in [run 31217865917](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31217865917).
- `InternetWireCodec` now uses a bounded pure-Kotlin JSON encoder with explicit string escaping, so the JVM test exercises the production encoder without Android framework stubs.

## Internet reconnect and nearby-audio reliability — 2026-08-10

- Before the fix, five consecutive live relay probes each emitted `MailboxUnavailable: Failed to dial the requested peer` before later becoming ready; this reproduced the phone's false mailbox failure.
- After making mailbox requests connection-aware and retryable, three consecutive encrypted store/fetch/ack round trips against `95.217.129.142:4001` passed without any `MailboxUnavailable` event.
- Local Rust evidence: `cargo test --workspace` passed all workspace tests, and `cargo clippy --workspace --all-targets -- -D warnings` completed without warnings.
- Nearby audio now has JVM regression coverage for compressed-envelope round trips and corrupt-frame rejection; Android build/lint/unit evidence will be supplied by the release workflow before publication.

## Bluetooth delivery and chat controls — 2026-08-10

- Discovery correctly counted the peer, but the receive GATT service was advertised before Android confirmed service registration; the client also used automatic Bluetooth transport and the server returned write success before frame validation.
- The scoped fix registers the GATT service first, connects explicitly over BLE, validates each acknowledged chunk/full frame before success, and retries one transient GATT failure. Internet and Wi-Fi/LAN source files were not modified.
- Added conversation deletion through Room with app-private attachment cleanup, plus local in-chat filtering by message text or attachment name.
