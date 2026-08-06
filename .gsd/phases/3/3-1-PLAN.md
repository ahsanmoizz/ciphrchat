---
phase: 3
plan: 1
wave: 1
---

# Plan 3.1: Shared LAN Discovery & Socket Session

## Objective
Implement real local network discovery using Android Network Service Discovery (NSD) and establish an authenticated socket session to exchange envelopes.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\LanTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement NsdManager Discovery</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\lan\LanDiscovery.kt</files>
  <action>
    - Create `LanDiscovery` class using `android.net.nsd.NsdManager`.
    - Register a service `_ciphr._tcp` with the device's public ID as the service name.
    - Discover other `_ciphr._tcp` services on the local network.
  </action>
  <verify>Ensure `LanDiscovery` compiles.</verify>
  <done>Device can register itself and discover peers on the local Wi-Fi.</done>
</task>

<task type="auto">
  <name>Implement Socket Server and Client</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\lan\LanConnection.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\LanTransportAdapter.kt</files>
  <action>
    - Create `LanConnection` to manage a ServerSocket (listening) and Socket (connecting).
    - Implement a simple length-prefixed binary framing for `OutboundEnvelope` exchange over TCP.
    - Update `LanTransportAdapter` to replace the mock with `LanDiscovery` and `LanConnection`.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>TCP socket can transmit an envelope to a discovered peer.</done>
</task>
