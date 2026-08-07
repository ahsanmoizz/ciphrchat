# Technology Stack

| Area | Technology | Purpose |
|---|---|---|
| Android | Kotlin, Jetpack Compose, Material 3 | Application UI and user flows |
| Android build | Gradle, Android Gradle Plugin, JDK 17 | Reproducible APK builds |
| Persistence | Room, SQLCipher for Android, Android Keystore | Local encrypted data and key protection |
| Messaging crypto | libsignal client Java APIs | Signal session establishment and message encryption |
| Native networking | Rust, Tokio, libp2p | Internet/P2P request-response and relay-client transport |
| Android native bridge | JNI through `ciphrchat-ffi` | Connects Rust code to Android |
| Relay service | Rust, Axum, libp2p | Circuit relay over TCP/QUIC plus health/info endpoints |
| CI | GitHub Actions | Android build/lint/release artifact and Rust/Docker validation |
| Container deployment | Docker Compose + manual GitHub workflow | Persistent-key VPS relay deployment |

## Build outputs

- Debug APK: `omnichat/apps/android/build/outputs/apk/debug/android-debug.apk`
- Relay container: `omnichat/services/bootstrap-relay/Dockerfile`
- Relay Compose definition: `omnichat/services/bootstrap-relay/compose.yaml`
