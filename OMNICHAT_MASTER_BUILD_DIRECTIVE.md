# OMNICHAT MASTER BUILD DIRECTIVE

**Document purpose:** Give a coding assistant a fixed product, architecture, UI, repository plan, file map, starter implementation, validation checklist, and strict execution order. The assistant must read this file and implement it. It must not redesign the product.

**Product name:** OmniChat  
**Tagline:** One identity. Every connection.  
**Primary platform:** Android  
**License:** AGPL-3.0  
**Primary use:** Minimal encrypted text messaging through every communication route genuinely available on the device.

---

## 0. EXECUTION TRUTH AND NON-NEGOTIABLE BOUNDARY

A complete, production-secure messenger supporting Internet P2P, relay fallback, Wi-Fi LAN, Wi-Fi Direct, Wi-Fi Aware, Bluetooth direct, Bluetooth mesh, ultrasound, infrared adapters, NFC, UWB assistance, encrypted storage, identity recovery, QR pairing, APK sharing, and audited end-to-end encryption cannot honestly be completed in one hour.

The one-hour target is therefore a **runnable Phase-1 foundation** that proves the design and gives the next developer no architectural decisions to make.

### One-hour deliverable

The assistant must produce:

1. A compiling Android application.
2. The exact white, black, and light-purple UI described below.
3. Welcome, permission setup, identity-ready, chats, chat, connect, and settings screens.
4. A locally generated prototype identity.
5. A fake local conversation repository so the UI works immediately.
6. A transport capability detector that labels methods as available, unavailable, permission-required, or experimental.
7. A transport abstraction and automatic routing policy with mock adapters.
8. A local pending-message queue interface.
9. APK sharing through the Android share sheet.
10. A Rust workspace containing protocol, identity, routing, and transport interfaces with tests.
11. A server directory containing a bootstrap-relay service scaffold and health endpoint.
12. GitHub Actions files for Android and Rust checks.
13. Documentation explaining what is real, what is mocked, and what remains.

### The assistant must not claim the following are complete in one hour

- Production end-to-end encryption.
- Signal Protocol session integration.
- Real Bluetooth mesh forwarding.
- Real Wi-Fi Direct or Wi-Fi Aware transfer.
- Real ultrasound modem reliability.
- Phone-to-phone infrared communication on unsupported hardware.
- Pure UWB message transport.
- Internet NAT traversal and relay delivery.
- Security audit or production readiness.

The initial code must use interfaces and safe stubs for unfinished security-sensitive components. It must never fake encryption by Base64 encoding or claim placeholder logic is secure.

---

# 1. PRODUCT DEFINITION

## 1.1 One-sentence definition

**OmniChat is an open-source, local-first encrypted text messenger that keeps one identity and one conversation while automatically moving messages through Internet, Wi-Fi, Bluetooth mesh, and every other communication method genuinely supported by the device.**

## 1.2 Product promise

A normal user downloads one APK, installs it, creates one identity, enables available connections, adds another person, and starts messaging. The user does not need to understand networking, ports, relays, radio protocols, or cryptography.

## 1.3 Core principles

1. **One identity:** A user has one cryptographic identity across all transports.
2. **One conversation:** A chat does not split when the transport changes.
3. **Automatic routing:** The application selects the best available route.
4. **Local-first:** Contacts, messages, keys, and queues live on the user’s device.
5. **Text first:** Text messaging is the main function.
6. **Open source:** Source, builds, protocol, threat model, and releases are public.
7. **Minimal interface:** No feed, stories, status posts, wallet, advertisements, or public profiles.
8. **Honest capabilities:** A transport is shown only when the device and operating system expose the required hardware/API.
9. **Safe failure:** When no route works, the encrypted message remains queued locally.
10. **No custom cryptographic invention:** Use reviewed protocols and libraries for production encryption.

## 1.4 Explicit non-features

Do not add:

- Social status or story system.
- Public timeline.
- Cryptocurrency or blockchain.
- Payments.
- Advertisement SDKs.
- Tracking analytics.
- Central phone-number account registration.
- Central email account registration.
- Public discoverable usernames in the first release.
- Voice or video calls in the first release.
- Large public groups.
- Bots or mini-apps.
- AI assistant inside chats.

---

# 2. FEATURE MAP

## 2.1 Foundation release

### Identity

- Create identity locally.
- Display name chosen by user.
- Stable public OmniChat ID.
- Private key material stored through Android Keystore-backed protection in production.
- Show personal QR invitation.
- Scan a contact invitation.
- Export encrypted recovery package later.
- Restore identity later.

### Messaging

- One-to-one text messages.
- Local timestamps.
- Delivery states: queued, routing, sent, delivered, failed.
- Retry queued messages.
- Block contact.
- Delete local conversation.
- No read receipts by default.
- No typing indicator in the first release.

### Connections

- Internet/mobile data.
- Existing shared Wi-Fi LAN.
- Wi-Fi Direct.
- Wi-Fi Aware where supported.
- Bluetooth Low Energy discovery.
- Bluetooth data path.
- Bluetooth mesh forwarding.
- Ultrasound experimental adapter.
- Infrared experimental adapter where a usable emitter/receiver path exists.
- NFC pairing.
- UWB nearby verification and handoff assistance.
- Future external adapter interface for LoRa, USB radio, or other hardware.

### Distribution

- Website download button.
- GitHub Release APK.
- F-Droid later.
- Google Play later.
- Share installed APK through Bluetooth, Wi-Fi, Quick Share, USB, or local network.
- QR installation link.
- Offline QR points to a local Wi-Fi APK endpoint, not the APK bytes themselves.

## 2.2 Later capabilities

- Small encrypted groups.
- Images and files only on suitable transports.
- Multiple devices per identity.
- iOS client.
- Desktop client.
- Optional encrypted offline mailbox.
- Self-hostable bootstrap and relay nodes.
- External radio adapters.

---

# 3. COMMUNICATION ROUTES

## 3.1 Route list

| Route | Purpose | Initial implementation state |
|---|---|---|
| Internet direct | Text and later attachments | Adapter scaffold, then libp2p |
| Internet relay | Live fallback when direct connection fails | Server scaffold, then libp2p relay |
| Shared Wi-Fi LAN | Fast local text and files | Adapter scaffold, then NSD + sockets |
| Wi-Fi Direct | Phone-to-phone without router | Adapter scaffold |
| Wi-Fi Aware | Nearby discovery and data path | Capability detection + adapter scaffold |
| Bluetooth BLE | Discovery and small packets | Capability detection + adapter scaffold |
| Bluetooth Classic/GATT | Direct text/data | Adapter scaffold |
| Bluetooth mesh | Multi-hop encrypted envelopes | Routing interfaces + simulator first |
| Ultrasound | Tiny packets, invitation, acknowledgement | Experimental adapter scaffold |
| Infrared | Tiny experimental output on supported hardware | Capability-only scaffold |
| NFC | Tap/near pairing and invitation exchange | Pairing adapter scaffold |
| UWB | Nearby verification and handoff | Capability-only scaffold |
| QR | Contact and installation bootstrap | UI and data format scaffold |
| USB/external hardware | Future plugin | Interface only |

## 3.2 Automatic route priority

The default routing order is:

1. Existing direct secure session over Internet.
2. Shared Wi-Fi LAN direct connection.
3. Wi-Fi Aware data path.
4. Wi-Fi Direct.
5. Direct Bluetooth connection.
6. Bluetooth mesh forwarding.
7. Ultrasound for a small supported payload.
8. Infrared only if the complete path is confirmed.
9. Internet circuit relay.
10. Queue locally and retry.

