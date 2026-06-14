# iOS Implementation Gap — jami-kmp

Audited: 2026-06-15. Compares `shared/src/iosMain/` against `shared/src/androidMain/` as the reference.

Effort scale: **S** = 1–2 days · **M** = 3–5 days · **L** = 1+ weeks

---

## 1. DaemonBridge (`DaemonBridge.ios.kt`)

### 1.1 Device Management

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getKnownRingDevices()` | Returns `emptyMap()` | Device list never populated | M |
| `confirmAddDevice()` | Missing | Link-device flow broken | M |
| `cancelAddDevice()` | Missing | Link-device flow broken | M |
| `provideAccountAuthentication()` | Missing | Device import flow blocked | M |
| `revokeDevice()` | No-op ("not exposed") | Cannot revoke linked devices | M |
| `setDeviceName()` | No-op ("not exposed") | Device name cannot be updated | S |

### 1.2 Account Management

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getAccountTemplate()` | Returns `emptyMap()` | Account type templates unavailable; affects SIP setup | S |
| `changeAccountPassword()` | Returns `false` | Password change not functional | L |

### 1.3 Credentials (SIP/VOIP)

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getCredentials()` | Returns `emptyList()` | Cannot list SIP credentials | M |
| `setCredentials()` | No-op ("not exposed") | Cannot update SIP credentials | M |

### 1.4 Messaging

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `sendAccountTextMessage()` | Stub with `Log.w` warning | Location sharing and MIME-typed messages (reactions, location updates) disabled | L |

### 1.5 Conversation Preferences

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getConversationPreferences()` | Returns `emptyMap()` | Per-conversation mute/notify settings unavailable | S |
| `setConversationPreferences()` | No-op ("not directly exposed") | Cannot persist conversation preferences | S |
| `getActiveCalls()` | Returns `emptyList()` | Active call list per conversation missing (affects group calls) | S |

### 1.6 Search & History

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `searchConversation()` | Returns `-1L` | Full-text search not working | L |
| `loadSwarmUntil()` | Returns `-1L` | Message history range loading not working | L |

### 1.7 Audio

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `playDtmf()` | Log-only stub | DTMF not sent to daemon | S |
| `muteRingtone()` | Log-only stub | Cannot suppress ringtone | S |
| `muteCapture()` | Log-only stub | Microphone mute not functional | S |
| `isCaptureMuted()` | Returns `false` always | Mute state unreadable | S |
| `restartAudioLayer()` | Log-only stub | Audio layer restart not functional | S |

