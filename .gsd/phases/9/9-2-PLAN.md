---
phase: 9
plan: fix-routing-and-jni
wave: 1
gap_closure: true
---

# Fix: Message Routing and P2P Bridge

## Problem
`AutomaticRouter` encrypts messages but never sends them. Rust `libp2p` node has no way to push messages to Kotlin.

## Root Cause
Scaffold implementations in Phase 5 and Phase 6 stopped at encryption/initialization but did not establish the cross-boundary data pipelines.

## Tasks

<task type="auto">
  <name>Fix AutomaticRouter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\messaging\AutomaticRouter.kt</files>
  <action>
    - Inject `TransportManager`.
    - Change `encryptAndRoute` to call `transportManager.send(envelope)` after encryption.
  </action>
  <verify>Ensure `AutomaticRouter` compiles with the transport dependency.</verify>
  <done>Envelopes are forwarded to the transport layer.</done>
</task>

<task type="auto">
  <name>Fix JNI Bridge</name>
  <files>d:\ciphrchat\omnichat\crates\ciphrchat-ffi\src\lib.rs, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\internet\RustP2pManager.kt</files>
  <action>
    - Add a JNI callback `onMessageReceived(ByteArray)` to `RustP2pManager`.
    - In `ciphrchat-ffi`, retrieve the JNI environment and invoke the callback when Gossipsub/Kademlia messages arrive.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug -PbuildRust` to verify bridge compilation.</verify>
  <done>Kotlin can receive payloads from the Rust background swarm.</done>
</task>