The router may change priority based on:

- Recipient reachability.
- Payload size.
- User-disabled methods.
- Device battery state.
- Permission state.
- Current network cost.
- Transport reliability score.
- Expected latency.
- Security policy.

## 3.3 Transport rules

Every transport implements the same contract:

```kotlin
interface TransportAdapter {
    val kind: TransportKind
    val capabilities: Set<TransportCapability>
    val state: StateFlow<TransportState>

    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun discoverPeers(): Result<List<DiscoveredPeer>>
    suspend fun canReach(recipientId: String): Reachability
    suspend fun send(envelope: OutboundEnvelope): SendResult
}
```

Transport code must receive an encrypted envelope in production. It must not receive raw plaintext message text.

---

# 4. IDENTITY AND MESSAGE MODEL

## 4.1 OmniChat ID

Example display form:

```text
omni:7F4A-29BC-91DE-4A10
```

The user sees a shortened fingerprint. The full identity is represented in the invitation payload.

## 4.2 Invitation payload

Versioned JSON for the prototype:

```json
{
  "type": "omnichat.invite",
  "version": 1,
  "displayName": "Ahsan",
  "publicIdentity": "BASE64_PUBLIC_IDENTITY",
  "fingerprint": "7F4A-29BC-91DE-4A10",
  "rendezvous": [],
  "createdAt": 1786057200000
}
```

Production should move to deterministic CBOR or Protocol Buffers and sign the invitation.

## 4.3 Message model

```kotlin
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAtEpochMs: Long,
    val direction: MessageDirection,
    val status: MessageStatus,
    val selectedTransport: TransportKind? = null,
    val routeDetails: String? = null
)
```

## 4.4 Encrypted envelope model

```kotlin
data class OutboundEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val recipientTag: ByteArray,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val hopLimit: Int,
    val payloadType: PayloadType,
    val encryptedPayload: ByteArray,
    val signature: ByteArray,
    val padding: ByteArray = byteArrayOf()
)
```

The prototype may use a `MockCryptoEngine` that clearly throws for network encryption or returns test-only tagged bytes. It must be impossible to accidentally label mock output as production-secure.

---

# 5. USER EXPERIENCE

## 5.1 Main navigation

Exactly three main destinations:

```text
Chats        Connect        Settings
```

Do not add more bottom navigation items.

## 5.2 First launch flow

1. Welcome.
2. Create or restore identity.
3. Explain available connection categories.
4. Request permissions in context.
5. Show identity ready screen.
6. Enter main application.

## 5.3 Normal messaging flow

1. User opens Chats.
2. User selects contact.
3. User enters text.
4. User taps purple send button.
5. Message immediately appears as queued.
6. Router tests available routes.
7. Message status changes to routing, sent, delivered, or failed.
8. Route icon can appear subtly beside the message.

The user must not choose a transport for every message. Manual route selection belongs only in Advanced settings and a developer diagnostics screen.

---

# 6. VISUAL DESIGN SYSTEM

## 6.1 Design direction

Use the visual direction described by the supplied Changelly reference:

- White main surface.
- Very pale lavender and blue ambient background glow.
- Rounded white cards.
- Strong black text.
- Soft grey secondary text.
- Light-purple primary buttons with black labels.
- Large breathing space.
- Minimal borders.
- No visual clutter.
- No cyberpunk neon.
- No dark crypto-exchange look.
- No fake glassmorphism overload.

## 6.2 Color tokens

```kotlin
val OmniBackground = Color(0xFFF7F8FC)
val OmniSurface = Color(0xFFFFFFFF)
val OmniSurfaceMuted = Color(0xFFF3F3F8)
val OmniPrimary = Color(0xFFC7B5FF)
val OmniPrimaryHover = Color(0xFFB49AFF)
val OmniPrimarySoft = Color(0xFFEEE9FF)
val OmniText = Color(0xFF111111)
val OmniTextSecondary = Color(0xFF747681)
val OmniBorder = Color(0xFFE3E4EC)
val OmniSuccess = Color(0xFF25845D)
val OmniWarning = Color(0xFFA66A16)
val OmniDanger = Color(0xFFC74D5B)
```

## 6.3 Shape tokens

```kotlin
val OmniCardShape = RoundedCornerShape(20.dp)
val OmniControlShape = RoundedCornerShape(14.dp)
val OmniButtonShape = RoundedCornerShape(18.dp)
val OmniPillShape = RoundedCornerShape(999.dp)
```

## 6.4 Spacing

Use an 8dp system:

```text
4dp  micro
8dp  compact
12dp small
16dp normal
24dp section
32dp large
48dp hero
```

## 6.5 Typography

- Use system sans-serif initially.
- Hero: 34sp, semi-bold.
- Screen title: 28sp, semi-bold.
- Card title: 18sp, medium.
- Body: 16sp, regular.
- Supporting: 14sp, regular.
- Small labels: 12sp, medium.
- Avoid all-uppercase headings except tiny technical status labels.

## 6.6 Core components

Create reusable components:

- `OmniPrimaryButton`
- `OmniSecondaryButton`
- `OmniTextButton`
- `OmniCard`
- `OmniTopBar`
- `OmniBottomNavigation`
- `OmniSearchField`
- `OmniStatusPill`
- `OmniTransportRow`
- `OmniEmptyState`
- `OmniMessageBubble`
- `OmniPermissionCard`
- `OmniIdentityCard`
- `OmniSectionHeader`

---

# 7. EXACT SCREEN SPECIFICATION

## 7.1 Welcome screen

Content:

```text
[OmniChat logo mark]

OmniChat
One identity. Every connection.

Communicate through internet, Wi-Fi, Bluetooth and every supported connection around you.

[ Create my identity ]
Restore an identity

Open source • Local first • No phone number
```

Behavior:

- Primary button advances to identity creation.
- Restore opens a disabled “Coming in Phase 2” sheet in the first-hour scaffold.
- No permission prompts on this screen.

## 7.2 Create identity screen

Fields:

- Display name.
- Optional device label hidden under Advanced.

Actions:

- `Create identity`.
- Validate display name is 1–40 characters.
- Generate prototype identity locally.
- Never require phone number or email.

## 7.3 Enable communication screen

Header:

```text
Enable communication
OmniChat uses the connection methods available on this device.
```

Rows:

- Internet.
- Wi-Fi and Wi-Fi Direct.
- Bluetooth and mesh.
- Nearby audio.
- NFC.
- UWB.
- Infrared when available.

Main action:

```text
[ Enable available connections ]
```

Permissions must be requested progressively. Do not request every permission blindly before explaining it.

## 7.4 Identity ready screen

Show:

- Display name.
- Short ID fingerprint.
- QR placeholder or generated QR.
- `Show my QR`.
- `Start messaging`.

## 7.5 Chats screen

Layout:

- Header `Messages`.
- Purple circular plus action.
- Search field.
- Conversation rows.
- Empty state if no chats.

Conversation row contains:

- Initial avatar.
- Contact name.
- Last message.
- Time.
- Small delivery indicator.
- No story rings.

## 7.6 Chat screen

Top bar:

- Back.
- Contact name.
- `Secure conversation` subtitle.
- Overflow menu.

Body:

- Incoming and outgoing message bubbles.
- Date separator only when useful.
- Route status displayed subtly.

