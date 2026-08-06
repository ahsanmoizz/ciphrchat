---
phase: 1
plan: fix-gradle-wrapper
wave: 1
gap_closure: true
---

# Fix Plan: Gradle Wrapper Missing

## Problem
The project lacks a `gradlew` wrapper, preventing deterministic building of the Android debug APK in CI and locally without a globally installed Gradle instance.

## Tasks

<task type="auto">
  <name>Generate Gradle Wrapper</name>
  <files>d:\ciphrchat\omnichat\gradlew, d:\ciphrchat\omnichat\gradlew.bat, d:\ciphrchat\omnichat\gradle\wrapper\gradle-wrapper.properties, d:\ciphrchat\omnichat\gradle\wrapper\gradle-wrapper.jar</files>
  <action>Copy a standard Gradle 8.12 wrapper into the project root (`d:\ciphrchat\omnichat`) or download the wrapper scripts. Then run `./gradlew :apps:android:assembleDebug` to verify it compiles.</action>
  <verify>Run `./gradlew --version` and verify it succeeds.</verify>
  <done>The Gradle wrapper is fully functional and committed to the repository, allowing Android builds to succeed.</done>
</task>
