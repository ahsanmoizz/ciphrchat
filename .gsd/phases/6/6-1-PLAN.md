---
phase: 6
plan: 1
wave: 1
---

# Plan 6.1: Signal Protocol Adapter Foundation

## Objective
Establish the cryptographic foundation for end-to-end encryption by integrating the Signal Protocol library and implementing its required persistent storage interfaces.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\build.gradle.kts
- d:\ciphrchat\omnichat\gradle\libs.versions.toml

## Tasks

<task type="auto">
  <name>Configure Signal Dependency</name>
  <files>d:\ciphrchat\omnichat\gradle\libs.versions.toml, d:\ciphrchat\omnichat\apps\android\build.gradle.kts</files>
  <action>
    - Add `org.whispersystems:signal-protocol-java:2.8.1` (or equivalent modern fork) to the Gradle version catalog.
    - Implement the dependency in the Android app.
  </action>
  <verify>Run `./gradlew :apps:android:dependencies` to ensure it resolves.</verify>
  <done>Signal protocol library is available in the classpath.</done>
</task>

<task type="auto">
  <name>Implement Signal Stores</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SignalStoreAdapter.kt</files>
  <action>
    - Create `SignalStoreAdapter` implementing `SignalProtocolStore` (which encompasses `IdentityKeyStore`, `PreKeyStore`, `SignedPreKeyStore`, and `SessionStore`).
    - Back these interfaces using Room DAOs or SQLCipher directly, ensuring all cryptographic material is encrypted at rest via the database passphrase.
  </action>
  <verify>Ensure `SignalStoreAdapter` compiles.</verify>
  <done>All required Signal storage interfaces are backed by the existing encrypted database.</done>
</task>
