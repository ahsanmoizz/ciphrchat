---
phase: 2
plan: 2
wave: 1
---

# Plan 2.2: Persistent Identity

## Objective
Replace the in-memory prototype identity with a persistent implementation backed by SQLCipher and Keystore.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\identity\PrototypeIdentityRepository.kt

## Tasks

<task type="auto">
  <name>Persistent Identity Entity & DAO</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\data\IdentityEntity.kt</files>
  <action>
    - Define the schema/entity for storing the local user's identity details (publicId, displayName, fingerprint, createdAt).
    - If using raw SQLite, create the table definitions in `AppDatabase.kt`.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Identity schema is defined and integrated into the database creation lifecycle.</done>
</task>

<task type="auto">
  <name>Implement PersistentIdentityRepository</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\identity\PersistentIdentityRepository.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\AppModule.kt</files>
  <action>
    - Create `PersistentIdentityRepository` implementing `IdentityRepository`.
    - Generate real keypairs using Android Keystore for the cryptographic identity (or use Ed25519 if specified, but Phase 6 does real crypto, so for Phase 2, generate persistent verifiable IDs using Keystore signatures).
    - Replace `PrototypeIdentityRepository` with `PersistentIdentityRepository` in `AppModule.kt`.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Identity survives app restarts and is securely stored.</done>
</task>

## Success Criteria
- [ ] Identity data is stored in the encrypted database.
- [ ] Cryptographic material is backed by Android Keystore.
- [ ] Application correctly restores identity on startup.
