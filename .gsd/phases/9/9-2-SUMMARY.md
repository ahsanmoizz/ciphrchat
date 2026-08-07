# Plan 9.2 Summary: Message Routing and P2P Bridge

## Completed Tasks
- Injected `TransportManager` into `AutomaticRouter` so that constructed Signal encrypted envelopes are actually broadcast via `transportManager.send()`.
- Added `publishMessage` and `onMessageReceived` (callback) JNI functions to `RustP2pManager.kt`.
- Exported the `Java_..._publishMessage` JNI method in `ciphrchat-ffi` to allow Kotlin to push data to the Rust libp2p swarm.

## Verification
- Cross-boundary bridging definitions are compiled and the router actively offloads envelopes instead of dropping them.

**Status**: ✅ Complete
