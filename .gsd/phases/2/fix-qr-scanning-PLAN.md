---
phase: 2
plan: fix-qr-scanning
wave: 1
gap_closure: true
---

# Fix Plan: QR Scanning Missing

## Problem
Phase 2 objective requires "contact QR generation and scanning", but only generation was implemented. We need a way to scan QR codes to add contacts.

## Tasks

<task type="auto">
  <name>Implement QR Scanner UI</name>
  <files>d:\ciphrchat\omnichat\apps\android\build.gradle.kts, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\ui\screens\ScannerScreen.kt</files>
  <action>
    - Add CameraX dependencies (`androidx.camera:camera-camera2`, `androidx.camera:camera-lifecycle`, `androidx.camera:camera-view`) to `build.gradle.kts` and `libs.versions.toml`.
    - Also add a barcode scanning library, such as Guava (required for CameraX) or just ZXing Android embedded if easier, but since ZXing core is there, we can use an `ImageAnalysis.Analyzer` with ZXing `MultiFormatReader`.
    - Create `ScannerScreen.kt` using `AndroidView` with `PreviewView` and `ImageAnalysis` to detect QR codes.
    - Handle camera permissions gracefully.
  </action>
  <verify>Check that code structure implements `ImageAnalysis.Analyzer` passing frames to ZXing.</verify>
  <done>ScannerScreen can decode `ciphr:` QR codes from the camera.</done>
</task>

<task type="auto">
  <name>Wire Scanner to Connect Tab</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\ui\screens\ConnectScreen.kt</files>
  <action>
    - Update `ConnectScreen` to include a "Scan QR Code" button.
    - When clicked, it should navigate to the `ScannerScreen` (simulated by a callback or state variable).
  </action>
  <verify>Check that `ConnectScreen` has the scan button wired.</verify>
  <done>User can initiate a scan from the Connect tab.</done>
</task>
