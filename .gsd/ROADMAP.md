# ROADMAP.md

> **Current Phase**: Phase 1
> **Milestone**: v0.1.0-dev (Foundation)

## Must-Haves (from SPEC)

- [ ] Compiling Android debug APK
- [ ] Exact white/black/light-purple UI
- [ ] Welcome → Identity → Connections → Ready → Main App flow
- [ ] 3-tab bottom nav: Chats, Connect, Settings
- [ ] Sample conversations and local messaging
- [ ] Transport capability detection and honest labeling
- [ ] AutomaticRouter with deterministic priority
- [ ] Mock adapters for all 12 transport kinds
- [ ] Prototype identity generation
- [ ] APK sharing via share sheet
- [ ] Rust workspace with protocol, routing, FFI crates + tests
- [ ] Bootstrap-relay server scaffold with health endpoint
- [ ] GitHub Actions CI for Android and Rust
- [ ] ONE_HOUR_RESULT.md documentation

## Phases

### Phase 1: Repository Foundation
**Status**: ⬜ Not Started
**Objective**: Create complete repository structure — Gradle wrapper, version catalog, Android module shell, Rust workspace, server scaffold, license, README, docs placeholders, .gitignore, .editorconfig, CI workflows.

### Phase 2: Android Shell & Theme
**Status**: ⬜ Not Started
**Objective**: Application class, MainActivity, Compose theme (colors, shapes, typography), navigation graph, ambient background, core reusable UI components (buttons, cards, top bar, bottom bar, message bubbles, transport rows). Confirm debug build compiles.

### Phase 3: All Screens
**Status**: ⬜ Not Started
**Objective**: Implement all 8 screens — Welcome, Create Identity, Enable Connections, Identity Ready, Chats, Chat, Connect, Settings — with exact content and layout from directive.

### Phase 4: Data & Routing Scaffold
**Status**: ⬜ Not Started
**Objective**: Identity models + PrototypeIdentityRepository, message models + InMemoryMessageRepository (with Sara/Ali/Usman seed data), transport models, all mock adapters, TransportRegistry, AutomaticRouter, AndroidCapabilityDetector, Hilt DI modules. Wire to screens.

### Phase 5: Sharing, Worker & Permissions
**Status**: ⬜ Not Started
**Objective**: AppShareManager, FileProvider definitions, PendingMessageRetryWorker scaffold, permission models and PermissionCoordinator, InstallQrPayload.

### Phase 6: Rust Core & Server
**Status**: ⬜ Not Started
**Objective**: Protocol crate (Envelope, validation, tests), Routing crate (TransportKind, priority, payload limits, tests), FFI scaffold, bootstrap-relay health service with Axum/Tokio.

### Phase 7: Tests, CI & Verification
**Status**: ⬜ Not Started
**Objective**: Android unit tests, Rust tests, GitHub Actions workflows, verify.sh/verify.ps1 scripts, write ONE_HOUR_RESULT.md with exact pass/fail evidence.
