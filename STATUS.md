# Jami KMP - Implementation Status

**Last Updated:** 2026-06-11
**Version:** In Development
**Primary Platforms:** Android, iOS

---

## ✅ Fully Implemented & Working

### UI / Navigation
- 25 screens with type-safe navigation (23 routes)
- Loading, Onboarding, and Main navigation graphs
- Material 3 design with JamiTheme (semantic + component token layers)
- Reusable UI components (buttons, inputs, avatars, dialogs, etc.)
- Dark/light theme support with user preference
- Compact mode for conversation list
- Auto-navigate to incoming call screen when app is in foreground

### Calling
- Full call UI: incoming, outgoing, ongoing, held, conference states
- Video rendering (local preview + remote stream via VideoSink)
- Audio/video mute, speaker toggle, camera switch
- DTMF dialpad
- On-hold / resume
- Conference calls: participant grid, moderator controls (mute/kick/video/lock)
- Screen sharing (Android: VirtualDisplay → Surface → daemon)
- Picture-in-Picture (Android: PictureInPictureParams with auto-enter)
- Foreground auto-navigation to call screen (SharedFlow-based, avoids StateFlow dedup bug)
- Mic / camera permission banners in-call with re-request flow
- Ringtone + audio state management (HardwareService)
- Call history logged to SQLDelight

### Conversation Features
- Conversation list sorted by last activity
- Message sending and receiving (text, emoji)
- Message drafts with auto-save
- Chat bubbles with sender names
- File sending and download
- Audio recording in chat (MediaRecorder / AVAudioRecorder)
- Location sharing with OpenStreetMap (Android + iOS with MapKit)
- Camera capture integration
- Message search and filtering
- Conversation sorting (last activity, alphabetical, unread first)
- Group conversation management: add/remove members, leave conversation
- Link detection in chat messages

### Account Management
- Multi-account support and account switching
- Account creation: new Jami account, SIP account, import, link-device
- Profile management (avatar, display name)
- Account export/backup
- Biometric authentication (Android BiometricPrompt, iOS/macOS LocalAuthentication)
- Device management
- Account migration dialog

### Contact Management
- Contact list, contact requests (accept/reject)
- Trust and block/unblock contacts
- Blocked contacts screen
- QR code generation (account sharing) and camera scanning (CameraX + ZXing on Android; AVFoundation on iOS)

### Notifications
- Android: full notification channel setup (calls, messages, file transfer, background service)
- iOS/macOS: UNUserNotificationCenter integration
- NotificationGuard: quiet hours with midnight wraparound, per-type control (calls, messages, requests)
- Sound/vibration preferences applied from user settings
- Full-screen intents for incoming calls (Android)
- Incoming call notification with Answer / Decline actions
- Ongoing call notification with Hang-up action
- Notification cancelled immediately when user answers from notification

### Settings
- SettingsRepository with reactive StateFlows (all settings DHT-synced)
- Privacy settings enforced: read receipts, typing indicators, link preview
- Notification settings enforced via NotificationGuard
- Call settings: auto-answer with configurable delay, video preference
- Per-conversation settings: mute, pin, custom notifications
- Draft persistence per conversation
- App appearance settings (theme, compact mode, sort order)
- System settings: start on boot, run in background (LocalPrefs)

### Background Services
- Android: JamiSyncService foreground service with timeout-based lifecycle
- iOS: background task scheduling
- Connectivity change handling; sync restart on network recovery

### Testing
- 55 test files across commonTest, desktopTest, androidUnitTest
- ViewModel tests, model tests, service integration tests, utility tests
- `runTest` + `advanceUntilIdle` pattern throughout
- `TestFixtures.kt` with stub `DaemonBridgeApi` for isolated ViewModel testing

---

## 🚧 Partially Implemented

### AppSettingsScreen
- All settings UI present and most wired
- Screenshot blocking: stored but window flags not applied
- Audio/video hardware: noise suppression and echo cancellation stored but not applied at platform layer

### AccountSettingsScreen
- Profile editing, media, messages, advanced — all done
- Change password dialog — deferred
- Export account full flow — deferred

### File Transfers
- File sending in swarm conversations — done
- File download/transfer UI — done
- Auto-accept size and WiFi/mobile toggles — stored, enforcement logic not wired

---

## ❌ Not Yet Implemented

### Push Notifications
- FCM (Android) and APNs (iOS) not integrated
- Calls and messages only arrive when the daemon is running in the foreground

### iOS Platform Integration
- CallKit not integrated (incoming calls don't use native call UI and don't wake device from background)

### Chat Features
- Video recording in chat — deferred (requires Camera2 / AVFoundation capture session)
- Chat plugins — Jami plugin system not ported to KMP

### Maps
- OsmMapView on Desktop/macOS — shows coordinate text; no viable JVM or AppKit map library

### Desktop & Web Platforms
- Desktop DaemonBridge — all 100+ methods are no-ops; architectural blocker (SWIG/KMP conflict)
- Desktop/Web VideoSurface — placeholders only
- Web/JS daemon bridge — REST stubs throughout; explicitly experimental

---

## Platform Status

### Android
**Status:** 🟢 Production-ready for all implemented features

- JNI bridge to libjami via SWIG — working
- Full call stack: audio, video, conference, screen share, PiP
- Notification system complete with settings enforcement
- Background sync operational
- 55 passing tests

### iOS
**Status:** 🟡 Good — core features working, platform integrations incomplete

- C interop bridge to libjami — working
- Core messaging, calling (audio + video), profile management
- Notification system with settings enforcement
- QR camera scanning (AVFoundation)
- Location sharing (MapKit)
- Biometric auth (LocalAuthentication)
- Missing: CallKit, push notifications (APNs)

### macOS
**Status:** 🟡 Fair — mirrors iOS implementation

- C interop bridge (same as iOS) — working
- Core messaging and calling
- Biometric auth, notifications, share utilities
- Missing: push notifications, map view

### Desktop JVM
**Status:** 🟠 Shell only

- Window launches, Compose renders
- All daemon bridge methods are no-ops (deprioritised)

### Web/JS
**Status:** 🔴 Experimental / stub

- Daemon bridge is entirely REST stubs
- Candidate for removal if REST bridge server is not developed
