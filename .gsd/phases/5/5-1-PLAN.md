---
phase: 5
plan: 1
wave: 1
---

# Plan 5.1: Setup Rust Libp2p and JNI Interface

## Objective
Establish the foundation for the Internet P2P layer by configuring `rust-libp2p` with TCP and QUIC, and exposing it via Java Native Interface (JNI).

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\crates\ciphrchat-routing\Cargo.toml
- d:\ciphrchat\omnichat\crates\ciphrchat-ffi\Cargo.toml

## Tasks

<task type="auto">
  <name>Configure Rust Dependencies</name>
  <files>d:\ciphrchat\omnichat\crates\ciphrchat-routing\Cargo.toml, d:\ciphrchat\omnichat\crates\ciphrchat-ffi\Cargo.toml</files>
  <action>
    - Add `libp2p` (features: `tcp`, `quic`, `noise`, `yamux`, `identify`, `tokio`, `macros`) and `tokio` (features: `full`) to `ciphrchat-routing`.
    - Add `jni` crate to `ciphrchat-ffi`.
    - Set `crate-type = ["cdylib"]` in `ciphrchat-ffi/Cargo.toml`.
  </action>
  <verify>Run `cargo check -p ciphrchat-ffi`.</verify>
  <done>Dependencies resolve and the FFI crate is configured as a C dynamic library.</done>
</task>

<task type="auto">
  <name>Implement Libp2p Swarm and JNI Wrapper</name>
  <files>d:\ciphrchat\omnichat\crates\ciphrchat-routing\src\lib.rs, d:\ciphrchat\omnichat\crates\ciphrchat-ffi\src\lib.rs</files>
  <action>
    - In `ciphrchat-routing/src/lib.rs`, implement a basic asynchronous `start_swarm()` function that creates a libp2p `Swarm` utilizing QUIC and TCP, authenticating via Noise, and multiplexing via Yamux.
    - In `ciphrchat-ffi/src/lib.rs`, expose a JNI function `Java_org_ciphrchat_app_transport_internet_RustP2pManager_startSwarm` that spawns a Tokio runtime and runs `start_swarm()`.
  </action>
  <verify>Run `cargo build -p ciphrchat-ffi`.</verify>
  <done>The Rust workspace compiles the JNI wrapper and libp2p stack successfully.</done>
</task>
