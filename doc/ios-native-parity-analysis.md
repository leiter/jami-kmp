# jami-kmp iOS vs. jami-client-ios — Feature-Parity & Implementation Gap Analysis

**Date:** 2026-09-10
**Compared:**
- `jami-kmp` — `shared/src/iosMain/` + `ios-app/` (Kotlin/Native + Compose Multiplatform, C-interop bridge to libjami)
- `jami-client-ios` (`Ring/`) — the shipping native Swift/RxSwift client (reference)

**Method:** static source reading only. No device, no simulator, no build. Apple targets do
not compile on this Linux host, so nothing below is runtime-verified. Items marked *inferred*
are reasoned from code structure, not observed.

> **Update 2026-09-10 — items 1, 3, 5 partially addressed on branch
> `feat/ios-app-group-and-notification-extension`:**
> - **App Group** `group.net.jami.kmp` declared in `iosApp.entitlements`; the daemon working
>   directory now resolves to the shared container (`net.jami.IOSConstants`,
>   `DaemonBridge.ios.kt`) with legacy-path migration and a fallback. Needs the group
>   registered on the App ID.
> - **Bundle defects fixed**: duplicate `UIBackgroundModes` collapsed to one key; `fetch` /
>   `processing` dropped (no `BGTaskScheduler` handler existed); `aps-environment` moved to the
>   `$(APS_ENVIRONMENT)` build variable (`development` Debug / `production` Release).
> - **Notification Service Extension scaffolded** under `ios-app/jamiNotificationExtension/`
>   (Swift lifecycle + app-active Darwin handshake, answered in `AppDelegate`; shared logic in
>   `NotificationExtensionHandler.kt`; localized fallback notification). The Xcode target,
>   libjami linkage, and the `TODO(nse-daemon)` headless-decrypt path still require a Mac —
>   see the extension `README.md`. Sections 2.1 / 3 below describe the pre-change state.
> - **Video pipeline (item 4)**: the dead per-frame NV12 `ByteArray` copy in `IOSCameraService`
>   (which fed the `captureVideoFrame` no-op ~30×/s during every call) has been removed. The
>   pipeline itself is still not functional — a full implementation plan for outgoing capture,
>   incoming render, and PiP is in `doc/ios-video-pipeline.md`. It needs a macOS rebuild of
>   `libJamiBridge_ios.a` and vendored FFmpeg headers, so §2.4 below still stands.

---

## 0. Relationship to the existing gap doc

