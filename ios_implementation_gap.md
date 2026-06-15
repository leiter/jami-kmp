# iOS Implementation Gap — jami-kmp

Audited: 2026-06-15. Updated: 2026-06-15 after gap-fixing sprint.
Compares `shared/src/iosMain/` against `shared/src/androidMain/` as the reference.

Effort scale: **S** = 1–2 days · **M** = 3–5 days · **L** = 1+ weeks

---

## Status after gap-fixing sprint (2026-06-15)

The following previously-listed gaps have been **resolved**:

| Area | Methods fixed |
|------|--------------|
| Device management | `getKnownRingDevices`, `revokeDevice`, `addDevice`, `confirmAddDevice`, `cancelAddDevice`, `provideAccountAuthentication`, `setDeviceName` |
| Account management | `getAccountTemplate`, `changeAccountPassword`, `setAccountsOrder` |
| Credentials | `getCredentials`, `setCredentials` |
| Messaging | `sendAccountTextMessage`, `sendTextMessage`, `cancelMessage`, `searchUser` |
| Conversation preferences | `getConversationPreferences`, `setConversationPreferences` |
| Search & history | `searchConversation`, `loadSwarmUntil` |
| Audio | `playDtmf`, `muteCapture`, `isCaptureMuted`, `muteRingtone` |
| Call ops | `transfer`, `attendedTransfer`, `getCallDetails` |
| Conference | `hangUpConference`, `joinParticipant`, `addParticipant`, `addMainParticipant`, `detachParticipant`, `getParticipantList`, `getConferenceDetails`, `setConferenceLayout`, `muteParticipantAudio`, `unmuteParticipantAudio` |
| Video device | `addVideoDevice`, `removeVideoDevice`, `setDefaultDevice`, `setDeviceOrientation`, `applySettings`, `switchVideoInput` |
| Codec ops | `getCodecList`, `getActiveCodecList`, `setActiveCodecList`, `getCodecDetails` |
| Media change | `requestMediaChange`, `answerMediaChangeRequest` |
| Camera capture | `ImageCaptureEffect` — wired to `UIImagePickerController` via bridge |
| Permissions | `hasCameraPermission`, `hasMicrophonePermission` — real AVFoundation checks |
| Permissions | `hasContactsPermission` — `CNContactStore.authorizationStatusForEntityType` |
| Permissions | `hasLocationPermission` — `CLLocationManager.authorizationStatus()` |
| Permissions | `hasNotificationsPermission` — cached async probe via `UNUserNotificationCenter` |
| Log capture | `captureRecentLogs` — reads native log file written by bridge |

---

## 1. Remaining Gaps

### 1.1 Video Rendering Pipeline (Architectural — L effort each)

The Android video pipeline is built on `ANativeWindow` / shared memory, which has no direct iOS equivalent. Full iOS video call rendering requires a Metal/CALayer implementation.

| Method | iOS status | Root cause |
|--------|-----------|-----------|
| `acquireNativeWindow()` | Returns `0L` stub | Android `ANativeWindow` concept; no iOS equivalent |
| `releaseNativeWindow()` | No-op stub | Same |
| `setNativeWindowGeometry()` | No-op stub | Same |
| `registerVideoCallback()` | Returns `false` stub | Requires `libjami::SinkTarget` with C++ function-pointer callbacks receiving `VideoFrame*`; not bridgeable via simple cinterop |
| `unregisterVideoCallback()` | No-op stub | Same |
| `captureVideoFrame()` | No-op stub | Requires writing raw `ByteArray` into `libjami::VideoFrame` via `getNewFrame`/`publishFrame`; complex interop |
| `captureVideoPacket()` | No-op stub | Hardware-encoded H.264 packet delivery; requires AVFoundation capture pipeline integration |

**What this means:** Remote video in calls does not render, and local camera is not sent to the daemon. Audio-only calls work fine.

**Path to fix:** Implement a Metal/CALayer `SinkTarget` in ObjC++ that receives `VideoFrame` data and renders via a Metal texture. Register it per call using `libjami::registerSinkTarget`. Wire camera frames from `AVCaptureSession` into `getNewFrame`/`publishFrame`. This is a complete multi-week feature.

---

### 1.2 Push Notifications (Architectural — L effort)

APNs push token delivery and processing are not wired. These methods are no-ops:

| Method | Note |
|--------|------|
| `setPushNotificationToken()` | No JamiBridge binding; requires APNs token delivery to daemon |
| `setPushNotificationConfig()` | No JamiBridge binding |
| `pushNotificationReceived()` | No JamiBridge binding; payload not processed |

**What this means:** Calls and messages only arrive when the daemon is running in the foreground. Background wake-up via push is not functional.

**This is a known gap in CLAUDE.md** — it also affects Android (FCM). Both platforms require push notification integration at the libjami level.

---

### 1.3 Background Sync

iOS enforces a ~30-second background task limit. The daemon cannot be kept alive indefinitely as on Android. No `BGTaskScheduler` integration exists — periodic background sync does not happen.

This is a platform limitation / architectural decision, not a simple bridging fix.

---

## 2. Platform Services

### 2.1 `IOSDeviceRuntimeService` — Permission Checks

All permission methods now query the real platform APIs:

| Method | API used | Notes |
|--------|----------|-------|
| `hasCameraPermission()` | `AVCaptureDevice.authorizationStatus` | Synchronous |
| `hasMicrophonePermission()` | `AVCaptureDevice.authorizationStatus` | Synchronous |
| `hasContactsPermission()` | `CNContactStore.authorizationStatusForEntityType` | Synchronous |
| `hasLocationPermission()` | `CLLocationManager.authorizationStatus()` | Synchronous |
| `hasNotificationsPermission()` | `UNUserNotificationCenter.getNotificationSettings` | Async; cached at init, defaults `true` until probe completes |

---

## 3. Summary

| Category | Status |
|----------|--------|
| Messaging & reactions | **Done** |
| Conference controls | **Done** |
| Account & SIP credentials | **Done** |
| Device management / linking | **Done** |
| Codec management | **Done** |
| Call transfer | **Done** |
| Camera capture (photo send) | **Done** |
| Search & history | **Done** |
| Log capture | **Done** |
| Video call rendering | **Remaining** — architectural, multi-week |
| Push notifications | **Remaining** — architectural, affects both platforms |
| Background sync | **Remaining** — iOS platform limitation |
| Contacts/notification/location permissions | **Done** |
