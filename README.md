# CiphrChat

**One identity. Every connection.**

CiphrChat is an open-source Android messaging app built around a persistent local identity, Signal Protocol sessions, and multiple direct or relayed transports. The repository includes the Android client, Rust/libp2p networking, and the deployable relay service.


## Install CiphrChat

<p>
  <a href="https://github.com/ahsanmoizz/ciphrchat/releases/download/latest/android-debug.apk">
    <strong>Download the latest CiphrChat Android APK</strong>
  </a>
</p>

Click the button above, open the downloaded `android-debug.apk` on your Android phone, and tap **Install**. Android may ask you to allow your browser or file manager to install apps from this source; allow it once and return to the installer.

This download always points to the newest build. You do not need to open GitHub Actions or download the source code. The [release page](https://github.com/ahsanmoizz/ciphrchat/releases/tag/latest) is available if you want the build details and checksum.

You can install it with Android Debug Bridge as well:

```bash
adb install -r android-debug.apk
```

On the first launch:

1. Tap **Create my identity**.
2. Enter a display name.
3. Continue through **Enable connections** and **Identity ready**.
4. Tap **Start messaging** to open the app.

For Internet messaging, first deploy the required relay and set the repository variable `CIPHRCHAT_RELAY_ADDRESS` to its multiaddress; follow the [deployment runbook](docs/DEPLOYMENT.md). Without it, the app correctly reports Internet transport as unavailable instead of pretending that messages were delivered. Pair contacts from **Connect** by scanning or pasting their invitation; invitations contain the peer address and Signal pre-key bundle.

The implemented message routes are Internet/libp2p relay, LAN, Wi-Fi Direct, Wi-Fi Aware, Bluetooth GATT, Bluetooth mesh, nearby audio, NFC tap sessions, UWB-assisted BLE delivery, and camera/IR optical delivery on compatible phones. Chat attachments preserve their MIME type and are encrypted end-to-end before transport; the current client limit is 512 KiB per attachment, so images, short videos, audio clips, PDFs, documents, and arbitrary files can be shared through the large-payload routes. Nearby audio, NFC, mesh, infrared, and UWB-assisted delivery remain device- and proximity-dependent. Live relay delivery and hardware interoperability still require the release test matrix. See [transport capabilities](docs/TRANSPORTS.md), [production status](docs/PRODUCTION_STATUS.md), and the [production verification audit](docs/PRODUCTION_VERIFICATION.md) for release gates.

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

### Signed Android release

Do not commit a keystore. Add these GitHub repository secrets before creating a `v*.*.*` tag or manually running [Android Release](https://github.com/ahsanmoizz/ciphrchat/actions/workflows/android-release.yml):

- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow fails if the keystore or any password is missing, builds the release with Rust JNI libraries, verifies it with `apksigner`, and publishes the APK plus SHA-256 checksum for a version tag.

## Relay deployment

The repository contains a deployable Rust/libp2p circuit-relay service at `omnichat/services/bootstrap-relay`. It exposes:

```text
GET /health
GET /info
```

Run it on a VPS with Docker Compose:

```bash
cd omnichat/services/bootstrap-relay
docker compose up -d --build
curl http://127.0.0.1:18081/health
```

Open TCP/UDP port `4001` in the VPS firewall. Do not open `18081` publicly; health/info stay on loopback and are checked over SSH. The relay identity is persisted in the `relay-data` volume, so do not delete that volume during upgrades. GitHub Actions also provides a manual **Deploy relay to VPS** workflow; configure `CIPHRCHAT_VPS_HOST`, `CIPHRCHAT_VPS_USER`, and `CIPHRCHAT_VPS_SSH_KEY` secrets (plus optional `CIPHRCHAT_VPS_PORT`) before running it.

Vercel is suitable for a web landing page or short HTTP API, but it is not a suitable host for this relay: libp2p requires a continuously running process with inbound TCP and UDP listeners. Use the VPS for the relay and Vercel only for optional web assets.

## License

[AGPL-3.0](omnichat/LICENSE)
