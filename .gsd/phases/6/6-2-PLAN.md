---
phase: 6
plan: 2
wave: 2
---

# Plan 6.2: Session Establishment & Ratcheted Messages

## Objective
Implement Double Ratchet encryption for OutboundEnvelopes, enabling forward secrecy and break-in recovery.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\messaging\AutomaticRouter.kt

## Tasks

<task type="auto">
  <name>Implement Session Builder and Cipher</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SignalSessionManager.kt</files>
  <action>
    - Create `SignalSessionManager`.
    - Implement `generatePreKeyBundle()` to create One-Time PreKeys and a Signed PreKey for the initial handshake.
    - Implement `processPreKeyBundle()` using `SessionBuilder` to establish a session with a new contact.
    - Implement `encryptMessage()` and `decryptMessage()` using `SessionCipher`.
  </action>
  <verify>Ensure `SignalSessionManager` compiles.</verify>
  <done>Session building and cipher operations are mapped to the Signal API.</done>
</task>

<task type="auto">
  <name>Integrate Ratchet into Router</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\messaging\AutomaticRouter.kt</files>
  <action>
    - Inject `SignalSessionManager` into `AutomaticRouter`.
    - Before an envelope is handed to a transport adapter, encrypt the plaintext payload with the recipient's Signal session.
    - Upon receiving an envelope, intercept it and decrypt the payload before delivering to the UI/MessageRepository.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>The messaging router actively encrypts and decrypts payloads via the Double Ratchet.</done>
</task>
