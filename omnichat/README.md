# CiphrChat

**One identity. Every connection.**

CiphrChat is an open-source, local-first encrypted text messenger for Android. It maintains persistent local identity and conversation state, pairs contacts with signed Signal pre-key material, and routes encrypted payloads through Internet/libp2p or supported local transports.

## Status

The project is under production hardening: Android and Rust/Docker CI are green, Internet relay/client code and invitation pairing are implemented, and the public user guide is in the repository root at [README.md](../README.md). A live VPS, signed release key, two-device delivery test, and independent security review are still release gates.

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
