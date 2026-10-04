# Mini-Plan: Calling & Conferencing Gaps

_Audited 2026-09-25. Scope: audio/video calls, conferences, moderation, media negotiation._

## Summary

The calling domain is fundamentally solid: basic 1:1 and multi-party calls, hold/resume, video, screen sharing, and call recording (Android) are all working. However, conference moderation is **significantly incomplete** — participant muting and per-stream controls are stubbed as empty no-ops, and several powerful daemon capabilities (moderator role assignment, fine-grained stream muting, raise-hand signalling, participant ejection) are not exposed to the UI at all. Additionally, a critical bug in `muteParticipantAudio` hangs up the entire conference instead of muting the participant. The largest opportunity for improvement is wiring the daemon's modern per-stream moderation APIs (`muteStream`, `setActiveStream`, `raiseHand`) to the UI so moderators can actually control their conferences.

## Findings

### F1: `muteParticipantAudio` implementation is incorrect — calls `hangUpConference` instead of muting the participant — **bug**
**Evidence:** `DaemonBridge.android.kt:1017-1018`
```kotlin
override fun muteParticipantAudio(accountId: String, confId: String, participantId: String) {
    JamiService.hangUpConference(accountId, confId)
}
```
The implementation calls `hangUpConference`, which terminates the entire conference. The correct daemon function is `muteStream` (see F2). This will hang up all participants in the conference when a moderator tries to mute one participant.

**Impact:** Any attempt to mute a conference participant terminates the entire call for everyone. This is a critical usability failure for conference moderation.

**Daemon support:** `callmanager_interface.h` line 128-133 exposes `muteStream(accountId, confId, accountUri, deviceId, streamId, bool)`. The reference `jami-client-android` calls this via `JamiService.muteStream()` at `CallService.kt:324-330`.

---

### F2: `unmuteParticipantAudio`, `disableParticipantVideo`, `enableParticipantVideo` are empty stubs — **gap**
**Evidence:** `DaemonBridge.android.kt:1020-1022`
```kotlin
override fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String) {}
override fun disableParticipantVideo(accountId: String, confId: String, participantId: String) {}
override fun enableParticipantVideo(accountId: String, confId: String, participantId: String) {}
```
These methods are declared in `DaemonBridgeApi` (lines 350-370 of `DaemonBridge.kt`) but have no implementation — they are silent no-ops. Called from `CallService.kt:349-371`, but they do nothing.

**Impact:** UI buttons for unmuting, disabling/enabling participant video in conferences are non-functional. Moderators cannot re-enable a muted participant or control their video.

**Daemon support:** 
- `muteStream(..., state=false)` unmutes audio (line 128-133, `callmanager_interface.h`)
- `setActiveStream(..., state=false)` disables video (line 138-143); `setActiveStream(..., state=true)` enables it

---

### F3: `setModerator` daemon function never called from jami-kmp — **gap**
**Evidence:** Zero calls to `setModerator` in the entire codebase. Daemon header `callmanager_interface.h:118-121` defines it:
```cpp
LIBJAMI_PUBLIC void setModerator(const std::string& accountId,
                                 const std::string& confId,
                                 const std::string& accountUri,
                                 const bool& state);
```
The reference `jami-android-client` does not appear to expose this in its UI either (not found in `CallService.kt` or `call/` screens), but the capability exists at the daemon layer.

**Impact:** There is no way to designate or remove moderators from a conference. Only the conference creator is a moderator, and this cannot be changed.

**Daemon support:** `callmanager_interface.h:118-121`

---

### F4: `muteStream` daemon function not called — **gap** (modern per-stream API not used)
**Evidence:** Zero calls to `JamiService.muteStream()` or `daemonBridge.muteStream()` anywhere. The daemon exposes this modern API:
```cpp
LIBJAMI_PUBLIC void muteStream(const std::string& accountId,
                               const std::string& confId,
                               const std::string& accountUri,
                               const std::string& deviceId,
                               const std::string& streamId,
                               const bool& state);
```
This is the modern replacement for the deprecated `muteParticipant` (which only takes a boolean mute state and is less precise). The reference `jami-android-client` uses this API (`CallService.kt:324-330`).

