# Production verification audit

Status: **human_needed**

This audit records what is proven by the repository and what still requires live infrastructure, Android hardware, release credentials, or independent review. It does not treat a green compile as proof of end-to-end messaging.

## Automated evidence

- Latest transport implementation commit: [`1dc785e`](https://github.com/ahsanmoizz/ciphrchat/commit/1dc785e)
- Android build, lint, unit tests, APK artifact, and latest APK publication are run by [`Android CI`](https://github.com/ahsanmoizz/ciphrchat/actions/workflows/android.yml); the latest transport commits must be green before installing a new APK.
- Rust formatting, clippy, workspace tests, relay tests, and relay container build are run by [`Rust CI`](https://github.com/ahsanmoizz/ciphrchat/actions/workflows/rust.yml).

## Requirement matrix

| Requirement | Current evidence | Status |
|---|---|---|
| Persistent identity and encrypted local storage | SQLCipher-backed Room database, Android Keystore-wrapped database passphrase, persisted Signal identity/session/pre-key records | Automated/static evidence; restart/reinstall test still needed |
| QR/text invitation and identity binding | Scanner/import flow, bounded invitation validation, Signal identity hash binding, pre-key bundle | Automated/static evidence; two-device fingerprint verification still needed |
| Encrypted one-to-one text messaging | Signal session encryption/decryption, encrypted payload/outbox persistence, inbound validation, delivery event updates | Automated/static evidence; two-device delivery still needed |
| Queue/retry/delivery states | Persistent message status model and retry worker; native delivery IDs are wired back to the message row | Automated/static evidence; offline/reconnect/duplicate test still needed |
| Internet P2P with relay | Rust/libp2p client and persistent-key relay service, Docker/VPS deployment workflow | Code/CI verified; live VPS and cross-network delivery not verified |
| LAN/Wi-Fi Direct/Bluetooth GATT framing | Bounded authenticated-envelope framing and inbound bus wiring; GATT frame length cap | Code/CI verified; hardware interoperability not verified |
| Transport implementation truthfulness | Wi-Fi Aware, Bluetooth mesh, nearby audio, and NFC have authenticated paths; UWB is proximity assist; IR is transmitter-only on stock Android | Code evidence; hardware interoperability still needed |
| Release hardening | Actions pinned to immutable SHAs; signed-release workflow fails closed without keystore secrets and runs `apksigner`/checksum verification | Workflow verified; signed release not executed |
| VPS deployment | Manual deployment workflow and [`DEPLOYMENT.md`](DEPLOYMENT.md) | Not verified until a real VPS run succeeds |
| Independent security review | No review artifact is present | Missing |

## Required human/infrastructure checks

1. Deploy the relay to a VPS and record `/health`, `/info`, TCP/UDP reachability, and stable peer identity across restart.
2. Build an APK with `CIPHRCHAT_RELAY_ADDRESS`, install on two Android devices on separate networks, pair, and exchange messages in both directions.
3. Repeat after process restart, device restart, offline queue/reconnect, duplicate delivery, recovery restore, and contact key change.
4. Configure the user-owned Android release keystore, publish a signed tag release, and independently verify its signature and checksum.
5. Complete an independent security review before making audited-security claims.
