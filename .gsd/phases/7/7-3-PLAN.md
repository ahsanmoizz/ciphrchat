---
phase: 7
plan: 3
wave: 3
---

# Plan 7.3: Infrared Hardware Scaffold

## Objective
Implement experimental support for Consumer IR hardware as a fallback unidirectional side-channel.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt

## Tasks

<task type="auto">
  <name>Implement Infrared Transport Adapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\AndroidManifest.xml, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\InfraredTransportAdapter.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\TransportModule.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt</files>
  <action>
    - Add `android.permission.TRANSMIT_IR` permission to the manifest.
    - Create `InfraredTransportAdapter` using `ConsumerIrManager` to transmit short sequences (e.g. SOS rendezvous pings) using standard IR frequencies (38kHz).
    - Wire it into the `TransportModule.kt`.
    - Remove the remaining mock adapters in `AllTransportAdapters.kt`.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Infrared hardware support is compiled and wired into the application graph.</done>
</task>