Composer:

- Rounded text field.
- Send button.
- Attachment icon disabled or hidden in initial release.

## 7.7 Connect screen

Top section:

```text
Add someone
[ Scan QR ]
[ Show my QR ]
[ Find nearby ]
[ Enter invitation ]
```

Second section:

```text
Connection methods
Automatic routing                  On
Internet                           Available
Wi-Fi                              Searching
Bluetooth                          Permission needed
Ultrasound                         Experimental
Infrared                           Not supported
UWB                                Nearby assist
```

A small `Advanced` link opens diagnostics later.

## 7.8 Settings screen

Sections:

### Identity

- My QR code.
- Display name.
- Back up identity.
- Restore identity.

### Connections

- Automatic routing.
- Internet.
- Wi-Fi.
- Bluetooth mesh.
- Experimental methods.

### Privacy

- App lock.
- Local storage.
- Blocked contacts.
- Delete local data.

### Distribution

- Share OmniChat.
- Verify installed build.
- Check for update.

### About

- Source code.
- Open-source licenses.
- Security model.
- Version.

---

# 8. INSTALLATION AND SHARING

## 8.1 Website

Main CTA:

```text
Download OmniChat for Android
```

The site provides:

- Latest stable APK.
- Version.
- SHA-256 checksum.
- Signing certificate fingerprint.
- GitHub source link.
- Installation instructions.

## 8.2 GitHub

Normal users download the signed APK from Releases. Developers may clone, fork, or download the source ZIP.

Repository ZIP is not directly installable. The release APK is installable.

## 8.3 Offline sharing

Inside Settings:

```text
Share OmniChat
```

The app shares the installed/saved official APK through Android’s share sheet. Supported receiving paths depend on installed Android apps and system services, such as Bluetooth, Quick Share, Wi-Fi tools, USB file transfer, or local file managers.

The first-hour implementation may share a bundled release file only if it exists. Otherwise it should share the official download page and clearly label direct APK sharing as pending.

## 8.4 QR installation

Online QR:

- Encodes official HTTPS download page.

Offline QR:

- Sender starts temporary local HTTP server over Wi-Fi/hotspot.
- QR encodes local URL and checksum.
- Recipient downloads APK directly.

Offline APK hosting is a later feature. The first-hour screen should show the online QR or a placeholder.

---

# 9. FINAL TECHNOLOGY STACK

## 9.1 Android

- Kotlin.
- Jetpack Compose.
- Material 3 components, visually overridden by OmniChat theme.
- Navigation Compose.
- ViewModel.
- Kotlin Coroutines and Flow.
- Hilt for dependency injection.
- WorkManager for persistent retry jobs.
- Android Keystore for wrapping local key material.
- SQLCipher-backed SQLite in production.
- QR generation/scanning library selected only after license review.

## 9.2 Shared core

- Rust.
- UniFFI Kotlin bindings.
- Protocol types.
- Routing policy.
- Identity primitives.
- Serialization.
- Later libsignal adapter.
- Later rust-libp2p transport core.

## 9.3 Server

- Rust.
- Tokio.
- Axum health endpoint.
- rust-libp2p bootstrap and circuit relay.
- Docker.
- Caddy or another simple TLS reverse proxy.
- No central account database.
- No plaintext message database.

## 9.4 Pinned build baseline

Use this baseline unless the environment proves a compatibility problem:

```text
JDK:                 17
Android Gradle Plugin: 9.2.0
Gradle:              9.4.1
Kotlin:              2.3.21
Dagger/Hilt:         2.60.1
Compose BOM:         2026.04.01
WorkManager:         2.11.2
AndroidX Hilt:       1.4.0
SQLCipher Android:   4.17.0
AndroidX SQLite:     2.6.2
rust-libp2p:         0.56.0
libsignal:           0.94.1 when integration begins
```

Do not use dynamic dependency versions such as `9.2.+`.

---

# 10. REPOSITORY STRUCTURE

```text
omnichat/
├── README.md
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── .editorconfig
├── .gitignore
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── apps/
│   └── android/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── java/org/omnichat/app/
│           │   │   ├── OmniChatApplication.kt
│           │   │   ├── MainActivity.kt
│           │   │   ├── app/
│           │   │   │   ├── OmniChatApp.kt
│           │   │   │   ├── AppRoute.kt
│           │   │   │   └── AppState.kt
│           │   │   ├── ui/
│           │   │   │   ├── theme/
│           │   │   │   │   ├── Color.kt
│           │   │   │   │   ├── Shape.kt
│           │   │   │   │   ├── Type.kt
│           │   │   │   │   └── Theme.kt
│           │   │   │   ├── components/
│           │   │   │   │   ├── OmniButtons.kt
│           │   │   │   │   ├── OmniCard.kt
│           │   │   │   │   ├── OmniTopBar.kt
│           │   │   │   │   ├── OmniBottomBar.kt
│           │   │   │   │   ├── OmniMessageBubble.kt
│           │   │   │   │   └── OmniTransportRow.kt
│           │   │   │   └── screens/
│           │   │   │       ├── WelcomeScreen.kt
│           │   │   │       ├── CreateIdentityScreen.kt
│           │   │   │       ├── EnableConnectionsScreen.kt
│           │   │   │       ├── IdentityReadyScreen.kt
│           │   │   │       ├── ChatsScreen.kt
│           │   │   │       ├── ChatScreen.kt
│           │   │   │       ├── ConnectScreen.kt
│           │   │   │       └── SettingsScreen.kt
│           │   │   ├── identity/
│           │   │   │   ├── IdentityModels.kt
│           │   │   │   ├── IdentityRepository.kt
│           │   │   │   └── PrototypeIdentityRepository.kt
│           │   │   ├── messaging/
│           │   │   │   ├── MessageModels.kt
│           │   │   │   ├── MessageRepository.kt
│           │   │   │   ├── InMemoryMessageRepository.kt
│           │   │   │   ├── ChatsViewModel.kt
│           │   │   │   └── ChatViewModel.kt
│           │   │   ├── transport/
│           │   │   │   ├── TransportModels.kt
│           │   │   │   ├── TransportAdapter.kt
│           │   │   │   ├── TransportRegistry.kt
│           │   │   │   ├── AutomaticRouter.kt
│           │   │   │   ├── AndroidCapabilityDetector.kt
│           │   │   │   └── adapters/
│           │   │   │       ├── InternetTransportAdapter.kt
│           │   │   │       ├── LanTransportAdapter.kt
│           │   │   │       ├── WifiDirectTransportAdapter.kt
│           │   │   │       ├── WifiAwareTransportAdapter.kt
│           │   │   │       ├── BluetoothTransportAdapter.kt
│           │   │   │       ├── BluetoothMeshTransportAdapter.kt
│           │   │   │       ├── UltrasoundTransportAdapter.kt
│           │   │   │       ├── InfraredTransportAdapter.kt
│           │   │   │       ├── NfcPairingAdapter.kt
│           │   │   │       └── UwbAssistAdapter.kt
│           │   │   ├── permissions/
│           │   │   │   ├── PermissionModels.kt
│           │   │   │   └── PermissionCoordinator.kt
│           │   │   ├── sharing/
│           │   │   │   ├── AppShareManager.kt
│           │   │   │   └── InstallQrPayload.kt
│           │   │   ├── di/
│           │   │   │   ├── AppModule.kt
│           │   │   │   └── TransportModule.kt
│           │   │   └── worker/
│           │   │       └── PendingMessageRetryWorker.kt
│           │   └── res/
│           │       ├── drawable/
│           │       ├── mipmap-anydpi-v26/
│           │       ├── values/
│           │       │   ├── strings.xml
│           │       │   └── themes.xml
│           │       └── xml/
│           │           ├── file_paths.xml
│           │           └── backup_rules.xml
│           ├── test/
│           └── androidTest/
├── crates/
│   ├── Cargo.toml
│   ├── omnichat-core/
│   │   ├── Cargo.toml
│   │   └── src/lib.rs
│   ├── omnichat-protocol/
│   │   ├── Cargo.toml
│   │   └── src/lib.rs
│   ├── omnichat-routing/
│   │   ├── Cargo.toml
│   │   └── src/lib.rs
│   └── omnichat-ffi/
│       ├── Cargo.toml
│       └── src/lib.rs
├── services/
│   └── bootstrap-relay/
│       ├── Cargo.toml
│       ├── Dockerfile
│       ├── compose.yaml
│       └── src/main.rs
├── docs/
│   ├── PRODUCT.md
│   ├── UI_SPEC.md
│   ├── ARCHITECTURE.md
│   ├── PROTOCOL.md
│   ├── THREAT_MODEL.md
│   ├── TRANSPORTS.md
│   ├── DISTRIBUTION.md
│   └── ONE_HOUR_RESULT.md
├── scripts/
│   ├── verify.sh
│   └── verify.ps1
└── .github/
    └── workflows/
        ├── android.yml
        ├── rust.yml
        └── release.yml
```