`ios_implementation_gap.md` compares `iosMain` against `androidMain`. It is now **substantially
stale** — it predates the 2026-09-08 commit wave (`ed6673a` "Forward the 15 daemon signals iOS
was never receiving", `94a1b76` "Declare the iOS bundle capabilities", `707f849` "Fix iOS audio
routing and add network monitoring", `de04740` "screen-capture protection"). Its §2.1, §3.1 and
§3.2 are largely closed in code. This document supersedes it for the parts that overlap and adds
the dimension it never covered: **parity with the native iOS client**, not with Android.

---

## 1. Executive summary

`jami-kmp` iOS has reached broad *screen-level* parity: onboarding, account creation/link/import,
smartlist, swarm chat, 1:1 and group calls (audio), conference grid, contacts and requests,
QR scan/generate, settings, location sharing, biometric lock, CallKit, and APNs/PushKit token
plumbing all exist. The CallKit implementation is in some respects more carefully written than
its history (push-placeholder adoption, deferred answer/decline) suggests.

The gaps that remain are **architectural**, not cosmetic, and they are the parts of an iOS Jami
client that are hardest to get right:

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| 1 | **No Notification Service Extension** — messages/files/calls do not notify when the app is not already running | Critical | L |
| 2 | **No Share Extension** — cannot share into Jami from other apps | Medium | M |
| 3 | **No App Group entitlement** — blocks (1) and (2), and any shared daemon/config state | Critical (enabler) | S |
| 4 | **No video pipeline** — remote and local video render paths are `TODO`; every captured frame is copied and discarded. Video calls are audio-only in practice | Critical | L (multi-week) |
| 5 | **Bundle config defects** — duplicate `UIBackgroundModes` key; `aps-environment` = `development` only; `processing`/`fetch` modes declared with no `BGTaskScheduler` registration or `BGTaskSchedulerPermittedIdentifiers` | High | S |
| 6 | Encoder controls, screen-capture blocking of still screenshots, in-conversation search wiring | Low–Medium | S–M |

Items 1–3 together are the single most important deliverable: on iOS, a P2P messenger that
only receives while foregrounded or within the ~30 s background window is not usable as a
daily driver. The native client solved this years ago with the notification extension.

---

## 2. Architectural gaps

### 2.1 Notification Service Extension — **missing entirely** (Critical)

**Native (`jamiNotificationExtension/`, ~1,150 lines in `NotificationService.swift` + a private
libjami adapter):** on APNs push arrival while the main app is suspended or killed, the extension
spins up its own libjami instance, opens an HTTP stream to the DHT proxy, decrypts the pushed
value IDs line by line, drives daemon events until the message body / file / clone completes,
resolves display names against the name server, and presents a rich local notification — then
tears the backend down. Reference-counted (`itemsToPresent` / `syncCompleted`), 25 s hard
timeout, hands off to the foreground app for calls.

**jami-kmp:** there is no extension target. `AppDelegate.didReceiveRemoteNotification` →
`IOSPushHelperKt.onPushReceived` → `IOSPushServiceManager.onPushReceived` →
`accountService.pushNotificationReceived("", payload)`. This runs **in the main app process**
and only does anything if:
- the app is already running in the background, and
- the OS grants the `beginBackgroundTaskWithName("JamiSync")` window (~30 s), and
- the daemon reconnects to the DHT and fires `onMessageReceived` before that window closes,
  at which point `IOSNotificationService` posts the banner.

If the app was terminated (swiped away, rebooted, evicted for memory) a message push produces
**no notification at all**. There is no fallback path.

**Consequence:** message and file delivery on iOS is unreliable by design. This also makes the
"still needs a push-capable DHT proxy" caveat in `STATUS.md` understate the problem — even with
that proxy, without the extension the message push has nowhere useful to land.

**What it needs:**
- an App Group (see 2.3) so the extension and app share the daemon config / accounts dir;
- a new `jamiNotificationExtension` target (Swift is fine — it can call a small
  Kotlin/Native static lib, or be mostly self-contained ObjC++ against libjami like the native
  one);
- the streaming/decrypt/present state machine. The native `NotificationService.swift` is a
  usable spec; most of its complexity is genuinely required.

### 2.2 Share Extension — **missing** (Medium)

**Native (`jamiShareExtension/`):** full `ShareViewController` + SwiftUI `ShareView` with its
own account and conversation view-models and libjami adapter; pick an account, pick a
conversation, send text/images/files from any other app's share sheet.

**jami-kmp:** no share extension target. `ShareUtils.ios.kt` is *outbound* only (share Jami
content out via `UIActivityViewController`). There is no inbound share path.

### 2.3 App Group entitlement — **missing** (enabler for 2.1 / 2.2)

`ios-app/iosApp/iosApp.entitlements` contains only `aps-environment`. There is no
`com.apple.security.application-groups`. Both native extensions rely on an app group for the
shared libjami working directory and for the Darwin-notification handshake that lets the
extension yield to a running app. Nothing in jami-kmp reads or writes a group container.

### 2.4 Video pipeline — **not implemented** (Critical for video calls)

| Concern | Native | jami-kmp iOS |
|---------|--------|--------------|
| Incoming frame sink | `VideoAdapterDelegate` / `DecodingAdapterDelegate` → renderer views, `CVImageBuffer` | `onDecodingStarted/Stopped` now forwarded, but `acquireNativeWindow` returns `0L`, `registerVideoCallback` returns `false` — nothing renders |
| Remote render surface | frame-extractor → UIView | `VideoSurface.ios.kt` creates an `AVSampleBufferDisplayLayer` that nothing ever enqueues into |
| Outgoing capture | `VideoService` / `VideoInputsManager` AVCapture → daemon | `IOSCameraService` captures NV12, copies each frame into a fresh `ByteArray`, hands it to `captureVideoFrame` which is `// TODO: Implement via JamiBridge cinterop` — **captured, copied, discarded every frame** (also wastes battery; an early return would at least stop that) |
| Encoder controls | wired | `requestKeyFrame` / `setBitrate` are `{}` — **correct on iOS**: `VideoSignal::RequestKeyFrame` / `SetBitrate` are `#ifdef __ANDROID__` in `videomanager_interface.h`; the daemon's VideoToolbox encoder self-manages. Parity, not a gap. `setParameters` is served from AVFoundation. |
| Picture-in-Picture | `PictureInPictureManager.swift`, real | `configurePipController` expects an `AVPlayerLayer` while the surface is `AVSampleBufferDisplayLayer`; nothing calls it; `enterPipMode()` returns `false` unconditionally |

Audio calls work. Video calls connect but show no remote image and send no image. This is the
multi-week `SinkTarget` (ObjC++ → Metal texture) work already flagged in
`ios_implementation_gap.md §5` and `STATUS.md`; noting it here because against the *native
client* it is a functional regression, not a "secondary platform" caveat.

**Not a gap:** screen sharing. The native iOS client has no ReplayKit/broadcast path either, so
jami-kmp's absent screen share on iOS is parity.

---

## 3. Bundle / entitlement / lifecycle defects

Found by reading `ios-app/iosApp/Info.plist`, `iosApp.entitlements`, `AppDelegate.swift`.

1. **Duplicate `UIBackgroundModes` key in `Info.plist`.** The dict has two
   `<key>UIBackgroundModes</key>` entries:
   - first: `audio, fetch, processing, remote-notification, voip`
   - second: `voip, remote-notification, audio`

   A plist dict with a duplicate key is malformed; depending on the toolchain the build either
   errors or last-wins. If last-wins, the app **loses `fetch` and `processing`**. Collapse to
   one key with the full set.

2. **`processing` / `fetch` background modes declared but unused.** `SyncManager.ios.kt` only
   calls `beginBackgroundTaskWithName` (the short window). There is no `BGTaskScheduler`
   registration and no `BGTaskSchedulerPermittedIdentifiers` array in `Info.plist`. Either
   implement `BGAppRefreshTask` / `BGProcessingTask` (and add the identifiers) or drop the two
   modes. As written they are dead declarations and can draw App Review questions.

3. **`aps-environment` is `development` only.** TestFlight / App Store builds need `production`
   (normally via Xcode's Push Notifications capability driving a `$(aps-environment)` build
   setting). With `development`, a distribution build's APNs registration fails silently and no
   push arrives. Also: the App ID must have Push Notifications enabled in the developer portal —
   not something this repo can encode, worth a checklist line in `doc/push-notifications.md`.

4. **`CXProviderConfiguration`** now sets `localizedName = "Jami"` (good — the older audit's
   "blank app name on the call screen" is fixed). No `iconTemplateImageData` / `ringtoneSound`
   though; native sets both. Minor.

5. **No scene-phase / lifecycle handling beyond the four `AppDelegate` hooks.** Native tracks
   active/inactive to coordinate the extension handoff and audio session. Tied to gap 2.1;
   revisit together.

6. **`CFBundleLocalizations`** is now present and lists ~95 locales (the older audit's "runs in
   English regardless of device language" concern is addressed at the plist level — still worth
   confirming Compose resources actually resolve the non-English bundles at runtime).

---

## 4. Feature-by-feature parity

Legend: ✅ parity · 🟡 partial / degraded · ❌ missing · ➖ N/A on this platform

| Area | Native iOS | jami-kmp iOS | Notes |
|------|-----------|--------------|-------|
| Onboarding / walkthrough | ✅ | ✅ | `WelcomeScreen`, `CreateAccountScreen`, `ProfileSetupScreen` |
| Create Jami account | ✅ | ✅ | |
| SIP account | ✅ | ✅ | `AccountAdvancedSettingsScreen` |
| Import from archive / backup | ✅ | ✅ | `ImportAccountScreen` |
| Link device (QR / new-device flow) | ✅ | ✅ | `LinkDeviceImportScreen`; `onAddDeviceStateChanged` now forwarded |
| Account migration | ✅ | ✅ | `MigrationDialog`; `onMigrationEnded` forwarded |
| Multi-account + switching | ✅ | ✅ | |
| Name registration / lookup | ✅ | 🟡 | `onRegisteredNameFound` wired; `onUserSearchEnded` (directory search) — Kotlin override present, confirm it fires end-to-end |
| Smartlist / conversation list | ✅ | ✅ | sorting, compact mode |
| Swarm 1:1 messaging | ✅ | 🟡 | works on fresh handshake; **first send into a still-bootstrapping 1:1 swarm can be silently dropped** (`doc/stabilization-findings-2026-09-04.md` F1/F4) — cross-platform, not iOS-specific |
| Group swarm mgmt (add/remove/leave/rename/avatar/roles) | ✅ | ✅ | |
| Message edit / delete / reactions / replies | ✅ | ✅ | history reactions marshalling fixed |
| Typing indicators / read receipts | ✅ | 🟡 | `onComposingStatusChanged` wired; `onAccountMessageStatusChanged` override present — verify receipts actually update UI |
| In-conversation message search | ✅ | 🟡 | `onMessagesFound` override present; native has dedicated `JamiSearchView` — confirm wired |
| File send / receive | ✅ | 🟡 | send + download UI done; `onDataTransferEvent` now forwarded so progress/completion *should* work — **unverified**; auto-accept size / wifi-only toggles stored but not enforced |
| Audio message record/play | ✅ | ✅ | `AVAudioRecorder` |
| Location sharing | ✅ | ✅ | MapKit |
| Media gallery | ✅ | ✅ | `MediaGalleryScreen` |
| Contacts: add / block / unblock / trust | ✅ | ✅ | `BlockedContactsScreen` |
| Contact requests (accept/decline) | ✅ | ✅ | `onConversationRequestDeclined` now forwarded |
| System-contacts sync | ✅ | ✅ | `SystemContactsService` |
| QR generate / scan | ✅ | ✅ | AVFoundation scanner |
| Audio call (1:1) | ✅ | ✅ | |
| Video call (1:1) | ✅ | ❌ | connects, no media rendered/sent — see §2.4 |
| Conference / grid / moderation | ✅ | 🟡 | UI + `onConferenceInfoUpdated` forwarded; video tiles blank without §2.4 |
| "Join active call" banner | ✅ | 🟡 | `onActiveCallsChanged` override present — confirm banner appears |
| CallKit incoming UI | ✅ | ✅ | plus push-placeholder adoption + deferred answer/decline |
| CallKit outgoing registration | ✅ | ✅ | |
| Dialpad / DTMF | ✅ | ✅ | in-call dialpad |
| Ringtone selection | ✅ | ➖ | iOS has no system ringtone picker; `RingtoneLauncher.ios.kt` returns `null` deliberately |
| Notifications while app running | ✅ | ✅ | `IOSNotificationService`, quiet hours, per-type toggles, action buttons wired |
| Notifications while app suspended/killed | ✅ | ❌ | **no notification extension** — see §2.1 |
| Share INTO Jami from other apps | ✅ | ❌ | **no share extension** — see §2.2 |
| Push token registration (APNs + PushKit) | ✅ | ✅ | `IOSPushServiceManager` |
| Push message decryption off-DHT-proxy | ✅ | ❌ | needs extension + push-capable proxy |
| Biometric account lock | ✅ | ✅ | Keychain-backed now (was plaintext `NSUserDefaults`) |
| Screen-capture protection | 🟡 | 🟡 | both block screen *recording*/mirroring; neither blocks still screenshots (iOS API limit) |
| Connectivity monitoring | ✅ | ✅ | `NWPathMonitor` added `707f849` |
| Bluetooth / wired headset call audio | ✅ | 🟡 | audio-session options fixed `707f849`; confirm output-device list now populates (was hardcoded `[INTERNAL, SPEAKERS]`) |
| Background refresh beyond ~30 s | ✅ (via extension) | ❌ | no `BGTaskScheduler`; iOS platform limit without the extension model |
| Diagnostic / log view | ✅ | ✅ | `DebugLogsScreen` |
| Chat plugins | ✅ | ❌ | Jami plugin system not ported (menu shows "not yet supported") — parity gap but low priority |

---

## 5. Suspected implementation issues in jami-kmp iOS

Static-read observations. Each needs simulator/device confirmation.

1. **`ios_implementation_gap.md` is stale and misleading.** Anyone reading it today will
   chase already-fixed work (bundle capabilities, audio routing, the 15 signals). Either
   update it or mark it superseded by this doc. *(documentation defect, real)*

2. **Forwarded daemon signals are unverified end-to-end.** `DaemonBridge.ios.kt` now *overrides*
   ~15 previously-missing callbacks and `JamiBridgeWrapper.h` declares matching selectors, but
   the audit trail explicitly says the test suite could not run against them and there is no
   two-instance test. High-value: `onDataTransferEvent` (file progress/completion),
   `onAccountMessageStatusChanged` (receipts), `onUserSearchEnded` / `onMessagesFound`
   (search). Treat as "implemented, unproven".

3. **`captureVideoFrame` TODO with a live producer.** ~~`IOSCameraService` is already pushing
   NV12 frames (with a per-frame `ByteArray` allocation) into a no-op.~~ **Fixed 2026-09-10** —
   the per-frame lock+copy was removed; capture/preview/dimension tracking stay. Forwarding
   frames to the daemon is planned in `doc/ios-video-pipeline.md` §2 (needs the bridge rebuilt).

4. **`onVoipPushReceived` payload key assumptions.** `IOSPushServiceManager.onVoipPushReceived`
   reads `payload["peerId"]`, `["displayName"]`, `["hasVideo"]`. The actual PushKit payload
   shape is defined by the DHT proxy; the native client's field names differ (`NotificationField`
   uses `to` / `key` / `aps`). If the proxy doesn't emit exactly `peerId`/`displayName`, the
   CallKit placeholder shows "Jami" / no video and `adoptPendingPush` still works only via the
   empty-peerId fallback. Verify against the real proxy payload.

