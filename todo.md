# jami-kmp — Open Work Items

Derived from gap analysis screenshots (2026-06-09) cross-referenced against current codebase (2026-06-13).
Items confirmed as already implemented are listed at the bottom for reference.

---

## P1 — Core / Blocking

### Push notifications (FCM + APNs)
- Android: integrate Firebase Cloud Messaging; register device token with Jami server; handle `RemoteMessage` in a `FirebaseMessagingService` subclass; wake the daemon on push arrival.
- iOS: integrate APNs via `UNUserNotificationCenter`; handle VoIP pushes with `PKPushRegistry` for call wakeup.
- Both platforms: pass token to `DaemonBridge.setPushNotificationConfig()`.
- **Reference**: `jami-client-android` `JamiFirebaseMessagingService.kt`

### CallKit (iOS)
- Implement `CXProvider` / `CXCallController` integration so incoming Jami calls use the native iOS call UI.
- Wire `CXProviderDelegate` callbacks (answer, end, hold) to `CallService`.
- **Reference**: `jami-client-ios` `CallKitAdapter`

---

## P2 — High Priority Features

### ~~Call transfer UI~~ ✓ DONE (2026-06-13)
`transfer()` in `CallViewModel` wired to `CallService.transfer()`.
Transfer button (`PhoneForwarded`) added to `OnGoingControls` first row.
`TransferSheet` bottom sheet with URI/number input added to `CallScreenContent`.

### ~~Advanced account settings (TLS / SRTP / DHT / Proxy)~~ ✓ DONE (2026-06-13)
DHT bootstrap, proxy, TURN/STUN, UPnP, and RTP port ranges were already present.
Added TLS/SRTP Security section: SRTP toggle, TLS enable + port + 3 cert file pickers
(CA list, client cert, private key) + password + method + ciphers + server name +
verify server/client + require client cert + negotiation timeout.

### ~~Audio/video codec selection UI~~ DONE
Already implemented in `AccountMediaSettingsScreen` with enable/disable toggles and up/down reorder arrows for both audio and video codecs. Backed by `AccountSubSettingsViewModel.setCodecEnabled()` / `moveCodec()` → `pushActiveCodecList()` → daemon.

### System contacts sync UI
- Permissions (`READ_CONTACTS`, `WRITE_CONTACTS`) are now declared and requested at onboarding.
- Need: a "Sync phone contacts" toggle in `AppSettingsScreen` or `AccountSettingsScreen`.
- On enable: call `ContactService.loadContacts(accountId)` which reads the phone book via `DeviceRuntimeService.loadContactsData()`.
- Optionally write discovered Jami usernames back to the phone book (`WRITE_CONTACTS`).

### ~~Ringtone picker~~ ✓ DONE (2026-06-13)
`RingtoneLauncherEffect` expect/actual added (Android: `RingtoneManager.ACTION_RINGTONE_PICKER`; other platforms: no-op).
`ringtone: String` field added to `AppSettingsState`; `updateRingtone()` added to `AppSettingsViewModel`.
`RingtoneRow` in AppSettingsScreen Notifications section — shows current name or "Default".

---

## P3 — Medium Priority

### Telecom API / ConnectionService (Android)
- Register a `ConnectionService` so Jami calls appear in the system call log and are routable through Bluetooth/car audio.
- Wire `Connection.onAnswer()`, `onDisconnect()`, `onHold()` to `CallService`.
- Declare `MANAGE_OWN_CALLS` permission (already in reference manifest; confirm in kmp manifest).
- **Reference**: `JamiConnectionService.kt` in `jami-client-android`

### ~~Conversation categories / filtering~~ ✓ DONE (2026-06-13)
`ConversationFilter` enum (ALL / UNREAD / GROUPS) added to `ConversationsViewModel`.
`isGroup: Boolean` added to `ConversationItem`; filter applied from cached list (no daemon round-trip).
Filter chips row added to `HomeScreen` below the search bar.

### ~~Debug logs viewer~~ DONE
`DebugLogsScreen.kt` added with scrollable monospace log view, Refresh FAB, and Share button in top bar.
`LogCapture` expect/actual function captures logcat on Android; no-op on other platforms.
Accessible from AppSettingsScreen → Advanced → Debug logs.

