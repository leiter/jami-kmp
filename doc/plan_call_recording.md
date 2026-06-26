# Plan: In-Call Recording

> Status: NOT STARTED. Target branch `main_merge`.

Add a record toggle to the active-call screen. The libjami daemon does all the heavy
lifting (capture, mixing, file writing); the app only needs to flip recording on/off and
reflect state. The wiring chain mirrors the existing `toggleMute` path end-to-end.

Android is fully functional in this plan. iOS/macOS get a compiling stub now; the native
ObjC++ bridge work is deferred (see "iOS / macOS follow-up").

---

## Background — what already exists

- **Daemon API (Android SWIG):** `JamiService.toggleRecording(accountId, callId): Boolean` and
  `JamiService.getIsRecording(accountId, callId): Boolean` already exist
  (`shared/src/androidMain/java/net/jami/daemon/JamiService.java:188,200`).
- **Daemon signals (Android SWIG):** `Callback.recordingStateChanged(callId, code)` and
  `Callback.recordPlaybackFilepath(id, filename)` already exist
  (`shared/src/androidMain/java/net/jami/daemon/Callback.java:122,98`).
- **Model:** `Call.kt:50` already has `private var isRecording = false` (currently unused) — it
  will be promoted to an observable property fed by the daemon signal.
- The daemon writes the recording file itself to the account's configured recording path; no
  file I/O is needed on the app side.

The cut points to add, in order: `DaemonBridgeApi` → per-platform `DaemonBridge` →
`CallService` → `CallViewModel` (`CallState`) → record button in `OnGoingControls`.

---

## Phase 1 — DaemonBridgeApi

**File:** `shared/src/commonMain/kotlin/net/jami/services/DaemonBridge.kt`

1a. Add to the `DaemonBridgeApi` interface, next to the call-ops block (near `transfer`, line ~70):

```kotlin
fun toggleRecording(accountId: String, callId: String): Boolean
fun getIsRecording(accountId: String, callId: String): Boolean
```

1b. Add to the `DaemonCallbacks` interface, next to `onAudioMuted` / `onVideoMuted` (line ~401):

```kotlin
fun onRecordingStateChanged(callId: String, recording: Boolean)
```

1c. Add `StubDaemonBridge` implementations, in the call-ops block (near line ~526):

```kotlin
override fun toggleRecording(accountId: String, callId: String): Boolean = false
override fun getIsRecording(accountId: String, callId: String): Boolean = false
```

---

## Phase 2 — Platform bridges

### 2a. Android — real (`shared/src/androidMain/kotlin/net/jami/services/DaemonBridge.android.kt`)

Add next to `transfer` (line ~321):

```kotlin
override fun toggleRecording(accountId: String, callId: String): Boolean =
    JamiService.toggleRecording(accountId, callId)

override fun getIsRecording(accountId: String, callId: String): Boolean =
    JamiService.getIsRecording(accountId, callId)
```

Wire the daemon signal in `createCallCallback` (the `object : Callback()` near line ~807),
mirroring how `callStateChanged` forwards to `callbacks`:

```kotlin
override fun recordingStateChanged(callId: String, code: Int) {
    callbacks.onRecordingStateChanged(callId, code != 0)
}
```

> `code` is the daemon's recording state: `0` = stopped, non-zero = recording.

### 2b. iOS / macOS — stub (compiles, inert)

**Files:** `DaemonBridge.ios.kt`, `DaemonBridge.macos.kt` (call-ops block, near `transfer`):

```kotlin
override fun toggleRecording(accountId: String, callId: String): Boolean = false
override fun getIsRecording(accountId: String, callId: String): Boolean = false
```

No `recordingStateChanged` delegate exists in `JamiBridgeWrapper`, so nothing to wire here yet.
See "iOS / macOS follow-up".

### 2c. Desktop / JS — stub

**Files:** `DaemonBridge.desktop.kt`, `DaemonBridge.js.kt` — same two-line stub as 2b, consistent
with their existing no-op call ops.

---

## Phase 3 — CallService

**File:** `shared/src/commonMain/kotlin/net/jami/services/CallService.kt`

3a. Add an action method next to `transfer` (line ~277), following the `hold`/`muteLocalMedia`
coroutine-launch pattern:

```kotlin
/**
 * Toggle recording of the active call. The daemon writes the file to the account's
 * recording path; the authoritative recording state arrives via onRecordingStateChanged.
 */
fun toggleRecording(accountId: String, callId: String) {
    scope.launch { daemonBridge.toggleRecording(accountId, callId) }
}
```

3b. Add the callback handler next to `onAudioMuted` (line ~486), mirroring its shape (update the
`Call` model, then let observers pick it up):

```kotlin
internal fun onRecordingStateChanged(callId: String, recording: Boolean) {
    val call = getCall(callId) ?: return
    call.isRecording = recording
    // re-emit currentCalls so CallViewModel observes the change
    refreshCurrentCalls()   // use whatever mechanism onAudioMuted uses to publish call changes
}
```

