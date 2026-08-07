# Technology Stack

| Area | Technology | Purpose |
|---|---|---|
| Android | Kotlin, Jetpack Compose, Material 3 | Application UI and user flows |
| Android build | Gradle, Android Gradle Plugin, JDK 17 | Reproducible APK builds |
| Persistence | Room, SQLCipher for Android, Android Keystore | Local encrypted data and key protection |
| Messaging crypto | libsignal client Java APIs | Identity and session cryptography foundation |
| Native networking | Rust, Tokio, libp2p | Internet/P2P networking foundation |
| Android native bridge | JNI through `ciphrchat-ffi` | Connects Rust code to Android |
| Relay HTTP service | Rust, Axum | Health endpoint and future service surface |
| CI | GitHub Actions | APK build, lint, and artifact upload |
| Container deployment | Docker Compose | VPS-style relay deployment option |

## Build outputs

- Debug APK: `omnichat/apps/android/build/outputs/apk/debug/android-debug.apk`
- Relay container: `omnichat/services/bootstrap-relay/Dockerfile`
- Relay Compose definition: `omnichat/services/bootstrap-relay/compose.yaml`
