---
phase: 5
plan: 2
wave: 2
---

# Plan 5.2: Android Rust Build Integration

## Objective
Integrate the compiled Rust `ciphrchat-ffi` library into the Android build system and wire it to the `InternetTransportAdapter`.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\build.gradle.kts
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt

## Tasks

<task type="auto">
  <name>Configure Cargo NDK in Gradle</name>
  <files>d:\ciphrchat\omnichat\apps\android\build.gradle.kts, d:\ciphrchat\omnichat\settings.gradle.kts</files>
  <action>
    - Ensure a mechanism (like executing `cargo ndk` via an Exec task or using a Rust Android Gradle plugin) is added to `build.gradle.kts` to compile `ciphrchat-ffi` and place `.so` files in `src/main/jniLibs`.
    - Due to environment complexity, configure it so it doesn't break standard Android Studio Sync (e.g. only run rust build if a specific property is set, or gracefully skip if NDK is missing).
  </action>
  <verify>Ensure `build.gradle.kts` compiles (run `./gradlew tasks`).</verify>
  <done>Gradle is configured to handle the Rust JNI library.</done>
</task>

<task type="auto">
  <name>Implement InternetTransportAdapter via JNI</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\internet\RustP2pManager.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\internet\InternetTransportAdapter.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\TransportModule.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt</files>
  <action>
    - Create `RustP2pManager.kt` that calls `System.loadLibrary("ciphrchat_ffi")` and declares the `external fun startSwarm()`.
    - Create `InternetTransportAdapter.kt` utilizing `RustP2pManager` to start the libp2p network.
    - Remove the mocked `InternetTransportAdapter` from `AllTransportAdapters.kt` and update DI.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Android application loads the Rust library and wires the Internet adapter.</done>
</task>
