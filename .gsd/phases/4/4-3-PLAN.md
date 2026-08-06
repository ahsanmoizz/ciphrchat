---
phase: 4
plan: 3
wave: 3
---

# Plan 4.3: Mesh Envelope Forwarding & Battery-Aware

## Objective
Implement mesh routing features for Bluetooth, allowing the device to forward envelopes it receives but are intended for someone else, while protecting against infinite loops and battery drain.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\BluetoothMeshTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement Duplicate Message Cache and Hop Limit</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\bluetooth\MeshRouter.kt</files>
  <action>
    - Create `MeshRouter` class to evaluate incoming `OutboundEnvelope`s.
    - Implement an LRU cache or rotating buffer to remember recently seen `messageId`s and prevent looping.
    - Check the `hopLimit` property. If > 0, decrement it and prepare for rebroadcast.
  </action>
  <verify>Ensure `MeshRouter` logic correctly identifies duplicates and updates hop limit.</verify>
  <done>Router prevents duplicate processing and respects the hop limit.</done>
</task>

<task type="auto">
  <name>Update BluetoothMeshTransportAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\bluetooth\BluetoothMeshTransportAdapter.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\TransportModule.kt</files>
  <action>
    - Extract `BluetoothMeshTransportAdapter` into the `bluetooth` package.
    - Wire `MeshRouter` and the `BluetoothTransportAdapter` (from 4.2) to forward eligible envelopes.
    - Adjust discovery/advertising intensity based on Android `BatteryManager` (e.g., lower frequency if battery is low).
    - Update `TransportModule.kt` to bind the concrete mesh adapter.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Mesh adapter is fully wired and respects battery state constraints.</done>
</task>
