---
phase: 2
plan: 1
wave: 1
---

# Plan 2.1: Android Keystore & SQLCipher Foundation

## Objective
Establish the secure persistent storage layer using SQLCipher, encrypted with a key derived from the Android Keystore System.

## Context
- .gsd/SPEC.md
- OMNICHAT_MASTER_BUILD_DIRECTIVE.md (Phase 2 requirements)

## Tasks

<task type="auto">
  <name>Implement KeyManager</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\security\KeyManager.kt, d:\ciphrchat\omnichat\apps\android\build.gradle.kts</files>
  <action>
    - Add `net.zetetic:android-database-sqlcipher:4.5.4` and `androidx.sqlite:sqlite-ktx:2.4.0` dependencies.
    - Create `KeyManager` class that generates an AES-256 GCM key in the Android Keystore (`AndroidKeyStore`).
    - Expose a method to get or create the database encryption passphrase.
  </action>
  <verify>Ensure `./gradlew :apps:android:assembleDebug` compiles successfully with the new dependencies and class.</verify>
  <done>KeyManager correctly interfaces with `AndroidKeyStore` to manage the DB passphrase.</done>
</task>

<task type="auto">
  <name>Create AppDatabase with SQLCipher</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\data\AppDatabase.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\DatabaseModule.kt</files>
  <action>
    - Implement Room or raw SQLiteOpenHelper with SQLCipher. Given the constraints and typical Android dev, use `androidx.room` or raw `SupportSQLiteOpenHelper` with `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`.
    - Note: The directive doesn't enforce Room, but SQLCipher is required. Implement the database factory and provide it as a singleton in `DatabaseModule.kt`, using the passphrase from `KeyManager`.
  </action>
  <verify>Ensure `./gradlew :apps:android:assembleDebug` compiles successfully.</verify>
  <done>The encrypted database instance is available in the DI graph.</done>
</task>

## Success Criteria
- [ ] SQLCipher dependency is integrated.
- [ ] Android Keystore correctly generates and retrieves keys.
- [ ] Encrypted database can be initialized.