**Impact:** jami-kmp cannot mute individual audio streams in a conference. The daemon *supports* per-participant, per-device, per-stream granularity, but the KMP layer only has the broken `muteParticipantAudio` stub (F1) which hangs up the conference.

**Daemon support:** `callmanager_interface.h:128-133`

---

### F5: `raiseHand` daemon function not wired to UI — **gap** (hand-raising not exposed)
**Evidence:** The daemon function is defined:
```cpp
LIBJAMI_PUBLIC void raiseHand(const std::string& accountId,
                              const std::string& confId,
                              const std::string& accountUri,
                              const std::string& deviceId,
                              const bool& state);
```
Zero calls to this function anywhere in jami-kmp. The reference `jami-android-client` (`CallService.kt:340-345`) exposes it, but jami-kmp does not.

**Impact:** Participants cannot raise their hands to signal they want to speak. This is a conference signalling mechanism that exists in the daemon but is completely absent from the UI.

**Daemon support:** `callmanager_interface.h:153-157`

---

### F6: `setActiveStream` not used (modern participant-focus API) — **gap**
**Evidence:** The daemon defines:
```cpp
LIBJAMI_PUBLIC void setActiveStream(const std::string& accountId,
                                    const std::string& confId,
                                    const std::string& accountUri,
                                    const std::string& deviceId,
                                    const std::string& streamId,
                                    const bool& state);
```
Zero calls in jami-kmp. jami-kmp uses the deprecated `setActiveParticipant` API instead (which takes a `callId` string, not the structured participant URI/device/stream tuple). The reference client uses both.

**Impact:** Cannot use the modern, more precise API for controlling which participant's video is displayed (speaker focus, gallery mode per stream, etc.). The old API is less flexible.

**Daemon support:** `callmanager_interface.h:138-143`

---

### F7: `hangupParticipant` not wired — **gap** (cannot eject participants)
**Evidence:** The daemon function exists:
```cpp
LIBJAMI_PUBLIC void hangupParticipant(const std::string& accountId,
                                      const std::string& confId,
                                      const std::string& accountUri,
                                      const std::string& deviceId);
```
Zero calls in jami-kmp. The reference `jami-android-client` (`CallService.kt:333-338`) exposes this.

**Impact:** Moderators cannot remove/kick a participant from a conference. Only full conference hangup (`hangUpConference`) is available.

**Daemon support:** `callmanager_interface.h:144-147`

---

### F8: In-call text messaging not exposed — **gap**
**Evidence:** The daemon supports `sendTextMessage` in the context of calls/conferences:
```cpp
LIBJAMI_PUBLIC void sendTextMessage(const std::string& accountId,
                                    const std::string& callId,
                                    const std::map<std::string, std::string>& messages,
                                    const std::string& from,
                                    bool isMixed);
```
`CallService.sendTextMessage()` (`CallService.kt:749-753`) only calls the conversation-level `daemonBridge.sendTextMessage()`, never the call-level one. The reference `jami-android-client` has this capability (`CallService` includes `sendTextMessage` overload for in-call use).

**Impact:** No way to send text chat messages during an active call. The reference client supports this for 1:1 and multi-party calls.

**Daemon support:** `callmanager_interface.h:176-180`

---

### F9: Call/conference media inspection APIs not exposed — **gap**
**Evidence:** Several daemon functions for introspecting call state are never called:
- `getCallList(accountId)` — get all active calls for an account (never called)
- `getConferenceList(accountId)` — get all active conferences (never called)  
- `currentMediaList(accountId, callId)` — get current media stream details (never called)
- `getConferenceInfos(accountId, confId)` — detailed participant info including streams (called, but only via callback `onConferenceInfoUpdated`, not proactively)

**Impact:** UI cannot introspect detailed media capabilities or stream metadata. Limited to the high-level state already tracked in `Call` and `Conference` models.

**Daemon support:** `callmanager_interface.h:55-57, 104, 115-117, 116-117`

---

### F10: Conference creation from participant list not used — **gap**
**Evidence:** `createConfFromParticipantList` exists in the daemon:
```cpp
LIBJAMI_PUBLIC void createConfFromParticipantList(const std::string& accountId,
                                                  const std::vector<std::string>& participants);
```
This is never called. Conferences are created implicitly by adding participants to an existing call via `addParticipant()`. No "instant conference creation" UI exists.

