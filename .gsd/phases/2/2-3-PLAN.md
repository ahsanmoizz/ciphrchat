---
phase: 2
plan: 3
wave: 2
---

# Plan 2.3: Persistent Messages & Queue

## Objective
Replace the in-memory message store with SQLCipher persistent storage and implement a reliable outbox queue.

## Context
- .gsd/SPEC.md
- d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\messaging\InMemoryMessageRepository.kt

## Tasks

<task type="auto">
  <name>Persistent Message Schema & Repository</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\data\MessageEntity.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\messaging\PersistentMessageRepository.kt, d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\di\AppModule.kt</files>
  <action>
    - Define schema for storing conversations and messages.
    - Create `PersistentMessageRepository` that uses the SQLCipher database.
    - Replace `InMemoryMessageRepository` with `PersistentMessageRepository` in DI.
    - Preserve the ability to inject seed data (or remove it if no longer needed, but better to keep sample data logic for UI testing in debug mode).
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>Messages are successfully saved and loaded from the encrypted database.</done>
</task>

<task type="auto">
  <name>Implement Pending Message Queue</name>
  <files>d:\ciphrchat\omnichat\apps\android\src\main\java\org\ciphrchat\app\worker\PendingMessageRetryWorker.kt</files>
  <action>
    - Update `PendingMessageRetryWorker` to query the `PersistentMessageRepository` for messages in `QUEUED` state and attempt to route them.
    - Handle success/failure states to update the DB.
  </action>
  <verify>Run `./gradlew :apps:android:assembleDebug`.</verify>
  <done>WorkManager correctly processes queued messages from persistent storage.</done>
</task>

## Success Criteria
- [ ] Chat history survives application restarts.
- [ ] Outgoing messages are queued persistently until routed.
- [ ] Background worker can retry pending messages.
