---
phase: 2
plan: 4
wave: 3
---

# Plan 2.4: Contact QR Generation & Recovery

## Objective
Implement QR code generation for exchanging contact identity and create an encrypted recovery file format for backups.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\ui\screens\IdentityReadyScreen.kt

## Tasks

<task type="auto">
  <name>QR Code Generation</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\identity\QrCodeGenerator.kt, d:\ciphrchat\omnichat\apps\android\build.gradle.kts, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\ui\screens\IdentityReadyScreen.kt</files>
  <action>
    - Add a lightweight QR generation dependency (e.g., `com.google.zxing:core`).
    - Create `QrCodeGenerator` utility to convert a `ciphr:xxx` URI into a Bitmap.
    - Update `IdentityReadyScreen` to display the actual QR code instead of the placeholder.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>QR codes are dynamically generated and displayed on screen.</done>
</task>

<task type="auto">
  <name>Encrypted Recovery File Export</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\backup\RecoveryManager.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\ui\screens\SettingsScreen.kt</files>
  <action>
    - Implement `RecoveryManager` to export the local identity (and database encryption passphrase) as an AES-encrypted file.
    - Wire "Back up identity" in `SettingsScreen` to trigger the export via `Storage Access Framework` (ACTION_CREATE_DOCUMENT).
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Users can export their identity to an encrypted file.</done>
</task>

## Success Criteria
- [ ] Users can view their own identity as a QR code.
- [ ] Users can export an encrypted recovery backup file.