> Match the exact re-publish mechanism `onAudioMuted` uses (e.g. mutating the `Call` in the
> `currentCalls` StateFlow and re-emitting). Do not invent a new channel.

3c. Promote `Call.isRecording` (`Call.kt:50`) from `private var` to an internal/observable
property so `CallService` can set it and `CallViewModel` can read it. Keep the default `false`.

---

## Phase 4 — CallViewModel

**File:** `shared/src/commonMain/kotlin/net/jami/ui/viewmodel/CallViewModel.kt`

4a. Add to `CallState` (line ~83 block):

```kotlin
val isRecording: Boolean = false,
```

4b. Add the toggle method next to `toggleMute` (line ~329), using optimistic local update plus
the authoritative callback (same dual approach as mute):

```kotlin
fun toggleRecording() {
    val callId = currentCallId ?: return
    val accountId = currentAccountId ?: return
    callService.toggleRecording(accountId, callId)
    // optimistic flip; corrected by the recordingStateChanged callback below
    _state.value = _state.value.copy(isRecording = !_state.value.isRecording)
}
```

4c. In the existing call-updates collector (`callUpdatesJob`, where `isAudioMuted` / `isOnHold`
are copied from the `Call`/`Conference` into `_state`), also map `call.isRecording` into
`isRecording`. This makes the daemon signal authoritative and corrects the optimistic flip.

---

## Phase 5 — CallScreen

**File:** `shared/src/commonMain/kotlin/net/jami/ui/screens/CallScreen.kt`

5a. Add `onToggleRecording: () -> Unit` to the `OnGoingControls` parameter list (signature near
line ~773), and pass `onToggleRecording = { viewModel.toggleRecording() }` at both
`OnGoingControls` call sites (the two control blocks near lines ~171 and ~255).

5b. Add the record button to the first controls row in `OnGoingControls`, after the screen-share
/ DTMF buttons (near line ~825), matching the existing `CallControlButton` style:

```kotlin
CallControlButton(
    icon = Icons.Default.FiberManualRecord,
    contentDescription = stringResource(
        if (state.isRecording) Res.string.content_desc_stop_recording
        else Res.string.content_desc_start_recording
    ),
    isActive = state.isRecording,
    onClick = onToggleRecording,
)
```

> `isActive = state.isRecording` gives the button the same active tint the mute/speaker buttons
> use, signalling the live recording state. `Icons.Default.FiberManualRecord` is the standard
> filled record dot and is already available in the Material icons set used elsewhere in this file.

---

## Phase 6 — Strings

**File:** `shared/src/commonMain/composeResources/values/strings_content_description.xml`

Add two content descriptions (KMP-only; the Android reference has no dedicated in-call record
key, so follow the existing `content_desc_*` convention used by the sibling call buttons):

```xml
<string name="content_desc_start_recording">Start recording</string>
<string name="content_desc_stop_recording">Stop recording</string>
```

> The KMP project already has `remote_recording` ("%1$s recording…", `strings.xml:288`) for the
> *peer-is-recording* banner; that is a separate concern and is out of scope here.

---

## iOS / macOS follow-up (deferred — requires native rebuild)

To activate recording on iOS/macOS, the ObjC++ bridge must expose the daemon calls and the
state signal, then `libJamiBridge.a` must be rebuilt (cannot be done in this environment):

1. `shared/src/nativeInterop/cinterop/JamiBridge/JamiBridgeWrapper.h` / `.mm`: add
   `- (BOOL)toggleRecording:(NSString*)accountId callId:(NSString*)callId;` and
   `- (BOOL)getIsRecording:(NSString*)accountId callId:(NSString*)callId;` delegating to
   `libjami::toggleRecording` / `libjami::getIsRecording`; add a
   `onRecordingStateChanged:(NSString*)callId recording:(BOOL)recording` delegate callback fed by
   the `RecordingStateChanged` signal subscription.
2. Replace the Phase 2b stubs in `DaemonBridge.ios.kt` / `DaemonBridge.macos.kt` with
   `bridge.toggleRecording(...)` / `bridge.getIsRecording(...)`, and forward the new delegate
   callback to `callbacks.onRecordingStateChanged(...)`.
3. Rebuild per `shared/src/nativeInterop/cinterop/JamiBridge/README.md`.

Tracking entry for `doc/TODO.md` "Call recording": Android done; iOS/macOS pending native bridge.

---

## Reference pointers

- Daemon: `shared/src/nativeInterop/cinterop/headers/callmanager_interface.h:159` (`toggleRecording`),
  `:164` (`getIsRecording`), `:256` (`RecordingStateChanged` signal).
- Pattern to mirror: `CallService.muteLocalMedia` / `onAudioMuted`; `CallViewModel.toggleMute`;
  `OnGoingControls` mute button (`CallScreen.kt:794`).
- Reference client: `cx.ring` `CallPresenter` recording toggle + `CallService.toggleRecording`.
