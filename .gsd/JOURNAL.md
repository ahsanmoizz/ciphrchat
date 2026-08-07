# JOURNAL.md — Session Log

> Session log entries will be added as work progresses.

## Production hardening — 2026-08-07

- Final code commit `912a089` passed Android build, lint, Android unit tests, APK artifact publication, and latest APK publication in [run 31211538741](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31211538741).
- The same commit passed Rust formatting, clippy, workspace tests, relay tests, and relay container build in [run 31211538286](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31211538286).
- Remaining evidence is external: live VPS deployment, two-device delivery/restart/recovery/offline/key-change tests, signed release execution, and independent security review.

## CI evidence update — 2026-08-07

- Commit `7c7347043e9c78772eb0ec398a1cd1af37cf87ba` passed Android build, lint, JVM unit tests, APK artifact publication, and latest APK publication in [run 31214031239](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31214031239).
- The same commit passed Rust formatting, clippy, workspace tests, relay tests, and relay container build in [run 31214031085](https://github.com/ahsanmoizz/ciphrchat/actions/runs/31214031085).
- The Android test suite now avoids parsing Android framework `JSONObject` stubs on the plain JVM; this keeps the test host-independent while production continues to use Android JSON serialization.
