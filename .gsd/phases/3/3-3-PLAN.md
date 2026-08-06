---
phase: 3
plan: 3
wave: 3
---

# Plan 3.3: Wi-Fi Aware Transport

## Objective
Implement transport via Wi-Fi Aware (NAN) for device-to-device communication without a router or group owner overhead.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\WifiAwareTransportAdapter.kt

## Tasks

<task type="auto">
  <name>Implement WifiAwareManager integration</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\wifi\WifiAwareService.kt</files>
  <action>
    - Create `WifiAwareService` using `android.net.wifi.aware.WifiAwareManager`.
    - Implement Publish and Subscribe sessions using `ciphrchat_aware` service name.
    - Attach identity payload in the discovery `serviceSpecificInfo`.
  </action>
  <verify>Ensure `WifiAwareService` compiles.</verify>
  <done>WifiAware Publish and Subscribe sessions correctly start and report discovered peers.</done>
</task>

<task type="auto">
  <name>Update WifiAwareTransportAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\WifiAwareTransportAdapter.kt</files>
  <action>
    - Replace mock implementation.
    - Upon sending, request a `WifiAwareNetworkSpecifier` data path.
    - Open a standard Socket once the `Network` is provided by `ConnectivityManager`.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>WifiAware adapter manages end-to-end data paths via Aware APIs.</done>
</task>
