---
phase: 9
plan: fix-wifiaware-hardcoding
wave: 1
gap_closure: true
---

# Fix: Wi-Fi Aware Hardcoding

## Problem
`WifiAwareService` and `WifiAwareTransportAdapter` use a hardcoded PSK (`"OmniChatSecurePsk"`) and IPv6 address (`fe80::1`), circumventing the actual dynamic discovery and secure pairing flow.

## Root Cause
Mocked during Phase 3 to establish compile-time pipelines.

## Tasks

<task type="auto">
  <name>Fix WifiAwareService</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\wifi\WifiAwareService.kt</files>
  <action>
    - Remove the hardcoded PSK.
    - If a PSK is used for the network specifier, derive it dynamically from the recipient's public identity key or pass it via `OutboundEnvelope` / Transport manager configuration.
  </action>
  <verify>Ensure `WifiAwareService` compiles.</verify>
  <done>Hardcoded PSK is removed.</done>
</task>

<task type="auto">
  <name>Fix WifiAwareTransportAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\wifi\WifiAwareTransportAdapter.kt</files>
  <action>
    - Remove the hardcoded `fe80::1` IPv6 address and `12347` port.
    - Extract the IP/Port dynamically from the `WifiAwareNetworkInfo` object provided during discovery callbacks.
  </action>
  <verify>Ensure `WifiAwareTransportAdapter` compiles.</verify>
  <done>Network connections use dynamically discovered peers instead of hardcoded IPs.</done>
</task>