### ~~AppSettingsScreen — hardware settings enforcement~~ DONE
Noise suppression → `DaemonBridge.setNoiseSuppression()` → `JamiService.setNoiseSuppressState("enabled"/"disabled")`.
Echo cancellation → `DaemonBridge.setEchoCancellation()` → `JamiService.setAgcState(Boolean)`.
Applied on load in `SettingsRepository.loadSettings()` and on every toggle.
Screenshot blocking → `WindowSecureEffect` expect/actual called from `JamiApp.kt`; Android adds/clears `FLAG_SECURE` reactively via `AppSettingsViewModel.state`.

### Ringtone — apply to notification channel (Android)
- `CallSettings.ringtone` is persisted and displayed in `AppSettingsScreen`, but the `jami_calls` notification channel is created once at app startup and its sound is set by the OS thereafter.
- On Android O+ (API 26), channel sound can only be configured at channel creation time; the OS ignores `setSound()` on an already-created channel.
- To apply a user-chosen ringtone: delete `jami_calls` (channel ID `"jami_calls_v2"`) and recreate it with the new `AudioAttributes`-wrapped URI before showing the next call notification.
- Guard with a stored "last applied ringtone" preference so the channel is only recreated when the setting actually changes, avoiding unnecessary notification interruptions.

---

## P4 — Polish / Low Priority

### strings_kmp.xml locale cleanup
- Legacy locale folders (`values-de/`, `values-fr/`, etc.) still contain a `strings_kmp.xml` file.
- Migrate any unique keys into the 5 canonical files; delete the `strings_kmp.xml` files.

### Chat plugins
- Plugin system not ported to KMP.
- Current state: menu item shows a "not yet supported" snackbar.
- Defer until core gaps are closed.

### OsmMapView (Desktop/macOS)
- No viable cross-platform map library; shows coordinate text fallback.
- Revisit when a JVM-compatible map tile renderer is available.

---

## Out of Scope (Deprioritised)

| Item | Reason |
|---|---|
| Desktop DaemonBridge | Architectural blocker (SWIG/KMP JNI conflict); separate JVM module required |
| Web/JS platform | REST bridge server not developed; candidate for removal |
| Android TV | Separate product; low overlap with mobile KMP goals |

---

## Already Implemented (verified 2026-06-13)

These were listed as missing/partial in the June 9 gap scan but are confirmed working in the current codebase:

| Feature | Evidence |
|---|---|
| Video call rendering | `VideoRenderer.kt` (TextureView + `HardwareService.addVideoSurface()`); `CallScreen.kt:356` |
| SIP account creation | `CreateAccountScreen.kt:196` — full SIP form with server/user/pass/port/display-name |
| Call controls (mute/speaker/camera flip) | `CallScreen.kt:761–818`; `CallViewModel.toggleMute/Speaker/Camera()` |
| DTMF dial pad | `DtmfDialpad()` composable `CallScreen.kt:954`; `CallViewModel.sendDtmf()` |
| Device export / link-new-device | `LinkDeviceSheetContent()` in `AccountSettingsScreen.kt:867+` |
| Conference calls | `ConferenceVideoLayout()` `CallScreen.kt:350`; moderator controls `CallScreen.kt:847` |
| Screen sharing | `CallViewModel.toggleScreenShare()`; `MainActivity` MediaProjection flow |
| Message reactions UI | `ReactionPill` composable; reaction picker wired in `ChatScreen` |
| Read receipts | SENDING/DELIVERED/READ checkmarks in `ChatBubble` |
| URI scheme deep links | `ring://`, `jami://`, `sip:`, `tel:` intent filters in manifest + `MainActivity` |
| Full-screen image viewer | `MediaViewerScreen` with pinch-to-zoom |
| Inline video player | `VideoPlayerScreen` using ExoPlayer/Media3 |
| Biometric lock | `BiometricLockScreen`; lifecycle lock on `ON_STOP` |
| Share-to-Jami | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filters; `SharePickerScreen` |
| Message editing UX | Edit mode banner + inline edit in `ChatScreen`; `isEdited` indicator on bubble |
| Link previews | `LinkPreviewFetcher` (OG tags); `LinkPreviewCard` in `ChatBubble` |
| File-transfer auto-accept | Enforced in `ConversationFacade.onDataTransferEvent()` with size limit |
| POST_NOTIFICATIONS onboarding | `AccountSummaryScreen` requests on first entry with rationale dialog |
| Foreground service type declarations | All 5 types + READ/WRITE_CONTACTS added to `AndroidManifest.xml` (2026-06-13) |
| Change password / export account | Flows in `AccountSettingsScreen` |
