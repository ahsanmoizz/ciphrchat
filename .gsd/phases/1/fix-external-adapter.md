---
phase: 1
plan: fix-external-adapter
wave: 1
gap_closure: true
---

# Fix Plan: Missing External Transport Adapter

## Problem
The `AllTransportAdapters.kt` implements 11 of the 12 required mock adapters, leaving `TransportKind.EXTERNAL` unaccounted for.

## Tasks

<task type="auto">
  <name>Add ExternalTransportAdapter</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\transport\adapters\AllTransportAdapters.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\TransportModule.kt</files>
  <action>Add `ExternalTransportAdapter` class to `AllTransportAdapters.kt` that inherits from `BaseMockTransportAdapter`, configured with `TransportKind.EXTERNAL`, `UNAVAILABLE` availability, and `EXTERNAL` capabilities. Then provide it in `TransportModule.kt` via `@Provides @IntoSet`.</action>
  <verify>Check both files for the new adapter and its DI provision.</verify>
  <done>External adapter is available in DI graph and registered in `TransportRegistry`.</done>
</task>
