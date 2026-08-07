---
phase: 7
plan: 1
wave: 1
---

# Plan 7.1: Ultrasound Modem PoC & Reed-Solomon

## Objective
Implement an experimental ultrasound transport layer using FSK/PSK audio modulation, wrapped with Reed-Solomon error correction for reliable short-burst data transfer (e.g., identity rendezvous).

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\UltrasoundTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement Ultrasound Modem</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\ultrasound\UltrasoundModem.kt, d:\ciphrchat\omnichat\apps\android\build.gradle.kts</files>
  <action>
    - Add a Reed-Solomon library (e.g., `com.google.zxing:core` has one, or use a standalone Java library).
    - Implement `UltrasoundModem` that utilizes `AudioTrack` and `AudioRecord` to transmit and receive data in the 18kHz-20kHz range.
    - Apply Reed-Solomon Forward Error Correction to the payload chunks.
  </action>
  <verify>Ensure `UltrasoundModem` compiles.</verify>
  <done>Audio modulation and FEC logic compiles without errors.</done>
</task>

<task type="auto">
  <name>Wire Ultrasound Adapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\UltrasoundTransportAdapter.kt</files>
  <action>
    - Extract `UltrasoundTransportAdapter` and back it with `UltrasoundModem`.
    - Handle `send()` by encoding the envelope payload into audio if small enough, or return a capability error for large payloads.
    - Remove the mock implementation.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>The ultrasound adapter is successfully integrated into the transport layer.</done>
</task>
