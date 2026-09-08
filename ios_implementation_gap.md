# iOS Implementation Gap — jami-kmp

Audited: 2026-06-15. Re-audited and rewritten: 2026-09-08, then reconciled with the
daemon-stability and push work merged from main, then updated again the same day once the
app had actually been **built and run in the simulator** for the first time.
Compares `shared/src/iosMain/` against `shared/src/androidMain/` as the reference.

The 2026-06-15 revision of this file declared almost everything "Done". That was optimistic:
it counted an `actual` existing as an `actual` working. Many compiled fine and did nothing.
This revision distinguishes **implemented**, **stubbed**, and **blocked**, and records what
has and has not been verified.

Effort scale: **S** = 1–2 days · **M** = 3–5 days · **L** = 1+ weeks

---

## 1. Completed 2026-09-08

### Defects

| Item | Was | Now |
|------|-----|-----|
| `NSFaceIDUsageDescription` | Absent from `Info.plist`; iOS **terminates the process** on the first Face ID evaluation | Present |
| `BiometricService.ios.kt` | Account password stored **plaintext** in `NSUserDefaults` (`jami_biometric_<accountId>`), despite the KDoc claiming Keychain | Keychain generic-password item, `SecAccessControl(.biometryCurrentSet)` + `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`; authenticated `LAContext` passed via `kSecUseAuthenticationContext` so there is one prompt, not two. Legacy plaintext values migrated on first read, then deleted |

`isEnabled()` deliberately reads a plain marker rather than probing the Keychain — a
biometry-guarded item cannot be read without prompting, and asking "is this on?" must not prompt.

### Notification actions

Every action button was inert, for three independent reasons — all fixed:

1. The Kotlin `IOSNotificationDelegate` was never installed; `AppDelegate.swift` registered an
   empty Swift stub. Now installed via `IOSApplicationHelper.setupNotificationDelegate()`, held
   with a strong reference (the `UNUserNotificationCenter.delegate` property is weak). The Swift
   stub and its pbxproj entries are deleted.
2. Call notifications wrote `confId`, but the delegate reads `accountId` / `callId` and returns
   early without them — so Answer and Decline could never have worked. Now writes both.
3. `ACTION_ACCEPT` / `ACTION_REQUEST_DECLINE` were registered with no handler. Added, together
   with a real send for Reply and a real `readMessages` for Mark-read.

Action titles and notification text now resolve through `Res.string.*`, reusing the keys
`AndroidNotificationService` already uses. One new key added: `notif_send_reply`.

### Preferences

`PreferencesService` was bound to `StubPreferencesService` — an in-memory map documented "for
testing" — on iOS, desktop, macOS **and** JS. Conversation mutes, per-account auto-accept size,
notification toggles, ringtone path and theme were lost on every relaunch, and the notification
toggles were hardcoded `true`.

`AndroidPreferencesService` moved to `commonMain` as `SettingsPreferencesService`, bound from all
five platform modules. It depends only on the `Settings` wrapper, which has a real `actual` on
every target. `StubPreferencesService` is retained for tests.

### Platform stubs

| Item | Fix |
|------|-----|
| `platformGetLastModified` returned `0L` | `stat`/`st_mtimespec`, in epoch **milliseconds** to match `File.lastModified()` on the JVM targets |
| `scaleImageBytes` returned its input | UIImage redraw at `scale = 1.0`, JPEG 0.85; returns input unchanged if it already fits or decoding fails |
| `extractVideoThumbnail` returned `null` | `AVAssetImageGenerator` at t=1s (frame 0 is black in many recordings) |
| `VideoPlayerView` drew a static placeholder | `AVPlayerViewController` in a `UIKitViewController` — native transport controls, autoplay, release on dispose, matching the Android ExoPlayer behaviour |
| `changeCamera()` always returned `null` | Returned before its `scope.launch` had run. Now derives the target camera id synchronously from the device list and launches the hardware switch behind it |
| `getCameraInfo()` hardcoded capabilities | Real `AVCaptureDevice.formats` enumeration via a new `IOSCameraService.getCameraCapabilities`; old list kept as fallback |
| History reactions dropped | `JBSwarmMessage.reactions` **was** already populated by the bridge and discarded in conversion. Now marshalled |

---

## 2. The Objective-C bridge — **done**

`JamiBridgeWrapper.{h,mm}` + a `build-jamibridge.sh` rebuild. Grouped because they shared that cost.

### 2.1 Missing daemon callbacks — **done**

This was the largest gap in the audit. `DaemonCallbacks` declares **46** methods; the
`JamiBridgeDelegate` protocol declared **31**, and iOS fired only **30**. Fifteen signals were
added to the wrapper (**34 → 49 `exportable_callback` registrations**) and forwarded from
`DaemonBridge.ios.kt` (**30 → 44 callbacks**). Each of these was wired end-to-end in Kotlin and
had simply never received its events:

| Callback | Had been broken on iOS |
|----------|------------------------|
| `onDataTransferEvent` | File transfers never reported progress or completion |
| `onUserSearchEnded` | User-directory search was dead — the query fired, results never arrived |
| `onMessagesFound` | In-conversation search was dead |
| `onAccountMessageStatusChanged` | No sent/delivered/read receipts |
| `onVolatileAccountDetailsChanged` | Registration and device-online state never refreshed |
| `onAddDeviceStateChanged` | Device linking / QR pairing showed no progress |
| `onDeviceRevocationEnded` | Revoke-device UI never completed |
| `onMigrationEnded` | Account migration never completed |
| `onIncomingAccountMessage` | Non-swarm / SIP text messages dropped |
| `onConversationPreferencesUpdated` | Pref changes from other devices ignored |
| `onConversationRequestDeclined` | Declined requests not removed from the list |
| `onActiveCallsChanged` | Group-call "join ongoing call" banner never appeared |
| `onAccountProfileReceived` | Own profile/avatar updates not received |

`onConferenceInfoUpdated` was already *received* and then discarded
(`// Not directly mapped to DaemonCallbacks`); it is now forwarded, so the participant grid
updates. `VideoSignal::DecodingStarted`/`DecodingStopped` had **no handler registered at all** and
now do, reaching `HardwareService.decodingStarted/Stopped` — a prerequisite for video rendering,
which is still out of scope (§5).

No commonMain change was needed; the Kotlin methods already existed.

**Header ABI note:** refreshing the libjami headers against the pinned daemon revealed four enum
underlying-type changes and a new `botOwner` parameter on `updateProfile`. Silent ABI drift of
this kind is invisible to the Kotlin compiler — it surfaces as wrong values at runtime, so the
headers must be refreshed whenever the daemon submodule moves.

### 2.2 Small passthroughs — **done**

`setNoiseSuppression` and `setEchoCancellation` now call `setNoiseSuppressState` /
`setEchoCancellationState`. Swarm-message `editions` now cross the bridge; the daemon's
`SwarmMessage` always had the field, `JBSwarmMessage` simply had no property for it.

`setPushNotificationToken` / `Topic` / `Config` and `pushNotificationReceived` are real
passthroughs now, and the client side around them landed separately on main (2026-07-27):
`IOSPushServiceManager`, `IOSPushHelper.kt`, the APNs and PushKit delegates in `AppDelegate`,
and a synchronous placeholder call reported to CallKit on VoIP wake, which the real daemon
call then adopts.

**Still blocked on infrastructure, both platforms:** the push is sent by the DHT proxy, not
the peer, and the public proxy carries only SFL's FCM credentials. It needs a self-hosted
`dhtnode --proxyserver` with this app's own APNs/FCM credentials — see
`doc/push-notifications.md`.

**Not a gap after all:** `disableParticipantVideo` and `enableParticipantVideo` are empty on
Android too, and the daemon exposes no matching API — only the deprecated `muteParticipant`
and `muteStream`, which need a `deviceId` and `streamId` unavailable at that call site.
Platform parity, left alone on both.

**Note:** Android implements `setEchoCancellation` via `setAgcState`, which is automatic gain
control, not echo cancellation. iOS uses `setEchoCancellationState`. Android looks wrong here;
untouched.

---

## 3. Host app and platform services

### 3.1 App bundle capabilities — **done**

The Xcode project had **no entitlements file and no capabilities at all**. Now:

- **`UIBackgroundModes`** — `voip`, `audio`, `remote-notification`, `fetch`, `processing`. Without
  `audio` an active call's `AVAudioSession` is torn down on backgrounding, which would have left
  CallKit and the notification work in §1 partly inert.
- **`CFBundleLocalizations`** — 96 entries, matching the locale folders shipped in
  `composeResources`. The earlier revision of this file suspected that, with no declared
  localizations, "the app runs in English regardless of device language" and flagged the claim as
  inferred. **That was never confirmed and should not be repeated as fact:** the first simulator
  run (locale `de-DE`) rendered the UI in German. Whether the plist key was what fixed it cannot
  be attributed without a comparison build against the old bundle.
- `NSPhotoLibraryAddUsageDescription`, `BGTaskSchedulerPermittedIdentifiers`,
  `ITSAppUsesNonExemptEncryption = false`, and an `iosApp.entitlements` with `aps-environment`
  + App Group.
- `CXProviderConfiguration.localizedName` is set, so the system call UI names the app.
- `AppDelegate.swift` has scene-phase handling.

### 3.2 Audio routing and connectivity — **done**

