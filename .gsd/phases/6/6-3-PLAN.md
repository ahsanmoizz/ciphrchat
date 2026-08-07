---
phase: 6
plan: 3
wave: 3
---

# Plan 6.3: Safety Number & Key-Change Detection

## Objective
Enable users to verify their end-to-end encryption channels using out-of-band QR code scanning, and detect if a contact's identity key has changed (mitigating MITM attacks).

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SignalSessionManager.kt

## Tasks

<task type="auto">
  <name>Implement Safety Number Verification</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SafetyNumberGenerator.kt</files>
  <action>
    - Create `SafetyNumberGenerator` utilizing the Signal fingerprinting APIs (or a custom SHA-512 hash of sorted public keys).
    - Expose a method to generate a visual fingerprint (QR data) and a numeric fingerprint.
  </action>
  <verify>Ensure `SafetyNumberGenerator` compiles.</verify>
  <done>Safety numbers can be deterministically generated from two identity keys.</done>
</task>

<task type="auto">
  <name>Add Key-Change Detection</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SignalStoreAdapter.kt</files>
  <action>
    - Modify the `saveIdentity` method in `SignalStoreAdapter` to check if an identity key for an address already exists and is different.
    - If a mismatch is detected, emit a `KeyChangeWarning` event or flag the contact record.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>The app detects and flags identity key changes for existing contacts.</done>
</task>