---

# 11. ANDROID BUILD FILES

## 11.1 Root `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://build-artifacts.signal.org/libraries/maven/")
    }
}

rootProject.name = "OmniChat"
include(":apps:android")
```

Do not add libsignal as an active dependency during the one-hour scaffold unless the integration is actually implemented and tested.

## 11.2 Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

## 11.3 `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.2.0"
kotlin = "2.3.21"
compose-bom = "2026.04.01"
activity-compose = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.9.8"
work = "2.11.2"
hilt = "2.60.1"
androidx-hilt = "1.4.0"
ksp = "2.3.11"
core-ktx = "1.19.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidx-hilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidx-hilt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

If any listed secondary AndroidX version is not resolvable in the actual environment, use the latest stable version visible in the installed Android Studio dependency assistant and record the change in `docs/ONE_HOUR_RESULT.md`. Do not silently use alpha dependencies.

## 11.4 Android module `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.omnichat.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.omnichat.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

---

# 12. ANDROID MANIFEST

Use only permissions needed for declared features. Runtime permission logic must handle Android version differences.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.NFC" />
    <uses-permission android:name="android.permission.UWB_RANGING" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
    <uses-feature android:name="android.hardware.wifi.direct" android:required="false" />
    <uses-feature android:name="android.hardware.wifi.aware" android:required="false" />
    <uses-feature android:name="android.hardware.nfc" android:required="false" />
    <uses-feature android:name="android.hardware.uwb" android:required="false" />
    <uses-feature android:name="android.hardware.consumerir" android:required="false" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />

    <application
        android:name=".OmniChatApplication"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:dataExtractionRules="@xml/backup_rules"
        android:label="OmniChat"
        android:theme="@style/Theme.OmniChat">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

Do not assume every listed permission is available on every Android API level. Guard runtime requests and feature calls.

---

# 13. STARTER KOTLIN IMPLEMENTATION

## 13.1 Application

```kotlin
package org.omnichat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmniChatApplication : Application()
```

## 13.2 Main activity

```kotlin
package org.omnichat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.omnichat.app.app.OmniChatApp
import org.omnichat.app.ui.theme.OmniChatTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniChatTheme {
                OmniChatApp()
            }
        }
    }
}
```

## 13.3 Routes

```kotlin
package org.omnichat.app.app

sealed class AppRoute(val route: String) {
    data object Welcome : AppRoute("welcome")
    data object CreateIdentity : AppRoute("create_identity")
    data object EnableConnections : AppRoute("enable_connections")
    data object IdentityReady : AppRoute("identity_ready")
    data object Chats : AppRoute("chats")
    data object Connect : AppRoute("connect")
    data object Settings : AppRoute("settings")
    data object Chat : AppRoute("chat/{conversationId}") {
        fun create(conversationId: String) = "chat/$conversationId"
    }
}
```

## 13.4 Theme colors

```kotlin
package org.omnichat.app.ui.theme

import androidx.compose.ui.graphics.Color

val OmniBackground = Color(0xFFF7F8FC)
val OmniSurface = Color(0xFFFFFFFF)
val OmniSurfaceMuted = Color(0xFFF3F3F8)
val OmniPrimary = Color(0xFFC7B5FF)
val OmniPrimaryHover = Color(0xFFB49AFF)
val OmniPrimarySoft = Color(0xFFEEE9FF)
val OmniText = Color(0xFF111111)
val OmniTextSecondary = Color(0xFF747681)
val OmniBorder = Color(0xFFE3E4EC)
val OmniSuccess = Color(0xFF25845D)
val OmniWarning = Color(0xFFA66A16)
val OmniDanger = Color(0xFFC74D5B)
```

## 13.5 Theme

```kotlin
package org.omnichat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OmniColorScheme = lightColorScheme(
    primary = OmniPrimary,
    onPrimary = OmniText,
    primaryContainer = OmniPrimarySoft,
    onPrimaryContainer = OmniText,
    background = OmniBackground,
    onBackground = OmniText,
    surface = OmniSurface,
    onSurface = OmniText,
    surfaceVariant = OmniSurfaceMuted,
    onSurfaceVariant = OmniTextSecondary,
    outline = OmniBorder,
    error = OmniDanger
)

@Composable
fun OmniChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmniColorScheme,
        typography = OmniTypography,
        shapes = OmniShapes,
        content = content
    )
}
```

## 13.6 Primary button

```kotlin
package org.omnichat.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.omnichat.app.ui.theme.OmniButtonShape
import org.omnichat.app.ui.theme.OmniPrimary
import org.omnichat.app.ui.theme.OmniText

@Composable
fun OmniPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = OmniButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = OmniPrimary,
            contentColor = OmniText
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
```

## 13.7 Identity models

```kotlin
package org.omnichat.app.identity

data class LocalIdentity(
    val displayName: String,
    val publicId: String,
    val fingerprint: String,
    val createdAtEpochMs: Long
)

interface IdentityRepository {
    suspend fun create(displayName: String): Result<LocalIdentity>
    suspend fun current(): LocalIdentity?
    suspend fun clear(): Result<Unit>
}
```

## 13.8 Prototype identity repository

This is only for the first-hour scaffold. It is not the final cryptographic identity implementation.

```kotlin
package org.omnichat.app.identity

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrototypeIdentityRepository @Inject constructor() : IdentityRepository {
    private val random = SecureRandom()
    @Volatile private var identity: LocalIdentity? = null

    override suspend fun create(displayName: String): Result<LocalIdentity> = runCatching {
        require(displayName.trim().length in 1..40) { "Display name must be 1–40 characters" }
        val bytes = ByteArray(16).also(random::nextBytes)
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val fingerprint = hex.chunked(4).take(4).joinToString("-")
        LocalIdentity(
            displayName = displayName.trim(),
            publicId = "omni:$hex",
            fingerprint = fingerprint,
            createdAtEpochMs = System.currentTimeMillis()
        ).also { identity = it }
    }

    override suspend fun current(): LocalIdentity? = identity

    override suspend fun clear(): Result<Unit> = runCatching {
        identity = null
    }
}
```