**Impact:** No way to create a multi-party conference directly from a list of participants. Must start a 1:1 call and add participants one-by-one, or rely on the daemon's implicit creation when participants interact.

**Daemon support:** `callmanager_interface.h:86-87`

---

### F11: Audio/video input switching during calls not fully implemented — **gap**
**Evidence:** `switchInput(accountId, callId, uri)` is defined and called via `DaemonBridge.switchVideoInput()` in `CallService.replaceVideoMedia()` and `CallViewModel.switchCamera()`. However:
1. Audio input switching via `switchInput` is never done (only video).
2. The daemon's full `switchInput` signature is used only for video; no corresponding audio path exists in jami-kmp.

**Impact:** Cannot switch audio input device (microphone) during an active call — only camera switching is supported. The daemon likely supports this, but it's not exposed.

**Daemon support:** `callmanager_interface.h:173` (generic `switchInput`, originally for both audio and video)

---

### F12: Recorded file playback not integrated into UI — **gap**
**Evidence:** Daemon functions exist for playing recorded calls:
```cpp
LIBJAMI_PUBLIC bool startRecordedFilePlayback(const std::string& filepath);
LIBJAMI_PUBLIC void stopRecordedFilePlayback();
LIBJAMI_PUBLIC void recordPlaybackSeek(double value);
```
These are never called from jami-kmp. The UI has no "playback recorded call" feature.

**Impact:** Recorded calls can be found on disk but must be played back externally. No in-app playback UI.

**Daemon support:** `callmanager_interface.h:160-168`

---

### F13: DTMF tone generation limited to key-press tone, not arbitrary tone start — **gap**
**Evidence:** `playDTMF(key)` is implemented, but `startTone(start, type)` is never called:
```cpp
LIBJAMI_PUBLIC void startTone(int32_t start, int32_t type);
```
This allows starting arbitrary tones (not just DTMF keys), useful for ringtones, hold-music tones, etc. The daemon separates tone-start (with type) from DTMF (single key).

**Impact:** No access to the broader tone-generation API. Only DTMF key tones are available.

**Daemon support:** `callmanager_interface.h:171`

---

### F14: Call details not proactively displayed — **gap**
**Evidence:** `getCallDetails(accountId, callId)` is implemented (`DaemonBridge.android.kt:339-341`), but it returns a map of details that is never displayed in the UI. `CallState` in `CallViewModel.kt` does not include raw call details.

The daemon returns details like:
- `AUDIO_CODEC`, `AUDIO_SAMPLE_RATE`
- `VIDEO_CODEC`, `VIDEO_BITRATE`, `VIDEO_MIN_BITRATE`, `VIDEO_MAX_BITRATE`
- `AUDIO_ONLY`, `VIDEO_SOURCE`
- `SOCKETS` (connection details)

None of these are surfaced in `CallScreen` or any call UI component.

**Impact:** No in-call statistics or codec info visible to the user. Useful for debugging call quality or understanding what's being used.

**Daemon support:** `callmanager_interface.h:55-56`, `call_const.h:41-65`

---

### F15: Conference layout modes not fully implemented — **gap**
**Evidence:** `setConferenceLayout(accountId, confId, layout)` is called with layout IDs (0 = grid, 1 = focus, 2 = locked), but:
1. These layout constants are never defined as named constants in the code (all uses are magic numbers).
2. No UI exists to let the user select layout mode.
3. The layout is set from `CallViewModel`, but there's no composable to switch between grid/focus/locked views.

**Impact:** Conference layout mode is not selectable by the user. The daemon supports multiple layouts, but the UI is missing the toggle.

**Daemon support:** `callmanager_interface.h:88`, implicit layout modes 0, 1, 2 from `F1` bug (line 1015 of `DaemonBridge.android.kt` uses magic number 2 for locked)

---

