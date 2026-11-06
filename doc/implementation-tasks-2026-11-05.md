# Implementation tasks — assembled 2026-11-05

Collected from `STATUS.md`, `CLAUDE.md`, `doc/TODO.md`, `doc/KNOWN-ISSUES.md`,
`doc/status-note-open-ends.md`, `doc/mini-plans/`, `doc/apple-bridge-rebuild.md`,
`docs/refine-video-call.md` and `doc/gap-analysis-medium-opportunities.md`.
Each task names the file or doc that carries its evidence; nothing here was re-derived
against source, so premises older than 2026-10 should be checked before coding (the
mini-plans roll-up records four findings whose evidence had gone stale).

Current HEAD when assembled: `989ba1b` "Apply three settings that were only ever stored".
A1 and A2 were closed on 2026-11-05, A3 and B2 on 2026-11-06.

---

## A. Defects with a known fix

- **A1 — ~~`Account.getByContact` throws when the account holds a group~~.** Already fixed in
  `69ab199`; `doc/KNOWN-ISSUES.md` still said "not yet done" and was corrected 2026-11-05. Both
  walks in `model/Account.kt` use `contactOrNull`, and `AccountGroupLookupTest` now also covers
  the `pending` walk (the second changed line, untested until then). **No work left.**

- **A2 — ~~screen sharing on Android 14+ has no `mediaProjection` foreground service~~.**
  Fixed 2026-11-05. Premise confirmed against source first (no permission, no service with the
  type, and `MainActivity.kt:98` redeeming the consent token straight out of `onActivityResult`).
  Fixed with a dedicated `net.jami.services.ScreenShareService` rather than by adding the type to
  `CallNotificationService` — that service starts for every call with no capture session to back
  the type with, as its own manifest comment already argued. **A device check is still owed on
  both the defect and the fix.**

- **A3 — ~~`AccountSettingsViewModel.loadAccount()` constructs a fresh state~~.**
  Fixed 2026-11-06. Premise confirmed against source first: it listed 15 of the state's 22
  fields, so `linkDeviceState` and the six `registerName*` fields were reset on every
  `AccountEvent.DetailsChanged` — the register-username dialog closed itself mid-typing. Now a
  `copy()`, and the status-note fields no longer need carrying through by hand. Regression test:
  `AccountSettingsViewModelTest.loadAccountDoesNotCloseTheRegisterNameDialog`.
  (`doc/status-note-open-ends.md` open end 3)

## B. Settings stored but applied nowhere

Static evidence from `doc/manual-test-plan-2026-09-10.md` B10–B14; B13/B15/B16 were closed
on 2026-10-31.

- **B1 (B10/B11) — notification sound and vibration toggles do nothing.**
  `AndroidNotificationService` sets them on the `Notification.Builder`, which Android ignores
  from API 26; channels hardcode `enableVibration(...)` and the default sound, and only
  `CHANNEL_CALLS` is ever recreated. Needs channel recreation on change (or per-setting
  channel ids). Also `NotificationGuard.getSoundUri()` returns `null` both for "sound off"
  and "no custom sound", and `null` means *use the default* — the two cases need separating.

- **B2 (B12) — ~~quiet hours suppresses incoming calls~~.**
  Fixed 2026-11-06. Premise confirmed against source first (the term was in the conjunction,
  and both platforms early-return on it at four call sites). Semantics chosen: quiet hours
  *silence* a call rather than hiding it, and blocking a call outright is left to the system's
  own Do Not Disturb. `shouldShowCallNotification()` lost the quiet-hours term; a new
  `shouldSilenceCallNotification()` sends Android's call notifications to `CHANNEL_CALLS_SILENT`
  (a separate channel because from API 26 the channel owns the sound; `IMPORTANCE_HIGH` is kept
  so the heads-up banner and full-screen intent survive) and drops the sound on iOS. Messages
  and requests keep the old suppression. Covered by the new `NotificationGuardTest`.
  **A device check is still owed**, and `doc/manual-test-plan-2026-09-10.md` B12 was rewritten
  to test the fix.

- **B3 (B14) — `blockUnknownContacts` is written and read back by the settings screen and
  used nowhere else.** Needs enforcement on the incoming trust-request / incoming-call path.

- **B4 — build and run the iOS half of B13.** `HardwareService.isMeteredNetwork()` uses
  `nw_path_is_expensive` on iOS; Apple targets are skipped on the Linux host, so that actual
  has never been through a compiler. (`STATUS.md`)

## C. Chat parity with jami-android-client

- **C1 — @mentions in group chat.** Reference parses and highlights `@username`; KMP has no
  mention system in the chat UI or view model.
