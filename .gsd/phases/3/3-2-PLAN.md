---
phase: 3
plan: 2
wave: 2
---

# Plan 3.2: Wi-Fi Direct Transport

## Objective
Implement device-to-device transport using Android Wi-Fi Direct (P2P) APIs.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\WifiDirectTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement WifiP2pManager integration</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\wifi\WifiDirectManager.kt</files>
  <action>
    - Create `WifiDirectManager` utilizing `android.net.wifi.p2p.WifiP2pManager`.
    - Handle `WIFI_P2P_PEERS_CHANGED_ACTION` and `WIFI_P2P_CONNECTION_CHANGED_ACTION`.
    - Expose discovered peers and connection state as Coroutine Flows.
  </action>
  <verify>Ensure `WifiDirectManager` compiles.</verify>
  <done>WifiP2pManager broadcast receivers are handled and mapped to flows.</done>
</task>

<task type="auto">
  <name>Update WifiDirectTransportAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\WifiDirectTransportAdapter.kt</files>
  <action>
    - Update the adapter to start `WifiDirectManager` discovery.
    - When `send` is called, initiate a `WifiP2pConfig` connection to the peer if not already connected.
    - Re-use `LanConnection` (from 3.1) to bind a socket over the Wi-Fi Direct group owner IP.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Wi-Fi Direct adapter delegates to real Android P2P APIs.</done>
</task>