5. **Duplicate `UIBackgroundModes`** — see §3.1. Concrete, fix now.

6. **`aps-environment: development`** will silently break push on TestFlight/App Store — see §3.3.

7. **`IOSNotificationService` uses `runBlocking` + `getString`** for string resources
   (`import kotlinx.coroutines.runBlocking`). If ever called from the main thread (notification
   presentation callback) this can deadlock or drop frames. Native resolves strings synchronously
   from bundle. Check the call sites.

8. **Audio session is activated by jami-kmp itself, not by CallKit's `didActivate` callback.**
   `CallKitManager` does **not** override `provider:didActivateAudioSession:` /
   `provider:didDeactivateAudioSession:`. Instead `HardwareService.ios.kt` calls
   `activateAudioSession()` directly on `RINGING/CONNECTING/CURRENT` call-status changes
   (`.playAndRecord` / `.voiceChat`, Bluetooth options — that part is correct). Apple's
   documented pattern is the opposite: configure the category early but let CallKit *activate*
   the session in `provider:didActivateAudioSession:`. Self-activating races CallKit and is a
   classic cause of "answered from the lock screen and hear nothing" / one-way audio on the
   first call. The native client routes activation through the provider delegate. **Verify on a
   device; likely the highest-impact call bug.**

9. **`maximumCallsPerCallGroup = 1u`** — no call waiting / second incoming call. Native allows
   it. Minor, but a deliberate limitation to record.