The production repository must use a stable cryptographic keypair, protected persistence, and signed identity data. Do not persist this prototype identity as plaintext and call it secure.

## 13.9 Transport models

```kotlin
package org.omnichat.app.transport

enum class TransportKind {
    INTERNET_DIRECT,
    INTERNET_RELAY,
    WIFI_LAN,
    WIFI_AWARE,
    WIFI_DIRECT,
    BLUETOOTH_DIRECT,
    BLUETOOTH_MESH,
    ULTRASOUND,
    INFRARED,
    NFC_PAIRING,
    UWB_ASSIST,
    EXTERNAL
}

enum class TransportAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    DISABLED_BY_USER,
    EXPERIMENTAL,
    STARTING,
    ERROR
}

data class TransportState(
    val kind: TransportKind,
    val availability: TransportAvailability,
    val detail: String,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

enum class TransportCapability {
    DISCOVERY,
    SMALL_TEXT,
    LARGE_PAYLOAD,
    MULTI_HOP,
    PAIRING,
    RANGING,
    OFFLINE
}

data class DiscoveredPeer(
    val ephemeralId: String,
    val displayHint: String?,
    val transport: TransportKind,
    val signalHint: Int? = null
)

sealed interface Reachability {
    data object Reachable : Reachability
    data object Unknown : Reachability
    data class Unreachable(val reason: String) : Reachability
}

sealed interface SendResult {
    data class Accepted(val transport: TransportKind, val routeId: String) : SendResult
    data class Rejected(val reason: String) : SendResult
    data class Failed(val error: Throwable) : SendResult
}
```

## 13.10 Transport adapter

```kotlin
package org.omnichat.app.transport

import kotlinx.coroutines.flow.StateFlow

interface TransportAdapter {
    val kind: TransportKind
    val capabilities: Set<TransportCapability>
    val state: StateFlow<TransportState>

    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun discoverPeers(): Result<List<DiscoveredPeer>>
    suspend fun canReach(recipientId: String): Reachability
    suspend fun send(envelope: OutboundEnvelope): SendResult
}
```

## 13.11 Envelope placeholder

```kotlin
package org.omnichat.app.transport

data class OutboundEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val recipientId: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val hopLimit: Int,
    val encryptedPayload: ByteArray,
    val testOnly: Boolean
)
```

Any envelope created by mock crypto must set `testOnly = true`. Real transport adapters must reject `testOnly` envelopes in release builds.

## 13.12 Base mock adapter

```kotlin
package org.omnichat.app.transport.adapters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.omnichat.app.transport.*
import java.util.UUID

abstract class BaseMockTransportAdapter(
    final override val kind: TransportKind,
    initialAvailability: TransportAvailability,
    initialDetail: String,
    final override val capabilities: Set<TransportCapability>
) : TransportAdapter {

    private val mutableState = MutableStateFlow(
        TransportState(kind, initialAvailability, initialDetail)
    )
    final override val state: StateFlow<TransportState> = mutableState

    override suspend fun start(): Result<Unit> = runCatching {
        mutableState.value = mutableState.value.copy(
            availability = TransportAvailability.AVAILABLE,
            detail = "Prototype adapter ready"
        )
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        mutableState.value = mutableState.value.copy(
            availability = TransportAvailability.DISABLED_BY_USER,
            detail = "Disabled"
        )
    }

    override suspend fun discoverPeers(): Result<List<DiscoveredPeer>> = Result.success(emptyList())

    override suspend fun canReach(recipientId: String): Reachability {
        return if (state.value.availability == TransportAvailability.AVAILABLE) {
            Reachability.Reachable
        } else {
            Reachability.Unknown
        }
    }

    override suspend fun send(envelope: OutboundEnvelope): SendResult {
        if (state.value.availability != TransportAvailability.AVAILABLE) {
            return SendResult.Rejected("Transport is not available")
        }
        return SendResult.Accepted(kind, UUID.randomUUID().toString())
    }
}
```

## 13.13 Example adapters

```kotlin
@Singleton
class InternetTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.INTERNET_DIRECT,
    initialAvailability = TransportAvailability.AVAILABLE,
    initialDetail = "Network available; real P2P not connected",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.LARGE_PAYLOAD
    )
)
```

```kotlin
@Singleton
class UltrasoundTransportAdapter @Inject constructor() : BaseMockTransportAdapter(
    kind = TransportKind.ULTRASOUND,
    initialAvailability = TransportAvailability.EXPERIMENTAL,
    initialDetail = "Experimental; microphone permission required",
    capabilities = setOf(
        TransportCapability.DISCOVERY,
        TransportCapability.SMALL_TEXT,
        TransportCapability.PAIRING,
        TransportCapability.OFFLINE
    )
)
```

Create equivalent adapters for every listed transport. Each must accurately label itself as mock or experimental.

## 13.14 Transport registry

```kotlin
package org.omnichat.app.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportRegistry @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards TransportAdapter>
) {
    fun all(): List<TransportAdapter> = adapters.sortedBy { it.kind.ordinal }

    fun states(): Flow<List<TransportState>> {
        val flows = all().map { it.state }
        return combine(flows) { states -> states.toList() }
    }

    fun byKind(kind: TransportKind): TransportAdapter? = adapters.firstOrNull { it.kind == kind }
}
```

## 13.15 Automatic router

```kotlin
package org.omnichat.app.transport

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticRouter @Inject constructor(
    private val registry: TransportRegistry
) {
    private val priority = listOf(
        TransportKind.INTERNET_DIRECT,
        TransportKind.WIFI_LAN,
        TransportKind.WIFI_AWARE,
        TransportKind.WIFI_DIRECT,
        TransportKind.BLUETOOTH_DIRECT,
        TransportKind.BLUETOOTH_MESH,
        TransportKind.ULTRASOUND,
        TransportKind.INFRARED,
        TransportKind.INTERNET_RELAY
    )

    suspend fun route(envelope: OutboundEnvelope): SendResult {
        val adapters = priority.mapNotNull(registry::byKind)
        val failures = mutableListOf<String>()

        for (adapter in adapters) {
            when (adapter.canReach(envelope.recipientId)) {
                Reachability.Reachable -> {
                    when (val result = adapter.send(envelope)) {
                        is SendResult.Accepted -> return result
                        is SendResult.Rejected -> failures += "${adapter.kind}: ${result.reason}"
                        is SendResult.Failed -> failures += "${adapter.kind}: ${result.error.message}"
                    }
                }
                Reachability.Unknown -> failures += "${adapter.kind}: reachability unknown"
                is Reachability.Unreachable -> failures += "${adapter.kind}: unreachable"
            }
        }

        return SendResult.Rejected(
            failures.joinToString(separator = "; ").ifBlank { "No transport available" }
        )
    }
}
```

## 13.16 Capability detector

```kotlin
package org.omnichat.app.transport

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCapabilityDetector @Inject constructor(
    private val context: Context
) {
    private val pm: PackageManager get() = context.packageManager

    fun hasBluetoothLe(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasWifiDirect(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun hasWifiAware(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
            context.getSystemService(WifiAwareManager::class.java) != null

    fun hasNfc(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_NFC)

    fun hasUwb(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pm.hasSystemFeature("android.hardware.uwb")

    fun hasConsumerIrEmitter(): Boolean {
        val manager = context.getSystemService(ConsumerIrManager::class.java)
        return manager?.hasIrEmitter() == true
    }

    fun hasMicrophone(): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
}
```

