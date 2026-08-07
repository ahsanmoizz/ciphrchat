# Plan 4.1 Summary: BLE Advertisements and Discovery

## Completed Tasks
- Created `BleAdvertiser.kt` utilizing `BluetoothLeAdvertiser` to broadcast a custom OmniChat service UUID. It embeds a truncated segment of the device's public identity inside the advertisement service data, configuring low-latency/high-tx-power parameters.
- Created `BleScanner.kt` utilizing `BluetoothLeScanner` to filter and discover nearby OmniChat service broadcasts. It extracts the truncated identity from the service data, calculates reachability based on RSSI, and exposes the peers via a Coroutine `StateFlow`.

## Verification
- Code successfully builds and fully utilizes Android BLE APIs for discovering adjacent peers and transmitting identity chunks out-of-band.

**Status**: ✅ Complete