---

## 6. Recommended priority order

1. **App Group entitlement** (S) — unblocks everything below. Add
   `group.net.jami.<...>`, point libjami's working dir at the group container in both app and
   (future) extension.
2. **Notification Service Extension** (L) — port the `NotificationService.swift` state machine.
   This is what makes iOS message delivery real. Do it before any more UI polish.
3. **Info.plist / entitlement fixes** (S) — dedupe `UIBackgroundModes`; `$(aps-environment)`;
   decide `BGTaskScheduler` vs. drop `fetch`/`processing`.
4. **Verify the 2026-09-08 signal-forwarding wave** (S–M) — have `StubDaemonBridge` retain its
   callbacks and add one firing test per new callback (the audit doc even specifies this).
   Confirm file progress, receipts, and directory search actually work.
5. **Confirm CallKit audio-session activation** (S) — the "answer from lock screen → silence"
   risk in §5.8.
6. **Video pipeline** (L, multi-week) — ObjC++ `SinkTarget` → Metal for incoming; wire
   `captureVideoFrame` for outgoing; fix the PiP layer-type mismatch. Until then, short-circuit
   the dead capture copy (§5.3).
7. **Share Extension** (M) — port `jamiShareExtension`.
8. Lower priority: encoder controls, auto-accept enforcement, in-conversation search UI parity,
   chat plugins.

---

## 7. What is already at parity (scope boundary)

So the gaps above are not read as "nothing works": account lifecycle, contacts/requests,
swarm messaging and group management, the full settings surface, QR, location, biometric lock,
audio calling, CallKit incoming/outgoing with push-placeholder handling, notification
presentation *while the app runs*, connectivity monitoring, and the APNs/PushKit token
lifecycle are all implemented and structurally comparable to the native client. The KMP
CallKit and push-token code is, if anything, tidier than the equivalent Swift.