## 13.17 Message models

```kotlin
package org.omnichat.app.messaging

enum class MessageDirection { INCOMING, OUTGOING }
enum class MessageStatus { QUEUED, ROUTING, SENT, DELIVERED, FAILED }

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAtEpochMs: Long,
    val direction: MessageDirection,
    val status: MessageStatus,
    val selectedTransport: String? = null
)

data class ConversationSummary(
    val id: String,
    val contactName: String,
    val contactId: String,
    val lastMessage: String,
    val lastMessageEpochMs: Long,
    val unreadCount: Int = 0
)
```

## 13.18 Message repository

```kotlin
package org.omnichat.app.messaging

import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun conversations(): Flow<List<ConversationSummary>>
    fun messages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun send(conversationId: String, recipientId: String, text: String): Result<ChatMessage>
}
```

## 13.19 In-memory repository

The scaffold must include sample contacts Sara, Ali, and Usman so the Chats screen is not empty.

```kotlin
@Singleton
class InMemoryMessageRepository @Inject constructor(
    private val router: AutomaticRouter
) : MessageRepository {
    private val messageMap = MutableStateFlow<Map<String, List<ChatMessage>>>(seedMessages())

    override fun conversations(): Flow<List<ConversationSummary>> = messageMap.map { map ->
        map.map { (conversationId, messages) ->
            val last = messages.maxByOrNull { it.createdAtEpochMs }
            ConversationSummary(
                id = conversationId,
                contactName = contactName(conversationId),
                contactId = "contact:$conversationId",
                lastMessage = last?.body.orEmpty(),
                lastMessageEpochMs = last?.createdAtEpochMs ?: 0L
            )
        }.sortedByDescending { it.lastMessageEpochMs }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> =
        messageMap.map { it[conversationId].orEmpty() }

    override suspend fun send(
        conversationId: String,
        recipientId: String,
        text: String
    ): Result<ChatMessage> = runCatching {
        require(text.isNotBlank()) { "Message cannot be empty" }
        require(text.length <= 4_000) { "Message is too long" }

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = "local",
            recipientId = recipientId,
            body = text.trim(),
            createdAtEpochMs = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.QUEUED
        )
        append(message)

        val envelope = OutboundEnvelope(
            protocolVersion = 1,
            messageId = message.id,
            recipientId = recipientId,
            createdAtEpochMs = message.createdAtEpochMs,
            expiresAtEpochMs = message.createdAtEpochMs + 7 * 24 * 60 * 60 * 1000L,
            hopLimit = 3,
            encryptedPayload = text.encodeToByteArray(),
            testOnly = true
        )

        when (val result = router.route(envelope)) {
            is SendResult.Accepted -> replace(
                message.copy(
                    status = MessageStatus.SENT,
                    selectedTransport = result.transport.name
                )
            )
            is SendResult.Rejected -> replace(message.copy(status = MessageStatus.QUEUED))
            is SendResult.Failed -> replace(message.copy(status = MessageStatus.FAILED))
        }

        message
    }

    private fun append(message: ChatMessage) {
        messageMap.update { current ->
            current + (message.conversationId to (current[message.conversationId].orEmpty() + message))
        }
    }

    private fun replace(message: ChatMessage) {
        messageMap.update { current ->
            current + (
                message.conversationId to current[message.conversationId].orEmpty().map {
                    if (it.id == message.id) message else it
                }
            )
        }
    }
}
```

Add required imports and helper seed functions.

## 13.20 App share manager

```kotlin
package org.omnichat.app.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShareManager @Inject constructor(
    private val context: Context
) {
    fun shareApk(apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists()) { "APK file does not exist" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apkFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share OmniChat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareDownloadLink(url: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share OmniChat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
```

Do not attempt to copy the currently installed base APK without testing behavior across split APK installs. Initial safe behavior is to share the official download link.

---

# 14. UI IMPLEMENTATION RULES

## 14.1 Background glow

Compose can create the soft ambient effect with large blurred circles behind the content. Keep it subtle and disable expensive blur if performance is poor.

Prototype:

```kotlin
@Composable
fun OmniAmbientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniBackground)
    ) {
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-130).dp, y = 90.dp)
                .background(Color(0xFFE8EFFF), CircleShape)
                .blur(90.dp)
        )
        Box(
            Modifier
                .size(400.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 160.dp)
                .background(Color(0xFFEFE8FF), CircleShape)
                .blur(100.dp)
        )
        content()
    }
}
```

## 14.2 Message bubbles

Outgoing:

- Light purple.
- Black text.
- Bottom-right corner slightly tighter.

Incoming:

- White or muted surface.
- Thin border.
- Black text.
- Bottom-left corner slightly tighter.

No gradients inside message bubbles.

## 14.3 Transport indicators

Use text or simple vector icons, not emoji in production:

- Internet: globe.
- Wi-Fi: Wi-Fi mark.
- Bluetooth: Bluetooth mark.
- Mesh: connected nodes.
- Ultrasound: wave.
- Infrared: beam.
- UWB: ranging rings.

Route indicators are small, secondary, and optional. They must not distract from messages.

---

# 15. DEPENDENCY INJECTION

## 15.1 App module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideIdentityRepository(
        implementation: PrototypeIdentityRepository
    ): IdentityRepository = implementation

    @Provides
    @Singleton
    fun provideMessageRepository(
        implementation: InMemoryMessageRepository
    ): MessageRepository = implementation
}
```

## 15.2 Transport module

Use `@IntoSet` providers for each adapter.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TransportModule {
    @Provides
    @IntoSet
    fun internet(adapter: InternetTransportAdapter): TransportAdapter = adapter

    @Provides
    @IntoSet
    fun ultrasound(adapter: UltrasoundTransportAdapter): TransportAdapter = adapter

    // Add every remaining adapter.
}
```

---

# 16. WORKMANAGER RETRY

Create `PendingMessageRetryWorker` but do not pretend it can bypass Android background restrictions.

```kotlin
@HiltWorker
class PendingMessageRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Phase 1: repository has no persistent pending-query API yet.
        // Return success and record this limitation in ONE_HOUR_RESULT.md.
        return Result.success()
    }
}
```

Later:

- Query persistent queued messages.
- Retry with network constraints.
- Use unique work names.
- Apply exponential backoff.
- Avoid aggressive periodic polling.

---

# 17. RUST WORKSPACE

## 17.1 Workspace Cargo file

```toml
[workspace]
resolver = "2"
members = [
  "omnichat-core",
  "omnichat-protocol",
  "omnichat-routing",
  "omnichat-ffi"
]

[workspace.package]
edition = "2021"
license = "AGPL-3.0"
version = "0.1.0"

[workspace.dependencies]
serde = { version = "1", features = ["derive"] }
thiserror = "2"
uuid = { version = "1", features = ["v4", "serde"] }
zeroize = { version = "1", features = ["derive"] }
uniffi = "0.29"
```

Before committing, resolve the current stable UniFFI release and update only if the generated Kotlin bindings are tested.

## 17.2 Protocol crate