- **C2 — Markdown in message bodies.** Reference renders with Markwon (bold, italics, code);
  KMP shows plain text. Clickable http(s) links already landed 2026-09-18.
- **C3 — read markers.** Reference shows peers' avatars at the last message each has read
  (`Conversation.lastDisplayedMessages`) and a status icon only on the last sent message
  (`lastSent`); KMP puts a tick on every outgoing message and no avatars.
  `Conversation.updateSwarmInteraction` already records both fields, so this is UI-layer work.
- **C4 — full emoji picker for reactions.** KMP offers six fixed emojis; reference has a
  "more" button opening a full picker.
- **C5 — video recording in chat.** Menu item shows a "coming soon" snackbar; needs a
  Camera2 / AVFoundation capture session.

## D. Calls

- **D1 — proximity sensor.** `proximityEnabled` exists in `SettingsModels.kt`;
  `PROXIMITY_SCREEN_OFF_WAKE_LOCK` is never acquired in the Android `HardwareService`.
- **D2 — haptic feedback on answer/decline.** No `Vibrator` / `HapticFeedbackConstants`
  anywhere in the call flow.
- **D3 — Bluetooth headset routing.** `toggleSpeaker()` exists; the 3-way
  earpiece/speaker/BT route is not fully wired through `AudioManager`.
- **D4 — incoming video call screen (`docs/refine-video-call.md` P2).** Add the live camera
  preview while a video call is ringing and a separate "accept audio only" button, matching
  `initIncomingCallDisplay`.
- **D5 — expose `videoEnabled` in call settings (P2)** and **gate outgoing calls on
  `videoEnabled` + `hasCamera()` (P3)**, matching `CallPresenter.initOutGoing():98`.

## E. App settings / platform integration

- **E1 — battery-optimisation exemption prompt.** No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  intent in KMP; the reference prompts for whitelisting.
- **E2 — language selector.** `UiSettings` already carries a `language` field, so this is UI
  wiring in `AppSettingsScreen` plus applying the locale.
- **E3 — "display name in notifications" privacy toggle.** Reference lets users hide contact
  names from notifications; absent from `AppSettingsScreen`.
- **E4 — conversation app shortcuts.** Declared in the reference `AndroidManifest.xml`, not
  in the KMP manifest.

## F. Apple platforms

- **F1 — one Mac session for the batched wrapper regeneration.** `JamiBridgeWrapper.h/.mm`
  needs five entry points, then `build-jamibridge.sh` and an Xcode link (a green
  `linkDebugFrameworkIosSimulatorArm64` proves nothing — the framework is `isStatic = true`;
  verify with `nm <archive> | grep -cE '[-+]\[JamiBridgeWrapper '`):
  - `connectivityChanged` — `NWPathMonitor` already feeds `HardwareService`, but
    `DaemonBridge.ios.kt` can only log it, so libjami never re-resolves connections after a
    network change.
  - `setDefaultModerator`.
  - `publish` (presence status note) **plus `lineStatus` in the presence callback** —
    `DaemonBridge.ios.kt:1364` / `.macos.kt:1063` synthesise the buddy notification and pass
    `""`, so Apple can neither send nor receive a note.
  - `sendFile`'s missing `parent` argument — a file sent as a reply arrives as a plain file.
  - `setAudioMeterState` + the `AudioMeter` callback — the microphone level ring stays dark.
  - `createConfFromParticipantList`.
  (`doc/apple-bridge-rebuild.md`, `doc/status-note-open-ends.md` open end 4)

- **F2 — iOS/macOS call recording.** `toggleRecording` / `getIsRecording` return `false`.
  Needs the two wrapper methods plus a `RecordingStateChanged` delegate, then a `.a` rebuild —
  fold into F1. (`doc/plan_call_recording.md`)

- **F3 — iOS remote video rendering.** No Metal/CALayer `SinkTarget`; audio-only calls work.
  Architectural, multi-week. (`ios_implementation_gap.md`)

- **F4 — shared `appleMain` source set, fixing the macOS build.** ~95 Kotlin errors, mostly
  `MacOSHardwareService.kt`; the Darwin implementations are now substantively identical, so
  one copy fixes both. Start by rebasing `origin/macos-build-and-apple-source-set` (`f0f66ce`)
  onto `main` — it predates the nine wrapper commits. Separately,
  `build-jamibridge.sh`'s macOS stage fails on UIKit code compiled against the macOS SDK
  (long-standing, `93df547a`).

- **F5 — script the device-side native symlinks.**
  `shared/src/nativeInterop/cinterop/lib/` is hand-made and drifts silently when the daemon's
  dependency set changes (three of five first-build failures were exactly this). The simulator
  side is already discovered by `scripts/make_sim_links.py`; mirror it for
  `jami-client-ios/DEPS/arm64-iPhoneOS/lib/` and keep `OTHER_LDFLAGS` in `project.pbxproj`
  in step.

