---
phase: 9
plan: fix-persistence-and-crypto
wave: 1
gap_closure: true
---

# Fix: Cryptographic Persistence and Key Derivation

## Problem
`SignalStoreAdapter` uses volatile `ConcurrentHashMap` objects for identity and prekey storage. `RecoveryManager` uses SHA-256 for key derivation.

## Root Cause
These were implemented as scaffolds/prototypes during Phase 6 and Phase 2, respectively, and were never upgraded to production standards.

## Tasks

<task type="auto">
  <name>Fix SignalStoreAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\crypto\SignalStoreAdapter.kt</files>
  <action>
    - Inject `AppDatabase` (or the equivalent SQLCipher interface).
    - Rewrite `saveIdentity`, `storePreKey`, `storeSession`, etc. to serialize and save to the database DAOs instead of maps.
  </action>
  <verify>Ensure `SignalStoreAdapter` compiles and correctly leverages SQL.</verify>
  <done>No volatile maps remain in the Signal adapter implementation.</done>
</task>

<task type="auto">
  <name>Fix RecoveryManager</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\backup\RecoveryManager.kt</files>
  <action>
    - Replace the SHA-256 fallback with PBKDF2 (using `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`).
    - Use a proper salt and iteration count (e.g., 100,000+).
  </action>
  <verify>Ensure `RecoveryManager` compiles with the new key derivation logic.</verify>
  <done>Weak key derivation is replaced with PBKDF2.</done>
</task>