```rust
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum PayloadType {
    Text,
    Invite,
    Acknowledgement,
    Control,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Envelope {
    pub protocol_version: u16,
    pub message_id: Uuid,
    pub recipient_tag: Vec<u8>,
    pub created_at_ms: u64,
    pub expires_at_ms: u64,
    pub hop_limit: u8,
    pub payload_type: PayloadType,
    pub encrypted_payload: Vec<u8>,
    pub signature: Vec<u8>,
    pub padding: Vec<u8>,
}

impl Envelope {
    pub fn validate_structure(&self, now_ms: u64) -> Result<(), ProtocolError> {
        if self.protocol_version != 1 {
            return Err(ProtocolError::UnsupportedVersion(self.protocol_version));
        }
        if self.expires_at_ms <= now_ms {
            return Err(ProtocolError::Expired);
        }
        if self.hop_limit > 7 {
            return Err(ProtocolError::InvalidHopLimit);
        }
        if self.encrypted_payload.len() > 64 * 1024 {
            return Err(ProtocolError::PayloadTooLarge);
        }
        Ok(())
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ProtocolError {
    #[error("unsupported protocol version: {0}")]
    UnsupportedVersion(u16),
    #[error("envelope expired")]
    Expired,
    #[error("invalid hop limit")]
    InvalidHopLimit,
    #[error("payload too large")]
    PayloadTooLarge,
}
```

## 17.3 Routing crate

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum TransportKind {
    InternetDirect,
    WifiLan,
    WifiAware,
    WifiDirect,
    BluetoothDirect,
    BluetoothMesh,
    Ultrasound,
    Infrared,
    InternetRelay,
}

pub const DEFAULT_PRIORITY: &[TransportKind] = &[
    TransportKind::InternetDirect,
    TransportKind::WifiLan,
    TransportKind::WifiAware,
    TransportKind::WifiDirect,
    TransportKind::BluetoothDirect,
    TransportKind::BluetoothMesh,
    TransportKind::Ultrasound,
    TransportKind::Infrared,
    TransportKind::InternetRelay,
];

pub fn allowed_for_payload(kind: TransportKind, payload_bytes: usize) -> bool {
    match kind {
        TransportKind::Ultrasound | TransportKind::Infrared => payload_bytes <= 512,
        TransportKind::BluetoothMesh => payload_bytes <= 16 * 1024,
        _ => payload_bytes <= 64 * 1024,
    }
}
```

## 17.4 FFI crate

First-hour scaffold:

```rust
#[uniffi::export]
pub fn protocol_version() -> u16 {
    1
}

uniffi::setup_scaffolding!();
```

The Android build does not have to link the Rust library in the first hour, but the Rust workspace must compile and test independently.

---

# 18. SERVER SCAFFOLD

## 18.1 Health-only first pass

```rust
use axum::{routing::get, Json, Router};
use serde::Serialize;
use std::net::SocketAddr;

#[derive(Serialize)]
struct Health {
    service: &'static str,
    status: &'static str,
    version: &'static str,
}

async fn health() -> Json<Health> {
    Json(Health {
        service: "omnichat-bootstrap-relay",
        status: "ok",
        version: env!("CARGO_PKG_VERSION"),
    })
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let app = Router::new().route("/health", get(health));
    let address = SocketAddr::from(([0, 0, 0, 0], 8080));
    let listener = tokio::net::TcpListener::bind(address).await?;
    axum::serve(listener, app).await?;
    Ok(())
}
```

## 18.2 Later relay responsibilities

- Generate or load stable libp2p peer identity.
- Listen on QUIC and TCP.
- Act as Kademlia bootstrap node.
- Enable circuit relay server with quotas.
- Expose peer ID and multiaddresses.
- Rate-limit abuse.
- Keep no plaintext messages.
- Add no user account system.
- Add metrics without message content or stable user tracking.

---

# 19. DATABASE PLAN

The first-hour scaffold may use in-memory repositories. The next implementation replaces them with SQLCipher.

## 19.1 Tables

### identities

```text
id
public_id
fingerprint
display_name
created_at
key_alias
```

### contacts

```text
id
public_identity
fingerprint
display_name
verified_at
blocked_at
created_at
```

### conversations

```text
id
contact_id
created_at
updated_at
```

### messages

```text
id
conversation_id
sender_id
recipient_id
body_ciphertext
created_at
status
selected_transport
route_metadata_ciphertext
```

### pending_envelopes

```text
id
message_id
recipient_id
envelope_ciphertext
attempt_count
next_attempt_at
expires_at
last_error
```

### transport_preferences

```text
transport_kind
enabled
experimental_enabled
priority_override
```

No plaintext secret keys in the database.

---

# 20. SECURITY RULES

1. Never log message bodies.
2. Never log private keys.
3. Never log full contact identifiers in release mode.
4. Never call Base64 “encryption.”
5. Never write a home-grown ratchet.
6. Never store a database key in source code.
7. Never commit signing keys or server secrets.
8. Reject malformed and oversized envelopes before expensive processing.
9. Use message IDs and duplicate caches to stop loops.
10. Apply hop limits and expiry.
11. Validate every transport input.
12. Treat Bluetooth, Wi-Fi, ultrasound, NFC, infrared, and relay peers as hostile channels.
13. Show key-change warnings after production session integration.
14. Keep experimental methods disabled by default until tested.
15. Perform a security audit before high-risk-user claims.

---

# 21. PERMISSION POLICY

Request permissions only when the user enables or uses the related method.

| Permission/capability | Trigger |
|---|---|
| Bluetooth scan/connect/advertise | Enable Bluetooth or Find nearby |
| Nearby Wi-Fi devices | Enable Wi-Fi Direct/Aware |
| Microphone | Enable ultrasound |
| NFC | Tap pairing feature |
| UWB ranging | Enable UWB assist on supported device |
| Notifications | Enable background delivery notifications |

The setup screen may offer one main `Enable available connections` action, but the app should sequence system prompts with a clear explanation between them.

---

# 22. TEST PLAN

## 22.1 Unit tests

- Identity display-name validation.
- Fingerprint formatting.
- Route priority.
- Payload size restrictions.
- Hop-limit validation.
- Message status transitions.
- Capability mapping.
- No transport available returns queued/rejected result.

## 22.2 UI tests

- Welcome primary action opens create identity.
- Empty display name is rejected.
- Valid identity advances to connection setup.
- Bottom navigation contains exactly three destinations.
- Chats list renders sample conversations.
- Sending a message adds a bubble.
- Connect screen shows all transport categories.
- Settings contains Share OmniChat.

## 22.3 Manual device tests

- Android emulator without Bluetooth/Wi-Fi Aware.
- Phone with BLE.
- Phone without IR.
- Phone with IR emitter.
- Phone without UWB.
- Android permission denial.
- Airplane mode.
- Wi-Fi-only mode.
- App restart.
- Dark mode: application should remain intentionally light unless future theme support is added.

---

# 23. GITHUB ACTIONS

## 23.1 Android workflow

```yaml
name: Android

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :apps:android:assembleDebug :apps:android:testDebugUnitTest
```

## 23.2 Rust workflow

```yaml
name: Rust

on:
  push:
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          components: rustfmt, clippy
      - run: cargo fmt --all --check
        working-directory: crates
      - run: cargo clippy --workspace --all-targets -- -D warnings
        working-directory: crates
      - run: cargo test --workspace
        working-directory: crates