## G. Push delivery

- **G1 — stand up a push-capable DHT proxy.** Client integration is done on both platforms;
  the push is sent by the proxy, and the public one only holds SFL's FCM credentials. Needs a
  self-hosted `dhtnode --proxyserver` holding this app's own Firebase/APNs server keys.
  (`doc/push-notifications.md`)

## H. Older gap-analysis leftovers — confirm before implementing

`doc/gap-analysis-medium-opportunities.md` is marked superseded (2026-03-19) and much of it
has since landed under other names. Treat each as a read-the-source check first:

- **H1** Conversation search and history pagination (`onMessagesFound`, `loadMore()`).
- **H2** Name-registration state machine (in-progress → success/failure) for the creation wizard.
- **H3** Presence streamed onto the `Contact` model as a `StateFlow` + availability indicators.
- **H4** Account wizard presenter parity (SIP creation, device linking/import, profile step).
- **H5** `CallViewModel` parity with the reference `CallPresenter`.
- **H6** Certificate/device management leftovers (`validateCertificatePath`,
  `getCertificateDetails`, `migrateAccount`, TLS method enumeration) — the certificates screen
  landed in `security-networking.md` F11, so scope this to what is genuinely missing.

## I. Test and harness infrastructure

- **I1 — Robolectric for the Android unit tests**, so `HardwareServiceTest`,
  `AppSettingsViewModelTest`, `AppViewModelTest` and `CallViewModelTest` run against the
  Android actuals instead of desktop only.
- **I2 — let `StubDaemonBridge` retain its callbacks.** `init(callbacks)` discards them, so
  none of the 15 daemon callbacks on the iOS bridge can be exercised without a live peer.
- **I3 — harden the harness "no competing daemon" precondition.** Add `cx.ring.debug` (and
  consider `com.savoirfairelinux.ring`) to `COMPETING_APP_IDS`; re-assert the force-stop just
  before the first cross-device step rather than once per run; add a pre-run
  `pm clear net.jami.android.harness` / account wipe so a timed-out run cannot poison the next
  scenario.
- **I4 — device verification owed on the durable outbox.** The `send-before-member-join` e2e
  scenario bypasses `SendQueueService`, so the UI-driven path has never been checked.
- **I5 — microphone level ring.** Needs a call someone answers, which adb cannot do alone.
  (`doc/two-device-verification-2026-10-29.md`)

## J. Licensing and attribution (GPLv3)

- **J1 — BLOCKED: decide the copyright holder name/entity.** Nothing else in this section can
  be written first, and it must not be guessed from a git config.
- **J2 — fix `AboutViewModel.kt:39`.** `COPYRIGHT = "Copyright (C) 2004-2025 Savoir-faire
  Linux Inc."` is rendered in the About screen, so the running app tells users this is an SFL
  product; GPLv3 §5(d) makes it a compliance matter. Becomes a two-line attribution.
  `AboutViewModelTest.kt:37` asserts on the constant and changes in the same commit.
  Highest priority after J1 — the only item users see.
- **J3 — declare the modification (§5(a)).** No file states that it was modified or when. Add
  a "part of Jami KMP, a modified cross-platform port of jami-client-android; modified 2026"
  line to the header block.
- **J4 — classify the ~341 `.kt` and 477 `.xml` files derived vs. original and add our line**
  below SFL's where derived, alone where original. Keep every SFL notice (§4/§5(b)).
- **J5 — consider an `AUTHORS` file** separating upstream from this port's contributors.

## K. Decisions before any code

- **K1 — chat plugins (`daemon-api-surface-audit.md` F3).** The single biggest remaining
  feature and the last open mini-plan finding: install format, file management, per-account
  and per-conversation state, dynamic preference forms from daemon metadata. Plugins are
  desktop-first in the reference clients and nothing has established that the mobile-primary
  targets want them. Plan: `doc/plan_plugin_system.md`.
- **K2 — Web/JS platform.** The whole daemon bridge is REST stubs; candidate for removal
  unless a REST bridge server is actually developed. Decide rather than carry it.
- **K3 — Desktop JVM.** `DaemonBridge` is 100+ no-ops; the blocker is SWIG-generated JNI
  classes conflicting with KMP's Android plugin, needing a separate JVM module.
  `VideoSurface.desktop.kt` and `OsmMapView` desktop/macOS stubs all sit behind it.
  Deprioritised — keep it that way, or fund the separate module.
- **K4 — `QRCodeUtils.js.kt`** returns null; only worth doing if K2 keeps the web target.