- **Bluetooth and wired headsets** — `activateAudioSession()` passed `options = 0u`, so iOS would
  not route call audio to a headset. It now passes `AllowBluetooth`/`AllowBluetoothA2DP`, builds
  the real output list from `AVAudioSession.availableInputs`/`currentRoute` instead of a hardcoded
  `[INTERNAL, SPEAKERS]`, and observes `AVAudioSessionRouteChangeNotification` to update
  `_audioState` and emit `bluetoothEvents`. **Unverified** — route changes are not simulable (§4).
- **Connectivity monitoring** — `connectivityChanged()` was never called *anywhere in the repo*, so
  `_connectivityState` was permanently `true` and the daemon was never told the network dropped or
  returned, despite `AccountService` and `ConversationFacade` collecting that flow to drive
  `setAccountsActive()`. iOS now feeds it from `NWPathMonitor`. **Android, desktop, macOS and JS
  still do not** — the same fix is owed on each, and Android's is the one that matters.

### 3.3 Screenshot blocking — **partially done**

`WindowSecure.ios.kt` was `{}`, so the toggle in `AppSettingsScreen.kt:196` did nothing on iOS.
It now covers the window opaquely while `UIScreen.isCaptured` is true (screen recording and
mirroring) and while the app is inactive (the app-switcher snapshot). All public API.

**Screenshots are still not blocked**, and cannot be through public API. The usual workaround
reparents the app's layer inside the private `_UITextLayoutCanvasView` of a secure
`UITextField`; applied to Compose's hosting window it risks blanking the entire UI if the
internals differ, which is not a trade worth making without a device to verify on.

So iOS is **weaker than Android here**, where `FLAG_SECURE` blocks screenshots too. The shared
string `pref_block_record_title` ("Block screenshot and recording") is accurate on Android and
overstates iOS. It was left alone deliberately: it mirrors the jami-android-client key and
already has translations in 95 locales, so rewording it would break translation sync and
understate the Android behaviour. Worth revisiting with a platform-specific summary string.

---

## 4. Verification status — read this before trusting §1–§3

Two standing constraints: **no physical-device testing** (peer-to-peer fails behind NAT here), and
**no tests simulating two communicating jami-kmp instances**.

**The test suite now compiles and passes.** It did not when this file was first rewritten: ~35
errors across 11 files, from production constructors gaining parameters (`vCardService`,
`contactService`, `biometricService`, `deviceRuntimeService`, `audioRecorderService`), a new
`linkColor`, and a removed `StubHardwareService`. All repaired. Last full run: **617/617
`:shared:desktopTest`** and **602/602 `:shared:iosSimulatorArm64Test`** green.

Three test-infrastructure fixes worth not re-learning:

- Services handed a raw `TestScope` leaked coroutines and failed `runTest` with
  `UncompletedCoroutinesError`. Fixed once, in the `TestFixtures.kt` factories, via an `isolated()`
  helper rather than per-test.
- `CallViewModel`'s duration timer is a `while (isActive) { delay(…) }` loop, so `advanceUntilIdle`
  never returns. Bound the advance and call `onCleared()`.
- A test whose `buildConversationItems` appeared to "return nothing" was in fact **hanging** on
  Compose's `getString(Res.string.you_txt_prefix)`, which outgoing messages resolve. Marking the
  fixtures `isIncoming = true` avoids the resource lookup.

**The app has now been built and run in the iOS Simulator** (iPhone 17 Pro). It launches, Koin
starts, `libjami::init()` succeeds, signal handlers register, and the Welcome screen renders. See
§4.1 for what that first build cost.

Still unverified, and unverifiable under the constraints above: the **Keychain rewrite** (Keychain
calls need entitlements a test binary does not have), the **reactions marshalling** against a real
peer, **Bluetooth and wired-headset routing**, **call audio surviving backgrounding**, and
**screen-recording blocking**. These ship on code review.

To make the §2.1 callbacks verifiable without peers: `StubDaemonBridge.init(callbacks)`
(`DaemonBridge.kt:480`) still discards its callbacks. Have it retain them, and each callback gets
a test that fires it and asserts the service reacts. **Not yet done.**

### 4.1 Defects found on the first Mac build

The iOS host app had never been compiled on a Mac. Five defects blocked it — none of which either
test suite or `linkDebugFrameworkIosSimulatorArm64` could have caught, because they live in the
Swift host and the Xcode link line, not in Kotlin:

1. `@Volatile` used with no import — Kotlin/Native needs `kotlin.concurrent.Volatile`.
2. `IOSPushHelper.initPush()` was **unreachable from Swift**: Kotlin/Native exports `init*` as
   `doInit*`. Renamed to `setupPush()`, which is the workspace playbook's standing rule.
3. `-lhttp_parser` still on the link line after the daemon dropped it upstream for llhttp.
4. `-lvpx` linked for the **simulator**, which has no arm64 slice by design (ffmpeg is configured
   `--disable-libvpx` there).
