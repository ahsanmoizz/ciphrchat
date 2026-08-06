---
phase: 4
plan: 1
wave: 1
---

# Plan 4.1: BLE Advertisements and Discovery

## Objective
Implement Bluetooth Low Energy (BLE) advertisements to broadcast identity and discover nearby peers passively.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\BluetoothTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement BluetoothLeAdvertiser</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\bluetooth\BleAdvertiser.kt</files>
  <action>
    - Create `BleAdvertiser` using `android.bluetooth.le.BluetoothLeAdvertiser`.
    - Advertise a specific service UUID for OmniChat.
    - Embed a hashed/truncated version of the identity `publicId` in the service data.
  </action>
  <verify>Ensure `BleAdvertiser` compiles.</verify>
  <done>BLE advertising successfully starts with identity payload.</done>
</task>

<task type="auto">
  <name>Implement BluetoothLeScanner</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\bluetooth\BleScanner.kt</files>
  <action>
    - Create `BleScanner` using `android.bluetooth.le.BluetoothLeScanner`.
    - Scan for the OmniChat service UUID.
    - Extract the identity from service data and expose as a Coroutine `StateFlow<List<DiscoveredPeer>>`.
  </action>
  <verify>Ensure `BleScanner` compiles.</verify>
  <done>Discovered BLE peers are mapped into the standard `DiscoveredPeer` model.</done>
</task>