```

Pin third-party GitHub Actions to commit SHAs before a production release.

---

# 24. ONE-HOUR EXECUTION ORDER

The coding assistant must follow this order.

## Minute 0–5: repository foundation

- Create repository structure.
- Add license, README, docs placeholders.
- Add Gradle wrapper and version catalog.
- Create Android module.
- Create Rust workspace.

## Minute 5–15: Android shell and theme

- Build application and activity.
- Add Compose theme.
- Add navigation.
- Add ambient background and core components.
- Confirm debug build compiles.

## Minute 15–30: screens

- Implement Welcome.
- Implement Create Identity.
- Implement Enable Connections.
- Implement Identity Ready.
- Implement Chats.
- Implement Chat.
- Implement Connect.
- Implement Settings.

## Minute 30–40: data and routing scaffold

- Add identity repository.
- Add in-memory messages.
- Add transport models.
- Add mock adapters.
- Add registry and automatic router.
- Connect transport states to Connect screen.

## Minute 40–47: sharing and worker

- Add download-link sharing.
- Add FileProvider definitions.
- Add retry worker scaffold.
- Add permission models.

## Minute 47–53: Rust and server

- Add protocol crate.
- Add routing crate.
- Add FFI scaffold.
- Add health service.

## Minute 53–58: tests and CI

- Add priority test.
- Add protocol validation tests.
- Add basic Android unit tests.
- Add workflows.

## Minute 58–60: verification record

Run:

```bash
./gradlew :apps:android:assembleDebug :apps:android:testDebugUnitTest
cd crates && cargo fmt --all --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace
cd ../services/bootstrap-relay && cargo check
```

Write exact pass/fail results to:

```text
docs/ONE_HOUR_RESULT.md
```

Do not state a command passed unless it actually ran successfully.

---

# 25. REQUIRED `ONE_HOUR_RESULT.md`

Use this format:

```markdown
# OmniChat One-Hour Result

## Completed
- ...

## Build evidence
- Android assembleDebug: PASS/FAIL
- Android unit tests: PASS/FAIL
- Rust fmt: PASS/FAIL
- Rust clippy: PASS/FAIL
- Rust tests: PASS/FAIL
- Relay cargo check: PASS/FAIL

## Real implementations
- ...

## Mock/stub implementations
- ...

## Security status
- Production encryption: NOT IMPLEMENTED
- Security audit: NOT PERFORMED

## Exact next task
- ...
```

---

# 26. ACCEPTANCE CRITERIA FOR THE FOUNDATION

The foundation is accepted only if:

1. Android debug APK builds.
2. Application opens without crashing.
3. The screen uses white, black, and light purple.
4. Welcome flow creates a prototype identity.
5. Bottom navigation has exactly Chats, Connect, Settings.
6. Chats screen contains sample conversations.
7. Chat screen can add a local outgoing message.
8. Connect screen lists Internet, Wi-Fi, Bluetooth, Ultrasound, Infrared, NFC, and UWB.
9. Unsupported hardware is not falsely shown as available.
10. AutomaticRouter exists and has deterministic priority.
11. Every transport has an adapter class, even if currently mocked.
12. Mock security is explicitly labeled test-only.
13. Share OmniChat shares a safe official link or a verified APK file.
14. Rust workspace compiles and tests.
15. Bootstrap-relay health service compiles.
16. No secrets are committed.
17. Documentation clearly distinguishes completed and incomplete work.

---

# 27. NEXT IMPLEMENTATION PHASES

## Phase 2: persistent local foundation

- SQLCipher database.
- Android Keystore-wrapped database key.
- Persistent identity.
- Contact QR generation and scanning.
- Persistent messages and queue.
- Encrypted recovery file format.

## Phase 3: real local network

- Shared LAN discovery.
- Authenticated socket session.
- Real Wi-Fi Direct.
- Real Wi-Fi Aware.
- Device-to-device test matrix.

## Phase 4: Bluetooth

- BLE advertisements with rotating identifiers.
- GATT or suitable direct transfer.
- Reliable chunking and acknowledgements.
- Mesh envelope forwarding.
- Duplicate cache.
- Hop limit.
- Battery-aware operation.

## Phase 5: Internet P2P

- rust-libp2p peer identity.
- QUIC/TCP.
- Bootstrap discovery.
- Kademlia.
- NAT reachability.
- Hole punching.
- Circuit relay.
- Contact-specific rendezvous data.

## Phase 6: production encryption

- Pinned libsignal adapter.
- Session establishment.
- Ratcheted messages.
- Key-change detection.
- Safety-number/QR verification.
- Migration and compatibility tests.

## Phase 7: experimental routes

- Ultrasound modem proof of concept.
- Reed-Solomon or suitable error correction.
- Device-frequency calibration.
- Infrared hardware experiments.
- UWB-assisted contact confirmation.
- NFC pairing.

## Phase 8: release hardening

- Fuzzing.
- Threat-model review.
- Dependency review.
- Reproducible builds.
- Signed APK.
- Website and domain.
- Public bootstrap/relay deployment.
- Independent security audit.

---

# 28. DOMAIN AND DEPLOYMENT

Recommended structure:

```text
omnichat.org
    Public landing page and download

docs.omnichat.org
    Documentation

download.omnichat.org
    Stable APK redirect/download

bootstrap.omnichat.org
    libp2p bootstrap address information

relay.omnichat.org
    live encrypted circuit relay
```

GitHub contains source, issues, CI, release artifacts, and source archives. A VPS runs bootstrap and relay networking. A domain is strongly recommended but not required for local Bluetooth/Wi-Fi use.

---

# 29. CODING ASSISTANT OPERATING RULES

The coding assistant receiving this file must:

1. Read the entire file before editing.
2. Inspect the repository before creating files.
3. Preserve existing valid work.
4. Follow the exact product name, colors, routes, and three-tab navigation.
5. Avoid asking design questions already answered here.
6. Avoid adding features not listed.
7. Use safe stubs rather than fake security.
8. Compile repeatedly, not only at the end.
9. Record every deviation and reason.
10. Provide exact changed-file list.
11. Provide exact command output summary.
12. Never claim production security.
13. Never commit `.env`, signing keys, private keys, or real server credentials.
14. Stop only for a genuine blocker such as missing SDK/toolchain, and report the exact blocker and command.

---

# 30. FINAL FIXED PRODUCT STATEMENT

**OmniChat is a minimal, open-source, local-first encrypted text messenger. It gives each user one identity and keeps each conversation continuous while automatically trying Internet, shared Wi-Fi, Wi-Fi Direct, Wi-Fi Aware, Bluetooth, Bluetooth mesh, ultrasound, infrared-capable hardware, NFC pairing, UWB assistance, and future external communication adapters. Messages and contacts belong to the user’s device. The interface stays simple: Chats, Connect, and Settings.**

The product is not a social network. It is not a crypto app. It is not a centralized account service. Its purpose is simple: **send text through whatever connection is genuinely available.**

---

# 31. OFFICIAL TECHNICAL REFERENCES USED FOR THIS DIRECTIVE

The implementation should verify details against current primary documentation before integrating each real transport:

- Android Developers: Jetpack Compose, connectivity, BLE, Wi-Fi Direct, Wi-Fi Aware, NFC, UWB, WorkManager, and Hilt.
- Mozilla UniFFI user guide.
- Signal `libsignal` repository and license.
- rust-libp2p documentation for Kademlia, DCUtR, and circuit relay.
- Zetetic SQLCipher Android documentation.

Do not replace official API contracts with random blog code.
