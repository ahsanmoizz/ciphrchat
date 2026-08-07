---
phase: 5
plan: 3
wave: 3
---

# Plan 5.3: P2P Network Features (Kademlia, NAT, Relay)

## Objective
Implement advanced libp2p behaviors inside the Rust workspace including Kademlia for bootstrap discovery, AutoNAT for reachability, Circuit Relay for NAT hole punching, and Rendezvous data.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\crates\ciphrchat-routing\Cargo.toml
- d:\ciphrchat\omnichat\crates\ciphrchat-routing\src\lib.rs

## Tasks

<task type="auto">
  <name>Configure Network Behaviours</name>
  <files>d:\ciphrchat\omnichat\crates\ciphrchat-routing\Cargo.toml, d:\ciphrchat\omnichat\crates\ciphrchat-routing\src\lib.rs</files>
  <action>
    - Ensure `libp2p` features `kad`, `autonat`, `relay`, `rendezvous`, and `dcutr` are enabled in `Cargo.toml`.
    - Define a custom `NetworkBehaviour` struct that combines Kademlia, AutoNAT, RelayClient, and Rendezvous.
  </action>
  <verify>Run `cargo check -p ciphrchat-routing`.</verify>
  <done>The network behaviour compiles with all requested features enabled.</done>
</task>

<task type="auto">
  <name>Implement Bootstrapping and Relay</name>
  <files>d:\ciphrchat\omnichat\crates\ciphrchat-routing\src\lib.rs</files>
  <action>
    - Add functionality to dial a hardcoded bootstrap multiaddr (e.g. `/dnsaddr/bootstrap.libp2p.io`).
    - Use Kademlia to bootstrap the DHT and find other nodes.
    - Enable DCUtR (Direct Connection Upgrade through Relay) to attempt hole punching when AutoNAT detects restricted reachability.
  </action>
  <verify>Run `cargo check -p ciphrchat-routing`.</verify>
  <done>Rust implementation handles dialing bootstrap nodes and configures hole punching.</done>
</task>
