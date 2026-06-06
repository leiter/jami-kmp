# Refine Video Call — Gap Analysis

Compares jami-android-client (reference) with jami-kmp (current) for receiver-side video call handling.

---

## 1. Incoming Call UI Differentiation (video vs audio-only)

### Android (reference)
- `NotificationServiceImpl.kt:186` — notification built with `.setIsVideo(hasVideo)`, causing Android's `CallStyle` notification to render a video-camera icon for video calls.
- `CallFragment.kt:1205-1224` — `initIncomingCallDisplay(hasVideo)` shows/hides camera preview toggle button and full-screen preview depending on whether `hasVideo` is true. Incoming video calls show the live camera preview while ringing.
- `Conference.hasVideo()` checks the media list for `MEDIA_TYPE_VIDEO` — this is the canonical source of truth.

### KMP (current)
- `strings_call.xml:42-43` — both `call_incoming_audio` and `call_incoming_video` strings exist.
- `CallViewModel.kt:219-222` — `hasVideoOffered` is computed correctly from `call.mediaList` and stored in `state.isVideo`.
- **Bug:** `CallScreen.kt:491` always shows `call_incoming_audio` regardless of `state.isVideo`:
  ```kotlin
  is CallMode.Incoming -> stringResource(Res.string.call_incoming_audio)  // always audio
  ```
- No camera preview is shown on the incoming call screen while ringing.

### Gap
| What | Android | KMP |
|------|---------|-----|
| Notification icon changes for video | ✅ | Not applicable (system notification) |
| Status text "Incoming video call" | ✅ | ❌ always shows audio string |
| Camera preview shown while ringing | ✅ | ❌ not shown |
| Toggle button visible on video incoming | ✅ | ❌ not shown |

