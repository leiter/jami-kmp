# Jami KMP - Implementation Status

**Last Updated:** 2026-10-31 (three "stored but not applied" settings now applied:
conversation sort order, compact mode, and auto-download by network type)
**Version:** In Development
**Primary Platforms:** Android, iOS

---

## ✅ Fully Implemented & Working

### UI / Navigation
- 33 screen files with type-safe navigation (32 routes)
- Loading, Onboarding, and Main navigation graphs
- Material 3 design with JamiTheme (semantic + component token layers)
- Reusable UI components (buttons, inputs, avatars, dialogs, etc.)
- Dark/light theme support with user preference
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
- CallKit integration (iOS): incoming calls use the native call UI and wake the device from background via `CXProvider`/`CXProviderDelegate`
- Telecom API / ConnectionService (Android): self-managed `PhoneAccount`, calls appear in the system call log and route through Bluetooth/car audio
- Push notifications (client side): FCM (Android) and APNs + PushKit VoIP (iOS) both wired to wake the daemon on push arrival — see Push Notifications below

### Conversation Features
- Conversation list sorted by last activity
- Message sending and receiving (text, emoji) — including the first send into a still-bootstrapping
  1:1 swarm, which used to be silently dropped (F1/F4 in `doc/stabilization-findings-2026-09-04.md`).
  A send into a conversation that is `Syncing`, or whose peer is still `invited`, now goes to the
  durable outbox (`SendQueueService` + `OutboxStore`) and is handed to the daemon on
  `ConversationReady`, on the peer joining, or after the next smartlist load — so it survives a
  restart. Started from both hosts (`JamiApplication` on Android, `IOSApplicationHelper` on iOS)
- Message drafts with auto-save
- Chat bubbles with sender names
- File sending and download
- Audio recording in chat (MediaRecorder / AVAudioRecorder)
- Location sharing with OpenStreetMap (Android + iOS with MapKit)
- Camera capture integration
- Message search and filtering
- Conversation list ordering: pinned first, then the user's **Conversation sort** choice —
  last activity, alphabetical, or unread first. The setting is DHT-synced, so a change made on
  another device re-sorts the open list without a reload (2026-10-31; was stored but unread
  before, B15)
- Group conversation management: add/remove members, leave conversation, rename group, change group avatar, admin role badges
- Link detection in chat messages
- Conversation media gallery (3-column grid of shared images/videos)

### Account Management
- Multi-account support and account switching
- Account creation: new Jami account, SIP account, import, link-device
- Profile management (avatar, display name)
- Account export/backup
- Biometric authentication (Android BiometricPrompt, iOS/macOS LocalAuthentication)
- System contacts sync (Android: ContactsContract lookup by SIP/IM address + PhoneLookup fallback, merged into Jami contacts)
- Device management
- Account migration dialog
- Certificate management (`CertificatesScreen`): list pinned certificates, pin from file, unpin,
  set trust status — rows collapsed on the SHA-1 fingerprint, every id acted on

### Contact Management
- Contact list, contact requests (accept/reject)
- Trust and block/unblock contacts
- Blocked contacts screen
- QR code generation (account sharing) and camera scanning (CameraX + ZXing on Android; AVFoundation on iOS)

### Notifications
- Android: full notification channel setup (calls, messages, file transfer, background service)
- iOS/macOS: UNUserNotificationCenter integration
- NotificationGuard: quiet hours with midnight wraparound, per-type control (calls, messages, requests)
- Per-type notification control (calls / messages / requests) and quiet hours via NotificationGuard
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
- App appearance settings (theme, compact mode and conversation sort order all applied)
- System settings: start on boot, run in background (LocalPrefs)
- Debug logs viewer (`DebugLogsScreen`, Advanced → Debug logs) with share/export; logcat capture on Android
- Call recordings browser (`RecordingsScreen`) — list and play daemon-written recordings (Android)

### Background Services
- Android: JamiSyncService foreground service with timeout-based lifecycle
- iOS: background task scheduling
- Connectivity change handling; sync restart on network recovery

