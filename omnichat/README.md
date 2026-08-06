# CiphrChat

**One identity. Every connection.**

CiphrChat is a minimal, open-source, local-first encrypted text messenger for Android. It gives each user one cryptographic identity and keeps each conversation continuous while automatically routing messages through Internet, shared Wi-Fi, Wi-Fi Direct, Wi-Fi Aware, Bluetooth, Bluetooth mesh, ultrasound, infrared, NFC pairing, UWB assistance, and future external adapters.

## Status

This is the Phase 1 foundation scaffold. See [docs/ONE_HOUR_RESULT.md](docs/ONE_HOUR_RESULT.md) for what is real vs. mocked.

## Build

### Android

```bash
./gradlew :apps:android:assembleDebug
```

### Rust

```bash
cd crates && cargo test --workspace
```

### Server

```bash
cd services/bootstrap-relay && cargo check
```

## License

[AGPL-3.0](LICENSE)