**Fix needed in `CallScreen.kt`:**
```kotlin
is CallMode.Incoming -> stringResource(
    if (state.isVideo) Res.string.call_incoming_video else Res.string.call_incoming_audio
)
```
And show a camera preview + video-accept button when `state.isVideo` is true (matching Android's `initIncomingCallDisplay`).

---

## 2. Video Transmission on Accept

### Android (reference)
- Two accept buttons: "accept audio" sets `presenter.wantVideo = false`; "accept" (video) sets `presenter.wantVideo = true`.
- Both flow through `prepareCall(true)` → `initializeCall(isIncoming, hasVideo)` → `presenter.acceptCall(hasVideo)`.
- `CallService.accept()` builds the media list from the incoming call's offered media. If `hasVideo = false`, all `MEDIA_TYPE_VIDEO` entries are set `isMuted = true`. Video starts **at the instant of acceptance** — no separate step.

### KMP (current)
- `CallScreen.kt:201` (incoming branch): `onAccept = { viewModel.acceptCurrent(withVideo = state.isVideo) }` — correctly passes the offered-video flag.
- `CallViewModel.acceptCurrent(withVideo)` → `callService.accept(accountId, callId, hasVideo = withVideo)` — mirrors Android logic.
- `CallService.accept()` sets VIDEO media to muted if `hasVideo = false`.
- **Bug:** `MainActivity.kt:127` — the notification action that triggers accept from the lock screen hardcodes `hasVideo = false`:
  ```kotlin
  callService.accept(resolvedAccount, callId, hasVideo = false)  // always audio-only
  ```

### Gap
| What | Android | KMP |
|------|---------|-----|
| Accept with video button | ✅ | ✅ (via `state.isVideo`) |
| Accept audio-only button | ✅ | ❌ no explicit audio-only accept button |
| Video on from moment of accept | ✅ | ✅ |
| Lock screen notification accept uses video flag | ✅ | ❌ always `hasVideo = false` |

**Fix needed in `MainActivity.kt:127`:**
Resolve `hasVideo` from the incoming call state before calling `accept`, the same way the in-app screen does.

---

## 3. Video-Related Settings

### Android (reference)
- `ConfigKey.kt:61` — `VIDEO_ENABLED("Account.videoEnabled", true)` is an account-level daemon config key. It defaults to `true`.
- `CallPresenter.initOutGoing():98` — outgoing video is gated on both `VIDEO_ENABLED` and `mHardwareService.hasCamera()`.
- No in-call per-session "default camera on/off" preference beyond the account config; hardware availability and permissions are the main runtime gate.

### KMP (current)
- `SettingsModels.kt:130-147` — `CallSettings.videoEnabled: Boolean = true` exists and is synced with the daemon account details via `SettingsRepository`.
- `CallService.kt:498-504` — auto-answer uses `callSettings.videoEnabled` correctly:
  ```kotlin
  accept(accountId, callId, callSettings.videoEnabled)
  ```
- **Gap:** No user-facing settings screen to expose this toggle. The daemon-side flag is present but unreachable by the user.
- **Gap:** Outgoing call initiation does not gate video on `callSettings.videoEnabled` (the Android `initOutGoing` pattern is missing).

### Gap
| What | Android | KMP |
|------|---------|-----|
| Account-level `videoEnabled` stored in daemon | ✅ | ✅ |
| Auto-answer respects `videoEnabled` | ✅ | ✅ |
| Settings UI for `videoEnabled` | ✅ | ❌ not exposed in UI |
| Outgoing call gated on `videoEnabled` + `hasCamera()` | ✅ | ❌ `hasCamera()` guard missing |

---

## 4. Toggle Video During a Call

### Android (reference)
- **Switch camera on/off:** `CallPresenter.switchOnOffCamera()` calls `mHardwareService.changeCamera(true)` (which toggles) and then `mCallService.replaceVideoMedia(conference, "camera://$camId", hasActive)`.
- **Switch front/back:** `switchVideoInputClick()` calls `changeCamera()` (no toggle, just cycles) and `replaceVideoMedia()`.
- Core mechanism: `CallService.replaceVideoMedia()` modifies the `source` URI and `isMuted` flag on the VIDEO media entry, then calls `JamiService.requestMediaChange()` to push the change to the daemon.
- UI: `updateBottomSheetButtonStatus()` reads `hasActiveCameraVideo` and sets the videocam button's checked state and icon.

### KMP (current)
- `CallViewModel.toggleVideo():285-296` — toggles `isVideoMuted`, checks camera permission before unmuting, calls `callService.muteLocalMedia(accountId, callId, MEDIA_TYPE_VIDEO, newMuteState)`. **Fully implemented.**
- `CallScreen.kt:648-654` — button shows `Videocam` / `VideocamOff` icon based on `state.isVideoMuted`. **Fully implemented.**
- `CallViewModel.switchCamera():353-359` — implemented.
- Camera permission request flow: implemented via `_cameraPermissionRequest` and `onCameraPermissionResult`.

### Gap
| What | Android | KMP |
|------|---------|-----|
| Toggle video mute/unmute | ✅ | ✅ |
| Switch front/back camera | ✅ | ✅ |
| Camera button icon reflects state | ✅ | ✅ |
| Camera permission requested on unmute | ✅ | ✅ |
| `replaceVideoMedia` (changes source URI) | ✅ | Uses `muteLocalMedia` — functionally equivalent for mute/unmute |

Toggle video is the **most complete** of the four areas. No critical gaps.

---

## Prioritised Fix List

| Priority | Issue | File | Fix |
|----------|-------|------|-----|
| P1 | Incoming screen always shows "audio call" text | `CallScreen.kt:491` | Use `state.isVideo` to pick string |
| P1 | Notification accept ignores video flag | `MainActivity.kt:127` | Resolve `hasVideo` from call state |
| P2 | No camera preview / video-accept path on incoming screen | `CallScreen.kt` | Add preview + accept-with-video button matching `initIncomingCallDisplay` |
| P2 | `videoEnabled` not exposed in settings UI | settings screen | Add toggle in call settings section |
| P3 | Outgoing call not gated on `videoEnabled` + `hasCamera()` | `CallViewModel` | Add guard matching `CallPresenter.initOutGoing():98` |
