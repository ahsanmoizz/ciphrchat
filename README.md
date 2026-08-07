# CiphrChat

**One identity. Every connection.**

CiphrChat is an open-source Android messaging app built around a local-first identity and multiple connection transports. The repository currently produces a working debug APK and includes Kotlin/Compose Android code plus Rust networking components.

> Development status: the app builds and passes CI, but it is not a production release or a completed security-audited messenger. Some restore, QR, nearby-device, and experimental transport actions are still placeholders.

## Try the Android app

<p>
  <a href="https://github.com/ahsanmoizz/ciphrchat/releases/download/latest/android-debug.apk">
    <strong>⬇ Download the latest Android APK</strong>
  </a>
</p>

The button downloads the latest build directly. On Android, open the downloaded APK and confirm **Install**. Android may ask you to allow your browser or file manager to install apps from unknown sources.

If the button is temporarily unavailable, use the GitHub Actions artifact:

1. Open the [Android CI workflow](https://github.com/ahsanmoizz/ciphrchat/actions/workflows/android.yml).
2. Open the latest run with a green check mark.
3. Download the `app-debug` artifact and unzip it.
4. Install `android-debug.apk` on an Android device.

You can also browse the [CiphrChat releases](https://github.com/ahsanmoizz/ciphrchat/releases).

You can install it with Android Debug Bridge as well:

```bash
adb install -r android-debug.apk
```

On the first launch:

1. Tap **Create my identity**.
2. Enter a display name.
3. Continue through **Enable connections** and **Identity ready**.
4. Tap **Start messaging** to open the app.

The current build contains sample conversations for UI testing. The **Connect** screen shows the intended connection model, but QR restore, nearby discovery, and invitation actions are not all wired to a complete production flow yet.

## Build from source

Requirements:

- Android Studio with Android SDK 37 and JDK 17
- Rust toolchain
- `cargo-ndk` for building the Rust Android libraries

From the repository root:

```bash
cd omnichat
./gradlew assembleDebug -PbuildRust       # macOS/Linux
./gradlew.bat assembleDebug -PbuildRust   # Windows
./gradlew lint
```

The APK is written to `omnichat/apps/android/build/outputs/apk/debug/android-debug.apk`.

Rust checks:

```bash
cd omnichat
cargo check --manifest-path services/bootstrap-relay/Cargo.toml
cargo test --workspace
```

## Relay status

The repository contains a small Rust HTTP service at `omnichat/services/bootstrap-relay`. It currently exposes:

```text
GET /health
```

It is useful as a deployment foundation and health endpoint. A public, always-on production relay has not been deployed yet. See [Architecture](docs/ARCHITECTURE.md) and [Stack](docs/STACK.md) for the current boundaries.

## License

[AGPL-3.0](omnichat/LICENSE)