5. `-lyrs` (Y-CRDT, a newer daemon dependency) not linked at all, **and** `libyrs.a` missing from
   `lib/`. `lib-sim/` had it only because `scripts/make_sim_links.py` was rewritten to *discover*
   libraries from the xcframework rather than use a fixed list.

Items 3–5 are all the same failure: **the device symlinks in `lib/` are hand-made and drift
silently when the daemon's dependency set changes.** See
`shared/src/nativeInterop/cinterop/JamiBridge/README.md`. Scripting the device side the way the
simulator side is scripted would close this off.

---

## 5. Out of scope — architectural

- **Video rendering, both directions.** `acquireNativeWindow` returns `0L`, `registerVideoCallback`
  returns `false`, `captureVideoFrame`/`captureVideoPacket` empty. `IOSCameraService` copies every
  NV12 frame into a fresh `ByteArray` and hands it to the empty TODO — **outgoing video is
  captured, copied and discarded every frame**; an early return would stop burning battery until
  the pipeline lands. `VideoSurface.ios.kt` creates an `AVSampleBufferDisplayLayer` nothing ever
  enqueues into. Needs a `libjami::SinkTarget` in ObjC++ feeding a Metal texture. Multi-week.
- **Encoder controls** — `setParameters`, `requestKeyFrame`, `setBitrate`,
  `updatePreviewVideoSurface`. Deliberately left as no-ops: they feed the dead capture path above
  and would be correct-but-inert until video works.
- **Screen sharing** — needs ReplayKit; nothing exists.
- **Picture-in-Picture** — `configurePipController` takes an `AVPlayerLayer` while the video
  surface uses `AVSampleBufferDisplayLayer`, and nothing calls it, so `enterPipMode()` returns
  `false` unconditionally. Only meaningful once video rendering exists.
- **Background sync beyond ~30s** — iOS platform limit.
- **`RingtoneLauncher.ios.kt`** — returns `null` deliberately; iOS has no system ringtone picker.
  Correct as-is.

---

## 6. Cross-platform bugs found while doing this work

Both were found while implementing the iOS work; both are now **fixed**.

1. **Android mis-mapped history reactions.** `SwigTypeConverters.toKotlinSwarmMessage` built
   `reactionId -> [emoji]`, while `ConversationFacade.kt:1331` consumes `emoji -> [authorUri]`
   — so Android rendered each history reaction with the reaction id as its body and tried to
   resolve a contact by parsing an emoji as a URI. Now builds `emoji -> [author]`, matching the
   consumer and the iOS implementation. Live `onReactionAdded/Removed` were never affected, which
   is why this survived: new reactions looked right, reloaded ones did not.
2. **`BiometricService.macos.kt` never worked, and never compiled.** Its query builder did
   `setValue(value, forKey = key.toString())` on the `CFStringRef` constants, producing keys like
   `CPointer(raw=0x…)` — every call would have returned `errSecParam`. It also failed to compile
   (22 errors: Core Foundation types never imported, `OSStatus`/`noErr` compared across `Int`/`UInt`).
   Rewritten against the working iOS implementation, keeping the richer `checkAvailability` that
   distinguishes NOT_ENROLLED from NO_HARDWARE via `LAError` codes. No migration path is needed
   because nothing was ever successfully stored.

   **Still unverified:** the macOS target does not compile — 95 remaining errors, none of them in
   this file (83 in `MacOSHardwareService.kt`, 6 in `PlatformModule.macos.kt`, 4 in
   `PermissionRequester.macos.kt`, 2 in `DaemonBridge.macos.kt`). Fixing those is separate work.

   Since the iOS and macOS implementations are now substantively identical Darwin code, the right
   follow-up is a shared `appleMain` source set holding one copy. Not done here: moving source
   sets while the target cannot compile is unverifiable.

`:shared:compileKotlinDesktop` was also broken on a clean tree — five `DaemonBridgeApi` members had
no override in `DaemonBridge.desktop.kt`. Fixed in passing, with no-op stubs matching that file's
existing pattern, because it blocked all desktop verification.

---

## 7. Open UI defect from the first simulator run

**A button label overflows in German.** On the Welcome screen the third button,
"Verbindung von einem anderen Gerät aus herstellen", is clipped — the second line's descenders are
cut off by the button bounds. Observed at the default iPhone 17 Pro size with the simulator set to
`de-DE`. It is the first screen a German user sees, and the same risk applies to every other long
locale, so the fix belongs in the button component rather than in that one call site.

Not a defect: `E/IOSPushServiceManager: Remote notification registration failed: no valid
aps-environment entitlement` in the simulator log. That is an artifact of building with
`CODE_SIGNING_ALLOWED=NO`, which strips entitlements; the entitlements file itself is present
(§3.1).