### 1.8 Call Details & Transfer

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getCallDetails()` | Returns `emptyMap()` | Call metadata (state, duration, codec) unreadable | M |
| `transfer()` | Returns `false` | Blind call transfer not supported | L |
| `attendedTransfer()` | Returns `false` | Attended call transfer not supported | L |

### 1.9 Conference Operations

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `hangUpConference()` | Returns `false` | Cannot end a conference | M |
| `joinParticipant()` | Returns `false` | Cannot merge two calls | L |
| `addParticipant()` | Returns `false` | Cannot add participant to conference | L |
| `addMainParticipant()` | Returns `false` | Cannot promote participant | L |
| `detachParticipant()` | Returns `false` | Cannot remove participant | L |
| `getParticipantList()` | Returns `emptyList()` | Participant IDs unavailable | S |
| `getConferenceDetails()` | Returns `emptyMap()` | Conference metadata unavailable | S |
| `setConferenceLayout()` | TODO comment | Grid/speaker/mosaic layout control missing | M |

### 1.10 Conference Participant Controls

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `muteParticipantAudio()` | **Calls `hangUpConference()` — wrong method** | Muting a participant hangs up the conference | M |
| `unmuteParticipantAudio()` | Empty stub | Cannot unmute specific participant | S |
| `muteAllParticipants()` | Delegates to `setConferenceLayout()` | Likely incorrect behavior | M |
| `setConferenceLocked()` | Delegates to `setConferenceLayout()` | Likely incorrect behavior | M |
| `disableParticipantVideo()` | Empty stub | Cannot disable participant's video | S |
| `enableParticipantVideo()` | Empty stub | Cannot enable participant's video | S |

### 1.11 Codec Operations

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `getCodecList()` | Returns `emptyList()` | Cannot enumerate codecs | M |
| `getActiveCodecList()` | Returns `emptyList()` | Active codec set unknown | M |
| `setActiveCodecList()` | No-op ("not exposed") | Cannot configure codec preferences | M |
| `getCodecDetails()` | Returns `emptyMap()` | Codec details unavailable | S |

### 1.12 Video Device Management

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `addVideoDevice()` | TODO | No video device registration with daemon | M |
| `removeVideoDevice()` | TODO | Cannot unregister video devices | S |
| `setDefaultDevice()` | TODO | Cannot set primary camera | S |
| `setDeviceOrientation()` | TODO | Device rotation not forwarded to daemon | S |
| `applySettings()` | TODO | Camera settings (focus, exposure) not applied | M |
| `captureVideoFrame()` | TODO | Software-encoded frames not forwarded | L |
| `captureVideoPacket()` | TODO | Hardware-encoded packets not forwarded | L |

### 1.13 Native Window / Video Rendering

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `acquireNativeWindow()` | TODO | No native video surface acquisition | M |
| `releaseNativeWindow()` | TODO | Cannot release rendering surfaces | S |
| `setNativeWindowGeometry()` | TODO | Cannot resize video window | S |
| `registerVideoCallback()` | TODO | Video sink callbacks not registered | M |
| `unregisterVideoCallback()` | TODO | Cannot unregister video sinks | S |

### 1.14 Media Change / Screen Sharing

| Method | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `switchVideoInput()` | TODO | Cannot switch cameras mid-call | S |
| `requestMediaChange()` | TODO | Screen sharing media negotiation not working | M |
| `answerMediaChangeRequest()` | Missing entirely | Cannot respond to peer media-change requests | M |

---

## 2. Platform Services

### 2.1 `IOSDeviceRuntimeService` — Permission Checks

All permission query methods return `true` unconditionally instead of querying the platform.

| Method | Should query | Effort |
|--------|-------------|--------|
| `hasCameraPermission()` | `AVCaptureDevice.authorizationStatus(for: .video)` | S |
| `hasMicrophonePermission()` | `AVAudioSession.recordPermission` | S |
| `hasContactsPermission()` | `CNContactStore.authorizationStatus(for: .contacts)` | S |
| `hasNotificationsPermission()` | `UNUserNotificationCenter.getNotificationSettings()` | S |
| `hasLocationPermission()` | `CLLocationManager.authorizationStatus` | S |

### 2.2 `IOSNotificationService`

| Issue | Gap | Effort |
|-------|-----|--------|
| Notification action handlers | Categories with actions are defined but not bridged to business logic handlers | M |
| `processPush()` | Stub — APNs tokens and payload not processed | L |
| Test push notification | Sends local notification only; does not test APNs delivery | S |

### 2.3 Background Sync (`SyncManager.ios.kt`)

| Issue | Gap | Effort |
|-------|-----|--------|
| iOS enforces ~30 second background task limit | Daemon cannot be kept alive as on Android | Architectural (N/A) |
| Periodic background sync | No `BGTaskScheduler` integration; Android uses WorkManager | M |

---

## 3. Platform Effects (Compose)

| Effect | iOS status | Gap | Effort |
|--------|-----------|-----|--------|
| `ImageCaptureEffect` | TODO — always calls `onImageCaptured(null)` | Camera capture always fails; user cannot take photos from chat | M |
| `LogCaptureEffect` | Returns empty string | Cannot capture logs for bug reports | S |
| `FilePickerEffect` | Delegates to `JamiBridgeWrapper`; works if bridge is complete | Verify JamiBridge `presentDocumentPickerWithMimeTypes` | S |
| `OsmMapView` | Delegates to `JamiBridgeWrapper` | Depends on native map implementation | M |
| `RingtoneLauncher` | Delegates to `JamiBridgeWrapper` | Ringtone picker may not work if bridge incomplete | M |
| `WindowSecure` | Delegates to `JamiBridgeWrapper` | Screen content protection depends on bridge | S |

---

## 4. Picture-in-Picture

| Issue | Gap | Effort |
|-------|-----|--------|
| `setAutoEnterEnabled()` | Empty stub — auto-PiP may not trigger consistently | S |
| Custom PiP actions | iOS doesn't support custom actions like Android; limited to standard controls | N/A (platform limitation) |

---

## Summary

### Effort totals

| Effort | Count |
|--------|-------|
| S (1–2 days) | ~25 items |
| M (3–5 days) | ~20 items |
| L (1+ weeks) | ~9 items |
| N/A (platform limitation) | ~3 items |

### Priority grouping

**Critical — blocks core call/messaging functionality:**
- `muteParticipantAudio()` calls wrong method (immediate bug)
- `sendAccountTextMessage()` stub (disables location sharing and reactions)
- `muteCapture()` / `isCaptureMuted()` (in-call mute broken)
- Video device registration + native window management (video calls broken)
- `ImageCaptureEffect` always returns null (cannot send photos)

**High — major features incomplete:**
- Device management: `getKnownRingDevices`, `confirmAddDevice`, `revokeDevice`
- Search: `searchConversation`, `loadSwarmUntil`
- Call transfer: `transfer`, `attendedTransfer`
- Conference: `joinParticipant`, `addParticipant`, `detachParticipant`, layout control
- Codec operations
- APNs push notification handling

**Medium — functionality gaps:**
- Permission checks in `IOSDeviceRuntimeService` (always return true)
- Credentials management (SIP accounts)
- Conversation preferences
- Conference participant controls (mute/unmute/video)
- Periodic background sync via `BGTaskScheduler`
- Notification action handlers

**Low / cosmetic:**
- `getCallDetails()` (metadata only, calls still work)
- `getConferenceDetails()` / `getParticipantList()` (display only)
- Log capture
- `setDeviceName()`

### Root cause

Most gaps are **JamiBridge bridging issues** — the functionality exists in `libjami` but is not yet exposed in the `JamiBridgeWrapper.mm` Objective-C++ layer. Once the bridge method is added, the KMP side typically just needs a one-line call.
