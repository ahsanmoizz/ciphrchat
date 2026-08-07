---
phase: 8
plan: 1
wave: 1
---

# Plan 8.1: Signing and Release Configuration

## Objective
Configure Android release builds with proper signing keys, ProGuard minification, and resource shrinking to ensure a small, hardened production APK.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\build.gradle.kts

## Tasks

<task type="auto">
  <name>Configure ProGuard Rules</name>
  <files>d:\ciphrchat\omnichat\apps\android\proguard-rules.pro</files>
  <action>
    - Create/update `proguard-rules.pro` to keep essential cryptographic interfaces (e.g., `org.whispersystems.libsignal.**`, JNI functions for rust-libp2p).
    - Ensure Kotlin Coroutines, Hilt, and Room rules are present if needed.
  </action>
  <verify>Check that `proguard-rules.pro` contains libsignal and JNI keep rules.</verify>
  <done>ProGuard rules protect critical reflection and JNI boundaries.</done>
</task>

<task type="auto">
  <name>Setup Release Build Type</name>
  <files>d:\ciphrchat\omnichat\apps\android\build.gradle.kts</files>
  <action>
    - Update `buildTypes { release { ... } }` to enable `isMinifyEnabled`, `isShrinkResources`, and apply the ProGuard files.
    - Set up a dummy signing config (or environmental fallback) so it can compile in CI.
  </action>
  <verify>Run `./gradlew :apps:android:assembleRelease --dry-run`.</verify>
  <done>Release build type is configured for minification and signing.</done>
</task>
