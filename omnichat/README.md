# CiphrChat

**One identity. Every connection.**

CiphrChat is a minimal, open-source, local-first encrypted text messenger for Android. It gives each user one cryptographic identity and keeps each conversation continuous while automatically routing messages through Internet, shared Wi-Fi, Wi-Fi Direct, Wi-Fi Aware, Bluetooth, Bluetooth mesh, ultrasound, infrared, NFC pairing, UWB assistance, and future external adapters.

## Status

This is a development build: Android CI passes, but several restore, QR, nearby-device, and experimental transport actions are still prototype-level or placeholders. The public user guide is in the repository root at [README.md](../README.md).

## Build

### Android

```bash
./gradlew assembleDebug -PbuildRust
```

### Rust

```bash
cargo test --workspace
```

### Server

```bash
cargo check --manifest-path services/bootstrap-relay/Cargo.toml
```

## License

[AGPL-3.0](LICENSE)