### Testing
- 97 test files (70 in commonTest; the rest in desktopTest 9, iosTest 5, macosTest 4, jsTest 4,
  androidUnitTest 4, androidInstrumentedTest 1)
- `:shared:desktopTest` — **870 tests, 0 failures** (last run 2026-10-31)
- ViewModel tests, model tests, service integration tests, utility tests
- `runTest` + `advanceUntilIdle` pattern throughout
- `TestFixtures.kt` with stub `DaemonBridgeApi` for isolated ViewModel testing

---

## 🚧 Partially Implemented

### AppSettingsScreen
- All settings UI present and wired
- Screenshot blocking: **done on Android** (`WindowSecure.android.kt` adds/clears `FLAG_SECURE`
  reactively). On iOS it covers the window while `UIScreen.isCaptured` is true and in the
  app-switcher snapshot, but **screenshots themselves cannot be blocked through public API** —
  see `ios_implementation_gap.md` §3.3
- Audio/video hardware: noise suppression and echo cancellation are applied —
  `SettingsRepository` calls `DaemonBridge.setNoiseSuppression()` / `setEchoCancellation()` on
  load and on every toggle. (Android routes `setEchoCancellation` to the daemon's `setAgcState`,
  which is automatic gain control, not echo cancellation — noted in `ios_implementation_gap.md` §2.2)

### Settings stored but not applied

Flagged by reading the code on 2026-10-26. These are hypotheses from
`doc/manual-test-plan-2026-09-10.md` (B10–B16) that have **not been given a device verdict**. The
static evidence still holds for the three below; B13, B15 and B16 were implemented on 2026-10-31
and have moved to the list after them.

- **Notification sound / vibration toggles** (B10, B11) — `AndroidNotificationService` sets them on
  the `Notification.Builder`, which Android ignores from API 26 on; the channels hardcode
  `enableVibration(...)` and the default sound, and only `CHANNEL_CALLS` is ever recreated (for the
  ringtone). `NotificationGuard.getSoundUri()` also returns `null` both for "sound off" and "no
  custom sound", and `null` means *use the default*.
- **Quiet hours suppresses incoming calls** (B12) — `NotificationGuard.shouldShowCallNotification()`
  includes `!isInQuietHours(settings)`, so a call during quiet hours shows no notification.
- **Block unknown contacts** (B14) — `blockUnknownContacts` is written and read back into the
  settings screen, and nowhere else.

**Closed 2026-10-31** (code written; a device verdict is still owed, as it is for the three above):

- **Auto-download on Wi-Fi / mobile data** (B13) — `ConversationFacade` now gates every
  auto-accept site on `autoDownloadWifi` / `autoDownloadMobile`, chosen by
  `HardwareService.isMeteredNetwork()` (Android: `NET_CAPABILITY_NOT_METERED`, which also
  catches a metered Wi-Fi link and a phone hotspot; iOS: `nw_path_is_expensive`; desktop:
  always unmetered). Manual downloads are untouched — asking for a file is the user
  overriding the setting. The iOS half (`nw_path_is_expensive`) compiles since 2026-10-31
  (`:shared:compileKotlinIosSimulatorArm64` on a Mac); the cellular branch is still a device check.
- **Conversation sort order** (B15) — applied in `ConversationsViewModel.conversationComparator()`.
  Pinned conversations stay on top under every mode, as in the reference client; each mode ends
  with a timestamp tiebreak so the order is total and therefore stable across reloads.
- **Compact mode** (B16) — `ConversationsState.isCompactMode` mirrors the setting and
  `ConversationListItem` drops most of its vertical padding and its inter-line gap when it is on.

### File transfers
- Sending in swarm conversations, download, and the transfer UI — done
- Auto-accept is enforced in `ConversationFacade` against both the size limit and the
  Wi-Fi / mobile-data toggles (2026-10-31, B13 above)

### Account settings
- Profile editing, media, messages, advanced — done
- Change password and account export — **done** (both reach `DaemonBridge`; the earlier
  "deferred" note here was stale)