### F16: `muteAllParticipants` incorrectly implemented — **secondary issue, lower priority than F1**
**Evidence:** `DaemonBridge.android.kt:1011-1013`
```kotlin
override fun muteAllParticipants(accountId: String, confId: String) {
    JamiService.setConferenceLayout(accountId, confId, 0)
}
```
This sets the layout to grid (0), not muting all participants. The correct daemon API is `muteStream` called for each participant (or possibly a broadcast mute if the daemon supports it, but that's not exposed). The reference `jami-android-client` also uses `setConferenceLayout` for this, suggesting the daemon may not have a "mute all" primitive — but this is semantically wrong and will confuse users expecting a mute to toggle the layout.

**Impact:** "Mute all participants" button changes the conference layout instead. Less critical than F1 because the UI may not even expose this feature, but it's semantically incorrect if it does.

**Daemon support:** No direct "mute all" function; would need to iterate participants via `muteStream`

---

## Proposed Plan

### Phase 1: Fix critical bugs (S)
- **F1 Fix**: Change `muteParticipantAudio` to call `JamiService.muteStream(accountId, confId, participantId, "", "", mute=true)` instead of `hangUpConference`.
  - File: `shared/src/androidMain/kotlin/net/jami/services/DaemonBridge.android.kt:1017-1019`
  - Verify on reference `jami-client-android` the correct parameter mapping for `muteStream`.
  - Add corresponding fixes to iOS/macOS platforms in `DaemonBridge.ios.kt`, `DaemonBridge.macos.kt`.

### Phase 2: Wire per-stream moderation (M)
- **F2 Implementation**: Wire `unmuteParticipantAudio`, `disableParticipantVideo`, `enableParticipantVideo` to call the daemon APIs:
  - `unmuteParticipantAudio`: `muteStream(..., mute=false)`
  - `disableParticipantVideo`: `setActiveStream(..., state=false)`
  - `enableParticipantVideo`: `setActiveStream(..., state=true)`
  - File: `shared/src/androidMain/kotlin/net/jami/services/DaemonBridge.android.kt:1020-1022`
  - Implement on all platforms.

- **F4 Exposure**: Expose `muteStream` via `DaemonBridgeApi` (add method) and call it from `CallService` for fine-grained participant muting (alternative to the high-level F2 APIs).

- **F7 Exposure**: Add `hangupParticipant()` to `DaemonBridgeApi`, implement on Android + iOS/macOS, and call from `CallService.removeParticipant()` instead of the current `detachParticipant` if appropriate.

### Phase 3: UI for conference moderation (M–L)
- **F2/F7 UI**: Add "Mute/Unmute" and "Remove" buttons to the participant list in `CallScreen`.
  - Requires `CallState` to include per-participant mute state and moderator status (already tracked in `Conference.ParticipantInfo`).
  - Add composable controls in `OnGoingControls` or a new `ParticipantGridView`.

- **F5 UI**: Add "Raise Hand" / "Lower Hand" toggle button to `CallScreen` for participants.
  - Wires `daemonBridge.raiseHand(accountId, confId, selfUri, selfDeviceId, state)`.
  - Requires getting the local participant's URI and device ID (available in `Account` + `HardwareService`).

- **F15 UI**: Add conference layout-mode selector (Grid / Focus / Locked) to `CallScreen`.
  - Three-state toggle or radio buttons.
  - Call `setConferenceLayout` with the selected mode.

### Phase 4: Extended moderation capabilities (L)
- **F3 Exposure**: Add `setModerator(accountId, confId, participantUri, state)` to `DaemonBridgeApi`, implement on all platforms.
  - UI: "Promote/Demote to Moderator" button (Admin only). Verify if the daemon allows changing moderator status after conference creation (likely not, but check).

- **F6 Exposure**: Prefer `setActiveStream` over `setActiveParticipant` where possible (more precise API).
  - Refactor `CallViewModel.setActiveParticipant()` to extract participant URI/device/stream and call the new API.

- **F8 Integration**: Wire in-call text messaging to the call UI.
  - Add text-input composable to `CallScreen` (collapsible/modal to avoid covering video).
  - Route to `callService.sendTextMessage()` using the call ID instead of conversation ID.

### Phase 5: Quality-of-life features (M–L, lower priority)
- **F9 Introspection**: Expose `currentMediaList` and `getConferenceInfos` in a call-state panel or debug overlay.
  - Useful for developers and power users debugging media issues.

- **F11 Audio Input**: Add audio input device selector during calls (parallel to camera switching).
  - Requires enumerating audio devices via `HardwareService` and calling `switchInput(accountId, callId, audioDeviceUri)`.

- **F12 Playback**: Build a basic recorded-call browser screen.
  - Query `getRecordingPath()` from account details, scan the directory, list recordings, and wire `startRecordedFilePlayback`.

- **F14 Stats**: Show in-call codec and bitrate info on a collapsible stats panel.
  - Query `getCallDetails()` periodically and display codec/bitrate values.

- **F13 Tones**: If needed, add `startTone()` calls for hold music or custom tones (unlikely to be user-facing).

---

## Effort Estimate

| Phase | Items | Size | Rationale |
|-------|-------|------|-----------|
| **1** | F1 fix (bugs) | **S** | Straightforward function replacement, 3 platform implementations (~1 hour). |
| **2** | F2, F4, F7 (core moderation APIs) | **M** | 6–7 DaemonBridge methods + implementations on 3 platforms (~4–6 hours). Low UI complexity. |
| **3** | F2/F7/F5/F15 UI (moderation UI) | **M–L** | Participant list with controls, layout selector, raise-hand button (~6–8 hours). Compose layout work. |
| **4** | F3, F6, F8 (moderator promotion, stream API, in-call chat) | **L** | 3 more daemon methods, mostly stubs on non-Android; in-call chat requires a new composable modal (~8–10 hours). |
| **5** | F9–F14 (stats, device switching, playback) | **M–L** | Nice-to-have; audio device enum+switch and playback UI are moderate; stats panel is low effort (~6–8 hours total, independent). |

**Total rough estimate:** 24–40 hours spread over 2–3 weeks, with Phase 1–3 as the critical path (8–14 hours for working moderator control).

---

## Implementation Status (2026-10-04, branch `docs/mini-plans`)

- **Phase 1 — done.** `muteParticipantAudio` now calls `muteStream` instead of
  `hangUpConference`.
- **Phase 2 — done.** `DaemonBridgeApi` exposes `muteStream`, `setActiveStream`,
  `hangupParticipant`, `raiseHand` and `setModerator`, implemented on Android (JNI) and
  iOS/macOS (new `JamiBridgeWrapper` passthroughs: `muteParticipantStream`,
  `setActiveParticipantStream`, `raiseParticipantHand`, `setParticipantModerator`),
  stubbed on desktop/JS. `CallService` gained device-aware wrappers, and
  `muteAllParticipants` iterates participants with `muteStream` instead of changing the
  layout (F16).
- **Phase 3 — done.** `ConferenceParticipantsSheet` + `ParticipantContextMenu` provide the
  roster, per-participant moderation, raise/lower hand and the grid/focus/speaker layout
  selector; `ConferenceLayout` replaces the magic numbers.
- **F2 correction.** Per-participant *video* muting does not exist in libjami's client API:
  `Conference::muteStream` (daemon `conference.cpp:1441`) ignores `streamId` and only mutes
  audio, and `setActiveStream` focuses a stream rather than muting it. The `MUTEVIDEO`
  conference-protocol key is host-internal. `disable/enableParticipantVideo` therefore stay
  no-ops (logged), and the menu hides video entries by default.
- **Also note.** `muteStream` resolves its target via `getCallWith(accountUri, deviceId)`,
  so the device ID from the conference info is required — hence `ParticipantUi.peerUri` /
  `.deviceId`. The old call-ID based methods remain for compatibility but pass an empty
  device.
- **Phases 4–5 — open.** F6 (prefer `setActiveStream` in `CallViewModel`, bridge method now
  available), F8 in-call text messaging, F9 introspection, F11 audio-input switching,
  F12 recorded playback, F13 tones, F14 call stats.

---

## Known Good References

- **Reference Android client** conference moderation: `jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/services/CallService.kt:324-345` (muteStream, raiseHand, hangupParticipant)
- **Daemon interface**: `/home/mandroid/Videos/JAMI/jami-client-android/daemon/src/jami/callmanager_interface.h` (lines 118–157)
- **Call constants**: `/home/mandroid/Videos/JAMI/jami-client-android/daemon/src/jami/call_const.h` (codec/bitrate details)

