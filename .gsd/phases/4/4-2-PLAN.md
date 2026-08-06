---
phase: 4
plan: 2
wave: 2
---

# Plan 4.2: GATT Server and Client for Direct Transfer

## Objective
Establish a direct data path over Bluetooth Low Energy using GATT characteristics, implementing manual chunking for reliable delivery of envelopes larger than the MTU.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\BluetoothTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement GATT Server and Chunking Receiver</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\bluetooth\GattServerManager.kt</files>
  <action>
    - Create `GattServerManager` using `BluetoothGattServer`.
    - Add a primary service and a writeable characteristic for receiving data.
    - Implement a chunk reassembly buffer since BLE MTU is limited (typically 20-512 bytes).
  </action>
  <verify>Ensure `GattServerManager` compiles without errors.</verify>
  <done>GATT server can receive chunked bytes and reassemble full envelopes.</done>
</task>

<task type="auto">
  <name>Update BluetoothTransportAdapter with GATT Client</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\BluetoothTransportAdapter.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\TransportModule.kt</files>
  <action>
    - Move `BluetoothTransportAdapter` to `org.ciphrchat.app.transport.bluetooth.BluetoothTransportAdapter`.
    - Wire `BleAdvertiser` and `BleScanner` for the `start` and `discoverPeers` methods.
    - On `send()`, use `BluetoothDevice.connectGatt` to connect to the peer's GATT server.
    - Implement MTU-aware chunking to write the `OutboundEnvelope` sequentially.
    - Update `TransportModule.kt` to bind the new adapter.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Bluetooth adapter can directly send envelopes over GATT with chunking.</done>
</task>
