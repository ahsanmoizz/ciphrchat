---
phase: 7
plan: 2
wave: 2
---

# Plan 7.2: NFC and UWB Experimental Adapters

## Objective
Implement Near Field Communication (NFC) for out-of-band key pairing and Ultra-Wideband (UWB) for distance/direction confirmation and high-bandwidth local transfer.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\AndroidManifest.xml

## Tasks

<task type="auto">
  <name>Implement NFC Transport Adapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\AndroidManifest.xml, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\NfcTransportAdapter.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt</files>
  <action>
    - Add `<uses-permission android:name="android.permission.NFC" />` and `<uses-feature android:name="android.hardware.nfc" />`.
    - Create `NfcTransportAdapter` utilizing Android `NfcAdapter` Host-based Card Emulation (HCE) to exchange small identity/session payloads.
    - Remove any mock implementation from `AllTransportAdapters.kt`.
  </action>
  <verify>Ensure `NfcTransportAdapter` compiles.</verify>
  <done>NFC is fully integrated as a short-range, low-bandwidth transport.</done>
</task>

<task type="auto">
  <name>Implement UWB Distance Confirmation</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\AndroidManifest.xml, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\UwbTransportAdapter.kt</files>
  <action>
    - Add UWB permissions: `android.permission.UWB_RANGING`.
    - Create `UwbTransportAdapter` using the Android `UwbManager` for secure ranging and distance estimation.
    - Connect ranging results into the transport adapter's state so the UI can display proximity validation during identity verification.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>UWB capabilities are scaffolded into the transport layer.</done>
</task>