### Incoming call screen
- Incoming video calls now show the correct "incoming video call" string, and notification-driven
  accept resolves `hasVideo` from the call's media list
- Still missing against the Android reference: the live camera preview while a video call is
  ringing, and a separate "accept audio only" button — see `docs/refine-video-call.md`

## ❌ Not Yet Implemented

### Push Notifications — delivery infrastructure only
- Client integration (FCM/APNs token registration, `DaemonBridge.setPushNotificationConfig()`, wake-on-push) is done on both platforms
- The iOS half **now compiles and runs** — built on a Mac and launched in the simulator (2026-10-14)
- Still blocked: the push is sent by the DHT proxy, not the peer, and the public proxy only holds SFL's FCM credentials — needs a self-hosted `dhtnode --proxyserver` with this app's own Firebase/APNs server keys. See `doc/push-notifications.md`

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
- Telecom API / ConnectionService integration (system call log, Bluetooth routing)
- FCM push client integration
- Notification system complete with settings enforcement
- Background sync operational
- 97 test files (see Testing section above)

### iOS
**Status:** 🟡 Good — core features working, platform integrations incomplete.
Built on a Mac and run in the simulator; the Obj-C++ wrapper `.a` files were rebuilt 2026-10-14
(1096 methods, all 153 header-declared ones present) and the Xcode simulator link is green.

- C interop bridge to libjami — working
- Core messaging, audio calling (1:1 and conference), profile management. **Video is not
  functional**: remote frames are never rendered and outgoing camera frames are captured, copied
  and dropped (`captureVideoFrame` is an empty stub) — see `ios_implementation_gap.md` §5
- Notification system with settings enforcement
- QR camera scanning (AVFoundation)
- Location sharing (MapKit)
- Biometric auth (LocalAuthentication)
- CallKit integration for native incoming-call UI
- APNs + PushKit VoIP push client integration
- Certificate management screen (list / pin / unpin / trust), exercised on the simulator — five
  bugs found and fixed there (2026-10-14, `89d1443`)
- Missing: remote video rendering (Metal/CALayer sink target — architectural, multi-week; audio-only calls work fine)
- Missing: `connectivityChanged` passthrough — `JamiBridgeWrapper.h` still exposes no entry point,
  so the daemon is never told to re-resolve connections after a network change
- Missing: call recording (`toggleRecording` / `getIsRecording` return `false`)
- Missing, waiting on the same wrapper rebuild as `connectivityChanged` (full list in
  `doc/apple-bridge-rebuild.md`): presence status note send/receive, default moderators,
  reply-to on file sends, microphone level ring, conference from a contact list
- Kotlin side compiles for Apple as of 2026-10-31 (`989ba1b`): the seven Linux-written commits
  touching `iosMain` since the last Mac session type-check and the Xcode simulator app links;
  none has been exercised on a simulator yet
- `iosX64` was removed from the build (2026-10-14): the simulator libraries are arm64-only, so that
  target could never link. Declared Apple targets are iosArm64, iosSimulatorArm64, macosArm64, macosX64

### macOS
**Status:** 🟠 Does not compile — ~95 errors, mostly in `MacOSHardwareService.kt`.
The fix in progress is a shared `appleMain` source set; see branch
`origin/macos-build-and-apple-source-set` and `doc/apple-bridge-rebuild.md` step 8.

- Source-complete: C interop bridge, core messaging and calling, biometric auth, notifications,
  share utilities — all written, **none of it verified, because the target does not build**
- Missing: push notifications, map view
- `build-jamibridge.sh`'s macOS stage also fails independently, on UIKit code compiled against the
  macOS SDK — long-standing (dates to `93df547a`), not caused by the recent wrapper work

### Desktop JVM
**Status:** 🟠 Shell only

- Window launches, Compose renders
- All daemon bridge methods are no-ops (deprioritised)

### Web/JS
**Status:** 🔴 Experimental / stub

- Daemon bridge is entirely REST stubs
- Candidate for removal if REST bridge server is not developed
