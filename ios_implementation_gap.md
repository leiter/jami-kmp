# iOS Implementation Gap — jami-kmp

Audited: 2026-06-15. Re-audited and rewritten: 2026-09-08.
Compares `shared/src/iosMain/` against `shared/src/androidMain/` as the reference.

The 2026-06-15 revision of this file declared almost everything "Done". That was optimistic:
it counted an `actual` existing as an `actual` working. Many compiled fine and did nothing.
This revision distinguishes **implemented**, **stubbed**, and **blocked**, and records what
has and has not been verified.

Effort scale: **S** = 1–2 days · **M** = 3–5 days · **L** = 1+ weeks

---

## 1. Completed 2026-09-08

Verified by compilation and `linkDebugFrameworkIosSimulatorArm64` only — see §4.

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

## 2. Remaining — needs the Objective-C bridge

`JamiBridgeWrapper.{h,mm}` + a `build-jamibridge.sh` rebuild. Grouped because they share that cost.

### 2.1 Missing daemon callbacks (the largest remaining gap — M)

`DaemonCallbacks` declares **46** methods. The `JamiBridgeDelegate` protocol declares **31**.
Android fires 41; iOS fires 30. These are wired end-to-end in Kotlin and simply never receive
their events:

| Callback | Broken on iOS |
|----------|---------------|
| `onDataTransferEvent` | **File transfers never report progress or completion** |
| `onUserSearchEnded` | **User-directory search is dead** — the query fires, results never arrive |
| `onMessagesFound` | **In-conversation search is dead** |
| `onAccountMessageStatusChanged` | No sent/delivered/read receipts |
| `onVolatileAccountDetailsChanged` | Registration and device-online state never refresh |
| `onAddDeviceStateChanged` | Device linking / QR pairing shows no progress |
| `onDeviceRevocationEnded` | Revoke-device UI never completes |
| `onMigrationEnded` | Account migration never completes |
| `onIncomingAccountMessage` | Non-swarm / SIP text messages dropped |
| `onConversationPreferencesUpdated` | Pref changes from other devices ignored |
| `onConversationRequestDeclined` | Declined requests not removed from the list |
| `onActiveCallsChanged` | Group-call "join ongoing call" banner never appears |
| `onAccountProfileReceived` | Own profile/avatar updates not received |

Plus `onConferenceInfoUpdated`, which **is** received at `DaemonBridge.ios.kt:878` and discarded
(`// Not directly mapped to DaemonCallbacks`) — the participant grid never updates. And there are
**no `VideoSignal` handlers registered at all**, so `HardwareService.decodingStarted/Stopped` are
never invoked.

Each is ~10 lines against 30 existing templates; `onMessageReceived` and `onKnownDevicesChanged`
cover both marshalling shapes. **No commonMain change** — the Kotlin methods already exist.

### 2.2 Small passthroughs, absent from the header (S)

`setNoiseSuppression`, `setEchoCancellation`, `disableParticipantVideo`, `enableParticipantVideo`,
and swarm-message `editions` (no such property on `JBSwarmMessage`).

`setPushNotificationToken` / `setPushNotificationConfig` / `pushNotificationReceived` are declared
by the daemon (`headers/configurationmanager_interface.h:252-266`) but not exposed. The three
passthroughs are cheap; **working push additionally needs PushKit and a push proxy** — ship the
passthroughs, not the feature.

---

## 3. Remaining — no bridge needed

### 3.1 App bundle capabilities (S) — pure `Info.plist` / entitlements / pbxproj

The Xcode project has **no entitlements file and no capabilities at all**.

- **`UIBackgroundModes`** absent — add `voip`, `audio`, `remote-notification`, `fetch`,
  `processing`. Without `audio`, an active call's `AVAudioSession` is torn down on backgrounding,
  so CallKit and the notification work in §1 are **partly inert until this lands**.
- **`CFBundleLocalizations`** — 95 locale folders ship in `composeResources`; the bundle declares
  none. Suspected effect: the app runs in English regardless of device language. *This is inferred,
  not observed — confirm before acting.*
- `NSPhotoLibraryAddUsageDescription`, `BGTaskSchedulerPermittedIdentifiers`,
  `ITSAppUsesNonExemptEncryption = false`, entitlements with `aps-environment` + App Group.
- `CXProviderConfiguration.localizedName` is unset, so the system call UI shows a blank app name.
- `AppDelegate.swift` has no scene-phase handling.

### 3.2 Audio routing and connectivity (M)

- **Bluetooth and wired headsets cannot carry call audio.** `activateAudioSession()` passes
  `options = 0u`; without `AllowBluetooth`/`AllowBluetoothA2DP` iOS will not route to a headset.
  The output list is hardcoded `[INTERNAL, SPEAKERS]` and `bluetoothEvents` never emits. The bridge
  already exposes `getAudioOutputDevices`/`setAudioOutputDevice`, unused.
- **Nothing monitors connectivity on *any* platform.** `connectivityChanged()` is never called
  anywhere, so `_connectivityState` is permanently `true` and the daemon is never told the network
  dropped or returned — despite `AccountService` and `ConversationFacade` collecting that flow to
  drive `setAccountsActive()`. iOS fix is `NWPathMonitor`; Android needs the same treatment.

### 3.3 Screenshot blocking (M) — held back deliberately

`WindowSecure.ios.kt` is `{}`; the toggle in `AppSettingsScreen.kt:196` does nothing on iOS.
The secure-`UITextField` technique has to wrap Compose's hosting view and there is a real chance it
compiles, links and silently fails. Not attempted, because it cannot be verified with the testing
available.

---

## 4. Verification status — read this before trusting §1

Two standing constraints: **no physical-device testing** (peer-to-peer fails behind NAT here), and
**no tests simulating two communicating jami-kmp instances**.

Everything in §1 was verified by `compileKotlinIosSimulatorArm64` +
`linkDebugFrameworkIosSimulatorArm64` **only**. No test has executed against any of it, because:

> **The test suite does not compile on a clean tree.** ~35 pre-existing errors across 11 files:
> production constructors gained parameters (`vCardService`, `contactService`, `biometricService`,
> `deviceRuntimeService`, `audioRecorderService`), `linkColor` was added, `StubHardwareService` was
> removed. This blocks every `:shared:*Test` task. `IOSNotificationDelegateTest` is separately
> broken — it reads `IOSNotificationDelegate.KEY_*` as companion members, but they are top-level
> constants.

Highest-risk unverified items: the **Keychain rewrite** (Keychain calls cannot run in a test binary
without entitlements anyway) and the **reactions marshalling**.

To make §2.1 verifiable without peers: `StubDaemonBridge.init(callbacks)`
(`DaemonBridge.kt:480`) currently discards its callbacks. Have it retain them, and each new
callback gets a test that fires it and asserts the service reacts.

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
